package com.wheelofadaptation.network;

import com.wheelofadaptation.WheelOfAdaptationMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPayload;

/**
 * Payload server -> client: on/off camera shake (command /woa shake on|off).
 */
public record ShakeConfigPayload(boolean enabled) implements CustomPayload {

	public static final CustomPayload.Id<ShakeConfigPayload> ID =
			new CustomPayload.Id<>(WheelOfAdaptationMod.id("shake_config"));

	public static final StreamCodec<ByteBuf, ShakeConfigPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ShakeConfigPayload::enabled,
			ShakeConfigPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> id() {
		return ID;
	}
}
