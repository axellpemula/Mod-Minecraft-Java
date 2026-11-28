package com.axell.hardcoretools.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.axell.hardcoretools.feature.WeaponDamageFeature;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

@Mixin(LivingEntity.class)
public abstract class WeaponDamageMixin {
	@ModifyVariable(method = "damage", at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private float hardcoreTools$modifyWeaponDamage(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient) {
			return amount;
		}
		if (source != null && source.getAttacker() instanceof ServerPlayerEntity player) {
			return WeaponDamageFeature.modifyPlayerAttackDamage(player, self, source, amount);
		}
		return amount;
	}
}
