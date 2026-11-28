package com.axell.hardcoretools.state;

import java.util.ArrayList;
import java.util.List;

import com.axell.hardcoretools.feature.ItemUpgraderFeature;

public class PlayerProfile {
	// Protection & Vision
	public boolean immortal = false;
	public boolean fullbright = false;
	public boolean autoTotem = false;
	public boolean autoTotemOffhand = true;
	public boolean panicSaver = true;
	public int panicHpThreshold = 6; // 3.0 hearts
	public boolean returnByDeath = false;
	public int returnByDeathSeconds = 60; // 15..60s (1 minute default)
	public int returnByDeathCooldown = 0; // 0..300s (0 = always ready)
	public transient long returnByDeathReadyTick = 0L;
	public boolean timeStopEnabled = false;

	// Vein Miner & Tree Capitator
	public boolean veinMiner = false;
	public boolean treeCapitator = false;
	public int veinMaxBlocks = 64;
	public boolean veinAutoCollect = true;
	public boolean veinAutoSmelt = false;
	public boolean fastLeafDecay = true;

	// Sneak Grow
	public boolean sneakGrow = false;
	public boolean sneakGrowInstant = false;
	public int sneakGrowRadius = 4;
	public int sneakGrowSpeed = 5;
	public boolean sneakGrowParticles = true;

	// Enchantments & Upgrader
	public boolean bypassEnchantConflicts = false;
	public double upgraderPity = 0.0;
	public int upgraderFailStreak = 0;
	public transient int selectedEnchantSlot = -1;
	public transient int selectedWeaponSlot = -1;
	public transient long lastUpgradeRollSeq = 0L;
	public transient ItemUpgraderFeature.RollOutcome pendingRollOutcome = null;

	// Combat Aim (Aimbot, Silent Aim, FOV Circle, Line-of-Sight & Mob Exclusion Filter)
	public boolean aimbotEnabled = false;
	public boolean aimSilentEnabled = false;
	public boolean aimShowFovCircle = true;
	public boolean aimVisibleOnly = true;
	public boolean aimWeaponOnly = true;
	public int aimFovDegrees = 30;   // 5..90 degrees half-angle cone
	public int aimSmoothSpeed = 50;  // 5..100 (100 = instant lock)
	public int aimMaxReach = 6;      // 3..16 blocks melee reach (ranged auto up to 48m)
	public boolean aimProtectPets = true;
	public boolean aimProtectVillagers = true;
	public boolean aimProtectPassive = true;
	public boolean aimProtectNeutral = false;
	public boolean aimProtectNamed = true;
	public boolean aimProtectPlayers = true;
	public List<String> aimExcludedMobs = new ArrayList<>();

	// Misc & UI Theme
	public int uiTheme = 0; // 0=Crimson Obsidian, 1=Witch's Amethyst, 2=Cyber Emerald, 3=Solar Gold, 4=Arctic Sapphire
	public boolean welcomed = false;
	public WaypointEntry lastTeleportOrigin = null;
	public List<WaypointEntry> waypoints = new ArrayList<>();

	public void sanitize() {
		if (uiTheme < 0 || uiTheme > 4) uiTheme = 0;
		if (panicHpThreshold < 2) panicHpThreshold = 2;
		if (panicHpThreshold > 18) panicHpThreshold = 18;
		if (returnByDeathSeconds < 15) returnByDeathSeconds = 15;
		if (returnByDeathSeconds > 60) returnByDeathSeconds = 60;
		if (returnByDeathCooldown < 0) returnByDeathCooldown = 0;
		if (returnByDeathCooldown > 300) returnByDeathCooldown = 300;
		if (veinMaxBlocks < 4) veinMaxBlocks = 4;
		if (veinMaxBlocks > 128) veinMaxBlocks = 128;
		if (sneakGrowRadius < 1) sneakGrowRadius = 1;
		if (sneakGrowRadius > 10) sneakGrowRadius = 10;
		if (sneakGrowSpeed < 1) sneakGrowSpeed = 1;
		if (sneakGrowSpeed > 20) sneakGrowSpeed = 20;
		if (Double.isNaN(upgraderPity) || upgraderPity < 0.0) upgraderPity = 0.0;
		if (upgraderPity > 100.0) upgraderPity = 100.0;
		if (upgraderFailStreak < 0) upgraderFailStreak = 0;
		if (aimFovDegrees < 5) aimFovDegrees = 5;
		if (aimFovDegrees > 90) aimFovDegrees = 90;
		if (aimSmoothSpeed < 5) aimSmoothSpeed = 5;
		if (aimSmoothSpeed > 100) aimSmoothSpeed = 100;
		if (aimMaxReach < 3) aimMaxReach = 3;
		if (aimMaxReach > 16) aimMaxReach = 16;
		if (aimExcludedMobs == null) aimExcludedMobs = new ArrayList<>();
		aimExcludedMobs.removeIf(s -> s == null || s.isBlank());
		if (waypoints == null) waypoints = new ArrayList<>();
	}
}
