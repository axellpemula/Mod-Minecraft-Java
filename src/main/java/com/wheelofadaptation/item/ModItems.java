package com.wheelofadaptation.item;

import com.wheelofadaptation.WheelOfAdaptationMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ToolMaterials;

/**
 * Semua item Wheel of Adaptation (portingan dari addon Bedrock
 * axellpemula/Wheel-of-Adaptation v1.4.0).
 *
 * Bedrock ID -> Java ID:
 *   xvoid:wheel_of_adaptation      -> wheel_of_adaptation:wheel_of_adaptation
 *   xvoid:sword_of_extermination   -> wheel_of_adaptation:sword_of_extermination
 *   xvoid:seal_of_judgment         -> wheel_of_adaptation:seal_of_judgment
 *   xvoid:mode_switcher            -> wheel_of_adaptation:mode_switcher
 *   xvoid:adaptation_config        -> wheel_of_adaptation:adaptation_config
 *   xvoid:mahoraga_chest/legs/boots-> wheel_of_adaptation:mahoraga_*
 *
 * Catatan Fase 1: material armor/pedang masih pakai material vanilla
 * (Netherite) supaya stabil. Material custom Mahoraga dibuat di Fase 2/3.
 */
public class ModItems {

	// ===== ITEM UTAMA =====

	// Wheel of Adaptation — helm utama. Dipakai di kepala -> sistem adaptasi aktif.
	// Bedrock: wearable head, protection 3.
	public static final Item WHEEL_OF_ADAPTATION = register(
			new ArmorItem(
					ArmorMaterials.NETHERITE,
					ArmorItem.Type.HELMET,
					new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
			),
			"wheel_of_adaptation"
	);

	// Sword of Extermination — pedang Mahoraga. Bedrock: damage 8.
	// Java Netherite sword = damage 8, jadi pas untuk Fase 1.
	public static final Item SWORD_OF_EXTERMINATION = register(
			new SwordItem(
					ToolMaterials.NETHERITE,
					new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
			),
			"sword_of_extermination"
	);

	// Seal of Judgment — item domain. Fase 1: item biasa, logika domain di Fase 2.
	public static final Item SEAL_OF_JUDGMENT = register(
			new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
			"seal_of_judgment"
	);

	// Wheel Mode Switcher — ganti mode Attack/Balance/Defense. Logika di Fase 2.
	public static final Item MODE_SWITCHER = register(
			new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
			"mode_switcher"
	);

	// Adaptation Settings — item menu config. Logika menu di Fase 2.
	public static final Item ADAPTATION_CONFIG = register(
			new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)),
			"adaptation_config"
	);

	// ===== MAHORAGA ARMOR =====
	// Bedrock: chest protection 6, legs 5, boots 2. Netherite di Java mendekati itu.

	public static final Item MAHORAGA_CHEST = register(
			new ArmorItem(
					ArmorMaterials.NETHERITE,
					ArmorItem.Type.CHESTPLATE,
					new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
			),
			"mahoraga_chest"
	);

	public static final Item MAHORAGA_LEGS = register(
			new ArmorItem(
					ArmorMaterials.NETHERITE,
					ArmorItem.Type.LEGGINGS,
					new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
			),
			"mahoraga_legs"
	);

	public static final Item MAHORAGA_BOOTS = register(
			new ArmorItem(
					ArmorMaterials.NETHERITE,
					ArmorItem.Type.BOOTS,
					new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
			),
			"mahoraga_boots"
	);

	private static Item register(Item item, String name) {
		return Registry.register(BuiltInRegistries.ITEM, WheelOfAdaptationMod.id(name), item);
	}

	public static void register() {
		WheelOfAdaptationMod.LOGGER.info("Registering items for " + WheelOfAdaptationMod.MOD_ID);
	}
}
