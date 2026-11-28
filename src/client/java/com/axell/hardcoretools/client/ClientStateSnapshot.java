package com.axell.hardcoretools.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.axell.hardcoretools.client.gui.CrimsonTheme;
import com.axell.hardcoretools.client.gui.HardcoreDashboardScreen;
import com.axell.hardcoretools.feature.CombatAimFeature;
import com.axell.hardcoretools.state.HardcoreStateManager;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ClientStateSnapshot {
	private static final Gson GSON = new Gson();
	public static final ClientStateSnapshot INSTANCE = new ClientStateSnapshot();

	public static class EnchantItem {
		public String id = "";
		public String name = "";
		public int maxLevel = 1;
		public int currentLevel = 0;
		public String currentRoman = "";
		public boolean fitsHeld = false;
		public boolean curse = false;
	}

	public static class GearSlotItem {
		public int slot = -1;
		public String tag = "";
		public String name = "";
		public int enchantCount = 0;
	}

	public static class WeaponCatalogItem {
		public String id = "";
		public String name = "";
		public String category = "sword";
		public double vanillaDamage = 1.0;
		public double vanillaSpeed = 1.6;
		public double activeDamage = 1.0;
		public double activeSpeed = 1.6;
		public boolean overridden = false;
	}

	public static class BagWeaponItem {
		public int slot = -1;
		public String tag = "";
		public String itemId = "";
		public String name = "";
		public double vanillaDamage = 1.0;
		public double vanillaSpeed = 1.6;
		public double currentDamage = 1.0;
		public double currentSpeed = 1.6;
		public boolean customItem = false;
		public boolean catalogOverridden = false;
	}

	public static class AimMobItem {
		public String id = "";
		public String name = "";
		public String group = "hostile";
		public boolean excluded = false;
	}

	public static class WaypointItem {
		public String id = "";
		public String name = "";
		public String dimension = "minecraft:overworld";
		public double x;
		public double y;
		public double z;
		public int color = 0xFFDC1E36;
	}

	// Persistent UI navigation state across sync packets
	public int activeTab = 0;
	public int uiTheme = 0;
	public int customEnchantLevel = 25;
	public int customBookLevel = 25;
	public boolean showOnlyCompatibleEnchants = false;
	public int effectSeconds = 300;
	public int effectAmplifier = 1; // Level II (0-based = 1)
	public boolean effectInfinite = true;
	public int teleportSubTab = 0; // 0=Biomes, 1=Structures, 2=Players, 3=Coords
	public boolean locatorAutoTp = true;
	public String selectedCoordDim = "minecraft:overworld";
	public int selectedWaypointColor = 0xFFDC1E36;

	// Item Upgrader UI & Arcane Altar Ritual state
	public static final long RITUAL_DURATION_MS = 3400L;
	public int upgraderSelectedSlot = 0;
	public int upgraderAmount = 1;
	public String upgraderTargetId = "minecraft:netherite_ingot";
	public String upgraderLastSacrificeItemId = "";
	public String upgraderSacrificeName = "Offering";
	public boolean upgraderModalOpen = false;
	public boolean upgraderSkipRequested = false;
	public long upgraderSpinStartMs = 0L;
	public long upgraderSpinEndMs = 0L;
	public long upgraderRevealStartMs = 0L;
	public int upgraderLastTickBucket = -1;
	public boolean upgraderHasResult = false;
	public boolean upgraderResultRevealed = true;
	public long upgraderRollSeq = 0L;
	public boolean upgraderWon = false;
	public double upgraderChance = 50.0;
	public double upgraderRolled = 0.0;
	public String upgraderResultTargetId = "minecraft:netherite_ingot";
	public String upgraderTargetName = "Netherite Ingot";
	public int upgraderSacrificedAmount = 1;
	public int upgraderXpRefund = 0;
	public int upgraderInheritedEnchants = 0;
	public double upgraderPity = 0.0;
	public int upgraderFailStreak = 0;
	public double upgraderPendingPity = -1.0;
	public int upgraderPendingFailStreak = -1;
	public boolean upgraderStartedWithFullPity = false;
	public boolean upgraderPityTriggered = false;
	public double upgraderPityBefore = 0.0;
	public double upgraderPityAfter = 0.0;
	public double upgraderPityGain = 0.0;
	public ItemStack upgraderSacrificeStack = ItemStack.EMPTY;
	public ItemStack upgraderRewardStack = ItemStack.EMPTY;

	// Uncrafter UI state
	public int uncraftSelectedSlot = -1;
	public int uncraftBatches = 1;
	public boolean uncraftSplitBooks = true;

	// Weapon Damage Studio UI state
	public int weaponStudioSubMode = 0; // 0 = All-Weapons Live Catalog, 1 = Bag & Hand Item Forge
	public int weaponCategoryFilter = 0; // 0 = All, 1 = Swords, 2 = Axes & Mace, 3 = Ranged & Trident, 4 = Tools
	public String selectedCatalogWeaponId = "minecraft:diamond_sword";
	public double pendingCatalogDamage = 7.0;
	public double pendingBagDamage = 7.0;
	public double pendingBagSpeed = 1.6;
	public int lastLoadedBagWeaponSlot = -99;
	public boolean weaponStudioEnabled = true;
	public double globalWeaponMultiplier = 1.0;
	public int selectedWeaponSlot = -1;
	public String selectedWeaponSlotLabel = "None";
	public List<BagWeaponItem> bagWeapons = new ArrayList<>();
	public List<WeaponCatalogItem> weaponCatalog = new ArrayList<>();

	// Combat Aim UI & Synced State
	public boolean serverModSynced = false;
	public boolean aimbotEnabled = false;
	public boolean aimSilentEnabled = false;
	public boolean aimShowFovCircle = true;
	public boolean aimVisibleOnly = true;
	public boolean aimWeaponOnly = true;
	public int aimFovDegrees = 30;
	public int aimSmoothSpeed = 50;
	public int aimMaxReach = 6;
	public boolean aimProtectPets = true;
	public boolean aimProtectVillagers = true;
	public boolean aimProtectPassive = true;
	public boolean aimProtectNeutral = false;
	public boolean aimProtectNamed = true;
	public boolean aimProtectPlayers = true;
	public Set<String> aimExcludedMobs = new HashSet<>();
	public List<AimMobItem> aimMobCatalog = new ArrayList<>();
	public int aimMobGroupFilter = 0; // 0=All, 1=Excluded, 2=Friendly/Neutral, 3=Hostile

	public CombatAimFeature.AimConfig toAimConfig() {
		return new CombatAimFeature.AimConfig(
				aimbotEnabled,
				aimSilentEnabled,
				aimShowFovCircle,
				aimVisibleOnly,
				aimWeaponOnly,
				aimFovDegrees,
				aimSmoothSpeed,
				aimMaxReach,
				aimProtectPets,
				aimProtectVillagers,
				aimProtectPassive,
				aimProtectNeutral,
				aimProtectNamed,
				aimProtectPlayers,
				aimExcludedMobs
		);
	}

	public synchronized void resetOnDisconnect() {
		this.serverModSynced = false;
		this.aimbotEnabled = false;
		this.aimSilentEnabled = false;
	}

	// Toast banner
	public String toastText = "";
	public boolean toastOk = true;
	public long toastExpireMs = 0L;

	// Synced Server State
	public int totemCount = 0;
	public int xpLevel = 0;
	public int xpTotal = 0;
	public float health = 20.0F;
	public float maxHealth = 20.0F;
	public String dimension = "minecraft:overworld";
	public double posX;
	public double posY;
	public double posZ;
	public boolean worldHardcore = false;
	public boolean hasSyncedWorldHardcore = false;
	public String gameMode = "survival";

	public boolean immortal;
	public boolean fullbright = false;
	public boolean autoTotem;
	public boolean autoTotemOffhand = true;
	public boolean panicSaver = true;
	public int panicHpThreshold = 6;
	public boolean returnByDeath = false;
	public int returnByDeathSeconds = 60;
	public int returnByDeathCooldown = 0;
	public int returnByDeathCooldownLeft = 0;

	public boolean veinMiner;
	public boolean treeCapitator;
	public int veinMaxBlocks = 64;
	public boolean veinAutoCollect = true;
	public boolean veinAutoSmelt = false;
	public boolean fastLeafDecay = true;

	public boolean sneakGrow;
	public boolean sneakGrowInstant;
	public int sneakGrowRadius = 4;
	public int sneakGrowSpeed = 5;
	public boolean sneakGrowParticles = true;

	public boolean orePlusEnabled;
	public int orePlusMultiplier = 2;
	public Map<String, Boolean> ores = new LinkedHashMap<>();

	public boolean bypassEnchantConflicts;
	public int selectedEnchantSlot = -1;
	public String targetSlotLabel = "None";
	public List<GearSlotItem> gearSlots = new ArrayList<>();
	public boolean heldEmpty = true;
	public String heldName = "";
	public int heldDamage = 0;
	public int heldMaxDamage = 0;
	public boolean heldDamageable = false;
	public int damagedCount = 0;

	public List<EnchantItem> enchantCatalog = new ArrayList<>();
	public List<String> onlinePlayers = new ArrayList<>();
	public boolean hasBackLocation = false;
	public String backLabel = "Previous Position";
	public String backDimension = "minecraft:overworld";
	public double backX;
	public double backY;
	public double backZ;
	public List<WaypointItem> waypoints = new ArrayList<>();
	public Map<String, Boolean> gamerules = new LinkedHashMap<>();

	private ClientStateSnapshot() {
		for (String k : HardcoreStateManager.ALL_ORE_KEYS) {
			ores.put(k, true);
		}
	}

	public synchronized void applyJson(String json) {
		if (json == null || json.isBlank()) return;
		try {
			JsonObject r = GSON.fromJson(json, JsonObject.class);
			if (r == null) return;

			String incomingToast = str(r, "toast", "");
			if (!incomingToast.isEmpty()) {
				this.toastText = incomingToast;
				this.toastOk = bool(r, "toastOk", true);
				this.toastExpireMs = System.currentTimeMillis() + 3400L;
				if (!this.toastOk && this.upgraderModalOpen && !this.upgraderHasResult) {
					this.upgraderModalOpen = false;
				}
				MinecraftClient mc = MinecraftClient.getInstance();
				if (mc != null && mc.inGameHud != null && !(mc.currentScreen instanceof HardcoreDashboardScreen)) {
					mc.inGameHud.setOverlayMessage(
							Text.literal(incomingToast).formatted(this.toastOk ? Formatting.GREEN : Formatting.RED),
							false
					);
				}
			}

			if (r.has("uiTheme")) {
				this.uiTheme = Math.max(0, Math.min(4, numInt(r, "uiTheme", this.uiTheme)));
				CrimsonTheme.applyTheme(this.uiTheme);
			}

			this.totemCount = numInt(r, "totemCount", totemCount);
			this.xpLevel = numInt(r, "xpLevel", xpLevel);
			this.xpTotal = numInt(r, "xpTotal", xpTotal);
			this.health = (float) numDouble(r, "health", health);
			this.maxHealth = (float) numDouble(r, "maxHealth", maxHealth);
			this.dimension = str(r, "dimension", dimension);
			this.posX = numDouble(r, "posX", posX);
			this.posY = numDouble(r, "posY", posY);
			this.posZ = numDouble(r, "posZ", posZ);
			if (r.has("worldHardcore")) {
				this.worldHardcore = bool(r, "worldHardcore", worldHardcore);
				this.hasSyncedWorldHardcore = true;
			}
			this.gameMode = str(r, "gameMode", gameMode);

			this.immortal = bool(r, "immortal", immortal);
			this.fullbright = bool(r, "fullbright", fullbright);
			this.autoTotem = bool(r, "autoTotem", autoTotem);
			this.autoTotemOffhand = bool(r, "autoTotemOffhand", autoTotemOffhand);
			this.panicSaver = bool(r, "panicSaver", panicSaver);
			this.panicHpThreshold = numInt(r, "panicHpThreshold", panicHpThreshold);
			this.returnByDeath = bool(r, "returnByDeath", returnByDeath);
			this.returnByDeathSeconds = numInt(r, "returnByDeathSeconds", returnByDeathSeconds);
			this.returnByDeathCooldown = numInt(r, "returnByDeathCooldown", returnByDeathCooldown);
			this.returnByDeathCooldownLeft = numInt(r, "returnByDeathCooldownLeft", returnByDeathCooldownLeft);

			this.veinMiner = bool(r, "veinMiner", veinMiner);
			this.treeCapitator = bool(r, "treeCapitator", treeCapitator);
			this.veinMaxBlocks = numInt(r, "veinMaxBlocks", veinMaxBlocks);
			this.veinAutoCollect = bool(r, "veinAutoCollect", veinAutoCollect);
			this.veinAutoSmelt = bool(r, "veinAutoSmelt", veinAutoSmelt);
			this.fastLeafDecay = bool(r, "fastLeafDecay", fastLeafDecay);

			this.sneakGrow = bool(r, "sneakGrow", sneakGrow);
			this.sneakGrowInstant = bool(r, "sneakGrowInstant", sneakGrowInstant);
			this.sneakGrowRadius = numInt(r, "sneakGrowRadius", sneakGrowRadius);
			this.sneakGrowSpeed = numInt(r, "sneakGrowSpeed", sneakGrowSpeed);
			this.sneakGrowParticles = bool(r, "sneakGrowParticles", sneakGrowParticles);

			this.orePlusEnabled = bool(r, "orePlusEnabled", orePlusEnabled);
			this.orePlusMultiplier = numInt(r, "orePlusMultiplier", orePlusMultiplier);
			if (r.has("ores") && r.get("ores").isJsonObject()) {
				JsonObject oo = r.getAsJsonObject("ores");
				for (String k : HardcoreStateManager.ALL_ORE_KEYS) {
					ores.put(k, bool(oo, k, true));
				}
			}

			this.weaponStudioEnabled = bool(r, "weaponStudioEnabled", weaponStudioEnabled);
			this.globalWeaponMultiplier = numDouble(r, "globalWeaponMultiplier", globalWeaponMultiplier);
			int prevWeaponSlot = this.selectedWeaponSlot;
			this.selectedWeaponSlot = numInt(r, "selectedWeaponSlot", selectedWeaponSlot);
			this.selectedWeaponSlotLabel = str(r, "selectedWeaponSlotLabel", selectedWeaponSlotLabel);

			if (r.has("bagWeapons") && r.get("bagWeapons").isJsonArray()) {
				List<BagWeaponItem> nextBag = new ArrayList<>();
				for (JsonElement el : r.get("bagWeapons").getAsJsonArray()) {
					if (!el.isJsonObject()) continue;
					JsonObject bo = el.getAsJsonObject();
					BagWeaponItem bw = new BagWeaponItem();
					bw.slot = numInt(bo, "slot", -1);
					bw.tag = str(bo, "tag", "");
					bw.itemId = str(bo, "itemId", "");
					bw.name = str(bo, "name", "");
					bw.vanillaDamage = numDouble(bo, "vanillaDamage", 1.0);
					bw.vanillaSpeed = numDouble(bo, "vanillaSpeed", 1.6);
					bw.currentDamage = numDouble(bo, "currentDamage", 1.0);
					bw.currentSpeed = numDouble(bo, "currentSpeed", 1.6);
					bw.customItem = bool(bo, "customItem", false);
					bw.catalogOverridden = bool(bo, "catalogOverridden", false);
					nextBag.add(bw);
				}
				this.bagWeapons = nextBag;
				if (this.selectedWeaponSlot != prevWeaponSlot || this.lastLoadedBagWeaponSlot != this.selectedWeaponSlot) {
					for (BagWeaponItem bw : this.bagWeapons) {
						if (bw.slot == this.selectedWeaponSlot) {
							this.pendingBagDamage = bw.currentDamage;
							this.pendingBagSpeed = bw.currentSpeed;
							this.lastLoadedBagWeaponSlot = this.selectedWeaponSlot;
							break;
						}
					}
				}
			}

			if (r.has("weaponCatalog") && r.get("weaponCatalog").isJsonArray()) {
				List<WeaponCatalogItem> nextCat = new ArrayList<>();
				for (JsonElement el : r.get("weaponCatalog").getAsJsonArray()) {
					if (!el.isJsonObject()) continue;
					JsonObject wo = el.getAsJsonObject();
					WeaponCatalogItem wc = new WeaponCatalogItem();
					wc.id = str(wo, "id", "");
					wc.name = str(wo, "name", wc.id);
					wc.category = str(wo, "category", "sword");
					wc.vanillaDamage = numDouble(wo, "vanillaDamage", 1.0);
					wc.vanillaSpeed = numDouble(wo, "vanillaSpeed", 1.6);
					wc.activeDamage = numDouble(wo, "activeDamage", 1.0);
					wc.activeSpeed = numDouble(wo, "activeSpeed", 1.6);
					wc.overridden = bool(wo, "overridden", false);
					nextCat.add(wc);
				}
				this.weaponCatalog = nextCat;
			}

			this.serverModSynced = true;
			this.aimbotEnabled = bool(r, "aimbotEnabled", aimbotEnabled);
			this.aimSilentEnabled = bool(r, "aimSilentEnabled", aimSilentEnabled);
			this.aimShowFovCircle = bool(r, "aimShowFovCircle", aimShowFovCircle);
			this.aimVisibleOnly = bool(r, "aimVisibleOnly", aimVisibleOnly);
			this.aimWeaponOnly = bool(r, "aimWeaponOnly", aimWeaponOnly);
			this.aimFovDegrees = numInt(r, "aimFovDegrees", aimFovDegrees);
			this.aimSmoothSpeed = numInt(r, "aimSmoothSpeed", aimSmoothSpeed);
			this.aimMaxReach = numInt(r, "aimMaxReach", aimMaxReach);
			this.aimProtectPets = bool(r, "aimProtectPets", aimProtectPets);
			this.aimProtectVillagers = bool(r, "aimProtectVillagers", aimProtectVillagers);
			this.aimProtectPassive = bool(r, "aimProtectPassive", aimProtectPassive);
			this.aimProtectNeutral = bool(r, "aimProtectNeutral", aimProtectNeutral);
			this.aimProtectNamed = bool(r, "aimProtectNamed", aimProtectNamed);
			this.aimProtectPlayers = bool(r, "aimProtectPlayers", aimProtectPlayers);

			if (r.has("aimExcludedMobs") && r.get("aimExcludedMobs").isJsonArray()) {
				Set<String> nextExcl = new HashSet<>();
				for (JsonElement el : r.get("aimExcludedMobs").getAsJsonArray()) {
					if (!el.isJsonNull()) {
						nextExcl.add(el.getAsString());
					}
				}
				this.aimExcludedMobs = nextExcl;
			}

			if (r.has("aimMobCatalog") && r.get("aimMobCatalog").isJsonArray()) {
				List<AimMobItem> nextMobs = new ArrayList<>();
				for (JsonElement el : r.get("aimMobCatalog").getAsJsonArray()) {
					if (!el.isJsonObject()) continue;
					JsonObject mo = el.getAsJsonObject();
					AimMobItem mi = new AimMobItem();
					mi.id = str(mo, "id", "");
					mi.name = str(mo, "name", mi.id);
					mi.group = str(mo, "group", "hostile");
					mi.excluded = bool(mo, "excluded", false);
					nextMobs.add(mi);
				}
				this.aimMobCatalog = nextMobs;
			}

			this.bypassEnchantConflicts = bool(r, "bypassEnchantConflicts", bypassEnchantConflicts);
			this.selectedEnchantSlot = numInt(r, "selectedEnchantSlot", selectedEnchantSlot);
			this.targetSlotLabel = str(r, "targetSlotLabel", targetSlotLabel);
			this.damagedCount = numInt(r, "damagedCount", 0);

			if (r.has("gearSlots") && r.get("gearSlots").isJsonArray()) {
				List<GearSlotItem> nextGear = new ArrayList<>();
				for (JsonElement el : r.get("gearSlots").getAsJsonArray()) {
					if (!el.isJsonObject()) continue;
					JsonObject go = el.getAsJsonObject();
					GearSlotItem gi = new GearSlotItem();
					gi.slot = numInt(go, "slot", -1);
					gi.tag = str(go, "tag", "");
					gi.name = str(go, "name", "");
					gi.enchantCount = numInt(go, "enchantCount", 0);
					nextGear.add(gi);
				}
				this.gearSlots = nextGear;
			}

			if (r.has("held") && r.get("held").isJsonObject()) {
				JsonObject ho = r.getAsJsonObject("held");
				this.heldEmpty = bool(ho, "empty", true);
				this.heldName = str(ho, "name", "");
				this.heldDamage = numInt(ho, "damage", 0);
				this.heldMaxDamage = numInt(ho, "maxDamage", 0);
				this.heldDamageable = bool(ho, "damageable", false);
			}

			if (r.has("enchantCatalog") && r.get("enchantCatalog").isJsonArray()) {
				List<EnchantItem> nextEnch = new ArrayList<>();
				for (JsonElement el : r.getAsJsonArray("enchantCatalog")) {
					if (!el.isJsonObject()) continue;
					JsonObject eo = el.getAsJsonObject();
					EnchantItem item = new EnchantItem();
					item.id = str(eo, "id", "");
					item.name = str(eo, "name", item.id);
					item.maxLevel = numInt(eo, "maxLevel", 1);
					item.currentLevel = numInt(eo, "currentLevel", 0);
					item.currentRoman = str(eo, "currentRoman", "");
					item.fitsHeld = bool(eo, "fitsHeld", false);
					item.curse = bool(eo, "curse", false);
					nextEnch.add(item);
				}
				this.enchantCatalog = nextEnch;
			}

			if (r.has("onlinePlayers") && r.get("onlinePlayers").isJsonArray()) {
				List<String> nextPlayers = new ArrayList<>();
				for (JsonElement el : r.getAsJsonArray("onlinePlayers")) {
					nextPlayers.add(el.getAsString());
				}
				this.onlinePlayers = nextPlayers;
			}

			if (r.has("lastLocation") && r.get("lastLocation").isJsonObject()) {
				JsonObject bo = r.getAsJsonObject("lastLocation");
				this.hasBackLocation = true;
				this.backLabel = str(bo, "label", "Previous Position");
				this.backDimension = str(bo, "dimension", "minecraft:overworld");
				this.backX = numDouble(bo, "x", 0);
				this.backY = numDouble(bo, "y", 64);
				this.backZ = numDouble(bo, "z", 0);
			} else {
				this.hasBackLocation = false;
			}

			if (r.has("upgraderRoll") && r.get("upgraderRoll").isJsonObject()) {
				JsonObject uo = r.getAsJsonObject("upgraderRoll");
				long seq = (long) numDouble(uo, "seq", 0);
				if (seq > this.upgraderRollSeq) {
					this.upgraderRollSeq = seq;
					this.upgraderWon = bool(uo, "won", false);
					this.upgraderPityTriggered = bool(uo, "pityTriggered", false);
					this.upgraderChance = numDouble(uo, "chance", 50.0);
					this.upgraderRolled = numDouble(uo, "rolled", 50.0);
					this.upgraderResultTargetId = str(uo, "targetId", this.upgraderTargetId);
					this.upgraderTargetName = str(uo, "targetName", "Reward");
					this.upgraderSacrificedAmount = numInt(uo, "amount", this.upgraderAmount);
					this.upgraderXpRefund = numInt(uo, "xpRefund", 0);
					this.upgraderInheritedEnchants = numInt(uo, "inheritedEnchants", 0);
					this.upgraderPityBefore = numDouble(uo, "pityBefore", this.upgraderPity);
					this.upgraderPityAfter = numDouble(uo, "pityAfter", this.upgraderPity);
					this.upgraderPityGain = numDouble(uo, "pityGain", 0.0);
					this.upgraderHasResult = true;
					this.upgraderResultRevealed = false;
					long now = System.currentTimeMillis();
					if (this.upgraderSkipRequested) {
						this.upgraderSpinEndMs = now;
					} else if (this.upgraderSpinEndMs <= now) {
						this.upgraderSpinStartMs = now;
						this.upgraderSpinEndMs = now + RITUAL_DURATION_MS;
						this.upgraderLastTickBucket = -1;
					}
				}
			}

			double incomingPity = numDouble(r, "upgraderPity", this.upgraderPity);
			int incomingStreak = numInt(r, "upgraderFailStreak", this.upgraderFailStreak);
			if (this.upgraderModalOpen && !this.upgraderResultRevealed) {
				// Buffer updated pity until the 4.0s Ritual explosion so nothing spoils the outcome early!
				this.upgraderPendingPity = incomingPity;
				this.upgraderPendingFailStreak = incomingStreak;
			} else {
				this.upgraderPity = incomingPity;
				this.upgraderFailStreak = incomingStreak;
				this.upgraderPendingPity = -1.0;
				this.upgraderPendingFailStreak = -1;
			}

			if (r.has("waypoints") && r.get("waypoints").isJsonArray()) {
				List<WaypointItem> nextWps = new ArrayList<>();
				for (JsonElement el : r.getAsJsonArray("waypoints")) {
					if (!el.isJsonObject()) continue;
					JsonObject wo = el.getAsJsonObject();
					WaypointItem wp = new WaypointItem();
					wp.id = str(wo, "id", "");
					wp.name = str(wo, "name", "Waypoint");
					wp.dimension = str(wo, "dimension", "minecraft:overworld");
					wp.x = numDouble(wo, "x", 0);
					wp.y = numDouble(wo, "y", 64);
					wp.z = numDouble(wo, "z", 0);
					wp.color = numInt(wo, "color", 0xFFDC1E36);
					nextWps.add(wp);
				}
				this.waypoints = nextWps;
			}

			if (r.has("gamerules") && r.get("gamerules").isJsonObject()) {
				JsonObject go = r.getAsJsonObject("gamerules");
				for (String key : go.keySet()) {
					gamerules.put(key, bool(go, key, false));
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static String str(JsonObject o, String k, String def) {
		return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : def;
	}

	private static int numInt(JsonObject o, String k, int def) {
		try {
			return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsInt() : def;
		} catch (Exception e) {
			return def;
		}
	}

	private static double numDouble(JsonObject o, String k, double def) {
		try {
			return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsDouble() : def;
		} catch (Exception e) {
			return def;
		}
	}

	private static boolean bool(JsonObject o, String k, boolean def) {
		try {
			return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsBoolean() : def;
		} catch (Exception e) {
			return def;
		}
	}
}
