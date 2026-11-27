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
	public transient long lastUpgradeRollSeq = 0L;
	public transient ItemUpgraderFeature.RollOutcome pendingRollOutcome = null;

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
		if (waypoints == null) waypoints = new ArrayList<>();
	}
}
