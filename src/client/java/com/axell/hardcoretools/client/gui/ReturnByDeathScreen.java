package com.axell.hardcoretools.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.joml.Matrix4f;

import com.axell.hardcoretools.HardcoreToolsMod;
import com.axell.hardcoretools.client.ReturnByDeathClientController;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 20s Return by Death Cinema Screen (Acts 1 & 2: 0.0s - 17.0s):
 * - Act 1  (0.0s - 5.5s):  Locked Chained Death Screen, rising soul embers & slow camera orbit over collapsed corpse
 * - Act 2A (5.5s - 7.0s):  18-vein prismatic spider-web glass cracks & travelling tip sparks across the ENTIRE screen
 * - Act 2B (7.0s - 8.95s): Full-screen 64-shard tempered glass explosion, dual shockwave & expanding grayscale dome
 * - Act 2C (7.6s - 17.0s): 9.4s ultra-smooth monochrome world fast-reverse, peripheral warp streaks & dual-ring chronometer
 * (Closes automatically at 17.0s so Act 3 shrinks the grayscale dome smoothly over 17.0s - 20.0s!)
 */
public class ReturnByDeathScreen extends Screen {
	private record CrackSegment(
			float x1, float y1,
			float x2, float y2,
			float startT, float endT,
			float widthPx,
			boolean primary
	) {
	}

	private record GlassShard(
			float cxPct, float cyPct,
			float vx, float vy,
			float rotSpeed, float flipSpeed,
			float radiusPx,
			float[] localAngles,
			float[] localRadii,
			int tintKind
	) {
	}

	private final List<CrackSegment> crackSegments = new ArrayList<>();
	private final List<GlassShard> glassShards = new ArrayList<>();
	private int builtWidth = -1;
	private int builtHeight = -1;

	public ReturnByDeathScreen() {
		super(Text.literal("Return by Death"));
	}

	@Override
	protected void init() {
		super.init();
		rebuildFractureGeometryIfNeeded(this.width, this.height);
	}

