package com.axell.hardcoretools.client.gui;

import java.util.Locale;

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
import net.minecraft.util.Formatting;

public class ReturnByDeathScreen extends Screen {
	public ReturnByDeathScreen() {
		super(Text.literal("Return by Death"));
	}

	@Override
	public boolean shouldPause() {
		// CRITICAL: Keep Singleplayer Integrated Server running at 20 TPS during the 5.0s rewind!
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Keep live 3D world completely unblurred so the fast-reverse flight is crystal clear!
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		if (!ReturnByDeathClientController.isActive()) {
			return;
		}

		long elapsed = ReturnByDeathClientController.getElapsedMs();
		long act1Ms = ReturnByDeathClientController.getAct1Ms(); // 1500ms
		long act2Ms = ReturnByDeathClientController.getAct2Ms(); // 4500ms

		if (elapsed < act1Ms) {
			renderAct1LockedDeathScreen(ctx, mouseX, mouseY, elapsed, act1Ms);
		} else {
			renderAct2ChronoRewindStage(ctx, elapsed, act1Ms, act2Ms);
		}
	}

	// ==================== BABAK 1 (0.0s - 1.5s): LOCKED HARDCORE/SURVIVAL DEATH SCREEN ====================
	private void renderAct1LockedDeathScreen(DrawContext ctx, int mx, int my, long elapsed, long act1Ms) {
		double t = Math.max(0.0D, Math.min(1.0D, elapsed / (double) Math.max(1L, act1Ms)));
		boolean hardcore = ReturnByDeathClientController.isWorldHardcore();

		// 1. Vanilla-style Crimson Death Gradient + Heartbeat Darkness Pulse
		double pulse = 0.0D;
		if (elapsed < 260L) {
			pulse = Math.sin((elapsed / 260.0D) * Math.PI);
		} else if (elapsed >= 760L && elapsed < 1040L) {
			pulse = Math.sin(((elapsed - 760L) / 280.0D) * Math.PI);
		}
		int topAlpha = Math.min(235, 165 + (int) (pulse * 40 + t * 28));
		int botAlpha = Math.min(248, 205 + (int) (pulse * 28 + t * 25));
		ctx.fillGradient(0, 0, this.width, this.height, (topAlpha << 24) | 0x500008, (botAlpha << 24) | 0x1A000A);

		int cx = this.width / 2;
		int btnW = 200;
		int btnH = 20;
		int btnX = cx - btnW / 2;
		int btn1Y = this.height / 4 + 72;
		int btn2Y = this.height / 4 + 96;

		// Tremor builds up as Abyssal Witch-Chains grip the screen (0.45..1.0) + extra rattle if clicked
		long sinceClick = System.currentTimeMillis() - ReturnByDeathClientController.getLockShakeClickMs();
		double clickJolt = (sinceClick >= 0L && sinceClick < 280L) ? (1.0D - sinceClick / 280.0D) * 4.5D : 0.0D;
		double gripTremor = (t > 0.42D ? ((t - 0.42D) / 0.58D) * 2.8D : 0.0D) + clickJolt;
		float jx = (float) (Math.sin(elapsed * 0.095D) * gripTremor);
		float jy = (float) (Math.cos(elapsed * 0.125D) * gripTremor);

		// 2. Creeping Abyssal Shadow Tendrils from all 4 corners (OpenGL smooth vector strips)
		beginVectorAlpha(ctx, false);
		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
		double tendrilReach = easeOutCubic(Math.min(1.0D, t * 1.18D));
		drawCreepingShadowTendrils(mat, this.width, this.height, cx, btn1Y + 22, tendrilReach, elapsed);
		endVectorBlend();

		// 3. Vanilla-style 2.0x Scaled "Game Over!" / "You Died!" Header + Cause of Death + Score
		String bigTitle = hardcore ? "Game Over!" : "You Died!";
		ctx.getMatrices().push();
		ctx.getMatrices().translate(cx + jx * 0.4F, 30.0F + jy * 0.4F, 0.0F);
		ctx.getMatrices().scale(2.0F, 2.0F, 2.0F);
		int titleW = this.textRenderer.getWidth(bigTitle);
		ctx.drawTextWithShadow(this.textRenderer, bigTitle, -titleW / 2, 0, 0xFFFF3838);
		ctx.getMatrices().pop();

		String cause = ReturnByDeathClientController.getDeathMessage();
		drawCentered(ctx, cause, cx, 82, 0xFFF2E6EA);

		String scoreLine = "Score: " + Formatting.YELLOW + ReturnByDeathClientController.getScore();
		drawCentered(ctx, scoreLine, cx, 98, 0xFFD8CCD2);

		// 4. Locked Respawn / Spectate World & Title Screen Buttons (Held Hostage by Return by Death!)
		String topBtnLabel = hardcore ? "⛓  Spectate World  [🔒]  ⛓" : "⛓  Respawn  [🔒]  ⛓";
		String botBtnLabel = "⛓  Title Screen  [🔒]  ⛓";

		drawLockedDeathButton(ctx, (int) (btnX + jx), (int) (btn1Y + jy), btnW, btnH, topBtnLabel,
				inRect(mx, my, btnX, btn1Y, btnW, btnH), t);
		drawLockedDeathButton(ctx, (int) (btnX - jx * 0.8F), (int) (btn2Y + jy), btnW, btnH, botBtnLabel,
				inRect(mx, my, btnX, btn2Y, btnW, btnH), t);

		// 5. Glowing Violet-Crimson Fracture Cracks spider-webbing across the locked buttons (0.35s .. 1.50s)
		if (t > 0.24D) {
			double crackP = (t - 0.24D) / 0.76D;
			beginVectorAlpha(ctx, true);
			Matrix4f gMat = ctx.getMatrices().peek().getPositionMatrix();
			drawButtonFractureCracks(gMat, btnX + jx, btn1Y + jy, btnW, btnH, crackP, 1L);
			drawButtonFractureCracks(gMat, btnX - jx * 0.8F, btn2Y + jy, btnW, btnH, crackP, 2L);

			// Rising Singularity Glow right before the 1.50s Shatter!
			if (t > 0.72D) {
				double preBurst = (t - 0.72D) / 0.28D;
				int glowA = (int) Math.round(215 * preBurst * preBurst);
				drawRadialGlow(gMat, cx, btn1Y + 22, (float) (28.0D + preBurst * 135.0D),
						withAlpha(0xDF9BFF, glowA), 0x00320854);
			}
			endVectorBlend();
		}

		// 6. Ominous Fate Rejection Caption below locked buttons
		int warnAlpha = Math.min(255, (int) Math.round(255 * Math.min(1.0D, t * 2.2D)));
		String rejectText = (sinceClick >= 0L && sinceClick < 600L)
				? "✦ FATE REJECTS YOUR DEATH — CANNOT SURRENDER ✦"
				: "✦ Abyssal Pact Triggered — Rejecting Death... ✦";
		int rejectCol = (sinceClick >= 0L && sinceClick < 600L) ? 0xFFFF5277 : 0xFFD884FF;
		drawCentered(ctx, rejectText, cx, btn2Y + 30, withAlpha(rejectCol, warnAlpha));
	}

