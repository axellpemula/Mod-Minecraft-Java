package com.axell.hardcoretools.client.gui;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

public final class CrimsonTheme {
	public record ThemePalette(
			String name,
			int swatchColor,
			int bgWindow,
			int bgHeaderTop,
			int bgHeaderBot,
			int bgSidebar,
			int bgCard,
			int bgCardHover,
			int bgInput,
			int accent,
			int accentBright,
			int accentDark,
			int borderOuter,
			int borderSubtle,
			int borderHover,
			int textPrimary,
			int textSecondary,
			int textMuted
	) {
	}

	public static final ThemePalette[] PALETTES = {
			// 0: Crimson Obsidian (Default Hardcore Signature)
			new ThemePalette(
					"Crimson Obsidian", 0xFFFF385C,
					0xDF0E090D, 0xF2240C14, 0xEB13080D, 0xE6110A0E,
					0xCC1B1117, 0xE6281720, 0xF00D070B,
					0xFFE02444, 0xFFFF4264, 0xFF7D1126,
					0xFFD92648, 0xFF361F29, 0xFF782C42,
					0xFFFFF7F9, 0xFFCDB5BE, 0xFF8C727C
			),
			// 1: Witch's Amethyst (Return by Death Violet)
			new ThemePalette(
					"Witch's Amethyst", 0xFFB85CFF,
					0xDF0B0814, 0xF21C0E30, 0xEB10091D, 0xE60E0919,
					0xCC171126, 0xE6221838, 0xF00A0712,
					0xFF9B42F5, 0xFFC26BFF, 0xFF531D8C,
					0xFFA84DFF, 0xFF2E2145, 0xFF693B9E,
					0xFFF9F5FF, 0xFFC4B5DB, 0xFF83739E
			),
			// 2: Cyber Emerald (Neon Jade Slate)
			new ThemePalette(
					"Cyber Emerald", 0xFF1FE088,
					0xDF08110E, 0xF20B241B, 0xEB071410, 0xE6091411,
					0xCC10201B, 0xE6162E26, 0xF0060D0B,
					0xFF17B86E, 0xFF28E88F, 0xFF0C663D,
					0xFF1FD480, 0xFF1C382E, 0xFF287556,
					0xFFF2FFF9, 0xFFB0CFC2, 0xFF6E9183
			),
			// 3: Solar Gold (Imperial Amber Onyx)
			new ThemePalette(
					"Solar Gold", 0xFFFFB42B,
					0xDF120E08, 0xF2291C0B, 0xEB171007, 0xE6140F08,
					0xCC21190E, 0xE6302414, 0xF00D0A05,
					0xFFE59419, 0xFFFFB838, 0xFF804E08,
					0xFFF5A322, 0xFF3D2E1B, 0xFF825D26,
					0xFFFFFCF2, 0xFFD4C5A9, 0xFF948368
			),
			// 4: Arctic Sapphire (Clean Frosted Cyan)
			new ThemePalette(
					"Arctic Sapphire", 0xFF2EC4FF,
					0xDF080E17, 0xF20C1E33, 0xEB081220, 0xE609121E,
					0xCC101D2E, 0xE6172A42, 0xF0060B12,
					0xFF1BA3E5, 0xFF38C8FF, 0xFF0D5680,
					0xFF29B6F6, 0xFF1D334A, 0xFF2E6794,
					0xFFF4FAFF, 0xFFB2C9DE, 0xFF7089A1
			)
	};

	private static int activeThemeIndex = 0;

	// Live Dynamic Palette Tokens (automatically updated when theme changes!)
	public static int BG_WINDOW = PALETTES[0].bgWindow();
	public static int BG_HEADER_TOP = PALETTES[0].bgHeaderTop();
	public static int BG_HEADER_BOT = PALETTES[0].bgHeaderBot();
	public static int BG_SIDEBAR = PALETTES[0].bgSidebar();
	public static int BG_CARD = PALETTES[0].bgCard();
	public static int BG_CARD_HOVER = PALETTES[0].bgCardHover();
	public static int BG_INPUT = PALETTES[0].bgInput();

	public static int CRIMSON = PALETTES[0].accent();
	public static int CRIMSON_BRIGHT = PALETTES[0].accentBright();
	public static int CRIMSON_DARK = PALETTES[0].accentDark();
	public static int BORDER_OUTER = PALETTES[0].borderOuter();
	public static int BORDER_SUBTLE = PALETTES[0].borderSubtle();
	public static int BORDER_HOVER = PALETTES[0].borderHover();

