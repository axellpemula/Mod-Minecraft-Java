package com.axell.hardcoretools.network;

import java.util.Comparator;

import com.axell.hardcoretools.HardcoreToolsMod;
import com.axell.hardcoretools.feature.ActionDispatcher;
import com.axell.hardcoretools.feature.EnchantFeature;
import com.axell.hardcoretools.feature.ProtectionHandler;
import com.axell.hardcoretools.state.HardcoreStateManager;
import com.axell.hardcoretools.state.PlayerProfile;
import com.axell.hardcoretools.state.WaypointEntry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

public final class HardcoreNetworking {
	private static final Gson GSON = new Gson();
	private static final TagKey<Enchantment> CURSE_TAG = TagKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("curse"));

	public record SyncStateS2CPayload(String json) implements CustomPayload {
		public static final CustomPayload.Id<SyncStateS2CPayload> ID =
				new CustomPayload.Id<>(Identifier.of(HardcoreToolsMod.MOD_ID, "sync_state"));
		public static final PacketCodec<RegistryByteBuf, SyncStateS2CPayload> CODEC =
				PacketCodec.tuple(PacketCodecs.string(262144), SyncStateS2CPayload::json, SyncStateS2CPayload::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record OpenScreenS2CPayload(String json) implements CustomPayload {
		public static final CustomPayload.Id<OpenScreenS2CPayload> ID =
				new CustomPayload.Id<>(Identifier.of(HardcoreToolsMod.MOD_ID, "open_screen"));
		public static final PacketCodec<RegistryByteBuf, OpenScreenS2CPayload> CODEC =
				PacketCodec.tuple(PacketCodecs.string(262144), OpenScreenS2CPayload::json, OpenScreenS2CPayload::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record RewindEventS2CPayload(String json) implements CustomPayload {
		public static final CustomPayload.Id<RewindEventS2CPayload> ID =
				new CustomPayload.Id<>(Identifier.of(HardcoreToolsMod.MOD_ID, "rewind_event"));
		public static final PacketCodec<RegistryByteBuf, RewindEventS2CPayload> CODEC =
				PacketCodec.tuple(PacketCodecs.string(262144), RewindEventS2CPayload::json, RewindEventS2CPayload::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record ActionC2SPayload(String action, String payloadJson) implements CustomPayload {
		public static final CustomPayload.Id<ActionC2SPayload> ID =
				new CustomPayload.Id<>(Identifier.of(HardcoreToolsMod.MOD_ID, "action"));
		public static final PacketCodec<RegistryByteBuf, ActionC2SPayload> CODEC =
				PacketCodec.tuple(
						PacketCodecs.string(128), ActionC2SPayload::action,
						PacketCodecs.string(65536), ActionC2SPayload::payloadJson,
						ActionC2SPayload::new
				);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	private HardcoreNetworking() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(SyncStateS2CPayload.ID, SyncStateS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenScreenS2CPayload.ID, OpenScreenS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RewindEventS2CPayload.ID, RewindEventS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ActionC2SPayload.ID, ActionC2SPayload.CODEC);
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(ActionC2SPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			MinecraftServer server = player.getServer();
			if (server == null) return;
			server.execute(() -> {
				if ("request_open".equals(payload.action())) {
					if (!HardcoreStateManager.canUseTools(player)) {
						return;
					}
					sendOpenScreen(player);
					return;
				}
				ActionDispatcher.handle(player, payload.action(), payload.payloadJson());
			});
		});
	}

	public static void sendOpenScreen(ServerPlayerEntity player) {
		String json = buildSnapshotJson(player, "", true);
		ServerPlayNetworking.send(player, new OpenScreenS2CPayload(json));
	}

	public static void sendSyncState(ServerPlayerEntity player, String toastMessage, boolean toastSuccess) {
		String json = buildSnapshotJson(player, toastMessage, toastSuccess);
		ServerPlayNetworking.send(player, new SyncStateS2CPayload(json));
	}

	public static void sendRewindEvent(ServerPlayerEntity player, String rewindJson) {
		ServerPlayNetworking.send(player, new RewindEventS2CPayload(rewindJson));
	}

	public static String buildSnapshotJson(ServerPlayerEntity player, String toastMessage, boolean toastSuccess) {
		MinecraftServer server = player.getServer();
		HardcoreStateManager.WorldData worldData = HardcoreStateManager.get(server);
		PlayerProfile profile = HardcoreStateManager.getProfile(player);

		JsonObject root = new JsonObject();
		root.addProperty("toast", toastMessage == null ? "" : toastMessage);
		root.addProperty("toastOk", toastSuccess);

		// Player stats & live badges
		root.addProperty("totemCount", ProtectionHandler.countTotems(player));
		root.addProperty("xpLevel", player.experienceLevel);
		root.addProperty("xpTotal", player.totalExperience);
		root.addProperty("health", player.getHealth());
		root.addProperty("maxHealth", player.getMaxHealth());
		root.addProperty("dimension", player.getServerWorld().getRegistryKey().getValue().toString());
		root.addProperty("posX", Math.round(player.getX() * 10.0) / 10.0);
		root.addProperty("posY", Math.round(player.getY() * 10.0) / 10.0);
		root.addProperty("posZ", Math.round(player.getZ() * 10.0) / 10.0);

		boolean worldHardcore = server.getSaveProperties().isHardcore();
		GameMode gm = player.interactionManager.getGameMode();
		String activeMode = switch (gm) {
			case CREATIVE -> "creative";
			case ADVENTURE -> "adventure";
			case SPECTATOR -> "spectator";
			case SURVIVAL -> worldHardcore ? "hardcore" : "survival";
		};
		root.addProperty("worldHardcore", worldHardcore);
		root.addProperty("gameMode", activeMode);
		root.addProperty("uiTheme", profile.uiTheme);

		// Protection
		root.addProperty("immortal", profile.immortal);
		root.addProperty("fullbright", profile.fullbright);
		root.addProperty("autoTotem", profile.autoTotem);
		root.addProperty("autoTotemOffhand", profile.autoTotemOffhand);
		root.addProperty("panicSaver", profile.panicSaver);
		root.addProperty("panicHpThreshold", profile.panicHpThreshold);
		root.addProperty("returnByDeath", profile.returnByDeath);
		root.addProperty("returnByDeathSeconds", profile.returnByDeathSeconds);
		root.addProperty("returnByDeathCooldown", profile.returnByDeathCooldown);
		long remCdTicks = Math.max(0L, profile.returnByDeathReadyTick - server.getTicks());
		root.addProperty("returnByDeathCooldownLeft", (int) Math.ceil(remCdTicks / 20.0));

		// Vein & Tree
		root.addProperty("veinMiner", profile.veinMiner);
		root.addProperty("treeCapitator", profile.treeCapitator);
		root.addProperty("veinMaxBlocks", profile.veinMaxBlocks);
		root.addProperty("veinAutoCollect", profile.veinAutoCollect);
		root.addProperty("veinAutoSmelt", profile.veinAutoSmelt);
		root.addProperty("fastLeafDecay", profile.fastLeafDecay);

		// Sneak Grow
		root.addProperty("sneakGrow", profile.sneakGrow);
		root.addProperty("sneakGrowInstant", profile.sneakGrowInstant);
		root.addProperty("sneakGrowRadius", profile.sneakGrowRadius);
		root.addProperty("sneakGrowSpeed", profile.sneakGrowSpeed);
		root.addProperty("sneakGrowParticles", profile.sneakGrowParticles);

		// Ore Plus+
		root.addProperty("orePlusEnabled", worldData.orePlusEnabled);
		root.addProperty("orePlusMultiplier", worldData.orePlusMultiplier);
		JsonObject oresObj = new JsonObject();
		for (String k : HardcoreStateManager.ALL_ORE_KEYS) {
			oresObj.addProperty(k, Boolean.TRUE.equals(worldData.oreToggles.getOrDefault(k, true)));
		}
		root.add("ores", oresObj);

		// Enchantment Studio & Target Item details (Armor / Offhand / Hotbar / Backpack)
		root.addProperty("bypassEnchantConflicts", profile.bypassEnchantConflicts);
		root.addProperty("upgraderPity", profile.upgraderPity);
		root.addProperty("upgraderFailStreak", profile.upgraderFailStreak);
		int resolvedSlot = EnchantFeature.resolveEffectiveSlotIndex(player, profile.selectedEnchantSlot);
		profile.selectedEnchantSlot = resolvedSlot;
		ItemStack targetStack = EnchantFeature.getStackAtSlot(player, resolvedSlot);

		root.addProperty("selectedEnchantSlot", resolvedSlot);
		root.addProperty("targetSlotLabel", EnchantFeature.describeSlot(player, resolvedSlot));

		JsonArray gearArr = new JsonArray();
		for (EnchantFeature.GearSlotEntry ge : EnchantFeature.collectEnchantableSlots(player)) {
			JsonObject go = new JsonObject();
			go.addProperty("slot", ge.slot());
			go.addProperty("tag", ge.tag());
			go.addProperty("name", ge.stack().getName().getString());
			go.addProperty("enchantCount", ge.enchantCount());
			gearArr.add(go);
		}
		root.add("gearSlots", gearArr);

		ItemStack held = player.getMainHandStack();
		JsonObject heldObj = new JsonObject();
		heldObj.addProperty("empty", held.isEmpty());
		if (!held.isEmpty()) {
			heldObj.addProperty("id", Registries.ITEM.getId(held.getItem()).toString());
			heldObj.addProperty("name", held.getName().getString());
			heldObj.addProperty("damage", held.getDamage());
			heldObj.addProperty("maxDamage", held.getMaxDamage());
			heldObj.addProperty("damageable", held.isDamageable());
		}
		root.add("held", heldObj);

		// Registry catalog of all Enchantments + current level on SELECTED target item
		Registry<Enchantment> enchReg = server.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		ItemEnchantmentsComponent targetEnchants = targetStack.isEmpty()
				? ItemEnchantmentsComponent.DEFAULT
				: (targetStack.isOf(Items.ENCHANTED_BOOK)
						? targetStack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT)
						: EnchantmentHelper.getEnchantments(targetStack));

		JsonArray enchCatalog = new JsonArray();
		enchReg.streamEntries()
				.sorted(Comparator.comparing(e -> e.registryKey().getValue().getPath()))
				.forEach(entry -> {
					JsonObject eo = new JsonObject();
					String idStr = entry.registryKey().getValue().toString();
					int curLv = targetStack.isEmpty() ? 0 : targetEnchants.getLevel(entry);
					boolean fitsHeld = !targetStack.isEmpty() && (targetStack.isOf(Items.ENCHANTED_BOOK) || entry.value().isAcceptableItem(targetStack));
					eo.addProperty("id", idStr);
					eo.addProperty("name", stripRomanSuffix(Enchantment.getName(entry, 1).getString(), entry.value().getMaxLevel()));
					eo.addProperty("maxLevel", entry.value().getMaxLevel());
					eo.addProperty("currentLevel", curLv);
					eo.addProperty("currentRoman", curLv > 0 ? EnchantFeature.toRoman(curLv) : "");
					eo.addProperty("fitsHeld", fitsHeld);
					eo.addProperty("curse", entry.isIn(CURSE_TAG));
					enchCatalog.add(eo);
				});
		root.add("enchantCatalog", enchCatalog);

		// Repair summary
		int damagedCount = 0;
		for (ItemStack s : player.getInventory().main) {
			if (!s.isEmpty() && s.isDamaged()) damagedCount++;
		}
		for (ItemStack s : player.getInventory().armor) {
			if (!s.isEmpty() && s.isDamaged()) damagedCount++;
		}
		for (ItemStack s : player.getInventory().offHand) {
			if (!s.isEmpty() && s.isDamaged()) damagedCount++;
		}
		root.addProperty("damagedCount", damagedCount);

		// Online Players
		JsonArray playersArr = new JsonArray();
		for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
			playersArr.add(online.getName().getString());
		}
		root.add("onlinePlayers", playersArr);

		// Last Teleport Origin (for ↩ Back button)
		if (profile.lastTeleportOrigin != null) {
			WaypointEntry back = profile.lastTeleportOrigin;
			JsonObject bo = new JsonObject();
			bo.addProperty("label", back.name);
			bo.addProperty("dimension", back.dimension);
			bo.addProperty("x", back.x);
			bo.addProperty("y", back.y);
			bo.addProperty("z", back.z);
			root.add("lastLocation", bo);
		}

		// Pending Item Upgrader Roll Outcome
		if (profile.pendingRollOutcome != null) {
			var ro = profile.pendingRollOutcome;
			profile.pendingRollOutcome = null;
			JsonObject uo = new JsonObject();
			uo.addProperty("seq", profile.lastUpgradeRollSeq);
			uo.addProperty("won", ro.won());
			uo.addProperty("pityTriggered", ro.pityTriggered());
			uo.addProperty("chance", ro.chance());
			uo.addProperty("rolled", ro.rolled());
			uo.addProperty("targetId", ro.targetId());
			uo.addProperty("targetName", ro.targetName());
			uo.addProperty("amount", ro.sacrificedAmount());
			uo.addProperty("xpRefund", ro.xpRefund());
			uo.addProperty("inheritedEnchants", ro.inheritedEnchants());
			uo.addProperty("pityBefore", ro.pityBefore());
			uo.addProperty("pityAfter", ro.pityAfter());
			uo.addProperty("pityGain", ro.pityGain());
			root.add("upgraderRoll", uo);
		}

		// Waypoints
		JsonArray wpArr = new JsonArray();
		for (WaypointEntry wp : profile.waypoints) {
			JsonObject wo = new JsonObject();
			wo.addProperty("id", wp.id);
			wo.addProperty("name", wp.name);
			wo.addProperty("dimension", wp.dimension);
			wo.addProperty("x", wp.x);
			wo.addProperty("y", wp.y);
			wo.addProperty("z", wp.z);
			wo.addProperty("color", wp.color);
			wpArr.add(wo);
		}
		root.add("waypoints", wpArr);

		// GameRules
		GameRules gr = server.getGameRules();
		JsonObject rulesObj = new JsonObject();
		rulesObj.addProperty("keepInventory", gr.getBoolean(GameRules.KEEP_INVENTORY));
		rulesObj.addProperty("mobGriefing", gr.getBoolean(GameRules.DO_MOB_GRIEFING));
		rulesObj.addProperty("daylightCycle", gr.getBoolean(GameRules.DO_DAYLIGHT_CYCLE));
		rulesObj.addProperty("weatherCycle", gr.getBoolean(GameRules.DO_WEATHER_CYCLE));
		rulesObj.addProperty("immediateRespawn", gr.getBoolean(GameRules.DO_IMMEDIATE_RESPAWN));
		rulesObj.addProperty("pvp", server.isPvpEnabled());
		root.add("gamerules", rulesObj);

		return GSON.toJson(root);
	}

	private static String stripRomanSuffix(String rawName, int maxLevel) {
		if (maxLevel > 1 && rawName.endsWith(" I")) {
			return rawName.substring(0, rawName.length() - 2);
		}
		return rawName;
	}
}