	private void drawLockedDeathButton(DrawContext ctx, int x, int y, int w, int h, String label, boolean hovered, double t) {
		int bgTop = hovered ? 0xFF28112B : 0xFF1B0C1E;
		int bgBot = hovered ? 0xFF140717 : 0xFF0D0510;
		int border = t > 0.55D ? 0xFFB84DFF : (hovered ? 0xFF8E3B9E : 0xFF562863);

		ctx.fillGradient(x, y, x + w, y + h, bgTop, bgBot);
		ctx.drawBorder(x, y, w, h, border);
		ctx.drawBorder(x + 1, y + 1, w - 2, h - 2, 0x55000000);

		// Diagonal iron-shadow chain bars across left & right ends of button
		ctx.fill(x + 3, y + 2, x + 7, y + h - 2, 0x883A1846);
		ctx.fill(x + w - 7, y + 2, x + w - 3, y + h - 2, 0x883A1846);

		int textCol = hovered ? 0xFFE5B8FF : 0xFFA889B5;
		drawCentered(ctx, label, x + w / 2, y + 6, textCol);
	}

	// ==================== BABAK 2 (1.5s - 4.5s): FAST-REVERSE WORLD & ASTRAL CLOCKWORK ====================
	private void renderAct2ChronoRewindStage(DrawContext ctx, long elapsed, long act1Ms, long act2Ms) {
		long act2Elapsed = Math.max(0L, elapsed - act1Ms);
		long act2Span = Math.max(1L, act2Ms - act1Ms); // 3000ms
		double rawP = Math.max(0.0D, Math.min(1.0D, act2Elapsed / (double) act2Span));
		double easedP = -(Math.cos(Math.PI * rawP) - 1.0D) / 2.0D;

		float cx = this.width * 0.5F;
		float cy = this.height * 0.5F - 6.0F;

		// 1. Subtle Abyssal Cinema Letterbox Vignette (leaves center 3D world 100% crisp & visible!)
		ctx.fillGradient(0, 0, this.width, 54, 0xCC090212, 0x00090212);
		ctx.fillGradient(0, this.height - 64, this.width, this.height, 0x00090212, 0xDD090212);

		// 2. OpenGL Additive Vector Clockwork, Shatter Shards & Temporal Warp Streaks
		beginVectorAlpha(ctx, true);
		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();

		// 2a. Shattered Death-Button Shards & Expanding Shockwave (first 0.75s of Act 2!)
		double secInAct2 = act2Elapsed / 1000.0D;
		if (secInAct2 < 0.75D) {
			double sp = secInAct2 / 0.75D;
			double ease = 1.0D - Math.pow(1.0D - sp, 3.0D);
			int swAlpha = (int) Math.round(230 * (1.0D - sp));
			float swR = (float) (14.0D + ease * Math.max(this.width, this.height) * 0.58D);
			drawGlowRing(mat, cx, cy, swR, 1.4F, 7.0F, withAlpha(0xD875FF, swAlpha));

			for (int i = 0; i < 16; i++) {
				double ang = i * (Math.PI * 2.0D / 16.0D) + (i % 2 == 0 ? 0.14D : -0.09D);
				float dist = (float) (18.0D + ease * (145.0D + (i % 3) * 38.0D));
				float sx = cx + (float) (Math.cos(ang) * dist);
				float sy = (this.height * 0.25F + 84.0F) + (float) (Math.sin(ang) * dist * 0.75D);
				double spin = ang + sp * (i % 2 == 0 ? 3.4D : -3.1D);
				int col = (i % 2 == 0) ? 0xFFFF4268 : 0xFFC86BFF;
				drawShardPolygon(mat, sx, sy, 6.5F * (float) (1.0D - sp * 0.45D), spin,
						withAlpha(col, swAlpha), withAlpha(0x52106B, swAlpha / 2));
			}
		}

		// 2b. 20 Inward-Streaming Temporal Warp Streaks around screen periphery
		float diag = (float) Math.hypot(this.width * 0.5D, this.height * 0.5D);
		for (int i = 0; i < 20; i++) {
			double baseAng = i * (Math.PI * 2.0D / 20.0D) - rawP * 0.85D;
			double wave = ((rawP * 5.5D) + (i * 0.17D)) % 1.0D;
			// Flows INWARD from outer screen edge toward clock outer perimeter (reverse time flow!)
			float rOuter = (float) (diag * (1.05D - wave * 0.52D));
			float rInner = Math.max(78.0F, rOuter - 34.0F);
			if (rOuter > rInner + 4.0F) {
				int streakAlpha = (int) Math.round(130 * Math.sin(wave * Math.PI));
				int streakRgb = (i % 2 == 0) ? 0xB86BFF : 0x38D4FF;
				float x1 = cx + (float) (Math.cos(baseAng) * rOuter);
				float y1 = cy + (float) (Math.sin(baseAng) * rOuter);
				float x2 = cx + (float) (Math.cos(baseAng) * rInner);
				float y2 = cy + (float) (Math.sin(baseAng) * rInner);
				drawGlowSegment(mat, x1, y1, x2, y2, 0.75F, withAlpha(streakRgb, streakAlpha));
			}
		}

		// 2c. Giant Translucent Astral Clockwork (Counter-Rotating Hands!)
		float clockR = Math.max(46.0F, Math.min(68.0F, this.height * 0.27F));
		float innerGearR = clockR * 0.68F;

		drawRadialGlow(mat, cx, cy, clockR + 22.0F, 0x308B3DFF, 0x0018042E);
		drawGlowRing(mat, cx, cy, clockR, 0.90F, 4.2F, 0xBBC86BFF);
		drawGlowRing(mat, cx, cy, clockR + 4.5F, 0.45F, 2.0F, 0x7738D4FF);
		drawGlowRing(mat, cx, cy, innerGearR, 0.55F, 2.4F, 0x88FFB84D);

		// 60 Dial Tick Marks + 12 Glowing Cardinal Hour Runes
		for (int m = 0; m < 60; m++) {
			double ang = -Math.PI * 0.5D + m * (Math.PI * 2.0D / 60.0D);
			boolean majorHour = (m % 5 == 0);
			float r1 = clockR - (majorHour ? 7.5F : 3.6F);
			float r2 = clockR - 1.2F;
			float x1 = cx + (float) (Math.cos(ang) * r1);
			float y1 = cy + (float) (Math.sin(ang) * r1);
			float x2 = cx + (float) (Math.cos(ang) * r2);
			float y2 = cy + (float) (Math.sin(ang) * r2);
			drawGlowSegment(mat, x1, y1, x2, y2, majorHour ? 0.95F : 0.45F,
					majorHour ? 0xDDFFE082 : 0x66C86BFF);
			if (majorHour) {
				float nx = cx + (float) (Math.cos(ang) * (clockR + 4.5F));
				float ny = cy + (float) (Math.sin(ang) * (clockR + 4.5F));
				drawRadialGlow(mat, nx, ny, 3.0F, 0xCCFFE082, 0x00FFD859);
			}
		}

		// Counter-Rotating Alchemical Inner Spokes
		double gearRot = rawP * Math.PI * 3.0D;
		for (int k = 0; k < 6; k++) {
			double gAng = gearRot + k * (Math.PI / 3.0D);
			float gx1 = cx + (float) (Math.cos(gAng) * 8.0F);
			float gy1 = cy + (float) (Math.sin(gAng) * 8.0F);
			float gx2 = cx + (float) (Math.cos(gAng) * innerGearR);
			float gy2 = cy + (float) (Math.sin(gAng) * innerGearR);
			drawGlowSegment(mat, gx1, gy1, gx2, gy2, 0.55F, 0x5538D4FF);
		}

		// Fast Counter-Clockwise Minute Hand (6 full backward revolutions!) & Hour Hand
		double minuteAng = -Math.PI * 0.5D - (easedP * Math.PI * 2.0D * 6.0D);
		double hourAng = -Math.PI * 0.5D - (easedP * Math.PI * 2.0D * 0.75D);

		// Luminous trailing sweep behind Minute Hand
		for (int trail = 1; trail <= 10; trail++) {
			double lagAng = minuteAng + trail * 0.085D;
			int trailAlpha = (int) Math.round(115 * (1.0D - trail / 11.0D));
			float tx = cx + (float) (Math.cos(lagAng) * (clockR - 8.5F));
			float ty = cy + (float) (Math.sin(lagAng) * (clockR - 8.5F));
			drawGlowSegment(mat, cx, cy, tx, ty, 0.9F, withAlpha(0x38D4FF, trailAlpha));
		}

		float minTipX = cx + (float) (Math.cos(minuteAng) * (clockR - 6.5F));
		float minTipY = cy + (float) (Math.sin(minuteAng) * (clockR - 6.5F));
		drawGlowSegment(mat, cx, cy, minTipX, minTipY, 1.35F, 0xFF80F0FF);

		float hrTipX = cx + (float) (Math.cos(hourAng) * (clockR * 0.54F));
		float hrTipY = cy + (float) (Math.sin(hourAng) * (clockR * 0.54F));
		drawGlowSegment(mat, cx, cy, hrTipX, hrTipY, 1.65F, 0xFFFFD86B);

		// Central Chrono Singularity Hub
		drawRadialGlow(mat, cx, cy, 11.0F, 0xEEFFF5D6, 0x00C86BFF);

		// 2d. Final 0.24s Pre-Snap Convergence Bloom (4.26s .. 4.50s -> seamless handoff into Babak 3!)
		if (rawP > 0.92D) {
			double snapRamp = (rawP - 0.92D) / 0.08D;
			int bloomA = (int) Math.round(230 * snapRamp * snapRamp);
			drawRadialGlow(mat, cx, cy, (float) (Math.max(this.width, this.height) * 0.78D * snapRamp),
					withAlpha(0xF5E6FF, bloomA), withAlpha(0x9B4DFF, bloomA / 3));
		}

		endVectorBlend();

		// 3. Crisp Rewind Time Counter & Subtitle below the Astral Clock
		double totalSec = ReturnByDeathClientController.getRewoundSeconds();
		double curRewound = easedP * totalSec;
		String timerHeadline = String.format(Locale.ROOT, "⏪  REWINDING TIME:  -%.1fs / -%.1fs", curRewound, totalSec);
		int textY = Math.min(this.height - 38, (int) (cy + clockR + 14));
		drawCentered(ctx, timerHeadline, (int) cx, textY, CrimsonTheme.GOLD);
		drawCentered(ctx, "✦ Reversing World, Blocks, Entities & Fate... ✦", (int) cx, textY + 13, 0xFFD8B8FF);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		long elapsed = ReturnByDeathClientController.getElapsedMs();
		if (elapsed < ReturnByDeathClientController.getAct1Ms()) {
			int cx = this.width / 2;
			int btnX = cx - 100;
			int btn1Y = this.height / 4 + 72;
			int btn2Y = this.height / 4 + 96;
			if (inRect(mouseX, mouseY, btnX, btn1Y, 200, 20) || inRect(mouseX, mouseY, btnX, btn2Y, 200, 20)) {
				ReturnByDeathClientController.triggerLockedButtonRattle();
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Swallow all keys during Act 1 & Act 2 so ESC/Inventory cannot interrupt the rewind!
		return true;
	}

	// ==================== OPENGL VECTOR HELPERS ====================
	private static void beginVectorAlpha(DrawContext ctx, boolean additive) {
		ctx.draw();
		RenderSystem.enableBlend();
		if (additive) {
			RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		} else {
			RenderSystem.defaultBlendFunc();
		}
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
	}

	private static void endVectorBlend() {
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private static void flush(BufferBuilder buf) {
		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}
	}

	private static void drawCreepingShadowTendrils(Matrix4f mat, int sw, int sh, float targetX, float targetY, double reach, long elapsed) {
		float[][] origins = {
				{0.0F, 0.0F}, {sw, 0.0F}, {0.0F, sh}, {sw, sh},
				{0.0F, sh * 0.5F}, {sw, sh * 0.5F}, {sw * 0.3F, sh}, {sw * 0.7F, sh}
		};
		for (int t = 0; t < origins.length; t++) {
			float ox = origins[t][0];
			float oy = origins[t][1];
			int steps = 16;
			BufferBuilder strip = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
			for (int i = 0; i <= steps; i++) {
				double f = (i / (double) steps) * reach;
				float bx = (float) (ox + (targetX - ox) * f * 0.88D);
				float by = (float) (oy + (targetY - oy) * f * 0.88D);
				double wave = Math.sin(f * 7.5D - elapsed * 0.006D + t * 1.3D) * (1.0D - f * 0.5D) * 14.0D;
				float dx = targetX - ox;
				float dy = targetY - oy;
				float len = Math.max(1.0F, (float) Math.hypot(dx, dy));
				float nx = -dy / len;
				float ny = dx / len;

				float px = bx + nx * (float) wave;
				float py = by + ny * (float) wave;
				float halfW = (float) ((1.0D - i / (double) steps) * 14.0D + 1.2D);
				int alpha = (int) Math.round(185 * (1.0D - (i / (double) steps) * 0.55D));
				int col = (alpha << 24) | ((t % 2 == 0) ? 0x160321 : 0x2B0738);

				strip.vertex(mat, px - nx * halfW, py - ny * halfW, 0.0F).color(col);
				strip.vertex(mat, px + nx * halfW, py + ny * halfW, 0.0F).color(col);
			}
			flush(strip);
		}
	}

	private static void drawButtonFractureCracks(Matrix4f mat, float bx, float by, float bw, float bh, double progress, long seed) {
		int branches = 5;
		int alpha = (int) Math.round(235 * Math.min(1.0D, progress * 1.25D));
		int col = withAlpha(0xDF6BFF, alpha);
		for (int b = 0; b < branches; b++) {
			float sx = bx + bw * (0.15F + 0.17F * b);
			float sy = (b % 2 == 0) ? by : (by + bh);
			float dirY = (b % 2 == 0) ? 1.0F : -1.0F;
			float curX = sx;
			float curY = sy;
			int segs = 4;
			for (int s = 1; s <= segs; s++) {
				if ((s - 1) / (double) segs > progress) break;
				double segFrac = Math.min(1.0D, (progress - (s - 1) / (double) segs) * segs);
				float zig = ((s + b + (int) seed) % 2 == 0 ? 1.0F : -1.0F) * (5.5F + b * 1.2F);
				float nx = curX + zig * (float) segFrac;
				float ny = curY + dirY * (bh / segs) * (float) segFrac;
				drawGlowSegment(mat, curX, curY, nx, ny, 0.75F, col);
				curX = nx;
				curY = ny;
			}
		}
	}

	private static void drawRadialGlow(Matrix4f mat, float cx, float cy, float radius, int centerCol, int edgeCol) {
		if (radius <= 0.5F || ((centerCol >>> 24) & 0xFF) == 0) return;
		int segs = 44;
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		buf.vertex(mat, cx, cy, 0.0F).color(centerCol);
		for (int i = 0; i <= segs; i++) {
			double ang = (i * Math.PI * 2.0D) / segs;
			buf.vertex(mat, cx + (float) (Math.cos(ang) * radius), cy + (float) (Math.sin(ang) * radius), 0.0F).color(edgeCol);
		}
		flush(buf);
	}

	private static void drawGlowRing(Matrix4f mat, float cx, float cy, float radius, float coreHalfW, float featherW, int color) {
		int baseAlpha = (color >>> 24) & 0xFF;
		if (radius <= 1.0F || baseAlpha <= 0) return;
		int segs = 64;
		int softCol = withAlpha(color, Math.max(1, (int) (baseAlpha * 0.42F)));
		int zeroCol = withAlpha(color, 0);
		float rInEdge = Math.max(0.2F, radius - coreHalfW - featherW);
		float rOutEdge = radius + coreHalfW + featherW;

		BufferBuilder inSkirt = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i <= segs; i++) {
			double a = (i * Math.PI * 2.0D) / segs;
			float c = (float) Math.cos(a);
			float s = (float) Math.sin(a);
			inSkirt.vertex(mat, cx + c * rInEdge, cy + s * rInEdge, 0.0F).color(zeroCol);
			inSkirt.vertex(mat, cx + c * radius, cy + s * radius, 0.0F).color(softCol);
		}
		flush(inSkirt);

		BufferBuilder outSkirt = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i <= segs; i++) {
			double a = (i * Math.PI * 2.0D) / segs;
			float c = (float) Math.cos(a);
			float s = (float) Math.sin(a);
			outSkirt.vertex(mat, cx + c * radius, cy + s * radius, 0.0F).color(softCol);
			outSkirt.vertex(mat, cx + c * rOutEdge, cy + s * rOutEdge, 0.0F).color(zeroCol);
		}
		flush(outSkirt);

		BufferBuilder core = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		float rCoreIn = Math.max(0.2F, radius - coreHalfW);
		float rCoreOut = radius + coreHalfW;
		for (int i = 0; i <= segs; i++) {
			double a = (i * Math.PI * 2.0D) / segs;
			float c = (float) Math.cos(a);
			float s = (float) Math.sin(a);
			core.vertex(mat, cx + c * rCoreIn, cy + s * rCoreIn, 0.0F).color(color);
			core.vertex(mat, cx + c * rCoreOut, cy + s * rCoreOut, 0.0F).color(color);
		}
		flush(core);
	}

