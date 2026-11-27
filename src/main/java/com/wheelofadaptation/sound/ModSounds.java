package com.wheelofadaptation.sound;

import com.wheelofadaptation.WheelOfAdaptationMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound custom dari RP Bedrock (sounds/xvoid/*.ogg).
 * File .ogg sudah dicopy ke assets/wheel_of_adaptation/sounds/.
 */
public class ModSounds {

	public static final SoundEvent WHEEL_SPIN = register("wheel_spin");
	public static final SoundEvent WHEEL_FIRST = register("wheel_first");
	public static final SoundEvent WHEEL_FAST = register("wheel_fast");
	public static final SoundEvent WHEEL_ADAPTED = register("wheel_adapted");
	public static final SoundEvent WHEEL_NULLIFY = register("wheel_nullify");
	public static final SoundEvent WHEEL_UNDYING = register("wheel_undying");
	public static final SoundEvent HEAL = register("heal");
	public static final SoundEvent SWORD_SLASH = register("sword_slash");
	public static final SoundEvent SWORD_STAB = register("sword_stab");
	public static final SoundEvent SLASH_HIT = register("slash_hit");
	public static final SoundEvent AIR_BLAST_FIRE = register("air_blast_fire");
	public static final SoundEvent DASH = register("dash");
	public static final SoundEvent SEAL_OPEN = register("seal_open");
	public static final SoundEvent SEAL_CLOSE = register("seal_close");
	public static final SoundEvent SEAL_CHAIN = register("seal_chain");
	public static final SoundEvent TELEPORT = register("teleport");
	public static final SoundEvent PURIFY = register("purify");

	private static SoundEvent register(String name) {
		return Registry.register(
				BuiltInRegistries.SOUND_EVENT,
				WheelOfAdaptationMod.id(name),
				SoundEvent.createVariableRangeEvent(WheelOfAdaptationMod.id(name))
		);
	}

	public static void register() {
		WheelOfAdaptationMod.LOGGER.info("Registering sounds for " + WheelOfAdaptationMod.MOD_ID);
	}
}
