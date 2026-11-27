package com.axell.hardcoretools.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.axell.hardcoretools.state.HardcoreStateManager;
import com.axell.hardcoretools.state.PlayerProfile;
import com.axell.hardcoretools.state.WaypointEntry;
import com.mojang.datafixers.util.Pair;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.structure.Structure;

public final class TeleportFeature {
	// Structures that generate underground or as enclosed dungeons where landing INSIDE the structure is preferred
	private static final Set<String> INTERIOR_STRUCTURES = Set.of(
			"trial_chambers",
			"ancient_city",
			"stronghold",
			"mineshaft",
			"mineshaft_mesa",
			"fortress",
			"bastion_remnant",
			"end_city",
			"mansion"
	);

	private TeleportFeature() {
	}

	public static void recordBackLocation(ServerPlayerEntity player, String label) {
		PlayerProfile profile = HardcoreStateManager.getProfile(player);
		String dim = player.getServerWorld().getRegistryKey().getValue().toString();
		profile.lastTeleportOrigin = new WaypointEntry(
				"back",
				label == null || label.isBlank() ? "Previous Position" : label,
				dim,
				Math.round(player.getX() * 10.0) / 10.0,
				Math.round(player.getY() * 10.0) / 10.0,
				Math.round(player.getZ() * 10.0) / 10.0,
				player.getYaw(),
				player.getPitch(),
				0xFFFFBC3B
		);
		HardcoreStateManager.save(player.getServer());
	}

	public static WaypointEntry teleportBack(ServerPlayerEntity player) {
		PlayerProfile profile = HardcoreStateManager.getProfile(player);
		WaypointEntry target = profile.lastTeleportOrigin;
		if (target == null) {
			return null;
		}
		ServerWorld targetWorld = resolveWorld(player.getServer(), target.dimension);
		if (targetWorld == null) {
			return null;
		}

		// Swap current position into lastTeleportOrigin so player can ping-pong Base <-> Dungeon effortlessly
		String curDim = player.getServerWorld().getRegistryKey().getValue().toString();
		WaypointEntry previousCurrent = new WaypointEntry(
				"back",
				"Before /back",
				curDim,
				Math.round(player.getX() * 10.0) / 10.0,
				Math.round(player.getY() * 10.0) / 10.0,
				Math.round(player.getZ() * 10.0) / 10.0,
				player.getYaw(),
				player.getPitch(),
				0xFFFFBC3B
		);
		profile.lastTeleportOrigin = previousCurrent;
		HardcoreStateManager.save(player.getServer());

		player.fallDistance = 0.0F;
		player.teleport(targetWorld, target.x, target.y, target.z, target.yaw, target.pitch);
		playTeleportSound(player);
		return target;
	}