	public static int TEXT_PRIMARY = PALETTES[0].textPrimary();
	public static int TEXT_SECONDARY = PALETTES[0].textSecondary();
	public static int TEXT_MUTED = PALETTES[0].textMuted();

	// Semantic status colors (consistent across themes)
	public static final int EMERALD = 0xFF23D17B;
	public static final int GOLD = 0xFFFFBC3B;
	public static final int AMETHYST = 0xFFD86BFF;
	public static final int CYAN = 0xFF38C9FF;

	// Smooth 60-240 FPS micro-animation state for toggles & sliders
	private static final Map<Integer, Float> SWITCH_PROGRESS = new HashMap<>();
	private static final Map<Integer, Long> SWITCH_TIME_NS = new HashMap<>();

	private CrimsonTheme() {
	}

	public static int getActiveThemeIndex() {
		return activeThemeIndex;
	}

	public static ThemePalette getActivePalette() {
		return PALETTES[activeThemeIndex];
	}

	public static void applyTheme(int index) {
		int idx = Math.max(0, Math.min(PALETTES.length - 1, index));
		activeThemeIndex = idx;
		ThemePalette p = PALETTES[idx];
		BG_WINDOW = p.bgWindow();
		BG_HEADER_TOP = p.bgHeaderTop();
		BG_HEADER_BOT = p.bgHeaderBot();
		BG_SIDEBAR = p.bgSidebar();
		BG_CARD = p.bgCard();
		BG_CARD_HOVER = p.bgCardHover();
		BG_INPUT = p.bgInput();
		CRIMSON = p.accent();
		CRIMSON_BRIGHT = p.accentBright();
		CRIMSON_DARK = p.accentDark();
		BORDER_OUTER = p.borderOuter();
		BORDER_SUBTLE = p.borderSubtle();
		BORDER_HOVER = p.borderHover();
		TEXT_PRIMARY = p.textPrimary();
		TEXT_SECONDARY = p.textSecondary();
		TEXT_MUTED = p.textMuted();
	}

	// ==================== MODERN FROSTED GLASS & ROUNDED VECTOR WIDGETS ====================

	public static void drawPanel(DrawContext ctx, int x, int y, int w, int h) {
		float r = 8.0F;
		// Multi-layer soft ambient drop shadow
		drawRoundedRect(ctx, x - 5, y - 4, w + 10, h + 10, r + 4.0F, 0x30000000);
		drawRoundedRect(ctx, x - 2, y - 2, w + 4, h + 5, r + 2.0F, 0x58000000);

		// Frosted dark glass body
		drawRoundedRect(ctx, x, y, w, h, r, BG_WINDOW);
		// Ultra-fine rounded border + subtle top specular glass rim
		drawRoundedOutline(ctx, x, y, w, h, r, 0.6F, withAlpha(BORDER_OUTER, 160));
		drawRoundedRect(ctx, x + 8, y + 0.8F, w - 16, 0.6F, 0.3F, withAlpha(0xFFFFFFFF, 34));
	}

	public static void drawCard(DrawContext ctx, int x, int y, int w, int h, boolean hovered, int accentBarColor) {
		float r = 5.0F;
		if (hovered) {
			drawRoundedRect(ctx, x - 0.8F, y - 0.8F, w + 1.6F, h + 1.6F, r + 0.8F, withAlpha(CRIMSON_BRIGHT, 24));
		}
		drawRoundedGradient(ctx, x, y, w, h, r,
				hovered ? brighten(BG_CARD_HOVER, 8) : brighten(BG_CARD, 4),
				hovered ? BG_CARD_HOVER : BG_CARD);
		drawRoundedOutline(ctx, x, y, w, h, r, 0.55F, hovered ? BORDER_HOVER : BORDER_SUBTLE);

		if (accentBarColor != 0) {
			float barH = Math.max(6.0F, h - 7.0F);
			drawRoundedRect(ctx, x + 1.8F, y + (h - barH) * 0.5F, 1.2F, barH, 0.6F, withAlpha(accentBarColor, 225));
		}
	}

	public static void drawSectionHeader(DrawContext ctx, TextRenderer font, int x, int y, int w, String title) {
		drawRoundedRect(ctx, x, y + 3.5F, 1.6F, 9.5F, 0.8F, CRIMSON_BRIGHT);
		ctx.drawTextWithShadow(font, title, x + 6, y + 4, CRIMSON_BRIGHT);
		int textW = font.getWidth(title);
		if (x + textW + 13 < x + w) {
			ctx.fillGradient(x + textW + 11, y + 8, x + w, y + 9, withAlpha(CRIMSON_BRIGHT, 68), withAlpha(BORDER_SUBTLE, 20));
		}
	}

