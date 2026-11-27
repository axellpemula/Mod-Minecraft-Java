package com.axell.hardcoretools.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.joml.Matrix4f;

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

/**
 * 17.5s Return by Death Cinema Screen (Acts 1 & 2: 0.0s - 15.0s):
 * - Act 1  (0.0s - 4.5s):  Locked Chained Death Screen over collapsed camera & real scattered 3D inventory
 * - Act 2A (4.5s - 6.0s):  Slow multi-branching spider-web glass cracks creeping across the ENTIRE screen
 * - Act 2B (6.0s - 7.4s):  Full-screen 44-shard tempered glass explosion + expanding monochrome grayscale dome
 * - Act 2C (6.6s - 15.0s): 8.4s ultra-smooth monochrome world & camera fast-reverse + silver astral chronometer
 * (Closes automatically at 15.0s so Act 3 shrinks the grayscale dome smoothly over 15.0s - 17.5s!)
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
			boolean crimsonTinted
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

		// 1. Build 14 full-screen multi-step radial fracture veins + side forks + spider-web cross arcs
		int veinCount = 14;
		int stepsPerVein = 7;
		float[][] veinNodesX = new float[veinCount][stepsPerVein + 1];
		float[][] veinNodesY = new float[veinCount][stepsPerVein + 1];

		for (int v = 0; v < veinCount; v++) {
			double baseAngle = (v * Math.PI * 2.0D / veinCount) + (rng.nextDouble() - 0.5D) * 0.22D;
			float curX = cx;
			float curY = cy;
			veinNodesX[v][0] = curX;
			veinNodesY[v][0] = curY;
			double curAngle = baseAngle;

			for (int s = 1; s <= stepsPerVein; s++) {
				float stepFrac0 = (s - 1) / (float) stepsPerVein;
				float stepFrac1 = s / (float) stepsPerVein;
				curAngle += (rng.nextDouble() - 0.5D) * 0.34D;
				float dist = maxReach * stepFrac1 * (0.90F + rng.nextFloat() * 0.18F);
				float nx = cx + (float) Math.cos(curAngle) * dist;
				float ny = cy + (float) Math.sin(curAngle) * dist;

				float startT = stepFrac0 * 0.78F + rng.nextFloat() * 0.05F;
				float endT = Math.min(0.98F, startT + 0.16F);
				float thickness = Math.max(1.1F, 2.5F - stepFrac0 * 1.2F);
				crackSegments.add(new CrackSegment(curX, curY, nx, ny, startT, endT, thickness, true));

				// Secondary diagonal glass fork
				if (s >= 2 && s <= 6 && rng.nextFloat() < 0.72F) {
					double forkAng = curAngle + (rng.nextBoolean() ? 1.0D : -1.0D) * (0.42D + rng.nextDouble() * 0.38D);
					float forkLen = maxReach * (0.11F + rng.nextFloat() * 0.10F);
					float fx = nx + (float) Math.cos(forkAng) * forkLen;
					float fy = ny + (float) Math.sin(forkAng) * forkLen;
					float fStart = Math.min(0.92F, endT - 0.03F);
					crackSegments.add(new CrackSegment(nx, ny, fx, fy, fStart, Math.min(1.0F, fStart + 0.14F), 1.15F, false));
				}

				veinNodesX[v][s] = nx;
				veinNodesY[v][s] = ny;
				curX = nx;
				curY = ny;
			}
		}

		// Concentric spider-web stress rings connecting adjacent veins
		int[] webRings = {1, 2, 3, 5};
		for (int ringStep : webRings) {
			float ringBaseT = 0.20F + (ringStep / (float) stepsPerVein) * 0.68F;
			for (int v = 0; v < veinCount; v++) {
				if (rng.nextFloat() > 0.82F) continue;
				int nextV = (v + 1) % veinCount;
				float x1 = veinNodesX[v][ringStep];
				float y1 = veinNodesY[v][ringStep];
				float x2 = veinNodesX[nextV][ringStep];
				float y2 = veinNodesY[nextV][ringStep];
				float st = Math.min(0.93F, ringBaseT + rng.nextFloat() * 0.06F);
				crackSegments.add(new CrackSegment(x1, y1, x2, y2, st, Math.min(1.0F, st + 0.10F), 1.05F, false));
			}
		}

		// 2. Build 44 Full-Screen Tempered Glass Shards distributed across concentric rings to the corners!
		int[] ringCounts = {8, 14, 22};
		float[] ringRadiusPct = {0.14F, 0.33F, 0.56F};
		for (int rIdx = 0; rIdx < ringCounts.length; rIdx++) {
			int count = ringCounts[rIdx];
			float rPct = ringRadiusPct[rIdx];
			for (int i = 0; i < count; i++) {
				double ang = (i * Math.PI * 2.0D / count) + (rng.nextDouble() - 0.5D) * (0.45D / (rIdx + 1));
				float distX = (float) Math.cos(ang) * rPct * (0.88F + rng.nextFloat() * 0.24F);
				float distY = (float) Math.sin(ang) * rPct * (0.88F + rng.nextFloat() * 0.24F);
				float cxPct = 0.5F + distX;
				float cyPct = 0.5F + distY;

				float speed = (155.0F + rIdx * 95.0F) + rng.nextFloat() * 145.0F;
				float vx = (float) Math.cos(ang) * speed + (rng.nextFloat() - 0.5F) * 45.0F;
				float vy = (float) Math.sin(ang) * speed - (35.0F + rng.nextFloat() * 65.0F);
				float rotSpd = (rng.nextFloat() - 0.5F) * 8.5F;
				float flipSpd = (2.5F + rng.nextFloat() * 5.5F) * (rng.nextBoolean() ? 1.0F : -1.0F);
				float baseRad = (16.0F + rIdx * 10.0F) + rng.nextFloat() * 16.0F;

				int verts = rng.nextBoolean() ? 4 : 3;
				float[] lAng = new float[verts];
				float[] lRad = new float[verts];
				for (int k = 0; k < verts; k++) {
					lAng[k] = (float) ((k * Math.PI * 2.0D / verts) + (rng.nextDouble() - 0.5D) * 0.55D);
					lRad[k] = baseRad * (0.68F + rng.nextFloat() * 0.50F);
				}

				glassShards.add(new GlassShard(
						cxPct, cyPct, vx, vy, rotSpd, flipSpd, baseRad,
						lAng, lRad, (i + rIdx) % 3 == 0
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
			int bw = 200;
			int bh = 20;
			int bx = (this.width - bw) / 2;
			for (int i = 0; i < BUTTON_COUNT; i++) {
				int by = this.height / 4 + 72 + i * 24;
				if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
					// Only the clicked button gets bound by chains — vanilla-clean otherwise.
					ReturnByDeathClientController.triggerLockedButton(i);
					return true;
				}
			}
		}
		return true;
	}

	private static final int BUTTON_COUNT = 2;

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
		long act1Ms = ReturnByDeathClientController.getAct1Ms();               // 4500ms
		long shatterMs = ReturnByDeathClientController.getShatterMs();         // 6000ms
		long rewindStartMs = ReturnByDeathClientController.getRewindStartMs(); // 6600ms
		long act2Ms = ReturnByDeathClientController.getAct2Ms();               // 15000ms

		// 1. Expanding Monochrome Grayscale World Dome (begins at 5.85s and covers 100% of screen by 7.15s!)
		float grayFrac = ReturnByDeathClientController.getGrayscaleRadiusFraction(elapsed);
		if (grayFrac > 0.002F) {
			ReturnByDeathClientController.renderGrayscaleWorldCircle(ctx, this.width, this.height, grayFrac);
		}

		// 2. Act 1 & Act 2A (0.0s .. 6.0s): Locked Chained Death Screen + Slow Full-Screen Spider-Web Glass Cracks!
		if (elapsed < shatterMs) {
			double crackP = elapsed <= act1Ms
					? 0.0D
					: (elapsed - act1Ms) / (double) Math.max(1L, shatterMs - act1Ms);
			renderLockedDeathScreenAndGlassCracks(ctx, elapsed, act1Ms, crackP, mouseX, mouseY);
			return;
		}

		// 3. Act 2B & 2C (6.0s .. 15.0s): Full-Screen Glass Shatter + Monochrome Smooth World Rewind!
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
		int topAlpha = (int) Math.min(150, 64 + elapsed * 0.008D);
		int botAlpha = (int) Math.min(170, 86 + elapsed * 0.010D);
		ctx.fillGradient(0, 0, sw, sh, (topAlpha << 24) | 0x4E0710, (botAlpha << 24) | 0x1E0206);

		// Heartbeat pulse vignette around screen edges
		double heartPulse = Math.pow(Math.max(0.0D, Math.sin(elapsed * 0.0056D)), 4.0D);
		int pulseAlpha = (int) Math.round(58 * heartPulse);
		if (pulseAlpha > 2) {
			ctx.fillGradient(0, 0, sw, 46, (pulseAlpha << 24) | 0xDC2626, 0x00DC2626);
			ctx.fillGradient(0, sh - 46, sw, sh, 0x00DC2626, (pulseAlpha << 24) | 0xDC2626);
		}

		// Subtle frost-glass sheen growing as the screen prepares to crack
		if (crackP > 0.0D) {
			int sheenAlpha = (int) Math.round(28 * crackP);
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

		// Vanilla-style death buttons (clean, no locks/chains) — chains only appear on the clicked one
		long sinceRattle = System.currentTimeMillis() - ReturnByDeathClientController.getLockShakeClickMs();
		int rattleX = sinceRattle < 320L ? (int) (Math.sin(sinceRattle * 0.075D) * 3.5D) : 0;

		int bw = 200;
		int bh = 20;
		int bx = (sw - bw) / 2 + paneJitterX;
		int b1y = sh / 4 + 72 + paneJitterY;
		int b2y = sh / 4 + 96 + paneJitterY;

		String firstLabel = hc ? "Spectate World" : "Respawn";
		int lockedIdx = ReturnByDeathClientController.getLockedButtonIndex();
		float lockP = ReturnByDeathClientController.getLockProgress();

		int hovered = -1;
		if (mx >= bx && mx <= bx + bw) {
			if (my >= b1y && my <= b1y + bh) hovered = 0;
			else if (my >= b2y && my <= b2y + bh) hovered = 1;
		}

		int shake0 = (lockedIdx == 0 && lockP > 0.0F) ? rattleX : 0;
		int shake1 = (lockedIdx == 1 && lockP > 0.0F) ? rattleX : 0;

		drawVanillaDeathButton(ctx, bx + shake0, b1y, bw, bh, firstLabel, hovered == 0, crackP);
		drawVanillaDeathButton(ctx, bx + shake1, b2y, bw, bh, "Title Screen", hovered == 1, crackP);

		// Chain & padlock binding animation, ONLY on the button that was just clicked
		if (lockP > 0.0F && lockedIdx >= 0 && lockedIdx < BUTTON_COUNT) {
			int ly = lockedIdx == 0 ? b1y : b2y;
			int lx = bx + (lockedIdx == 0 ? shake0 : shake1);
			drawBindingChains(ctx, lx, ly, bw, bh, lockP);
		}

		// Ominous Abyssal Whisper Subtitle
		String whisper;
		int whisperColor;
		if (elapsed < 1600L) {
			whisper = "\u2726 FATE REJECTS THIS ENDING... \u2726";
			whisperColor = 0xCCFCA5A5;
		} else if (elapsed < act1Ms) {
			whisper = "\u26D3 BOUND BY THE COVENANT OF RETURN BY DEATH \u26D3";
			whisperColor = 0xFFE9D5FF;
		} else {
			whisper = "\u2736 SHATTERING THE CAUSALITY VEIL... \u2736";
			whisperColor = 0xFFF8FAFC;
		}
		int ww = this.textRenderer.getWidth(whisper);
		ctx.drawTextWithShadow(this.textRenderer, whisper, (sw - ww) / 2, b2y + 34, whisperColor);

		// Act 2A (4.5s .. 6.0s): Slow Full-Screen Spider-Web Glass Fracture!
		if (crackP > 0.001D) {
			drawFullScreenGlassCracks(ctx, (float) crackP);
		}
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
	 * Chains + padlock snapping around a button after it is clicked.
	 * lockP fades 1.0 -> 0.0 over 1.4s, so the bindings uncoil & dissolve on their own.
	 */
	private void drawBindingChains(DrawContext ctx, int x, int y, int w, int h, float lockP) {
		float snap = Math.min(1.0F, (1.0F - lockP) * 3.2F);   // 0.0 = just snapped on
		float alpha = Math.min(1.0F, lockP * 1.6F);           // fade out near the end
		int a = (int) Math.round(235 * alpha);
		if (a <= 4) {
			return;
		}

		int chainDark = (a << 24) | 0x3F2A2F;
		int chainHi = (a << 24) | 0xF87171;
		int chainSteel = (a << 24) | 0xCBD5E1;

		// 1. Two iron bands wrapping around the button (top & bottom), anchored beyond the edges
		int bandInset = (int) Math.round((1.0F - snap) * (w * 0.45F));
		for (int band = 0; band < 2; band++) {
			int by = band == 0 ? y - 2 : y + h;
			int startX = x + bandInset;
			int endX = x + w - bandInset;
			if (endX - startX < 10) {
				continue;
			}
			for (int i = startX; i < endX - 6; i += 12) {
				int segEnd = Math.min(endX, i + 9);
				ctx.fill(i, by, segEnd, by + 4, chainDark);
				ctx.fill(i + 1, by + 1, segEnd - 1, by + 2, chainHi);
			}
		}

		// 2. Diagonal chain crossing the face of the button, drawn link by link
		int links = 10;
		int visible = Math.max(0, (int) Math.round(links * snap * 3.0F));
		for (int i = 0; i < Math.min(links, visible); i++) {
			int px = x + 8 + (int) ((w - 24) * (i / (float) Math.max(1, links - 1)));
			int py = y + 4 + (i % 2 == 0 ? 0 : 2);
			ctx.fill(px, py, px + 9, py + 3, chainDark);
			ctx.fill(px + 1, py, px + 8, py + 1, chainSteel);
			// Alternate diagonal offset to read as interlocking rings
			if (i % 2 == 0) {
				ctx.fill(px + 3, py + 3, px + 6, py + 5, chainHi);
			}
		}

		// 3. Padlock clamping shut in the middle of the button
		int lx = x + w / 2 - 6;
		int ly = y + h / 2 - 7;
		int lockAlpha = (int) Math.round(255 * Math.max(0.0F, Math.min(1.0F, snap * 2.0F)));
		int gold = (lockAlpha << 24) | 0xFBBF24;
		int goldDark = (lockAlpha << 24) | 0xB45309;
		// shackle
		ctx.fill(lx + 1, ly, lx + 11, ly + 2, gold);
		ctx.fill(lx, ly + 1, lx + 2, ly + 5, gold);
		ctx.fill(lx + 10, ly + 1, lx + 12, ly + 5, gold);
		// body
		ctx.fill(lx - 1, ly + 5, lx + 13, ly + 14, gold);
		ctx.fill(lx - 1, ly + 5, lx + 13, ly + 6, goldDark);
		// keyhole
		ctx.fill(lx + 5, ly + 8, lx + 7, ly + 12, 0xFF1A1016);
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
			float hwShadow = seg.widthPx * 0.95F;
			emitQuadLine(quads, mat, seg.x1 + 1.0F, seg.y1 + 1.2F, ex + 1.0F, ey + 1.2F, nx * hwShadow, ny * hwShadow, 0xB8090B10);

			// Crisp silver-ice / pale-amethyst outer refraction bevel
			float hwOuter = seg.widthPx * 0.85F;
			int outerColor = seg.primary ? 0xDDE2E8F0 : 0xB5CBD5E1;
			emitQuadLine(quads, mat, seg.x1, seg.y1, ex, ey, nx * hwOuter, ny * hwOuter, outerColor);

			// Bright white-crystalline core along primary fracture veins
			if (seg.primary) {
				float hwCore = Math.max(0.55F, seg.widthPx * 0.38F);
				emitQuadLine(quads, mat, seg.x1, seg.y1, ex, ey, nx * hwCore, ny * hwCore, 0xFFFFFFFF);
			}
		}

		if (hasAny) {
			BuiltBuffer built = quads.endNullable();
			if (built != null) {
				BufferRenderer.drawWithGlobalProgram(built);
			}
		}

		// Central impact star glow intensifying right before 6.0s shatter!
		if (crackP > 0.15F) {
			RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
			float cx = this.width * 0.5F;
			float cy = this.height * 0.5F;
			float glowR = 26.0F + crackP * 75.0F;
			int centerAlpha = (int) Math.min(210, Math.round(crackP * crackP * 205.0F));
			BufferBuilder fan = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
			fan.vertex(mat, cx, cy, 0.0F).color((centerAlpha << 24) | 0xF8FAFC);
			for (int i = 0; i <= 36; i++) {
				double a = (i * Math.PI * 2.0D) / 36.0D;
				fan.vertex(mat, cx + (float) Math.cos(a) * glowR, cy + (float) Math.sin(a) * glowR, 0.0F).color(0x00DCE4F5);
			}
			BuiltBuffer builtGlow = fan.endNullable();
			if (builtGlow != null) {
				BufferRenderer.drawWithGlobalProgram(builtGlow);
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

		// 1. Crisp Shatter Flash at t = 6.00s .. 6.32s
		if (sinceShatterSec < 0.32D) {
			int flashAlpha = (int) Math.round(185 * (1.0D - sinceShatterSec / 0.32D));
			ctx.fill(0, 0, sw, sh, (flashAlpha << 24) | 0xF8FAFC);
		}

		// 2. Cinema Widescreen Letterbox Framing (keeps center 3D monochrome world 100% crisp & clear!)
		ctx.fillGradient(0, 0, sw, 44, 0xCC090B10, 0x00090B10);
		ctx.fillGradient(0, sh - 48, sw, sh, 0x00090B10, 0xD8090B10);

		// 3. Monochrome Silver-Gold Astral Chronometer Ring spinning backward during rewind
		drawMonochromeChronometer(ctx, cx, cy, Math.min(sw, sh) * 0.36F, elapsed, rewindEasedP);

		// 4. Full-Screen 44-Shard Tempered Glass Explosion tumbling & falling away (6.00s .. 7.75s)
		if (sinceShatterSec < 1.75D) {
			drawTumblingGlassShards(ctx, sw, sh, (float) sinceShatterSec);
		}

		// 5. Sleek Top & Bottom Rewind HUD Readout
		double shownRewound = ReturnByDeathClientController.getRewoundSeconds() * rewindEasedP;
		String topTag = elapsed < rewindStartMs
				? "\u2726 SHATTERING FATE  \u2022  RECLAIMING SCATTERED SOUL \u2726"
				: "\u27F2 TEMPORAL REWIND IN PROGRESS";
		int topW = this.textRenderer.getWidth(topTag);
		ctx.drawTextWithShadow(this.textRenderer, topTag, (sw - topW) / 2, 14, 0xFFE2E8F0);

		String timerText = String.format(Locale.ROOT, "-%.1fs / -%.0fs", shownRewound, ReturnByDeathClientController.getRewoundSeconds());
		int timeW = this.textRenderer.getWidth(timerText);
		ctx.drawTextWithShadow(this.textRenderer, timerText, (sw - timeW) / 2, sh - 34, CrimsonTheme.GOLD);

		// Minimal monochrome progress hairline at bottom
		int barW = Math.min(240, sw - 80);
		int barX = (sw - barW) / 2;
		int barY = sh - 18;
		ctx.fill(barX, barY, barX + barW, barY + 3, 0x661E293B);
		int fillW = (int) Math.round(barW * (1.0D - rewindEasedP));
		if (fillW > 0) {
			ctx.fill(barX, barY, barX + fillW, barY + 3, 0xFFE2E8F0);
		}
	}

	private void drawMonochromeChronometer(DrawContext ctx, int cx, int cy, float radius, long elapsed, double easedP) {
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();

		// Outer silver hairline dial
		int segs = 72;
		BufferBuilder ring = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i <= segs; i++) {
			double a = (i * Math.PI * 2.0D) / segs;
			float cos = (float) Math.cos(a);
			float sin = (float) Math.sin(a);
			ring.vertex(mat, cx + cos * (radius - 1.4F), cy + sin * (radius - 1.4F), 0.0F).color(0x48E2E8F0);
			ring.vertex(mat, cx + cos * (radius + 1.4F), cy + sin * (radius + 1.4F), 0.0F).color(0x00E2E8F0);
		}
		BuiltBuffer rb = ring.endNullable();
		if (rb != null) {
			BufferRenderer.drawWithGlobalProgram(rb);
		}

		// 12 Hour Dial Ticks + Reverse Clock Hands
		BufferBuilder quads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		double dialSpin = -easedP * Math.PI * 2.0D;
		for (int i = 0; i < 12; i++) {
			double a = dialSpin + (i * Math.PI * 2.0D / 12.0D);
			float cos = (float) Math.cos(a);
			float sin = (float) Math.sin(a);
			float inner = (i % 3 == 0) ? radius - 11.0F : radius - 6.0F;
			emitQuadLine(quads, mat, cx + cos * inner, cy + sin * inner, cx + cos * radius, cy + sin * radius, -sin * 1.1F, cos * 1.1F, 0x68F8FAFC);
		}

		// Fast counter-clockwise minute hand & hour hand (faint translucent silver so center view stays unobstructed)
		double minAngle = -Math.PI * 0.5D - easedP * Math.PI * 14.0D;
		double hrAngle = -Math.PI * 0.5D - easedP * Math.PI * 2.5D;
		float mCos = (float) Math.cos(minAngle), mSin = (float) Math.sin(minAngle);
		float hCos = (float) Math.cos(hrAngle), hSin = (float) Math.sin(hrAngle);
		emitQuadLine(quads, mat, cx + mCos * 18.0F, cy + mSin * 18.0F, cx + mCos * (radius - 14.0F), cy + mSin * (radius - 14.0F), -mSin * 1.1F, mCos * 1.1F, 0x52F8FAFC);
		emitQuadLine(quads, mat, cx + hCos * 18.0F, cy + hSin * 18.0F, cx + hCos * (radius * 0.58F), cy + hSin * (radius * 0.58F), -hSin * 1.5F, hCos * 1.5F, 0x58FDE68A);

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
		float fade = Math.max(0.0F, 1.0F - (tSec / 1.75F));
		int bodyAlpha = (int) Math.round(195 * fade);
		int glintAlpha = (int) Math.round(245 * fade);
		if (bodyAlpha <= 3) {
			RenderSystem.enableDepthTest();
			RenderSystem.enableCull();
			return;
		}

		for (GlassShard s : glassShards) {
			float px = s.cxPct * sw + s.vx * tSec;
			float py = s.cyPct * sh + s.vy * tSec + 250.0F * tSec * tSec;
			float rot = s.rotSpeed * tSec;
			// 3D tumbling perspective scale across one axis + bright specular flash when catching the light!
			float flipCos = (float) Math.cos(s.flipSpeed * tSec);
			float squeeze = Math.max(0.18F, Math.abs(flipCos));
			boolean specularFlash = Math.abs(flipCos) > 0.88F;

			int baseRgb = specularFlash
					? 0xFFFFFF
					: (s.crimsonTinted ? 0xB91C1C : 0xCBD5E1);
			int edgeRgb = 0xF8FAFC;

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
				emitQuadLine(bevel, mat, vxArr[k], vyArr[k], vxArr[nxt], vyArr[nxt], (-dy / len) * 0.7F, (dx / len) * 0.7F, (glintAlpha << 24) | 0xFFFFFF);
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
