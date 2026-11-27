package com.wheelofadaptation.network;

import com.wheelofadaptation.WheelOfAdaptationMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Payload server -> client: ringkasan data adaptasi player (Fase 4).
 * Supaya HUD + WheelScreen di client menampilkan data yang benar.
 * Encoding manual via FriendlyByteBuf (API stabil), dipisah varInt array.
 */
public record AdaptationSyncPayload(
		boolean hasWheel,
		int modeOrdinal,
		int[] adaptedOrdinals,
		int[] evolvedOrdinals,
		int[] hitsByOrdinal // index = ordinal AdaptationType
) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<AdaptationSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(WheelOfAdaptationMod.id("adaptation_sync"));

	public static final StreamCodec<FriendlyByteBuf, AdaptationSyncPayload> CODEC = StreamCodec.of(
			AdaptationSyncPayload::write,
			AdaptationSyncPayload::read
	);

	private static void write(FriendlyByteBuf buf, AdaptationSyncPayload p) {
		buf.writeBoolean(p.hasWheel());
		buf.writeByte(p.modeOrdinal());
		buf.writeVarIntArray(p.adaptedOrdinals());
		buf.writeVarIntArray(p.evolvedOrdinals());
		buf.writeVarIntArray(p.hitsByOrdinal());
	}

	private static AdaptationSyncPayload read(FriendlyByteBuf buf) {
		return new AdaptationSyncPayload(
				buf.readBoolean(),
				buf.readByte(),
				buf.readVarIntArray(),
				buf.readVarIntArray(),
				buf.readVarIntArray()
		);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