	public static void drawPillSwitch(DrawContext ctx, int x, int y, boolean on, boolean hovered) {
		int w = 30;
		int h = 14;
		float r = h * 0.5F;

		// Smooth frame-independent spring animation keyed by widget screen slot
		int animKey = (x * 7349) ^ (y * 3187);
		long now = System.nanoTime();
		float target = on ? 1.0F : 0.0F;
		float cur = SWITCH_PROGRESS.getOrDefault(animKey, target);
		long prevNs = SWITCH_TIME_NS.getOrDefault(animKey, now);
		float dt = Math.min(0.05F, Math.max(0.0F, (now - prevNs) / 1_000_000_000.0F));
		if (Math.abs(target - cur) > 0.003F) {
			cur += (target - cur) * (1.0F - (float) Math.exp(-dt * 18.0F));
		} else {
			cur = target;
		}
		SWITCH_PROGRESS.put(animKey, cur);
		SWITCH_TIME_NS.put(animKey, now);
		if (SWITCH_PROGRESS.size() > 256) {
			SWITCH_PROGRESS.clear();
			SWITCH_TIME_NS.clear();
		}

		int offTrack = hovered ? brighten(BG_CARD_HOVER, 12) : darken(BG_CARD, 8);
		int onTrack = hovered ? CRIMSON_BRIGHT : CRIMSON;
		int trackColor = lerpColor(offTrack, onTrack, cur);
		int borderColor = lerpColor(BORDER_SUBTLE, brighten(CRIMSON_BRIGHT, 28), cur);

		if (cur > 0.15F) {
			drawRoundedRect(ctx, x - 0.8F, y - 0.8F, w + 1.6F, h + 1.6F, r + 0.8F, withAlpha(CRIMSON_BRIGHT, (int) (36 * cur)));
		}
		drawRoundedRect(ctx, x, y, w, h, r, trackColor);
		drawRoundedOutline(ctx, x, y, w, h, r, 0.55F, borderColor);

		float knobR = 4.8F;
		float knobCx = (x + 7.0F) + cur * (w - 14.0F);
		float knobCy = y + h * 0.5F;
		drawCircle(ctx, knobCx, knobCy + 0.6F, knobR, 0x44000000);
		drawCircle(ctx, knobCx, knobCy, knobR, lerpColor(0xFFC5B8C0, 0xFFFFFFFF, cur));
	}

	public static void drawButton(DrawContext ctx, TextRenderer font, int x, int y, int w, int h, String label, int accentColor, boolean hovered) {
		float r = 4.5F;
		int top = hovered ? brighten(accentColor, 28) : brighten(accentColor, 8);
		int bot = hovered ? darken(accentColor, 18) : darken(accentColor, 34);
		if (hovered) {
			drawRoundedRect(ctx, x - 0.8F, y - 0.8F, w + 1.6F, h + 1.6F, r + 0.8F, withAlpha(accentColor, 48));
		}
		drawRoundedGradient(ctx, x, y, w, h, r, top, bot);
		drawRoundedOutline(ctx, x, y, w, h, r, 0.6F, withAlpha(hovered ? brighten(accentColor, 52) : brighten(accentColor, 20), 195));
		int tx = x + (w - font.getWidth(label)) / 2;
		int ty = y + (h - 8) / 2;
		ctx.drawTextWithShadow(font, label, tx, ty, TEXT_PRIMARY);
	}

	public static void drawSubtleButton(DrawContext ctx, TextRenderer font, int x, int y, int w, int h, String label, boolean active, boolean hovered) {
		float r = 4.5F;
		int top = active ? brighten(CRIMSON_DARK, 18) : (hovered ? brighten(BG_CARD_HOVER, 10) : BG_CARD);
		int bot = active ? CRIMSON_DARK : (hovered ? BG_CARD_HOVER : darken(BG_CARD, 8));
		int border = active ? withAlpha(CRIMSON_BRIGHT, 195) : (hovered ? BORDER_HOVER : BORDER_SUBTLE);
		drawRoundedGradient(ctx, x, y, w, h, r, top, bot);
		drawRoundedOutline(ctx, x, y, w, h, r, 0.55F, border);
		int tx = x + (w - font.getWidth(label)) / 2;
		int ty = y + (h - 8) / 2;
		ctx.drawTextWithShadow(font, label, tx, ty, active ? TEXT_PRIMARY : (hovered ? TEXT_PRIMARY : TEXT_SECONDARY));
	}

