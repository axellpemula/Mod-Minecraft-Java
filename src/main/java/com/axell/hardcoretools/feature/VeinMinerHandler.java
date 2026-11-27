package com.axell.hardcoretools.feature;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import com.axell.hardcoretools.state.HardcoreStateManager;
import com.axell.hardcoretools.state.PlayerProfile;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.RedstoneOreBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class VeinMinerHandler {
	private static final Set<UUID> ACTIVE_VEIN_PLAYERS = new HashSet<>();
	private static final int[][] FACES_6 = {
			{1, 0, 0}, {-1, 0, 0},
			{0, 1, 0}, {0, -1, 0},
			{0, 0, 1}, {0, 0, -1}
	};

	private VeinMinerHandler() {
	}

	public static void register() {
		// Primary block break interception is handled via ServerPlayerInteractionManagerMixin#tryBreakBlock(@At("RETURN"))
		// so that Block#afterBreak has already spawned Block #1's ItemEntity drops before Auto-Smelt & Auto-Collect run!
	}

	public static boolean isActiveVeinPlayer(UUID uuid) {
		return ACTIVE_VEIN_PLAYERS.contains(uuid);
	}

	public static void handlePrimaryBlockBroken(ServerWorld serverWorld, ServerPlayerEntity serverPlayer, BlockPos pos, BlockState state, ItemStack preBreakTool) {
		if (serverPlayer.isCreative() || serverPlayer.isSpectator()) {
			return;
		}
		UUID uuid = serverPlayer.getUuid();
		if (ACTIVE_VEIN_PLAYERS.contains(uuid)) {
			return;
		}

		PlayerProfile profile = HardcoreStateManager.getProfile(serverPlayer);
		ItemStack liveTool = serverPlayer.getMainHandStack();
		ItemStack effectiveRefTool = preBreakTool.isEmpty() ? liveTool : preBreakTool;

		boolean isOre = isOreBlock(state);
		boolean isLog = isTreeLog(state);
		boolean hasPureSilk = hasPureSilkTouchWithoutFortune(serverWorld, effectiveRefTool);
		boolean canAutoSmelt = isOre && profile.veinAutoSmelt && isValidOreTool(effectiveRefTool, state) && !hasPureSilk;
		boolean shouldAutoCollectPrimary = profile.veinAutoCollect && (isOre || (isLog && profile.treeCapitator));

		// 1. Smelt & Auto-Collect Block #1's freshly spawned drops right after Block#afterBreak!
		if (canAutoSmelt || shouldAutoCollectPrimary) {
			int fortuneBonus = getFortuneIfBothSilkAndFortune(serverWorld, effectiveRefTool);
			processPrimaryBlockDrops(serverWorld, serverPlayer, pos, state, canAutoSmelt, shouldAutoCollectPrimary, fortuneBonus);
		}

		// 2. Apply Ore Plus+ bonus drops to Block #1 as well
		if (isOre) {
			grantOrePlusBonus(serverWorld, serverPlayer, pos, state, effectiveRefTool, canAutoSmelt);
		}

		// 3. Chain-mine connected Vein / Tree blocks
		if (isOre && profile.veinMiner && isValidOreTool(effectiveRefTool, state)) {
			ACTIVE_VEIN_PLAYERS.add(uuid);
			try {
				runVeinMine(serverWorld, serverPlayer, pos, state, liveTool, effectiveRefTool, profile, canAutoSmelt);
				serverPlayer.playerScreenHandler.sendContentUpdates();
			} finally {
				ACTIVE_VEIN_PLAYERS.remove(uuid);
			}
		} else if (isLog && profile.treeCapitator && effectiveRefTool.isIn(ItemTags.AXES)) {
			if (hasMatchingNaturalLeavesNearby(serverWorld, pos, state.getBlock())) {
				ACTIVE_VEIN_PLAYERS.add(uuid);
				try {
					runTreeCapitator(serverWorld, serverPlayer, pos, state, liveTool, profile);
					serverPlayer.playerScreenHandler.sendContentUpdates();
				} finally {
					ACTIVE_VEIN_PLAYERS.remove(uuid);
				}
			}
		}
	}

	private static void processPrimaryBlockDrops(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, boolean autoSmelt, boolean autoCollect, int godStackFortune) {
		List<ItemEntity> freshDrops = world.getEntitiesByClass(
				ItemEntity.class,
				new Box(pos).expand(1.65),
				e -> !e.isRemoved() && e.getItemAge() == 0
		);
		if (freshDrops.isEmpty()) {
			return;
		}

		float totalSmeltXp = 0.0F;
		boolean smeltedAny = false;
		boolean inventoryChanged = false;

		for (ItemEntity entity : freshDrops) {
			ItemStack stack = entity.getStack();
			if (stack.isEmpty()) continue;

			// If tool had BOTH Silk Touch + Fortune (God-Stack), convert Silk-dropped ore block count with Fortune!
			if (godStackFortune > 0 && isDroppedOreBlockItem(stack)) {
				int mult = 1 + world.random.nextInt(godStackFortune + 1);
				stack = stack.copyWithCount(stack.getCount() * mult);
				entity.setStack(stack);
			}

			if (autoSmelt) {
				SmeltResult res = smeltOreStack(stack, state);
				if (res != null) {
					stack = res.output();
					entity.setStack(stack);
					totalSmeltXp += res.xp();
					smeltedAny = true;
				}
			}

			if (autoCollect) {
				int maxSize = Math.max(1, stack.getMaxCount());
				int remaining = stack.getCount();
				int leftover = 0;
				while (remaining > 0) {
					int batch = Math.min(maxSize, remaining);
					ItemStack part = stack.copyWithCount(batch);
					player.getInventory().insertStack(part);
					if (!part.isEmpty()) {
						leftover += part.getCount();
					}
					remaining -= batch;
				}
				inventoryChanged = true;
				if (leftover <= 0) {
					entity.discard();
				} else {
					entity.setStack(stack.copyWithCount(Math.min(maxSize, leftover)));
				}
			}
		}

		if (inventoryChanged) {
			player.playerScreenHandler.sendContentUpdates();
		}
		if (smeltedAny) {
			awardSmeltingXpAndParticles(world, player, pos, totalSmeltXp, autoCollect);
		}
	}

	private static void runVeinMine(ServerWorld world, ServerPlayerEntity player, BlockPos origin, BlockState targetState, ItemStack liveTool, ItemStack refTool, PlayerProfile profile, boolean autoSmelt) {
		String targetFamily = getOreFamily(targetState.getBlock());
		int maxBlocks = profile.veinMaxBlocks;
		ItemStack lootTool = buildEffectiveLootTool(world, refTool, autoSmelt);

		Queue<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		visited.add(origin.toImmutable());
		enqueueNeighbors26(origin, visited, queue);

		int broken = 0;
		while (!queue.isEmpty() && broken < maxBlocks - 1) {
			if (!liveTool.isEmpty() && liveTool.isDamageable() && liveTool.getDamage() >= liveTool.getMaxDamage() - 1) {
				break;
			}
			BlockPos current = queue.poll();
			BlockState state = world.getBlockState(current);
			if (!isOreBlock(state) || !getOreFamily(state.getBlock()).equals(targetFamily)) {
				continue;
			}
			breakAndCollect(world, player, current, state, liveTool, lootTool, profile.veinAutoCollect, true, autoSmelt);
			broken++;
			enqueueNeighbors26(current, visited, queue);
		}
	}

	private static void runTreeCapitator(ServerWorld world, ServerPlayerEntity player, BlockPos origin, BlockState targetState, ItemStack tool, PlayerProfile profile) {
		Block originBlock = targetState.getBlock();
		int maxBlocks = profile.veinMaxBlocks;
		int maxHorizRadius = getMaxBranchRadius(originBlock);

		Queue<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		Set<BlockPos> brokenLogs = new HashSet<>();
		BlockPos originImm = origin.toImmutable();
		visited.add(originImm);
		brokenLogs.add(originImm);
		enqueueTreeNeighbors(originImm, originImm, visited, queue);

		int broken = 0;
		while (!queue.isEmpty() && broken < maxBlocks - 1) {
			if (tool.isEmpty() || (tool.isDamageable() && tool.getDamage() >= tool.getMaxDamage() - 1)) {
				break;
			}
			BlockPos current = queue.poll();
			if (current.getY() < origin.getY() - 1
					|| Math.abs(current.getX() - origin.getX()) > maxHorizRadius
					|| Math.abs(current.getZ() - origin.getZ()) > maxHorizRadius) {
				continue;
			}
			BlockState state = world.getBlockState(current);
			if (state.getBlock() != originBlock) {
				continue;
			}
			// Do not jump horizontally into a neighboring tree's separate grounded trunk!
			if ((Math.abs(current.getX() - origin.getX()) > 1 || Math.abs(current.getZ() - origin.getZ()) > 1)
					&& isSeparateGroundedTrunk(world, current, originBlock, origin, brokenLogs)) {
				continue;
			}

			breakAndCollect(world, player, current, state, tool, tool, profile.veinAutoCollect, false, false);
			BlockPos curImm = current.toImmutable();
			brokenLogs.add(curImm);
			broken++;
			enqueueTreeNeighbors(curImm, originImm, visited, queue);
		}

		if (profile.fastLeafDecay && !brokenLogs.isEmpty()) {
			decayIsolatedTreeLeaves(world, player, originBlock, brokenLogs, profile.veinAutoCollect);
		}
	}

	private static void decayIsolatedTreeLeaves(ServerWorld world, ServerPlayerEntity player, Block originLogBlock, Set<BlockPos> brokenLogs, boolean autoCollect) {
		int maxLeafSteps = 6;
		Map<BlockPos, Integer> distFromFelled = new HashMap<>();
		Queue<BlockPos> leafQueue = new ArrayDeque<>();

		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

		for (BlockPos logPos : brokenLogs) {
			minX = Math.min(minX, logPos.getX());
			minY = Math.min(minY, logPos.getY());
			minZ = Math.min(minZ, logPos.getZ());
			maxX = Math.max(maxX, logPos.getX());
			maxY = Math.max(maxY, logPos.getY());
			maxZ = Math.max(maxZ, logPos.getZ());

			for (int[] d : FACES_6) {
				BlockPos adj = logPos.add(d[0], d[1], d[2]).toImmutable();
				if (brokenLogs.contains(adj) || distFromFelled.containsKey(adj)) continue;
				if (matchesTreeLeaf(originLogBlock, world.getBlockState(adj))) {
					distFromFelled.put(adj, 1);
					leafQueue.add(adj);
				}
			}
		}

		while (!leafQueue.isEmpty() && distFromFelled.size() < 512) {
			BlockPos cur = leafQueue.poll();
			int curDist = distFromFelled.get(cur);
			if (curDist >= maxLeafSteps) continue;

			for (int[] d : FACES_6) {
				BlockPos nxt = cur.add(d[0], d[1], d[2]).toImmutable();
				if (brokenLogs.contains(nxt) || distFromFelled.containsKey(nxt)) continue;
				if (matchesTreeLeaf(originLogBlock, world.getBlockState(nxt))) {
					distFromFelled.put(nxt, curDist + 1);
					leafQueue.add(nxt);
				}
			}
		}

		if (distFromFelled.isEmpty()) {
			return;
		}

		Map<BlockPos, Integer> distFromStanding = new HashMap<>();
		Queue<BlockPos> standingQueue = new ArrayDeque<>();
		int margin = maxLeafSteps + 2;

		for (int x = minX - margin; x <= maxX + margin; x++) {
			for (int y = minY - 2; y <= maxY + margin; y++) {
				for (int z = minZ - margin; z <= maxZ + margin; z++) {
					BlockPos scan = new BlockPos(x, y, z);
					if (brokenLogs.contains(scan)) continue;
					BlockState st = world.getBlockState(scan);
					if (!isTreeLog(st)) continue;

					for (int[] d : FACES_6) {
						BlockPos adj = scan.add(d[0], d[1], d[2]).toImmutable();
						if (distFromStanding.containsKey(adj)) continue;
						BlockState adjState = world.getBlockState(adj);
						if (isNaturalLeaf(adjState)) {
							distFromStanding.put(adj, 1);
							standingQueue.add(adj);
						}
					}
				}
			}
		}

		while (!standingQueue.isEmpty()) {
			BlockPos cur = standingQueue.poll();
			int curDist = distFromStanding.get(cur);
			if (curDist >= maxLeafSteps) continue;

			for (int[] d : FACES_6) {
				BlockPos nxt = cur.add(d[0], d[1], d[2]).toImmutable();
				Integer prev = distFromStanding.get(nxt);
				if (prev != null && prev <= curDist + 1) continue;
				if (nxt.getX() < minX - margin || nxt.getX() > maxX + margin
						|| nxt.getY() < minY - 3 || nxt.getY() > maxY + margin
						|| nxt.getZ() < minZ - margin || nxt.getZ() > maxZ + margin) {
					continue;
				}
				if (isNaturalLeaf(world.getBlockState(nxt))) {
					distFromStanding.put(nxt, curDist + 1);
					standingQueue.add(nxt);
				}
			}
		}

		int decayed = 0;
		for (Map.Entry<BlockPos, Integer> entry : distFromFelled.entrySet()) {
			if (decayed >= 220) break;
			BlockPos leafPos = entry.getKey();
			int dFelled = entry.getValue();
			Integer dStanding = distFromStanding.get(leafPos);

			if (dStanding != null && dStanding <= dFelled) {
				continue;
			}

			BlockState leafState = world.getBlockState(leafPos);
			if (!matchesTreeLeaf(originLogBlock, leafState)) {
				continue;
			}
			List<ItemStack> drops = Block.getDroppedStacks(leafState, world, leafPos, null, player, ItemStack.EMPTY);
			world.setBlockState(leafPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
			deliverDrops(world, player, leafPos, stateOrAir(leafState), drops, autoCollect, false);
			decayed++;
		}
	}

	private static void enqueueTreeNeighbors(BlockPos center, BlockPos origin, Set<BlockPos> visited, Queue<BlockPos> queue) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					BlockPos next = center.add(dx, dy, dz);
					if (dy < 0 && (Math.abs(next.getX() - origin.getX()) > 1 || Math.abs(next.getZ() - origin.getZ()) > 1)) {
						continue;
					}
					if (visited.add(next.toImmutable())) {
						queue.add(next.toImmutable());
					}
				}
			}
		}
	}

	private static boolean isSeparateGroundedTrunk(ServerWorld world, BlockPos pos, Block logBlock, BlockPos origin, Set<BlockPos> brokenLogs) {
		int logsBelow = 0;
		for (int step = 1; step <= 16; step++) {
			BlockPos below = pos.down(step);
			if (brokenLogs.contains(below)) {
				return false;
			}
			BlockState st = world.getBlockState(below);
			if (st.getBlock() == logBlock) {
				logsBelow++;
				continue;
			}
			if (logsBelow >= 1 && isTreeSoil(st)) {
				return Math.abs(below.getX() - origin.getX()) > 1 || Math.abs(below.getZ() - origin.getZ()) > 1;
			}
			break;
		}
		return false;
	}

	private static boolean isTreeSoil(BlockState state) {
		return state.isIn(BlockTags.DIRT)
				|| state.isOf(Blocks.MUD)
				|| state.isOf(Blocks.MUDDY_MANGROVE_ROOTS)
				|| state.isOf(Blocks.MANGROVE_ROOTS)
				|| state.isOf(Blocks.NETHERRACK)
				|| state.isOf(Blocks.CRIMSON_NYLIUM)
				|| state.isOf(Blocks.WARPED_NYLIUM);
	}

	private static int getMaxBranchRadius(Block logBlock) {
		if (logBlock == Blocks.BIRCH_LOG || logBlock == Blocks.SPRUCE_LOG) {
			return 3;
		}
		return 8;
	}

	private static BlockState stateOrAir(BlockState s) {
		return s == null ? Blocks.AIR.getDefaultState() : s;
	}

	private static void breakAndCollect(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, ItemStack liveTool, ItemStack lootTool, boolean autoCollect, boolean applyOrePlus, boolean autoSmelt) {
		BlockEntity be = world.getBlockEntity(pos);
		List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, be, player, lootTool);
		world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		// Pass false to spawnStacks so vanilla only spawns XP orbs (not duplicate raw item drops!)
		state.onStacksDropped(world, pos, lootTool, true);

		deliverDrops(world, player, pos, state, drops, autoCollect, autoSmelt);
		if (applyOrePlus) {
			grantOrePlusBonus(world, player, pos, state, lootTool, autoSmelt);
		}

		if (!liveTool.isEmpty() && liveTool.isDamageable()) {
			liveTool.damage(1, player, EquipmentSlot.MAINHAND);
		}
	}

	private static void grantOrePlusBonus(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, ItemStack tool, boolean autoSmelt) {
		String oreKey = getOrePlusKey(state.getBlock());
		if (oreKey == null || !HardcoreStateManager.isOreBoosted(world.getServer(), oreKey)) {
			return;
		}
		if (hasPureSilkTouchWithoutFortune(world, tool)) {
			return;
		}
		Item bonusDrop = getOreRawDrop(oreKey);
		if (bonusDrop == null) {
			return;
		}
		int mult = HardcoreStateManager.get(world.getServer()).orePlusMultiplier;
		int extraCount = Math.max(1, mult - 1);
		if (oreKey.equals("redstone") || oreKey.equals("lapis") || oreKey.equals("copper")) {
			extraCount *= (2 + world.random.nextInt(2));
		}
		ItemStack bonusStack = new ItemStack(bonusDrop, extraCount);
		PlayerProfile profile = HardcoreStateManager.getProfile(player);
		deliverDrops(world, player, pos, state, List.of(bonusStack), profile.veinAutoCollect, autoSmelt);
	}

	/**
	 * Returns true ONLY if the tool has Silk Touch WITHOUT Fortune (an intentional Silk-Touch-only pickaxe).
	 * If a tool has BOTH Silk Touch and Fortune (from Bypass Conflicts God-Stack), Fortune + Auto-Smelt take priority!
	 */
	private static boolean hasPureSilkTouchWithoutFortune(ServerWorld world, ItemStack tool) {
		if (tool.isEmpty()) return false;
		var reg = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		RegistryEntry<Enchantment> silk = reg.getEntry(Enchantments.SILK_TOUCH).orElse(null);
		RegistryEntry<Enchantment> fortune = reg.getEntry(Enchantments.FORTUNE).orElse(null);
		boolean hasSilk = silk != null && EnchantmentHelper.getLevel(silk, tool) > 0;
		boolean hasFortune = fortune != null && EnchantmentHelper.getLevel(fortune, tool) > 0;
		return hasSilk && !hasFortune;
	}

	private static int getFortuneIfBothSilkAndFortune(ServerWorld world, ItemStack tool) {
		if (tool.isEmpty()) return 0;
		var reg = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		RegistryEntry<Enchantment> silk = reg.getEntry(Enchantments.SILK_TOUCH).orElse(null);
		RegistryEntry<Enchantment> fortune = reg.getEntry(Enchantments.FORTUNE).orElse(null);
		if (silk != null && fortune != null && EnchantmentHelper.getLevel(silk, tool) > 0) {
			return EnchantmentHelper.getLevel(fortune, tool);
		}
		return 0;
	}

	private static ItemStack buildEffectiveLootTool(ServerWorld world, ItemStack tool, boolean autoSmelt) {
		if (tool.isEmpty()) return tool;
		var reg = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		RegistryEntry<Enchantment> silk = reg.getEntry(Enchantments.SILK_TOUCH).orElse(null);
		RegistryEntry<Enchantment> fortune = reg.getEntry(Enchantments.FORTUNE).orElse(null);
		boolean hasSilk = silk != null && EnchantmentHelper.getLevel(silk, tool) > 0;
		boolean hasFortune = fortune != null && EnchantmentHelper.getLevel(fortune, tool) > 0;
		if (hasSilk && (hasFortune || autoSmelt)) {
			ItemStack cleanCopy = tool.copy();
			EnchantmentHelper.apply(cleanCopy, b -> b.set(silk, 0));
			return cleanCopy;
		}
		return tool;
	}

	private static boolean isDroppedOreBlockItem(ItemStack stack) {
		return stack.isOf(Items.IRON_ORE) || stack.isOf(Items.DEEPSLATE_IRON_ORE)
				|| stack.isOf(Items.GOLD_ORE) || stack.isOf(Items.DEEPSLATE_GOLD_ORE)
				|| stack.isOf(Items.COPPER_ORE) || stack.isOf(Items.DEEPSLATE_COPPER_ORE)
				|| stack.isOf(Items.COAL_ORE) || stack.isOf(Items.DEEPSLATE_COAL_ORE)
				|| stack.isOf(Items.DIAMOND_ORE) || stack.isOf(Items.DEEPSLATE_DIAMOND_ORE)
				|| stack.isOf(Items.EMERALD_ORE) || stack.isOf(Items.DEEPSLATE_EMERALD_ORE)
				|| stack.isOf(Items.LAPIS_ORE) || stack.isOf(Items.DEEPSLATE_LAPIS_ORE)
				|| stack.isOf(Items.REDSTONE_ORE) || stack.isOf(Items.DEEPSLATE_REDSTONE_ORE)
				|| stack.isOf(Items.NETHER_GOLD_ORE) || stack.isOf(Items.NETHER_QUARTZ_ORE);
	}

	private static void deliverDrops(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState brokenState, List<ItemStack> drops, boolean autoCollect, boolean autoSmelt) {
		float totalSmeltXp = 0.0F;
		boolean smeltedAny = false;

		for (ItemStack drop : drops) {
			if (drop.isEmpty()) continue;
			ItemStack copy = drop.copy();
			if (autoSmelt) {
				SmeltResult res = smeltOreStack(copy, brokenState);
				if (res != null) {
					copy = res.output();
					totalSmeltXp += res.xp();
					smeltedAny = true;
				}
			}
			if (autoCollect) {
				// Split oversized stacks (> 64 from high Fortune / Raw Blocks) cleanly into standard stack sizes
				int maxSize = Math.max(1, copy.getMaxCount());
				int remaining = copy.getCount();
				while (remaining > 0) {
					int batch = Math.min(maxSize, remaining);
					ItemStack part = copy.copyWithCount(batch);
					player.getInventory().insertStack(part);
					if (!part.isEmpty()) {
						Block.dropStack(world, player.getBlockPos(), part);
					}
					remaining -= batch;
				}
			} else {
				Block.dropStack(world, pos, copy);
			}
		}

		if (smeltedAny) {
			awardSmeltingXpAndParticles(world, player, pos, totalSmeltXp, autoCollect);
		}
	}

	private static void awardSmeltingXpAndParticles(ServerWorld world, ServerPlayerEntity player, BlockPos pos, float rawXp, boolean autoCollect) {
		int xp = MathHelper.floor(rawXp);
		float frac = rawXp - xp;
		if (frac > 0.0F && world.random.nextFloat() < frac) {
			xp++;
		}
		if (xp > 0) {
			Vec3d xpPos = autoCollect ? player.getPos() : Vec3d.ofCenter(pos);
			ExperienceOrbEntity.spawn(world, xpPos, xp);
		}
		world.spawnParticles(
				ParticleTypes.FLAME,
				pos.getX() + 0.5,
				pos.getY() + 0.5,
				pos.getZ() + 0.5,
				3,
				0.22,
				0.22,
				0.22,
				0.015
		);
	}

	private static SmeltResult smeltOreStack(ItemStack stack, BlockState brokenState) {
		int count = stack.getCount();
		if (count <= 0) return null;

		if (stack.isOf(Items.RAW_IRON) || stack.isOf(Items.IRON_ORE) || stack.isOf(Items.DEEPSLATE_IRON_ORE)) {
			return new SmeltResult(new ItemStack(Items.IRON_INGOT, count), 0.7F * count);
		}
		if (stack.isOf(Items.RAW_IRON_BLOCK)) {
			return new SmeltResult(new ItemStack(Items.IRON_INGOT, count * 9), 6.3F * count);
		}
		if (stack.isOf(Items.RAW_GOLD) || stack.isOf(Items.GOLD_ORE) || stack.isOf(Items.DEEPSLATE_GOLD_ORE) || stack.isOf(Items.NETHER_GOLD_ORE)) {
			return new SmeltResult(new ItemStack(Items.GOLD_INGOT, count), 1.0F * count);
		}
		if (stack.isOf(Items.RAW_GOLD_BLOCK)) {
			return new SmeltResult(new ItemStack(Items.GOLD_INGOT, count * 9), 9.0F * count);
		}
		if (stack.isOf(Items.RAW_COPPER) || stack.isOf(Items.COPPER_ORE) || stack.isOf(Items.DEEPSLATE_COPPER_ORE)) {
			return new SmeltResult(new ItemStack(Items.COPPER_INGOT, count), 0.7F * count);
		}
		if (stack.isOf(Items.RAW_COPPER_BLOCK)) {
			return new SmeltResult(new ItemStack(Items.COPPER_INGOT, count * 9), 6.3F * count);
		}
		if (stack.isOf(Items.ANCIENT_DEBRIS)) {
			return new SmeltResult(new ItemStack(Items.NETHERITE_SCRAP, count), 2.0F * count);
		}
		if (stack.isOf(Items.GOLD_NUGGET) && brokenState != null
				&& (brokenState.isOf(Blocks.NETHER_GOLD_ORE) || brokenState.isOf(Blocks.GILDED_BLACKSTONE))) {
			int ingots = Math.max(1, count / 4);
			return new SmeltResult(new ItemStack(Items.GOLD_INGOT, ingots), 1.0F * ingots);
		}

		// Support modded raw_<metal> & <metal>_ore -> <metal>_ingot conventions automatically
		Identifier itemId = Registries.ITEM.getId(stack.getItem());
		String path = itemId.getPath();
		if (path.startsWith("raw_") && !path.endsWith("_block")) {
			String metal = path.substring(4);
			Identifier ingotId = Identifier.of(itemId.getNamespace(), metal + "_ingot");
			if (Registries.ITEM.containsId(ingotId)) {
				Item ingot = Registries.ITEM.get(ingotId);
				if (ingot != Items.AIR) {
					return new SmeltResult(new ItemStack(ingot, count), 0.7F * count);
				}
			}
		} else if (path.endsWith("_ore")) {
			String metal = path.replace("deepslate_", "").replace("nether_", "").replace("_ore", "");
			Identifier ingotId = Identifier.of(itemId.getNamespace(), metal + "_ingot");
			if (Registries.ITEM.containsId(ingotId)) {
				Item ingot = Registries.ITEM.get(ingotId);
				if (ingot != Items.AIR) {
					return new SmeltResult(new ItemStack(ingot, count), 0.7F * count);
				}
			}
		}
		return null;
	}

	private record SmeltResult(ItemStack output, float xp) {
	}

	private static void enqueueNeighbors26(BlockPos center, Set<BlockPos> visited, Queue<BlockPos> queue) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					BlockPos next = center.add(dx, dy, dz);
					if (visited.add(next)) {
						queue.add(next);
					}
				}
			}
		}
	}

	private static boolean hasMatchingNaturalLeavesNearby(ServerWorld world, BlockPos origin, Block logBlock) {
		for (int dx = -4; dx <= 4; dx++) {
			for (int dy = 0; dy <= 16; dy++) {
				for (int dz = -4; dz <= 4; dz++) {
					BlockState s = world.getBlockState(origin.add(dx, dy, dz));
					if (matchesTreeLeaf(logBlock, s)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean matchesTreeLeaf(Block logBlock, BlockState leafState) {
		if (!isNaturalLeaf(leafState)) {
			return false;
		}
		Block leaf = leafState.getBlock();
		if (logBlock == Blocks.OAK_LOG || logBlock == Blocks.OAK_WOOD) {
			return leaf == Blocks.OAK_LEAVES || leaf == Blocks.AZALEA_LEAVES || leaf == Blocks.FLOWERING_AZALEA_LEAVES;
		}
		if (logBlock == Blocks.SPRUCE_LOG || logBlock == Blocks.SPRUCE_WOOD) {
			return leaf == Blocks.SPRUCE_LEAVES;
		}
		if (logBlock == Blocks.BIRCH_LOG || logBlock == Blocks.BIRCH_WOOD) {
			return leaf == Blocks.BIRCH_LEAVES;
		}
		if (logBlock == Blocks.JUNGLE_LOG || logBlock == Blocks.JUNGLE_WOOD) {
			return leaf == Blocks.JUNGLE_LEAVES;
		}
		if (logBlock == Blocks.ACACIA_LOG || logBlock == Blocks.ACACIA_WOOD) {
			return leaf == Blocks.ACACIA_LEAVES;
		}
		if (logBlock == Blocks.DARK_OAK_LOG || logBlock == Blocks.DARK_OAK_WOOD) {
			return leaf == Blocks.DARK_OAK_LEAVES;
		}
		if (logBlock == Blocks.MANGROVE_LOG || logBlock == Blocks.MANGROVE_WOOD || logBlock == Blocks.MANGROVE_ROOTS) {
			return leaf == Blocks.MANGROVE_LEAVES;
		}
		if (logBlock == Blocks.CHERRY_LOG || logBlock == Blocks.CHERRY_WOOD) {
			return leaf == Blocks.CHERRY_LEAVES;
		}
		if (logBlock == Blocks.CRIMSON_STEM || logBlock == Blocks.CRIMSON_HYPHAE) {
			return leaf == Blocks.NETHER_WART_BLOCK || leaf == Blocks.SHROOMLIGHT;
		}
		if (logBlock == Blocks.WARPED_STEM || logBlock == Blocks.WARPED_HYPHAE) {
			return leaf == Blocks.WARPED_WART_BLOCK || leaf == Blocks.SHROOMLIGHT;
		}

		Identifier logId = Registries.BLOCK.getId(logBlock);
		Identifier leafId = Registries.BLOCK.getId(leaf);
		if (!logId.getNamespace().equals(leafId.getNamespace())) {
			return false;
		}
		String species = logId.getPath()
				.replace("_log", "")
				.replace("_wood", "")
				.replace("_stem", "")
				.replace("_hyphae", "");
		return !species.isEmpty() && leafId.getPath().startsWith(species);
	}

	private static boolean isNaturalLeaf(BlockState state) {
		if (state.isOf(Blocks.NETHER_WART_BLOCK) || state.isOf(Blocks.WARPED_WART_BLOCK) || state.isOf(Blocks.SHROOMLIGHT)) {
			return true;
		}
		if (!state.isIn(BlockTags.LEAVES)) {
			return false;
		}
		if (state.contains(LeavesBlock.PERSISTENT) && state.get(LeavesBlock.PERSISTENT)) {
			return false;
		}
		return true;
	}

	private static boolean isTreeLog(BlockState state) {
		return state.isIn(BlockTags.LOGS) || state.isOf(Blocks.MANGROVE_ROOTS);
	}

	private static boolean isValidOreTool(ItemStack tool, BlockState state) {
		if (tool.isEmpty()) return false;
		return tool.isIn(ItemTags.PICKAXES) || tool.isSuitableFor(state);
	}

	private static boolean isOreBlock(BlockState state) {
		Block b = state.getBlock();
		if (b instanceof ExperienceDroppingBlock || b instanceof RedstoneOreBlock
				|| state.isOf(Blocks.ANCIENT_DEBRIS) || state.isOf(Blocks.GILDED_BLACKSTONE)
				|| state.isOf(Blocks.RAW_IRON_BLOCK) || state.isOf(Blocks.RAW_GOLD_BLOCK)
				|| state.isOf(Blocks.RAW_COPPER_BLOCK) || state.isOf(Blocks.AMETHYST_CLUSTER)
				|| state.isOf(Blocks.GLOWSTONE)) {
			return true;
		}
		Identifier id = Registries.BLOCK.getId(b);
		String path = id.getPath();
		return path.endsWith("_ore") || path.startsWith("ore_");
	}

	private static String getOreFamily(Block block) {
		if (block == Blocks.RAW_IRON_BLOCK) return "minecraft:iron_ore";
		if (block == Blocks.RAW_COPPER_BLOCK) return "minecraft:copper_ore";
		if (block == Blocks.RAW_GOLD_BLOCK) return "minecraft:gold_ore";
		Identifier id = Registries.BLOCK.getId(block);
		String path = id.getPath().replace("deepslate_", "").replace("nether_", "");
		return id.getNamespace() + ":" + path;
	}

	private static String getOrePlusKey(Block block) {
		Identifier id = Registries.BLOCK.getId(block);
		String p = id.getPath();
		if (p.contains("coal_ore")) return "coal";
		if (p.contains("copper_ore")) return "copper";
		if (p.contains("iron_ore")) return "iron";
		if (p.equals("nether_gold_ore") || p.equals("gilded_blackstone")) return "nether_gold";
		if (p.contains("gold_ore")) return "gold";
		if (p.contains("redstone_ore")) return "redstone";
		if (p.contains("lapis_ore")) return "lapis";
		if (p.contains("diamond_ore")) return "diamond";
		if (p.contains("emerald_ore")) return "emerald";
		if (p.contains("quartz_ore")) return "quartz";
		if (p.equals("ancient_debris")) return "ancient_debris";
		if (p.equals("amethyst_cluster")) return "amethyst";
		return null;
	}

	private static Item getOreRawDrop(String oreKey) {
		return switch (oreKey) {
			case "coal" -> Items.COAL;
			case "copper" -> Items.RAW_COPPER;
			case "iron" -> Items.RAW_IRON;
			case "gold" -> Items.RAW_GOLD;
			case "nether_gold" -> Items.GOLD_NUGGET;
			case "redstone" -> Items.REDSTONE;
			case "lapis" -> Items.LAPIS_LAZULI;
			case "diamond" -> Items.DIAMOND;
			case "emerald" -> Items.EMERALD;
			case "quartz" -> Items.QUARTZ;
			case "ancient_debris" -> Items.ANCIENT_DEBRIS;
			case "amethyst" -> Items.AMETHYST_SHARD;
			default -> null;
		};
	}
}
