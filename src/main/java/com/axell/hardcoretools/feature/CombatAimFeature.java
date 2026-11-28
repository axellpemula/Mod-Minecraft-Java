package com.axell.hardcoretools.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.axell.hardcoretools.state.HardcoreStateManager;
import com.axell.hardcoretools.state.PlayerProfile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PiglinBruteEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class CombatAimFeature {
	public static final double RANGED_MAX_DISTANCE = 52.0;

	public record AimConfig(
			boolean aimbotEnabled,
			boolean aimSilentEnabled,
			boolean aimShowFovCircle,
			boolean aimVisibleOnly,
			boolean aimWeaponOnly,
			int aimFovDegrees,
			int aimSmoothSpeed,
			int aimMaxReach,
			boolean protectPets,
			boolean protectVillagers,
			boolean protectPassive,
			boolean protectNeutral,
			boolean protectNamed,
			boolean protectPlayers,
			Set<String> excludedMobIds
	) {
		public static AimConfig fromProfile(PlayerProfile p) {
			Set<String> excl = p.aimExcludedMobs == null ? Set.of() : new HashSet<>(p.aimExcludedMobs);
			return new AimConfig(
					p.aimbotEnabled,
					p.aimSilentEnabled,
					p.aimShowFovCircle,
					p.aimVisibleOnly,
					p.aimWeaponOnly,
					p.aimFovDegrees,
					p.aimSmoothSpeed,
					p.aimMaxReach,
					p.aimProtectPets,
					p.aimProtectVillagers,
					p.aimProtectPassive,
					p.aimProtectNeutral,
					p.aimProtectNamed,
					p.aimProtectPlayers,
					excl
			);
		}
	}

	public record AimTargetResult(
			LivingEntity entity,
			Vec3d aimPoint,
			double centerAngleDeg,
			double edgeAngleDeg,
			double distance
	) {
	}

	public record MobFilterEntry(
			String id,
			String name,
			String group,
			boolean excluded
	) {
	}

	private CombatAimFeature() {
	}

	public static boolean isHoldingCombatWeapon(PlayerEntity player, boolean requireWeapon) {
		if (!requireWeapon) {
			return true;
		}
		ItemStack main = player.getMainHandStack();
		if (main.isEmpty()) {
			return false;
		}
		return WeaponDamageFeature.isCatalogWeapon(main.getItem())
				|| WeaponDamageFeature.isCustomItemWeapon(main);
	}

	public static boolean isRangedWeaponHeld(PlayerEntity player) {
		ItemStack main = player.getMainHandStack();
		if (main.isEmpty()) return false;
		Item item = main.getItem();
		if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof RangedWeaponItem) {
			return true;
		}
		// Trident counts as ranged when the player is actively drawing it back to throw
		return item instanceof TridentItem && player.isUsingItem();
	}

	public static double getEffectiveRange(PlayerEntity player, AimConfig cfg) {
		if (isRangedWeaponHeld(player)) {
			return Math.max(cfg.aimMaxReach(), RANGED_MAX_DISTANCE);
		}
		return cfg.aimMaxReach();
	}

	public static boolean isAllowedTarget(PlayerEntity attacker, LivingEntity candidate, AimConfig cfg) {
		if (candidate == null || candidate == attacker || !candidate.isAlive() || candidate.isSpectator() || candidate.isInvulnerable()) {
			return false;
		}
		if (candidate instanceof ArmorStandEntity) {
			return false;
		}
		if (attacker.getVehicle() == candidate || candidate.hasPassenger(attacker)) {
			return false;
		}

		String typeId = Registries.ENTITY_TYPE.getId(candidate.getType()).toString();
		if (cfg.excludedMobIds() != null && cfg.excludedMobIds().contains(typeId)) {
			return false;
		}

		if (candidate instanceof PlayerEntity) {
			return !cfg.protectPlayers();
		}

		if (cfg.protectNamed() && candidate.hasCustomName()) {
			return false;
		}

		if (cfg.protectPets()) {
			if (candidate instanceof TameableEntity tame && tame.isTamed()) {
				return false;
			}
			if (candidate instanceof AbstractHorseEntity horse && horse.isTame()) {
				return false;
			}
			if (candidate instanceof Tameable t && t.getOwnerUuid() != null) {
				return false;
			}
		}

		if (cfg.protectVillagers()) {
			if (candidate instanceof MerchantEntity || candidate instanceof GolemEntity || candidate instanceof AllayEntity) {
				return false;
			}
		}

		boolean isProvoked = isEntityAngryOrAttacking(candidate);
		boolean isMonster = (candidate instanceof Monster)
				|| candidate.getType().getSpawnGroup() == SpawnGroup.MONSTER;

		if (cfg.protectPassive() && !isMonster && !isProvoked) {
			return false;
		}

		if (cfg.protectNeutral() && isNeutralSpecies(candidate) && !isProvoked) {
			return false;
		}

		return true;
	}

	private static boolean isNeutralSpecies(LivingEntity entity) {
		if (entity instanceof PiglinBruteEntity) {
			return false;
		}
		return (entity instanceof EndermanEntity)
				|| (entity instanceof Angerable)
				|| (entity instanceof AbstractPiglinEntity);
	}

	private static boolean isEntityAngryOrAttacking(LivingEntity entity) {
		if (entity instanceof EndermanEntity enderman && enderman.isAngry()) {
			return true;
		}
		if (entity instanceof Angerable ang && ang.getAngerTime() > 0) {
			return true;
		}
		if (entity instanceof MobEntity mob && mob.isAttacking()) {
			return true;
		}
		return false;
	}

	/**
	 * Resolves a clear aim point on the target's body. When requireVisible is true, checks 4 vertical
	 * anchor points along the target's hitbox using collider raycasting so peeking mobs are still detected
	 * while mobs fully behind solid blocks are ignored.
	 */
	public static Vec3d resolveAimPoint(PlayerEntity viewer, LivingEntity target, boolean requireVisible, float tickDelta) {
		Vec3d eyePos = tickDelta >= 0.999F ? viewer.getEyePos() : viewer.getCameraPosVec(tickDelta);
		Vec3d basePos = tickDelta >= 0.999F ? target.getPos() : target.getLerpedPos(tickDelta);
		double h = target.getHeight();

		Vec3d upperChest = new Vec3d(basePos.x, basePos.y + h * 0.72, basePos.z);
		if (!requireVisible) {
			return upperChest;
		}

		Vec3d headPt = new Vec3d(basePos.x, basePos.y + Math.min(h * 0.90, target.getStandingEyeHeight()), basePos.z);
		Vec3d midPt = new Vec3d(basePos.x, basePos.y + h * 0.50, basePos.z);
		Vec3d lowPt = new Vec3d(basePos.x, basePos.y + h * 0.22, basePos.z);

		for (Vec3d candidatePt : new Vec3d[] { upperChest, headPt, midPt, lowPt }) {
			BlockHitResult hit = viewer.getWorld().raycast(new RaycastContext(
					eyePos,
					candidatePt,
					RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE,
					viewer
			));
			if (hit.getType() == HitResult.Type.MISS) {
				return candidatePt;
			}
		}
		return null;
	}

	public static AimTargetResult findBestTarget(PlayerEntity viewer, AimConfig cfg, double maxRange, float tickDelta) {
		if (viewer == null || viewer.getWorld() == null) {
			return null;
		}
		if (!isHoldingCombatWeapon(viewer, cfg.aimWeaponOnly())) {
			return null;
		}

		Vec3d eyePos = tickDelta >= 0.999F ? viewer.getEyePos() : viewer.getCameraPosVec(tickDelta);
		Vec3d lookVec = (tickDelta >= 0.999F ? viewer.getRotationVector() : viewer.getRotationVec(tickDelta)).normalize();

		Box searchBox = viewer.getBoundingBox().expand(maxRange + 2.5);
		List<LivingEntity> candidates = viewer.getWorld().getEntitiesByClass(
				LivingEntity.class,
				searchBox,
				e -> isAllowedTarget(viewer, e, cfg)
		);

		AimTargetResult best = null;
		double bestScore = Double.MAX_VALUE;
		double fovLimit = cfg.aimFovDegrees();

		for (LivingEntity cand : candidates) {
			Box targetBox = getInterpolatedBox(cand, tickDelta).expand(cand.getTargetingMargin());
			double dist = Math.sqrt(targetBox.squaredMagnitude(eyePos));
			if (dist > maxRange) {
				continue;
			}

			Vec3d aimPt = resolveAimPoint(viewer, cand, cfg.aimVisibleOnly(), tickDelta);
			if (aimPt == null) {
				continue;
			}

			double centerAngle = angleBetweenDegrees(lookVec, aimPt.subtract(eyePos));
			double edgeAngle = hitboxEdgeAngleDegrees(eyePos, lookVec, targetBox, centerAngle);
			if (edgeAngle > fovLimit) {
				continue;
			}

			// Primary priority: closest to crosshair center; gentle tie-breaker: closer distance
			double score = centerAngle + (dist * 0.08);
			if (score < bestScore) {
				bestScore = score;
				best = new AimTargetResult(cand, aimPt, centerAngle, edgeAngle, dist);
			}
		}
		return best;
	}

	private static Box getInterpolatedBox(LivingEntity e, float tickDelta) {
		if (tickDelta >= 0.999F) {
			return e.getBoundingBox();
		}
		Vec3d cur = e.getPos();
		Vec3d lerp = e.getLerpedPos(tickDelta);
		return e.getBoundingBox().offset(lerp.x - cur.x, lerp.y - cur.y, lerp.z - cur.z);
	}

	public static double angleBetweenDegrees(Vec3d normalizedLook, Vec3d toTarget) {
		double lenSq = toTarget.lengthSquared();
		if (lenSq < 1e-8) return 0.0;
		double dot = normalizedLook.dotProduct(toTarget.multiply(1.0 / Math.sqrt(lenSq)));
		dot = MathHelper.clamp(dot, -1.0, 1.0);
		return Math.toDegrees(Math.acos(dot));
	}

	private static double hitboxEdgeAngleDegrees(Vec3d eyePos, Vec3d lookVec, Box box, double centerAngle) {
		if (box.contains(eyePos)) {
			return 0.0;
		}
		Vec3d center = box.getCenter();
		double t = Math.max(0.05, center.subtract(eyePos).dotProduct(lookVec));
		Vec3d rayPt = eyePos.add(lookVec.multiply(t));
		double cx = MathHelper.clamp(rayPt.x, box.minX, box.maxX);
		double cy = MathHelper.clamp(rayPt.y, box.minY, box.maxY);
		double cz = MathHelper.clamp(rayPt.z, box.minZ, box.maxZ);
		double edge = angleBetweenDegrees(lookVec, new Vec3d(cx - eyePos.x, cy - eyePos.y, cz - eyePos.z));
		return Math.min(centerAngle, edge);
	}

	public static Vec3d solveBallisticAimPoint(Vec3d shooterEye, LivingEntity target, Vec3d baseAimPt, double speed, double gravityPerTick) {
		if (speed <= 0.1) {
			return baseAimPt;
		}
		double dist = shooterEye.distanceTo(baseAimPt);
		double flightTicks = dist / speed;
		Vec3d vel = target.getVelocity();
		double leadX = baseAimPt.x + vel.x * flightTicks * 0.82;
		double leadZ = baseAimPt.z + vel.z * flightTicks * 0.82;
		double dropComp = 0.5 * gravityPerTick * flightTicks * flightTicks * 1.03;
		double leadY = baseAimPt.y + dropComp;
		return new Vec3d(leadX, leadY, leadZ);
	}

	public static float[] directionToYawPitch(Vec3d from, Vec3d to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		float pitch = (float) (-(MathHelper.atan2(dy, horiz) * (180.0 / Math.PI)));
		return new float[] { yaw, MathHelper.clamp(pitch, -90.0F, 90.0F) };
	}

	/**
	 * Server-side validated Silent Aim melee strike.
	 */
	public static boolean executeSilentMeleeStrike(ServerPlayerEntity player, int suggestedEntityId) {
		if (player == null || player.getServer() == null || !HardcoreStateManager.canUseTools(player)) {
			return false;
		}
		PlayerProfile profile = HardcoreStateManager.getProfile(player);
		if (!profile.aimSilentEnabled) {
			return false;
		}

		AimConfig cfg = AimConfig.fromProfile(profile);
		if (!isHoldingCombatWeapon(player, cfg.aimWeaponOnly())) {
			return false;
		}

		double maxReach = cfg.aimMaxReach() + 1.25;
		LivingEntity resolved = null;

		if (suggestedEntityId >= 0) {
			Entity cand = player.getServerWorld().getEntityById(suggestedEntityId);
			if (cand instanceof LivingEntity liv && isAllowedTarget(player, liv, cfg)) {
				Box box = liv.getBoundingBox().expand(liv.getTargetingMargin());
				double dist = Math.sqrt(box.squaredMagnitude(player.getEyePos()));
				Vec3d aimPt = resolveAimPoint(player, liv, cfg.aimVisibleOnly(), 1.0F);
				if (dist <= maxReach && aimPt != null) {
					Vec3d look = player.getRotationVector().normalize();
					double edgeAng = hitboxEdgeAngleDegrees(player.getEyePos(), look, box,
							angleBetweenDegrees(look, aimPt.subtract(player.getEyePos())));
					if (edgeAng <= cfg.aimFovDegrees() + 10.0) {
						resolved = liv;
					}
				}
			}
		}

		if (resolved == null) {
			AimTargetResult res = findBestTarget(player, cfg, cfg.aimMaxReach() + 0.75, 1.0F);
			if (res != null) {
				resolved = res.entity();
			}
		}

		if (resolved == null) {
			return false;
		}

		if (resolved instanceof EnderDragonEntity dragon) {
			player.attack(dragon.head);
		} else {
			player.attack(resolved);
		}
		return true;
	}

	/**
	 * Server-side Silent Aim projectile steering for Bow, Crossbow (arrows & rockets), and Thrown Trident.
	 */
	public static Vec3d computeSilentProjectileVelocity(ServerPlayerEntity shooter, ProjectileEntity projectile, double speed) {
		if (shooter == null || shooter.getServer() == null || projectile == null || speed <= 0.05) {
			return null;
		}
		if (!(projectile instanceof PersistentProjectileEntity) && !(projectile instanceof FireworkRocketEntity)) {
			return null;
		}
		if (!HardcoreStateManager.canUseTools(shooter)) {
			return null;
		}
		PlayerProfile profile = HardcoreStateManager.getProfile(shooter);
		if (!profile.aimSilentEnabled) {
			return null;
		}

		AimConfig cfg = AimConfig.fromProfile(profile);
		AimTargetResult best = findBestTarget(shooter, cfg, Math.max(cfg.aimMaxReach(), RANGED_MAX_DISTANCE), 1.0F);
		if (best == null) {
			return null;
		}

		double gravity = (projectile instanceof FireworkRocketEntity) ? 0.0 : 0.05;
		Vec3d start = projectile.getPos();
		Vec3d solved = solveBallisticAimPoint(start, best.entity(), best.aimPoint(), speed, gravity);
		Vec3d dir = solved.subtract(start);
		if (dir.lengthSquared() < 1e-6) {
			return null;
		}
		return dir.normalize().multiply(speed);
	}

	public static List<MobFilterEntry> collectMobCatalog(Set<String> excludedIds) {
		List<MobFilterEntry> list = new ArrayList<>();
		for (EntityType<?> type : Registries.ENTITY_TYPE) {
			SpawnGroup sg = type.getSpawnGroup();
			Identifier id = Registries.ENTITY_TYPE.getId(type);
			String path = id.getPath();
			boolean specialLiving = path.equals("villager") || path.equals("wandering_trader")
					|| path.equals("iron_golem") || path.equals("snow_golem")
					|| path.equals("allay") || path.equals("ender_dragon") || path.equals("wither");
			if (sg == SpawnGroup.MISC && !specialLiving) {
				continue;
			}

			String group = classifyMobGroup(sg, path);
			String idStr = id.toString();
			boolean isExcluded = excludedIds != null && excludedIds.contains(idStr);
			list.add(new MobFilterEntry(idStr, type.getName().getString(), group, isExcluded));
		}

		list.sort(Comparator
				.comparingInt((MobFilterEntry e) -> e.excluded() ? 0 : 1)
				.thenComparingInt(e -> groupOrder(e.group()))
				.thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
		return list;
	}

	private static String classifyMobGroup(SpawnGroup sg, String path) {
		if (path.equals("villager") || path.equals("wandering_trader") || path.equals("iron_golem")
				|| path.equals("snow_golem") || path.equals("allay")) {
			return "friendly";
		}
		if (path.equals("enderman") || path.equals("zombified_piglin") || path.equals("piglin")
				|| path.equals("bee") || path.equals("wolf") || path.equals("polar_bear")
				|| path.equals("llama") || path.equals("trader_llama") || path.equals("panda")
				|| path.equals("dolphin") || path.equals("goat")) {
			return "neutral";
		}
		if (sg == SpawnGroup.MONSTER || path.equals("ender_dragon") || path.equals("wither")) {
			return "hostile";
		}
		return "passive";
	}

	private static int groupOrder(String g) {
		return switch (g) {
			case "friendly" -> 0;
			case "neutral" -> 1;
			case "passive" -> 2;
			case "hostile" -> 3;
			default -> 4;
		};
	}
}
