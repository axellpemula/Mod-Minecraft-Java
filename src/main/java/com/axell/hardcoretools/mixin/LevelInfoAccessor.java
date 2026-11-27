package com.axell.hardcoretools.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.LevelInfo;

@Mixin(LevelInfo.class)
public interface LevelInfoAccessor {
	@Mutable
	@Accessor("hardcore")
	void hardcoreTools$setHardcore(boolean hardcore);
}
