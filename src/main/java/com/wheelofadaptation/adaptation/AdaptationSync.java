package com.wheelofadaptation.adaptation;

import com.wheelofadaptation.network.AdaptationSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.stream.IntStream;

/**
 * Sync ringkasan data adaptasi server -> client (Fase 4).
 * Dipanggil saat join, setelah event adaptasi, ganti mode/equip wheel, dan tiap 100 tick.
 */
public final class AdaptationSync {

	private AdaptationSync() {
	}

	public static void syncTo(ServerPlayer player) {
		PlayerAdaptationData data = AdaptationManager.get(player.getUUID());
		if (data == null) {
			return;
		}

		int[] adapted = data.getAdapted().stream().mapToInt(AdaptationType::ordinal).toArray();
		int[] evolved = data.getEvolved().stream().mapToInt(AdaptationType::ordinal).toArray();
		int[] hits = IntStream.range(0, AdaptationType.values().length)
				.map(i -> data.getHits(AdaptationType.values()[i]))
				.toArray();

		ServerPlayNetworking.send(player, new AdaptationSyncPayload(
				data.hasWheel(),
				data.getMode().ordinal(),
				adapted,
				evolved,
				hits
		));
	}
}
