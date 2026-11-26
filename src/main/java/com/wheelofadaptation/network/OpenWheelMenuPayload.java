package com.wheelofadaptation.network;

import com.wheelofadaptation.WheelOfAdaptationMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPayload;

/**
 * Payload client -> server untuk buka Wheel Menu via keybind.
 */
public record OpenWheelMenuPayload() implements CustomPayload {

	public static final CustomPayload.Id<OpenWheelMenuPayload> ID =
			new CustomPayload.Id<>(WheelOfAdaptationMod.id("open_menu"));

	public static final StreamCodec<ByteBuf, OpenWheelMenuPayload> CODEC =
			StreamCodec.unit(new OpenWheelMenuPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> id() {
		return ID;
	}
}
