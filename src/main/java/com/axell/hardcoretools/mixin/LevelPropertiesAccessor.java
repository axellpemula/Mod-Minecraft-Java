package com.axell.hardcoretools.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.LevelProperties;

/**
 * LevelProperties#getLevelInfo() returns a *copy* built with
 * LevelInfo#withCopiedGameRules(), so mutating that copy never reaches the
 * real save data. This accessor exposes the genuine LevelInfo instance stored
 * on the LevelProperties so the hardcore flag can be flipped live in-game and
 * persisted into level.dat on the next autosave.
 */
@Mixin(LevelProperties.class)
public interface LevelPropertiesAccessor {
	@Accessor("levelInfo")
	LevelInfo hardcoreTools$getLevelInfo();

	@Mutable
	@Accessor("levelInfo")
	void hardcoreTools$setLevelInfo(LevelInfo levelInfo);
}
