package com.wheelofadaptation.adaptation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wheelofadaptation.WheelOfAdaptationMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager data adaptasi semua player + persistensi JSON.
 *
 * File: <world>/wheel_of_adaptation/playerdata/<uuid>.json
 * Load saat player join, save saat leave + tiap 100 tick.
 */
public class AdaptationManager {

	private static final Map<UUID, PlayerAdaptationData> DATA = new ConcurrentHashMap<>();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static PlayerAdaptationData getOrCreate(UUID playerId) {
		return DATA.computeIfAbsent(playerId, id -> new PlayerAdaptationData());
	}

	public static PlayerAdaptationData get(UUID playerId) {
		return DATA.get(playerId);
	}

	public static boolean hasWheel(UUID playerId) {
		PlayerAdaptationData data = DATA.get(playerId);
		return data != null && data.hasWheel();
	}

	public static void resetAdaptations(UUID playerId) {
		PlayerAdaptationData data = DATA.get(playerId);
		if (data != null) {
			data.resetAdaptations();
		}
	}

	// --- Persistence ---

	private static Path playerFile(MinecraftServer server, UUID playerId) {
		return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
				.resolve("wheel_of_adaptation/playerdata/" + playerId + ".json");
	}

	public static void loadPlayer(MinecraftServer server, UUID playerId) {
		PlayerAdaptationData data = getOrCreate(playerId);
		Path file = playerFile(server, playerId);
		if (!Files.exists(file)) {
			return;
		}
		try {
			String json = Files.readString(file);
			JsonObject obj = GSON.fromJson(json, JsonObject.class);

			Set<AdaptationType> adapted = EnumSet.noneOf(AdaptationType.class);
			Set<AdaptationType> evolved = EnumSet.noneOf(AdaptationType.class);
			Map<AdaptationType, Integer> hits = new EnumMap<>(AdaptationType.class);
			Map<AdaptationType, Integer> evolvedHits = new EnumMap<>(AdaptationType.class);

			if (obj.has("adapted")) {
				for (JsonElement e : obj.getAsJsonArray("adapted")) {
					try {
						adapted.add(AdaptationType.valueOf(e.getAsString()));
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
			if (obj.has("evolved")) {
				for (JsonElement e : obj.getAsJsonArray("evolved")) {
					try {
						evolved.add(AdaptationType.valueOf(e.getAsString()));
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
			if (obj.has("hits")) {
				JsonObject hitsObj = obj.getAsJsonObject("hits");
				for (Map.Entry<String, JsonElement> entry : hitsObj.entrySet()) {
					try {
						hits.put(AdaptationType.valueOf(entry.getKey()), entry.getValue().getAsInt());
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
			if (obj.has("evolvedHits")) {
				JsonObject hitsObj = obj.getAsJsonObject("evolvedHits");
				for (Map.Entry<String, JsonElement> entry : hitsObj.entrySet()) {
					try {
						evolvedHits.put(AdaptationType.valueOf(entry.getKey()), entry.getValue().getAsInt());
					} catch (IllegalArgumentException ignored) {
					}
				}
			}

			data.loadAdapted(adapted, evolved, hits, evolvedHits);

			if (obj.has("mode")) {
				try {
					data.setMode(WheelMode.valueOf(obj.get("mode").getAsString()));
				} catch (IllegalArgumentException ignored) {
				}
			}
			if (obj.has("hasWheel") && obj.get("hasWheel").getAsBoolean()) {
				// Wheel stack disimpan sebagai flag; item asli di-restore sebagai Wheel (count 1).
				try {
					data.setWheelStack(new ItemStack(
							net.minecraft.core.registries.BuiltInRegistries.ITEM
									.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
											WheelOfAdaptationMod.MOD_ID, "wheel_of_adaptation"))
					));
				} catch (Exception ignored) {
				}
			}
			if (obj.has("slashCutsBlocks")) {
				data.setSlashCutsBlocks(obj.get("slashCutsBlocks").getAsBoolean());
			}
			if (obj.has("keepOnDeath")) {
				data.setKeepOnDeath(obj.get("keepOnDeath").getAsBoolean());
			}
			if (obj.has("lowFx")) {
				data.setLowFx(obj.get("lowFx").getAsBoolean());
			}
		} catch (IOException | RuntimeException e) {
			WheelOfAdaptationMod.LOGGER.warn("Failed to load adaptation data for {}", playerId, e);
		}
	}

	public static void savePlayer(MinecraftServer server, UUID playerId) {
		PlayerAdaptationData data = DATA.get(playerId);
		if (data == null) {
			return;
		}
		JsonObject obj = new JsonObject();

		JsonArray adaptedArr = new JsonArray();
		for (AdaptationType type : data.getAdapted()) {
			adaptedArr.add(type.name());
		}
		obj.add("adapted", adaptedArr);

		JsonArray evolvedArr = new JsonArray();
		for (AdaptationType type : data.getEvolved()) {
			evolvedArr.add(type.name());
		}
		obj.add("evolved", evolvedArr);

		JsonObject hitsObj = new JsonObject();
		for (Map.Entry<AdaptationType, Integer> entry : data.getHitsMap().entrySet()) {
			hitsObj.addProperty(entry.getKey().name(), entry.getValue());
		}
		obj.add("hits", hitsObj);

		JsonObject evolvedHitsObj = new JsonObject();
		for (Map.Entry<AdaptationType, Integer> entry : data.getEvolvedHitsMap().entrySet()) {
			evolvedHitsObj.addProperty(entry.getKey().name(), entry.getValue());
		}
		obj.add("evolvedHits", evolvedHitsObj);

		obj.addProperty("mode", data.getMode().name());
		obj.addProperty("hasWheel", data.hasWheel());
		obj.addProperty("slashCutsBlocks", data.isSlashCutsBlocks());
		obj.addProperty("keepOnDeath", data.isKeepOnDeath());
		obj.addProperty("lowFx", data.isLowFx());

		Path file = playerFile(server, playerId);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(obj));
		} catch (IOException e) {
			WheelOfAdaptationMod.LOGGER.warn("Failed to save adaptation data for {}", playerId, e);
		}
	}

	public static void saveAll(MinecraftServer server) {
		for (UUID playerId : DATA.keySet()) {
			savePlayer(server, playerId);
		}
	}
}
