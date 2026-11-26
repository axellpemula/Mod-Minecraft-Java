package com.wheelofadaptation.particle;

import com.wheelofadaptation.WheelOfAdaptationMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

/**
 * Particle custom (Fase 4): texture adapt_wheel.png dari RP Bedrock.
 * Digunakan saat Adapted/Evolved/RBD (mengganti sebagian END_ROD vanilla).
 */
public class ModParticles {

	public static final SimpleParticleType ADAPT_WHEEL = register("adapt_wheel");

	private static SimpleParticleType register(String name) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WheelOfAdaptationMod.MOD_ID, name);
		// SimpleParticleType ctor protected -> pakai anonymous subclass (pola umum mod Fabric)
		return net.minecraft.core.Registry.register(BuiltInRegistries.PARTICLE_TYPE, id, new SimpleParticleType(false) {
		});
	}

	public static void register() {
		WheelOfAdaptationMod.LOGGER.info("Registering particles for " + WheelOfAdaptationMod.MOD_ID);
	}
}
