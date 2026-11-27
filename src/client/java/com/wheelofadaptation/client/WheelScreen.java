package com.wheelofadaptation.client;

import com.wheelofadaptation.adaptation.AdaptationManager;
import com.wheelofadaptation.adaptation.AdaptationType;
import com.wheelofadaptation.adaptation.PlayerAdaptationData;
import com.wheelofadaptation.screen.WheelScreenHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Wheel Menu Screen (UI custom Java, bukan form Bedrock).
 *
 * Menampilkan:
 * - Slot khusus Wheel di atas
 * - Mode aktif
 * - Daftar Adapted + Evolved
 * - Progress Analysing
 */
public class WheelScreen extends AbstractContainerScreen<WheelScreenHandler> {

	private static final ResourceLocation BACKGROUND =
			ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/generic_9x3.png");

	public WheelScreen(WheelScreenHandler handler, net.minecraft.world.entity.player.Inventory inventory, Component title) {
		super(handler, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 166;
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		int x = (this.width - this.imageWidth) / 2;
		int y = (this.height - this.imageHeight) / 2;
		context.blit(BACKGROUND, x, y, 0, 0, this.imageWidth, this.imageHeight);

		// Info adaptasi di sisi kanan slot
		Minecraft client = this.minecraft;
		if (client == null || client.player == null) {
			return;
		}
		PlayerAdaptationData data = AdaptationManager.get(client.player.getUUID());
		if (data == null) {
			return;
		}

		int textX = x + 104;
		int textY = y + 18;
		context.drawString(this.font, "Mode: " + data.getMode().getDisplayName(), textX, textY, 0xFFFFFF, false);
		textY += 12;
		context.drawString(this.font, "Adapted:", textX, textY, 0x55FFFF, false);
		textY += 10;

		if (data.getAdapted().isEmpty()) {
			context.drawString(this.font, "  (none)", textX, textY, 0xAAAAAA, false);
			textY += 10;
		} else {
			for (AdaptationType type : data.getAdapted()) {
				String label = (data.isEvolved(type) ? "II " : "") + type.getDisplayName();
				context.drawString(this.font, "  " + label, textX, textY, 0x55FF55, false);
				textY += 10;
			}
		}

		// Progress Analysing
		textY += 4;
		context.drawString(this.font, "Analysing:", textX, textY, 0xFFFF55, false);
		textY += 10;
		boolean anyAnalysing = false;
		for (AdaptationType type : AdaptationType.values()) {
			if (data.getHits(type) > 0 && !data.isAdapted(type)) {
				anyAnalysing = true;
				context.drawString(this.font, "  " + type.getDisplayName()
						+ " " + data.getHits(type) + "/" + PlayerAdaptationData.HITS_TO_ADAPT,
						textX, textY, 0xFFFFAA, false);
				textY += 10;
			}
		}
		if (!anyAnalysing) {
			context.drawString(this.font, "  (none)", textX, textY, 0xAAAAAA, false);
		}
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		this.renderTooltip(context, mouseX, mouseY);
	}
}