	private static void drawGlowSegment(Matrix4f mat, float x1, float y1, float x2, float y2, float halfW, int color) {
		int alpha = (color >>> 24) & 0xFF;
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.hypot(dx, dy);
		if (len < 0.2F || alpha <= 0) return;
		float nx = -dy / len;
		float ny = dx / len;
		float gw = halfW * 2.6F;
		int glowCol = withAlpha(color, Math.max(1, alpha / 3));

		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		buf.vertex(mat, x1 + nx * gw, y1 + ny * gw, 0.0F).color(glowCol);
		buf.vertex(mat, x2 + nx * gw, y2 + ny * gw, 0.0F).color(glowCol);
		buf.vertex(mat, x2 - nx * gw, y2 - ny * gw, 0.0F).color(glowCol);
		buf.vertex(mat, x1 - nx * gw, y1 - ny * gw, 0.0F).color(glowCol);

		buf.vertex(mat, x1 + nx * halfW, y1 + ny * halfW, 0.0F).color(color);
		buf.vertex(mat, x2 + nx * halfW, y2 + ny * halfW, 0.0F).color(color);
		buf.vertex(mat, x2 - nx * halfW, y2 - ny * halfW, 0.0F).color(color);
		buf.vertex(mat, x1 - nx * halfW, y1 - ny * halfW, 0.0F).color(color);
		flush(buf);
	}

