package com.axell.hardcoretools.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.axell.hardcoretools.client.gui.CrimsonTheme;
import com.axell.hardcoretools.client.gui.ReturnByDeathScreen;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
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
	private static final long LOCK_EFFECT_MS = 1400L;

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

	private static ShaderProgram chronoGrayscaleShader;
	private static SimpleFramebuffer snapshotFbo;

	private static boolean active = false;
	private static long startMs = 0L;
	private static long durationMs = 17500L;     // 0.0s - 17.5s total
	private static long act1Ms = 4500L;          // 0.0s - 4.5s  Locked Chained Death Screen
	private static long shatterMs = 6000L;       // 4.5s - 6.0s  Slow Full-Screen Spider-Web Cracks -> 6.0s Shatter!
	private static long rewindStartMs = 6600L;   // 6.6s - 15.0s Ultra-Smooth 8.4s Fast-Reverse Flight
	private static long act2Ms = 15000L;         // 15.0s - 17.5s Act 3 Shrinking Grayscale Dome
	private static String deathMessage = "You met a fatal end";
	private static int score = 0;
	private static boolean worldHardcore = false;
	private static double rewoundSeconds = 60.0D;
	private static List<RewindFrame> frames = List.of();

	// Multi-Act Sound Choreography State
	private static int lastHeartbeatIdx = 0;
	private static int lastCrackCreakIdx = -1;
	private static boolean playedGlassShatter = false;
	private static boolean playedGrayscaleBoom = false;
	private static boolean playedAct3Release = false;
	private static boolean playedColorAwakening = false;
	private static int lastClockTickIdx = -1;
	private static long lockShakeClickMs = 0L;
	private static int lockedButtonIndex = -1;

	// Vanilla-style "soul leaving the body" death camera state
	private static Perspective savedPerspective = null;
	private static boolean perspectiveForced = false;


	private ReturnByDeathClientController() {
	}

	public static void setChronoGrayscaleShader(ShaderProgram program) {
		chronoGrayscaleShader = program;
	}

	public static ShaderProgram getChronoGrayscaleShader() {
		return chronoGrayscaleShader;
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

	public static long getShatterMs() {
		return shatterMs;
	}

	public static long getRewindStartMs() {
		return rewindStartMs;
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

	/** -1 = nothing locked, otherwise the index of the death-screen button that was just bound. */
	public static int getLockedButtonIndex() {
		return lockedButtonIndex;
	}

	/** 1.0 right after the click, fading down to 0.0 over 1.4s (0.0 = no chains drawn). */
	public static float getLockProgress() {
		if (lockShakeClickMs <= 0L) {
			return 0.0F;
		}
		long since = System.currentTimeMillis() - lockShakeClickMs;
		if (since < 0L || since >= LOCK_EFFECT_MS) {
			return 0.0F;
		}
		return 1.0F - (since / (float) LOCK_EFFECT_MS);
	}

	public static void triggerLockedButton(int index) {
		lockedButtonIndex = index;
		lockShakeClickMs = System.currentTimeMillis();
		playVanillaSound("block.chain.place", 0.72F);
		playVanillaSound("block.chain.hit", 1.32F);
		playVanillaSound("block.respawn_anchor.deplete", 1.45F);
	}

	/** 0.0 -> 1.0 -> 0.0 pulse used to swing the chains in and settle them (1.4s total). */
	public static float getChainBindProgress() {
		float p = 1.0F - getLockProgress();
		if (p <= 0.0F) {
			return 0.0F;
		}
		return (float) easeInOutCubic(p);
	}

	/**
	 * Authentic-Vanilla "soul leaving the body" death camera.
	 * <p>
	 * Vanilla itself does NOT tilt the view sideways: on death it forces third-person and the
	 * camera keeps the player's look angle, so you hover a few blocks behind/above your own body.
	 * Because the player entity keeps that rotation, holding a straight look angle means the camera
	 * looks down at the corpse from above — exactly the "zoom out & stare down at your whole body"
	 * framing people remember.
	 * <p>
	 * Timeline:
	 * - 0.00s .. 0.55s: camera pulls out and swings up above the body (smooth ease-out)
	 * - 0.55s .. 6.00s: hovers above/behind the corpse while items scatter, tiny breathing drift
	 * - 6.00s .. 7.20s: glides back down into the body while the world rewinds
	 * - Rotation roll is only a tiny 3D "slump" wobble, never a sideways first-person tilt.
	 */
	public static float getDeathCameraRollDegrees() {
		if (!active) return 0.0F;
		long elapsed = getElapsedMs();
		if (elapsed >= shatterMs + 1200L) {
			return 0.0F;
		}
		// Gentle astral sway only — vanilla stays upright, so keep this subtle (max ~3.5°)
		double sway = Math.sin(elapsed * 0.0016D) * 3.5D;
		double fade;
		if (elapsed < 600L) {
			fade = elapsed / 600.0D;
		} else if (elapsed < shatterMs) {
			fade = 1.0D;
		} else {
			fade = Math.max(0.0D, 1.0D - (elapsed - shatterMs) / 1200.0D);
		}
		return (float) (sway * fade);
	}

	/** 0.0 -> 1.0: how far the death camera has swung out & above the corpse. */
	private static double deathCameraOutAmount(long elapsed) {
		if (elapsed < 550L) {
			double t = elapsed / 550.0D;
			return 1.0D - Math.pow(1.0D - t, 3.0D);
		}
		if (elapsed < shatterMs) {
			return 1.0D;
		}
		double riseT = Math.min(1.0D, (elapsed - shatterMs) / Math.max(1.0D, rewindStartMs - shatterMs));
		return 1.0D - easeInOutSine(riseT);
	}

	/**
	 * Expanding & Shrinking Circular Grayscale Dome Fraction (0.0 = none, 1.08 = covers 100% of screen corners):
	 * - 0.0s .. 5.85s: 0.0
	 * - 5.85s .. 7.15s: expands smoothly from center (0.0 -> 1.08) right after glass shatters!
	 * - 7.15s .. 15.0s: stays at 1.08 (entire world vision is monochrome gray during fast-reverse!)
	 * - 15.0s .. 17.5s: shrinks smoothly in Act 3 (1.08 -> 0.0) revealing natural world colors!
	 */
	public static float getGrayscaleRadiusFraction(long elapsed) {
		long expandStart = shatterMs - 150L; // 5850ms
		long expandEnd = shatterMs + 1150L;  // 7150ms
		if (elapsed <= expandStart) {
			return 0.0F;
		}
		if (elapsed < expandEnd) {
			double t = (elapsed - expandStart) / (double) Math.max(1L, expandEnd - expandStart);
			return (float) (easeInOutCubic(t) * 1.08D);
		}
		if (elapsed < act2Ms) {
			return 1.08F;
		}
		if (elapsed < durationMs) {
			double t = (elapsed - act2Ms) / (double) Math.max(1L, durationMs - act2Ms);
			return (float) ((1.0D - easeInOutCubic(t)) * 1.08D);
		}
		return 0.0F;
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

			durationMs = r.has("durationMs") ? r.get("durationMs").getAsLong() : 17500L;
			act1Ms = r.has("act1Ms") ? r.get("act1Ms").getAsLong() : 4500L;
			shatterMs = r.has("shatterMs") ? r.get("shatterMs").getAsLong() : 6000L;
			rewindStartMs = r.has("rewindStartMs") ? r.get("rewindStartMs").getAsLong() : 6600L;
			act2Ms = r.has("act2Ms") ? r.get("act2Ms").getAsLong() : 15000L;
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
			lastHeartbeatIdx = 0;
			lastCrackCreakIdx = -1;
			playedGlassShatter = false;
			playedGrayscaleBoom = false;
			playedAct3Release = false;
			playedColorAwakening = false;
			lastClockTickIdx = -1;
			lockShakeClickMs = 0L;
			lockedButtonIndex = -1;
			perspectiveForced = false;

			// Act 1 (0.0s): Heavy Death Impact + Abyssal Bell + Witch's Call
			playVanillaSound("entity.warden.heartbeat", 0.64F);
			playVanillaSound("block.bell.resonate", 0.50F);
			playVanillaSound("entity.elder_guardian.curse", 0.52F);

			MinecraftClient mc = MinecraftClient.getInstance();
			ClientStateSnapshot.INSTANCE.upgraderModalOpen = false;
			mc.setScreen(new ReturnByDeathScreen());
		} catch (Exception ignored) {
		}
	}

	public static void reset() {
		active = false;
		frames = List.of();
		lockShakeClickMs = 0L;
		lockedButtonIndex = -1;
		restoreDeathCamera(MinecraftClient.getInstance());
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
		if (elapsed >= rewindStartMs) {
			restoreDeathCamera(client);
		}

		// Keep ReturnByDeathScreen open through Act 1 (0..4.5s) & Act 2 (4.5..15.0s), then release at 15.0s for Act 3!
		if (elapsed < act2Ms) {
			if (!(client.currentScreen instanceof ReturnByDeathScreen)) {
				client.setScreen(new ReturnByDeathScreen());
			}
		} else if (client.currentScreen instanceof ReturnByDeathScreen) {
			client.setScreen(null);
		}

		// 1. Act 1 (0.0s - 4.5s): Slow ominous heartbeats & tightening chains
		if (elapsed < act1Ms) {
			int beatIdx = (int) (elapsed / 1120L); // Beats at ~1.12s, 2.24s, 3.36s
			if (beatIdx > lastHeartbeatIdx) {
				lastHeartbeatIdx = beatIdx;
				playVanillaSound("entity.warden.heartbeat", 0.68F + beatIdx * 0.08F);
				if (beatIdx >= 2) {
					playVanillaSound("block.chain.place", 0.58F + beatIdx * 0.08F);
				}
			}
		}

		// 2. Act 2A (4.5s - 6.0s): Slow creeping glass fracture creaks across the screen!
		if (elapsed >= act1Ms && elapsed < shatterMs) {
			double crackP = (elapsed - act1Ms) / (double) Math.max(1L, shatterMs - act1Ms);
			int creakIdx = (int) (crackP * 6.0D);
			if (creakIdx != lastCrackCreakIdx) {
				lastCrackCreakIdx = creakIdx;
				playVanillaSound("block.glass.hit", 0.58F + creakIdx * 0.14F);
				playVanillaSound("block.amethyst_block.chime", 0.52F + creakIdx * 0.10F);
			}
		}

		// 3. 6.0s Climax: Full-Screen Glass Shatter + Expanding Grayscale Time-Freeze!
		if (!playedGlassShatter && elapsed >= shatterMs) {
			playedGlassShatter = true;
			playVanillaSound("block.glass.break", 0.68F);
			playVanillaSound("block.glass.break", 1.08F);
			playVanillaSound("block.respawn_anchor.deplete", 0.75F);
		}
		if (!playedGrayscaleBoom && elapsed >= shatterMs + 180L) {
			playedGrayscaleBoom = true;
			playVanillaSound("block.beacon.deactivate", 0.62F);
			playVanillaSound("block.portal.trigger", 1.55F);
		}

		// 4. Act 2C (6.6s - 15.0s): Smooth 8.4s pocket-watch reverse ticks & astral chimes
		if (elapsed >= rewindStartMs && elapsed < act2Ms) {
			double rawP = (elapsed - rewindStartMs) / (double) Math.max(1L, act2Ms - rewindStartMs);
			int tickIdx = (int) (easeInOutSine(rawP) * 44.0D);
			if (tickIdx != lastClockTickIdx) {
				lastClockTickIdx = tickIdx;
				float tickPitch = (tickIdx % 2 == 0) ? 1.56F : 1.24F;
				playVanillaSound("block.comparator.click", tickPitch);
				if (tickIdx % 4 == 0) {
					float chimePitch = 0.60F + (float) (rawP * 0.95F);
					playVanillaSound("block.amethyst_block.chime", chimePitch);
				}
			}
		}

		// 5. Act 3 (15.0s - 17.5s): Shrinking Grayscale Dome & Return of World Color!
		if (!playedAct3Release && elapsed >= act2Ms) {
			playedAct3Release = true;
			playVanillaSound("block.beacon.activate", 1.18F);
			playVanillaSound("block.respawn_anchor.set_spawn", 1.15F);
			playVanillaSound("entity.player.breath", 0.92F);
		}
		if (!playedColorAwakening && elapsed >= durationMs - 380L) {
			playedColorAwakening = true;
			playVanillaSound("block.amethyst_cluster.break", 1.35F);
			playVanillaSound("entity.warden.heartbeat", 1.0F);
		}
	}

	/**
	 * Runs right before Camera#update in GameRenderer#renderWorld at native monitor FPS (60/144/240 Hz):
	 * - During 0.0s..6.6s: force third-person so the camera floats out & above the body (vanilla death look)
	 * - During 6.6s..15.0s: glides backward along a C1-continuous Catmull-Rom cubic spline across the last 60s!
	 */
	public static void onBeforeWorldRender() {
		if (!active) {
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

		// Vanilla-style death framing runs even without recorded frames (e.g. freshly joined worlds)
		if (elapsed < rewindStartMs) {
			applyDeathCameraFraming(mc, elapsed);
		} else {
			restoreDeathCamera(mc);
		}

		if (frames.isEmpty()) {
			return;
		}

		String currentDim = mc.world.getRegistryKey().getValue().toString();

		if (elapsed < rewindStartMs) {
			RewindFrame first = frames.get(0);
			if (first.dim.equals(currentDim)) {
				// Act 1: camera floats out of the body and cranes DOWN over it — the classic vanilla
				// "soul leaves the body, you look at your own corpse from above" framing.
				double out = deathCameraOutAmount(elapsed);
				float overheadPitch = MathHelper.clamp((float) (42.0D * out), -90.0F, 90.0F);
				double breath = Math.sin(elapsed * 0.0016D) * 0.06D * out;
				applySmoothPlayerCamera(player, first.x, first.y + breath, first.z, first.yaw, overheadPitch);
			}
			return;
		}

		// Act 2C (6.6s - 15.0s): 8.4s Ultra-Smooth Catmull-Rom Cubic Spline Reverse Flight!
		double rawP = (elapsed - rewindStartMs) / (double) Math.max(1L, act2Ms - rewindStartMs);
		double easedP = easeInOutSine(rawP);

		int segIdx = 0;
		for (int i = 0; i < frames.size() - 1; i++) {
			if (easedP >= frames.get(i).p && easedP <= frames.get(i + 1).p) {
				segIdx = i;
				break;
			}
			if (easedP > frames.get(i + 1).p) {
				segIdx = i;
			}
		}

		RewindFrame p1 = frames.get(segIdx);
		RewindFrame p2 = frames.get(Math.min(frames.size() - 1, segIdx + 1));
		if (!p1.dim.equals(currentDim) || !p2.dim.equals(currentDim)) {
			return;
		}

		RewindFrame p0 = frames.get(Math.max(0, segIdx - 1));
		RewindFrame p3 = frames.get(Math.min(frames.size() - 1, segIdx + 2));
		if (!p0.dim.equals(currentDim)) p0 = p1;
		if (!p3.dim.equals(currentDim)) p3 = p2;

		double span = Math.max(0.00001D, p2.p - p1.p);
		float t = (float) Math.max(0.0D, Math.min(1.0D, (easedP - p1.p) / span));

		double ix = catmullRom(t, p0.x, p1.x, p2.x, p3.x);
		double iy = catmullRom(t, p0.y, p1.y, p2.y, p3.y);
		double iz = catmullRom(t, p0.z, p1.z, p2.z, p3.z);
		float iyaw = MathHelper.lerpAngleDegrees(t, p1.yaw, p2.yaw);
		float ipitch = MathHelper.lerp(t, p1.pitch, p2.pitch);

		applySmoothPlayerCamera(player, ix, iy, iz, iyaw, ipitch);

		long isky = Math.round(p1.skyTime + (p2.skyTime - p1.skyTime) * t);
		if (isky >= 0L && "minecraft:overworld".equals(currentDim)) {
			mc.world.setTimeOfDay(isky);
		}
	}

	/**
	 * Vanilla-style death camera framing: force third-person ("out from the body") and tilt the head
	 * down so the whole corpse is visible from above, then release everything once the rewind starts.
	 */
	private static void applyDeathCameraFraming(MinecraftClient mc, long elapsed) {
		if (mc.options == null || mc.player == null) {
			return;
		}
		boolean wantThirdPerson = elapsed < shatterMs;

		if (wantThirdPerson) {
			if (!perspectiveForced) {
				savedPerspective = mc.options.getPerspective();
				perspectiveForced = true;
			}
			if (mc.options.getPerspective().isFirstPerson()) {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			}
		} else if (perspectiveForced) {
			restoreDeathCamera(mc);
		}
	}

	private static void restoreDeathCamera(MinecraftClient mc) {
		if (mc == null || mc.options == null) {
			return;
		}
		if (perspectiveForced) {
			mc.options.setPerspective(savedPerspective == null ? Perspective.FIRST_PERSON : savedPerspective);
			perspectiveForced = false;
			savedPerspective = null;
		}
	}

	private static double catmullRom(float t, double p0, double p1, double p2, double p3) {
		double t2 = t * t;
		double t3 = t2 * t;
		return 0.5D * ((2.0D * p1)
				+ (-p0 + p2) * t
				+ (2.0D * p0 - 5.0D * p1 + 4.0D * p2 - p3) * t2
				+ (-p0 + 3.0D * p1 - 3.0D * p2 + p3) * t3);
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
	 * Renders a True Circular Luminance-Grayscale World Lens of radius `radiusPx` centered on `(cx, cy)`.
	 * Samples the live rendered 3D world from `mainFbo` so everything inside the circle appears in crisp
	 * monochrome gray while everything outside the circle stays in natural full color!
	 */
	public static void renderGrayscaleWorldCircle(DrawContext ctx, int sw, int sh, float radiusFraction) {
		if (radiusFraction <= 0.002F || sw <= 0 || sh <= 0) {
			return;
		}
		ctx.draw();

		MinecraftClient mc = MinecraftClient.getInstance();
		Framebuffer mainFbo = mc.getFramebuffer();
		float cx = sw * 0.5F;
		float cy = sh * 0.5F;
		float halfDiag = (float) Math.hypot(cx, cy);
		float coreRadius = halfDiag * radiusFraction;
		float featherW = Math.min(28.0F, Math.max(8.0F, coreRadius * 0.08F));

		boolean canUseShader = (chronoGrayscaleShader != null && mainFbo != null && mainFbo.textureWidth > 0 && mainFbo.textureHeight > 0);
		if (canUseShader) {
			int fbW = mainFbo.textureWidth;
			int fbH = mainFbo.textureHeight;
			if (snapshotFbo == null) {
				snapshotFbo = new SimpleFramebuffer(fbW, fbH, false, MinecraftClient.IS_SYSTEM_MAC);
			} else if (snapshotFbo.textureWidth != fbW || snapshotFbo.textureHeight != fbH) {
				snapshotFbo.resize(fbW, fbH, MinecraftClient.IS_SYSTEM_MAC);
			}

			// Copy live 3D world color buffer into snapshotFbo, then restore mainFbo as active draw target
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo.fbo);
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshotFbo.fbo);
			GL30.glBlitFramebuffer(0, 0, fbW, fbH, 0, 0, fbW, fbH, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
			mainFbo.beginWrite(false);

			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableCull();
			RenderSystem.disableDepthTest();
			RenderSystem.setShader(ReturnByDeathClientController::getChronoGrayscaleShader);
			RenderSystem.setShaderTexture(0, snapshotFbo.getColorAttachment());
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

			Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
			int segs = 72;
			float innerSolidR = Math.max(0.0F, coreRadius - featherW * 0.5F);
			float outerEdgeR = coreRadius + featherW * 0.5F;

			// 1. Solid inner monochrome disk
			if (innerSolidR > 0.5F) {
				BufferBuilder fan = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_TEXTURE_COLOR);
				fan.vertex(mat, cx, cy, 0.0F).texture(cx / sw, 1.0F - (cy / sh)).color(255, 255, 255, 255);
				for (int i = 0; i <= segs; i++) {
					double a = (i * Math.PI * 2.0D) / segs;
					float px = cx + (float) Math.cos(a) * innerSolidR;
					float py = cy + (float) Math.sin(a) * innerSolidR;
					fan.vertex(mat, px, py, 0.0F).texture(px / sw, 1.0F - (py / sh)).color(255, 255, 255, 255);
				}
				BuiltBuffer built = fan.endNullable();
				if (built != null) {
					BufferRenderer.drawWithGlobalProgram(built);
				}
			}

			// 2. Soft feathered outer ring so the expanding/shrinking gray boundary glides like silk!
			if (radiusFraction < 1.03F) {
				BufferBuilder skirt = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_TEXTURE_COLOR);
				for (int i = 0; i <= segs; i++) {
					double a = (i * Math.PI * 2.0D) / segs;
					float cos = (float) Math.cos(a);
					float sin = (float) Math.sin(a);
					float ix = cx + cos * innerSolidR;
					float iy = cy + sin * innerSolidR;
					float ox = cx + cos * outerEdgeR;
					float oy = cy + sin * outerEdgeR;
					skirt.vertex(mat, ix, iy, 0.0F).texture(ix / sw, 1.0F - (iy / sh)).color(255, 255, 255, 255);
					skirt.vertex(mat, ox, oy, 0.0F).texture(ox / sw, 1.0F - (oy / sh)).color(255, 255, 255, 0);
				}
				BuiltBuffer builtSkirt = skirt.endNullable();
				if (builtSkirt != null) {
					BufferRenderer.drawWithGlobalProgram(builtSkirt);
				}
			}
		} else {
			// Fallback translucent ash-gray circle if custom core shader wasn't initialized
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableCull();
			RenderSystem.disableDepthTest();
			RenderSystem.setShader(GameRenderer::getPositionColorProgram);
			Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
			BufferBuilder fan = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
			fan.vertex(mat, cx, cy, 0.0F).color(0xB87A7E87);
			int segs = 64;
			for (int i = 0; i <= segs; i++) {
				double a = (i * Math.PI * 2.0D) / segs;
				fan.vertex(mat, cx + (float) Math.cos(a) * coreRadius, cy + (float) Math.sin(a) * coreRadius, 0.0F).color(0xB87A7E87);
			}
			BuiltBuffer built = fan.endNullable();
			if (built != null) {
				BufferRenderer.drawWithGlobalProgram(built);
			}
		}

		// 3. Luminous Silver-White Boundary Iris Ring whenever the circle edge is visible inside the screen!
		if (radiusFraction > 0.01F && radiusFraction < 1.02F) {
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
			RenderSystem.disableCull();
			RenderSystem.disableDepthTest();
			RenderSystem.setShader(GameRenderer::getPositionColorProgram);

			Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
			int segs = 72;
			float rIn = Math.max(0.0F, coreRadius - 4.0F);
			float rOut = coreRadius + 4.0F;
			BufferBuilder rim = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
			for (int i = 0; i <= segs; i++) {
				double a = (i * Math.PI * 2.0D) / segs;
				float cos = (float) Math.cos(a);
				float sin = (float) Math.sin(a);
				rim.vertex(mat, cx + cos * rIn, cy + sin * rIn, 0.0F).color(0x00DCE4F5);
				rim.vertex(mat, cx + cos * coreRadius, cy + sin * coreRadius, 0.0F).color(0xCCDCE4F5);
			}
			BuiltBuffer b1 = rim.endNullable();
			if (b1 != null) BufferRenderer.drawWithGlobalProgram(b1);

			BufferBuilder rimOut = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
			for (int i = 0; i <= segs; i++) {
				double a = (i * Math.PI * 2.0D) / segs;
				float cos = (float) Math.cos(a);
				float sin = (float) Math.sin(a);
				rimOut.vertex(mat, cx + cos * coreRadius, cy + sin * coreRadius, 0.0F).color(0xCCDCE4F5);
				rimOut.vertex(mat, cx + cos * rOut, cy + sin * rOut, 0.0F).color(0x00DCE4F5);
			}
			BuiltBuffer b2 = rimOut.endNullable();
			if (b2 != null) BufferRenderer.drawWithGlobalProgram(b2);
		}

		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	/**
	 * Babak 3 (15.0s - 17.5s): Shrinking Grayscale Sphere & Return of World Color HUD Overlay!
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

		// 1. Shrinking Monochrome Grayscale Sphere (1.08 -> 0.0 over 15.0s .. 17.5s)
		float grayFrac = getGrayscaleRadiusFraction(elapsed);
		renderGrayscaleWorldCircle(ctx, sw, sh, grayFrac);

		double p = (elapsed - act2Ms) / (double) Math.max(1L, durationMs - act2Ms); // 0.0 .. 1.0 over 2.5s
		double vigFade = Math.pow(1.0D - Math.min(1.0D, p), 1.6D);

		// 2. Soft Cinema Letterbox Vignette lifting away as color returns
		int vigAlpha = (int) Math.round(145 * vigFade);
		if (vigAlpha > 0) {
			ctx.fillGradient(0, 0, sw, 40, (vigAlpha << 24) | 0x101218, 0x00101218);
			ctx.fillGradient(0, sh - 40, sw, sh, 0x00101218, (vigAlpha << 24) | 0x101218);
		}

		// 3. Final Singularity Sparkle at center right as the shrinking gray circle vanishes (p = 0.82 .. 1.0)
		if (p > 0.80D) {
			double endT = (p - 0.80D) / 0.20D;
			int sparkAlpha = (int) Math.round(190 * Math.sin(endT * Math.PI));
			if (sparkAlpha > 4) {
				ctx.draw();
				RenderSystem.enableBlend();
				RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
				RenderSystem.disableCull();
				RenderSystem.disableDepthTest();
				RenderSystem.setShader(GameRenderer::getPositionColorProgram);
				Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
				float cx = sw * 0.5F, cy = sh * 0.5F;
				float r = (float) (28.0D * (1.0D - endT * 0.5D));
				BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
				buf.vertex(mat, cx, cy, 0.0F).color((sparkAlpha << 24) | 0xFFF8E8);
				for (int i = 0; i <= 32; i++) {
					double a = (i * Math.PI * 2.0D) / 32.0D;
					buf.vertex(mat, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 0.0F).color(0x00FFF8E8);
				}
				BuiltBuffer built = buf.endNullable();
				if (built != null) BufferRenderer.drawWithGlobalProgram(built);
				RenderSystem.enableDepthTest();
				RenderSystem.enableCull();
				RenderSystem.defaultBlendFunc();
				RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			}
		}

		// 4. Crisp Awakening Banner
		int textAlpha = Math.max(0, Math.min(255, (int) Math.round(255 * Math.sin(Math.min(1.0D, p) * Math.PI))));
		if (textAlpha > 18 && mc.textRenderer != null) {
			String title = String.format(Locale.ROOT, "✦ RETURNED BY DEATH -%.1fs ✦", rewoundSeconds);
			int tw = mc.textRenderer.getWidth(title);
			ctx.drawTextWithShadow(mc.textRenderer, title, (sw - tw) / 2, sh - 52, (textAlpha << 24) | (CrimsonTheme.GOLD & 0x00FFFFFF));
		}
	}

	public static double easeInOutSine(double t) {
		double c = Math.max(0.0D, Math.min(1.0D, t));
		return -(Math.cos(Math.PI * c) - 1.0D) / 2.0D;
	}

	public static double easeInOutCubic(double t) {
		double c = Math.max(0.0D, Math.min(1.0D, t));
		return c < 0.5D ? 4.0D * c * c * c : 1.0D - Math.pow(-2.0D * c + 2.0D, 3.0D) / 2.0D;
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
