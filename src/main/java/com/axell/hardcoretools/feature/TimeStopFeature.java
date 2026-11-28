package com.axell.hardcoretools.feature;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.axell.hardcoretools.network.HardcoreNetworking;
import com.axell.hardcoretools.state.HardcoreStateManager;
import com.axell.hardcoretools.state.PlayerProfile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Chrono Time Stop ("Hentikan Waktu") Server Controller:
 * - Freezes 100% of world ticks (entities, AI, projectiles, blocks, fluids, sky) via ServerTickManager
 *   while allowing the player to move, jump, aim, and attack freely.
 * - Intercepts and stacks all damage + knockback dealt to frozen entities without killing or moving them
 *   until Time Stop is toggled off, then unleashes the entire accumulated damage burst simultaneously.
 */
public final class TimeStopFeature {
	public record TargetKey(RegistryKey<World> dimension, UUID entityUuid) {
	}

	public static final class DeferredHitEntry {
		public float totalDamage = 0.0F;
		public int hitCount = 0;
		public UUID lastAttackerUuid = null;
		public double kbX = 0.0D;
		public double kbY = 0.0D;
		public double kbZ = 0.0D;
	}

	private static boolean timeStopped = false;
	private static UUID casterUuid = null;
	private static boolean flushingDeferredDamage = false;
	private static long postResumeGraceUntilTick = 0L;
	private static final Map<TargetKey, DeferredHitEntry> DEFERRED_HITS = new LinkedHashMap<>();

	private TimeStopFeature() {
	}

	public static boolean isTimeStopped(MinecraftServer server) {
		return timeStopped && server != null;
	}

	public static UUID getCasterUuid() {
		return casterUuid;
	}

	public static boolean isFlushingDeferredDamage() {
		return flushingDeferredDamage;
	}

	public static boolean isInPostResumeGraceWindow(MinecraftServer server, DamageSource source) {
		if (server == null || server.getTicks() > postResumeGraceUntilTick) {
			return false;
		}
		return source != null && (source.getSource() instanceof ProjectileEntity || source.getAttacker() instanceof ServerPlayerEntity);
	}

	public static int getTotalStackedHits() {
		int sum = 0;
		for (DeferredHitEntry e : DEFERRED_HITS.values()) {
			sum += e.hitCount;
		}
		return sum;
	}

	public static float getTotalStackedDamage() {
		float sum = 0.0F;
		for (DeferredHitEntry e : DEFERRED_HITS.values()) {
			sum += e.totalDamage;
		}
		return Math.round(sum * 10.0F) / 10.0F;
	}

	public static void toggleTimeStopFromKeybind(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) {
			return;
		}
		MinecraftServer server = player.getServer();
		PlayerProfile profile = HardcoreStateManager.getProfile(player);
		if (!profile.timeStopEnabled) {
			if (timeStopped) {
				resumeTimeStop(server, player, true);
			} else {
				HardcoreNetworking.sendSyncState(player, "⏸ Enable Time Stop first in Protection menu!", false);
			}
			return;
		}

