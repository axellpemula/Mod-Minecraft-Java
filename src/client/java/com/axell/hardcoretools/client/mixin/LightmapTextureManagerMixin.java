package com.axell.hardcoretools.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.axell.hardcoretools.client.ClientStateSnapshot;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
	@Shadow
	private boolean dirty;

	@Shadow
	@Final
	private NativeImage image;

	@Shadow
	@Final
	private NativeImageBackedTexture texture;

	@Unique
	private boolean hardcoreTools$wasFullbright = false;

	@Inject(method = "update", at = @At("HEAD"), cancellable = true)
	private void hardcoreTools$applyFullbright(float delta, CallbackInfo ci) {
		boolean active = ClientStateSnapshot.INSTANCE.fullbright;
		if (active != this.hardcoreTools$wasFullbright) {
			this.hardcoreTools$wasFullbright = active;
			this.dirty = true;
		}
		if (!active) {
			return;
		}
		if (this.dirty) {
			this.dirty = false;
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					this.image.setColor(x, y, 0xFFFFFFFF);
				}
			}
			this.texture.upload();
		}
		ci.cancel();
	}

	@Inject(method = "getDarknessFactor", at = @At("HEAD"), cancellable = true)
	private void hardcoreTools$suppressDarkness(float delta, CallbackInfoReturnable<Float> cir) {
		if (ClientStateSnapshot.INSTANCE.fullbright) {
			cir.setReturnValue(0.0F);
		}
	}
}
