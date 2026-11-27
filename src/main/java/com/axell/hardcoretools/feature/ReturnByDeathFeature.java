package com.axell.hardcoretools.feature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.axell.hardcoretools.HardcoreToolsMod;
import com.axell.hardcoretools.network.HardcoreNetworking;
import com.axell.hardcoretools.state.HardcoreStateManager;
import com.axell.hardcoretools.state.PlayerProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Clearable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class ReturnByDeathFeature {
	// 5.0-Second 3-Act Choreography (100 ticks @ 20 TPS)
	public static final int ACT1_TICKS = 30;            // 0.0s - 1.5s: Death Impact & Locked Fate
	public static final int ACT2_TICKS = 60;            // 1.5s - 4.5s: Fast-Reverse Time Rewind
	public static final int ACT3_TICKS = 10;            // 4.5s - 5.0s: Snap Back to Reality
	public static final int TOTAL_SEQUENCE_TICKS = ACT1_TICKS + ACT2_TICKS + ACT3_TICKS; // 100 ticks (5.0s)
	public static final int GRACE_TICKS = 70;           // 3.5s Chrono Grace invulnerability after snap-back

	private static final long MAX_HISTORY_TICKS = 1280L; // ~64s buffer for 60s max window
	private static final int MAX_BLOCK_JOURNAL = 24000;
	private static final int MAX_ENTITY_DEATHS = 160;
	private static final double BLOCK_TRACK_RADIUS_SQ = 88.0D * 88.0D;
	private static final double ENTITY_TRACK_RADIUS = 56.0D;

	private static boolean restoringWorld = false;

	public record TrajectorySample(
			long tick,
			RegistryKey<World> dimension,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			long timeOfDay
	) {
	}

	public record StateCheckpoint(
			long tick,
			RegistryKey<World> dimension,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			float health,
			int foodLevel,
			float saturation,
			float exhaustion,
			int air,
			int xpLevel,
			float xpProgress,
			int totalXp,
			int selectedSlot,
			List<ItemStack> mainInv,
			List<ItemStack> armorInv,
			List<ItemStack> offHandInv,
			List<StatusEffectInstance> statusEffects,
			long overworldTimeOfDay,
			boolean raining,
			boolean thundering
	) {
	}

	private record BlockUndoEntry(
			long tick,
			RegistryKey<World> dimension,
			BlockPos pos,
			BlockState oldState,
			NbtCompound oldBlockEntityNbt
	) {
	}

	private record EntityTrackSample(
			long tick,
			RegistryKey<World> dimension,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			float health
	) {
	}

	private record EntityDeathRecord(
			long deathTick,
			UUID uuid,
			RegistryKey<World> dimension,
			double x,
			double y,
			double z,
			NbtCompound entityNbt
	) {
	}

	private static final class PlayerHistory {
		final Deque<TrajectorySample> trajectory = new ArrayDeque<>();
		final Deque<StateCheckpoint> checkpoints = new ArrayDeque<>();
		long graceUntilTick = 0L;
	}

	private static final class ActiveRewindSession {
		final UUID playerUuid;
		final long startServerTick;
		final long deathTick;
		final long targetTick;
		final double rewoundSeconds;
		final StateCheckpoint targetCheckpoint;
		final List<TrajectorySample> reversePath; // Ordered from deathTick (index 0) back to targetTick (last)
		final long skyTimeAtDeath;
		final long skyTimeAtTarget;
		long lastAppliedVirtualTick;
		boolean restoredFinalState = false;

		ActiveRewindSession(UUID playerUuid, long startServerTick, long deathTick, long targetTick,
							double rewoundSeconds, StateCheckpoint targetCheckpoint,
							List<TrajectorySample> reversePath, long skyTimeAtDeath, long skyTimeAtTarget) {
			this.playerUuid = playerUuid;
			this.startServerTick = startServerTick;
			this.deathTick = deathTick;
			this.targetTick = targetTick;
			this.rewoundSeconds = rewoundSeconds;
			this.targetCheckpoint = targetCheckpoint;
			this.reversePath = reversePath;
			this.skyTimeAtDeath = skyTimeAtDeath;
			this.skyTimeAtTarget = skyTimeAtTarget;
			this.lastAppliedVirtualTick = deathTick;
		}
	}

	private static final Map<UUID, PlayerHistory> PLAYER_HISTORIES = new HashMap<>();
	private static final Map<UUID, ActiveRewindSession> ACTIVE_REWINDS = new HashMap<>();
	private static final Deque<BlockUndoEntry> BLOCK_JOURNAL = new ArrayDeque<>();
	private static final Map<UUID, Deque<EntityTrackSample>> ENTITY_TRACKS = new HashMap<>();
	private static final Map<UUID, Float> LAST_KNOWN_ENTITY_HP = new HashMap<>();
	private static final Deque<EntityDeathRecord> ENTITY_DEATHS = new ArrayDeque<>();

	private ReturnByDeathFeature() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ReturnByDeathFeature::onServerTick);
		ServerLivingEntityEvents.AFTER_DEATH.register(ReturnByDeathFeature::onLivingEntityDied);
	}

	public static void clearAll() {
		PLAYER_HISTORIES.clear();
		ACTIVE_REWINDS.clear();
		BLOCK_JOURNAL.clear();
		ENTITY_TRACKS.clear();
		LAST_KNOWN_ENTITY_HP.clear();
		ENTITY_DEATHS.clear();
		restoringWorld = false;
	}

	public static boolean isRestoringWorld() {
		return restoringWorld;
	}

	public static boolean isRewinding(UUID playerUuid) {
		return ACTIVE_REWINDS.containsKey(playerUuid);
	}

	public static boolean isProtectedOrRewinding(ServerPlayerEntity player) {
		if (player == null) return false;
		if (ACTIVE_REWINDS.containsKey(player.getUuid())) {
			return true;
		}
		PlayerHistory hist = PLAYER_HISTORIES.get(player.getUuid());
		return hist != null && player.getServer() != null && player.getServer().getTicks() < hist.graceUntilTick;
	}

	public static void captureImmediateBaseline(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null || !player.isAlive()) return;
		long tick = player.getServer().getTicks();
		PlayerHistory hist = PLAYER_HISTORIES.computeIfAbsent(player.getUuid(), k -> new PlayerHistory());
		recordTrajectorySample(player, hist, tick);
		recordStateCheckpoint(player, hist, tick);
	}

	public static void recordBlockChange(ServerWorld world, BlockPos pos, BlockState newState) {
		if (restoringWorld || world == null || pos == null || newState == null) {
			return;
		}
		MinecraftServer server = world.getServer();
		if (server == null || ACTIVE_REWINDS.size() > 0 && isAnyRewindInDimension(world.getRegistryKey())) {
			return;
		}
		if (!hasActiveRbdObserverNear(world, pos)) {
			return;
		}
		BlockState oldState = world.getBlockState(pos);
		if (oldState == newState) {
			return;
		}

		NbtCompound oldBeNbt = null;
		if (oldState.hasBlockEntity()) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be != null) {
				try {
					oldBeNbt = be.createNbtWithIdentifyingData(world.getRegistryManager());
				} catch (Exception ignored) {
				}
			}
		}

		if (BLOCK_JOURNAL.size() >= MAX_BLOCK_JOURNAL) {
			BLOCK_JOURNAL.pollFirst();
		}
		BLOCK_JOURNAL.addLast(new BlockUndoEntry(
				server.getTicks(),
				world.getRegistryKey(),
				pos.toImmutable(),
				oldState,
				oldBeNbt
		));
	}

	public static void recordCreeperPreExplosion(CreeperEntity creeper) {
		if (restoringWorld || creeper == null || !(creeper.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		if (!hasActiveRbdObserverNear(serverWorld, creeper.getBlockPos())) {
			return;
		}
		try {
			NbtCompound nbt = new NbtCompound();
			if (creeper.saveSelfNbt(nbt)) {
				nbt.putFloat("Health", creeper.getMaxHealth());
				nbt.putShort("DeathTime", (short) 0);
				nbt.putShort("HurtTime", (short) 0);
				nbt.putShort("Fuse", (short) 30);
				nbt.putByte("ignited", (byte) 0);
				pushEntityDeathRecord(new EntityDeathRecord(
						serverWorld.getServer().getTicks(),
						creeper.getUuid(),
						serverWorld.getRegistryKey(),
						creeper.getX(),
						creeper.getY(),
						creeper.getZ(),
						nbt
				));
			}
		} catch (Exception ignored) {
		}
	}

	private static void onLivingEntityDied(LivingEntity entity, DamageSource source) {
		if (restoringWorld || entity == null || entity instanceof PlayerEntity || !(entity.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		if (!hasActiveRbdObserverNear(serverWorld, entity.getBlockPos())) {
			return;
		}
		try {
			NbtCompound nbt = new NbtCompound();
			if (entity.saveSelfNbt(nbt)) {
				float restoreHp = LAST_KNOWN_ENTITY_HP.getOrDefault(entity.getUuid(), entity.getMaxHealth());
				if (restoreHp <= 0.5F) {
					restoreHp = entity.getMaxHealth();
				}
				nbt.putFloat("Health", Math.min(entity.getMaxHealth(), restoreHp));
				nbt.putShort("DeathTime", (short) 0);
				nbt.putShort("HurtTime", (short) 0);
				nbt.putShort("Fire", (short) 0);
				pushEntityDeathRecord(new EntityDeathRecord(
						serverWorld.getServer().getTicks(),
						entity.getUuid(),
						serverWorld.getRegistryKey(),
						entity.getX(),
						entity.getY(),
						entity.getZ(),
						nbt
				));
			}
		} catch (Exception ignored) {
		}
	}

	private static void pushEntityDeathRecord(EntityDeathRecord rec) {
		for (EntityDeathRecord existing : ENTITY_DEATHS) {
			if (existing.uuid.equals(rec.uuid) && Math.abs(existing.deathTick - rec.deathTick) <= 5L) {
				return;
			}
		}
		if (ENTITY_DEATHS.size() >= MAX_ENTITY_DEATHS) {
			ENTITY_DEATHS.pollFirst();
		}
		ENTITY_DEATHS.addLast(rec);
	}

	public static boolean tryTriggerReturnByDeath(ServerPlayerEntity player, DamageSource source) {
		if (player == null || player.getServer() == null) {
			return false;
		}
		MinecraftServer server = player.getServer();
		PlayerProfile profile = HardcoreStateManager.getProfile(player);
		if (!profile.returnByDeath) {
			return false;
		}
		if (ACTIVE_REWINDS.containsKey(player.getUuid())) {
			player.setHealth(1.0F);
			return true;
		}

		long nowTick = server.getTicks();
		if (nowTick < profile.returnByDeathReadyTick) {
			return false;
		}

		PlayerHistory hist = PLAYER_HISTORIES.computeIfAbsent(player.getUuid(), k -> new PlayerHistory());
		if (hist.checkpoints.isEmpty()) {
			// Fallback safety if enabled moments before lethal blow
			return false;
		}

		long desiredBackTicks = Math.max(20L, profile.returnByDeathSeconds * 20L);
		long desiredTargetTick = nowTick - desiredBackTicks;

		StateCheckpoint chosenCheckpoint = hist.checkpoints.peekFirst();
		for (StateCheckpoint cp : hist.checkpoints) {
			if (cp.tick <= desiredTargetTick) {
				chosenCheckpoint = cp;
			} else {
				break;
			}
		}
		if (chosenCheckpoint == null) {
			return false;
		}

		long actualTargetTick = chosenCheckpoint.tick;
		double rewoundSec = Math.max(0.5D, Math.round(((nowTick - actualTargetTick) / 20.0D) * 10.0D) / 10.0D);

		// Build reverse trajectory from death moment (index 0) back to actualTargetTick (last index)
		ServerWorld overworld = server.getOverworld();
		long skyDeath = overworld != null ? overworld.getTimeOfDay() : 0L;
		List<TrajectorySample> reversePath = new ArrayList<>();
		reversePath.add(new TrajectorySample(
				nowTick,
				player.getServerWorld().getRegistryKey(),
				player.getX(),
				player.getY(),
				player.getZ(),
				player.getYaw(),
				player.getPitch(),
				skyDeath
		));

		Iterator<TrajectorySample> descIter = hist.trajectory.descendingIterator();
		while (descIter.hasNext()) {
			TrajectorySample s = descIter.next();
			if (s.tick >= actualTargetTick && s.tick < nowTick) {
				reversePath.add(s);
			}
		}
		reversePath.add(new TrajectorySample(
				actualTargetTick,
				chosenCheckpoint.dimension,
				chosenCheckpoint.x,
				chosenCheckpoint.y,
				chosenCheckpoint.z,
				chosenCheckpoint.yaw,
				chosenCheckpoint.pitch,
				chosenCheckpoint.overworldTimeOfDay
		));

		// Freeze player on the brink of death (1.0F HP so vanilla DeathScreen & Hardcore Spectator lock NEVER fire!)
		player.closeHandledScreen();
		player.setHealth(1.0F);
		player.setVelocity(0.0D, 0.0D, 0.0D);
		player.velocityModified = true;
		player.setFireTicks(0);
		player.fallDistance = 0.0F;

		ActiveRewindSession session = new ActiveRewindSession(
				player.getUuid(),
				nowTick,
				nowTick,
				actualTargetTick,
				rewoundSec,
				chosenCheckpoint,
				reversePath,
				skyDeath,
				chosenCheckpoint.overworldTimeOfDay
		);
		ACTIVE_REWINDS.put(player.getUuid(), session);

		String deathMsg;
		try {
			deathMsg = source.getDeathMessage(player).getString();
		} catch (Exception e) {
			deathMsg = player.getName().getString() + " met a fatal end";
		}

		String payloadJson = buildRewindPayloadJson(
				true,
				deathMsg,
				player.getScore(),
				server.getSaveProperties().isHardcore(),
				rewoundSec,
				reversePath
		);
		HardcoreNetworking.sendRewindEvent(player, payloadJson);
		HardcoreToolsMod.LOGGER.info("Return by Death triggered for {} (rewinding {}s)", player.getName().getString(), rewoundSec);
		return true;
	}

	private static String buildRewindPayloadJson(boolean active, String deathMessage, int score, boolean hardcore,
												 double rewoundSec, List<TrajectorySample> reversePath) {
		JsonObject root = new JsonObject();
		root.addProperty("active", active);
		root.addProperty("durationMs", TOTAL_SEQUENCE_TICKS * 50L); // 5000ms
		root.addProperty("act1Ms", ACT1_TICKS * 50L);               // 1500ms
		root.addProperty("act2Ms", (ACT1_TICKS + ACT2_TICKS) * 50L);// 4500ms
		root.addProperty("deathMessage", deathMessage);
		root.addProperty("score", score);
		root.addProperty("worldHardcore", hardcore);
		root.addProperty("rewoundSeconds", rewoundSec);

		JsonArray framesArr = new JsonArray();
		if (reversePath != null && !reversePath.isEmpty()) {
			long startTick = reversePath.get(0).tick;
			long endTick = reversePath.get(reversePath.size() - 1).tick;
			long span = Math.max(1L, startTick - endTick);

			// Downsample to at most ~90 keyframes so packet stays ultra-compact (< 5 KB)
			int total = reversePath.size();
			int step = Math.max(1, total / 85);
			for (int i = 0; i < total; i += step) {
				TrajectorySample s = reversePath.get(i);
				framesArr.add(serializeFrame(s, startTick, span));
			}
			if ((total - 1) % step != 0) {
				framesArr.add(serializeFrame(reversePath.get(total - 1), startTick, span));
			}
		}
		root.add("frames", framesArr);
		return root.toString();
	}

	private static JsonObject serializeFrame(TrajectorySample s, long startTick, long span) {
		JsonObject fo = new JsonObject();
		double rel = Math.max(0.0D, Math.min(1.0D, (startTick - s.tick) / (double) span));
		fo.addProperty("p", Math.round(rel * 10000.0D) / 10000.0D);
		fo.addProperty("dim", s.dimension.getValue().toString());
		fo.addProperty("x", Math.round(s.x * 1000.0D) / 1000.0D);
		fo.addProperty("y", Math.round(s.y * 1000.0D) / 1000.0D);
		fo.addProperty("z", Math.round(s.z * 1000.0D) / 1000.0D);
		fo.addProperty("yaw", Math.round(s.yaw * 100.0F) / 100.0F);
		fo.addProperty("pitch", Math.round(s.pitch * 100.0F) / 100.0F);
		fo.addProperty("sky", s.timeOfDay);
		return fo;
	}

	private static void onServerTick(MinecraftServer server) {
		long tick = server.getTicks();

		// 1. Advance active 5.0-second Return by Death rewinds
		if (!ACTIVE_REWINDS.isEmpty()) {
			tickActiveRewinds(server, tick);
		}

		// 2. Record history for players with Return by Death enabled (who are not currently rewinding)
		List<ServerPlayerEntity> activeObservers = new ArrayList<>();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!player.isAlive() || player.isSpectator()) continue;
			PlayerProfile profile = HardcoreStateManager.getProfile(player);
			PlayerHistory hist = PLAYER_HISTORIES.computeIfAbsent(player.getUuid(), k -> new PlayerHistory());

			if (tick < hist.graceUntilTick) {
				player.setFireTicks(0);
				player.fallDistance = 0.0F;
			}

			if (!profile.returnByDeath || ACTIVE_REWINDS.containsKey(player.getUuid())) {
				continue;
			}
			activeObservers.add(player);

			if (tick % 2L == 0L || hist.trajectory.isEmpty()) {
				recordTrajectorySample(player, hist, tick);
			}
			if (tick % 10L == 0L || hist.checkpoints.isEmpty()) {
				recordStateCheckpoint(player, hist, tick);
			}

			// Prune player history older than MAX_HISTORY_TICKS
			long minKeepTick = tick - MAX_HISTORY_TICKS;
			while (hist.trajectory.size() > 2 && hist.trajectory.peekFirst().tick < minKeepTick) {
				hist.trajectory.pollFirst();
			}
			while (hist.checkpoints.size() > 2 && hist.checkpoints.peekFirst().tick < minKeepTick) {
				hist.checkpoints.pollFirst();
			}
		}

		// 3. Track nearby living entities every 6 ticks & prune world journals every 20 ticks
		if (!activeObservers.isEmpty() && tick % 6L == 0L) {
			recordNearbyEntities(activeObservers, tick);
		}
		if (tick % 20L == 0L) {
			pruneWorldJournals(tick - MAX_HISTORY_TICKS);
		}
	}

	private static void tickActiveRewinds(MinecraftServer server, long nowTick) {
		Iterator<Map.Entry<UUID, ActiveRewindSession>> it = ACTIVE_REWINDS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, ActiveRewindSession> entry = it.next();
			ActiveRewindSession session = entry.getValue();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.playerUuid);
			if (player == null) {
				it.remove();
				continue;
			}

			long elapsed = nowTick - session.startServerTick;
			player.setFireTicks(0);
			player.fallDistance = 0.0F;

			if (elapsed <= ACT1_TICKS) {
				// ==================== BABAK 1 (0.0s - 1.5s): FROZEN DEATH MOMENT ====================
				player.setHealth(1.0F);
				player.setVelocity(0.0D, 0.0D, 0.0D);
				player.velocityModified = true;
				freezeNearbyHostiles(player, 32.0D);
			} else if (elapsed < ACT1_TICKS + ACT2_TICKS) {
				// ==================== BABAK 2 (1.5s - 4.5s): FAST-REVERSE TIME REWIND ====================
				player.setHealth(Math.max(1.0F, player.getHealth()));
				double rawP = (elapsed - ACT1_TICKS) / (double) ACT2_TICKS;
				double easedP = easeInOutSine(rawP);
				long span = Math.max(1L, session.deathTick - session.targetTick);
				long currentVirtualTick = Math.round(session.deathTick - easedP * span);

				// Rewind Overworld Sky Time smoothly
				ServerWorld overworld = server.getOverworld();
				if (overworld != null) {
					long interpSky = Math.round(session.skyTimeAtDeath + (session.skyTimeAtTarget - session.skyTimeAtDeath) * easedP);
					if (interpSky >= 0L) {
						overworld.setTimeOfDay(interpSky);
					}
				}

				// Undo blocks, respawn dead/exploded mobs, discard post-born drops/projectiles, & rewind mob positions
				rewindWorldSlice(server, player, currentVirtualTick, session.deathTick);
				session.lastAppliedVirtualTick = currentVirtualTick;

				// Move server player every 6 ticks (or immediately on dimension cross) so chunks stay loaded
				// while letting Client's 240 FPS GameRendererMixin glide smoothly without packet jitter!
				TrajectorySample sample = sampleReversePath(session.reversePath, currentVirtualTick);
				if (sample != null) {
					boolean crossDim = !player.getServerWorld().getRegistryKey().equals(sample.dimension);
					if (crossDim || elapsed % 6L == 0L) {
						ServerWorld destWorld = server.getWorld(sample.dimension);
						if (destWorld != null) {
							player.teleport(destWorld, sample.x, sample.y, sample.z, sample.yaw, sample.pitch);
							player.setVelocity(0.0D, 0.0D, 0.0D);
						}
					}
				}
				freezeNearbyHostiles(player, 36.0D);
			} else {
				// ==================== BABAK 3 (4.5s - 5.0s): SNAP BACK TO REALITY ====================
				if (!session.restoredFinalState) {
					session.restoredFinalState = true;
					rewindWorldSlice(server, player, session.targetTick, session.deathTick);
					restorePlayerFullCheckpoint(server, player, session.targetCheckpoint);

					PlayerProfile profile = HardcoreStateManager.getProfile(player);
					if (profile.returnByDeathCooldown > 0) {
						profile.returnByDeathReadyTick = nowTick + (profile.returnByDeathCooldown * 20L);
					} else {
						profile.returnByDeathReadyTick = 0L;
					}

					PlayerHistory hist = PLAYER_HISTORIES.computeIfAbsent(player.getUuid(), k -> new PlayerHistory());
					hist.graceUntilTick = nowTick + GRACE_TICKS;
					// Purge post-target history so timeline branches cleanly from -60s!
					while (!hist.trajectory.isEmpty() && hist.trajectory.peekLast().tick > session.targetTick) {
						hist.trajectory.pollLast();
					}
					while (!hist.checkpoints.isEmpty() && hist.checkpoints.peekLast().tick > session.targetTick) {
						hist.checkpoints.pollLast();
					}
					captureImmediateBaseline(player);
				}

				if (elapsed >= TOTAL_SEQUENCE_TICKS) {
					it.remove();
					HardcoreNetworking.sendSyncState(
							player,
							"⏪ Returned by Death (-" + session.rewoundSeconds + "s)",
							true
					);
				}
			}
		}
	}

	private static void rewindWorldSlice(MinecraftServer server, ServerPlayerEntity player, long virtualTick, long deathTick) {
		restoringWorld = true;
		try {
			// 1. Undo Block Journal entries newer than virtualTick in strict LIFO order
			int particlesSpawned = 0;
			while (!BLOCK_JOURNAL.isEmpty() && BLOCK_JOURNAL.peekLast().tick > virtualTick) {
				BlockUndoEntry b = BLOCK_JOURNAL.pollLast();
				ServerWorld w = server.getWorld(b.dimension);
				if (w == null) continue;

				BlockEntity currentBe = w.getBlockEntity(b.pos);
				if (currentBe != null) {
					Clearable.clear(currentBe);
					w.removeBlockEntity(b.pos);
				}
				w.setBlockState(b.pos, b.oldState, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
				if (b.oldBlockEntityNbt != null && b.oldState.hasBlockEntity()) {
					BlockEntity restoredBe = w.getBlockEntity(b.pos);
					if (restoredBe != null) {
						try {
							restoredBe.read(b.oldBlockEntityNbt, w.getRegistryManager());
							restoredBe.markDirty();
						} catch (Exception ignored) {
						}
					}
				}
				if (particlesSpawned < 18 && w == player.getServerWorld()) {
					particlesSpawned++;
					w.spawnParticles(
							ParticleTypes.REVERSE_PORTAL,
							b.pos.getX() + 0.5D,
							b.pos.getY() + 0.5D,
							b.pos.getZ() + 0.5D,
							3,
							0.25D,
							0.25D,
							0.25D,
							0.02D
					);
				}
			}

			// 2. Respawn slain / exploded entities whose deathTick > virtualTick
			while (!ENTITY_DEATHS.isEmpty() && ENTITY_DEATHS.peekLast().deathTick > virtualTick) {
				EntityDeathRecord rec = ENTITY_DEATHS.pollLast();
				ServerWorld w = server.getWorld(rec.dimension);
				if (w == null) continue;
				Entity existing = w.getEntity(rec.uuid);
				if (existing != null && !(existing instanceof PlayerEntity)) {
					existing.discard();
				}
				try {
					Entity loaded = EntityType.loadEntityWithPassengers(rec.entityNbt, w, e -> {
						e.refreshPositionAndAngles(rec.x, rec.y, rec.z, e.getYaw(), e.getPitch());
						return e;
					});
					if (loaded != null) {
						if (loaded instanceof CreeperEntity creeper) {
							creeper.setFuseSpeed(-1);
						}
						if (loaded instanceof MobEntity mob) {
							mob.setTarget(null);
						}
						w.spawnEntity(loaded);
						w.spawnParticles(ParticleTypes.PORTAL, rec.x, rec.y + 0.8D, rec.z, 12, 0.3D, 0.4D, 0.3D, 0.05D);
					}
				} catch (Exception ignored) {
				}
			}

			// 3. Erase newly spawned drops/XP/projectiles/TNT/mobs born after virtualTick & rewind tracked mobs
			ServerWorld pw = player.getServerWorld();
			long serverNow = server.getTicks();
			Box area = player.getBoundingBox().expand(72.0D);
			for (Entity ent : pw.getOtherEntities(player, area, e -> e != null && !(e instanceof PlayerEntity) && !(e instanceof EnderDragonPart))) {
				long birthTick = serverNow - ent.age;
				boolean transientType = (ent instanceof ItemEntity)
						|| (ent instanceof ExperienceOrbEntity)
						|| (ent instanceof ProjectileEntity)
						|| (ent instanceof TntEntity);
				if (birthTick > virtualTick && (transientType || !ENTITY_TRACKS.containsKey(ent.getUuid()))) {
					ent.discard();
					continue;
				}

				Deque<EntityTrackSample> track = ENTITY_TRACKS.get(ent.getUuid());
				if (track != null && !track.isEmpty()) {
					EntityTrackSample targetSample = null;
					Iterator<EntityTrackSample> desc = track.descendingIterator();
					while (desc.hasNext()) {
						EntityTrackSample s = desc.next();
						targetSample = s;
						if (s.tick <= virtualTick) {
							break;
						}
					}
					if (targetSample != null && targetSample.dimension.equals(pw.getRegistryKey())) {
						ent.setPosition(targetSample.x, targetSample.y, targetSample.z);
						ent.setYaw(targetSample.yaw);
						ent.setPitch(targetSample.pitch);
						ent.setVelocity(0.0D, 0.0D, 0.0D);
						if (ent instanceof LivingEntity le && le.isAlive()) {
							le.setHealth(Math.min(le.getMaxHealth(), Math.max(1.0F, targetSample.health)));
						}
						if (ent instanceof CreeperEntity cr) {
							cr.setFuseSpeed(-1);
						}
					}
				}
			}
		} finally {
			restoringWorld = false;
		}
	}

	private static void restorePlayerFullCheckpoint(MinecraftServer server, ServerPlayerEntity player, StateCheckpoint cp) {
		restoringWorld = true;
		try {
			ServerWorld destWorld = server.getWorld(cp.dimension);
			if (destWorld == null) {
				destWorld = server.getOverworld();
			}

			// Ensure safe footing if the -60s position was already below world bottom (e.g. prolonged End void fall)
			double safeX = cp.x;
			double safeY = cp.y;
			double safeZ = cp.z;
			if (safeY < destWorld.getBottomY() + 1) {
				safeY = Math.max(destWorld.getBottomY() + 8, 64.0D);
				BlockPos platCenter = BlockPos.ofFloored(safeX, safeY - 1, safeZ);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos padPos = platCenter.add(dx, 0, dz);
						if (destWorld.getBlockState(padPos).isAir()) {
							destWorld.setBlockState(padPos, Blocks.OBSIDIAN.getDefaultState());
						}
					}
				}
			}

			player.teleport(destWorld, safeX, safeY, safeZ, cp.yaw, cp.pitch);
			player.setVelocity(0.0D, 0.0D, 0.0D);
			player.velocityModified = true;
			player.setFireTicks(0);
			player.fallDistance = 0.0F;

			// Restore exact Inventory (Main 36 + Armor 4 + Offhand 1 + Selected Hotbar Slot)
			PlayerInventory inv = player.getInventory();
			for (int i = 0; i < inv.main.size(); i++) {
				inv.main.set(i, i < cp.mainInv.size() ? cp.mainInv.get(i).copy() : ItemStack.EMPTY);
			}
			for (int i = 0; i < inv.armor.size(); i++) {
				inv.armor.set(i, i < cp.armorInv.size() ? cp.armorInv.get(i).copy() : ItemStack.EMPTY);
			}
			for (int i = 0; i < inv.offHand.size(); i++) {
				inv.offHand.set(i, i < cp.offHandInv.size() ? cp.offHandInv.get(i).copy() : ItemStack.EMPTY);
			}
			if (cp.selectedSlot >= 0 && cp.selectedSlot < 9) {
				inv.selectedSlot = cp.selectedSlot;
			}
			player.playerScreenHandler.sendContentUpdates();

			// Restore Vitals, Hunger & Air (guarantee at least 2.0 hearts so player never re-dies on arrival)
			player.setHealth(Math.min(player.getMaxHealth(), Math.max(4.0F, cp.health)));
			HungerManager hm = player.getHungerManager();
			hm.setFoodLevel(cp.foodLevel);
			hm.setSaturationLevel(cp.saturation);
			hm.setExhaustion(cp.exhaustion);
			player.setAir(Math.max(60, cp.air));

			// Restore XP accurately
			player.setExperienceLevel(cp.xpLevel);
			player.experienceProgress = cp.xpProgress;
			player.totalExperience = cp.totalXp;

			// Restore Status Effects + 3.5s Chrono Grace Resistance
			player.clearStatusEffects();
			for (StatusEffectInstance inst : cp.statusEffects) {
				player.addStatusEffect(new StatusEffectInstance(inst));
			}
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, GRACE_TICKS, 3, false, false, true));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, GRACE_TICKS, 0, false, false, false));

			// Restore Overworld Sky Time & Weather
			ServerWorld overworld = server.getOverworld();
			if (overworld != null) {
				if (cp.overworldTimeOfDay >= 0L) {
					overworld.setTimeOfDay(cp.overworldTimeOfDay);
				}
				if (!cp.raining && !cp.thundering) {
					overworld.setWeather(6000, 0, false, false);
				} else {
					overworld.setWeather(0, 6000, cp.raining, cp.thundering);
				}
			}

			destWorld.spawnParticles(
					ParticleTypes.REVERSE_PORTAL,
					safeX,
					safeY + 1.0D,
					safeZ,
					42,
					0.45D,
					0.75D,
					0.45D,
					0.12D
			);
		} finally {
			restoringWorld = false;
		}
	}

	private static void recordTrajectorySample(ServerPlayerEntity player, PlayerHistory hist, long tick) {
		ServerWorld overworld = player.getServer().getOverworld();
		long sky = overworld != null ? overworld.getTimeOfDay() : 0L;
		hist.trajectory.addLast(new TrajectorySample(
				tick,
				player.getServerWorld().getRegistryKey(),
				player.getX(),
				player.getY(),
				player.getZ(),
				player.getYaw(),
				player.getPitch(),
				sky
		));
	}

	private static void recordStateCheckpoint(ServerPlayerEntity player, PlayerHistory hist, long tick) {
		PlayerInventory inv = player.getInventory();
		List<ItemStack> mainCopy = new ArrayList<>(inv.main.size());
		for (ItemStack s : inv.main) {
			mainCopy.add(s.isEmpty() ? ItemStack.EMPTY : s.copy());
		}
		List<ItemStack> armorCopy = new ArrayList<>(inv.armor.size());
		for (ItemStack s : inv.armor) {
			armorCopy.add(s.isEmpty() ? ItemStack.EMPTY : s.copy());
		}
		List<ItemStack> offCopy = new ArrayList<>(inv.offHand.size());
		for (ItemStack s : inv.offHand) {
			offCopy.add(s.isEmpty() ? ItemStack.EMPTY : s.copy());
		}
		List<StatusEffectInstance> effectsCopy = new ArrayList<>();
		for (StatusEffectInstance inst : player.getStatusEffects()) {
			effectsCopy.add(new StatusEffectInstance(inst));
		}

		ServerWorld overworld = player.getServer().getOverworld();
		long sky = overworld != null ? overworld.getTimeOfDay() : 0L;
		boolean rain = overworld != null && overworld.isRaining();
		boolean thunder = overworld != null && overworld.isThundering();
		HungerManager hm = player.getHungerManager();

		hist.checkpoints.addLast(new StateCheckpoint(
				tick,
				player.getServerWorld().getRegistryKey(),
				player.getX(),
				player.getY(),
				player.getZ(),
				player.getYaw(),
				player.getPitch(),
				player.getHealth(),
				hm.getFoodLevel(),
				hm.getSaturationLevel(),
				hm.getExhaustion(),
				player.getAir(),
				player.experienceLevel,
				player.experienceProgress,
				player.totalExperience,
				inv.selectedSlot,
				mainCopy,
				armorCopy,
				offCopy,
				effectsCopy,
				sky,
				rain,
				thunder
		));
	}

	private static void recordNearbyEntities(List<ServerPlayerEntity> observers, long tick) {
		for (ServerPlayerEntity player : observers) {
			ServerWorld w = player.getServerWorld();
			Box box = player.getBoundingBox().expand(ENTITY_TRACK_RADIUS);
			for (LivingEntity le : w.getEntitiesByClass(LivingEntity.class, box, e -> e != null && e.isAlive() && !(e instanceof PlayerEntity))) {
				LAST_KNOWN_ENTITY_HP.put(le.getUuid(), le.getHealth());
				Deque<EntityTrackSample> dq = ENTITY_TRACKS.computeIfAbsent(le.getUuid(), k -> new ArrayDeque<>());
				dq.addLast(new EntityTrackSample(
						tick,
						w.getRegistryKey(),
						le.getX(),
						le.getY(),
						le.getZ(),
						le.getYaw(),
						le.getPitch(),
						le.getHealth()
				));
				while (dq.size() > 220) {
					dq.pollFirst();
				}
			}
		}
	}

	private static void pruneWorldJournals(long minKeepTick) {
		while (!BLOCK_JOURNAL.isEmpty() && BLOCK_JOURNAL.peekFirst().tick < minKeepTick) {
			BLOCK_JOURNAL.pollFirst();
		}
		while (!ENTITY_DEATHS.isEmpty() && ENTITY_DEATHS.peekFirst().deathTick < minKeepTick) {
			EntityDeathRecord removed = ENTITY_DEATHS.pollFirst();
			if (removed != null) {
				LAST_KNOWN_ENTITY_HP.remove(removed.uuid);
			}
		}
		Iterator<Map.Entry<UUID, Deque<EntityTrackSample>>> it = ENTITY_TRACKS.entrySet().iterator();
		while (it.hasNext()) {
			Deque<EntityTrackSample> dq = it.next().getValue();
			while (!dq.isEmpty() && dq.peekFirst().tick < minKeepTick) {
				dq.pollFirst();
			}
			if (dq.isEmpty()) {
				it.remove();
			}
		}
	}

	private static void freezeNearbyHostiles(ServerPlayerEntity player, double radius) {
		ServerWorld w = player.getServerWorld();
		Box box = player.getBoundingBox().expand(radius);
		for (MobEntity mob : w.getEntitiesByClass(MobEntity.class, box, Entity::isAlive)) {
			mob.getNavigation().stop();
			mob.setTarget(null);
			mob.setVelocity(0.0D, Math.min(0.0D, mob.getVelocity().y), 0.0D);
			if (mob instanceof CreeperEntity creeper) {
				creeper.setFuseSpeed(-1);
			}
		}
	}

	private static TrajectorySample sampleReversePath(List<TrajectorySample> reversePath, long virtualTick) {
		if (reversePath == null || reversePath.isEmpty()) return null;
		for (TrajectorySample s : reversePath) {
			if (s.tick <= virtualTick) {
				return s;
			}
		}
		return reversePath.get(reversePath.size() - 1);
	}

	private static boolean hasActiveRbdObserverNear(ServerWorld world, BlockPos pos) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!player.isAlive() || ACTIVE_REWINDS.containsKey(player.getUuid())) continue;
			PlayerProfile profile = HardcoreStateManager.getProfile(player);
			if (profile.returnByDeath && player.squaredDistanceTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= BLOCK_TRACK_RADIUS_SQ) {
				return true;
			}
		}
		return false;
	}

	private static boolean isAnyRewindInDimension(RegistryKey<World> dimKey) {
		for (ActiveRewindSession s : ACTIVE_REWINDS.values()) {
			if (s.targetCheckpoint.dimension.equals(dimKey)) {
				return true;
			}
		}
		return false;
	}

	public static double easeInOutSine(double t) {
		double c = Math.max(0.0D, Math.min(1.0D, t));
		return -(Math.cos(Math.PI * c) - 1.0D) / 2.0D;
	}
}
