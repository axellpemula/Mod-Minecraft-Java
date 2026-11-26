package com.wheelofadaptation.adaptation;

import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Data adaptasi satu player.
 *
 * Bedrock: Analysing -> Adapted -> Evolved (II).
 * Fase 2: + Evolved, + Mode, + Wheel slot khusus, + Settings.
 * Persistensi JSON di AdaptationManager.
 */
public class PlayerAdaptationData {

	/** Berapa kali kena hit tipe ini (progress analysing). */
	private final Map<AdaptationType, Integer> hits = new EnumMap<>(AdaptationType.class);

	/** Tipe yang sudah Adapted (damage di-nullify). */
	private final Set<AdaptationType> adapted = EnumSet.noneOf(AdaptationType.class);

	/** Tipe yang sudah Evolved (II) — efek lebih kuat. */
	private final Set<AdaptationType> evolved = EnumSet.noneOf(AdaptationType.class);

	/** Hit tambahan setelah Adapted untuk jadi Evolved. */
	private final Map<AdaptationType, Integer> evolvedHits = new EnumMap<>(AdaptationType.class);

	/** Wheel yang ter-equip di slot khusus (bukan helm vanilla). */
	private ItemStack wheelStack = ItemStack.EMPTY;

	/** Mode aktif: Attack / Balance / Defense. */
	private WheelMode mode = WheelMode.BALANCE;

	// Settings (dari Adaptation Config Bedrock)
	private boolean slashCutsBlocks = true;
	private boolean keepOnDeath = false;
	private boolean lowFx = false;

	/** Hit yang dibutuhkan untuk Adapted. Bedrock pakai tier; Fase 2 pakai 3. */
	public static final int HITS_TO_ADAPT = 3;

	/** Hit tambahan untuk Evolved. */
	public static final int HITS_TO_EVOLVE = 5;

	public boolean hasWheel() {
		return !wheelStack.isEmpty();
	}

	public ItemStack getWheelStack() {
		return wheelStack;
	}

	public void setWheelStack(ItemStack stack) {
		this.wheelStack = stack;
	}

	public WheelMode getMode() {
		return mode;
	}

	public void setMode(WheelMode mode) {
		this.mode = mode;
	}

	public boolean isSlashCutsBlocks() {
		return slashCutsBlocks;
	}

	public void setSlashCutsBlocks(boolean value) {
		this.slashCutsBlocks = value;
	}

	public boolean isKeepOnDeath() {
		return keepOnDeath;
	}

	public void setKeepOnDeath(boolean value) {
		this.keepOnDeath = value;
	}

	public boolean isLowFx() {
		return lowFx;
	}

	public void setLowFx(boolean value) {
		this.lowFx = value;
	}

	public boolean isAdapted(AdaptationType type) {
		return adapted.contains(type);
	}

	public boolean isEvolved(AdaptationType type) {
		return evolved.contains(type);
	}

	public int getHits(AdaptationType type) {
		return hits.getOrDefault(type, 0);
	}

	public int getEvolvedHits(AdaptationType type) {
		return evolvedHits.getOrDefault(type, 0);
	}

	/**
	 * Catat satu hit saat Analysing. Return true jika hit ini menyelesaikan adaptasi.
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

	/**
	 * Catat satu hit saat sudah Adapted (untuk Evolved).
	 * Return true jika hit ini menyelesaikan Evolved.
	 */
	public boolean recordEvolvedHit(AdaptationType type) {
		if (evolved.contains(type)) {
			return false;
		}
		int next = getEvolvedHits(type) + 1;
		evolvedHits.put(type, next);
		if (next >= HITS_TO_EVOLVE) {
			evolved.add(type);
			return true;
		}
		return false;
	}

	public Set<AdaptationType> getAdapted() {
		return adapted;
	}

	public Set<AdaptationType> getEvolved() {
		return evolved;
	}

	public void resetAdaptations() {
		hits.clear();
		adapted.clear();
		evolved.clear();
		evolvedHits.clear();
	}

	// --- Serialization helpers (untuk JSON persistence) ---

	public void loadAdapted(Set<AdaptationType> adaptedSet, Set<AdaptationType> evolvedSet,
			Map<AdaptationType, Integer> hitsMap, Map<AdaptationType, Integer> evolvedHitsMap) {
		adapted.clear();
		adapted.addAll(adaptedSet);
		evolved.clear();
		evolved.addAll(evolvedSet);
		hits.clear();
		hits.putAll(hitsMap);
		evolvedHits.clear();
		evolvedHits.putAll(evolvedHitsMap);
	}

	public Map<AdaptationType, Integer> getHitsMap() {
		return hits;
	}

	public Map<AdaptationType, Integer> getEvolvedHitsMap() {
		return evolvedHits;
	}
}
