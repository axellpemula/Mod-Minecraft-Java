package com.axell.hardcoretools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.axell.hardcoretools.client.CombatAimClientController;
import com.axell.hardcoretools.client.ReturnByDeathClientController;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderWorld", at = @At("HEAD"))
	private void hardcoreTools$beforeRenderWorld(RenderTickCounter tickCounter, CallbackInfo ci) {
		ReturnByDeathClientController.onBeforeWorldRender();
		CombatAimClientController.onBeforeWorldRender(tickCounter);
	}

	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
	private void hardcoreTools$applyDeathCollapseRoll(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		float rollDeg = ReturnByDeathClientController.getDeathCameraRollDegrees();
		if (Math.abs(rollDeg) > 0.01F) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rollDeg));
		}
	}
}
