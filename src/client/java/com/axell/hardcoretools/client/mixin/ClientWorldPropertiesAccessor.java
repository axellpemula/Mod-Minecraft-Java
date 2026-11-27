package com.axell.hardcoretools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.world.ClientWorld;

@Mixin(ClientWorld.Properties.class)
public interface ClientWorldPropertiesAccessor {
	@Mutable
	@Accessor("hardcore")
	void hardcoreTools$setHardcore(boolean hardcore);
}
