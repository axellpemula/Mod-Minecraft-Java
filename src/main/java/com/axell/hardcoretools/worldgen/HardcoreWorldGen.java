package com.axell.hardcoretools.worldgen;

import java.util.List;

import com.axell.hardcoretools.HardcoreToolsMod;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class HardcoreWorldGen {
	private static final List<String> OVERWORLD_GENERAL_FEATURES = List.of(
			"ore_coal_plus",
			"ore_copper_plus",
			"ore_iron_plus",
			"ore_gold_plus",
			"ore_redstone_plus",
			"ore_lapis_plus",
			"ore_diamond_plus"
	);

	private static final List<String> NETHER_FEATURES = List.of(
			"ore_quartz_plus",
			"ore_nether_gold_plus",
			"ore_debris_plus"
	);

	private static final TagKey<Biome> IS_MOUNTAIN = TagKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", "is_mountain"));
	private static final TagKey<Biome> IS_HILL = TagKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", "is_hill"));

	private HardcoreWorldGen() {
	}

	public static void register() {
		for (String name : OVERWORLD_GENERAL_FEATURES) {
			RegistryKey<PlacedFeature> key = RegistryKey.of(
					RegistryKeys.PLACED_FEATURE,
					Identifier.of(HardcoreToolsMod.MOD_ID, name)
			);
			BiomeModifications.addFeature(
					BiomeSelectors.foundInOverworld(),
					GenerationStep.Feature.UNDERGROUND_ORES,
					key
			);
		}

		// Keep Emerald Plus natural: spawn only in Mountain & Hill biomes instead of every Overworld cave
		RegistryKey<PlacedFeature> emeraldKey = RegistryKey.of(
				RegistryKeys.PLACED_FEATURE,
				Identifier.of(HardcoreToolsMod.MOD_ID, "ore_emerald_plus")
		);
		BiomeModifications.addFeature(
				BiomeSelectors.tag(IS_MOUNTAIN).or(BiomeSelectors.tag(IS_HILL)),
				GenerationStep.Feature.UNDERGROUND_ORES,
				emeraldKey
		);

		for (String name : NETHER_FEATURES) {
			RegistryKey<PlacedFeature> key = RegistryKey.of(
					RegistryKeys.PLACED_FEATURE,
					Identifier.of(HardcoreToolsMod.MOD_ID, name)
			);
			BiomeModifications.addFeature(
					BiomeSelectors.foundInTheNether(),
					GenerationStep.Feature.UNDERGROUND_ORES,
					key
			);
		}
	}
}
