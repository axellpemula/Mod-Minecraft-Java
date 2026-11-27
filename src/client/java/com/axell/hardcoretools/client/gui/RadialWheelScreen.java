package com.axell.hardcoretools.client.gui;

import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import com.axell.hardcoretools.HardcoreToolsMod;
import com.axell.hardcoretools.client.ClientStateSnapshot;
import com.axell.hardcoretools.client.HardcoreToolsClient;
import com.axell.hardcoretools.network.HardcoreNetworking;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class RadialWheelScreen extends Screen {
	private static final int SECTOR_COUNT = 8;
	private static final float DEADZONE_R = 26.0F;
	private static final float INNER_R = 36.0F;
	private static final float OUTER_R = 98.0F;

	private final long openMs = System.currentTimeMillis();
	private final float[] hoverAnim = new float[SECTOR_COUNT];
	private long lastFrameNs = System.nanoTime();
	private int hoveredSector = -1;
	private int lastSoundSector = -1;
	private boolean actionExecuted = false;

	public RadialWheelScreen() {
		super(Text.literal("Hardcore Quick Wheel"));
	}

	private static Identifier icon(String path) {
		return Identifier.of(HardcoreToolsMod.MOD_ID, path);
	}

	private record SliceInfo(String title, String status, String hint, Identifier icon, boolean activeState, int stateColor) {
	}

	private SliceInfo getSliceInfo(int idx, ClientStateSnapshot s) {
		return switch (idx) {
			case 0 -> new SliceInfo(
					"Immortal Mode",
					s.immortal ? "ACTIVE" : "OFF",
					"Zero damage, full hunger & void rescue",
					icon("textures/gui/icons/immortal.png"),
					s.immortal,
					CrimsonTheme.EMERALD
			);
			case 1 -> new SliceInfo(
					"Return by Death",
					s.returnByDeath
							? (s.returnByDeathCooldownLeft > 0 ? ("CD " + s.returnByDeathCooldownLeft + "s") : ("ON -" + s.returnByDeathSeconds + "s"))
							: "OFF",
					"Rewinds 60s of world, blocks & fate on death",
					icon("textures/gui/icons/rbd.png"),
					s.returnByDeath,
					CrimsonTheme.AMETHYST
			);
			case 2 -> new SliceInfo(
					"Auto-Totem",
					s.autoTotem ? ("ON · " + s.totemCount) : ("OFF · " + s.totemCount),
					"Auto-pops Totems from any inventory slot",
					icon("textures/gui/icons/totem.png"),
					s.autoTotem,
					CrimsonTheme.GOLD
			);
			case 3 -> new SliceInfo(
					"Fullbright",
					s.fullbright ? "ACTIVE" : "OFF",
					"100% pure cave & Nether daylight",
					icon("textures/gui/icons/fullbright.png"),
					s.fullbright,
					CrimsonTheme.GOLD
			);
			case 4 -> new SliceInfo(
					"Open Dashboard",
					"14 TABS",
					"Open full Hardcore Tools control center",
					icon("textures/gui/emblem.png"),
					false,
					CrimsonTheme.CRIMSON_BRIGHT
			);
			case 5 -> new SliceInfo(
					"Heal & Feed",
					"INSTANT",
					"Restore 100% HP, saturation & clear debuffs",
					icon("textures/gui/icons/heal.png"),
					false,
					0xFFFF4D79
			);
			case 6 -> new SliceInfo(
					"Repair Held",
					s.heldDamageable && s.heldDamage > 0 ? "DAMAGED" : "READY",
					"Restore held tool/weapon to 100% durability",
					icon("textures/gui/icons/repair.png"),
					false,
					CrimsonTheme.CYAN
			);
			default -> {
				boolean bothOn = s.veinMiner && s.treeCapitator;
				boolean anyOn = s.veinMiner || s.treeCapitator;
				yield new SliceInfo(
						"Vein + Tree",
						bothOn ? "BOTH ON" : (anyOn ? "PARTIAL" : "OFF"),
						"Toggle Vein Miner & Tree Capitator together",
						icon("textures/gui/icons/veinminer.png"),
						anyOn,
						CrimsonTheme.CYAN
				);
			}
		};
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		if (this.client != null && this.client.world != null) {
			try {
				this.applyBlur(delta);
			} catch (Exception ignored) {
			}
		}
		context.fillGradient(0, 0, this.width, this.height, 0x7A06040A, 0x9908050D);
	}

	@Override
	public void tick() {
		if (this.actionExecuted || this.client == null) {
			return;
		}
		// Release-to-Trigger when player lets go of the Radial Wheel keybind (after 110ms tap grace)
		if (System.currentTimeMillis() - this.openMs >= 110L && !isRadialKeyStillHeld(this.client)) {
			triggerAndClose(this.hoveredSector);
		}
	}

	private static boolean isRadialKeyStillHeld(MinecraftClient client) {
		if (HardcoreToolsClient.getRadialWheelKey() == null || client.getWindow() == null) {
			return false;
		}
		InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(HardcoreToolsClient.getRadialWheelKey());
		long handle = client.getWindow().getHandle();
		if (bound.getCategory() == InputUtil.Type.KEYSYM) {
			if (bound.getCode() == InputUtil.UNKNOWN_KEY.getCode()) return false;
			return InputUtil.isKeyPressed(handle, bound.getCode());
		} else if (bound.getCategory() == InputUtil.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(handle, bound.getCode()) == GLFW.GLFW_PRESS;
		}
		return false;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);

		// Check real-time key release at frame rate for ultra-snappy responsiveness!
		if (!this.actionExecuted && this.client != null && System.currentTimeMillis() - this.openMs >= 110L) {
			if (!isRadialKeyStillHeld(this.client)) {
				triggerAndClose(this.hoveredSector);
				return;
			}
		}

		ClientStateSnapshot s = ClientStateSnapshot.INSTANCE;
		long nowNs = System.nanoTime();
		float dt = Math.min(0.05F, Math.max(0.0F, (nowNs - this.lastFrameNs) / 1_000_000_000.0F));
		this.lastFrameNs = nowNs;

		// Smooth pop-in scale animation (140ms)
		float popT = Math.min(1.0F, (System.currentTimeMillis() - this.openMs) / 140.0F);
		float popEase = 1.0F - (float) Math.pow(1.0F - popT, 3.0D);
		float scale = 0.72F + 0.28F * popEase;

		float cx = this.width * 0.5F;
		float cy = this.height * 0.5F;
		float dx = mouseX - cx;
		float dy = mouseY - cy;
		float dist = (float) Math.hypot(dx, dy);

		if (dist < DEADZONE_R * scale) {
			this.hoveredSector = -1;
		} else {
			// Angle 0 at top (12 o'clock), clockwise 0..2*PI across 8 sectors
			double ang = Math.atan2(dy, dx) + Math.PI * 0.5D;
			if (ang < 0.0D) ang += Math.PI * 2.0D;
			double shifted = (ang + (Math.PI / SECTOR_COUNT)) % (Math.PI * 2.0D);
			this.hoveredSector = ((int) Math.floor(shifted / (Math.PI * 2.0D / SECTOR_COUNT))) % SECTOR_COUNT;
		}

		if (this.hoveredSector != this.lastSoundSector) {
			this.lastSoundSector = this.hoveredSector;
			if (this.hoveredSector >= 0 && this.client != null) {
				this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.45F));
			}
		}

		for (int i = 0; i < SECTOR_COUNT; i++) {
			float target = (i == this.hoveredSector) ? 1.0F : 0.0F;
			this.hoverAnim[i] += (target - this.hoverAnim[i]) * (1.0F - (float) Math.exp(-dt * 20.0F));
		}

		// Draw Radial Wheel Sectors & Ambient Glow in OpenGL
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);

		Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();

		// Soft outer ambient ring
		drawRingSector(mat, cx, cy, (INNER_R - 4.0F) * scale, (OUTER_R + 8.0F) * scale,
				0.0D, Math.PI * 2.0D, 64, 0x38000000, 0x00000000);

		double sectorSpan = (Math.PI * 2.0D) / SECTOR_COUNT;
		double gapRad = Math.toRadians(3.2D);

		for (int i = 0; i < SECTOR_COUNT; i++) {
			SliceInfo info = getSliceInfo(i, s);
			float hAnim = this.hoverAnim[i];
			double centerAng = -Math.PI * 0.5D + i * sectorSpan;
			double a0 = centerAng - sectorSpan * 0.5D + gapRad * 0.5D;
			double a1 = centerAng + sectorSpan * 0.5D - gapRad * 0.5D;

			float rIn = (INNER_R + hAnim * 2.0F) * scale;
			float rOut = (OUTER_R + hAnim * 9.5F) * scale;

			int baseIn = CrimsonTheme.withAlpha(CrimsonTheme.BG_CARD, 224);
			int baseOut = CrimsonTheme.withAlpha(CrimsonTheme.BG_WINDOW, 236);
			int hovIn = CrimsonTheme.withAlpha(CrimsonTheme.CRIMSON_DARK, 238);
			int hovOut = CrimsonTheme.withAlpha(CrimsonTheme.CRIMSON, 228);

			int colIn = CrimsonTheme.lerpColor(baseIn, hovIn, hAnim);
			int colOut = CrimsonTheme.lerpColor(baseOut, hovOut, hAnim);

			drawRingSector(mat, cx, cy, rIn, rOut, a0, a1, 18, colIn, colOut);

			// Outer arc accent rim (lights up when active or hovered)
			int rimCol = info.activeState()
					? CrimsonTheme.lerpColor(info.stateColor(), 0xFFFFFFFF, hAnim * 0.35F)
					: CrimsonTheme.lerpColor(CrimsonTheme.BORDER_SUBTLE, CrimsonTheme.CRIMSON_BRIGHT, hAnim);
			drawRingSector(mat, cx, cy, rOut - 2.2F * scale, rOut, a0, a1, 18, rimCol, rimCol);

			// Inner arc subtle trim
			int inRim = CrimsonTheme.lerpColor(CrimsonTheme.BORDER_SUBTLE, CrimsonTheme.CRIMSON_BRIGHT, hAnim);
			drawRingSector(mat, cx, cy, rIn, rIn + 1.2F * scale, a0, a1, 18, inRim, inRim);
		}

		// Central Hub Circle + Glowing Ring
		float hubR = (DEADZONE_R + 4.0F) * scale;
		drawSolidCircle(mat, cx, cy, hubR + 2.0F, CrimsonTheme.withAlpha(CrimsonTheme.BORDER_OUTER, 180));
		drawSolidCircle(mat, cx, cy, hubR, CrimsonTheme.withAlpha(CrimsonTheme.BG_WINDOW, 242));

		// Directional pointer dot toward mouse if outside deadzone
		if (this.hoveredSector >= 0) {
			double ptrAng = Math.atan2(dy, dx);
			float px = cx + (float) Math.cos(ptrAng) * (hubR - 4.5F);
			float py = cy + (float) Math.sin(ptrAng) * (hubR - 4.5F);
			RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
			drawSolidCircle(mat, px, py, 4.2F, CrimsonTheme.withAlpha(CrimsonTheme.CRIMSON_BRIGHT, 160));
			RenderSystem.defaultBlendFunc();
			drawSolidCircle(mat, px, py, 2.2F, 0xFFFFFFFF);
		}

		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		// Draw Crisp 64x64 HD Icons & Status Labels on Each Sector
		for (int i = 0; i < SECTOR_COUNT; i++) {
			SliceInfo info = getSliceInfo(i, s);
			float hAnim = this.hoverAnim[i];
			double centerAng = -Math.PI * 0.5D + i * sectorSpan;
			float midR = ((INNER_R + OUTER_R) * 0.5F + hAnim * 5.0F) * scale;
			float ix = cx + (float) Math.cos(centerAng) * midR;
			float iy = cy + (float) Math.sin(centerAng) * midR;

			int iconSize = Math.round((18.0F + hAnim * 3.0F) * scale);
			ctx.drawTexture(info.icon(), Math.round(ix - iconSize * 0.5F), Math.round(iy - iconSize * 0.5F - 4.0F),
					iconSize, iconSize, 0.0F, 0.0F, 64, 64, 64, 64);

			// Short status tag underneath icon
			ctx.getMatrices().push();
			ctx.getMatrices().translate(ix, iy + iconSize * 0.5F - 1.0F, 0.0F);
			ctx.getMatrices().scale(0.75F, 0.75F, 1.0F);
			int sw = this.textRenderer.getWidth(info.status());
			int stCol = info.activeState() ? info.stateColor() : (hAnim > 0.4F ? CrimsonTheme.TEXT_PRIMARY : CrimsonTheme.TEXT_SECONDARY);
			ctx.drawTextWithShadow(this.textRenderer, info.status(), -sw / 2, 0, stCol);
			ctx.getMatrices().pop();
		}

		// Central Hub Label + Bottom Hint Card
		if (this.hoveredSector >= 0) {
			SliceInfo sel = getSliceInfo(this.hoveredSector, s);
			drawCentered(ctx, "⚡", (int) cx, (int) cy - 11, CrimsonTheme.CRIMSON_BRIGHT);
			ctx.getMatrices().push();
			ctx.getMatrices().translate(cx, cy + 2.0F, 0.0F);
			ctx.getMatrices().scale(0.78F, 0.78F, 1.0F);
			String shortTitle = sel.title();
			ctx.drawTextWithShadow(this.textRenderer, shortTitle, -this.textRenderer.getWidth(shortTitle) / 2, 0, CrimsonTheme.TEXT_PRIMARY);
			ctx.getMatrices().pop();

			// Sleek rounded info pill below the wheel
			int cardW = Math.max(176, this.textRenderer.getWidth(sel.hint()) + 24);
			int cardX = Math.round(cx - cardW * 0.5F);
			int cardY = Math.min(this.height - 34, Math.round(cy + (OUTER_R + 18.0F) * scale));
			CrimsonTheme.drawCard(ctx, cardX, cardY, cardW, 26, true, sel.activeState() ? sel.stateColor() : CrimsonTheme.CRIMSON_BRIGHT);
			drawCentered(ctx, sel.title() + "  ·  " + sel.status(), (int) cx, cardY + 4, CrimsonTheme.TEXT_PRIMARY);
			drawCentered(ctx, sel.hint(), (int) cx, cardY + 14, CrimsonTheme.TEXT_SECONDARY);
		} else {
			ctx.getMatrices().push();
			ctx.getMatrices().translate(cx, cy - 6.0F, 0.0F);
			ctx.getMatrices().scale(0.80F, 0.80F, 1.0F);
			String t1 = "QUICK";
			String t2 = "WHEEL";
			ctx.drawTextWithShadow(this.textRenderer, t1, -this.textRenderer.getWidth(t1) / 2, 0, CrimsonTheme.CRIMSON_BRIGHT);
			ctx.drawTextWithShadow(this.textRenderer, t2, -this.textRenderer.getWidth(t2) / 2, 10, CrimsonTheme.TEXT_SECONDARY);
			ctx.getMatrices().pop();

			int hintY = Math.min(this.height - 24, Math.round(cy + (OUTER_R + 18.0F) * scale));
			String keyName = HardcoreToolsClient.getKeyLabel(HardcoreToolsClient.radialWheelKey, "R");
			drawCentered(ctx, "Move cursor to select  ·  Release [" + keyName + "] or Click to trigger", (int) cx, hintY, CrimsonTheme.TEXT_MUTED);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			triggerAndClose(this.hoveredSector);
			return true;
		} else if (button == 1) {
			this.actionExecuted = true;
			this.close();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (HardcoreToolsClient.getRadialWheelKey() != null
				&& HardcoreToolsClient.getRadialWheelKey().matchesMouse(button)) {
			triggerAndClose(this.hoveredSector);
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (HardcoreToolsClient.getRadialWheelKey() != null
				&& HardcoreToolsClient.getRadialWheelKey().matchesKey(keyCode, scanCode)) {
			triggerAndClose(this.hoveredSector);
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	private void triggerAndClose(int sector) {
		if (this.actionExecuted) return;
		this.actionExecuted = true;

		MinecraftClient mc = MinecraftClient.getInstance();
		if (sector < 0) {
			mc.setScreen(null);
			return;
		}

		mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.15F));
		ClientStateSnapshot s = ClientStateSnapshot.INSTANCE;

		switch (sector) {
			case 0 -> {
				sendAction("toggle_immortal", new JsonObject());
				mc.setScreen(null);
			}
			case 1 -> {
				sendAction("toggle_rbd", new JsonObject());
				mc.setScreen(null);
			}
			case 2 -> {
				sendAction("toggle_autototem", new JsonObject());
				mc.setScreen(null);
			}
			case 3 -> {
				boolean next = !s.fullbright;
				s.fullbright = next;
				JsonObject o = new JsonObject();
				o.addProperty("value", next);
				sendAction("toggle_fullbright", o);
				if (mc.inGameHud != null) {
					mc.inGameHud.setOverlayMessage(
							Text.literal("☀ Fullbright: ").formatted(Formatting.GOLD)
									.append(Text.literal(next ? "ON" : "OFF").formatted(next ? Formatting.GREEN : Formatting.RED)),
							false
					);
				}
				mc.setScreen(null);
			}
			case 4 -> {
				if (ClientPlayNetworking.canSend(HardcoreNetworking.ActionC2SPayload.ID)) {
					mc.setScreen(null);
					ClientPlayNetworking.send(new HardcoreNetworking.ActionC2SPayload("request_open", "{}"));
				} else {
					mc.setScreen(new HardcoreDashboardScreen());
				}
			}
			case 5 -> {
				JsonObject o = new JsonObject();
				o.addProperty("cmd", "heal_feed");
				sendAction("console_quick", o);
				mc.setScreen(null);
			}
			case 6 -> {
				JsonObject o = new JsonObject();
				o.addProperty("mode", "held");
				sendAction("repair", o);
				mc.setScreen(null);
			}
			case 7 -> {
				sendAction("radial_toggle_harvest", new JsonObject());
				mc.setScreen(null);
			}
		}
	}

	private static void sendAction(String action, JsonObject obj) {
		if (ClientPlayNetworking.canSend(HardcoreNetworking.ActionC2SPayload.ID)) {
			ClientPlayNetworking.send(new HardcoreNetworking.ActionC2SPayload(action, obj.toString()));
		}
	}

	private void drawCentered(DrawContext ctx, String text, int cx, int y, int color) {
		ctx.drawTextWithShadow(this.textRenderer, text, cx - this.textRenderer.getWidth(text) / 2, y, color);
	}

	private static void drawRingSector(Matrix4f mat, float cx, float cy, float rIn, float rOut,
									   double a0, double a1, int steps, int colIn, int colOut) {
		if (rOut <= rIn) return;
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i <= steps; i++) {
			double a = a0 + (a1 - a0) * (i / (double) steps);
			float cos = (float) Math.cos(a);
			float sin = (float) Math.sin(a);
			buf.vertex(mat, cx + cos * rIn, cy + sin * rIn, 0.0F).color(colIn);
			buf.vertex(mat, cx + cos * rOut, cy + sin * rOut, 0.0F).color(colOut);
		}
		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}
	}

	private static void drawSolidCircle(Matrix4f mat, float cx, float cy, float r, int color) {
		if (r <= 0.2F || ((color >>> 24) & 0xFF) == 0) return;
		int segs = 36;
		BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		buf.vertex(mat, cx, cy, 0.0F).color(color);
		for (int i = 0; i <= segs; i++) {
			double a = (i * Math.PI * 2.0D) / segs;
			buf.vertex(mat, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 0.0F).color(color);
		}
		BuiltBuffer built = buf.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}
	}
}
