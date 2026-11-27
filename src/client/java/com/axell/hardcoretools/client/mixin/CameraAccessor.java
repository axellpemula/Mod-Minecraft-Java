package com.axell.hardcoretools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.render.Camera;

/**
 * Camera#setRotation is protected, so the Return by Death controller cannot call it directly.
 * This invoker exposes it as a public mixin method.
 */
@Mixin(Camera.class)
public interface CameraAccessor {
	@Invoker("setRotation")
	void hardcoreTools$setRotation(float yaw, float pitch);
}
