package com.axell.hardcoretools.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.axell.hardcoretools.feature.ReturnByDeathFeature;

import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
	@Inject(
			method = "extract(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/entity/ItemEntity;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private static void hardcoreTools$ignoreRbdGhostDrops(Inventory inventory, ItemEntity itemEntity,
														  CallbackInfoReturnable<Boolean> cir) {
		if (itemEntity != null && itemEntity.getCommandTags().contains(ReturnByDeathFeature.GHOST_DROP_TAG)) {
			cir.setReturnValue(false);
		}
	}
}
