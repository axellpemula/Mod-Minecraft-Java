package com.axell.hardcoretools.client;

import java.util.Locale;

import org.joml.Matrix4f;

import com.axell.hardcoretools.client.gui.CrimsonTheme;
import com.axell.hardcoretools.feature.CombatAimFeature;
import com.axell.hardcoretools.network.HardcoreNetworking;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class CombatAimClientController {
	private static long lastRenderNs = 0L;
	private static CombatAimFeature.AimTargetResult currentLockedTarget = null;

	private CombatAimClientController() {
	}

	public static void reset() {
		lastRenderNs = 0L;
		currentLockedTarget = null;
	}

	/**
	 * Runs once per rendered world frame for ultra-smooth FPS-matched Aimbot tracking & target lock preview.
	 */
	public static void onBeforeWorldRender(RenderTickCounter tickCounter) {
		long nowNs = System.nanoTime();
		double dtSec = (lastRenderNs == 0L) ? (1.0 / 60.0) : ((nowNs - lastRenderNs) * 1.0e-9);
		lastRenderNs = nowNs;
		dtSec = MathHelper.clamp(dtSec, 0.0005, 0.1);

		MinecraftClient mc = MinecraftClient.getInstance();
		ClientStateSnapshot s = ClientStateSnapshot.INSTANCE;
		if (!s.serverModSynced || ReturnByDeathClientController.isActive()
				|| mc.player == null || mc.world == null || !mc.player.isAlive() || mc.currentScreen != null) {
			currentLockedTarget = null;
			return;
		}
		if (!s.aimbotEnabled && !s.aimSilentEnabled) {
			currentLockedTarget = null;
			return;
		}

		float tickDelta = tickCounter.getTickDelta(false);
		CombatAimFeature.AimConfig cfg = s.toAimConfig();
		double range = CombatAimFeature.getEffectiveRange(mc.player, cfg);
		CombatAimFeature.AimTargetResult best = CombatAimFeature.findBestTarget(mc.player, cfg, range, tickDelta);
		currentLockedTarget = best;

		if (!s.aimbotEnabled || best == null) {
			return;
		}

		Vec3d eyePos = mc.player.getCameraPosVec(tickDelta);
		Vec3d targetPt = best.aimPoint();
		if (CombatAimFeature.isRangedWeaponHeld(mc.player)) {
			double speed = (mc.player.getMainHandStack().getItem() instanceof TridentItem) ? 2.5 : 3.0;
			targetPt = CombatAimFeature.solveBallisticAimPoint(eyePos, best.entity(), targetPt, speed, 0.05);
		}

		float[] desired = CombatAimFeature.directionToYawPitch(eyePos, targetPt);
		float curYaw = mc.player.getYaw();
		float curPitch = mc.player.getPitch();
		float dYaw = MathHelper.wrapDegrees(desired[0] - curYaw);
		float dPitch = desired[1] - curPitch;

		float alpha;
		if (s.aimSmoothSpeed >= 98) {
			alpha = 1.0F;
		} else {
			double norm = s.aimSmoothSpeed / 100.0;
			double rate = 2.2 + (norm * norm) * 44.0;
			alpha = (float) MathHelper.clamp(1.0 - Math.exp(-rate * dtSec), 0.0, 1.0);
		}

		float nextYaw = curYaw + dYaw * alpha;
		float nextPitch = MathHelper.clamp(curPitch + dPitch * alpha, -90.0F, 90.0F);
		mc.player.setYaw(nextYaw);
		mc.player.setPitch(nextPitch);
		mc.player.prevYaw = nextYaw;
		mc.player.prevPitch = nextPitch;
	}

	/**
	 * Intercepts left-click attacks on the client when Aim Silent or Mob Protection is active.
	 * Returns Boolean.TRUE / Boolean.FALSE if handled, or null to fall through to vanilla doAttack().
	 */
	public static Boolean handleClientDoAttack(MinecraftClient mc) {
		ClientStateSnapshot s = ClientStateSnapshot.INSTANCE;
		ClientPlayerEntity player = mc.player;
		if (!s.serverModSynced || ReturnByDeathClientController.isActive()
				|| player == null || mc.world == null || !player.isAlive() || player.isSpectator()) {
			return null;
		}
		if (!s.aimSilentEnabled && !s.aimbotEnabled) {
			return null;
		}

		CombatAimFeature.AimConfig cfg = s.toAimConfig();
		if (!CombatAimFeature.isHoldingCombatWeapon(player, cfg.aimWeaponOnly())) {
			return null;
		}

		HitResult hit = mc.crosshairTarget;
		if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity hitLiv) {
			if (CombatAimFeature.isAllowedTarget(player, hitLiv, cfg)) {
				// Vanilla crosshair is already directly on a valid target — let vanilla attack proceed!
				return null;
			}
			// Crosshair is on a PROTECTED / EXCLUDED mob (e.g. Pet Wolf, Villager, Blacklisted Mob)!
			if (s.aimSilentEnabled) {
				CombatAimFeature.AimTargetResult alt = CombatAimFeature.findBestTarget(player, cfg, cfg.aimMaxReach(), 1.0F);
				if (alt != null) {
					dispatchSilentMeleeHit(player, alt.entity());
					return Boolean.TRUE;
				}
			}
			// Shield the protected mob from accidental swing!
			player.swingHand(Hand.MAIN_HAND);
			return Boolean.FALSE;
		}

		if (!s.aimSilentEnabled) {
			return null;
		}

		// Silent Aim: even when aiming at empty air or beside a mob, hit the best valid target inside the FOV Circle!
		CombatAimFeature.AimTargetResult best = CombatAimFeature.findBestTarget(player, cfg, cfg.aimMaxReach(), 1.0F);
		if (best != null) {
			dispatchSilentMeleeHit(player, best.entity());
			return Boolean.TRUE;
		}
		return null;
	}

	private static void dispatchSilentMeleeHit(ClientPlayerEntity player, LivingEntity target) {
		if (ClientPlayNetworking.canSend(HardcoreNetworking.ActionC2SPayload.ID)) {
			JsonObject o = new JsonObject();
			o.addProperty("targetId", target.getId());
			ClientPlayNetworking.send(new HardcoreNetworking.ActionC2SPayload("silent_melee_hit", o.toString()));
		}
		player.resetLastAttackedTicks();
		player.swingHand(Hand.MAIN_HAND);
	}

	/**
	 * Renders the HUD FOV Circle and Target Lock Reticle when Aimbot or Silent Aim is active.
	 */
	public static void renderAimHud(DrawContext ctx, RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientStateSnapshot s = ClientStateSnapshot.INSTANCE;
		if (!s.serverModSynced || ReturnByDeathClientController.isActive()) {
			return;
		}
		if (mc.player == null || mc.world == null || mc.options.hudHidden || mc.currentScreen != null) {
			return;
		}
		if ((!s.aimbotEnabled && !s.aimSilentEnabled) || !s.aimShowFovCircle) {
			return;
		}
		if (!CombatAimFeature.isHoldingCombatWeapon(mc.player, s.aimWeaponOnly)) {
			return;
		}

		int sw = ctx.getScaledWindowWidth();
		int sh = ctx.getScaledWindowHeight();
		float cx = sw * 0.5F;
		float cy = sh * 0.5F;

		double baseFov = mc.options.getFov().getValue();
		double effVertFovDeg = MathHelper.clamp(baseFov * mc.player.getFovMultiplier(), 15.0, 150.0);
		double focal = (sh * 0.5) / Math.tan(Math.toRadians(effVertFovDeg * 0.5));
		double coneDeg = Math.min(82.0, s.aimFovDegrees);
		float radiusPx = (float) Math.min(Math.max(sw, sh) * 0.70, focal * Math.tan(Math.toRadians(coneDeg)));

		CombatAimFeature.AimTargetResult locked = currentLockedTarget;
		boolean hasLock = (locked != null && locked.entity().isAlive());

		int ringCol = hasLock
				? CrimsonTheme.withAlpha(CrimsonTheme.EMERALD, 185)
				: CrimsonTheme.withAlpha(CrimsonTheme.CRIMSON_BRIGHT, 110);
		int glowCol = hasLock
				? CrimsonTheme.withAlpha(CrimsonTheme.EMERALD, 36)
				: CrimsonTheme.withAlpha(CrimsonTheme.CRIMSON_BRIGHT, 22);

		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
		drawSmoothRing(mat, cx, cy, radiusPx, 2.6F, glowCol, 72);
		drawSmoothRing(mat, cx, cy, radiusPx, 1.05F, ringCol, 72);

		if (hasLock) {
			// Project 3D locked target point onto screen inside the FOV circle
			Camera cam = mc.gameRenderer.getCamera();
			Vec3d camPos = cam.getPos();
			Vec3d forward = Vec3d.fromPolar(cam.getPitch(), cam.getYaw()).normalize();
			Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
			Vec3d right = forward.crossProduct(worldUp);
			if (right.lengthSquared() < 1e-6) {
				right = new Vec3d(1.0, 0.0, 0.0);
			} else {
				right = right.normalize();
			}
			Vec3d up = right.crossProduct(forward).normalize();

			Vec3d rel = locked.aimPoint().subtract(camPos);
			double zCam = rel.dotProduct(forward);
			if (zCam > 0.05) {
				double xCam = rel.dotProduct(right);
				double yCam = rel.dotProduct(up);
				float tx = cx + (float) ((xCam / zCam) * focal);
				float ty = cy - (float) ((yCam / zCam) * focal);
				drawTargetBracket(ctx, tx, ty, 5.5F, CrimsonTheme.withAlpha(CrimsonTheme.EMERALD, 230));
			}

			String modeTag = (s.aimbotEnabled && s.aimSilentEnabled) ? "LOCK+SILENT"
					: (s.aimSilentEnabled ? "SILENT" : "LOCK");
			String label = String.format(Locale.US, "🎯 %s · %s (%.1fm)",
					modeTag, locked.entity().getName().getString(), locked.distance());
			ctx.getMatrices().push();
			float labelScale = 0.78F;
			int lw = Math.round(mc.textRenderer.getWidth(label) * labelScale);
			float lx = cx - lw * 0.5F;
			float ly = Math.min(sh - 36.0F, cy + radiusPx + 6.0F);
			ctx.getMatrices().translate(lx, ly, 0.0F);
			ctx.getMatrices().scale(labelScale, labelScale, 1.0F);
			ctx.drawTextWithShadow(mc.textRenderer, label, 0, 0, CrimsonTheme.EMERALD);
			ctx.getMatrices().pop();
		}
	}

	private static void drawTargetBracket(DrawContext ctx, float x, float y, float halfSize, int color) {
		int x1 = Math.round(x - halfSize);
		int y1 = Math.round(y - halfSize);
		int x2 = Math.round(x + halfSize);
		int y2 = Math.round(y + halfSize);
		int arm = 3;
		// Top-left
		ctx.fill(x1, y1, x1 + arm, y1 + 1, color);
		ctx.fill(x1, y1, x1 + 1, y1 + arm, color);
		// Top-right
		ctx.fill(x2 - arm, y1, x2, y1 + 1, color);
		ctx.fill(x2 - 1, y1, x2, y1 + arm, color);
		// Bottom-left
		ctx.fill(x1, y2 - 1, x1 + arm, y2, color);
		ctx.fill(x1, y2 - arm, x1 + 1, y2, color);
		// Bottom-right
		ctx.fill(x2 - arm, y2 - 1, x2, y2, color);
		ctx.fill(x2 - 1, y2 - arm, x2, y2, color);
		// Center pinpoint dot
		ctx.fill(Math.round(x) - 1, Math.round(y) - 1, Math.round(x) + 1, Math.round(y) + 1, color);
	}

	private static void drawSmoothRing(Matrix4f mat, float cx, float cy, float radius, float thickness, int argb, int segments) {
		if (radius < 2.0F) return;
		float a = ((argb >>> 24) & 0xFF) / 255.0F;
		if (a <= 0.004F) return;
		float r = ((argb >>> 16) & 0xFF) / 255.0F;
		float g = ((argb >>> 8) & 0xFF) / 255.0F;
		float b = (argb & 0xFF) / 255.0F;

		float innerR = Math.max(0.5F, radius - thickness * 0.5F);
		float outerR = radius + thickness * 0.5F;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		double step = (Math.PI * 2.0) / segments;
		for (int i = 0; i < segments; i++) {
			double a0 = i * step;
			double a1 = (i + 1) * step;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);

			buf.vertex(mat, cx + c0 * outerR, cy + s0 * outerR, 0.0F).color(r, g, b, a);
			buf.vertex(mat, cx + c0 * innerR, cy + s0 * innerR, 0.0F).color(r, g, b, a);
			buf.vertex(mat, cx + c1 * innerR, cy + s1 * innerR, 0.0F).color(r, g, b, a);
			buf.vertex(mat, cx + c1 * outerR, cy + s1 * outerR, 0.0F).color(r, g, b, a);
		}
		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}
	}
}
