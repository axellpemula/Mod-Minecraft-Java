package com.axell.hardcoretools.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.axell.hardcoretools.feature.ReturnByDeathFeature;

import net.minecraft.entity.mob.CreeperEntity;

@Mixin(CreeperEntity.class)
public abstract class CreeperEntityMixin {
	@Inject(method = "explode", at = @At("HEAD"))
	private void hardcoreTools$captureCreeperBeforeExplosion(CallbackInfo ci) {
		ReturnByDeathFeature.recordCreeperPreExplosion((CreeperEntity) (Object) this);
	}
}