		if (timeStopped) {
			resumeTimeStop(server, player, true);
		} else {
			activateTimeStop(server, player);
		}
	}

	public static void activateTimeStop(MinecraftServer server, ServerPlayerEntity player) {
		if (server == null || player == null) {
			return;
		}
		if (ReturnByDeathFeature.isRewinding(player.getUuid())) {
			return;
		}
		timeStopped = true;
		casterUuid = player.getUuid();
		DEFERRED_HITS.clear();

		server.getTickManager().setFrozen(true);

		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			HardcoreNetworking.sendTimeStopEvent(p, true, "freeze", 0, 0.0F);
			HardcoreNetworking.sendSyncState(p, "", true);
		}
	}

	public static void resumeTimeStop(MinecraftServer server, ServerPlayerEntity triggerPlayer, boolean flushHits) {
		if (server == null) {
			return;
		}
		boolean wasStopped = timeStopped;
		timeStopped = false;
		casterUuid = null;
		postResumeGraceUntilTick = server.getTicks() + 25L;

		if (server.getTickManager().isFrozen()) {
			server.getTickManager().setFrozen(false);
		}

		int flushedHits = getTotalStackedHits();
		float flushedDmg = getTotalStackedDamage();

		Map<TargetKey, DeferredHitEntry> pending = new LinkedHashMap<>(DEFERRED_HITS);
		DEFERRED_HITS.clear();

		if (wasStopped) {
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				HardcoreNetworking.sendTimeStopEvent(p, false, "resume", flushedHits, flushedDmg);
				HardcoreNetworking.sendSyncState(p, "", true);
			}
		}

		if (flushHits && !pending.isEmpty()) {
			flushingDeferredDamage = true;
			try {
				for (Map.Entry<TargetKey, DeferredHitEntry> mapEntry : pending.entrySet()) {
					TargetKey key = mapEntry.getKey();
					DeferredHitEntry hit = mapEntry.getValue();
					if (hit.totalDamage <= 0.0F) {
						continue;
					}
					ServerWorld world = server.getWorld(key.dimension());
					if (world == null) {
						continue;
					}
					Entity ent = world.getEntity(key.entityUuid());
					if (!(ent instanceof LivingEntity target) || !target.isAlive() || target.isRemoved()) {
						continue;
					}

					target.timeUntilRegen = 0;
					target.hurtTime = 0;

					ServerPlayerEntity attacker = hit.lastAttackerUuid != null
							? server.getPlayerManager().getPlayer(hit.lastAttackerUuid)
							: triggerPlayer;
					DamageSource burstSource = (attacker != null)
							? world.getDamageSources().playerAttack(attacker)
							: world.getDamageSources().generic();

					target.damage(burstSource, hit.totalDamage);

					if (Math.abs(hit.kbX) > 1.0E-4D || Math.abs(hit.kbY) > 1.0E-4D || Math.abs(hit.kbZ) > 1.0E-4D) {
						target.addVelocity(hit.kbX, hit.kbY, hit.kbZ);
						target.velocityModified = true;
					}

					double cx = target.getX();
					double cy = target.getY() + target.getHeight() * 0.55D;
					double cz = target.getZ();
					world.spawnParticles(ParticleTypes.SWEEP_ATTACK, cx, cy, cz, 2, 0.25D, 0.2D, 0.25D, 0.0D);
					world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, cx, cy, cz,
							Math.min(18, Math.max(3, hit.hitCount * 2)), 0.32D, 0.35D, 0.32D, 0.12D);
					world.spawnParticles(ParticleTypes.CRIT, cx, cy, cz,
							Math.min(24, Math.max(6, hit.hitCount * 3)), 0.35D, 0.35D, 0.35D, 0.28D);
				}
			} finally {
				flushingDeferredDamage = false;
			}
		}
	}

	public static boolean deferDamage(LivingEntity target, DamageSource source, float amount) {
		if (!timeStopped || flushingDeferredDamage || target == null || source == null || amount <= 0.0F) {
			return false;
		}
		if (target.getWorld().isClient || !(target.getWorld() instanceof ServerWorld world)) {
			return false;
		}
		if (casterUuid != null && target.getUuid().equals(casterUuid)) {
			return false;
		}
		if (target.isDead() || target.isRemoved() || target.isInvulnerableTo(source)) {
			return false;
		}

		TargetKey key = new TargetKey(world.getRegistryKey(), target.getUuid());
		DeferredHitEntry entry = DEFERRED_HITS.computeIfAbsent(key, k -> new DeferredHitEntry());
		entry.totalDamage += amount;
		entry.hitCount += 1;

		ServerPlayerEntity attackerPlayer = null;
		if (source.getAttacker() instanceof ServerPlayerEntity sp) {
			attackerPlayer = sp;
			entry.lastAttackerUuid = sp.getUuid();

			double dx = target.getX() - sp.getX();
			double dz = target.getZ() - sp.getZ();
			double len = Math.hypot(dx, dz);
			if (len > 1.0E-4D) {
				entry.kbX = MathHelper.clamp(entry.kbX + (dx / len) * 0.24D, -2.4D, 2.4D);
				entry.kbZ = MathHelper.clamp(entry.kbZ + (dz / len) * 0.24D, -2.4D, 2.4D);
				entry.kbY = MathHelper.clamp(Math.max(entry.kbY, 0.18D) + 0.035D, 0.0D, 0.62D);
			}
		}

		// Clear invulnerability frames so rapid consecutive hits during frozen time ALWAYS register
		target.timeUntilRegen = 0;
		target.hurtTime = 0;

		// Spawn frozen impact sparks that hang motionless in mid-air until time resumes!
		double px = target.getX();
		double py = target.getY() + target.getHeight() * 0.55D;
		double pz = target.getZ();
		world.spawnParticles(ParticleTypes.CRIT, px, py, pz, 5, 0.26D, 0.28D, 0.26D, 0.02D);
		world.spawnParticles(ParticleTypes.END_ROD, px, py, pz, 2, 0.22D, 0.25D, 0.22D, 0.005D);

		if (attackerPlayer != null) {
			HardcoreNetworking.sendTimeStopEvent(
					attackerPlayer,
					true,
					"hit",
					getTotalStackedHits(),
					getTotalStackedDamage()
			);
		}
		return true;
	}

	public static boolean deferKnockback(LivingEntity target, double strength, double x, double z) {
		if (!timeStopped || flushingDeferredDamage || target == null || target.getWorld().isClient) {
			return false;
		}
		if (casterUuid != null && target.getUuid().equals(casterUuid)) {
			return false;
		}
		if (!(target.getWorld() instanceof ServerWorld world)) {
			return false;
		}
		TargetKey key = new TargetKey(world.getRegistryKey(), target.getUuid());
		DeferredHitEntry entry = DEFERRED_HITS.get(key);
		if (entry != null && strength > 0.0D) {
			double len = Math.hypot(x, z);
			if (len > 1.0E-4D) {
				double pushScale = Math.min(0.55D, strength * 0.45D);
				entry.kbX = MathHelper.clamp(entry.kbX - (x / len) * pushScale, -2.4D, 2.4D);
				entry.kbZ = MathHelper.clamp(entry.kbZ - (z / len) * pushScale, -2.4D, 2.4D);
				entry.kbY = MathHelper.clamp(Math.max(entry.kbY, 0.22D) + 0.03D, 0.0D, 0.62D);
			}
		}
		return true;
	}

	public static void syncPlayerOnJoin(MinecraftServer server, ServerPlayerEntity player) {
		if (server != null && player != null && timeStopped) {
			HardcoreNetworking.sendTimeStopEvent(player, true, "sync", getTotalStackedHits(), getTotalStackedDamage());
		}
	}

	public static void onPlayerDisconnect(MinecraftServer server, ServerPlayerEntity player) {
		if (!timeStopped || server == null || player == null) {
			return;
		}
		if (player.getUuid().equals(casterUuid) || server.getPlayerManager().getPlayerList().size() <= 1) {
			resumeTimeStop(server, null, true);
		}
	}

	public static void forceResumeOnEmergency(MinecraftServer server) {
		if (timeStopped && server != null) {
			resumeTimeStop(server, null, false);
		}
	}

	public static void onServerStopping(MinecraftServer server) {
		if (server != null && (timeStopped || server.getTickManager().isFrozen())) {
			if (timeStopped) {
				server.getTickManager().setFrozen(false);
			}
		}
		timeStopped = false;
		casterUuid = null;
		flushingDeferredDamage = false;
		postResumeGraceUntilTick = 0L;
		DEFERRED_HITS.clear();
	}
}
