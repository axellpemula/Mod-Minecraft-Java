package com.axell.hardcoretools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.axell.hardcoretools.client.CombatAimClientController;

import net.minecraft.client.MinecraftClient;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientAimMixin {
	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void hardcoreTools$handleSilentAimAttack(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient self = (MinecraftClient) (Object) this;
		Boolean handled = CombatAimClientController.handleClientDoAttack(self);
		if (handled != null) {
			cir.setReturnValue(handled);
		}
	}
}
