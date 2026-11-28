package com.axell.hardcoretools.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.axell.hardcoretools.feature.CombatAimFeature;

import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileSilentAimMixin {
	@Inject(method = "setVelocity(DDDFF)V", at = @At("TAIL"))
	private void hardcoreTools$steerSilentAimProjectile(double x, double y, double z, float power, float uncertainty, CallbackInfo ci) {
		ProjectileEntity self = (ProjectileEntity) (Object) this;
		if (self.getWorld().isClient) {
			return;
		}
		if (!(self.getOwner() instanceof ServerPlayerEntity shooter)) {
			return;
		}
		Vec3d steered = CombatAimFeature.computeSilentProjectileVelocity(shooter, self, power);
		if (steered != null) {
			self.setVelocity(steered);
			double horiz = steered.horizontalLength();
			self.setYaw((float) (MathHelper.atan2(steered.x, steered.z) * (180.0 / Math.PI)));
			self.setPitch((float) (MathHelper.atan2(steered.y, horiz) * (180.0 / Math.PI)));
			self.prevYaw = self.getYaw();
			self.prevPitch = self.getPitch();
			self.velocityDirty = true;
		}
	}
}
