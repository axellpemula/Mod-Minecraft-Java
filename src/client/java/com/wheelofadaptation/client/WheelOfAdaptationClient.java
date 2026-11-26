package com.wheelofadaptation.client;

import com.wheelofadaptation.network.OpenWheelMenuPayload;
import com.wheelofadaptation.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.MenuScreens;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class WheelOfAdaptationClient implements ClientModInitializer {

	public static final KeyMapping OPEN_WHEEL_MENU = new KeyMapping(
			"key.wheel_of_adaptation.open_menu",
			KeyMapping.Category.register("key.categories.wheel_of_adaptation"),
			GLFW.GLFW_KEY_R
	);

	@Override
	public void onInitializeClient() {
		// Keybind R untuk buka Wheel Menu
		KeyBindingHelper.registerKeyBinding(OPEN_WHEEL_MENU);

		// Screen untuk WheelScreenHandler
		MenuScreens.register(ModScreenHandlers.WHEEL_MENU, WheelScreen::new);

		// HUD
		WheelHud.register();

		// Poll keybind tiap tick client
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_WHEEL_MENU.consumeClick()) {
				if (client.player != null && client.screen == null) {
					ClientPlayNetworking.send(new OpenWheelMenuPayload());
				}
			}
		});
	}
}
