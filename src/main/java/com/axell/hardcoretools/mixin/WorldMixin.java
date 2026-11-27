package com.axell.hardcoretools.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.axell.hardcoretools.feature.ReturnByDeathFeature;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(World.class)
public abstract class WorldMixin {
	@Inject(
			method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
			at = @At("HEAD")
	)
	private void hardcoreTools$beforeSetBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
												   CallbackInfoReturnable<Boolean> cir) {
		if (((Object) this) instanceof ServerWorld serverWorld) {
			ReturnByDeathFeature.recordBlockChange(serverWorld, pos, state);
		}
	}
}
