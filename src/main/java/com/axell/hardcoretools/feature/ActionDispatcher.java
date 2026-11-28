package com.axell.hardcoretools.feature;

import java.util.List;
import java.util.Locale;

import com.axell.hardcoretools.mixin.LevelInfoAccessor;
import com.axell.hardcoretools.mixin.LevelPropertiesAccessor;
import com.axell.hardcoretools.network.HardcoreNetworking;
import com.axell.hardcoretools.state.HardcoreStateManager;
import com.axell.hardcoretools.state.PlayerProfile;
import com.axell.hardcoretools.state.WaypointEntry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.LevelProperties;

public final class ActionDispatcher {
	private static final Gson GSON = new Gson();

	private ActionDispatcher() {
	}

	public static void handle(ServerPlayerEntity player, String action, String payloadJson) {
		MinecraftServer server = player.getServer();
		if (server == null || !HardcoreStateManager.canUseTools(player)) {
			HardcoreNetworking.sendSyncState(player, "Permission denied on this server.", false);
			return;
		}

		JsonObject obj;
		try {
			obj = (payloadJson == null || payloadJson.isBlank()) ? new JsonObject() : GSON.fromJson(payloadJson, JsonObject.class);
			if (obj == null) obj = new JsonObject();
		} catch (Exception e) {
			obj = new JsonObject();
		}

		HardcoreStateManager.WorldData worldData = HardcoreStateManager.get(server);
		PlayerProfile profile = HardcoreStateManager.getProfile(player);
		String toast = "";
		boolean ok = true;

		switch (action) {
			case "refresh" -> toast = "";
			case "set_ui_theme" -> {
				profile.uiTheme = getInt(obj, "value", profile.uiTheme);
				profile.sanitize();
				HardcoreStateManager.save(server);
				toast = "";
			}
			case "radial_toggle_harvest" -> {
				boolean turnOn = !(profile.veinMiner && profile.treeCapitator);
				profile.veinMiner = turnOn;
				profile.treeCapitator = turnOn;
				HardcoreStateManager.save(server);
				toast = "⛏ Vein Miner & Tree Capitator: " + (turnOn ? "ON" : "OFF");
			}

			// 1. Protection
			case "toggle_immortal" -> {
				profile.immortal = !profile.immortal;
				if (profile.immortal) {
					player.setHealth(player.getMaxHealth());
					player.getHungerManager().setFoodLevel(20);
					player.getHungerManager().setSaturationLevel(20.0F);
					ProtectionHandler.clearHarmfulEffects(player);
				}
				HardcoreStateManager.save(server);
				toast = "Immortal Mode: " + (profile.immortal ? "ON" : "OFF");
			}
			case "toggle_fullbright" -> {
				if (obj.has("value") && !obj.get("value").isJsonNull()) {
					profile.fullbright = obj.get("value").getAsBoolean();
				} else {
					profile.fullbright = !profile.fullbright;
				}
				HardcoreStateManager.save(server);
				toast = "Fullbright: " + (profile.fullbright ? "ON" : "OFF");
			}
			case "toggle_autototem" -> {
				profile.autoTotem = !profile.autoTotem;
				if (profile.autoTotem && profile.autoTotemOffhand) {
					ProtectionHandler.refillOffhandTotem(player);
				}
				HardcoreStateManager.save(server);
				toast = "Auto-Totem: " + (profile.autoTotem ? "ON" : "OFF");
			}
			case "toggle_autototem_offhand" -> {
				profile.autoTotemOffhand = !profile.autoTotemOffhand;
				HardcoreStateManager.save(server);
				toast = "Offhand Auto-Refill: " + (profile.autoTotemOffhand ? "ON" : "OFF");
			}
			case "toggle_panic_saver" -> {
				profile.panicSaver = !profile.panicSaver;
				HardcoreStateManager.save(server);
				toast = "Panic Low-HP Saver: " + (profile.panicSaver ? "ON" : "OFF");
			}
			case "set_panic_hp" -> {
				profile.panicHpThreshold = getInt(obj, "value", profile.panicHpThreshold);
				profile.sanitize();
				HardcoreStateManager.save(server);
				toast = "Panic Threshold: " + (profile.panicHpThreshold / 2.0F) + " Hearts";
			}
			case "toggle_rbd" -> {
				profile.returnByDeath = !profile.returnByDeath;
				if (profile.returnByDeath) {
					ReturnByDeathFeature.captureImmediateBaseline(player);
				}
				HardcoreStateManager.save(server);
				toast = "⏪ Return by Death: " + (profile.returnByDeath ? ("ON (-" + profile.returnByDeathSeconds + "s)") : "OFF");
			}
			case "set_rbd_seconds" -> {
				profile.returnByDeathSeconds = getInt(obj, "value", profile.returnByDeathSeconds);
				profile.sanitize();
				HardcoreStateManager.save(server);
				toast = "Rewind Window: " + profile.returnByDeathSeconds + "s";
			}
			case "set_rbd_cooldown" -> {
				profile.returnByDeathCooldown = getInt(obj, "value", profile.returnByDeathCooldown);
				profile.sanitize();
				if (profile.returnByDeathCooldown == 0) {
					profile.returnByDeathReadyTick = 0L;
				}
				HardcoreStateManager.save(server);
				toast = profile.returnByDeathCooldown == 0
						? "Rewind Cooldown: None (Always Ready)"
						: ("Rewind Cooldown: " + profile.returnByDeathCooldown + "s");
			}
			case "toggle_timestop_enabled" -> {
				profile.timeStopEnabled = !profile.timeStopEnabled;
				HardcoreStateManager.save(server);
				if (!profile.timeStopEnabled && TimeStopFeature.isTimeStopped(server)) {
					TimeStopFeature.resumeTimeStop(server, player, true);
				}
				toast = "⏸ Time Stop: " + (profile.timeStopEnabled ? "ON (Use Keybind in-game)" : "OFF");
			}
			case "trigger_timestop" -> {
				TimeStopFeature.toggleTimeStopFromKeybind(player);
				return;
			}

			// 2. Vein Miner & Tree Capitator
			case "toggle_vein" -> {
				profile.veinMiner = !profile.veinMiner;
				HardcoreStateManager.save(server);
				toast = "Vein Miner: " + (profile.veinMiner ? "ON" : "OFF");
			}
			case "toggle_tree" -> {
				profile.treeCapitator = !profile.treeCapitator;
				HardcoreStateManager.save(server);
				toast = "Tree Capitator: " + (profile.treeCapitator ? "ON" : "OFF");
			}
			case "toggle_autocollect" -> {
				profile.veinAutoCollect = !profile.veinAutoCollect;
				HardcoreStateManager.save(server);
				toast = "Auto-Collect Drops: " + (profile.veinAutoCollect ? "ON" : "OFF");
			}
			case "toggle_autosmelt" -> {
				profile.veinAutoSmelt = !profile.veinAutoSmelt;
				HardcoreStateManager.save(server);
				toast = "Auto-Smelt Ores: " + (profile.veinAutoSmelt ? "ON" : "OFF");
			}
			case "toggle_leafdecay" -> {
				profile.fastLeafDecay = !profile.fastLeafDecay;
				HardcoreStateManager.save(server);
				toast = "Fast Leaf Decay: " + (profile.fastLeafDecay ? "ON" : "OFF");
			}
			case "set_vein_max" -> {
				profile.veinMaxBlocks = getInt(obj, "value", profile.veinMaxBlocks);
				profile.sanitize();
				HardcoreStateManager.save(server);
				toast = "Max Chain Blocks: " + profile.veinMaxBlocks;
			}

			// 3. Sneak Grow
			case "toggle_sneakgrow" -> {
				profile.sneakGrow = !profile.sneakGrow;
				HardcoreStateManager.save(server);
				toast = "Sneak Grow: " + (profile.sneakGrow ? "ON" : "OFF");
			}
			case "toggle_sneak_instant" -> {
				profile.sneakGrowInstant = !profile.sneakGrowInstant;
				HardcoreStateManager.save(server);
				toast = "Sneak Grow Mode: " + (profile.sneakGrowInstant ? "Instant Mature" : "Stage-by-Stage");
			}
			case "toggle_sneak_particles" -> {
				profile.sneakGrowParticles = !profile.sneakGrowParticles;
				HardcoreStateManager.save(server);
				toast = "Grow Particles: " + (profile.sneakGrowParticles ? "ON" : "OFF");
			}
			case "set_sneak_radius" -> {
				profile.sneakGrowRadius = getInt(obj, "value", profile.sneakGrowRadius);
				profile.sanitize();
				HardcoreStateManager.save(server);
				toast = "Sneak Grow Radius: " + profile.sneakGrowRadius + " blocks";
			}
			case "set_sneak_speed" -> {
				profile.sneakGrowSpeed = getInt(obj, "value", profile.sneakGrowSpeed);
				profile.sanitize();
				HardcoreStateManager.save(server);
				toast = "Sneak Grow Speed: " + profile.sneakGrowSpeed;
			}

			// 4. Ore Plus+
			case "toggle_oreplus" -> {
				worldData.orePlusEnabled = !worldData.orePlusEnabled;
				HardcoreStateManager.save(server);
				toast = "Ore Plus+ Boost: " + (worldData.orePlusEnabled ? "ON" : "OFF");
			}
			case "set_ore_multiplier" -> {
				worldData.orePlusMultiplier = getInt(obj, "value", worldData.orePlusMultiplier);
				worldData.sanitize();
				HardcoreStateManager.save(server);
				toast = "Ore Plus+ Multiplier: x" + worldData.orePlusMultiplier;
			}
			case "toggle_single_ore" -> {
				String oreKey = getStr(obj, "ore", "");
				if (HardcoreStateManager.ALL_ORE_KEYS.contains(oreKey)) {
					boolean cur = Boolean.TRUE.equals(worldData.oreToggles.getOrDefault(oreKey, true));
					worldData.oreToggles.put(oreKey, !cur);
					HardcoreStateManager.save(server);
					toast = oreKey.toUpperCase() + ": " + (!cur ? "ON" : "OFF");
				}
			}
			case "set_all_ores" -> {
				boolean enable = getBool(obj, "enable", true);
				for (String k : HardcoreStateManager.ALL_ORE_KEYS) {
					worldData.oreToggles.put(k, enable);
				}
				HardcoreStateManager.save(server);
				toast = enable ? "All 12 Ores Enabled" : "All 12 Ores Disabled";
			}

			// 5. Enchantments (Vanilla & Over-Enchant Lv. 25 — Armor, Offhand, Hotbar & Bag!)
			case "toggle_weapon_studio" -> {
				worldData.weaponStudioEnabled = !worldData.weaponStudioEnabled;
				HardcoreStateManager.save(server);
				WeaponDamageFeature.syncAllOnlineInventories(server);
				toast = "⚔ Live Weapon Damage: " + (worldData.weaponStudioEnabled ? "ENABLED" : "PAUSED (Vanilla)");
			}
			case "set_global_weapon_mult" -> {
				double mult = Math.max(0.0, Math.min(10000.0, Math.round(getDouble(obj, "value", 1.0) * 100.0) / 100.0));
				worldData.globalWeaponMultiplier = mult;
				HardcoreStateManager.save(server);
				toast = String.format(Locale.US, "⚔ Global Weapon Multiplier: %.2fx", mult);
			}
			case "select_weapon_slot" -> {
				profile.selectedWeaponSlot = getInt(obj, "slot", -1);
				toast = "";
			}
			case "apply_bag_weapon" -> {
				int slot = getInt(obj, "slot", profile.selectedWeaponSlot);
				double dmg = WeaponDamageFeature.clampDamage(getDouble(obj, "damage", 7.0));
				double spd = WeaponDamageFeature.clampSpeed(getDouble(obj, "speed", 1.6));
				ItemStack target = EnchantFeature.getStackAtSlot(player, slot);
				if (target.isEmpty()) {
					toast = "No item found in selected slot!";
					ok = false;
				} else {
					String itemName = target.getName().getString();
					WeaponDamageFeature.applyCustomStatsToSlot(player, slot, dmg, spd);
					toast = String.format(Locale.US, "⚔ Forged %s → %s DMG · %s SPD",
							itemName, formatStat(dmg), formatStat(spd));
				}
			}
			case "reset_bag_weapon" -> {
				int slot = getInt(obj, "slot", profile.selectedWeaponSlot);
				ItemStack target = EnchantFeature.getStackAtSlot(player, slot);
				if (target.isEmpty()) {
					toast = "No item found in selected slot!";
					ok = false;
				} else {
					String itemName = target.getName().getString();
					WeaponDamageFeature.resetSlotWeaponToDefault(player, slot);
					toast = "↺ Reset " + itemName + " to default stats";
				}
			}
			case "set_catalog_weapon" -> {
				String itemId = getStr(obj, "id", "");
				Identifier parsed = Identifier.tryParse(itemId);
				if (parsed == null || !Registries.ITEM.containsId(parsed)) {
					toast = "Unknown weapon ID: " + itemId;
					ok = false;
				} else {
					Double dmg = (obj.has("damage") && !obj.get("damage").isJsonNull())
							? WeaponDamageFeature.clampDamage(getDouble(obj, "damage", 7.0))
							: null;
					Double spd = (obj.has("speed") && !obj.get("speed").isJsonNull())
							? WeaponDamageFeature.clampSpeed(getDouble(obj, "speed", 1.6))
							: null;
					WeaponDamageFeature.setCatalogWeaponOverride(server, parsed.toString(), dmg, spd);
					String itemName = Registries.ITEM.get(parsed).getName().getString();
					toast = dmg != null
							? ("⚔ Live " + itemName + " Damage → " + formatStat(dmg))
							: ("⚔ Updated " + itemName);
				}
			}
			case "reset_catalog_weapon" -> {
				String itemId = getStr(obj, "id", "");
				Identifier parsed = Identifier.tryParse(itemId);
				if (parsed != null && Registries.ITEM.containsId(parsed)) {
					WeaponDamageFeature.resetCatalogWeaponOverride(server, parsed.toString());
					String itemName = Registries.ITEM.get(parsed).getName().getString();
					toast = "↺ Reset " + itemName + " to Vanilla Damage";
				} else {
					WeaponDamageFeature.resetCatalogWeaponOverride(server, itemId);
					toast = "↺ Reset weapon to Vanilla Damage";
				}
			}
			case "reset_all_catalog_weapons" -> {
				WeaponDamageFeature.resetAllCatalogOverrides(server);
				player.getWorld().playSound(null, player.getBlockPos(),
						SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 0.85F, 1.0F);
				toast = "↺ All Weapon Overrides & Multiplier Reset to Vanilla";
			}

			// Combat Aim (Aimbot, Silent Aim, FOV Circle, Visibility & Mob Exclusion Filter)
			case "silent_melee_hit" -> {
				CombatAimFeature.executeSilentMeleeStrike(player, getInt(obj, "targetId", -1));
				return;
			}
			case "toggle_aimbot" -> {
				profile.aimbotEnabled = !profile.aimbotEnabled;
				HardcoreStateManager.save(server);
				toast = "🎯 Aimbot (Camera Lock): " + (profile.aimbotEnabled ? "ON" : "OFF");
			}
			case "toggle_aim_silent" -> {
				profile.aimSilentEnabled = !profile.aimSilentEnabled;
				HardcoreStateManager.save(server);
				toast = "🎯 Aim Silent (FOV Hit): " + (profile.aimSilentEnabled ? "ON" : "OFF");
			}
			case "toggle_aim_fov_circle" -> {
				profile.aimShowFovCircle = !profile.aimShowFovCircle;
				HardcoreStateManager.save(server);
				toast = "🎯 HUD FOV Circle: " + (profile.aimShowFovCircle ? "SHOWN" : "HIDDEN");
			}
			case "toggle_aim_visible" -> {
				profile.aimVisibleOnly = !profile.aimVisibleOnly;
				HardcoreStateManager.save(server);
				toast = "👁 Visible Target Only: " + (profile.aimVisibleOnly ? "ON (Line-of-Sight)" : "OFF (Ignore Walls)");
			}
			case "toggle_aim_weapon_only" -> {
				profile.aimWeaponOnly = !profile.aimWeaponOnly;
				HardcoreStateManager.save(server);
				toast = "⚔ Hold Weapon Only: " + (profile.aimWeaponOnly ? "ON" : "OFF");
			}
			case "set_aim_fov" -> {
				profile.aimFovDegrees = getInt(obj, "value", profile.aimFovDegrees);
				profile.sanitize();
				HardcoreStateManager.save(server);
				toast = "🎯 FOV Circle Radius: " + profile.aimFovDegrees + "°";
			}
			case "set_aim_speed" -> {
				profile.aimSmoothSpeed = getInt(obj, "value", profile.aimSmoothSpeed);
				profile.sanitize();
				HardcoreStateManager.save(server);
				toast = "🎯 Aimbot Lock Speed: " + profile.aimSmoothSpeed + "%";
			}
			case "set_aim_reach" -> {
				profile.aimMaxReach = getInt(obj, "value", profile.aimMaxReach);
				profile.sanitize();
				HardcoreStateManager.save(server);
				toast = "🎯 Melee Aim Reach: " + profile.aimMaxReach + " blocks";
			}
			case "toggle_aim_protect" -> {
				String key = getStr(obj, "key", "");
				switch (key) {
					case "pets" -> {
						profile.aimProtectPets = !profile.aimProtectPets;
						toast = "🛡 Protect Pets & Tamed: " + (profile.aimProtectPets ? "ON" : "OFF");
					}
					case "villagers" -> {
						profile.aimProtectVillagers = !profile.aimProtectVillagers;
						toast = "🛡 Protect Villagers & Golems: " + (profile.aimProtectVillagers ? "ON" : "OFF");
					}
					case "passive" -> {
						profile.aimProtectPassive = !profile.aimProtectPassive;
						toast = "🛡 Protect Passive Animals: " + (profile.aimProtectPassive ? "ON" : "OFF");
					}
					case "neutral" -> {
						profile.aimProtectNeutral = !profile.aimProtectNeutral;
						toast = "🛡 Protect Unprovoked Neutral: " + (profile.aimProtectNeutral ? "ON" : "OFF");
					}
					case "named" -> {
						profile.aimProtectNamed = !profile.aimProtectNamed;
						toast = "🛡 Protect Named Mobs: " + (profile.aimProtectNamed ? "ON" : "OFF");
					}
					case "players" -> {
						profile.aimProtectPlayers = !profile.aimProtectPlayers;
						toast = "🛡 Protect Players: " + (profile.aimProtectPlayers ? "ON" : "OFF");
					}
				}
				HardcoreStateManager.save(server);
			}
			case "toggle_aim_mob_exclude" -> {
				String mobId = getStr(obj, "id", "").trim();
				Identifier parsed = Identifier.tryParse(mobId);
				if (parsed != null && Registries.ENTITY_TYPE.containsId(parsed)) {
					String norm = parsed.toString();
					String mobName = Registries.ENTITY_TYPE.get(parsed).getName().getString();
					if (profile.aimExcludedMobs.contains(norm)) {
						profile.aimExcludedMobs.remove(norm);
						toast = "⚔ Allowed Target: " + mobName;
					} else {
						profile.aimExcludedMobs.add(norm);
						toast = "🛡 Protected (Jangan Kena): " + mobName;
					}
					HardcoreStateManager.save(server);
				}
			}
			case "clear_aim_mob_excludes" -> {
				profile.aimExcludedMobs.clear();
				HardcoreStateManager.save(server);
				toast = "↺ Custom Mob Exclusion List Cleared";
			}

			case "select_enchant_slot" -> {
				profile.selectedEnchantSlot = getInt(obj, "slot", -1);
				toast = "";
			}
			case "toggle_bypass_conflicts" -> {
				profile.bypassEnchantConflicts = !profile.bypassEnchantConflicts;
				HardcoreStateManager.save(server);
				toast = "God Stack (Bypass Conflicts): " + (profile.bypassEnchantConflicts ? "ON" : "OFF");
			}
			case "enchant_automax" -> {
				boolean over25 = getBool(obj, "over25", false);
				int slot = getInt(obj, "slot", profile.selectedEnchantSlot);
				int count = EnchantFeature.autoMaxSlotItem(player, slot, over25, profile.bypassEnchantConflicts);
				if (count < 0) {
					toast = "No enchantable item found in equipment or inventory!";
					ok = false;
				} else if (count == 0) {
					toast = "No compatible enchantments for this item.";
					ok = false;
				} else {
					toast = "Applied " + count + " enchantments (" + (over25 ? "Over-Enchant Lv. 25" : "Vanilla Max") + ")!";
				}
			}
			case "enchant_automax_all" -> {
				boolean over25 = getBool(obj, "over25", false);
				int items = EnchantFeature.autoMaxAllGear(player, over25, profile.bypassEnchantConflicts);
				if (items <= 0) {
					toast = "No weapons, tools, or armor found in your inventory!";
					ok = false;
				} else {
					toast = "Auto-Maxed " + items + " gear item(s) (" + (over25 ? "Over-Enchant Lv. 25" : "Vanilla Max") + ")!";
				}
			}
			case "enchant_apply" -> {
				String enchId = getStr(obj, "id", "");
				int level = getInt(obj, "level", 1);
				int slot = getInt(obj, "slot", profile.selectedEnchantSlot);
				ok = EnchantFeature.applySingleEnchant(player, slot, enchId, level, profile.bypassEnchantConflicts);
				toast = ok ? ("Applied " + enchId.replace("minecraft:", "") + " " + EnchantFeature.toRoman(level)) : "Select an item first!";
			}
			case "enchant_remove" -> {
				String enchId = getStr(obj, "id", "");
				int slot = getInt(obj, "slot", profile.selectedEnchantSlot);
				ok = EnchantFeature.removeSingleEnchant(player, slot, enchId);
				toast = ok ? ("Removed " + enchId.replace("minecraft:", "")) : "Could not remove enchantment.";
			}
			case "enchant_clear" -> {
				int slot = getInt(obj, "slot", profile.selectedEnchantSlot);
				ok = EnchantFeature.clearTargetEnchants(player, slot);
				toast = ok ? "Cleared all enchantments on selected item." : "Select an item first!";
			}

			// 6. Enchanted Books
			case "give_book" -> {
				String enchId = getStr(obj, "id", "");
				int level = getInt(obj, "level", 1);
				ok = EnchantFeature.giveEnchantedBook(player, enchId, level);
				toast = ok ? ("Received Enchanted Book: " + enchId.replace("minecraft:", "") + " " + EnchantFeature.toRoman(level)) : "Failed to create Enchanted Book.";
			}

			// 7. Repair Workshop
			case "repair" -> {
				String mode = getStr(obj, "mode", "held");
				int count = performRepair(player, mode);
				if (count <= 0) {
					toast = "No damaged items found for '" + mode + "'.";
					ok = false;
				} else {
					player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.85F, 1.15F);
					toast = "Repaired " + count + " item(s) to 100% durability!";
				}
			}

			// 7b. Item Upgrader (Gacha Forge)
			case "upgrader_roll" -> {
				int slot = getInt(obj, "slot", 0);
				int amount = getInt(obj, "amount", 1);
				String targetId = getStr(obj, "targetId", "minecraft:netherite_ingot");
				ItemUpgraderFeature.RollOutcome outcome = ItemUpgraderFeature.executeRoll(
						player,
						profile,
						slot,
						amount,
						targetId,
						profile.bypassEnchantConflicts
				);
				if (!outcome.valid()) {
					ok = false;
					toast = outcome.error();
				} else {
					profile.lastUpgradeRollSeq++;
					profile.pendingRollOutcome = outcome;
					// Leave toast blank so the client's 1.45s Roulette Spin reveals the climax without early spoiler!
					toast = "";
				}
			}

			// 7c. Uncrafter & Enchant Book Extractor
			case "uncraft_execute" -> {
				int slot = getInt(obj, "slot", 0);
				int batches = getInt(obj, "batches", 1);
				boolean splitBooks = getBool(obj, "splitBooks", true);
				String summary = UncraftFeature.executeUncraft(player, slot, batches, splitBooks);
				if (summary == null) {
					ok = false;
					toast = "Cannot uncraft that item right now.";
				} else {
					toast = summary;
				}
			}

			// 8. Potion Effects
			case "effect_apply" -> {
				String effectId = getStr(obj, "id", "minecraft:strength");
				int seconds = getInt(obj, "seconds", 300);
				int amplifier = Math.max(0, Math.min(24, getInt(obj, "amplifier", 0)));
				boolean infinite = getBool(obj, "infinite", false);
				Identifier id = Identifier.tryParse(effectId);
				RegistryEntry<StatusEffect> entry = id == null ? null : Registries.STATUS_EFFECT.getEntry(id).orElse(null);
				if (entry == null) {
					ok = false;
					toast = "Unknown effect: " + effectId;
				} else {
					int ticks = infinite ? StatusEffectInstance.INFINITE : Math.max(20, seconds * 20);
					player.addStatusEffect(new StatusEffectInstance(entry, ticks, amplifier, false, true, true));
					player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_SPLASH_POTION_BREAK, SoundCategory.PLAYERS, 0.8F, 1.2F);
					toast = "Applied " + id.getPath() + " " + EnchantFeature.toRoman(amplifier + 1) + (infinite ? " (∞)" : " (" + seconds + "s)");
				}
			}
			case "effect_clear_negative" -> {
				ProtectionHandler.clearHarmfulEffects(player);
				toast = "Cleared all negative status effects!";
			}
			case "effect_clear_all" -> {
				player.clearStatusEffects();
				toast = "Cleared all active status effects!";
			}

