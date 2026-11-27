package com.axell.hardcoretools.feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.axell.hardcoretools.state.PlayerProfile;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public final class ItemUpgraderFeature {
	public static final double MIN_CHANCE = 0.5D;
	public static final double MAX_CHANCE = 95.0D;
	public static final double MAX_PITY = 100.0D;
	public static final double SOFT_PITY_THRESHOLD = 70.0D;
	private static final double FORGE_TAX_FACTOR = 1.12D;

	public static final List<String> FEATURED_TARGETS = List.of(
			"minecraft:netherite_ingot",
			"minecraft:heavy_core",
			"minecraft:mace",
			"minecraft:enchanted_golden_apple",
			"minecraft:elytra",
			"minecraft:totem_of_undying",
			"minecraft:nether_star",
			"minecraft:beacon",
			"minecraft:trident",
			"minecraft:netherite_upgrade_smithing_template",
			"minecraft:diamond",
			"minecraft:emerald",
			"minecraft:ancient_debris",
			"minecraft:netherite_scrap",
			"minecraft:echo_shard",
			"minecraft:ominous_trial_key",
			"minecraft:shulker_shell",
			"minecraft:golden_apple",
			"minecraft:wither_skeleton_skull",
			"minecraft:heart_of_the_sea",
			"minecraft:conduit",
			"minecraft:sponge",
			"minecraft:dragon_breath",
			"minecraft:ghast_tear",
			"minecraft:breeze_rod",
			"minecraft:blaze_rod",
			"minecraft:ender_pearl",
			"minecraft:slime_ball",
			"minecraft:gold_ingot",
			"minecraft:iron_ingot",
			"minecraft:quartz",
			"minecraft:amethyst_shard",
			"minecraft:experience_bottle",
			"minecraft:wind_charge",
			"minecraft:netherite_sword",
			"minecraft:netherite_pickaxe",
			"minecraft:netherite_axe",
			"minecraft:netherite_shovel",
			"minecraft:netherite_helmet",
			"minecraft:netherite_chestplate",
			"minecraft:netherite_leggings",
			"minecraft:netherite_boots",
			"minecraft:diamond_sword",
			"minecraft:diamond_pickaxe",
			"minecraft:diamond_axe",
			"minecraft:diamond_helmet",
			"minecraft:diamond_chestplate",
			"minecraft:diamond_leggings",
			"minecraft:diamond_boots"
	);

	private static final Set<String> BLOCKED_TARGET_PATHS = Set.of(
			"air",
			"bedrock",
			"barrier",
			"command_block",
			"chain_command_block",
			"repeating_command_block",
			"command_block_minecart",
			"structure_block",
			"structure_void",
			"jigsaw",
			"debug_stick",
			"light",
			"knowledge_book",
			"spawner",
			"trial_spawner",
			"vault",
			"end_portal_frame",
			"reinforced_deepslate",
			"petrified_oak_slab",
			"player_head",
			"frosted_ice",
			"farmland",
			"dirt_path",
			"chorus_plant",
			"budding_amethyst",
			"infested_stone",
			"infested_cobblestone",
			"infested_stone_bricks",
			"infested_deepslate"
	);

	private static final Map<Item, Double> EXACT_POINTS = new HashMap<>();

	static {
		// Mythic & Endgame
		EXACT_POINTS.put(Items.DRAGON_EGG, 5000.0);
		EXACT_POINTS.put(Items.MACE, 4200.0);
		EXACT_POINTS.put(Items.ENCHANTED_GOLDEN_APPLE, 3800.0);
		EXACT_POINTS.put(Items.HEAVY_CORE, 3600.0);
		EXACT_POINTS.put(Items.BEACON, 3400.0);
		EXACT_POINTS.put(Items.NETHER_STAR, 3200.0);
		EXACT_POINTS.put(Items.NETHERITE_BLOCK, 25200.0);
		EXACT_POINTS.put(Items.NETHERITE_INGOT, 2800.0);

		// Netherite Gear
		EXACT_POINTS.put(Items.NETHERITE_CHESTPLATE, 4200.0);
		EXACT_POINTS.put(Items.NETHERITE_LEGGINGS, 4000.0);
		EXACT_POINTS.put(Items.NETHERITE_PICKAXE, 3800.0);
		EXACT_POINTS.put(Items.NETHERITE_AXE, 3800.0);
		EXACT_POINTS.put(Items.NETHERITE_SWORD, 3600.0);
		EXACT_POINTS.put(Items.NETHERITE_HELMET, 3600.0);
		EXACT_POINTS.put(Items.NETHERITE_BOOTS, 3500.0);
		EXACT_POINTS.put(Items.NETHERITE_SHOVEL, 3300.0);
		EXACT_POINTS.put(Items.NETHERITE_HOE, 3300.0);

		// Legendary & Boss / Rare Loot
		EXACT_POINTS.put(Items.ELYTRA, 2400.0);
		EXACT_POINTS.put(Items.DRAGON_HEAD, 1800.0);
		EXACT_POINTS.put(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1500.0);
		EXACT_POINTS.put(Items.CONDUIT, 1500.0);
		EXACT_POINTS.put(Items.TRIDENT, 1400.0);
		EXACT_POINTS.put(Items.WITHER_SKELETON_SKULL, 1100.0);
		EXACT_POINTS.put(Items.HEART_OF_THE_SEA, 1100.0);
		EXACT_POINTS.put(Items.TOTEM_OF_UNDYING, 950.0);
		EXACT_POINTS.put(Items.SHULKER_BOX, 900.0);

		// Diamond Gear
		EXACT_POINTS.put(Items.DIAMOND_CHESTPLATE, 2200.0);
		EXACT_POINTS.put(Items.DIAMOND_LEGGINGS, 1950.0);
		EXACT_POINTS.put(Items.DIAMOND_HELMET, 1450.0);
		EXACT_POINTS.put(Items.DIAMOND_BOOTS, 1200.0);
		EXACT_POINTS.put(Items.DIAMOND_PICKAXE, 950.0);
		EXACT_POINTS.put(Items.DIAMOND_AXE, 950.0);
		EXACT_POINTS.put(Items.DIAMOND_SWORD, 720.0);
		EXACT_POINTS.put(Items.DIAMOND_HOE, 650.0);
		EXACT_POINTS.put(Items.DIAMOND_SHOVEL, 420.0);

		// High Resources & Compact Blocks
		EXACT_POINTS.put(Items.DIAMOND_BLOCK, 2700.0);
		EXACT_POINTS.put(Items.EMERALD_BLOCK, 810.0);
		EXACT_POINTS.put(Items.NETHERITE_SCRAP, 680.0);
		EXACT_POINTS.put(Items.ANCIENT_DEBRIS, 650.0);
		EXACT_POINTS.put(Items.OMINOUS_TRIAL_KEY, 550.0);
		EXACT_POINTS.put(Items.ECHO_SHARD, 480.0);
		EXACT_POINTS.put(Items.GOLD_BLOCK, 450.0);
		EXACT_POINTS.put(Items.RAW_GOLD_BLOCK, 378.0);
		EXACT_POINTS.put(Items.GOLDEN_APPLE, 420.0);
		EXACT_POINTS.put(Items.SHULKER_SHELL, 380.0);
		EXACT_POINTS.put(Items.SPONGE, 350.0);
		EXACT_POINTS.put(Items.WET_SPONGE, 340.0);
		EXACT_POINTS.put(Items.ENCHANTED_BOOK, 340.0);
		EXACT_POINTS.put(Items.DRAGON_BREATH, 320.0);
		EXACT_POINTS.put(Items.DIAMOND, 300.0);
		EXACT_POINTS.put(Items.TRIAL_KEY, 280.0);
		EXACT_POINTS.put(Items.GHAST_TEAR, 260.0);
		EXACT_POINTS.put(Items.IRON_BLOCK, 225.0);
		EXACT_POINTS.put(Items.RAW_IRON_BLOCK, 180.0);
		EXACT_POINTS.put(Items.NAUTILUS_SHELL, 180.0);
		EXACT_POINTS.put(Items.EXPERIENCE_BOTTLE, 120.0);
		EXACT_POINTS.put(Items.LAPIS_BLOCK, 108.0);
		EXACT_POINTS.put(Items.REDSTONE_BLOCK, 90.0);
		EXACT_POINTS.put(Items.COPPER_BLOCK, 90.0);
		EXACT_POINTS.put(Items.COAL_BLOCK, 72.0);

		// Mid Resources
		EXACT_POINTS.put(Items.BREEZE_ROD, 95.0);
		EXACT_POINTS.put(Items.ENDER_EYE, 95.0);
		EXACT_POINTS.put(Items.EMERALD, 90.0);
		EXACT_POINTS.put(Items.TURTLE_SCUTE, 90.0);
		EXACT_POINTS.put(Items.BLAZE_ROD, 65.0);
		EXACT_POINTS.put(Items.PHANTOM_MEMBRANE, 65.0);
		EXACT_POINTS.put(Items.CRYING_OBSIDIAN, 65.0);
		EXACT_POINTS.put(Items.ARMADILLO_SCUTE, 55.0);
		EXACT_POINTS.put(Items.GOLD_INGOT, 50.0);
		EXACT_POINTS.put(Items.ENDER_PEARL, 48.0);
		EXACT_POINTS.put(Items.SLIME_BALL, 45.0);
		EXACT_POINTS.put(Items.GOLDEN_CARROT, 45.0);
		EXACT_POINTS.put(Items.RAW_GOLD, 42.0);
		EXACT_POINTS.put(Items.OBSIDIAN, 40.0);
		EXACT_POINTS.put(Items.MAGMA_CREAM, 38.0);

		// Uncommon & Everyday Mining
		EXACT_POINTS.put(Items.IRON_INGOT, 25.0);
		EXACT_POINTS.put(Items.WIND_CHARGE, 22.0);
		EXACT_POINTS.put(Items.RAW_IRON, 20.0);
		EXACT_POINTS.put(Items.PRISMARINE_CRYSTALS, 20.0);
		EXACT_POINTS.put(Items.PRISMARINE_SHARD, 18.0);
		EXACT_POINTS.put(Items.AMETHYST_SHARD, 18.0);
		EXACT_POINTS.put(Items.GUNPOWDER, 18.0);
		EXACT_POINTS.put(Items.LEATHER, 16.0);
		EXACT_POINTS.put(Items.QUARTZ, 15.0);
		EXACT_POINTS.put(Items.GLOWSTONE_DUST, 14.0);
		EXACT_POINTS.put(Items.LAPIS_LAZULI, 12.0);
		EXACT_POINTS.put(Items.REDSTONE, 10.0);
		EXACT_POINTS.put(Items.COPPER_INGOT, 10.0);
		EXACT_POINTS.put(Items.BONE, 10.0);
		EXACT_POINTS.put(Items.STRING, 10.0);
		EXACT_POINTS.put(Items.SPIDER_EYE, 10.0);
		EXACT_POINTS.put(Items.COAL, 8.0);
		EXACT_POINTS.put(Items.CHARCOAL, 8.0);
		EXACT_POINTS.put(Items.RAW_COPPER, 8.0);
		EXACT_POINTS.put(Items.GOLD_NUGGET, 5.5);
		EXACT_POINTS.put(Items.IRON_NUGGET, 2.8);
	}

	private ItemUpgraderFeature() {
	}

	public record BagOfferSlot(int slot, ItemStack sample, int totalCount, int enchantCount) {
	}

	public record RollOutcome(
			boolean valid,
			String error,
			boolean won,
			boolean pityTriggered,
			double chance,
			double rolled,
			String targetId,
			String targetName,
			int sacrificedAmount,
			int xpRefund,
			int inheritedEnchants,
			double pityBefore,
			double pityAfter,
			double pityGain
	) {
	}

	public static boolean isAllowedTarget(Item item) {
		if (item == null || item == Items.AIR) {
			return false;
		}
		Identifier id = Registries.ITEM.getId(item);
		String p = id.getPath();
		if (BLOCKED_TARGET_PATHS.contains(p) || p.endsWith("_spawn_egg")) {
			return false;
		}
		return true;
	}

	public static double getBaseItemPoints(Item item) {
		if (item == null || item == Items.AIR) {
			return 1.0D;
		}
		Double exact = EXACT_POINTS.get(item);
		if (exact != null) {
			return exact;
		}

		Identifier id = Registries.ITEM.getId(item);
		String p = id.getPath();
		if (p.contains("netherite")) return 2800.0;
		if (p.endsWith("_shulker_box")) return 900.0;
		if (p.endsWith("_smithing_template")) return 850.0;
		if (p.startsWith("music_disc_")) return 520.0;
		if (p.contains("diamond")) return 320.0;
		if (p.contains("emerald")) return 95.0;
		if (p.startsWith("iron_")) return 130.0;
		if (p.startsWith("golden_")) return 150.0;
		if (p.startsWith("chainmail_")) return 180.0;
		if (p.contains("bow") || p.contains("shield") || p.contains("fishing_rod") || p.contains("brush")) return 75.0;
		if (p.endsWith("_ore")) return 60.0;
		if (p.endsWith("_ingot")) return 40.0;
		if (p.startsWith("cooked_") || p.equals("bread") || p.equals("pumpkin_pie")) return 12.0;
		if (p.endsWith("_log") || p.endsWith("_stem") || p.endsWith("_wood")) return 6.0;
		if (p.endsWith("_planks") || p.equals("stick") || p.equals("rotten_flesh") || p.endsWith("_seeds")) return 1.5;
		if (p.equals("cobblestone") || p.equals("cobbled_deepslate") || p.equals("dirt") || p.equals("netherrack")
				|| p.equals("sand") || p.equals("gravel") || p.equals("stone") || p.equals("tuff") || p.equals("andesite")
				|| p.equals("diorite") || p.equals("granite")) {
			return 1.5;
		}
		if (item instanceof BlockItem) {
			return 3.5;
		}
		return 8.0;
	}

	public static double getStackUnitPoints(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0.0D;
		}
		double pts = getBaseItemPoints(stack.getItem());

		ItemEnchantmentsComponent comp = getEnchantComponent(stack);
		if (comp.getSize() > 0) {
			for (RegistryEntry<Enchantment> entry : comp.getEnchantments()) {
				int lv = comp.getLevel(entry);
				pts += 60.0 + (Math.min(25, lv) * 35.0);
			}
		}

		if (stack.isDamageable() && stack.getMaxDamage() > 0 && stack.isDamaged()) {
			double durabilityRatio = Math.max(0.25D, 1.0D - (stack.getDamage() / (double) stack.getMaxDamage()) * 0.5D);
			pts *= durabilityRatio;
		}
		return pts;
	}

	public static double calculateWinChance(ItemStack inputSample, int count, Item targetItem) {
		if (inputSample == null || inputSample.isEmpty() || targetItem == null || targetItem == Items.AIR || count <= 0) {
			return 0.0D;
		}
		double offered = getStackUnitPoints(inputSample) * count;
		double required = Math.max(1.0D, getBaseItemPoints(targetItem) * FORGE_TAX_FACTOR);
		double pct = (offered / required) * 100.0D;
		double clamped = Math.max(MIN_CHANCE, Math.min(MAX_CHANCE, pct));
		return round1(clamped);
	}

	public static double calculatePityBonusChance(double currentPity) {
		if (Double.isNaN(currentPity) || currentPity <= 0.0D) {
			return 0.0D;
		}
		if (currentPity >= MAX_PITY) {
			return 100.0D;
		}
		if (currentPity < SOFT_PITY_THRESHOLD) {
			// Gentle blessing: up to +7.0% bonus chance from 0%..70% pity
			return round1(currentPity * 0.10D);
		}
		// Genshin-style Soft Pity surge (70%..99.9%): ramps from +7.0% up to +25.0% bonus chance!
		double softProgress = (currentPity - SOFT_PITY_THRESHOLD) / (MAX_PITY - SOFT_PITY_THRESHOLD);
		return round1(7.0D + softProgress * 18.0D);
	}

	public static double calculateEffectiveWinChance(ItemStack inputSample, int count, Item targetItem, double currentPity) {
		if (inputSample == null || inputSample.isEmpty() || targetItem == null || targetItem == Items.AIR || count <= 0) {
			return 0.0D;
		}
		if (currentPity >= MAX_PITY) {
			return 100.0D;
		}
		double base = calculateWinChance(inputSample, count, targetItem);
		double boosted = Math.min(MAX_CHANCE, base + calculatePityBonusChance(currentPity));
		return round1(boosted);
	}

	public static double calculateFailPityGain(ItemStack inputSample, int count, Item targetItem) {
		if (inputSample == null || inputSample.isEmpty() || targetItem == null || targetItem == Items.AIR || count <= 0) {
			return 0.0D;
		}
		double offered = getStackUnitPoints(inputSample) * count;
		double required = Math.max(1.0D, getBaseItemPoints(targetItem) * FORGE_TAX_FACTOR);
		double rawPct = (offered / required) * 100.0D;
		double gain = 6.5D + rawPct * 0.55D;
		// Scale down ultra-cheap 1x dirt/cobble offers so pity cannot be cheesed in 5 clicks with junk blocks
		if (offered < 15.0D) {
			gain *= Math.max(0.08D, offered / 15.0D);
		}
		double clamped = Math.max(0.5D, Math.min(50.0D, gain));
		return round1(clamped);
	}

	private static double round1(double val) {
		return Math.round(val * 10.0D) / 10.0D;
	}

	public static int calculateSmartAmountForMaxChance(ItemStack inputSample, int maxOwned, Item targetItem) {
		if (inputSample == null || inputSample.isEmpty() || targetItem == null || maxOwned <= 1) {
			return 1;
		}
		double unitPts = Math.max(0.1D, getStackUnitPoints(inputSample));
		double targetPtsFor95 = (MAX_CHANCE / 100.0D) * (getBaseItemPoints(targetItem) * FORGE_TAX_FACTOR);
		int needed = (int) Math.ceil(targetPtsFor95 / unitPts);
		return Math.max(1, Math.min(maxOwned, needed));
	}

	public static int calculateFailXp(ItemStack inputSample, int count) {
		if (inputSample == null || inputSample.isEmpty() || count <= 0) {
			return 0;
		}
		double totalPts = getStackUnitPoints(inputSample) * count;
		return Math.max(3, Math.min(2500, (int) Math.round(totalPts * 0.12D)));
	}

	public static List<BagOfferSlot> collectPlayerOffers(PlayerInventory inv) {
		List<BagOfferSlot> offers = new ArrayList<>();
		List<ItemStack> seenStackables = new ArrayList<>();

		// Slots 0..35 = inv.main, Slot 40 = offhand
		int[] scanOrder = new int[37];
		for (int i = 0; i < 36; i++) scanOrder[i] = i;
		scanOrder[36] = 40;

		for (int slot : scanOrder) {
			ItemStack stack = getBagStack(inv, slot);
			if (stack.isEmpty()) continue;

			int enchCount = getEnchantComponent(stack).getSize();
			boolean uniqueGear = stack.getMaxCount() == 1 || enchCount > 0 || stack.isDamaged();
			if (uniqueGear) {
				offers.add(new BagOfferSlot(slot, stack.copyWithCount(1), 1, enchCount));
			} else {
				boolean alreadyGrouped = false;
				for (ItemStack prev : seenStackables) {
					if (ItemStack.areItemsAndComponentsEqual(prev, stack)) {
						alreadyGrouped = true;
						break;
					}
				}
				if (!alreadyGrouped) {
					seenStackables.add(stack.copyWithCount(1));
					int total = countMatchingInBag(inv, slot);
					offers.add(new BagOfferSlot(slot, stack.copyWithCount(1), total, 0));
				}
			}
		}
		return offers;
	}

	public static ItemStack getBagStack(PlayerInventory inv, int slot) {
		if (inv == null) return ItemStack.EMPTY;
		if (slot >= 0 && slot < inv.main.size()) {
			return inv.main.get(slot);
		}
		if (slot == 40 && !inv.offHand.isEmpty()) {
			return inv.offHand.get(0);
		}
		return ItemStack.EMPTY;
	}

	public static int countMatchingInBag(PlayerInventory inv, int selectedSlot) {
		ItemStack ref = getBagStack(inv, selectedSlot);
		if (ref.isEmpty()) return 0;
		int enchCount = getEnchantComponent(ref).getSize();
		if (ref.getMaxCount() == 1 || enchCount > 0 || ref.isDamaged()) {
			return 1;
		}
		int sum = 0;
		for (int i = 0; i < inv.main.size(); i++) {
			ItemStack s = inv.main.get(i);
			if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, ref)) {
				sum += s.getCount();
			}
		}
		if (!inv.offHand.isEmpty()) {
			ItemStack off = inv.offHand.get(0);
			if (!off.isEmpty() && ItemStack.areItemsAndComponentsEqual(off, ref)) {
				sum += off.getCount();
			}
		}
		return sum;
	}

	public static RollOutcome executeRoll(ServerPlayerEntity player, PlayerProfile profile, int slotIndex, int requestedAmount,
										  String targetItemId, boolean bypassConflicts) {
		PlayerInventory inv = player.getInventory();
		ItemStack ref = getBagStack(inv, slotIndex);
		if (ref.isEmpty()) {
			return new RollOutcome(false, "Select a sacrifice item from your bag first!", false, false, 0, 0, targetItemId, "", 0, 0, 0, 0, 0, 0);
		}

		Identifier targetIdent = Identifier.tryParse(targetItemId);
		if (targetIdent == null || !Registries.ITEM.containsId(targetIdent)) {
			return new RollOutcome(false, "Invalid target item.", false, false, 0, 0, targetItemId, "", 0, 0, 0, 0, 0, 0);
		}
		Item targetItem = Registries.ITEM.get(targetIdent);
		if (!isAllowedTarget(targetItem)) {
			return new RollOutcome(false, "That item cannot be forged.", false, false, 0, 0, targetItemId, "", 0, 0, 0, 0, 0, 0);
		}

		ItemStack sample = ref.copyWithCount(1);
		int sampleEnchants = getEnchantComponent(sample).getSize();
		if (sample.getItem() == targetItem && sampleEnchants == 0) {
			return new RollOutcome(false, "Choose a different target item to upgrade into!", false, false, 0, 0, targetItemId, "", 0, 0, 0, 0, 0, 0);
		}

		int available = countMatchingInBag(inv, slotIndex);
		if (available <= 0) {
			return new RollOutcome(false, "Item no longer in inventory.", false, false, 0, 0, targetItemId, "", 0, 0, 0, 0, 0, 0);
		}
		int amount = Math.max(1, Math.min(available, requestedAmount));
		double pityBefore = profile == null ? 0.0D : Math.max(0.0D, Math.min(MAX_PITY, profile.upgraderPity));
		boolean guaranteedByPity = pityBefore >= MAX_PITY;
		double chance = calculateEffectiveWinChance(sample, amount, targetItem, pityBefore);
		int failXp = calculateFailXp(sample, amount);

		// Consume the offered items immediately (prevents ESC-cancel exploits)
		consumeFromBag(inv, slotIndex, sample, amount);
		player.playerScreenHandler.sendContentUpdates();

		ServerWorld world = player.getServerWorld();
		boolean won;
		double displayRoll;
		if (guaranteedByPity) {
			won = true;
			displayRoll = 0.0D;
		} else {
			double rawRoll = world.random.nextDouble() * 100.0D;
			won = rawRoll < chance;
			displayRoll = round1(rawRoll);
			if (won && displayRoll >= chance) {
				displayRoll = Math.max(0.0D, round1(chance - 0.1D));
			} else if (!won && displayRoll < chance) {
				displayRoll = Math.min(99.9D, round1(chance + 0.1D));
			}
		}

		ItemStack rewardPreview = new ItemStack(targetItem, 1);
		String targetName = rewardPreview.getName().getString();
		int inherited = 0;
		double pityGain;
		double pityAfter;

		if (won) {
			pityGain = 0.0D;
			pityAfter = 0.0D;
			if (profile != null) {
				profile.upgraderPity = 0.0D;
				profile.upgraderFailStreak = 0;
			}
			inherited = transferEnchantsIfApplicable(sample, rewardPreview, bypassConflicts);
			if (!inv.insertStack(rewardPreview)) {
				player.dropItem(rewardPreview, false);
			}
			player.playerScreenHandler.sendContentUpdates();
			world.spawnParticles(
					guaranteedByPity ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.HAPPY_VILLAGER,
					player.getX(),
					player.getY() + 1.0,
					player.getZ(),
					guaranteedByPity ? 28 : 16,
					0.45,
					0.45,
					0.45,
					guaranteedByPity ? 0.18 : 0.05
			);
		} else {
			pityGain = calculateFailPityGain(sample, amount, targetItem);
			pityAfter = Math.min(MAX_PITY, round1(pityBefore + pityGain));
			if (profile != null) {
				profile.upgraderPity = pityAfter;
				profile.upgraderFailStreak++;
			}
			if (failXp > 0) {
				player.addExperience(failXp);
			}
			world.spawnParticles(
					ParticleTypes.FLAME,
					player.getX(),
					player.getY() + 0.9,
					player.getZ(),
					10,
					0.35,
					0.35,
					0.35,
					0.02
			);
		}

		return new RollOutcome(true, "", won, guaranteedByPity, chance, displayRoll, targetIdent.toString(),
				targetName, amount, failXp, inherited, pityBefore, pityAfter, pityGain);
	}

	public static ItemStack createRewardPreview(ItemStack sacrificeSample, Item targetItem, boolean copyEnchants, boolean bypassConflicts) {
		ItemStack out = new ItemStack(targetItem, 1);
		if (copyEnchants && sacrificeSample != null && !sacrificeSample.isEmpty()) {
			transferEnchantsIfApplicable(sacrificeSample, out, bypassConflicts);
		}
		return out;
	}

	private static void consumeFromBag(PlayerInventory inv, int selectedSlot, ItemStack sample, int amount) {
		int remaining = amount;
		ItemStack primary = getBagStack(inv, selectedSlot);
		if (!primary.isEmpty()) {
			int take = Math.min(remaining, primary.getCount());
			primary.decrement(take);
			remaining -= take;
		}
		if (remaining <= 0) return;

		for (int i = 0; i < inv.main.size() && remaining > 0; i++) {
			ItemStack s = inv.main.get(i);
			if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, sample)) {
				int take = Math.min(remaining, s.getCount());
				s.decrement(take);
				remaining -= take;
			}
		}
		if (remaining > 0 && !inv.offHand.isEmpty()) {
			ItemStack off = inv.offHand.get(0);
			if (!off.isEmpty() && ItemStack.areItemsAndComponentsEqual(off, sample)) {
				int take = Math.min(remaining, off.getCount());
				off.decrement(take);
			}
		}
	}

	private static int transferEnchantsIfApplicable(ItemStack source, ItemStack target, boolean bypassConflicts) {
		ItemEnchantmentsComponent srcComp = getEnchantComponent(source);
		if (srcComp.getSize() == 0) {
			return 0;
		}
		boolean targetIsBook = target.isOf(Items.ENCHANTED_BOOK);
		boolean targetIsGear = target.isDamageable() || target.isEnchantable() || targetIsBook;
		if (!targetIsGear) {
			return 0;
		}

		int[] copied = {0};
		EnchantmentHelper.apply(target, builder -> {
			for (RegistryEntry<Enchantment> entry : srcComp.getEnchantments()) {
				int lv = srcComp.getLevel(entry);
				if (lv <= 0) continue;
				if (targetIsBook || entry.value().isAcceptableItem(target) || bypassConflicts) {
					builder.set(entry, lv);
					copied[0]++;
				}
			}
		});
		return copied[0];
	}

	public static ItemEnchantmentsComponent getEnchantComponent(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return ItemEnchantmentsComponent.DEFAULT;
		}
		if (stack.isOf(Items.ENCHANTED_BOOK)) {
			return stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		}
		return EnchantmentHelper.getEnchantments(stack);
	}
}
