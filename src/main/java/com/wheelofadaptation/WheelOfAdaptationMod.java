package com.wheelofadaptation;

import com.wheelofadaptation.adaptation.AdaptationManager;
import com.wheelofadaptation.adaptation.AdaptationSync;
import com.wheelofadaptation.adaptation.WheelAttackHandler;
import com.wheelofadaptation.adaptation.WheelDamageHandler;
import com.wheelofadaptation.adaptation.WheelTickHandler;
import com.wheelofadaptation.adaptation.WheelUseHandler;
import com.wheelofadaptation.block.ModBlocks;
import com.wheelofadaptation.command.WheelCommands;
import com.wheelofadaptation.item.ModItems;
import com.wheelofadaptation.itemgroup.ModItemGroups;
import com.wheelofadaptation.network.WheelNetworking;
import com.wheelofadaptation.particle.ModParticles;
import com.wheelofadaptation.screen.ModScreenHandlers;
import com.wheelofadaptation.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WheelOfAdaptationMod implements ModInitializer {
	public static final String MOD_ID = "wheel_of_adaptation";

	// Logger ini dipakai untuk menulis teks ke console dan file log.
	// Praktik terbaik: pakai mod id sebagai nama logger.
	// Supaya jelas mod mana yang menulis info, warning, dan error.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Kode ini jalan begitu Minecraft sudah siap load mod.
		// Tapi beberapa hal (seperti resources) mungkin belum ter-initialize.
		// Jadi hati-hati ya.

		LOGGER.info("Wheel of Adaptation loaded! Mahoraga's Dharma Wheel siap beradaptasi!");

		ModItems.register();
		ModBlocks.register();
		ModItemGroups.register();
		ModSounds.register();
		ModParticles.register();
		ModScreenHandlers.register();
		WheelDamageHandler.register();
		WheelTickHandler.register();
		WheelUseHandler.register();
		WheelAttackHandler.register();
		WheelCommands.register();
		WheelNetworking.register();

		// Load/save data player + sync ke client
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			AdaptationManager.loadPlayer(server, handler.getPlayer().getUUID());
			AdaptationSync.syncTo(handler.getPlayer());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			AdaptationManager.savePlayer(server, handler.getPlayer().getUUID());
		});
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
