package com.wheelofadaptation.itemgroup;

import com.wheelofadaptation.WheelOfAdaptationMod;
import com.wheelofadaptation.block.ModBlocks;
import com.wheelofadaptation.item.ModItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Creative tab khusus untuk mod Wheel of Adaptation.
 * Semua item & block mod ini akan muncul di tab ini di Creative Inventory.
 */
public class ModItemGroups {

	public static final CreativeModeTab WHEEL_OF_ADAPTATION_GROUP = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			WheelOfAdaptationMod.id("wheel_of_adaptation_group"),
			FabricItemGroup.builder()
					.title(Component.translatable("itemGroup.wheel_of_adaptation.wheel_of_adaptation_group"))
					.icon(() -> new ItemStack(ModItems.ADAPTATION_WHEEL))
					.displayItems((parameters, output) -> {
						output.accept(ModItems.ADAPTATION_WHEEL);
						output.accept(ModBlocks.ADAPTATION_PEDESTAL);
						// Tambah item/block lain di sini supaya muncul di creative tab
					})
					.build()
	);

	public static void register() {
		WheelOfAdaptationMod.LOGGER.info("Registering item groups for " + WheelOfAdaptationMod.MOD_ID);
	}
}
