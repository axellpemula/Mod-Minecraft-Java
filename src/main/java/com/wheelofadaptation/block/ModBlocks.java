package com.wheelofadaptation.block;

import com.wheelofadaptation.WheelOfAdaptationMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Semua block custom dari mod Wheel of Adaptation didaftarkan di sini.
 *
 * Cara tambah block baru:
 * 1. Tambah field baru di bawah, contoh:
 *    public static final Block BLOCK_BARU = register(new Block(...), "block_baru");
 * 2. Buat blockstate JSON di:
 *    src/main/resources/assets/wheel_of_adaptation/blockstates/block_baru.json
 * 3. Buat model JSON di:
 *    src/main/resources/assets/wheel_of_adaptation/models/block/block_baru.json
 * 4. Buat item model di:
 *    src/main/resources/assets/wheel_of_adaptation/models/item/block_baru.json
 * 5. Tambah nama di lang/en_us.json
 */
public class ModBlocks {

	// ===== CONTOH BLOCK =====
	// Adaptation Pedestal: block dekorasi / tempat roda adaptasi.
	// Untuk sekarang masih block biasa dengan sifat seperti batu.
	public static final Block ADAPTATION_PEDESTAL = register(
			new Block(BlockBehaviour.Properties.of()
					.strength(3.0F, 6.0F)
					.requiresCorrectToolForDrops()),
			"adaptation_pedestal"
	);

	// ===== DAFTAR BLOCK LAIN (tambah di sini) =====
	// public static final Block CONTOH_BLOCK = register(...);

	private static Block register(Block block, String name) {
		// Daftarkan block-nya
		Registry.register(BuiltInRegistries.BLOCK, WheelOfAdaptationMod.id(name), block);

		// Daftarkan juga BlockItem-nya, supaya block ini muncul sebagai item di inventory
		Registry.register(
				BuiltInRegistries.ITEM,
				WheelOfAdaptationMod.id(name),
				new BlockItem(block, new Item.Properties())
		);

		return block;
	}

	public static void register() {
		WheelOfAdaptationMod.LOGGER.info("Registering blocks for " + WheelOfAdaptationMod.MOD_ID);
	}
}
