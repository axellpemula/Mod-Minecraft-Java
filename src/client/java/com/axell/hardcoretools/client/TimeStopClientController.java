package com.axell.hardcoretools.client;

import java.util.Locale;

import com.axell.hardcoretools.client.gui.CrimsonTheme;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Client-side visual & audio choreography for Chrono Time Stop:
 * - Expanding monochrome grayscale sphere (0.56s) -> full monochrome desaturated world -> collapsing sphere (0.58s).
 * - Layered bass warp + decelerating/accelerating clockwork ticks + crystal time-snap audio.
 * - Sleek HUD badge displaying frozen status and accumulated stacked hits/damage.
 */
public final class TimeStopClientController {
	private static final Gson GSON = new Gson();
	private static final long EXPAND_MS = 560L;
	private static final long COLLAPSE_MS = 580L;

	private static boolean active = false;
	private static long transitionStartMs = 0L;
	private static boolean collapsing = false;
	private static int stackedHits = 0;
	private static float stackedDamage = 0.0F;
	private static long lastHitPulseMs = 0L;

	// Timed clockwork sound steps during freeze / resume transitions
	private static int freezeTickStep = 99;
	private static int resumeTickStep = 99;
	private static int pendingFlushedHits = 0;

	private TimeStopClientController() {
	}

	public static boolean isActive() {
		return active;
	}

	public static void reset() {
		active = false;
		collapsing = false;
		transitionStartMs = 0L;
		stackedHits = 0;
		stackedDamage = 0.0F;
		lastHitPulseMs = 0L;
		freezeTickStep = 99;
		resumeTickStep = 99;
		pendingFlushedHits = 0;
	}

	public static void handleServerPayload(String json) {
		if (json == null || json.isBlank()) {
			return;
		}
		try {
			JsonObject obj = GSON.fromJson(json, JsonObject.class);
			if (obj == null) return;

			boolean newActive = obj.has("active") && obj.get("active").getAsBoolean();
			String event = obj.has("event") ? obj.get("event").getAsString() : "";
			int hits = obj.has("hits") ? obj.get("hits").getAsInt() : 0;
			float dmg = obj.has("damage") ? obj.get("damage").getAsFloat() : 0.0F;

			long now = System.currentTimeMillis();
			ClientStateSnapshot.INSTANCE.timeStopActive = newActive;

			if ("freeze".equals(event)) {
				active = true;
				collapsing = false;
				transitionStartMs = now;
				stackedHits = 0;
				stackedDamage = 0.0F;
				freezeTickStep = 0;
				resumeTickStep = 99;

				// Immediate Time-Stop Warp Impact: deep resonant bass drop + temporal seal
				playSound("block.respawn_anchor.deplete", 0.52F, 0.95F);
				playSound("block.beacon.deactivate", 0.58F, 0.90F);
				playSound("block.end_portal_frame.fill", 0.50F, 0.72F);
			} else if ("hit".equals(event)) {
				active = true;
				stackedHits = hits;
				stackedDamage = dmg;
				lastHitPulseMs = now;

				float comboPitch = Math.min(1.45F, 0.74F + hits * 0.045F);
				playSound("block.amethyst_cluster.hit", comboPitch, 0.68F);
				playSound("entity.player.attack.crit", 0.68F, 0.48F);
			} else if ("resume".equals(event)) {
				active = false;
				collapsing = true;
				transitionStartMs = now;
				pendingFlushedHits = hits;
				stackedHits = 0;
				stackedDamage = 0.0F;
				freezeTickStep = 99;
				resumeTickStep = 0;

				// Immediate first winding clock tick on resume
				playSound("ui.button.click", 0.74F, 0.52F);
			} else if ("sync".equals(event)) {
				active = newActive;
				collapsing = false;
				transitionStartMs = newActive ? (now - EXPAND_MS - 50L) : 0L;
				stackedHits = hits;
				stackedDamage = dmg;
			}
		} catch (Exception ignored) {
		}
	}

