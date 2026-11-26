package com.wheelofadaptation.item;

import com.wheelofadaptation.WheelOfAdaptationMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;

/**
 * Semua item Wheel of Adaptation (portingan dari addon Bedrock
 * axellpemula/Wheel-of-Adaptation v1.4.0, dengan kebebasan Java).
 *
 * PERBEDAAN DARI BEDROCK (sengaja):
 * - Wheel BUKAN helm vanilla lagi, tapi item slot khusus.
 *   Player equip Wheel lewat menu khusus (keybind / Adaptation Settings),
 *   bukan lewat slot kepala. Ini memberi kebebasan: bisa pakai helm lain
 *   sambil pakai Wheel, dan UI adaptasi bisa custom.
 * - Mahoraga armor tetap armor vanilla (chest/legs/boots).
 */
public class ModItems {

	// ===== ITEM UTAMA =====

	// Wheel of Adaptation — item slot khusus (bukan armor).
	// Equip lewat Wheel Menu, bukan slot kepala.
	public static final Item WHEEL_OF_ADAPTATION = register(
			new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
			"wheel_of_adaptation"
	);

	// Sword of Extermination — pedang Mahoraga. Bedrock: damage 8.
	// Java Netherite sword = damage 8, jadi pas untuk Fase 1. (Mojang: Tiers, bukan ToolMaterials)
	public static final Item SWORD_OF_EXTERMINATION = register(
			new SwordItem(
					net.minecraft.world.item.Tiers.NETHERITE,
					new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
			),
			"sword_of_extermination"
	);

	// Seal of Judgment — item domain.
	public static final Item SEAL_OF_JUDGMENT = register(
			new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
			"seal_of_judgment"
	);

	// Wheel Mode Switcher — ganti mode Attack/Balance/Defense (klik kanan).
	public static final Item MODE_SWITCHER = register(
			new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
			"mode_switcher"
	);

	// Adaptation Settings — klik kanan untuk buka Wheel Menu.
	public static final Item ADAPTATION_CONFIG = register(
			new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)),
			"adaptation_config"
	);

	// Barehand — tinju Mahoraga. Klik kanan: Teleport Kick. Sneak+klik kanan: Guard.
	public static final Item BAREHAND = register(
			new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
			"barehand"
	);

	// ===== MAHORAGA ARMOR =====
	// Bedrock: chest protection 6, legs 5, boots 2 + texture Mahoraga sendiri
	// (wheel_of_adaptation:textures/models/armor/mahoraga_layer_1/2.png).

	public static final net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> MAHORAGA_MATERIAL;

	static {
		net.minecraft.world.item.ArmorMaterial material = new net.minecraft.world.item.ArmorMaterial(
				net.minecraft.Util.make(new java.util.EnumMap<net.minecraft.world.item.ArmorItem.Type, Integer>(
						net.minecraft.world.item.ArmorItem.Type.class), map -> {
					map.put(net.minecraft.world.item.ArmorItem.Type.BOOTS, 2);
					map.put(net.minecraft.world.item.ArmorItem.Type.LEGGINGS, 5);
					map.put(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, 6);
					map.put(net.minecraft.world.item.ArmorItem.Type.HELMET, 3);
					map.put(net.minecraft.world.item.ArmorItem.Type.BODY, 6);
				}),
				37, // enchantability (netherite)
				net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE,
				() -> net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.NETHERITE_INGOT),
				java.util.List.of(new net.minecraft.world.item.ArmorMaterial.Layer(
						WheelOfAdaptationMod.id("mahoraga"))),
				3.0F, // toughness
				0.1F  // knockback resistance
		);
		net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.ARMOR_MATERIAL,
				WheelOfAdaptationMod.id("mahoraga"),
				material
		);
		MAHORAGA_MATERIAL = net.minecraft.core.Holder.direct(material);
	}

	public static final Item MAHORAGA_CHEST = register(
			new ArmorItem(MAHORAGA_MATERIAL, ArmorItem.Type.CHESTPLATE,
					new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
			"mahoraga_chest"
	);

	public static final Item MAHORAGA_LEGS = register(
			new ArmorItem(MAHORAGA_MATERIAL, ArmorItem.Type.LEGGINGS,
					new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
			"mahoraga_legs"
	);

	public static final Item MAHORAGA_BOOTS = register(
			new ArmorItem(MAHORAGA_MATERIAL, ArmorItem.Type.BOOTS,
					new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
			"mahoraga_boots"
	);

	private static Item register(Item item, String name) {
		return Registry.register(BuiltInRegistries.ITEM, WheelOfAdaptationMod.id(name), item);
	}

	public static void register() {
		WheelOfAdaptationMod.LOGGER.info("Registering items for " + WheelOfAdaptationMod.MOD_ID);
	}
}
