package com.axell.hardcoretools.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

public final class EnchantFeature {
	public static final int OVER_ENCHANT_MAX = 25;
	private static final TagKey<Enchantment> CURSE_TAG = TagKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("curse"));

	// Preferred winners during Auto Max when conflicts are respected
	private static final Set<String> PREFERRED_ENCHANTS = Set.of(
			"minecraft:sharpness",
			"minecraft:protection",
			"minecraft:fortune",
			"minecraft:mending",
			"minecraft:loyalty",
			"minecraft:multishot",
			"minecraft:density"
	);

	public record GearSlotEntry(int slot, String tag, ItemStack stack, int enchantCount) {
	}

	private EnchantFeature() {
	}

	/**
	 * Unified slot indexing:
	 *  0..35  -> inv.main (0..8 hotbar, 9..35 backpack)
	 *  36..39 -> inv.armor (36=boots, 37=leggings, 38=chestplate, 39=helmet)
	 *  40     -> inv.offHand.get(0)
	 */
	public static ItemStack getStackAtSlot(ServerPlayerEntity player, int slot) {
		if (slot < 0) return ItemStack.EMPTY;
		PlayerInventory inv = player.getInventory();
		if (slot < 36) return inv.main.get(slot);
		if (slot < 40) return inv.armor.get(slot - 36);
		if (slot == 40) return inv.offHand.get(0);
		return ItemStack.EMPTY;
	}

	public static String describeSlot(ServerPlayerEntity player, int slot) {
		if (slot < 0) return "None";
		PlayerInventory inv = player.getInventory();
		if (slot == inv.selectedSlot) return "Mainhand (Hotbar " + (slot + 1) + ")";
		if (slot < 9) return "Hotbar " + (slot + 1);
		if (slot < 36) return "Backpack #" + (slot - 8);
		return switch (slot) {
			case 39 -> "Helmet (Equipped)";
			case 38 -> "Chestplate (Equipped)";
			case 37 -> "Leggings (Equipped)";
			case 36 -> "Boots (Equipped)";
			case 40 -> "Offhand";
			default -> "Inventory";
		};
	}

	public static String shortSlotTag(PlayerInventory inv, int slot) {
		if (slot == inv.selectedSlot) return "Hand";
		if (slot < 9) return "HB" + (slot + 1);
		if (slot < 36) return "Bag";
		return switch (slot) {
			case 39 -> "Helm";
			case 38 -> "Chest";
			case 37 -> "Legs";
			case 36 -> "Boots";
			case 40 -> "Off";
			default -> "Inv";
		};
	}

	public static boolean isTargetCandidate(Registry<Enchantment> registry, ItemStack stack) {
		if (stack.isEmpty() || stack.isOf(Items.TOTEM_OF_UNDYING)) {
			return false;
		}
		if (stack.isOf(Items.ENCHANTED_BOOK) || EnchantmentHelper.hasEnchantments(stack) || stack.isDamageable()) {
			return true;
		}
		return registry.streamEntries().anyMatch(e -> !e.isIn(CURSE_TAG) && e.value().isAcceptableItem(stack));
	}

	public static List<GearSlotEntry> collectEnchantableSlots(ServerPlayerEntity player) {
		Registry<Enchantment> registry = player.getServer().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		PlayerInventory inv = player.getInventory();
		List<GearSlotEntry> result = new ArrayList<>();

		// Order: Mainhand -> Equipped Armor (Helm..Boots) -> Offhand -> Remaining Hotbar -> Backpack
		int[] orderedSlots = new int[41];
		int idx = 0;
		orderedSlots[idx++] = inv.selectedSlot;
		orderedSlots[idx++] = 39;
		orderedSlots[idx++] = 38;
		orderedSlots[idx++] = 37;
		orderedSlots[idx++] = 36;
		orderedSlots[idx++] = 40;
		for (int i = 0; i < 9; i++) {
			if (i != inv.selectedSlot) orderedSlots[idx++] = i;
		}
		for (int i = 9; i < 36; i++) {
			orderedSlots[idx++] = i;
		}

		for (int slot : orderedSlots) {
			ItemStack s = getStackAtSlot(player, slot);
			if (!isTargetCandidate(registry, s)) continue;
			ItemEnchantmentsComponent comp = s.isOf(Items.ENCHANTED_BOOK)
					? s.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT)
					: EnchantmentHelper.getEnchantments(s);
			result.add(new GearSlotEntry(slot, shortSlotTag(inv, slot), s, comp.getSize()));
		}
		return result;
	}

	public static int resolveEffectiveSlotIndex(ServerPlayerEntity player, int preferredSlot) {
		Registry<Enchantment> registry = player.getServer().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		if (preferredSlot >= 0 && preferredSlot <= 40) {
			ItemStack s = getStackAtSlot(player, preferredSlot);
			if (!s.isEmpty()) {
				return preferredSlot;
			}
		}
		int mainSlot = player.getInventory().selectedSlot;
		ItemStack mainStack = player.getMainHandStack();
		if (isTargetCandidate(registry, mainStack)) {
			return mainSlot;
		}
		List<GearSlotEntry> candidates = collectEnchantableSlots(player);
		if (!candidates.isEmpty()) {
			return candidates.get(0).slot();
		}
		return mainStack.isEmpty() ? -1 : mainSlot;
	}

	public static int autoMaxSlotItem(ServerPlayerEntity player, int preferredSlot, boolean overEnchant25, boolean bypassConflicts) {
		int resolved = resolveEffectiveSlotIndex(player, preferredSlot);
		ItemStack target = getStackAtSlot(player, resolved);
		if (target.isEmpty()) {
			return -1;
		}
		Registry<Enchantment> registry = player.getServer().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		int applied = applyAutoMaxToStack(registry, target, overEnchant25, bypassConflicts);
		if (applied > 0) {
			playEnchantSound(player);
		}
		return applied;
	}

	public static int autoMaxAllGear(ServerPlayerEntity player, boolean overEnchant25, boolean bypassConflicts) {
		Registry<Enchantment> registry = player.getServer().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		int itemsMaxed = 0;
		for (int slot = 0; slot <= 40; slot++) {
			ItemStack stack = getStackAtSlot(player, slot);
			if (stack.isEmpty() || stack.isOf(Items.ENCHANTED_BOOK) || stack.isOf(Items.BOOK) || stack.isOf(Items.TOTEM_OF_UNDYING)) {
				continue;
			}
			if (applyAutoMaxToStack(registry, stack, overEnchant25, bypassConflicts) > 0) {
				itemsMaxed++;
			}
		}
		if (itemsMaxed > 0) {
			playEnchantSound(player);
		}
		return itemsMaxed;
	}

	private static int applyAutoMaxToStack(Registry<Enchantment> registry, ItemStack stack, boolean overEnchant25, boolean bypassConflicts) {
		boolean isBook = stack.isOf(Items.ENCHANTED_BOOK);
		List<RegistryEntry.Reference<Enchantment>> candidates = new ArrayList<>();
		registry.streamEntries().forEach(entry -> {
			if (entry.isIn(CURSE_TAG)) {
				return;
			}
			if (isBook || entry.value().isAcceptableItem(stack)) {
				candidates.add(entry);
			}
		});
		if (candidates.isEmpty()) {
			return 0;
		}

		candidates.sort(Comparator.comparingInt((RegistryEntry.Reference<Enchantment> e) ->
				PREFERRED_ENCHANTS.contains(e.registryKey().getValue().toString()) ? 0 : 1
		).thenComparing(e -> e.registryKey().getValue().toString()));

		List<RegistryEntry<Enchantment>> chosen = new ArrayList<>();
		EnchantmentHelper.apply(stack, builder -> {
			for (RegistryEntry<Enchantment> existing : new ArrayList<>(builder.getEnchantments())) {
				if (existing.isIn(CURSE_TAG)) {
					builder.set(existing, 0);
				}
			}

			for (RegistryEntry.Reference<Enchantment> entry : candidates) {
				String enchIdStr = entry.registryKey().getValue().toString();
				// Even with Bypass Conflicts ON, never auto-apply Silk Touch alongside Fortune (Silk Touch overrides Fortune in vanilla loot tables!)
				if ("minecraft:silk_touch".equals(enchIdStr) && !isBook) {
					boolean hasFortuneChosen = chosen.stream().anyMatch(e -> e.matchesId(Identifier.of("minecraft", "fortune")));
					if (hasFortuneChosen) {
						continue;
					}
				}

				if (!bypassConflicts) {
					boolean conflict = false;
					for (RegistryEntry<Enchantment> prev : chosen) {
						if (!prev.equals(entry) && !Enchantment.canBeCombined(prev, entry)) {
							conflict = true;
							break;
						}
					}
					if (conflict) continue;

					for (RegistryEntry<Enchantment> existing : new ArrayList<>(builder.getEnchantments())) {
						if (!existing.equals(entry) && !Enchantment.canBeCombined(existing, entry)) {
							builder.set(existing, 0);
						}
					}
				}

				int vanillaMax = entry.value().getMaxLevel();
				int targetLevel = (vanillaMax <= 1) ? 1 : (overEnchant25 ? OVER_ENCHANT_MAX : vanillaMax);
				builder.set(entry, targetLevel);
				chosen.add(entry);
			}
		});
		return chosen.size();
	}

	public static boolean applySingleEnchant(ServerPlayerEntity player, int preferredSlot, String enchantId, int requestedLevel, boolean bypassConflicts) {
		int resolved = resolveEffectiveSlotIndex(player, preferredSlot);
		ItemStack target = getStackAtSlot(player, resolved);
		if (target.isEmpty()) {
			return false;
		}
		Identifier id = Identifier.tryParse(enchantId);
		if (id == null) {
			return false;
		}
		Registry<Enchantment> registry = player.getServer().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		RegistryEntry.Reference<Enchantment> entry = registry.getEntry(id).orElse(null);
		if (entry == null) {
			return false;
		}

		int vanillaMax = entry.value().getMaxLevel();
		int finalLevel = (vanillaMax <= 1)
				? 1
				: Math.max(1, Math.min(OVER_ENCHANT_MAX, requestedLevel));

		EnchantmentHelper.apply(target, builder -> {
			if (!bypassConflicts) {
				for (RegistryEntry<Enchantment> existing : new ArrayList<>(builder.getEnchantments())) {
					if (!existing.equals(entry) && !Enchantment.canBeCombined(existing, entry)) {
						builder.set(existing, 0);
					}
				}
			}
			builder.set(entry, finalLevel);
		});

		playEnchantSound(player);
		return true;
	}

	public static boolean removeSingleEnchant(ServerPlayerEntity player, int preferredSlot, String enchantId) {
		int resolved = resolveEffectiveSlotIndex(player, preferredSlot);
		ItemStack target = getStackAtSlot(player, resolved);
		if (target.isEmpty()) return false;
		Identifier id = Identifier.tryParse(enchantId);
		if (id == null) return false;
		Registry<Enchantment> registry = player.getServer().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		RegistryEntry.Reference<Enchantment> entry = registry.getEntry(id).orElse(null);
		if (entry == null) return false;

		EnchantmentHelper.apply(target, builder -> builder.set(entry, 0));
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 0.9F, 1.1F);
		return true;
	}

	public static boolean clearTargetEnchants(ServerPlayerEntity player, int preferredSlot) {
		int resolved = resolveEffectiveSlotIndex(player, preferredSlot);
		ItemStack target = getStackAtSlot(player, resolved);
		if (target.isEmpty()) return false;
		if (target.isOf(Items.ENCHANTED_BOOK)) {
			target.set(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		} else {
			target.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		}
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	public static boolean giveEnchantedBook(ServerPlayerEntity player, String enchantId, int requestedLevel) {
		Identifier id = Identifier.tryParse(enchantId);
		if (id == null) return false;
		Registry<Enchantment> registry = player.getServer().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		RegistryEntry.Reference<Enchantment> entry = registry.getEntry(id).orElse(null);
		if (entry == null) return false;

		int vanillaMax = entry.value().getMaxLevel();
		int finalLevel = (vanillaMax <= 1)
				? 1
				: Math.max(1, Math.min(OVER_ENCHANT_MAX, requestedLevel));

		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
		builder.set(entry, finalLevel);
		book.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());

		if (!player.getInventory().insertStack(book)) {
			player.dropItem(book, false);
		}
		playEnchantSound(player);
		return true;
	}

	private static void playEnchantSound(ServerPlayerEntity player) {
		player.getWorld().playSound(
				null,
				player.getX(),
				player.getY(),
				player.getZ(),
				SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
				SoundCategory.PLAYERS,
				1.0F,
				1.1F
		);
	}

	public static String toRoman(int num) {
		if (num <= 0) return String.valueOf(num);
		if (num > 50) return "Lv." + num;
		int[] values = {50, 40, 10, 9, 5, 4, 1};
		String[] numerals = {"L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder sb = new StringBuilder();
		int rem = num;
		for (int i = 0; i < values.length; i++) {
			while (rem >= values[i]) {
				sb.append(numerals[i]);
				rem -= values[i];
			}
		}
		return sb.toString();
	}
}