	private static void drawShardPolygon(Matrix4f mat, float cx, float cy, float size, double rot, int coreCol, int edgeCol) {
		if (size <= 0.3F || ((coreCol >>> 24) & 0xFF) == 0) return;
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		buf.vertex(mat, cx, cy, 0.0F).color(coreCol);
		float[] scales = {1.35F, 0.55F, 1.05F, 0.48F, 1.35F};
		double[] offsets = {0.0D, 1.35D, Math.PI, 4.55D, Math.PI * 2.0D};
		for (int i = 0; i < scales.length; i++) {
			double a = rot + offsets[i];
			float r = size * scales[i];
			buf.vertex(mat, cx + (float) (Math.cos(a) * r), cy + (float) (Math.sin(a) * r), 0.0F).color(edgeCol);
		}
		flush(buf);
	}

	private void drawCentered(DrawContext ctx, String text, int cx, int y, int color) {
		ctx.drawTextWithShadow(this.textRenderer, text, cx - this.textRenderer.getWidth(text) / 2, y, color);
	}

	private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static double easeOutCubic(double t) {
		double c = Math.max(0.0D, Math.min(1.0D, t));
		return 1.0D - Math.pow(1.0D - c, 3.0D);
	}

	private static int withAlpha(int rgbOrArgb, int alpha) {
		int clamped = Math.max(0, Math.min(255, alpha));
		return (clamped << 24) | (rgbOrArgb & 0x00FFFFFF);
	}
}