	public static void onClientTick(MinecraftClient client) {
		if (client == null || client.player == null) {
			return;
		}
		long now = System.currentTimeMillis();

		// Decelerating clockwork ticks as time grinds to 0%
		if (active && freezeTickStep < 3) {
			long elapsed = now - transitionStartMs;
			if (freezeTickStep == 0 && elapsed >= 110L) {
				freezeTickStep = 1;
				playSound("ui.button.click", 1.45F, 0.52F);
			} else if (freezeTickStep == 1 && elapsed >= 245L) {
				freezeTickStep = 2;
				playSound("ui.button.click", 1.02F, 0.50F);
			} else if (freezeTickStep == 2 && elapsed >= 420L) {
				freezeTickStep = 3;
				playSound("ui.button.click", 0.58F, 0.55F);
			}
		}

		// Accelerating clockwork ticks -> bright temporal snap as world resumes normal flow!
		if (collapsing && resumeTickStep < 3) {
			long elapsed = now - transitionStartMs;
			if (resumeTickStep == 0 && elapsed >= 95L) {
				resumeTickStep = 1;
				playSound("ui.button.click", 1.12F, 0.52F);
			} else if (resumeTickStep == 1 && elapsed >= 185L) {
				resumeTickStep = 2;
				playSound("ui.button.click", 1.56F, 0.55F);
				playSound("block.beacon.activate", 1.38F, 0.90F);
				playSound("block.amethyst_cluster.break", 0.88F, 0.82F);
				playSound("block.respawn_anchor.charge", 1.32F, 0.72F);
				if (pendingFlushedHits > 0) {
					playSound("entity.player.attack.knockback", 0.82F, 0.90F);
					pendingFlushedHits = 0;
				}
			} else if (resumeTickStep == 2 && elapsed >= COLLAPSE_MS) {
				resumeTickStep = 3;
				collapsing = false;
			}
		} else if (collapsing && (now - transitionStartMs) >= COLLAPSE_MS) {
			collapsing = false;
		}
	}

	public static void renderTimeStopHud(DrawContext ctx, RenderTickCounter tickCounter) {
		if (ReturnByDeathClientController.isActive()) {
			return;
		}
		if (!active && !collapsing) {
			return;
		}

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null) {
			return;
		}

		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		long now = System.currentTimeMillis();
		long elapsed = Math.max(0L, now - transitionStartMs);

		float grayRadiusFrac;
		if (active) {
			if (elapsed < EXPAND_MS) {
				double p = elapsed / (double) EXPAND_MS;
				double ease = 1.0D - Math.pow(1.0D - p, 3.2D);
				grayRadiusFrac = (float) (ease * 1.10D);
			} else {
				grayRadiusFrac = 1.12F;
			}
		} else {
			if (elapsed >= COLLAPSE_MS) {
				collapsing = false;
				return;
			}
			double p = elapsed / (double) COLLAPSE_MS;
			double ease = 1.0D - Math.pow(p, 2.4D);
			grayRadiusFrac = (float) Math.max(0.0D, ease * 1.10D);
		}

		// 1. Monochrome Desaturated Grayscale World Sphere / Full-Screen Filter
		ReturnByDeathClientController.renderGrayscaleWorldCircle(ctx, sw, sh, grayRadiusFrac);

		// 2. Subtle Initial Temporal Warp Flash (0..180ms on Freeze)
		if (active && elapsed < 180L) {
			double flashFade = 1.0D - (elapsed / 180.0D);
			int flashAlpha = (int) Math.round(68 * flashFade * flashFade);
			if (flashAlpha > 0) {
				ctx.fill(0, 0, sw, sh, (flashAlpha << 24) | 0xD8ECFF);
			}
		}

		// 3. Subtle Frozen Edge Vignette & Sleek Top-Center Time-Stop + Stacked Damage Pill
		if (active) {
			ctx.fillGradient(0, 0, sw, 22, 0x4805070B, 0x0005070B);
			ctx.fillGradient(0, sh - 22, sw, sh, 0x0005070B, 0x4805070B);

			String statusText = stackedHits > 0
					? String.format(Locale.ROOT, "⏸ TIME STOPPED  ·  ⚔ %d %s (%.1f DMG)",
							stackedHits, stackedHits == 1 ? "Hit" : "Hits", stackedDamage)
					: "⏸ TIME STOPPED";

			long sinceHit = now - lastHitPulseMs;
			float hitGlow = (sinceHit >= 0L && sinceHit < 260L) ? (1.0F - (sinceHit / 260.0F)) : 0.0F;

			int textW = mc.textRenderer.getWidth(statusText);
			int pillW = textW + 16;
			int pillH = 15;
			int pillX = (sw - pillW) / 2;
			int pillY = 8;

			int borderCol = stackedHits > 0
					? CrimsonTheme.lerpColor(0xFFB8D4F0, CrimsonTheme.GOLD, 0.45F + 0.55F * hitGlow)
					: 0xFF94A3B8;
			CrimsonTheme.drawRoundedRect(ctx, pillX, pillY, pillW, pillH, 4.5F, 0xCC090B10);
			CrimsonTheme.drawRoundedOutline(ctx, pillX, pillY, pillW, pillH, 4.5F, 0.55F, borderCol);
			ctx.drawTextWithShadow(mc.textRenderer, statusText, pillX + 8, pillY + 4,
					stackedHits > 0 ? (hitGlow > 0.2F ? CrimsonTheme.GOLD : 0xFFE2E8F0) : 0xFFE2E8F0);
		}
	}

	private static void playSound(String vanillaPath, float pitch, float volume) {
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc == null) return;
			SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.ofVanilla(vanillaPath));
			if (ev != null) {
				mc.getSoundManager().play(PositionedSoundInstance.master(ev, pitch, volume));
			}
		} catch (Exception ignored) {
		}
	}
}
