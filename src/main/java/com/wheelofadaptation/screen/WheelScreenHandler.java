package com.wheelofadaptation.screen;

import com.wheelofadaptation.adaptation.AdaptationManager;
import com.wheelofadaptation.adaptation.PlayerAdaptationData;
import com.wheelofadaptation.item.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleInventory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * ScreenHandler untuk Wheel Menu (slot khusus Wheel).
 *
 * Layout:
 * - Slot 0 (80, 18): slot khusus Wheel (hanya terima Wheel item)
 * - Slot player inventory 9x3 + hotbar di bawahnya
 *
 * Wheel disimpan di PlayerAdaptationData (bukan di slot kepala vanilla).
 * Buka menu: keybind R, klik kanan Adaptation Settings, atau /woa menu.
 */
public class WheelScreenHandler extends AbstractContainerMenu {

	private final SimpleInventory wheelInventory = new SimpleInventory(1);
	private final Player player;

	// Constructor server (dipakai MenuProvider)
	public WheelScreenHandler(int syncId, Inventory playerInventory, Player player) {
		super(ModScreenHandlers.WHEEL_MENU, syncId);
		this.player = player;

		// Load wheel dari data player ke slot khusus
		PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());
		wheelInventory.setItem(0, data.getWheelStack().copy());

		initSlots(playerInventory);
	}

	// Constructor client (dipakai MenuType factory dari network)
	public WheelScreenHandler(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
		super(ModScreenHandlers.WHEEL_MENU, syncId);
		this.player = null;
		initSlots(playerInventory);
	}

	private void initSlots(Inventory playerInventory) {
		// Slot khusus Wheel
		this.addSlot(new Slot(wheelInventory, 0, 80, 18) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() == ModItems.WHEEL_OF_ADAPTATION;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});

		// Player inventory 9x3
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		// Hotbar
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		// Simpan wheel dari slot khusus kembali ke data player
		PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());
		data.setWheelStack(wheelInventory.getItem(0).copy());
		// Save langsung supaya tidak hilang
		if (player.getServer() != null) {
			AdaptationManager.savePlayer(player.getServer(), player.getUUID());
		}
	}

	@Override
	public void slotsChanged(Container inventory) {
		super.slotsChanged(inventory);
		// Sync langsung ke data supaya HUD/damage handler update real-time
		if (player != null) {
			PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());
			data.setWheelStack(wheelInventory.getItem(0).copy());
		}
	}
}
