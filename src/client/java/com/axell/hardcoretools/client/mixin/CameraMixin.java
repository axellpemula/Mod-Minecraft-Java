package com.axell.hardcoretools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.axell.hardcoretools.client.ReturnByDeathClientController;

import net.minecraft.client.render.Camera;

/**
 * Applies the Return by Death "soul leaving the body" framing AFTER vanilla has already
 * resolved the third-person camera. Doing it here (instead of teleporting the player entity
 * every frame) keeps the camera perfectly smooth — no more judder.
 * <p>
 * A re-entrancy guard is required because this callback re-invokes Camera#setRotation.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Unique
	private boolean hardcoreTools$applyingFraming = false;

	@Inject(method = "setRotation", at = @At("TAIL"))
	private void hardcoreTools$applyDeathFraming(float yaw, float pitch, CallbackInfo ci) {
		if (this.hardcoreTools$applyingFraming) {
			return;
		}
		this.hardcoreTools$applyingFraming = true;
		try {
			ReturnByDeathClientController.applyDeathCameraRotation((Camera) (Object) this);
		} finally {
			this.hardcoreTools$applyingFraming = false;
		}
	}
}
