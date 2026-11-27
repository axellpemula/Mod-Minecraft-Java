package com.wheelofadaptation.client;

import com.wheelofadaptation.adaptation.AdaptationManager;
import com.wheelofadaptation.adaptation.AdaptationType;
import com.wheelofadaptation.adaptation.PlayerAdaptationData;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * HUD Wheel of Adaptation (UI custom Java).
 *
 * Menampilkan di kiri atas:
 * - Emblem Wheel (texture dari Bedrock)
 * - Mode + jumlah Adapted/Evolved
 * - Progress bar Analysing (jika ada)
 *
 * Hanya tampil jika player equip Wheel di slot khusus.
 */
public class WheelHud {

	private static final ResourceLocation WHEEL_TEXTURE =
			ResourceLocation.fromNamespaceAndPath("wheel_of_adaptation", "textures/item/wheel_of_adaptation.png");

	public static void register() {
		HudRenderCallback.EVENT.register((context, tickDelta) -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null || client.options.hideGui) {
				return;
			}
			if (!AdaptationManager.hasWheel(client.player.getUUID())) {
				return;
			}
			PlayerAdaptationData data = AdaptationManager.get(client.player.getUUID());
			if (data == null) {
				return;
			}

			int x = 6;
			int y = 6;

			// Background panel
			context.fill(x - 2, y - 2, x + 150, y + 52, 0x80000000);

			// Emblem Wheel 32x32 (texture asli 64x64 dari Bedrock, di-scale ke 32px)
			context.blit(WHEEL_TEXTURE, x, y, 0, 0, 32, 32, 64, 64);

			// Teks
			int textX = x + 38;
			context.drawString(client.font, "Wheel of Adaptation", textX, y + 2, 0xFFAA00, false);
			context.drawString(client.font, "Mode: " + data.getMode().getDisplayName(), textX, y + 12, 0xFFFFFF, false);
			context.drawString(client.font, "Adapted: " + data.getAdapted().size()
					+ "  Evolved: " + data.getEvolved().size(), textX, y + 22, 0x55FF55, false);

			// Progress bar Analysing (tipe pertama yang sedang analysing)
			for (AdaptationType type : AdaptationType.values()) {
				if (data.getHits(type) > 0 && !data.isAdapted(type)) {
					int barX = x + 38;
					int barY = y + 36;
					int barW = 100;
					int barH = 6;
					// Background bar
					context.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
					// Fill bar
					float progress = (float) data.getHits(type) / PlayerAdaptationData.HITS_TO_ADAPT;
					int fillW = (int) (barW * progress);
					context.fill(barX, barY, barX + fillW, barY + barH, 0xFFFFAA00);
					context.drawString(client.font, "Scanning: " + type.getDisplayName(),
							barX, barY + barH + 2, 0xFFFFAA, false);
					break;
				}
			}

			// Watermark credit (sesuai HUD Bedrock: "by xVoid_Axell")
			context.drawString(client.font, "Java port of the addon by xVoid_Axell",
					x - 2, y + 54, 0x777777, false);
		});
	}
}
