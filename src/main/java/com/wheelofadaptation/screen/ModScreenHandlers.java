package com.wheelofadaptation.screen;

import com.wheelofadaptation.WheelOfAdaptationMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/**
 * ScreenHandlerType untuk Wheel Menu.
 *
 * MenuType factory (client) memakai constructor (syncId, inventory, buf).
 * Server memakai MenuProvider dengan constructor (syncId, inventory, player).
 */
public class ModScreenHandlers {

	public static final MenuType<WheelScreenHandler> WHEEL_MENU =
			new MenuType<>(WheelScreenHandler::new, FeatureFlags.VANILLA_SET);

	public static void register() {
		Registry.register(BuiltInRegistries.MENU, WheelOfAdaptationMod.id("wheel_menu"), WHEEL_MENU);
	}
}
