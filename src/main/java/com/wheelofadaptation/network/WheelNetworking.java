package com.wheelofadaptation.network;

import com.wheelofadaptation.adaptation.WheelUseHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registrasi networking Wheel (keybind -> server buka menu).
 */
public class WheelNetworking {

	public static void register() {
		PayloadTypeRegistry.playC2SPayloads().register(OpenWheelMenuPayload.ID, OpenWheelMenuPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(OpenWheelMenuPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				WheelUseHandler.openWheelMenu(player);
			});
		});
	}
}
