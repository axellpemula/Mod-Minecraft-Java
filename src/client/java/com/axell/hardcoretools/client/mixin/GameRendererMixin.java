package com.axell.hardcoretools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.axell.hardcoretools.client.ReturnByDeathClientController;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderWorld", at = @At("HEAD"))
	private void hardcoreTools$beforeRenderWorld(RenderTickCounter tickCounter, CallbackInfo ci) {
		ReturnByDeathClientController.onBeforeWorldRender();
	}
}
