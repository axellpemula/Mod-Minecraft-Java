package com.wheelofadaptation.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registrasi networking Wheel (Fase 2/4):
 * - C2S: open_menu (keybind R -> server buka menu)
 * - S2C: shake (camera shake saat Adapted/Evolved/RBD/Seal), shake_config (/woa shake)
 */
public class WheelNetworking {

	public static void register() {
		PayloadTypeRegistry.playC2S().register(OpenWheelMenuPayload.TYPE, OpenWheelMenuPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ShakePayload.TYPE, ShakePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ShakeConfigPayload.TYPE, ShakeConfigPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(OpenWheelMenuPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				com.wheelofadaptation.adaptation.WheelUseHandler.openWheelMenu(player);
			});
		});
	}

	/** Kirim camera shake ke satu player. */
	public static void shake(ServerPlayer player, float intensity) {
		ServerPlayNetworking.send(player, new ShakePayload(intensity));
	}

	/** Kirim on/off camera shake ke satu player. */
	public static void shakeConfig(ServerPlayer player, boolean enabled) {
		ServerPlayNetworking.send(player, new ShakeConfigPayload(enabled));
	}
}
