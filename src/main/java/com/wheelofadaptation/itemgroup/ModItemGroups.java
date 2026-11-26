package com.wheelofadaptation.itemgroup;

import com.wheelofadaptation.WheelOfAdaptationMod;
import com.wheelofadaptation.item.ModItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Creative tab khusus Wheel of Adaptation.
 * Urutan sesuai addon Bedrock: Wheel, Sword, Seal, Switcher, Armor, Config.
 */
public class ModItemGroups {

	public static final CreativeModeTab WHEEL_OF_ADAPTATION_GROUP = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			WheelOfAdaptationMod.id("wheel_of_adaptation_group"),
			FabricItemGroup.builder()
					.title(Component.translatable("itemGroup.wheel_of_adaptation.wheel_of_adaptation_group"))
					.icon(() -> new ItemStack(ModItems.WHEEL_OF_ADAPTATION))
					.displayItems((parameters, output) -> {
						output.accept(ModItems.WHEEL_OF_ADAPTATION);
						output.accept(ModItems.SWORD_OF_EXTERMINATION);
						output.accept(ModItems.SEAL_OF_JUDGMENT);
						output.accept(ModItems.MODE_SWITCHER);
						output.accept(ModItems.MAHORAGA_CHEST);
						output.accept(ModItems.MAHORAGA_LEGS);
						output.accept(ModItems.MAHORAGA_BOOTS);
						output.accept(ModItems.ADAPTATION_CONFIG);
					})
					.build()
	);

	public static void register() {
		WheelOfAdaptationMod.LOGGER.info("Registering item groups for " + WheelOfAdaptationMod.MOD_ID);
	}
}