	public static String teleportToPlayer(ServerPlayerEntity player, String targetName) {
		MinecraftServer server = player.getServer();
		if (server == null) return null;
		ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetName);
		if (target == null) {
			return null;
		}
		recordBackLocation(player, "Before TP to " + target.getName().getString());
		player.fallDistance = 0.0F;
		player.teleport(
				target.getServerWorld(),
				target.getX(),
				target.getY(),
				target.getZ(),
				target.getYaw(),
				target.getPitch()
		);
		playTeleportSound(player);
		return target.getName().getString();
	}

	public static boolean teleportToCoords(ServerPlayerEntity player, String dimensionId, double x, double y, double z) {
		ServerWorld targetWorld = resolveWorld(player.getServer(), dimensionId);
		if (targetWorld == null) {
			targetWorld = player.getServerWorld();
		}
		recordBackLocation(player, "Before Coords TP");
		player.fallDistance = 0.0F;
		player.teleport(targetWorld, x, y, z, player.getYaw(), player.getPitch());
		playTeleportSound(player);
		return true;
	}

	public static BlockPos locateAndTeleportBiome(ServerPlayerEntity player, String biomeId, boolean autoTeleport) {
		Identifier id = Identifier.tryParse(biomeId);
		if (id == null) return null;

		ServerWorld targetWorld = pickDimensionForTarget(player, id.getPath());
		BlockPos searchOrigin = getScaledOrigin(player, targetWorld);

		Pair<BlockPos, RegistryEntry<Biome>> found = targetWorld.locateBiome(
				entry -> entry.matchesId(id),
				searchOrigin,
				6400,
				32,
				64
		);
		if (found == null) {
			return null;
		}
		BlockPos foundPos = found.getFirst();
		BlockPos safePos = findSafeLandingPos(targetWorld, foundPos.getX(), foundPos.getZ());
		if (autoTeleport) {
			recordBackLocation(player, "Before " + id.getPath());
			player.fallDistance = 0.0F;
			player.teleport(targetWorld, safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, player.getYaw(), player.getPitch());
			playTeleportSound(player);
		}
		return safePos;
	}

	public static BlockPos locateAndTeleportStructure(ServerPlayerEntity player, String structureId, boolean autoTeleport) {
		Identifier id = Identifier.tryParse(structureId);
		if (id == null) return null;

		ServerWorld targetWorld = pickDimensionForStructure(player, id.getPath());
		Registry<Structure> structureRegistry = player.getServer().getRegistryManager().get(RegistryKeys.STRUCTURE);
		RegistryEntry.Reference<Structure> entry = structureRegistry.getEntry(id).orElse(null);
		if (entry == null) {
			return null;
		}

		BlockPos searchOrigin = getScaledOrigin(player, targetWorld);
		Pair<BlockPos, RegistryEntry<Structure>> found = targetWorld.getChunkManager().getChunkGenerator().locateStructure(
				targetWorld,
				RegistryEntryList.of(entry),
				searchOrigin,
				100,
				false
		);
		if (found == null) {
			return null;
		}

		BlockPos foundPos = found.getFirst();
		StructureLanding landing = findSmartStructureLanding(targetWorld, entry.value(), id.getPath(), foundPos);
		BlockPos safePos = landing.feetPos();
		if (autoTeleport) {
			recordBackLocation(player, "Before " + id.getPath());
			float targetYaw = player.getYaw();
			float targetPitch = player.getPitch();
			if (landing.focalPos() != null && !landing.focalPos().equals(safePos)) {
				double dx = (landing.focalPos().getX() + 0.5D) - (safePos.getX() + 0.5D);
				double dz = (landing.focalPos().getZ() + 0.5D) - (safePos.getZ() + 0.5D);
				if (Math.abs(dx) > 0.01D || Math.abs(dz) > 0.01D) {
					targetYaw = (float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
					targetPitch = 8.0F;
				}
			}
			player.fallDistance = 0.0F;
			player.teleport(targetWorld, safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, targetYaw, targetPitch);
			playTeleportSound(player);
		}
		return safePos;
	}

	private record StructureLanding(BlockPos feetPos, BlockPos focalPos) {
	}

	/**
	 * Pinpoint Core Structure Landing:
	 * Inspects StructurePieces by piece type ID & signature core blocks so teleporting to a structure
	 * lands the player directly in front of its heart:
	 *  - Stronghold      -> End Portal Room (END_PORTAL_FRAME)
	 *  - End City        -> End Ship Elytra Cabin (BREWING_STAND) or Treasure Room (ENDER_CHEST / CHEST)
	 *  - Trial Chambers  -> Vault / Ominous Vault Room (VAULT / TRIAL_SPAWNER)
	 *  - Ancient City    -> Central Warden Portal & Altar Chests (REINFORCED_DEEPSLATE)
	 *  - Nether Fortress -> Blaze Spawner Platform (SPAWNER) or Nether Wart Stairs (NETHER_WART)
	 *  - Bastion Remnant -> Treasure Gold Room (GOLD_BLOCK / CHEST)
	 *  - Villages        -> Town Bell Meeting Point (BELL)
	 *  - Igloo           -> Secret Underground Lab (BREWING_STAND) if present, else cabin
	 *  - Desert/Jungle   -> Treasure Chamber (trap-safe, avoids pressure plates & tripwire!)
	 *  - Buried Treasure -> Exact X/Z block directly above the buried Chest!
	 */
	private static StructureLanding findSmartStructureLanding(ServerWorld world, Structure structure, String path, BlockPos locatedPos) {
		Chunk originChunk = world.getChunk(locatedPos.getX() >> 4, locatedPos.getZ() >> 4);
		StructureStart start = originChunk.getStructureStarts().get(structure);
		if (start == null || !start.hasChildren()) {
			start = world.getStructureAccessor().getStructureAt(locatedPos, structure);
		}

		// Special case: Buried Treasure -> pinpoint exact buried Chest X/Z so digging straight down hits it immediately
		if (path.equals("buried_treasure")) {
			BlockBox treasureArea = (start != null && start.hasChildren())
					? start.getBoundingBox()
					: new BlockBox(locatedPos.getX() - 12, world.getBottomY() + 8, locatedPos.getZ() - 12,
							locatedPos.getX() + 12, world.getTopY() - 4, locatedPos.getZ() + 12);
			BlockPos chestPos = findAnchorBlockInBox(world, treasureArea, List.of(Blocks.CHEST));
			if (chestPos != null) {
				BlockPos surface = findSafeLandingPos(world, chestPos.getX(), chestPos.getZ());
				return new StructureLanding(surface, chestPos);
			}
		}

		List<Block> primaryCoreBlocks = getPrimaryCoreBlocks(path);
		List<Block> secondaryCoreBlocks = getSecondaryCoreBlocks(path);

		if (start != null && start.hasChildren()) {
			List<StructurePiece> orderedPieces = prioritizePiecesForCore(start.getChildren(), path);
			int scanLimit = path.equals("stronghold") || path.equals("fortress") || path.equals("end_city")
					? orderedPieces.size()
					: Math.min(orderedPieces.size(), 18);

			// Pass 1: Search prioritized pieces for primary signature core block (End Portal, End Ship, Vault, Blaze Spawner, etc.)
			if (!primaryCoreBlocks.isEmpty()) {
				int checkedChunksPieces = 0;
				for (int i = 0; i < scanLimit && checkedChunksPieces < 14; i++) {
					StructurePiece piece = orderedPieces.get(i);
					// For Stronghold, if a 'shpr' PortalRoom exists it is at index 0!
					checkedChunksPieces++;
					StructureLanding hit = scanPieceForCoreAnchor(world, piece.getBoundingBox(), primaryCoreBlocks);
					if (hit != null) {
						return hit;
					}
				}
			}

			// Pass 2: Search for secondary core blocks (Nether Wart, Trial Spawner, Ender Chest, etc.)
			if (!secondaryCoreBlocks.isEmpty()) {
				for (int i = 0; i < Math.min(scanLimit, 12); i++) {
					StructureLanding hit = scanPieceForCoreAnchor(world, orderedPieces.get(i).getBoundingBox(), secondaryCoreBlocks);
					if (hit != null) {
						return hit;
					}
				}
			}

			// Pass 3: Interior dry room inside prioritized pieces
			boolean preferInterior = INTERIOR_STRUCTURES.contains(path)
					|| path.equals("desert_pyramid")
					|| path.equals("jungle_pyramid")
					|| path.equals("igloo")
					|| path.equals("swamp_hut")
					|| path.equals("pillager_outpost")
					|| world.getDimension().hasCeiling()
					|| world.getRegistryKey().equals(World.NETHER);

			if (preferInterior) {
				for (int i = 0; i < Math.min(orderedPieces.size(), 10); i++) {
					BlockPos inside = scanPieceForDryRoom(world, orderedPieces.get(i).getBoundingBox());
					if (inside != null) {
						return new StructureLanding(inside, orderedPieces.get(i).getBoundingBox().getCenter());
					}
				}
				BlockPos fallbackInside = scanPieceForDryRoom(world, start.getBoundingBox());
				if (fallbackInside != null) {
					return new StructureLanding(fallbackInside, start.getBoundingBox().getCenter());
				}
			} else {
				BlockBox firstBox = orderedPieces.get(0).getBoundingBox();
				BlockPos center = firstBox.getCenter();
				if (path.equals("monument")) {
					for (int i = 0; i < Math.min(orderedPieces.size(), 6); i++) {
						BlockPos dryRoom = scanPieceForDryRoom(world, orderedPieces.get(i).getBoundingBox());
						if (dryRoom != null) return new StructureLanding(dryRoom, center);
					}
				}
				return new StructureLanding(findSafeLandingPos(world, center.getX(), center.getZ()), center);
			}
		}

		// Fallback if StructureStart metadata wasn't attached directly on originChunk
		if (path.equals("ancient_city")) {
			BlockPos underground = scanColumnBandForDryRoom(world, locatedPos.getX(), locatedPos.getZ(), -52, -20);
			if (underground != null) return new StructureLanding(underground, null);
		} else if (path.equals("trial_chambers")) {
			BlockPos underground = scanColumnBandForDryRoom(world, locatedPos.getX(), locatedPos.getZ(), -42, -5);
			if (underground != null) return new StructureLanding(underground, null);
		} else if (path.equals("stronghold")) {
			BlockPos underground = scanColumnBandForDryRoom(world, locatedPos.getX(), locatedPos.getZ(), -32, 48);
			if (underground != null) return new StructureLanding(underground, null);
		}

		return new StructureLanding(findSafeLandingPos(world, locatedPos.getX(), locatedPos.getZ()), null);
	}

	private static List<StructurePiece> prioritizePiecesForCore(List<StructurePiece> rawPieces, String path) {
		List<StructurePiece> sorted = new ArrayList<>(rawPieces);
		sorted.sort(Comparator.comparingInt((StructurePiece p) -> piecePriorityScore(p, path)).reversed());
		return sorted;
	}

	private static int piecePriorityScore(StructurePiece piece, String path) {
		Identifier typeId = Registries.STRUCTURE_PIECE.getId(piece.getType());
		String typePath = typeId == null ? "" : typeId.getPath();
		BlockBox b = piece.getBoundingBox();
		int area = Math.max(1, (b.getMaxX() - b.getMinX() + 1) * (b.getMaxZ() - b.getMinZ() + 1));
		int height = Math.max(1, b.getMaxY() - b.getMinY() + 1);

		if (path.equals("stronghold")) {
			// 'shpr' is StrongholdGenerator.PortalRoom (End Portal Room!)
			if (typePath.equals("shpr")) return 1_000_000;
			// 'shli' is Stronghold Library
			if (typePath.equals("shli")) return 50_000;
			return area;
		}
		if (path.equals("fortress")) {
			// 'nebs' is NetherFortressGenerator.BridgePlatform (Blaze Spawner!)
			if (typePath.equals("nebs")) return 1_000_000;
			// 'nesc' is CorridorNetherWartsRoom
			if (typePath.equals("nesc")) return 500_000;
			return area;
		}
		if (path.equals("end_city")) {
			// End Ship generates high & elongated with large footprint
			return (b.getMinY() * 200) + area;
		}
		if (path.equals("igloo")) {
			// Secret basement piece has the lowest minY!
			return -b.getMinY() * 1000 + height * 20;
		}
		if (path.equals("pillager_outpost")) {
			// Top watchtower piece has highest maxY
			return b.getMaxY() * 500 + area;
		}
		// For Ancient City, Bastion, Trial Chambers, Mansion, Villages: largest central room piece first!
		return area * Math.min(24, height);
	}

	private static List<Block> getPrimaryCoreBlocks(String path) {
		if (path.equals("stronghold")) return List.of(Blocks.END_PORTAL_FRAME);
		if (path.equals("end_city")) return List.of(Blocks.BREWING_STAND); // Inside End Ship right next to the Elytra!
		if (path.equals("trial_chambers")) return List.of(Blocks.VAULT);
		if (path.equals("ancient_city")) return List.of(Blocks.REINFORCED_DEEPSLATE);
		if (path.equals("fortress")) return List.of(Blocks.SPAWNER);
		if (path.equals("bastion_remnant")) return List.of(Blocks.GOLD_BLOCK);
		if (path.equals("mansion")) return List.of(Blocks.CHEST, Blocks.DIAMOND_BLOCK, Blocks.LAPIS_BLOCK);
		if (path.equals("desert_pyramid")) return List.of(Blocks.BLUE_TERRACOTTA);
		if (path.equals("jungle_pyramid")) return List.of(Blocks.CHEST, Blocks.LEVER);
		if (path.equals("igloo")) return List.of(Blocks.BREWING_STAND, Blocks.CAULDRON);
		if (path.equals("pillager_outpost")) return List.of(Blocks.CHEST);
		if (path.startsWith("village_")) return List.of(Blocks.BELL);
		if (path.equals("swamp_hut")) return List.of(Blocks.CAULDRON, Blocks.CRAFTING_TABLE);
		if (path.equals("shipwreck")) return List.of(Blocks.CHEST);
		return List.of();
	}

	private static List<Block> getSecondaryCoreBlocks(String path) {
		if (path.equals("end_city")) return List.of(Blocks.ENDER_CHEST, Blocks.CHEST);
		if (path.equals("trial_chambers")) return List.of(Blocks.TRIAL_SPAWNER);
		if (path.equals("fortress")) return List.of(Blocks.NETHER_WART, Blocks.CHEST);
		if (path.equals("bastion_remnant")) return List.of(Blocks.CHEST, Blocks.GILDED_BLACKSTONE);
		if (path.equals("ancient_city")) return List.of(Blocks.CHEST);
		return List.of();
	}

	private static StructureLanding scanPieceForCoreAnchor(ServerWorld world, BlockBox box, List<Block> targetBlocks) {
		BlockPos anchor = findAnchorBlockInBox(world, box, targetBlocks);
		if (anchor == null) {
			return null;
		}
		// Find a safe, dry, trap-free standing spot within 1..5 blocks of the core block!
		for (int r = 1; r <= 5; r++) {
			for (int dy = 0; dy >= -2; dy--) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
						BlockPos candidate = anchor.add(dx, dy, dz);
						world.getChunk(candidate.getX() >> 4, candidate.getZ() >> 4);
						if (isDryBreathableRoom(world, candidate)) {
							return new StructureLanding(candidate, anchor);
						}
					}
				}
			}
			for (int dy = 1; dy <= 2; dy++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
						BlockPos candidate = anchor.add(dx, dy, dz);
						world.getChunk(candidate.getX() >> 4, candidate.getZ() >> 4);
						if (isDryBreathableRoom(world, candidate)) {
							return new StructureLanding(candidate, anchor);
						}
					}
				}
			}
		}
		// If standing directly on top of the anchor itself is safe (e.g. Desert Pyramid Blue Terracotta center)
		if (isDryBreathableRoom(world, anchor.up())) {
			return new StructureLanding(anchor.up(), anchor);
		}
		return null;
	}

	private static BlockPos findAnchorBlockInBox(ServerWorld world, BlockBox box, List<Block> targets) {
		int minX = box.getMinX();
		int maxX = box.getMaxX();
		int minZ = box.getMinZ();
		int maxZ = box.getMaxZ();
		int minY = Math.max(world.getBottomY() + 1, box.getMinY());
		int maxY = Math.min(world.getTopY() - 2, box.getMaxY());

		// Force-load intersecting chunks of this piece
		for (int cx = (minX >> 4); cx <= (maxX >> 4); cx++) {
			for (int cz = (minZ >> 4); cz <= (maxZ >> 4); cz++) {
				world.getChunk(cx, cz);
			}
		}

		for (Block preferred : targets) {
			for (int y = maxY; y >= minY; y--) {
				for (int x = minX; x <= maxX; x++) {
					for (int z = minZ; z <= maxZ; z++) {
						if (world.getBlockState(new BlockPos(x, y, z)).isOf(preferred)) {
							return new BlockPos(x, y, z);
						}
					}
				}
			}
		}
		return null;
	}

	private static BlockPos scanPieceForDryRoom(ServerWorld world, BlockBox box) {
		BlockPos center = box.getCenter();
		int cx = center.getX();
		int cz = center.getZ();
		int minY = Math.max(world.getBottomY() + 1, box.getMinY());
		int maxY = Math.min(world.getTopY() - 3, box.getMaxY());

		// Force-load the chunk containing the piece center so all structure blocks are actually placed!
		world.getChunk(cx >> 4, cz >> 4);

		int maxRadius = Math.min(10, Math.max(box.getMaxX() - cx, box.getMaxZ() - cz));
		for (int r = 0; r <= maxRadius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
					int x = cx + dx;
					int z = cz + dz;
					if (x < box.getMinX() || x > box.getMaxX() || z < box.getMinZ() || z > box.getMaxZ()) continue;
					world.getChunk(x >> 4, z >> 4);

					for (int y = minY + 1; y <= maxY; y++) {
						BlockPos feet = new BlockPos(x, y, z);
						if (isDryBreathableRoom(world, feet)) {
							return feet;
						}
					}
				}
			}
		}
		return null;
	}

	private static BlockPos scanColumnBandForDryRoom(ServerWorld world, int cx, int cz, int minY, int maxY) {
		for (int r = 0; r <= 12; r += 2) {
			for (int dx = -r; dx <= r; dx += 2) {
				for (int dz = -r; dz <= r; dz += 2) {
					if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;
					int x = cx + dx;
					int z = cz + dz;
					world.getChunk(x >> 4, z >> 4);
					for (int y = minY; y <= maxY; y++) {
						BlockPos feet = new BlockPos(x, y, z);
						if (isDryBreathableRoom(world, feet)) {
							return feet;
						}
					}
				}
			}
		}
		return null;
	}

	private static boolean isDryBreathableRoom(ServerWorld world, BlockPos feet) {
		BlockState floor = world.getBlockState(feet.down());
		BlockState lower = world.getBlockState(feet);
		BlockState upper = world.getBlockState(feet.up());
		if (!isSafeFloor(floor) || floor.isOf(Blocks.BEDROCK) || floor.isOf(Blocks.SCULK_SHRIEKER)
				|| floor.isOf(Blocks.SCULK_SENSOR) || floor.isOf(Blocks.TNT) || floor.isOf(Blocks.SPAWNER)) {
			return false;
		}
		if (lower.isOf(Blocks.STONE_PRESSURE_PLATE) || lower.isOf(Blocks.OAK_PRESSURE_PLATE) || lower.isOf(Blocks.TRIPWIRE)) {
			return false;
		}
		if (!lower.getFluidState().isEmpty() || !upper.getFluidState().isEmpty()) {
			return false;
		}
		return isPassableSafe(lower) && isPassableSafe(upper);
	}

	public static WaypointEntry createWaypoint(ServerPlayerEntity player, String name, int color) {
		PlayerProfile profile = HardcoreStateManager.getProfile(player);
		if (profile.waypoints.size() >= 64) {
			return null;
		}
		String cleanName = (name == null || name.isBlank()) ? ("Waypoint #" + (profile.waypoints.size() + 1)) : name.trim();
		if (cleanName.length() > 36) {
			cleanName = cleanName.substring(0, 36);
		}
		String dim = player.getServerWorld().getRegistryKey().getValue().toString();
		WaypointEntry wp = new WaypointEntry(
				UUID.randomUUID().toString().substring(0, 8),
				cleanName,
				dim,
				Math.round(player.getX() * 10.0) / 10.0,
				Math.round(player.getY() * 10.0) / 10.0,
				Math.round(player.getZ() * 10.0) / 10.0,
				player.getYaw(),
				player.getPitch(),
				color
		);
		profile.waypoints.add(wp);
		HardcoreStateManager.save(player.getServer());
		playTeleportSound(player);
		return wp;
	}

	public static boolean teleportToWaypoint(ServerPlayerEntity player, String waypointId) {
		PlayerProfile profile = HardcoreStateManager.getProfile(player);
		for (WaypointEntry wp : profile.waypoints) {
			if (wp.id.equals(waypointId)) {
				ServerWorld world = resolveWorld(player.getServer(), wp.dimension);
				if (world == null) return false;
				recordBackLocation(player, "Before WP: " + wp.name);
				player.fallDistance = 0.0F;
				player.teleport(world, wp.x, wp.y, wp.z, wp.yaw, wp.pitch);
				playTeleportSound(player);
				return true;
			}
		}
		return false;
	}

	public static boolean deleteWaypoint(ServerPlayerEntity player, String waypointId) {
		PlayerProfile profile = HardcoreStateManager.getProfile(player);
		boolean removed = profile.waypoints.removeIf(wp -> wp.id.equals(waypointId));
		if (removed) {
			HardcoreStateManager.save(player.getServer());
		}
		return removed;
	}

	private static BlockPos getScaledOrigin(ServerPlayerEntity player, ServerWorld targetWorld) {
		ServerWorld cur = player.getServerWorld();
		if (cur == targetWorld) {
			return player.getBlockPos();
		}
		double scale = DimensionType.getCoordinateScaleFactor(cur.getDimension(), targetWorld.getDimension());
		return BlockPos.ofFloored(player.getX() * scale, 64, player.getZ() * scale);
	}

	private static ServerWorld pickDimensionForTarget(ServerPlayerEntity player, String path) {
		MinecraftServer server = player.getServer();
		if (path.contains("nether") || path.contains("crimson") || path.contains("warped") || path.contains("soul_sand") || path.contains("basalt")) {
			ServerWorld nether = server.getWorld(World.NETHER);
			if (nether != null) return nether;
		}
		if (path.startsWith("end_") || path.equals("the_end") || path.contains("small_end")) {
			ServerWorld end = server.getWorld(World.END);
			if (end != null) return end;
		}
		ServerWorld overworld = server.getWorld(World.OVERWORLD);
		return overworld != null ? overworld : player.getServerWorld();
	}

	private static ServerWorld pickDimensionForStructure(ServerPlayerEntity player, String path) {
		MinecraftServer server = player.getServer();
		if (path.equals("fortress") || path.equals("bastion_remnant") || path.equals("nether_fossil") || path.equals("ruined_portal_nether")) {
			ServerWorld nether = server.getWorld(World.NETHER);
			if (nether != null) return nether;
		}
		if (path.equals("end_city")) {
			ServerWorld end = server.getWorld(World.END);
			if (end != null) return end;
		}
		ServerWorld overworld = server.getWorld(World.OVERWORLD);
		return overworld != null ? overworld : player.getServerWorld();
	}

	public static ServerWorld resolveWorld(MinecraftServer server, String dimensionId) {
		if (server == null || dimensionId == null) return null;
		Identifier id = Identifier.tryParse(dimensionId);
		if (id == null) return null;
		RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
		return server.getWorld(key);
	}

	public static BlockPos findSafeLandingPos(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);

		if (world.getDimension().hasCeiling() || world.getRegistryKey().equals(World.NETHER)) {
			for (int y = 118; y >= 32; y--) {
				BlockPos floor = new BlockPos(x, y - 1, z);
				BlockPos feet = new BlockPos(x, y, z);
				BlockPos head = new BlockPos(x, y + 1, z);
				BlockState floorState = world.getBlockState(floor);
				if (isSafeFloor(floorState) && isPassableSafe(world.getBlockState(feet)) && isPassableSafe(world.getBlockState(head))) {
					return feet;
				}
			}
			BlockPos safePad = new BlockPos(x, 63, z);
			world.setBlockState(safePad, Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_ALL);
			world.setBlockState(safePad.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
			world.setBlockState(safePad.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
			return safePad.up();
		}

		int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (topY <= world.getBottomY() + 1) {
			BlockPos pad = new BlockPos(x, 64, z);
			world.setBlockState(pad, Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_ALL);
			return pad.up();
		}
		BlockPos floor = new BlockPos(x, topY - 1, z);
		BlockState floorState = world.getBlockState(floor);
		if (floorState.isOf(Blocks.LAVA) || floorState.isOf(Blocks.MAGMA_BLOCK) || floorState.isOf(Blocks.POWDER_SNOW)) {
			world.setBlockState(floor, Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
		}
		return new BlockPos(x, topY, z);
	}

	private static boolean isSafeFloor(BlockState state) {
		if (state.isAir() || state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)
				|| state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.POWDER_SNOW)
				|| state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.WITHER_ROSE)) {
			return false;
		}
		return state.getFluidState().isEmpty() && state.blocksMovement();
	}

	private static boolean isPassableSafe(BlockState state) {
		if (state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)
				|| state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.COBWEB) || state.isOf(Blocks.SWEET_BERRY_BUSH)) {
			return false;
		}
		return state.isAir() || (!state.blocksMovement() && state.getFluidState().isEmpty());
	}

	private static void playTeleportSound(ServerPlayerEntity player) {
		player.getWorld().playSound(
				null,
				player.getX(),
				player.getY(),
				player.getZ(),
				SoundEvents.ENTITY_ENDERMAN_TELEPORT,
				SoundCategory.PLAYERS,
				0.9F,
				1.05F
		);
	}
}
