package com.wheelofadaptation.client.mixin;

import com.wheelofadaptation.client.WheelShakeState;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera shake (Fase 4): inject di akhir Camera#setup supaya offset rotasi kecil
 * diterapkan setiap frame ketika ada intensity (dari Adapted/Evolved/RBD/Seal).
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {

	@Shadow
	public abstract float getXRot();

	@Shadow
	public abstract float getYRot();

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "setup", at = @At("TAIL"))
	private void wheelofadaptation$shake(BlockGetter level, Entity entity, boolean detached,
			boolean mirror, float partialTick, CallbackInfo ci) {
		float shake = WheelShakeState.getIntensity();
		if (shake > 0.001F) {
			float yawOffset = WheelShakeState.nextOffset() * shake * 3.0F;
			float pitchOffset = WheelShakeState.nextOffset() * shake * 2.0F;
			this.setRotation(this.getYRot() + yawOffset, this.getXRot() + pitchOffset);
		}
	}
}
