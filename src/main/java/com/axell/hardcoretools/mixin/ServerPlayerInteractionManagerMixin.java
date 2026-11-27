package com.axell.hardcoretools.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.axell.hardcoretools.feature.VeinMinerHandler;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
	@Shadow
	protected ServerWorld world;

	@Shadow
	@Final
	protected ServerPlayerEntity player;

	@Unique
	private BlockState hardcoreTools$breakingState;

	@Unique
	private ItemStack hardcoreTools$breakingTool = ItemStack.EMPTY;

	@Inject(method = "tryBreakBlock", at = @At("HEAD"))
	private void hardcoreTools$capturePreBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (this.world != null && this.player != null && !VeinMinerHandler.isActiveVeinPlayer(this.player.getUuid())) {
			this.hardcoreTools$breakingState = this.world.getBlockState(pos);
			this.hardcoreTools$breakingTool = this.player.getMainHandStack().copy();
		} else {
			this.hardcoreTools$breakingState = null;
			this.hardcoreTools$breakingTool = ItemStack.EMPTY;
		}
	}

	@Inject(method = "tryBreakBlock", at = @At("RETURN"))
	private void hardcoreTools$afterBlockDropsSpawned(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		BlockState capturedState = this.hardcoreTools$breakingState;
		ItemStack capturedTool = this.hardcoreTools$breakingTool;
		this.hardcoreTools$breakingState = null;
		this.hardcoreTools$breakingTool = ItemStack.EMPTY;

		if (cir.getReturnValue() && capturedState != null && this.world != null && this.player != null) {
			VeinMinerHandler.handlePrimaryBlockBroken(this.world, this.player, pos.toImmutable(), capturedState, capturedTool);
		}
	}
}
