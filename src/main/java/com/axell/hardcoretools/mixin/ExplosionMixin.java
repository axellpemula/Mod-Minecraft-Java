package com.axell.hardcoretools.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.axell.hardcoretools.feature.ReturnByDeathFeature;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

/**
 * Vanilla explosions destroy blocks through an internal chunk write, NOT through
 * World#setBlockState, so the Return by Death block journal never saw creeper / TNT /
 * bed / wither craters. This hook snapshots every affected block right before the
 * explosion is applied, so the rewind can restore them.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
	@Shadow
	private World world;

	@Inject(method = "affectWorld", at = @At("HEAD"))
	private void hardcoreTools$captureExplodedBlocks(boolean particles, CallbackInfo ci) {
		Explosion self = (Explosion) (Object) this;
		List<BlockPos> affected = self.getAffectedBlocks();
		if (affected == null || affected.isEmpty() || this.world == null) {
			return;
		}
		ReturnByDeathFeature.recordExplosionBlocks(this.world, new ArrayList<>(affected));
	}
}
