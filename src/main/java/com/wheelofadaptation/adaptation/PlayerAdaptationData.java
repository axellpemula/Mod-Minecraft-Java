package com.wheelofadaptation.adaptation;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Data adaptasi satu player (Fase 1: in-memory).
 *
 * Bedrock: Analysing -> Adapted -> Evolved (II).
 * Fase 1 hanya Analysing -> Adapted. Evolved + lock + persistensi NBT di Fase 2.
 */
public class PlayerAdaptationData {

	/** Berapa kali kena hit tipe ini (progress analysing). */
	private final Map<AdaptationType, Integer> hits = new EnumMap<>(AdaptationType.class);

	/** Tipe yang sudah Adapted (damage di-nullify). */
	private final Set<AdaptationType> adapted = EnumSet.noneOf(AdaptationType.class);

	/** Hit yang dibutuhkan untuk Adapted. Bedrock pakai tier per-fenomena; Fase 1 pakai 3. */
	public static final int HITS_TO_ADAPT = 3;

	public boolean isAdapted(AdaptationType type) {
		return adapted.contains(type);
	}

	public int getHits(AdaptationType type) {
		return hits.getOrDefault(type, 0);
	}

	/**
	 * Catat satu hit. Return true jika hit ini menyelesaikan adaptasi.
	 */
	public boolean recordHit(AdaptationType type) {
		if (adapted.contains(type)) {
			return false;
		}
		int next = getHits(type) + 1;
		hits.put(type, next);
		if (next >= HITS_TO_ADAPT) {
			adapted.add(type);
			return true;
		}
		return false;
	}

	public Set<AdaptationType> getAdapted() {
		return adapted;
	}

	public void reset() {
		hits.clear();
		adapted.clear();
	}
}