	public static void drawSlider(DrawContext ctx, TextRenderer font, int x, int y, int w, String label, String badge, int value, int min, int max, boolean active) {
		ctx.drawTextWithShadow(font, label, x, y, TEXT_PRIMARY);
		int bw = font.getWidth(badge) + 10;
		drawBadge(ctx, font, x + w - bw, y - 1, badge,
				active ? withAlpha(CRIMSON, 75) : BG_INPUT,
				active ? CRIMSON_BRIGHT : BORDER_SUBTLE,
				active ? GOLD : TEXT_SECONDARY);

		float trackY = y + 13.5F;
		float trackH = 5.0F;
		float tr = 2.5F;
		drawRoundedRect(ctx, x, trackY, w, trackH, tr, BG_INPUT);
		drawRoundedOutline(ctx, x, trackY, w, trackH, tr, 0.5F, BORDER_SUBTLE);

		float ratio = (max <= min) ? 1.0F : Math.max(0.0F, Math.min(1.0F, (float) (value - min) / (float) (max - min)));
		float fillW = Math.max(0.0F, w * ratio);
		if (fillW > 1.5F) {
			drawRoundedGradient(ctx, x, trackY, fillW, trackH, Math.min(tr, fillW * 0.5F), CRIMSON_BRIGHT, CRIMSON);
		}

		float knobCx = x + Math.max(4.0F, Math.min(w - 4.0F, w * ratio));
		float knobCy = trackY + trackH * 0.5F;
		if (active) {
			drawCircle(ctx, knobCx, knobCy, 6.0F, withAlpha(CRIMSON_BRIGHT, 65));
		}
		drawCircle(ctx, knobCx, knobCy, 4.2F, active ? 0xFFFFFFFF : 0xFFF2ECF0);
		drawCircle(ctx, knobCx, knobCy, 2.0F, CRIMSON_BRIGHT);
	}

	public static int drawBadge(DrawContext ctx, TextRenderer font, int x, int y, String text, int bg, int border, int textColor) {
		int w = font.getWidth(text) + 10;
		int h = 11;
		float r = 4.5F;
		drawRoundedRect(ctx, x, y, w, h, r, bg);
		drawRoundedOutline(ctx, x, y, w, h, r, 0.5F, withAlpha(border, 185));
		ctx.drawTextWithShadow(font, text, x + 5, y + 2, textColor);
		return w;
	}

	// ==================== LOW-LEVEL OPENGL ROUNDED VECTOR PRIMITIVES ====================

	public static void drawRoundedRect(DrawContext ctx, float x, float y, float w, float h, float radius, int color) {
		drawRoundedGradient(ctx, x, y, w, h, radius, color, color);
	}

	public static void drawRoundedGradient(DrawContext ctx, float x, float y, float w, float h, float radius, int topColor, int botColor) {
		if (w <= 0.2F || h <= 0.2F) return;
		if (((topColor >>> 24) & 0xFF) == 0 && ((botColor >>> 24) & 0xFF) == 0) return;

		float r = Math.max(0.0F, Math.min(radius, Math.min(w, h) * 0.5F));
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);

		float cx = x + w * 0.5F;
		float cy = y + h * 0.5F;
		buf.vertex(mat, cx, cy, 0.0F).color(lerpColor(topColor, botColor, 0.5F));

