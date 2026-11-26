package com.wheelofadaptation.network;

import com.wheelofadaptation.WheelOfAdaptationMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Payload server -> client untuk camera shake (Fase 4).
 * Dikirim saat Adapted/Evolved/RBD/Seal (kecuali lowFx).
 */
public record ShakePayload(float intensity) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ShakePayload> TYPE =
			new CustomPacketPayload.Type<>(WheelOfAdaptationMod.id("shake"));

	public static final StreamCodec<ByteBuf, ShakePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, ShakePayload::intensity,
			ShakePayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
