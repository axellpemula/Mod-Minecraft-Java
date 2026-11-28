package com.axell.hardcoretools.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.axell.hardcoretools.feature.TimeStopFeature;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;

/**
 * Priority 1100 ensures WeaponDamageMixin (priority 1000) has already scaled custom weapon damage
 * before TimeStopFeature stacks the hit into its deferred time-stop damage buffer.
 */
@Mixin(value = LivingEntity.class, priority = 1100)
public abstract class TimeStopDamageMixin {
	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void hardcoreTools$handleTimeStopDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient) {
			return;
		}
		if (TimeStopFeature.isFlushingDeferredDamage()
				|| TimeStopFeature.isInPostResumeGraceWindow(self.getServer(), source)) {
			self.timeUntilRegen = 0;
			self.hurtTime = 0;
			return;
		}
		if (TimeStopFeature.deferDamage(self, source, amount)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
	private void hardcoreTools$deferTimeStopKnockback(double strength, double x, double z, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.getWorld().isClient && TimeStopFeature.deferKnockback(self, strength, x, z)) {
			ci.cancel();
		}
	}
}
