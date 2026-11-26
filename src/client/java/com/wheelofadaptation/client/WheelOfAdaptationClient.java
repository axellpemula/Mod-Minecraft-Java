package com.wheelofadaptation.client;

import com.wheelofadaptation.adaptation.AdaptationManager;
import com.wheelofadaptation.network.OpenWheelMenuPayload;
import com.wheelofadaptation.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import org.lwjgl.glfw.GLFW;

public class WheelOfAdaptationClient implements ClientModInitializer {

	public static final KeyMapping OPEN_WHEEL_MENU = new KeyMapping(
			"key.wheel_of_adaptation.open_menu",
			GLFW.GLFW_KEY_R,
			"key.categories.wheel_of_adaptation"
	);

	@Override
	public void onInitializeClient() {
		// Keybind R untuk buka Wheel Menu
		KeyBindingHelper.registerKeyBinding(OPEN_WHEEL_MENU);

		// Screen untuk WheelScreenHandler
		MenuScreens.register(ModScreenHandlers.WHEEL_MENU, WheelScreen::new);

		// Particle custom (adapt_wheel dari RP Bedrock)
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
				.register(com.wheelofadaptation.particle.ModParticles.ADAPT_WHEEL,
						AdaptWheelParticle.factory());

		// Camera shake (Fase 4): receiver payload server + decay per tick
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.wheelofadaptation.network.ShakePayload.TYPE,
				(payload, context) -> context.client().execute(() -> WheelShakeState.trigger(payload.intensity()))
		);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.wheelofadaptation.network.ShakeConfigPayload.TYPE,
				(payload, context) -> context.client().execute(() -> WheelShakeState.setEnabled(payload.enabled()))
		);

		// Sync data adaptasi dari server -> update data client untuk HUD + Screen
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.wheelofadaptation.network.AdaptationSyncPayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					if (context.player() == null) {
						return;
					}
					var data = AdaptationManager.getOrCreate(context.player().getUUID());
					data.setWheelStack(payload.hasWheel()
							? new net.minecraft.world.item.ItemStack(com.wheelofadaptation.item.ModItems.WHEEL_OF_ADAPTATION)
							: net.minecraft.world.item.ItemStack.EMPTY);
					com.wheelofadaptation.adaptation.WheelMode[] modes = com.wheelofadaptation.adaptation.WheelMode.values();
					data.setMode(modes[Math.floorMod(payload.modeOrdinal(), modes.length)]);
					com.wheelofadaptation.adaptation.AdaptationType[] types = com.wheelofadaptation.adaptation.AdaptationType.values();

					java.util.EnumSet<com.wheelofadaptation.adaptation.AdaptationType> adapted =
							java.util.EnumSet.noneOf(com.wheelofadaptation.adaptation.AdaptationType.class);
					for (int ord : payload.adaptedOrdinals()) {
						if (ord >= 0 && ord < types.length) {
							adapted.add(types[ord]);
						}
					}
					java.util.EnumSet<com.wheelofadaptation.adaptation.AdaptationType> evolved =
							java.util.EnumSet.noneOf(com.wheelofadaptation.adaptation.AdaptationType.class);
					for (int ord : payload.evolvedOrdinals()) {
						if (ord >= 0 && ord < types.length) {
							evolved.add(types[ord]);
						}
					}
					java.util.EnumMap<com.wheelofadaptation.adaptation.AdaptationType, Integer> hits =
							new java.util.EnumMap<>(com.wheelofadaptation.adaptation.AdaptationType.class);
					int[] hitsArr = payload.hitsByOrdinal();
					for (int i = 0; i < types.length && i < hitsArr.length; i++) {
						if (hitsArr[i] > 0) {
							hits.put(types[i], hitsArr[i]);
						}
					}
					data.loadAdapted(adapted, evolved, hits, new java.util.EnumMap<>(com.wheelofadaptation.adaptation.AdaptationType.class));
				})
		);

		WheelShakeState.registerTick();

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