			// 9. XP Manager
			case "xp_action" -> {
				String op = getStr(obj, "op", "add_levels");
				int amount = getInt(obj, "amount", 10);
				switch (op) {
					case "add_levels" -> {
						player.addExperienceLevels(amount);
						toast = (amount >= 0 ? "+" : "") + amount + " XP Levels (Now Lv. " + player.experienceLevel + ")";
					}
					case "set_level" -> {
						int target = Math.max(0, Math.min(10000, amount));
						player.setExperienceLevel(target);
						player.setExperiencePoints(0);
						toast = "Set XP Level to " + target;
					}
					case "reset" -> {
						player.setExperienceLevel(0);
						player.setExperiencePoints(0);
						toast = "Reset XP to Level 0";
					}
				}
				player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8F, 1.2F);
			}

			// 10. Teleport & Locator
			case "tp_back" -> {
				WaypointEntry target = TeleportFeature.teleportBack(player);
				if (target == null) {
					ok = false;
					toast = "No previous teleport position recorded yet.";
				} else {
					toast = "Returned to " + (int) target.x + ", " + (int) target.y + ", " + (int) target.z + " (" + target.dimension.replace("minecraft:", "") + ")";
				}
			}
			case "tp_player" -> {
				String target = getStr(obj, "target", "");
				String name = TeleportFeature.teleportToPlayer(player, target);
				ok = name != null;
				toast = ok ? ("Teleported to " + name) : "Player not found.";
			}
			case "tp_coords" -> {
				String dim = getStr(obj, "dimension", player.getServerWorld().getRegistryKey().getValue().toString());
				double x = getDouble(obj, "x", player.getX());
				double y = getDouble(obj, "y", player.getY());
				double z = getDouble(obj, "z", player.getZ());
				ok = TeleportFeature.teleportToCoords(player, dim, x, y, z);
				toast = "Teleported to " + (int) x + ", " + (int) y + ", " + (int) z;
			}
			case "tp_biome" -> {
				String biomeId = getStr(obj, "id", "minecraft:cherry_grove");
				boolean autoTp = getBool(obj, "autoTp", true);
				BlockPos pos = TeleportFeature.locateAndTeleportBiome(player, biomeId, autoTp);
				if (pos == null) {
					ok = false;
					toast = "Biome not found within 6,400 blocks.";
				} else {
					toast = (autoTp ? "Teleported to " : "Located ") + biomeId.replace("minecraft:", "") + " [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
				}
			}
			case "tp_structure" -> {
				String structId = getStr(obj, "id", "minecraft:ancient_city");
				boolean autoTp = getBool(obj, "autoTp", true);
				BlockPos pos = TeleportFeature.locateAndTeleportStructure(player, structId, autoTp);
				if (pos == null) {
					ok = false;
					toast = "Structure not found nearby.";
				} else {
					toast = (autoTp ? "Teleported to " : "Located ") + structId.replace("minecraft:", "") + " [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
				}
			}

			// 11. Waypoints
			case "waypoint_create" -> {
				String name = getStr(obj, "name", "");
				int color = getInt(obj, "color", 0xFFDC1E36);
				WaypointEntry wp = TeleportFeature.createWaypoint(player, name, color);
				ok = wp != null;
				toast = ok ? ("Saved Waypoint: " + wp.name) : "Waypoint limit (64) reached!";
			}
			case "waypoint_tp" -> {
				String id = getStr(obj, "id", "");
				ok = TeleportFeature.teleportToWaypoint(player, id);
				toast = ok ? "Teleported to Waypoint!" : "Waypoint not found.";
			}
			case "waypoint_delete" -> {
				String id = getStr(obj, "id", "");
				ok = TeleportFeature.deleteWaypoint(player, id);
				toast = ok ? "Deleted Waypoint." : "Waypoint not found.";
			}

			// 12. Quick Console & GameRules
			case "console_gamerule" -> {
				String rule = getStr(obj, "rule", "");
				GameRules gr = server.getGameRules();
				switch (rule) {
					case "keepInventory" -> {
						boolean next = !gr.getBoolean(GameRules.KEEP_INVENTORY);
						gr.get(GameRules.KEEP_INVENTORY).set(next, server);
						toast = "keepInventory: " + next;
					}
					case "mobGriefing" -> {
						boolean next = !gr.getBoolean(GameRules.DO_MOB_GRIEFING);
						gr.get(GameRules.DO_MOB_GRIEFING).set(next, server);
						toast = "mobGriefing: " + next;
					}
					case "daylightCycle" -> {
						boolean next = !gr.getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
						gr.get(GameRules.DO_DAYLIGHT_CYCLE).set(next, server);
						toast = "doDaylightCycle: " + next;
					}
					case "weatherCycle" -> {
						boolean next = !gr.getBoolean(GameRules.DO_WEATHER_CYCLE);
						gr.get(GameRules.DO_WEATHER_CYCLE).set(next, server);
						toast = "doWeatherCycle: " + next;
					}
					case "immediateRespawn" -> {
						boolean next = !gr.getBoolean(GameRules.DO_IMMEDIATE_RESPAWN);
						gr.get(GameRules.DO_IMMEDIATE_RESPAWN).set(next, server);
						toast = "doImmediateRespawn: " + next;
					}
					case "pvp" -> {
						boolean next = !server.isPvpEnabled();
						server.setPvpEnabled(next);
						toast = "PvP: " + next;
					}
				}
			}
			case "console_quick" -> {
				String cmd = getStr(obj, "cmd", "");
				ServerWorld overworld = server.getOverworld();
				switch (cmd) {
					case "day" -> {
						overworld.setTimeOfDay(1000L);
						toast = "Time set to Day (1000)";
					}
					case "noon" -> {
						overworld.setTimeOfDay(6000L);
						toast = "Time set to Noon (6000)";
					}
					case "night" -> {
						overworld.setTimeOfDay(13000L);
						toast = "Time set to Night (13000)";
					}
					case "midnight" -> {
						overworld.setTimeOfDay(18000L);
						toast = "Time set to Midnight (18000)";
					}
					case "weather_clear" -> {
						overworld.setWeather(12000, 0, false, false);
						toast = "Weather set to Clear";
					}
					case "weather_rain" -> {
						overworld.setWeather(0, 12000, true, false);
						toast = "Weather set to Rain";
					}
					case "weather_thunder" -> {
						overworld.setWeather(0, 12000, true, true);
						toast = "Weather set to Thunder";
					}
					case "heal_feed" -> {
						player.setHealth(player.getMaxHealth());
						player.getHungerManager().setFoodLevel(20);
						player.getHungerManager().setSaturationLevel(20.0F);
						player.setFireTicks(0);
						ProtectionHandler.clearHarmfulEffects(player);
						toast = "Fully Healed & Fed!";
					}
					case "clear_lag" -> {
						int cleared = 0;
						for (ServerWorld w : server.getWorlds()) {
							Box box = new Box(player.getBlockPos()).expand(256);
							List<ItemEntity> drops = w.getEntitiesByClass(ItemEntity.class, box, Entity::isAlive);
							for (ItemEntity item : drops) {
								if (item.getCommandTags().contains(ReturnByDeathFeature.GHOST_DROP_TAG)) continue;
								item.discard();
								cleared++;
							}
						}
						toast = "Cleared " + cleared + " ground item entities!";
					}
				}
			}
			case "set_gamemode" -> {
				String mode = getStr(obj, "mode", "survival");
				String msg = applyGameMode(player, mode);
				if (msg == null) {
					ok = false;
					toast = "Unknown gamemode: " + mode;
				} else {
					toast = msg;
				}
			}
			case "console_run" -> {
				String rawCmd = getStr(obj, "command", "").trim();
				if (rawCmd.isEmpty()) {
					ok = false;
					toast = "Enter a command first.";
				} else {
					String normalized = (rawCmd.startsWith("/") ? rawCmd.substring(1) : rawCmd).trim().toLowerCase(Locale.ROOT);
					if (normalized.equals("gamemode hardcore")) {
						toast = applyGameMode(player, "hardcore");
					} else {
						ServerCommandSource elevated = player.getCommandSource().withLevel(4);
						server.getCommandManager().executeWithPrefix(elevated, rawCmd);
						toast = "Executed: " + (rawCmd.startsWith("/") ? rawCmd : "/" + rawCmd);
					}
				}
			}
		}

		HardcoreNetworking.sendSyncState(player, toast, ok);
	}

	public static String applyGameMode(ServerPlayerEntity player, String rawMode) {
		MinecraftServer server = player.getServer();
		if (server == null || rawMode == null) {
			return null;
		}
		String mode = rawMode.trim().toLowerCase(Locale.ROOT);
		LevelInfo info = server.getSaveProperties().getLevelInfo();

		switch (mode) {
			case "creative" -> {
				player.changeGameMode(GameMode.CREATIVE);
				player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8F, 1.25F);
				return "✦ GameMode: Creative";
			}
			case "survival" -> {
				boolean wasHardcore = info.isHardcore();
				setWorldHardcore(server, false);
				server.setDifficultyLocked(false);
				player.changeGameMode(GameMode.SURVIVAL);
				player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8F, 1.15F);
				if (wasHardcore) {
					persistWorldProperties(server);
				}
				broadcastWorldHardcoreSync(server);
				return "✦ GameMode: Survival (Standard Hearts)";
			}
			case "hardcore" -> {
				boolean changedWorldFlag = !info.isHardcore();
				setWorldHardcore(server, true);
				server.setDifficulty(Difficulty.HARD, true);
				server.setDifficultyLocked(true);
				player.changeGameMode(GameMode.SURVIVAL);
				player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.85F, 1.15F);
				if (changedWorldFlag) {
					persistWorldProperties(server);
				}
				broadcastWorldHardcoreSync(server);
				return "☠ GameMode: HARDCORE! (Hardcore Hearts Active)";
			}
			case "spectator" -> {
				player.changeGameMode(GameMode.SPECTATOR);
				player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7F, 1.35F);
				return "✦ GameMode: Spectator";
			}
			case "adventure" -> {
				player.changeGameMode(GameMode.ADVENTURE);
				player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8F, 0.95F);
				return "✦ GameMode: Adventure";
			}
			default -> {
				return null;
			}
		}
	}

	/**
	 * Flips the REAL hardcore flag stored on the world's LevelProperties.
	 * SaveProperties#getLevelInfo() hands out a copy (withCopiedGameRules()), so writing to it
	 * silently did nothing before. This resolves the genuine LevelInfo instance instead, which
	 * makes the hardcore hearts, the "Game Over!" death screen and the level.dat entry all
	 * switch live while in-game.
	 */
	private static void setWorldHardcore(MinecraftServer server, boolean hardcore) {
		SaveProperties saveProperties = server.getSaveProperties();
		LevelInfo realInfo = null;
		if (saveProperties instanceof LevelProperties levelProperties) {
			realInfo = ((LevelPropertiesAccessor) levelProperties).hardcoreTools$getLevelInfo();
		}
		if (realInfo == null) {
			realInfo = saveProperties.getLevelInfo();
		}
		((LevelInfoAccessor) (Object) realInfo).hardcoreTools$setHardcore(hardcore);
	}

	/**
	 * Writes level.dat immediately so the toggle survives a crash / force-close and shows up as
	 * "Hardcore Mode" in the singleplayer world list.
	 */
	private static void persistWorldProperties(MinecraftServer server) {
		try {
			server.saveAll(false, true, true);
		} catch (Exception ignored) {
		}
	}

	private static void broadcastWorldHardcoreSync(MinecraftServer server) {
		for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
			HardcoreNetworking.sendSyncState(other, "", true);
		}
	}

	private static int performRepair(ServerPlayerEntity player, String mode) {
		int repaired = 0;
		switch (mode) {
			case "held" -> {
				ItemStack held = player.getMainHandStack();
				if (!held.isEmpty() && held.isDamaged()) {
					held.setDamage(0);
					repaired++;
				}
			}
			case "armor" -> {
				for (ItemStack s : player.getInventory().armor) {
					if (!s.isEmpty() && s.isDamaged()) {
						s.setDamage(0);
						repaired++;
					}
				}
				ItemStack off = player.getInventory().offHand.get(0);
				if (!off.isEmpty() && off.isDamaged()) {
					off.setDamage(0);
					repaired++;
				}
			}
			case "hotbar" -> {
				for (int i = 0; i < 9; i++) {
					ItemStack s = player.getInventory().main.get(i);
					if (!s.isEmpty() && s.isDamaged()) {
						s.setDamage(0);
						repaired++;
					}
				}
			}
			case "all" -> {
				for (ItemStack s : player.getInventory().main) {
					if (!s.isEmpty() && s.isDamaged()) {
						s.setDamage(0);
						repaired++;
					}
				}
				for (ItemStack s : player.getInventory().armor) {
					if (!s.isEmpty() && s.isDamaged()) {
						s.setDamage(0);
						repaired++;
					}
				}
				for (ItemStack s : player.getInventory().offHand) {
					if (!s.isEmpty() && s.isDamaged()) {
						s.setDamage(0);
						repaired++;
					}
				}
			}
		}
		return repaired;
	}

	private static String getStr(JsonObject o, String key, String def) {
		return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : def;
	}

	private static int getInt(JsonObject o, String key, int def) {
		try {
			return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsInt() : def;
		} catch (Exception e) {
			return def;
		}
	}

	private static double getDouble(JsonObject o, String key, double def) {
		try {
			return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsDouble() : def;
		} catch (Exception e) {
			return def;
		}
	}

	private static boolean getBool(JsonObject o, String key, boolean def) {
		try {
			return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsBoolean() : def;
		} catch (Exception e) {
			return def;
		}
	}

	private static String formatStat(double val) {
		if (Math.abs(val - Math.rint(val)) < 1e-4) {
			return Long.toString(Math.round(val));
		}
		return String.format(Locale.US, "%.2f", val).replaceAll("0+$", "").replaceAll("\\.$", "");
	}
}