	private void rebuildFractureGeometryIfNeeded(int sw, int sh) {
		if (sw <= 0 || sh <= 0 || (sw == builtWidth && sh == builtHeight && !crackSegments.isEmpty())) {
			return;
		}
		builtWidth = sw;
		builtHeight = sh;
		crackSegments.clear();
		glassShards.clear();

		Random rng = new Random(0x524244313731L);
		float cx = sw * 0.5F;
		float cy = sh * 0.50F;
		float maxReach = (float) Math.hypot(cx, cy) * 1.08F;

		// 1. Build 18 full-screen multi-step radial fracture veins + diagonal forks + micro-splinters + 5 spider-web stress rings
		int veinCount = 18;
		int stepsPerVein = 8;
		float[][] veinNodesX = new float[veinCount][stepsPerVein + 1];
		float[][] veinNodesY = new float[veinCount][stepsPerVein + 1];

		for (int v = 0; v < veinCount; v++) {
			double baseAngle = (v * Math.PI * 2.0D / veinCount) + (rng.nextDouble() - 0.5D) * 0.20D;
			float curX = cx;
			float curY = cy;
			veinNodesX[v][0] = curX;
			veinNodesY[v][0] = curY;
			double curAngle = baseAngle;

			for (int s = 1; s <= stepsPerVein; s++) {
				float stepFrac0 = (s - 1) / (float) stepsPerVein;
				float stepFrac1 = s / (float) stepsPerVein;
				curAngle += (rng.nextDouble() - 0.5D) * 0.32D;
				float dist = maxReach * stepFrac1 * (0.90F + rng.nextFloat() * 0.18F);
				float nx = cx + (float) Math.cos(curAngle) * dist;
				float ny = cy + (float) Math.sin(curAngle) * dist;

				float startT = stepFrac0 * 0.78F + rng.nextFloat() * 0.05F;
				float endT = Math.min(0.98F, startT + 0.15F);
				float thickness = Math.max(1.1F, 2.6F - stepFrac0 * 1.25F);
				crackSegments.add(new CrackSegment(curX, curY, nx, ny, startT, endT, thickness, true));

				// Secondary diagonal glass fork + optional tertiary micro-hairline splinter
				if (s >= 2 && s <= 7 && rng.nextFloat() < 0.76F) {
					double side = rng.nextBoolean() ? 1.0D : -1.0D;
					double forkAng = curAngle + side * (0.40D + rng.nextDouble() * 0.38D);
					float forkLen = maxReach * (0.10F + rng.nextFloat() * 0.10F);
					float fx = nx + (float) Math.cos(forkAng) * forkLen;
					float fy = ny + (float) Math.sin(forkAng) * forkLen;
					float fStart = Math.min(0.91F, endT - 0.03F);
					float fEnd = Math.min(0.99F, fStart + 0.13F);
					crackSegments.add(new CrackSegment(nx, ny, fx, fy, fStart, fEnd, 1.18F, false));

					if (rng.nextFloat() < 0.52F) {
						double subAng = forkAng - side * (0.32D + rng.nextDouble() * 0.30D);
						float subLen = forkLen * (0.50F + rng.nextFloat() * 0.32F);
						float sx = fx + (float) Math.cos(subAng) * subLen;
						float sy = fy + (float) Math.sin(subAng) * subLen;
						float sStart = Math.min(0.94F, fEnd - 0.02F);
						crackSegments.add(new CrackSegment(fx, fy, sx, sy, sStart, Math.min(1.0F, sStart + 0.09F), 0.85F, false));
					}
				}

				veinNodesX[v][s] = nx;
				veinNodesY[v][s] = ny;
				curX = nx;
				curY = ny;
			}
		}

		// Concentric spider-web stress rings connecting adjacent veins
		int[] webRings = {1, 2, 4, 5, 7};
		for (int ringStep : webRings) {
			float ringBaseT = 0.16F + (ringStep / (float) stepsPerVein) * 0.72F;
			for (int v = 0; v < veinCount; v++) {
				if (rng.nextFloat() > 0.84F) continue;
				int nextV = (v + 1) % veinCount;
				float x1 = veinNodesX[v][ringStep];
				float y1 = veinNodesY[v][ringStep];
				float x2 = veinNodesX[nextV][ringStep];
				float y2 = veinNodesY[nextV][ringStep];
				float st = Math.min(0.93F, ringBaseT + rng.nextFloat() * 0.06F);
				crackSegments.add(new CrackSegment(x1, y1, x2, y2, st, Math.min(1.0F, st + 0.10F), 1.05F, false));
			}
		}

		// 2. Build 64 Full-Screen Tempered Glass Shards distributed across 4 concentric rings to the corners!
		int[] ringCounts = {10, 16, 20, 18};
		float[] ringRadiusPct = {0.11F, 0.25F, 0.42F, 0.62F};
		for (int rIdx = 0; rIdx < ringCounts.length; rIdx++) {
			int count = ringCounts[rIdx];
			float rPct = ringRadiusPct[rIdx];
			for (int i = 0; i < count; i++) {
				double ang = (i * Math.PI * 2.0D / count) + (rng.nextDouble() - 0.5D) * (0.42D / (rIdx + 1));
				float distX = (float) Math.cos(ang) * rPct * (0.88F + rng.nextFloat() * 0.24F);
				float distY = (float) Math.sin(ang) * rPct * (0.88F + rng.nextFloat() * 0.24F);
				float cxPct = 0.5F + distX;
				float cyPct = 0.5F + distY;

				float speed = (145.0F + rIdx * 82.0F) + rng.nextFloat() * 150.0F;
				float vx = (float) Math.cos(ang) * speed + (rng.nextFloat() - 0.5F) * 48.0F;
				float vy = (float) Math.sin(ang) * speed - (40.0F + rng.nextFloat() * 68.0F);
				float rotSpd = (rng.nextFloat() - 0.5F) * 9.0F;
				float flipSpd = (2.6F + rng.nextFloat() * 5.8F) * (rng.nextBoolean() ? 1.0F : -1.0F);
				float baseRad = (13.0F + rIdx * 8.5F) + rng.nextFloat() * 15.0F;

				int verts = 3 + rng.nextInt(3); // 3, 4, or 5-sided irregular shard
				float[] lAng = new float[verts];
				float[] lRad = new float[verts];
				for (int k = 0; k < verts; k++) {
					lAng[k] = (float) ((k * Math.PI * 2.0D / verts) + (rng.nextDouble() - 0.5D) * 0.48D);
					lRad[k] = baseRad * (0.66F + rng.nextFloat() * 0.48F);
				}

				int tintKind = ((i + rIdx) % 4 == 0) ? 1 : (((i + rIdx * 2) % 5 == 0) ? 2 : 0);
				glassShards.add(new GlassShard(
						cxPct, cyPct, vx, vy, rotSpd, flipSpd, baseRad,
						lAng, lRad, tintKind
				));
			}
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Transparent background so the live collapsed camera, scattered 3D items, and rewinding world remain visible!
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		long elapsed = ReturnByDeathClientController.getElapsedMs();
		if (elapsed < ReturnByDeathClientController.getShatterMs()) {
			int bx = buttonX();
			for (int i = 0; i < BUTTON_COUNT; i++) {
				int by = buttonY(i);
				if (mouseX >= bx && mouseX <= bx + BUTTON_W && mouseY >= by && mouseY <= by + BUTTON_H) {
					// Only the clicked button gets bound by chains — vanilla-clean otherwise.
					ReturnByDeathClientController.triggerLockedButton(i);
					return true;
				}
			}
		}
		return true;
	}

	private static final int BUTTON_COUNT = 2;
	private static final int BUTTON_W = 200;
	private static final int BUTTON_H = 20;
	private static final int BUTTON_GAP = 4;
	private static final int BUTTON_TOP_OFFSET = 72;

	// Single source of truth for the death-button bounds, shared by rendering, hover
	// detection and mouseClicked so the clickable area always matches the drawn button.
	// Buttons intentionally do NOT follow the glass-pane jitter: a button that wobbles
	// under the cursor has no stable boundary and clicks land outside it.
	private int buttonX() {
		return (this.width - BUTTON_W) / 2;
	}

	private int buttonY(int index) {
		return this.height / 4 + BUTTON_TOP_OFFSET + index * (BUTTON_H + BUTTON_GAP);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		if (!ReturnByDeathClientController.isActive()) {
			if (this.client != null) {
				this.client.setScreen(null);
			}
			return;
		}
		rebuildFractureGeometryIfNeeded(this.width, this.height);

		long elapsed = ReturnByDeathClientController.getElapsedMs();
		long act1Ms = ReturnByDeathClientController.getAct1Ms();               // 5500ms
		long shatterMs = ReturnByDeathClientController.getShatterMs();         // 7000ms
		long rewindStartMs = ReturnByDeathClientController.getRewindStartMs(); // 7600ms
		long act2Ms = ReturnByDeathClientController.getAct2Ms();               // 17000ms

		// 1. Expanding Monochrome Grayscale World Dome (begins at 6.85s and covers 100% of screen by 8.15s!)
		float grayFrac = ReturnByDeathClientController.getGrayscaleRadiusFraction(elapsed);
		if (grayFrac > 0.002F) {
			ReturnByDeathClientController.renderGrayscaleWorldCircle(ctx, this.width, this.height, grayFrac);
		}

		// 2. Act 1 & Act 2A (0.0s .. 7.0s): Locked Chained Death Screen + Slow Full-Screen Spider-Web Glass Cracks!
		if (elapsed < shatterMs) {
			double crackP = elapsed <= act1Ms
					? 0.0D
					: (elapsed - act1Ms) / (double) Math.max(1L, shatterMs - act1Ms);
			renderLockedDeathScreenAndGlassCracks(ctx, elapsed, act1Ms, crackP, mouseX, mouseY);
			return;
		}

		// 3. Act 2B & 2C (7.0s .. 17.0s): Full-Screen Glass Shatter + Monochrome Smooth World Rewind!
		renderShatterAndMonochromeRewind(ctx, elapsed, shatterMs, rewindStartMs, act2Ms);
	}

	private void renderLockedDeathScreenAndGlassCracks(DrawContext ctx, long elapsed, long act1Ms, double crackP, int mx, int my) {
		int sw = this.width;
		int sh = this.height;

		// Subtle glass pane tension vibration as cracks approach full-screen break (crackP > 0.55)
		int paneJitterX = 0;
		int paneJitterY = 0;
		if (crackP > 0.50D) {
			double amp = (crackP - 0.50D) * 5.2D;
			paneJitterX = (int) Math.round(Math.sin(elapsed * 0.095D) * amp);
			paneJitterY = (int) Math.round(Math.cos(elapsed * 0.125D) * amp);
		}

		// Light translucent Crimson Death Veil — kept subtle so the third-person body and the
		// scattered 3D inventory stay clearly readable, just like the vanilla death screen.
		int topAlpha = (int) Math.min(148, 60 + elapsed * 0.007D);
		int botAlpha = (int) Math.min(168, 82 + elapsed * 0.009D);
		ctx.fillGradient(0, 0, sw, sh, (topAlpha << 24) | 0x4E0710, (botAlpha << 24) | 0x1E0206);

		// 4-edge realistic double-beat ("lub-dub") crimson pulse + rising peripheral soul embers
		drawPeripheralDeathVignetteAndEmbers(ctx, sw, sh, elapsed, crackP);

		// Subtle frost-glass sheen growing as the screen prepares to crack
		if (crackP > 0.0D) {
			int sheenAlpha = (int) Math.round(32 * crackP);
			ctx.fill(0, 0, sw, sh, (sheenAlpha << 24) | 0xDCE6F7);
		}

		// Scaled 2.0x Authentic Vanilla Death Title
		boolean hc = ReturnByDeathClientController.isWorldHardcore();
		String title = hc ? "Game Over!" : "You Died!";
		ctx.getMatrices().push();
		ctx.getMatrices().translate(paneJitterX, paneJitterY, 0.0F);
		ctx.getMatrices().scale(2.0F, 2.0F, 2.0F);
		int tw = this.textRenderer.getWidth(title);
		ctx.drawTextWithShadow(this.textRenderer, title, (sw / 2 - tw) / 2, 30, 0xFFFFEFEF);
		ctx.getMatrices().pop();

		// Death Cause + Score
		String cause = ReturnByDeathClientController.getDeathMessage();
		int cw = this.textRenderer.getWidth(cause);
		ctx.drawTextWithShadow(this.textRenderer, cause, (sw - cw) / 2 + paneJitterX, 85 + paneJitterY, 0xFFF2D9DE);

		String scoreLine = "Score: \u00A7e" + ReturnByDeathClientController.getScore();
		int scw = this.textRenderer.getWidth(scoreLine);
		ctx.drawTextWithShadow(this.textRenderer, scoreLine, (sw - scw) / 2 + paneJitterX, 100 + paneJitterY, 0xFFEAEAEA);

		// Vanilla-style death buttons (clean, no locks/chains) — chains only appear on the clicked one.
		// Buttons use the stable buttonX()/buttonY() bounds (no pane jitter) so their drawn
		// edges, hover area and click area always line up.
		long sinceRattle = System.currentTimeMillis() - ReturnByDeathClientController.getLockShakeClickMs();
		int rattleX = sinceRattle < 320L ? (int) (Math.sin(sinceRattle * 0.075D) * 3.5D) : 0;

		int bx = buttonX();
		int b1y = buttonY(0);
		int b2y = buttonY(1);

		String firstLabel = hc ? "Spectate World" : "Respawn";
		int lockedIdx = ReturnByDeathClientController.getLockedButtonIndex();
		float lockP = ReturnByDeathClientController.getLockProgress();

		int hovered = -1;
		if (mx >= bx && mx <= bx + BUTTON_W) {
			if (my >= b1y && my <= b1y + BUTTON_H) hovered = 0;
			else if (my >= b2y && my <= b2y + BUTTON_H) hovered = 1;
		}

		int shake0 = (lockedIdx == 0 && lockP > 0.0F) ? rattleX : 0;
		int shake1 = (lockedIdx == 1 && lockP > 0.0F) ? rattleX : 0;

		drawVanillaDeathButton(ctx, bx + shake0, b1y, BUTTON_W, BUTTON_H, firstLabel, hovered == 0, crackP);
		drawVanillaDeathButton(ctx, bx + shake1, b2y, BUTTON_W, BUTTON_H, "Title Screen", hovered == 1, crackP);

		// Chain & padlock binding animation, ONLY on the button that was just clicked.
		// drawBindingChains draws its own dimming overlay + locked frame strictly inside
		// the button bounds, so the effect has a clear edge instead of a floating plate.
		if (lockP > 0.0F && lockedIdx >= 0 && lockedIdx < BUTTON_COUNT) {
			int lx = bx + (lockedIdx == 0 ? shake0 : shake1);
			int ly = lockedIdx == 0 ? b1y : b2y;
			drawBindingChains(ctx, lx, ly, BUTTON_W, BUTTON_H, lockP);
		}

		// Ominous Abyssal Whisper Subtitle (4-stage progression across 0..5.5s Act 1 + 5.5..7.0s fracture)
		String whisper;
		int whisperBaseRgb;
		if (elapsed < 1800L) {
			whisper = "\u2726 FATE REJECTS THIS ENDING... \u2726";
			whisperBaseRgb = 0xFCA5A5;
		} else if (elapsed < 3700L) {
			whisper = "\u26D3 BOUND BY THE COVENANT OF RETURN BY DEATH \u26D3";
			whisperBaseRgb = 0xE9D5FF;
		} else if (elapsed < act1Ms) {
			whisper = "\u2727 UNSEALING TEMPORAL ANCHOR... \u2727";
			whisperBaseRgb = 0xFDE68A;
		} else {
			whisper = "\u2736 SHATTERING THE CAUSALITY VEIL... \u2736";
			whisperBaseRgb = 0xF8FAFC;
		}
		int whisperAlpha = 205 + (int) Math.round(45 * Math.sin(elapsed * 0.006D));
		int ww = this.textRenderer.getWidth(whisper);
		ctx.drawTextWithShadow(this.textRenderer, whisper, (sw - ww) / 2, b2y + 34, (whisperAlpha << 24) | whisperBaseRgb);

		// Act 2A (5.5s .. 7.0s): Slow Full-Screen Spider-Web Glass Fracture!
		if (crackP > 0.001D) {
			drawFullScreenGlassCracks(ctx, (float) crackP);
		}
	}

	/**
	 * Renders a 4-edge double-beat ("lub-dub") crimson vignette + 20 rising peripheral soul embers
	 * during the corpse lying & glass-fracture phases while keeping the center corpse & buttons clear.
	 */
	private void drawPeripheralDeathVignetteAndEmbers(DrawContext ctx, int sw, int sh, long elapsed, double crackP) {
		// Realistic cardiac double-beat synced to ~1040ms period
		double beatPhase = (elapsed % 1040L) / 1040.0D;
		double lub = Math.exp(-Math.pow((beatPhase - 0.12D) / 0.065D, 2.0D));
		double dub = 0.68D * Math.exp(-Math.pow((beatPhase - 0.28D) / 0.055D, 2.0D));
		double heartPulse = Math.min(1.0D, lub + dub);
		int pulseAlpha = (int) Math.round((24 + 54 * heartPulse) * (1.0D - crackP * 0.35D));

		if (pulseAlpha > 2) {
			ctx.fillGradient(0, 0, sw, 48, (pulseAlpha << 24) | 0xDC2626, 0x00DC2626);
			ctx.fillGradient(0, sh - 48, sw, sh, 0x00DC2626, (pulseAlpha << 24) | 0xDC2626);
		}

		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();

		// Horizontal left & right heartbeat vignette strips
		if (pulseAlpha > 2) {
			int sideA = (pulseAlpha * 4) / 5;
			int edgeCol = (sideA << 24) | 0x991B1B;
			int clearCol = 0x00991B1B;
			float sideW = Math.min(68.0F, sw * 0.16F);
			BufferBuilder vQuads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
			// Left strip
			vQuads.vertex(mat, 0.0F, 0.0F, 0.0F).color(edgeCol);
			vQuads.vertex(mat, 0.0F, sh, 0.0F).color(edgeCol);
			vQuads.vertex(mat, sideW, sh, 0.0F).color(clearCol);
			vQuads.vertex(mat, sideW, 0.0F, 0.0F).color(clearCol);
			// Right strip
			vQuads.vertex(mat, sw - sideW, 0.0F, 0.0F).color(clearCol);
			vQuads.vertex(mat, sw - sideW, sh, 0.0F).color(clearCol);
			vQuads.vertex(mat, sw, sh, 0.0F).color(edgeCol);
			vQuads.vertex(mat, sw, 0.0F, 0.0F).color(edgeCol);
			BuiltBuffer vb = vQuads.endNullable();
			if (vb != null) {
				BufferRenderer.drawWithGlobalProgram(vb);
			}
		}

		// 20 peripheral rising abyssal soul embers (outer left & right bands so center stays unobstructed)
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		BufferBuilder embers = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		float tSec = elapsed * 0.001F;
		for (int i = 0; i < 20; i++) {
			boolean rightSide = (i % 2 == 1);
			float seed = (i * 0.6180339F) % 1.0F;
			float bandX = 0.04F + seed * 0.24F;
			float xBase = rightSide ? (1.0F - bandX) * sw : bandX * sw;
			float sway = (float) Math.sin(tSec * (1.3F + (i % 4) * 0.35F) + i * 1.7F) * 9.0F;
			float speed = 0.11F + (i % 5) * 0.028F;
			float yFrac = 1.06F - ((tSec * speed + seed * 1.37F) % 1.12F);
			float px = xBase + sway;
			float py = yFrac * sh;

			float env = (float) Math.sin(Math.max(0.0F, Math.min(1.0F, yFrac)) * Math.PI);
			int alpha = (int) Math.round(135 * env);
			if (alpha <= 6) continue;

			int rgb = crackP > 0.25D
					? ((i % 3 == 0) ? 0xE2E8F0 : 0xC4B5FD)
					: ((i % 3 == 0) ? 0xC084FC : 0xF87171);
			int coreCol = (alpha << 24) | rgb;
			float r = 1.6F + (i % 3) * 0.65F;

			// Crisp diamond mote
			embers.vertex(mat, px, py - r * 1.4F, 0.0F).color(coreCol);
			embers.vertex(mat, px - r, py, 0.0F).color(coreCol);
			embers.vertex(mat, px, py + r * 1.4F, 0.0F).color(coreCol);
			embers.vertex(mat, px + r, py, 0.0F).color(coreCol);
		}
		BuiltBuffer eb = embers.endNullable();
		if (eb != null) {
			BufferRenderer.drawWithGlobalProgram(eb);
		}

		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	/**
	 * Clean vanilla-looking death button ("Respawn" / "Spectate World" / "Title Screen") with a
	 * crimson abyssal tint so it still matches the Void Sovereign death screen.
	 */
	private void drawVanillaDeathButton(DrawContext ctx, int x, int y, int w, int h, String label, boolean hovered, double crackP) {
		int bg = hovered ? 0xE03A1420 : 0xD81A1016;
		ctx.fill(x, y, x + w, y + h, bg);
		int borderCol = crackP > 0.05D ? 0xFFE2E8F0 : (hovered ? 0xFFF87171 : 0xFF7F1D1D);
		ctx.drawBorder(x, y, w, h, borderCol);
		ctx.fill(x + 1, y + 1, x + w - 1, y + 2, hovered ? 0x66FCA5A5 : 0x38FCA5A5);

		int textColor = hovered ? 0xFFFFFFFF : 0xFFE5E7EB;
		int lw = this.textRenderer.getWidth(label);
		ctx.drawTextWithShadow(this.textRenderer, label, x + (w - lw) / 2, y + 6, textColor);
	}

	/**
	 * Dedicated HD RGBA sprite assets for the clicked-button chain-and-padlock seal,
	 * eliminating procedural quad artifacts and keeping every link crisp across all GUI scales.
	 */
	private static final Identifier BUTTON_CHAIN_A_TEX = Identifier.of(HardcoreToolsMod.MOD_ID, "textures/gui/button_chain_a.png");
	private static final Identifier BUTTON_CHAIN_B_TEX = Identifier.of(HardcoreToolsMod.MOD_ID, "textures/gui/button_chain_b.png");
	private static final Identifier BUTTON_PADLOCK_TEX = Identifier.of(HardcoreToolsMod.MOD_ID, "textures/gui/button_padlock.png");
	private static final int CHAIN_TEX_W = 600;
	private static final int CHAIN_TEX_H = 60;
	private static final int PADLOCK_TEX_SIZE = 96;

	/**
	 * Two forged iron chains shooting across the clicked button in an X and clamped by an
	 * ornate Void Sovereign padlock in the middle, rendered from dedicated 3x HD RGBA assets.
	 * lockP goes 1.0 -> 0.0 over 1.4s, so the bindings visibly snap shut and then dissolve.
	 */
	private void drawBindingChains(DrawContext ctx, int x, int y, int w, int h, float lockP) {
		float bind = 1.0F - lockP; // 0.0 = just clicked, 1.0 = fully dissolved
		float alpha = lockP <= 0.28F ? lockP / 0.28F : 1.0F;
		float alphaF = Math.max(0.0F, Math.min(1.0F, alpha));
		int a = (int) Math.round(255 * alphaF);
		if (a <= 6) {
			return;
		}

		// 1. Locked button boundary: subtle dark veil inside the button + crisp steel/crimson frame
		int overlayA = (int) Math.round(125 * Math.min(1.0F, bind * 4.0F) * alphaF);
		if (overlayA > 0) {
			ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, (overlayA << 24) | 0x08090E);
		}
		ctx.drawBorder(x, y, w, h, (a << 24) | 0x94A3B8);
		if (w > 4 && h > 4) {
			ctx.drawBorder(x + 1, y + 1, w - 2, h - 2, ((a * 3 / 5) << 24) | 0xF87171);
		}

		// Flush DrawContext quads before textured sprite draws with custom shader alpha
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alphaF);

		// 2. Chain A shoots left -> right at constant 1:1 link aspect (no horizontal stretching)
		float chainAP = Math.min(1.0F, bind * 5.2F);
		int drawWA = Math.round(w * chainAP);
		int regWA = Math.round(CHAIN_TEX_W * chainAP);
		if (drawWA > 1 && regWA > 1) {
			ctx.drawTexture(BUTTON_CHAIN_A_TEX, x, y, drawWA, h,
					0.0F, 0.0F, regWA, CHAIN_TEX_H, CHAIN_TEX_W, CHAIN_TEX_H);
		}

		// 3. Chain B shoots right -> left right across Chain A (staggered by ~70ms for crisp X-snap)
		float chainBP = Math.max(0.0F, Math.min(1.0F, (bind - 0.05F) * 5.2F));
		int drawWB = Math.round(w * chainBP);
		int regWB = Math.round(CHAIN_TEX_W * chainBP);
		if (drawWB > 1 && regWB > 1) {
			float uStart = (float) (CHAIN_TEX_W - regWB);
			ctx.drawTexture(BUTTON_CHAIN_B_TEX, x + w - drawWB, y, drawWB, h,
					uStart, 0.0F, regWB, CHAIN_TEX_H, CHAIN_TEX_W, CHAIN_TEX_H);
		}

		// 4. Center Void Sovereign Padlock snapping shut with ease-out-back bounce
		float padlockRaw = Math.max(0.0F, Math.min(1.0F, (bind - 0.09F) * 4.8F));
		if (padlockRaw > 0.02F) {
			float lockScale = easeOutBack(padlockRaw);
			float cx = x + w * 0.5F;
			float cy = y + h * 0.5F;
			int lockSize = Math.max(12, h - 2); // 18x18 settled (stays strictly <= 20px button height even on bounce)
			int halfLock = lockSize / 2;

			ctx.getMatrices().push();
			ctx.getMatrices().translate(cx, cy, 0.0F);
			ctx.getMatrices().scale(lockScale, lockScale, 1.0F);
			ctx.drawTexture(BUTTON_PADLOCK_TEX, -halfLock, -halfLock, lockSize, lockSize,
					0.0F, 0.0F, PADLOCK_TEX_SIZE, PADLOCK_TEX_SIZE, PADLOCK_TEX_SIZE, PADLOCK_TEX_SIZE);
			ctx.getMatrices().pop();
		}

		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		// 5. Brief golden-crimson impact spark right as the padlock clamps shut
		float sparkEnv = Math.max(0.0F, 1.0F - Math.abs(bind - 0.16F) * 9.0F) * alphaF;
		if (sparkEnv > 0.04F) {
			int cx = x + w / 2;
			int cy = y + h / 2;
			int sa = (int) Math.round(210 * sparkEnv);
			ctx.fill(cx - 11, cy, cx + 11, cy + 1, (sa << 24) | 0xFEF3C7);
			ctx.fill(cx, cy - 8, cx + 1, cy + 8, (sa << 24) | 0xFCA5A5);
		}
	}

