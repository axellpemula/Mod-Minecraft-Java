package com.axell.hardcoretools.state;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.axell.hardcoretools.HardcoreToolsMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

public final class HardcoreStateManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final List<String> ALL_ORE_KEYS = List.of(
			"coal", "copper", "iron", "gold", "redstone", "lapis",
			"diamond", "emerald", "quartz", "nether_gold", "ancient_debris", "amethyst"
	);

	public static class WorldData {
		public boolean allowAllPlayers = true;
		public boolean orePlusEnabled = false;
		public int orePlusMultiplier = 2;
		public Map<String, Boolean> oreToggles = new LinkedHashMap<>();
		public Map<String, PlayerProfile> players = new LinkedHashMap<>();

		public void sanitize() {
			if (orePlusMultiplier < 2) orePlusMultiplier = 2;
			if (orePlusMultiplier > 4) orePlusMultiplier = 4;
			if (oreToggles == null) oreToggles = new LinkedHashMap<>();
			for (String key : ALL_ORE_KEYS) {
				oreToggles.putIfAbsent(key, true);
			}
			if (players == null) players = new LinkedHashMap<>();
			for (PlayerProfile profile : players.values()) {
				if (profile != null) {
					profile.sanitize();
				}
			}
		}
	}

	private static MinecraftServer activeServer;
	private static WorldData activeData;

	private HardcoreStateManager() {
	}

	public static synchronized WorldData get(MinecraftServer server) {
		if (activeServer != server || activeData == null) {
			activeServer = server;
			activeData = load(server);
		}
		return activeData;
	}

	public static synchronized PlayerProfile getProfile(ServerPlayerEntity player) {
		WorldData data = get(player.getServer());
		String uuid = player.getUuidAsString();
		PlayerProfile profile = data.players.computeIfAbsent(uuid, k -> {
			PlayerProfile created = new PlayerProfile();
			created.sanitize();
			return created;
		});
		profile.sanitize();
		return profile;
	}

	public static synchronized boolean isOreBoosted(MinecraftServer server, String oreKey) {
		WorldData data = get(server);
		return data.orePlusEnabled && Boolean.TRUE.equals(data.oreToggles.getOrDefault(oreKey, true));
	}

	public static synchronized boolean canUseTools(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) return false;
		if (server.isSingleplayer() || server.isHost(player.getGameProfile()) || player.hasPermissionLevel(2)) {
			return true;
		}
		return get(server).allowAllPlayers;
	}

	private static Path getStateFile(MinecraftServer server) {
		return server.getSavePath(WorldSavePath.ROOT).resolve("hardcore_tools_state.json");
	}

	private static WorldData load(MinecraftServer server) {
		Path path = getStateFile(server);
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				WorldData loaded = GSON.fromJson(reader, WorldData.class);
				if (loaded != null) {
					loaded.sanitize();
					return loaded;
				}
			} catch (Exception e) {
				HardcoreToolsMod.LOGGER.warn("Failed to read hardcore_tools_state.json, creating fresh state", e);
			}
		}
		WorldData fresh = new WorldData();
		fresh.sanitize();
		return fresh;
	}

	public static synchronized void save(MinecraftServer server) {
		if (server == null || activeData == null) return;
		Path path = getStateFile(server);
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(activeData, writer);
			}
		} catch (IOException e) {
			HardcoreToolsMod.LOGGER.error("Failed to save hardcore_tools_state.json", e);
		}
	}

	public static synchronized void clearCache() {
		activeServer = null;
		activeData = null;
	}
}
