package com.wheelofadaptation.adaptation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager data adaptasi semua player.
 *
 * Fase 1: in-memory (hilang saat restart). Persistensi ke NBT player di Fase 2.
 */
public class AdaptationManager {

	private static final Map<UUID, PlayerAdaptationData> DATA = new ConcurrentHashMap<>();

	public static PlayerAdaptationData getOrCreate(UUID playerId) {
		return DATA.computeIfAbsent(playerId, id -> new PlayerAdaptationData());
	}

	public static PlayerAdaptationData get(UUID playerId) {
		return DATA.get(playerId);
	}

	public static void reset(UUID playerId) {
		PlayerAdaptationData data = DATA.get(playerId);
		if (data != null) {
			data.reset();
		}
	}
}