	/** Ease-out-back: fast snap-in with a slight overshoot, then settles at 1.0. */
	private static float easeOutBack(float t) {
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		float u = Math.max(0.0F, Math.min(1.0F, t)) - 1.0F;
		return 1.0F + c3 * u * u * u + c1 * u * u;
	}

	private void drawFullScreenGlassCracks(DrawContext ctx, float crackP) {
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
		BufferBuilder quads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		boolean hasAny = false;

		for (CrackSegment seg : crackSegments) {
			if (crackP <= seg.startT) continue;
			float segP = Math.min(1.0F, (crackP - seg.startT) / Math.max(0.01F, seg.endT - seg.startT));
			float ex = seg.x1 + (seg.x2 - seg.x1) * segP;
			float ey = seg.y1 + (seg.y2 - seg.y1) * segP;

			float dx = ex - seg.x1;
			float dy = ey - seg.y1;
			float len = (float) Math.hypot(dx, dy);
			if (len < 0.5F) continue;
			hasAny = true;

			float nx = -dy / len;
			float ny = dx / len;

			// Dark tempered-glass depth shadow offset by (+1.0, +1.2)
			float hwShadow = seg.widthPx * 0.96F;
			emitQuadLine(quads, mat, seg.x1 + 1.0F, seg.y1 + 1.2F, ex + 1.0F, ey + 1.2F, nx * hwShadow, ny * hwShadow, 0xBC090B10);

			// Subtle prismatic violet-cyan refraction fringe on primary veins
			if (seg.primary) {
				float hwFringe = seg.widthPx * 0.72F;
				emitQuadLine(quads, mat, seg.x1 - 0.8F, seg.y1 - 0.8F, ex - 0.8F, ey - 0.8F, nx * hwFringe, ny * hwFringe, 0x74A5B4FC);
			}

			// Crisp silver-ice / pale-amethyst outer refraction bevel
			float hwOuter = seg.widthPx * 0.86F;
			int outerColor = seg.primary ? 0xE2E2E8F0 : 0xB8CBD5E1;
			emitQuadLine(quads, mat, seg.x1, seg.y1, ex, ey, nx * hwOuter, ny * hwOuter, outerColor);

			// Bright white-crystalline core + travelling fracture-tip glint along primary veins
			if (seg.primary) {
				float hwCore = Math.max(0.55F, seg.widthPx * 0.40F);
				emitQuadLine(quads, mat, seg.x1, seg.y1, ex, ey, nx * hwCore, ny * hwCore, 0xFFFFFFFF);

				if (segP > 0.04F && segP < 0.99F) {
					float sr = 3.2F;
					quads.vertex(mat, ex, ey - sr, 0.0F).color(0xFFFFFFFF);
					quads.vertex(mat, ex - sr, ey, 0.0F).color(0xFFE0E7FF);
					quads.vertex(mat, ex, ey + sr, 0.0F).color(0xFFFFFFFF);
					quads.vertex(mat, ex + sr, ey, 0.0F).color(0xFFE0E7FF);
				}
			}
		}

		if (hasAny) {
			BuiltBuffer built = quads.endNullable();
			if (built != null) {
				BufferRenderer.drawWithGlobalProgram(built);
			}
		}

		// Central impact star glow + 6-ray crystalline flare spikes intensifying right before 7.0s shatter!
		if (crackP > 0.12F) {
			RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
			float cx = this.width * 0.5F;
			float cy = this.height * 0.5F;
			float glowR = 28.0F + crackP * 84.0F;
			int centerAlpha = (int) Math.min(220, Math.round(crackP * crackP * 215.0F));
			BufferBuilder fan = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
			fan.vertex(mat, cx, cy, 0.0F).color((centerAlpha << 24) | 0xF8FAFC);
			for (int i = 0; i <= 36; i++) {
				double a = (i * Math.PI * 2.0D) / 36.0D;
				fan.vertex(mat, cx + (float) Math.cos(a) * glowR, cy + (float) Math.sin(a) * glowR, 0.0F).color(0x00C4B5FD);
			}
			BuiltBuffer builtGlow = fan.endNullable();
			if (builtGlow != null) {
				BufferRenderer.drawWithGlobalProgram(builtGlow);
			}

			int spikeAlpha = (int) Math.min(230, Math.round(crackP * 210.0F));
			if (spikeAlpha > 8) {
				float spikeLen = 18.0F + crackP * 88.0F;
				float spikeW = 1.6F + crackP * 1.4F;
				BufferBuilder spikes = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
				for (int k = 0; k < 6; k++) {
					double a = k * (Math.PI / 3.0D) + 0.24D;
					float cos = (float) Math.cos(a);
					float sin = (float) Math.sin(a);
					float px = -sin * spikeW;
					float py = cos * spikeW;
					int coreC = (spikeAlpha << 24) | 0xFFFFFF;
					int tipC = 0x00E0E7FF;
					spikes.vertex(mat, cx - cos * spikeLen, cy - sin * spikeLen, 0.0F).color(tipC);
					spikes.vertex(mat, cx + px, cy + py, 0.0F).color(coreC);
					spikes.vertex(mat, cx + cos * spikeLen, cy + sin * spikeLen, 0.0F).color(tipC);
					spikes.vertex(mat, cx - px, cy - py, 0.0F).color(coreC);
				}
				BuiltBuffer builtSpikes = spikes.endNullable();
				if (builtSpikes != null) {
					BufferRenderer.drawWithGlobalProgram(builtSpikes);
				}
			}
		}

		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private static void emitQuadLine(
			BufferBuilder buf, Matrix4f mat,
			float x1, float y1, float x2, float y2,
			float ox, float oy, int argb
	) {
		buf.vertex(mat, x1 - ox, y1 - oy, 0.0F).color(argb);
		buf.vertex(mat, x1 + ox, y1 + oy, 0.0F).color(argb);
		buf.vertex(mat, x2 + ox, y2 + oy, 0.0F).color(argb);
		buf.vertex(mat, x2 - ox, y2 - oy, 0.0F).color(argb);
	}

	private void renderShatterAndMonochromeRewind(DrawContext ctx, long elapsed, long shatterMs, long rewindStartMs, long act2Ms) {
		int sw = this.width;
		int sh = this.height;
		int cx = sw / 2;
		int cy = sh / 2;

		double sinceShatterSec = (elapsed - shatterMs) / 1000.0D;
		double rewindRawP = elapsed < rewindStartMs
				? 0.0D
				: Math.min(1.0D, (elapsed - rewindStartMs) / (double) Math.max(1L, act2Ms - rewindStartMs));
		double rewindEasedP = ReturnByDeathClientController.easeInOutSine(rewindRawP);

		// 1. Crisp Shatter Flash + Dual Shockwave Distortion Ring at t = 7.00s .. 7.68s
		if (sinceShatterSec < 0.34D) {
			int flashAlpha = (int) Math.round(192 * Math.pow(1.0D - sinceShatterSec / 0.34D, 1.35D));
			ctx.fill(0, 0, sw, sh, (flashAlpha << 24) | 0xF8FAFC);
		}
		if (sinceShatterSec >= 0.0D && sinceShatterSec < 0.68D) {
			drawShatterShockwave(ctx, sw, sh, (float) (sinceShatterSec / 0.68D));
		}

		// 2. Peripheral Reverse-Time Warp Streaks pulling inward during fast-reverse (keeps center 100% clear!)
		if (rewindRawP > 0.01D && rewindRawP < 0.99D) {
			drawReverseTimeStreaks(ctx, sw, sh, elapsed, rewindRawP);
		}

		// 3. Cinema Widescreen Letterbox Framing (keeps center 3D monochrome world 100% crisp & clear!)
		ctx.fillGradient(0, 0, sw, 46, 0xD407090E, 0x0007090E);
		ctx.fillGradient(0, sh - 52, sw, sh, 0x0007090E, 0xDF07090E);

		// 4. Dual-Ring Astral Pocket-Watch Chronometer spinning backward during rewind
		drawMonochromeChronometer(ctx, cx, cy, Math.min(sw, sh) * 0.36F, elapsed, rewindEasedP);

		// 5. Full-Screen 64-Shard Tempered Glass Explosion tumbling & falling away (7.00s .. 8.95s)
		if (sinceShatterSec < 1.95D) {
			drawTumblingGlassShards(ctx, sw, sh, (float) sinceShatterSec);
		}

		// 6. Sleek Top & Bottom Rewind HUD Readout + Glowing Playhead Progress Bar
		double shownRewound = ReturnByDeathClientController.getRewoundSeconds() * rewindEasedP;
		String topTag = elapsed < rewindStartMs
				? "\u2726 SHATTERING FATE  \u2022  RECLAIMING SCATTERED SOUL \u2726"
				: "\u27F2 TEMPORAL REWIND IN PROGRESS";
		int topW = this.textRenderer.getWidth(topTag);
		int topX = (sw - topW) / 2;
		ctx.drawTextWithShadow(this.textRenderer, topTag, topX, 14, 0xFFE2E8F0);
		ctx.fill(topX - 22, 18, topX - 6, 19, 0x66C4B5FD);
		ctx.fill(topX + topW + 6, 18, topX + topW + 22, 19, 0x66C4B5FD);

		String timerText = String.format(Locale.ROOT, "-%.1fs  /  -%.0fs", shownRewound, ReturnByDeathClientController.getRewoundSeconds());
		int timeW = this.textRenderer.getWidth(timerText);
		ctx.drawTextWithShadow(this.textRenderer, timerText, (sw - timeW) / 2, sh - 36, CrimsonTheme.GOLD);

		// Dual-tone silver-gold progress bar with glowing diamond playhead gliding backward (right -> left)
		int barW = Math.min(260, sw - 80);
		int barX = (sw - barW) / 2;
		int barY = sh - 19;
		ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + 4, 0x550F172A);
		ctx.fill(barX, barY, barX + barW, barY + 3, 0x771E293B);
		int fillW = (int) Math.round(barW * (1.0D - rewindEasedP));
		if (fillW > 0) {
			ctx.fill(barX, barY, barX + fillW, barY + 2, 0xFFE2E8F0);
			ctx.fill(barX, barY + 2, barX + fillW, barY + 3, 0xCCFDE68A);
		}
		int headX = barX + fillW;
		ctx.fill(headX - 2, barY - 2, headX + 2, barY + 5, 0xCCFDE68A);
		ctx.fill(headX - 1, barY - 1, headX + 1, barY + 4, 0xFFFFFFFF);
	}

