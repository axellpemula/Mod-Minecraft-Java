package com.wheelofadaptation.network;

import com.wheelofadaptation.WheelOfAdaptationMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Payload server -> client: on/off camera shake (command /woa shake on|off).
 */
public record ShakeConfigPayload(boolean enabled) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ShakeConfigPayload> TYPE =
			new CustomPacketPayload.Type<>(WheelOfAdaptationMod.id("shake_config"));

	public static final StreamCodec<ByteBuf, ShakeConfigPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ShakeConfigPayload::enabled,
			ShakeConfigPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
