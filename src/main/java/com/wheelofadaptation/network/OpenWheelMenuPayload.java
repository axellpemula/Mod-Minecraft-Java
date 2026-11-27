package com.wheelofadaptation.network;

import com.wheelofadaptation.WheelOfAdaptationMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Payload client -> server untuk buka Wheel Menu via keybind.
 * (Mojang mappings: CustomPacketPayload + Type; Yarn menyebutnya CustomPayload.Id)
 */
public record OpenWheelMenuPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<OpenWheelMenuPayload> TYPE =
			new CustomPacketPayload.Type<>(WheelOfAdaptationMod.id("open_menu"));

	public static final StreamCodec<ByteBuf, OpenWheelMenuPayload> CODEC =
			StreamCodec.unit(new OpenWheelMenuPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