	private void drawShatterShockwave(DrawContext ctx, int sw, int sh, float tNorm) {
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
		float cx = sw * 0.5F;
		float cy = sh * 0.5F;
		float maxR = (float) Math.hypot(cx, cy) * 1.05F;
		float eased = 1.0F - (float) Math.pow(1.0D - tNorm, 2.6D);
		float r1 = eased * maxR;
		float r2 = Math.max(0.0F, eased - 0.14F) * maxR;
		int a1 = (int) Math.round(175 * (1.0F - tNorm));
		int a2 = (int) Math.round(115 * (1.0F - tNorm));

		int segs = 64;
		if (a1 > 4 && r1 > 4.0F) {
			BufferBuilder s1 = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
			for (int i = 0; i <= segs; i++) {
				double a = (i * Math.PI * 2.0D) / segs;
				float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
				s1.vertex(mat, cx + cos * Math.max(0.0F, r1 - 9.0F), cy + sin * Math.max(0.0F, r1 - 9.0F), 0.0F).color(0x00E0E7FF);
				s1.vertex(mat, cx + cos * r1, cy + sin * r1, 0.0F).color((a1 << 24) | 0xF8FAFC);
			}
			BuiltBuffer b1 = s1.endNullable();
			if (b1 != null) BufferRenderer.drawWithGlobalProgram(b1);
		}
		if (a2 > 4 && r2 > 4.0F) {
			BufferBuilder s2 = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
			for (int i = 0; i <= segs; i++) {
				double a = (i * Math.PI * 2.0D) / segs;
				float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
				s2.vertex(mat, cx + cos * Math.max(0.0F, r2 - 5.5F), cy + sin * Math.max(0.0F, r2 - 5.5F), 0.0F).color(0x00C4B5FD);
				s2.vertex(mat, cx + cos * r2, cy + sin * r2, 0.0F).color((a2 << 24) | 0xC4B5FD);
			}
			BuiltBuffer b2 = s2.endNullable();
			if (b2 != null) BufferRenderer.drawWithGlobalProgram(b2);
		}

		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private void drawReverseTimeStreaks(DrawContext ctx, int sw, int sh, long elapsed, double rewindRawP) {
		double speedEnv = Math.sin(rewindRawP * Math.PI);
		if (speedEnv <= 0.04D) {
			return;
		}
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
		float cx = sw * 0.5F;
		float cy = sh * 0.5F;
		float maxDiag = (float) Math.hypot(cx, cy);
		float tSec = elapsed * 0.001F;

		BufferBuilder streaks = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		for (int i = 0; i < 28; i++) {
			double angle = (i * Math.PI * 2.0D / 28.0D) + Math.sin(i * 2.3D) * 0.09D;
			float cos = (float) Math.cos(angle);
			float sin = (float) Math.sin(angle);
			float cycle = (tSec * (1.15F + (i % 4) * 0.24F) + i * 0.37F) % 1.0F;
			// Pull radially INWARD from outer corner zone (1.02 -> 0.44 of half-diagonal) so center stays clear!
			float headFrac = 1.02F - cycle * 0.58F;
			float lenFrac = (0.06F + (i % 3) * 0.025F) * (float) speedEnv;
			float rInner = headFrac * maxDiag;
			float rOuter = (headFrac + lenFrac) * maxDiag;

			int alpha = (int) Math.round(92 * speedEnv * Math.sin(cycle * Math.PI));
			if (alpha <= 5) continue;

			int rgb = (i % 4 == 0) ? 0xFDE68A : ((i % 3 == 0) ? 0xC4B5FD : 0xE2E8F0);
			float ox = -sin * 0.75F;
			float oy = cos * 0.75F;
			float x0 = cx + cos * rInner, y0 = cy + sin * rInner;
			float x1 = cx + cos * rOuter, y1 = cy + sin * rOuter;
			int headCol = (alpha << 24) | rgb;
			int tailCol = 0x00E2E8F0;
			streaks.vertex(mat, x0 - ox, y0 - oy, 0.0F).color(headCol);
			streaks.vertex(mat, x0 + ox, y0 + oy, 0.0F).color(headCol);
			streaks.vertex(mat, x1 + ox, y1 + oy, 0.0F).color(tailCol);
			streaks.vertex(mat, x1 - ox, y1 - oy, 0.0F).color(tailCol);
		}
		BuiltBuffer bs = streaks.endNullable();
		if (bs != null) {
			BufferRenderer.drawWithGlobalProgram(bs);
		}

		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private void drawMonochromeChronometer(DrawContext ctx, int cx, int cy, float radius, long elapsed, double easedP) {
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
		int segs = 80;

		// 1. Outer silver hairline dial
		BufferBuilder ring = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i <= segs; i++) {
			double a = (i * Math.PI * 2.0D) / segs;
			float cos = (float) Math.cos(a);
			float sin = (float) Math.sin(a);
			ring.vertex(mat, cx + cos * (radius - 1.5F), cy + sin * (radius - 1.5F), 0.0F).color(0x54E2E8F0);
			ring.vertex(mat, cx + cos * (radius + 1.5F), cy + sin * (radius + 1.5F), 0.0F).color(0x00E2E8F0);
		}
		BuiltBuffer rb = ring.endNullable();
		if (rb != null) {
			BufferRenderer.drawWithGlobalProgram(rb);
		}

		// 2. Inner counter-rotating arcane escapement ring (keeps inner 78% completely clear!)
		float innerRingR = radius * 0.80F;
		BufferBuilder innerRing = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i <= segs; i++) {
			double a = (i * Math.PI * 2.0D) / segs;
			float cos = (float) Math.cos(a);
			float sin = (float) Math.sin(a);
			innerRing.vertex(mat, cx + cos * (innerRingR - 0.9F), cy + sin * (innerRingR - 0.9F), 0.0F).color(0x2EC4B5FD);
			innerRing.vertex(mat, cx + cos * (innerRingR + 0.9F), cy + sin * (innerRingR + 0.9F), 0.0F).color(0x00C4B5FD);
		}
		BuiltBuffer irb = innerRing.endNullable();
		if (irb != null) {
			BufferRenderer.drawWithGlobalProgram(irb);
		}

		// 3. Center hollow escapement hub ring around crosshair
		float hubR = 15.5F;
		BufferBuilder hub = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i <= 40; i++) {
			double a = (i * Math.PI * 2.0D) / 40.0D;
			float cos = (float) Math.cos(a);
			float sin = (float) Math.sin(a);
			hub.vertex(mat, cx + cos * (hubR - 1.0F), cy + sin * (hubR - 1.0F), 0.0F).color(0x44FDE68A);
			hub.vertex(mat, cx + cos * (hubR + 1.0F), cy + sin * (hubR + 1.0F), 0.0F).color(0x00FDE68A);
		}
		BuiltBuffer hb = hub.endNullable();
		if (hb != null) {
			BufferRenderer.drawWithGlobalProgram(hb);
		}

