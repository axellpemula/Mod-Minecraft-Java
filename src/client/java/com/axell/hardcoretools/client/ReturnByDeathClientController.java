package com.axell.hardcoretools.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix4f;

import com.axell.hardcoretools.client.gui.CrimsonTheme;
import com.axell.hardcoretools.client.gui.ReturnByDeathScreen;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class ReturnByDeathClientController {
	private static final Gson GSON = new Gson();

	public record RewindFrame(
			double p,
			String dim,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			long skyTime
	) {
	}

	private static boolean active = false;
	private static long startMs = 0L;
	private static long durationMs = 5000L;
	private static long act1Ms = 1500L;
	private static long act2Ms = 4500L;
	private static String deathMessage = "You met a fatal end";
	private static int score = 0;
	private static boolean worldHardcore = false;
	private static double rewoundSeconds = 60.0D;
	private static List<RewindFrame> frames = List.of();

	// Sound choreography state
	private static boolean playedSecondBeat = false;
	private static boolean playedShatterStart = false;
	private static boolean playedSnapBack = false;
	private static int lastClockTickIdx = -1;
	private static long lockShakeClickMs = 0L;

	private ReturnByDeathClientController() {
	}

	public static boolean isActive() {
		return active;
	}

	public static long getElapsedMs() {
		if (!active) return 0L;
		return Math.max(0L, System.currentTimeMillis() - startMs);
	}

	public static long getAct1Ms() {
		return act1Ms;
	}

	public static long getAct2Ms() {
		return act2Ms;
	}

	public static long getDurationMs() {
		return durationMs;
	}

	public static String getDeathMessage() {
		return deathMessage;
	}

	public static int getScore() {
		return score;
	}

	public static boolean isWorldHardcore() {
		return worldHardcore;
	}

	public static double getRewoundSeconds() {
		return rewoundSeconds;
	}

	public static long getLockShakeClickMs() {
		return lockShakeClickMs;
	}

	public static void triggerLockedButtonRattle() {
		lockShakeClickMs = System.currentTimeMillis();
		playVanillaSound("block.chain.place", 0.72F);
		playVanillaSound("block.respawn_anchor.deplete", 1.45F);
	}

	public static void handleServerPayload(String json) {
		try {
			JsonObject r = GSON.fromJson(json, JsonObject.class);
			if (r == null) return;
			boolean isAct = !r.has("active") || r.get("active").getAsBoolean();
			if (!isAct) {
				reset();
				return;
			}

			durationMs = r.has("durationMs") ? r.get("durationMs").getAsLong() : 5000L;
			act1Ms = r.has("act1Ms") ? r.get("act1Ms").getAsLong() : 1500L;
			act2Ms = r.has("act2Ms") ? r.get("act2Ms").getAsLong() : 4500L;
			deathMessage = r.has("deathMessage") ? r.get("deathMessage").getAsString() : "You died";
			score = r.has("score") ? r.get("score").getAsInt() : 0;
			worldHardcore = r.has("worldHardcore") && r.get("worldHardcore").getAsBoolean();
			rewoundSeconds = r.has("rewoundSeconds") ? r.get("rewoundSeconds").getAsDouble() : 60.0D;

			List<RewindFrame> parsed = new ArrayList<>();
			if (r.has("frames") && r.get("frames").isJsonArray()) {
				for (JsonElement el : r.getAsJsonArray("frames")) {
					if (!el.isJsonObject()) continue;
					JsonObject fo = el.getAsJsonObject();
					parsed.add(new RewindFrame(
							fo.has("p") ? fo.get("p").getAsDouble() : 0.0D,
							fo.has("dim") ? fo.get("dim").getAsString() : "minecraft:overworld",
							fo.has("x") ? fo.get("x").getAsDouble() : 0.0D,
							fo.has("y") ? fo.get("y").getAsDouble() : 64.0D,
							fo.has("z") ? fo.get("z").getAsDouble() : 0.0D,
							fo.has("yaw") ? fo.get("yaw").getAsFloat() : 0.0F,
							fo.has("pitch") ? fo.get("pitch").getAsFloat() : 0.0F,
							fo.has("sky") ? fo.get("sky").getAsLong() : 0L
					));
				}
			}
			frames = parsed;

			active = true;
			startMs = System.currentTimeMillis();
			playedSecondBeat = false;
			playedShatterStart = false;
			playedSnapBack = false;
			lastClockTickIdx = -1;
			lockShakeClickMs = 0L;

			// Act 1 Opening Audio: Deep Heartbeat + Abyssal Bell + Eerie Witch's Call Chord!
			playVanillaSound("entity.warden.heartbeat", 0.66F);
			playVanillaSound("block.bell.resonate", 0.50F);
			playVanillaSound("entity.elder_guardian.curse", 0.54F);

			MinecraftClient mc = MinecraftClient.getInstance();
			ClientStateSnapshot.INSTANCE.upgraderModalOpen = false;
			mc.setScreen(new ReturnByDeathScreen());
		} catch (Exception ignored) {
		}
	}

	public static void reset() {
		active = false;
		frames = List.of();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc != null && mc.currentScreen instanceof ReturnByDeathScreen) {
			mc.setScreen(null);
		}
	}

	public static void onClientTick(MinecraftClient client) {
		if (!active) {
			return;
		}
		if (client.player == null || client.world == null) {
			reset();
			return;
		}

		long elapsed = getElapsedMs();
		if (elapsed >= durationMs) {
			reset();
			return;
		}

		// Keep ReturnByDeathScreen active during Act 1 (0..1.5s) & Act 2 (1.5..4.5s), then release at 4.5s for Act 3!
		if (elapsed < act2Ms) {
			if (!(client.currentScreen instanceof ReturnByDeathScreen)) {
				client.setScreen(new ReturnByDeathScreen());
			}
		} else if (client.currentScreen instanceof ReturnByDeathScreen) {
			client.setScreen(null);
		}

		// Multi-Act Audio Choreography
		if (!playedSecondBeat && elapsed >= 760L) {
			playedSecondBeat = true;
			playVanillaSound("entity.warden.heartbeat", 0.84F);
			playVanillaSound("block.chain.place", 0.62F);
		}

		if (!playedShatterStart && elapsed >= act1Ms) {
			playedShatterStart = true;
			playVanillaSound("block.glass.break", 0.72F);
			playVanillaSound("block.portal.trigger", 1.65F);
			playVanillaSound("block.beacon.deactivate", 0.78F);
		}

		if (elapsed >= act1Ms && elapsed < act2Ms) {
			double rawP = (elapsed - act1Ms) / (double) Math.max(1L, act2Ms - act1Ms);
			// Accelerating pocket-watch reverse ticks (24 ticks across 3.0s)
			int tickIdx = (int) (Math.pow(rawP, 0.82) * 24.0);
			if (tickIdx != lastClockTickIdx) {
				lastClockTickIdx = tickIdx;
				float tickPitch = (tickIdx % 2 == 0) ? 1.62F : 1.28F;
				playVanillaSound("block.comparator.click", tickPitch);
				if (tickIdx % 3 == 0) {
					float chimePitch = 0.62F + (float) (rawP * 1.12F);
					playVanillaSound("block.amethyst_block.chime", chimePitch);
				}
			}
		}

		if (!playedSnapBack && elapsed >= act2Ms) {
			playedSnapBack = true;
			playVanillaSound("block.respawn_anchor.set_spawn", 1.25F);
			playVanillaSound("block.amethyst_cluster.break", 1.35F);
			playVanillaSound("entity.player.breath", 0.94F);
			playVanillaSound("entity.warden.heartbeat", 1.0F);
		}
	}

	/**
	 * Runs right before Camera#update in GameRenderer#renderWorld at native monitor FPS (60/144/240 Hz)
	 * so the 3D world glides backward with zero 20-TPS packet stutter!
	 */
	public static void onBeforeWorldRender() {
		if (!active || frames.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.world == null) {
			return;
		}

		long elapsed = getElapsedMs();
		if (elapsed >= act2Ms) {
			return;
		}

		String currentDim = mc.world.getRegistryKey().getValue().toString();

		if (elapsed < act1Ms) {
			// Act 1: Hold steady at death spot with subtle dying heartbeat shudder
			RewindFrame first = frames.get(0);
			if (first.dim.equals(currentDim)) {
				double beat = 0.0D;
				if (elapsed < 260L) {
					beat = Math.sin((elapsed / 260.0D) * Math.PI) * 0.9D;
				} else if (elapsed >= 760L && elapsed < 1020L) {
					beat = Math.sin(((elapsed - 760L) / 260.0D) * Math.PI) * 1.1D;
				}
				applySmoothPlayerCamera(player, first.x, first.y, first.z,
						first.yaw + (float) (beat * 0.6F),
						MathHelper.clamp(first.pitch + (float) (beat * 1.4F), -90.0F, 90.0F));
			}
			return;
		}

		// Act 2 (1.5s - 4.5s): Buttery-smooth sub-tick reverse trajectory interpolation!
		double rawP = (elapsed - act1Ms) / (double) Math.max(1L, act2Ms - act1Ms);
		double easedP = -(Math.cos(Math.PI * Math.max(0.0D, Math.min(1.0D, rawP))) - 1.0D) / 2.0D;

		RewindFrame f0 = frames.get(0);
		RewindFrame f1 = frames.get(frames.size() - 1);
		for (int i = 0; i < frames.size() - 1; i++) {
			RewindFrame a = frames.get(i);
			RewindFrame b = frames.get(i + 1);
			if (easedP >= a.p && easedP <= b.p) {
				f0 = a;
				f1 = b;
				break;
			}
		}

		if (!f0.dim.equals(currentDim) || !f1.dim.equals(currentDim)) {
			return;
		}

		double span = Math.max(0.00001D, f1.p - f0.p);
		float localT = (float) Math.max(0.0D, Math.min(1.0D, (easedP - f0.p) / span));

		double ix = MathHelper.lerp(localT, f0.x, f1.x);
		double iy = MathHelper.lerp(localT, f0.y, f1.y);
		double iz = MathHelper.lerp(localT, f0.z, f1.z);
		float iyaw = MathHelper.lerpAngleDegrees(localT, f0.yaw, f1.yaw);
		float ipitch = MathHelper.lerp(localT, f0.pitch, f1.pitch);

		applySmoothPlayerCamera(player, ix, iy, iz, iyaw, ipitch);

		long isky = Math.round(f0.skyTime + (f1.skyTime - f0.skyTime) * localT);
		if (isky >= 0L && "minecraft:overworld".equals(currentDim)) {
			mc.world.setTimeOfDay(isky);
		}
	}

	private static void applySmoothPlayerCamera(ClientPlayerEntity player, double x, double y, double z, float yaw, float pitch) {
		player.setPos(x, y, z);
		player.resetPosition();
		player.setVelocity(0.0D, 0.0D, 0.0D);
		player.setYaw(yaw);
		player.prevYaw = yaw;
		player.headYaw = yaw;
		player.prevHeadYaw = yaw;
		player.bodyYaw = yaw;
		player.prevBodyYaw = yaw;
		player.setPitch(pitch);
		player.prevPitch = pitch;
	}

	/**
	 * Babak 3 (4.5s - 5.0s): Snap Back to Reality HUD Awakening Overlay (after ReturnByDeathScreen closes!)
	 */
	public static void renderAct3HudOverlay(DrawContext ctx, RenderTickCounter tickCounter) {
		if (!active) {
			return;
		}
		long elapsed = getElapsedMs();
		if (elapsed < act2Ms || elapsed >= durationMs) {
			return;
		}

		MinecraftClient mc = MinecraftClient.getInstance();
		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();

		double p = (elapsed - act2Ms) / (double) Math.max(1L, durationMs - act2Ms); // 0.0 .. 1.0 over 0.5s
		double fade = Math.pow(1.0D - Math.min(1.0D, p), 1.85D);

		// 1. Soft Abyssal Chrono Vignette fading out
		int vigAlpha = (int) Math.round(165 * fade);
		if (vigAlpha > 0) {
			ctx.fillGradient(0, 0, sw, 46, (vigAlpha << 24) | 0x160524, 0x00160524);
			ctx.fillGradient(0, sh - 46, sw, sh, 0x00160524, (vigAlpha << 24) | 0x160524);
		}

		// 2. Smooth Radial Chrono Singularity Awakening Bloom
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
		float cx = sw * 0.5F;
		float cy = sh * 0.5F;
		float maxRadius = Math.max(sw, sh) * 0.78F;
		int flashAlpha = Math.max(0, Math.min(245, (int) Math.round(235 * Math.pow(1.0D - p, 2.2D))));
		if (flashAlpha > 2) {
			BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
			buf.vertex(mat, cx, cy, 0.0F).color((flashAlpha << 24) | 0xF5E6FF);
			int segs = 40;
			int edgeCol = ((flashAlpha / 3) << 24) | 0x9B4DFF;
			for (int i = 0; i <= segs; i++) {
				double ang = (i * Math.PI * 2.0D) / segs;
				buf.vertex(mat, cx + (float) (Math.cos(ang) * maxRadius), cy + (float) (Math.sin(ang) * maxRadius), 0.0F).color(edgeCol);
			}
			BuiltBuffer built = buf.endNullable();
			if (built != null) {
				BufferRenderer.drawWithGlobalProgram(built);
			}
		}

		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		// 3. Crisp Awakening Banner
		int textAlpha = Math.max(0, Math.min(255, (int) Math.round(255 * Math.sin(Math.min(1.0D, p) * Math.PI))));
		if (textAlpha > 18 && mc.textRenderer != null) {
			String title = String.format(Locale.ROOT, "✦ RETURNED BY DEATH -%.1fs ✦", rewoundSeconds);
			int tw = mc.textRenderer.getWidth(title);
			ctx.drawTextWithShadow(mc.textRenderer, title, (sw - tw) / 2, sh / 2 - 36, (textAlpha << 24) | (CrimsonTheme.GOLD & 0x00FFFFFF));
		}
	}

	public static void playVanillaSound(String vanillaPath, float pitch) {
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc == null) return;
			SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.ofVanilla(vanillaPath));
			if (ev != null) {
				mc.getSoundManager().play(PositionedSoundInstance.master(ev, pitch));
			}
		} catch (Exception ignored) {
		}
	}
}
