package com.wheelofadaptation.screen;

import com.wheelofadaptation.adaptation.AdaptationManager;
import com.wheelofadaptation.adaptation.PlayerAdaptationData;
import com.wheelofadaptation.item.ModItems;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
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

	private final SimpleContainer wheelInventory = new SimpleContainer(1);
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

	// Constructor client (dipakai MenuType factory dari network) — 2 arg (MenuSupplier vanilla)
	public WheelScreenHandler(int syncId, Inventory playerInventory) {
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
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index == 0) {
			// Dari slot khusus Wheel ke inventory player
			if (!this.moveItemStackTo(stack, 1, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			// Dari inventory player ke slot khusus Wheel
			if (!this.moveItemStackTo(stack, 0, 1, false)) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		// Server-side: simpan wheel dari slot khusus kembali ke data player
		if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
				&& player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());
			data.setWheelStack(wheelInventory.getItem(0).copy());
			// Save langsung supaya tidak hilang + sync ke client (HUD update)
			AdaptationManager.savePlayer(serverLevel.getServer(), player.getUUID());
			com.wheelofadaptation.adaptation.AdaptationSync.syncTo(serverPlayer);
		}
	}

	@Override
	public void slotsChanged(Container inventory) {
		super.slotsChanged(inventory);
		// Sync langsung ke data supaya HUD/damage handler update real-time (server-side saja)
		if (player != null && player.level() instanceof net.minecraft.server.level.ServerLevel) {
			PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());
			data.setWheelStack(wheelInventory.getItem(0).copy());
		}
	}
}