		// 4. 60 Fine Second Sub-Ticks + 12 Hour Ticks + Counter-Rotating Inner Gear Notches + 3 Reverse Hands
		BufferBuilder quads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		double dialSpin = -easedP * Math.PI * 2.0D;
		for (int i = 0; i < 60; i++) {
			double a = dialSpin + (i * Math.PI * 2.0D / 60.0D);
			float cos = (float) Math.cos(a);
			float sin = (float) Math.sin(a);
			boolean cardinal = (i % 15 == 0);
			boolean hourMark = (i % 5 == 0);
			float inner = cardinal ? radius - 13.0F : (hourMark ? radius - 7.5F : radius - 3.6F);
			float hw = cardinal ? 1.4F : (hourMark ? 1.0F : 0.55F);
			int col = cardinal ? 0x88FDE68A : (hourMark ? 0x6CF8FAFC : 0x34CBD5E1);
			emitQuadLine(quads, mat, cx + cos * inner, cy + sin * inner, cx + cos * radius, cy + sin * radius, -sin * hw, cos * hw, col);
		}

		double innerSpin = easedP * Math.PI * 3.0D;
		for (int i = 0; i < 24; i++) {
			double a = innerSpin + (i * Math.PI * 2.0D / 24.0D);
			float cos = (float) Math.cos(a);
			float sin = (float) Math.sin(a);
			emitQuadLine(quads, mat,
					cx + cos * (innerRingR - 3.2F), cy + sin * (innerRingR - 3.2F),
					cx + cos * (innerRingR + 2.2F), cy + sin * (innerRingR + 2.2F),
					-sin * 0.7F, cos * 0.7F, 0x36C4B5FD);
		}

