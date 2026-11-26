package com.wheelofadaptation.item;

import com.wheelofadaptation.WheelOfAdaptationMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * Semua item custom dari mod Wheel of Adaptation didaftarkan di sini.
 *
 * Cara tambah item baru (langkah demi langkah):
 * 1. Tambah field baru di bawah, contoh:
 *    public static final Item ITEM_BARU = register(new Item(...), "item_baru");
 * 2. Buat model JSON di:
 *    src/main/resources/assets/wheel_of_adaptation/models/item/item_baru.json
 * 3. Tambah nama di:
 *    src/main/resources/assets/wheel_of_adaptation/lang/en_us.json
 * 4. (Opsional) Tambah recipe di:
 *    src/main/resources/data/wheel_of_adaptation/recipes/item_baru.json
 */
public class ModItems {

	// ===== CONTOH ITEM =====
	// Adaptation Wheel: item pertama mod ini.
	// Untuk sekarang masih item biasa (belum ada fungsi khusus).
	// Nanti bisa kita tambahkan: klik kanan untuk putar roda, dapat efek acak, dll.
	public static final Item ADAPTATION_WHEEL = register(
			new Item(new Item.Properties().stacksTo(1)),
			"adaptation_wheel"
	);

	// ===== DAFTAR ITEM LAIN (tambah di sini) =====
	// public static final Item CONTOH_ITEM = register(...);

	private static Item register(Item item, String name) {
		return Registry.register(BuiltInRegistries.ITEM, WheelOfAdaptationMod.id(name), item);
	}

	public static void register() {
		WheelOfAdaptationMod.LOGGER.info("Registering items for " + WheelOfAdaptationMod.MOD_ID);
	}
}
