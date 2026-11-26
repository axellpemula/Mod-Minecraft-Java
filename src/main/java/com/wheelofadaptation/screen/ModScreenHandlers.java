package com.wheelofadaptation.screen;

import net.minecraft.world.flag.FeatureSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * ScreenHandlerType untuk Wheel Menu.
 */
public class ModScreenHandlers {

	public static final MenuType<WheelScreenHandler> WHEEL_MENU =
			new MenuType<>(WheelScreenHandler::new, FeatureSet.of());

	public static void register() {
		// Type terdaftar via field di atas; method ini untuk konsistensi.
	}
}
