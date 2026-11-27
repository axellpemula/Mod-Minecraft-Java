package com.axell.hardcoretools.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class UncraftFeature {
	// Raw base materials that should NOT reverse-craft into storage blocks or nuggets
	private static final Set<Item> RAW_BASE_ITEMS = Set.of(
			Items.COAL,
			Items.CHARCOAL,
			Items.RAW_COPPER,
			Items.COPPER_INGOT,
			Items.RAW_IRON,
			Items.IRON_INGOT,
			Items.IRON_NUGGET,
			Items.RAW_GOLD,
			Items.GOLD_INGOT,
			Items.GOLD_NUGGET,
			Items.REDSTONE,
			Items.LAPIS_LAZULI,
			Items.DIAMOND,
			Items.EMERALD,
			Items.QUARTZ,
			Items.AMETHYST_SHARD,
			Items.ANCIENT_DEBRIS,
			Items.NETHERITE_SCRAP,
			Items.NETHERITE_INGOT,
			Items.WHEAT,
			Items.SLIME_BALL,
			Items.HONEYCOMB,
			Items.DRIED_KELP,
			Items.BONE_MEAL
	);

	private UncraftFeature() {
	}

	public record UncraftCandidate(
			int slot,
			String slotTag,
			ItemStack sample,
			int availableCount,
			int requiredPerBatch,
			int maxBatches,
			int enchantCount
	) {
	}

	public record UncraftPlan(
			boolean canUncraft,
			String reason,
			int requiredInputCount,
			int maxBatches,
			List<ItemStack> rawMaterials,
			List<ItemStack> enchantBooks,
			boolean disenchantOnlyKeepBase
	) {
		public List<ItemStack> allOutputs() {
			List<ItemStack> all = new ArrayList<>(rawMaterials.size() + enchantBooks.size());
			all.addAll(rawMaterials);
			all.addAll(enchantBooks);
			return all;
		}
	}

	public record RecipeBreakdown(int outputCountPerBatch, List<ItemStack> ingredientsPerBatch) {
	}

	public static List<UncraftCandidate> collectUncraftableSlots(World world, PlayerInventory inv) {
		List<UncraftCandidate> result = new ArrayList<>();
		if (world == null || inv == null) {
			return result;
		}

		List<ItemStack> seenStackables = new ArrayList<>();
		// Scan Equipped Armor (39..36), Offhand (40), Hotbar (0..8), Bag (9..35)
		int[] order = new int[41];
		int idx = 0;
		for (int a = 39; a >= 36; a--) order[idx++] = a;
		order[idx++] = 40;
		for (int m = 0; m < 36; m++) order[idx++] = m;

		for (int slot : order) {
			ItemStack stack = getSlotStack(inv, slot);
			if (stack.isEmpty()) continue;

			ItemEnchantmentsComponent ench = getEnchantComponent(stack);
			int enchCount = ench.getSize();

			// Multi-enchanted book can be split into separate Enchanted Books; single-enchant book is already extracted
			if (stack.isOf(Items.ENCHANTED_BOOK)) {
				if (enchCount >= 2) {
					result.add(new UncraftCandidate(slot, slotTag(slot), stack.copyWithCount(1), 1, 1, 1, enchCount));
				}
				continue;
			}

			RecipeBreakdown recipe = resolveRecipe(world, stack.getItem());
			if (recipe == null && enchCount == 0) {
				continue;
			}

			boolean uniqueGear = stack.getMaxCount() == 1 || enchCount > 0 || stack.isDamaged() || slot >= 36;
			int reqPerBatch = recipe != null ? recipe.outputCountPerBatch() : 1;

			if (uniqueGear) {
				if (1 >= reqPerBatch) {
					result.add(new UncraftCandidate(slot, slotTag(slot), stack.copyWithCount(1), 1, reqPerBatch, 1, enchCount));
				}
			} else {
				boolean alreadySeen = false;
				for (ItemStack prev : seenStackables) {
					if (ItemStack.areItemsAndComponentsEqual(prev, stack)) {
						alreadySeen = true;
						break;
					}
				}
				if (alreadySeen) continue;
				seenStackables.add(stack.copyWithCount(1));

				int totalOwned = countMatchingMainAndOffhand(inv, slot);
				int maxBatches = totalOwned / Math.max(1, reqPerBatch);
				if (maxBatches >= 1) {
					result.add(new UncraftCandidate(slot, slotTag(slot), stack.copyWithCount(1), totalOwned, reqPerBatch, maxBatches, 0));
				}
			}
		}
		return result;
	}

	public static UncraftPlan buildPlan(World world, PlayerInventory inv, int slot, int requestedBatches, boolean splitBooks) {
		if (world == null || inv == null) {
			return new UncraftPlan(false, "Unavailable.", 0, 0, List.of(), List.of(), false);
		}
		ItemStack ref = getSlotStack(inv, slot);
		if (ref.isEmpty()) {
			return new UncraftPlan(false, "Select an item to uncraft below.", 0, 0, List.of(), List.of(), false);
		}

		ItemEnchantmentsComponent ench = getEnchantComponent(ref);
		int enchCount = ench.getSize();

		// Case 1: Multi-Enchanted Book -> Split into individual Enchanted Books
		if (ref.isOf(Items.ENCHANTED_BOOK)) {
			if (enchCount < 2) {
				return new UncraftPlan(false, "This Enchanted Book already has a single enchantment.", 1, 0, List.of(), List.of(), false);
			}
			List<ItemStack> books = buildExtractedBooks(ench, true);
			return new UncraftPlan(true, "", 1, 1, List.of(), books, false);
		}

		RecipeBreakdown recipe = resolveRecipe(world, ref.getItem());
		boolean uniqueGear = ref.getMaxCount() == 1 || enchCount > 0 || ref.isDamaged() || slot >= 36;

		// Case 2: Non-craftable Enchanted Gear (e.g. Trident, Elytra, Chainmail) -> Extract Enchanted Books & return clean item!
		if (recipe == null) {
			if (enchCount == 0) {
				return new UncraftPlan(false, "No crafting recipe or enchantments on this item.", 1, 0, List.of(), List.of(), false);
			}
			ItemStack cleanBase = ref.copyWithCount(1);
			cleanBase.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
			List<ItemStack> books = buildExtractedBooks(ench, splitBooks);
			return new UncraftPlan(true, "", 1, 1, List.of(cleanBase), books, true);
		}

		// Case 3: Craftable / Netherite Item (with or without Enchantments)
		int reqPerBatch = Math.max(1, recipe.outputCountPerBatch());
		int available = uniqueGear ? 1 : countMatchingMainAndOffhand(inv, slot);
		int maxBatches = available / reqPerBatch;
		if (maxBatches <= 0) {
			return new UncraftPlan(false, "Need at least " + reqPerBatch + "x " + ref.getName().getString() + " to uncraft 1 batch!", reqPerBatch, 0, List.of(), List.of(), false);
		}

		int batches = uniqueGear ? 1 : Math.max(1, Math.min(maxBatches, requestedBatches));
		int totalConsume = batches * reqPerBatch;

		List<ItemStack> rawOutputs = multiplyAndSplitStacks(recipe.ingredientsPerBatch(), batches);
		List<ItemStack> books = enchCount > 0 ? buildExtractedBooks(ench, splitBooks) : List.of();
		return new UncraftPlan(true, "", totalConsume, maxBatches, rawOutputs, books, false);
	}

	public static String executeUncraft(ServerPlayerEntity player, int slot, int requestedBatches, boolean splitBooks) {
		ServerWorld world = player.getServerWorld();
		PlayerInventory inv = player.getInventory();
		ItemStack ref = getSlotStack(inv, slot);
		if (ref.isEmpty()) {
			return null;
		}
		String sourceName = ref.getName().getString();
		ItemStack matchTemplate = ref.copyWithCount(1);
		boolean uniqueSlotOnly = ref.getMaxCount() == 1 || getEnchantComponent(ref).getSize() > 0 || ref.isDamaged() || slot >= 36;

		UncraftPlan plan = buildPlan(world, inv, slot, requestedBatches, splitBooks);
		if (!plan.canUncraft()) {
			return null;
		}

		// Consume source item(s)
		if (uniqueSlotOnly) {
			ref.decrement(plan.requiredInputCount());
		} else {
			consumeStackableFromBag(inv, slot, matchTemplate, plan.requiredInputCount());
		}

		// Deliver all raw materials + extracted Enchanted Books
		for (ItemStack out : plan.allOutputs()) {
			if (out.isEmpty()) continue;
			ItemStack copy = out.copy();
			if (!inv.insertStack(copy)) {
				player.dropItem(copy, false);
			}
		}
		player.playerScreenHandler.sendContentUpdates();

		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 0.9F, 1.15F);
		if (!plan.enchantBooks().isEmpty()) {
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.95F, 1.15F);
		}
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.35, 0.35, 0.35, 0.04);

		int rawTypes = plan.rawMaterials().size();
		int bookCount = plan.enchantBooks().size();
		if (bookCount > 0 && plan.disenchantOnlyKeepBase()) {
			return "Extracted " + bookCount + " Enchanted Book(s) from " + sourceName + "!";
		}
		if (bookCount > 0) {
			return "Uncrafted " + sourceName + " ➔ Raw Materials + " + bookCount + " Enchanted Book(s)!";
		}
		return "Uncrafted " + plan.requiredInputCount() + "x " + sourceName + " ➔ " + rawTypes + " Raw Material(s)!";
	}

	public static RecipeBreakdown resolveRecipe(World world, Item targetItem) {
		if (targetItem == null || targetItem == Items.AIR || RAW_BASE_ITEMS.contains(targetItem)) {
			return null;
		}

		// 1. Explicit full raw-material deconstruction for Netherite Gear & Mace
		RecipeBreakdown netherite = resolveNetheriteGearBreakdown(targetItem);
		if (netherite != null) {
			return netherite;
		}

		// 2. Scan Crafting Recipes in RecipeManager
		DynamicRegistryManager regMgr = world.getRegistryManager();
		List<RecipeEntry<CraftingRecipe>> allCrafting = world.getRecipeManager().listAllOfType(RecipeType.CRAFTING);

		RecipeEntry<CraftingRecipe> bestEntry = null;
		int bestScore = Integer.MIN_VALUE;

		for (RecipeEntry<CraftingRecipe> entry : allCrafting) {
			CraftingRecipe recipe = entry.value();
			ItemStack out = recipe.getResult(regMgr);
			if (out == null || out.isEmpty() || out.getItem() != targetItem) {
				continue;
			}
			List<Ingredient> ingredients = recipe.getIngredients();
			if (ingredients == null || ingredients.isEmpty()) {
				continue;
			}

			String path = entry.id().getPath();
			int score = 100;
			if ("minecraft".equals(entry.id().getNamespace())) score += 20;
			if (path.contains("_from_")) score -= 250;
			if (path.startsWith("dye_") || path.contains("recolor")) score -= 300;
			if (path.contains("repair")) score -= 400;

			int nonEmptyCount = 0;
			boolean selfLoop = false;
			for (Ingredient ing : ingredients) {
				if (ing == null || ing.isEmpty()) continue;
				ItemStack[] matches = ing.getMatchingStacks();
				if (matches == null || matches.length == 0) continue;
				nonEmptyCount++;
				if (matches[0].getItem() == targetItem) {
					selfLoop = true;
				}
			}
			if (nonEmptyCount == 0 || selfLoop) {
				continue;
			}
			score += nonEmptyCount * 12;
			if (out.getCount() == 1) score += 15;

			if (score > bestScore) {
				bestScore = score;
				bestEntry = entry;
			}
		}

		if (bestEntry == null) {
			return null;
		}

		CraftingRecipe chosen = bestEntry.value();
		ItemStack resultOut = chosen.getResult(regMgr);
		int outCount = Math.max(1, resultOut.getCount());

		Map<Item, Integer> tally = new LinkedHashMap<>();
		for (Ingredient ing : chosen.getIngredients()) {
			if (ing == null || ing.isEmpty()) continue;
			ItemStack[] matches = ing.getMatchingStacks();
			if (matches == null || matches.length == 0 || matches[0].isEmpty()) continue;
			Item ingItem = matches[0].getItem();
			if (ingItem == Items.AIR || ingItem.hasRecipeRemainder()) {
				continue;
			}
			tally.merge(ingItem, 1, Integer::sum);
		}
		if (tally.isEmpty()) {
			return null;
		}

		List<ItemStack> merged = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : tally.entrySet()) {
			merged.add(new ItemStack(e.getKey(), e.getValue()));
		}
		return new RecipeBreakdown(outCount, merged);
	}

	private static RecipeBreakdown resolveNetheriteGearBreakdown(Item item) {
		if (item == Items.NETHERITE_SWORD) return netheriteWithDiamondsAndSticks(2, 1);
		if (item == Items.NETHERITE_PICKAXE) return netheriteWithDiamondsAndSticks(3, 2);
		if (item == Items.NETHERITE_AXE) return netheriteWithDiamondsAndSticks(3, 2);
		if (item == Items.NETHERITE_SHOVEL) return netheriteWithDiamondsAndSticks(1, 2);
		if (item == Items.NETHERITE_HOE) return netheriteWithDiamondsAndSticks(2, 2);
		if (item == Items.NETHERITE_HELMET) return netheriteWithDiamondsAndSticks(5, 0);
		if (item == Items.NETHERITE_CHESTPLATE) return netheriteWithDiamondsAndSticks(8, 0);
		if (item == Items.NETHERITE_LEGGINGS) return netheriteWithDiamondsAndSticks(7, 0);
		if (item == Items.NETHERITE_BOOTS) return netheriteWithDiamondsAndSticks(4, 0);
		return null;
	}

	private static RecipeBreakdown netheriteWithDiamondsAndSticks(int diamonds, int sticks) {
		List<ItemStack> list = new ArrayList<>();
		list.add(new ItemStack(Items.NETHERITE_INGOT, 1));
		list.add(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
		if (diamonds > 0) list.add(new ItemStack(Items.DIAMOND, diamonds));
		if (sticks > 0) list.add(new ItemStack(Items.STICK, sticks));
		return new RecipeBreakdown(1, list);
	}

	private static List<ItemStack> buildExtractedBooks(ItemEnchantmentsComponent ench, boolean splitBooks) {
		List<ItemStack> books = new ArrayList<>();
		if (ench == null || ench.getSize() == 0) {
			return books;
		}

		List<RegistryEntry<Enchantment>> sortedEntries = new ArrayList<>(ench.getEnchantments());
		sortedEntries.sort(Comparator.comparing(e -> e.getIdAsString()));

		if (splitBooks) {
			for (RegistryEntry<Enchantment> entry : sortedEntries) {
				int lv = ench.getLevel(entry);
				if (lv <= 0) continue;
				ItemStack book = new ItemStack(Items.ENCHANTED_BOOK, 1);
				ItemEnchantmentsComponent.Builder b = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
				b.set(entry, lv);
				book.set(DataComponentTypes.STORED_ENCHANTMENTS, b.build());
				books.add(book);
			}
		} else {
			ItemStack combinedBook = new ItemStack(Items.ENCHANTED_BOOK, 1);
			ItemEnchantmentsComponent.Builder b = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
			for (RegistryEntry<Enchantment> entry : sortedEntries) {
				int lv = ench.getLevel(entry);
				if (lv > 0) {
					b.set(entry, lv);
				}
			}
			combinedBook.set(DataComponentTypes.STORED_ENCHANTMENTS, b.build());
			books.add(combinedBook);
		}
		return books;
	}

	private static List<ItemStack> multiplyAndSplitStacks(List<ItemStack> perBatch, int batches) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack base : perBatch) {
			int total = base.getCount() * batches;
			int maxStack = Math.max(1, base.getMaxCount());
			while (total > 0) {
				int chunk = Math.min(maxStack, total);
				out.add(new ItemStack(base.getItem(), chunk));
				total -= chunk;
			}
		}
		return out;
	}

	public static ItemStack getSlotStack(PlayerInventory inv, int slot) {
		if (inv == null) return ItemStack.EMPTY;
		if (slot >= 0 && slot < inv.main.size()) {
			return inv.main.get(slot);
		}
		if (slot >= 36 && slot < 36 + inv.armor.size()) {
			return inv.armor.get(slot - 36);
		}
		if (slot == 40 && !inv.offHand.isEmpty()) {
			return inv.offHand.get(0);
		}
		return ItemStack.EMPTY;
	}

	private static int countMatchingMainAndOffhand(PlayerInventory inv, int slot) {
		ItemStack ref = getSlotStack(inv, slot);
		if (ref.isEmpty()) return 0;
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

	private static void consumeStackableFromBag(PlayerInventory inv, int selectedSlot, ItemStack template, int amount) {
		int rem = amount;
		ItemStack first = getSlotStack(inv, selectedSlot);
		if (!first.isEmpty() && ItemStack.areItemsAndComponentsEqual(first, template)) {
			int take = Math.min(rem, first.getCount());
			first.decrement(take);
			rem -= take;
		}
		for (int i = 0; i < inv.main.size() && rem > 0; i++) {
			ItemStack s = inv.main.get(i);
			if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, template)) {
				int take = Math.min(rem, s.getCount());
				s.decrement(take);
				rem -= take;
			}
		}
		if (rem > 0 && !inv.offHand.isEmpty()) {
			ItemStack off = inv.offHand.get(0);
			if (!off.isEmpty() && ItemStack.areItemsAndComponentsEqual(off, template)) {
				int take = Math.min(rem, off.getCount());
				off.decrement(take);
			}
		}
	}

	private static String slotTag(int slot) {
		if (slot == 39) return "H";
		if (slot == 38) return "C";
		if (slot == 37) return "L";
		if (slot == 36) return "B";
		if (slot == 40) return "OH";
		if (slot >= 0 && slot <= 8) return String.valueOf(slot + 1);
		return "";
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