		// Three counter-clockwise hands (centisecond sweep, silver minute hand, gold hour hand)
		double secAngle = -Math.PI * 0.5D - easedP * Math.PI * 38.0D;
		double minAngle = -Math.PI * 0.5D - easedP * Math.PI * 14.0D;
		double hrAngle = -Math.PI * 0.5D - easedP * Math.PI * 2.5D;
		float sCos = (float) Math.cos(secAngle), sSin = (float) Math.sin(secAngle);
		float mCos = (float) Math.cos(minAngle), mSin = (float) Math.sin(minAngle);
		float hCos = (float) Math.cos(hrAngle), hSin = (float) Math.sin(hrAngle);
		emitQuadLine(quads, mat, cx + sCos * hubR, cy + sSin * hubR, cx + sCos * (radius - 6.0F), cy + sSin * (radius - 6.0F), -sSin * 0.65F, sCos * 0.65F, 0x3CC4B5FD);
		emitQuadLine(quads, mat, cx + mCos * hubR, cy + mSin * hubR, cx + mCos * (innerRingR - 6.0F), cy + mSin * (innerRingR - 6.0F), -mSin * 1.15F, mCos * 1.15F, 0x58F8FAFC);
		emitQuadLine(quads, mat, cx + hCos * hubR, cy + hSin * hubR, cx + hCos * (radius * 0.54F), cy + hSin * (radius * 0.54F), -hSin * 1.6F, hCos * 1.6F, 0x64FDE68A);