		appendRoundedPerimeter(buf, mat, x, y, w, h, r, topColor, botColor);

		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}
		restoreGuiRenderState();
	}

	public static void drawRoundedOutline(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
		int alpha = (color >>> 24) & 0xFF;
		if (w <= 0.5F || h <= 0.5F || alpha == 0) return;

		float rOut = Math.max(0.0F, Math.min(radius, Math.min(w, h) * 0.5F));
		float rIn = Math.max(0.0F, rOut - thickness);
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

		int arcSteps = 5;
		float[][] outCenters = {
				{x + w - rOut, y + rOut},
				{x + w - rOut, y + h - rOut},
				{x + rOut, y + h - rOut},
				{x + rOut, y + rOut}
		};
		float[][] inCenters = {
				{x + w - thickness - rIn, y + thickness + rIn},
				{x + w - thickness - rIn, y + h - thickness - rIn},
				{x + thickness + rIn, y + h - thickness - rIn},
				{x + thickness + rIn, y + thickness + rIn}
		};
		double[] startAngles = {-Math.PI * 0.5D, 0.0D, Math.PI * 0.5D, Math.PI};

		float firstOx = 0.0F, firstOy = 0.0F, firstIx = 0.0F, firstIy = 0.0F;
		boolean firstSet = false;

		for (int q = 0; q < 4; q++) {
			for (int i = 0; i <= arcSteps; i++) {
				double ang = startAngles[q] + (i * (Math.PI * 0.5D) / arcSteps);
				float cos = (float) Math.cos(ang);
				float sin = (float) Math.sin(ang);
				float ox = outCenters[q][0] + cos * rOut;
				float oy = outCenters[q][1] + sin * rOut;
				float ix = inCenters[q][0] + cos * rIn;
				float iy = inCenters[q][1] + sin * rIn;
				if (!firstSet) {
					firstOx = ox;
					firstOy = oy;
					firstIx = ix;
					firstIy = iy;
					firstSet = true;
				}
				buf.vertex(mat, ox, oy, 0.0F).color(color);
				buf.vertex(mat, ix, iy, 0.0F).color(color);
			}
		}
		if (firstSet) {
			buf.vertex(mat, firstOx, firstOy, 0.0F).color(color);
			buf.vertex(mat, firstIx, firstIy, 0.0F).color(color);
		}

		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}
		restoreGuiRenderState();
	}

	public static void drawCircle(DrawContext ctx, float cx, float cy, float radius, int color) {
		int alpha = (color >>> 24) & 0xFF;
		if (radius <= 0.2F || alpha == 0) return;

		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
		int segs = 24;
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		buf.vertex(mat, cx, cy, 0.0F).color(color);
		for (int i = 0; i <= segs; i++) {
			double a = (i * Math.PI * 2.0D) / segs;
			buf.vertex(mat, cx + (float) (Math.cos(a) * radius), cy + (float) (Math.sin(a) * radius), 0.0F).color(color);
		}
		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}
		restoreGuiRenderState();
	}

	private static void appendRoundedPerimeter(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h,
											   float r, int topColor, int botColor) {
		int arcSteps = 5;
		float[][] centers = {
				{x + w - r, y + r},
				{x + w - r, y + h - r},
				{x + r, y + h - r},
				{x + r, y + r}
		};
		double[] startAngles = {-Math.PI * 0.5D, 0.0D, Math.PI * 0.5D, Math.PI};

		float firstPx = 0.0F, firstPy = 0.0F;
		int firstCol = topColor;
		boolean firstSet = false;

		for (int q = 0; q < 4; q++) {
			for (int i = 0; i <= arcSteps; i++) {
				double ang = startAngles[q] + (i * (Math.PI * 0.5D) / arcSteps);
				float px = centers[q][0] + (float) (Math.cos(ang) * r);
				float py = centers[q][1] + (float) (Math.sin(ang) * r);
				float t = Math.max(0.0F, Math.min(1.0F, (py - y) / Math.max(1.0F, h)));
				int vCol = (topColor == botColor) ? topColor : lerpColor(topColor, botColor, t);
				if (!firstSet) {
					firstPx = px;
					firstPy = py;
					firstCol = vCol;
					firstSet = true;
				}
				buf.vertex(mat, px, py, 0.0F).color(vCol);
			}
		}
		if (firstSet) {
			buf.vertex(mat, firstPx, firstPy, 0.0F).color(firstCol);
		}
	}

	private static void restoreGuiRenderState() {
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	public static int withAlpha(int rgbOrArgb, int alpha) {
		int clamped = Math.max(0, Math.min(255, alpha));
		return (clamped << 24) | (rgbOrArgb & 0x00FFFFFF);
	}

	public static int lerpColor(int c1, int c2, float t) {
		float f = Math.max(0.0F, Math.min(1.0F, t));
		int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
		int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;
		int a = Math.round(a1 + (a2 - a1) * f);
		int r = Math.round(r1 + (r2 - r1) * f);
		int g = Math.round(g1 + (g2 - g1) * f);
		int b = Math.round(b1 + (b2 - b1) * f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static int brighten(int argb, int delta) {
		int a = (argb >>> 24) & 0xFF;
		int r = Math.min(255, ((argb >>> 16) & 0xFF) + delta);
		int g = Math.min(255, ((argb >>> 8) & 0xFF) + delta);
		int b = Math.min(255, (argb & 0xFF) + delta);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static int darken(int argb, int delta) {
		int a = (argb >>> 24) & 0xFF;
		int r = Math.max(0, ((argb >>> 16) & 0xFF) - delta);
		int g = Math.max(0, ((argb >>> 8) & 0xFF) - delta);
		int b = Math.max(0, (argb & 0xFF) - delta);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