		BuiltBuffer qb = quads.endNullable();
		if (qb != null) {
			BufferRenderer.drawWithGlobalProgram(qb);
		}

		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private void drawTumblingGlassShards(DrawContext ctx, int sw, int sh, float tSec) {
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
		float fade = Math.max(0.0F, 1.0F - (tSec / 1.95F));
		int bodyAlpha = (int) Math.round(200 * fade);
		int glintAlpha = (int) Math.round(248 * fade);
		if (bodyAlpha <= 3) {
			RenderSystem.enableDepthTest();
			RenderSystem.enableCull();
			return;
		}

		// Subtle horizontal drag so shards burst fast on impact and then float/tumble naturally under gravity
		float dragT = tSec * (1.0F - 0.16F * tSec);

		for (GlassShard s : glassShards) {
			float px = s.cxPct * sw + s.vx * dragT;
			float py = s.cyPct * sh + s.vy * dragT + 255.0F * tSec * tSec;
			float rot = s.rotSpeed * tSec;
			// 3D tumbling perspective scale across one axis + bright specular flash when catching the light!
			float flipCos = (float) Math.cos(s.flipSpeed * tSec);
			float squeeze = Math.max(0.18F, Math.abs(flipCos));
			boolean specularFlash = Math.abs(flipCos) > 0.88F;

			int baseRgb;
			if (specularFlash) {
				baseRgb = 0xFFFFFF;
			} else if (s.tintKind == 1) {
				baseRgb = 0xB91C1C;
			} else if (s.tintKind == 2) {
				baseRgb = 0xC4B5FD;
			} else {
				baseRgb = 0xCBD5E1;
			}
			int edgeRgb = (s.tintKind == 2) ? 0xEDE9FE : 0xF8FAFC;

			int verts = s.localAngles.length;
			float[] vxArr = new float[verts];
			float[] vyArr = new float[verts];
			for (int k = 0; k < verts; k++) {
				double a = s.localAngles[k] + rot;
				float lx = (float) Math.cos(a) * s.localRadii[k] * squeeze;
				float ly = (float) Math.sin(a) * s.localRadii[k];
				vxArr[k] = px + lx;
				vyArr[k] = py + ly;
			}

			// Glass shard polygon body
			BufferBuilder fan = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
			fan.vertex(mat, px, py, 0.0F).color((bodyAlpha << 24) | baseRgb);
			for (int k = 0; k <= verts; k++) {
				int idx = k % verts;
				fan.vertex(mat, vxArr[idx], vyArr[idx], 0.0F).color(((bodyAlpha * 3 / 4) << 24) | edgeRgb);
			}
			BuiltBuffer bf = fan.endNullable();
			if (bf != null) {
				BufferRenderer.drawWithGlobalProgram(bf);
			}

			// Crisp crystalline bevel edge around the shard
			BufferBuilder bevel = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
			for (int k = 0; k < verts; k++) {
				int nxt = (k + 1) % verts;
				float dx = vxArr[nxt] - vxArr[k];
				float dy = vyArr[nxt] - vyArr[k];
				float len = (float) Math.hypot(dx, dy);
				if (len < 0.4F) continue;
				emitQuadLine(bevel, mat, vxArr[k], vyArr[k], vxArr[nxt], vyArr[nxt], (-dy / len) * 0.72F, (dx / len) * 0.72F, (glintAlpha << 24) | 0xFFFFFF);
			}
			BuiltBuffer bb = bevel.endNullable();
			if (bb != null) {
				BufferRenderer.drawWithGlobalProgram(bb);
			}
		}

		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}
}
