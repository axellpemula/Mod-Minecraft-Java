package com.axell.hardcoretools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axell.hardcoretools.command.HardcoreCommands;
import com.axell.hardcoretools.feature.ProtectionHandler;
import com.axell.hardcoretools.feature.ReturnByDeathFeature;
import com.axell.hardcoretools.feature.SneakGrowHandler;
import com.axell.hardcoretools.feature.VeinMinerHandler;
import com.axell.hardcoretools.feature.WeaponDamageFeature;
import com.axell.hardcoretools.network.HardcoreNetworking;
import com.axell.hardcoretools.state.HardcoreStateManager;
import com.axell.hardcoretools.state.PlayerProfile;
import com.axell.hardcoretools.worldgen.HardcoreWorldGen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class HardcoreToolsMod implements ModInitializer {
	public static final String MOD_ID = "hardcore_tools";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		HardcoreNetworking.registerPayloads();
		HardcoreNetworking.registerServerReceivers();
		HardcoreWorldGen.register();
		HardcoreCommands.register();

		ProtectionHandler.register();
		ReturnByDeathFeature.register();
		VeinMinerHandler.register();
		SneakGrowHandler.register();
		WeaponDamageFeature.register();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			server.execute(() -> {
				PlayerProfile profile = HardcoreStateManager.getProfile(player);
				WeaponDamageFeature.syncPlayerInventory(player, HardcoreStateManager.get(server));
				if (profile.returnByDeath) {
					ReturnByDeathFeature.captureImmediateBaseline(player);
				}
				if (!profile.welcomed) {
					profile.welcomed = true;
					HardcoreStateManager.save(server);
					player.sendMessage(Text.translatable("hc.java.hint.join", "H"), false);
				}
				HardcoreNetworking.sendSyncState(player, "", true);
			});
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			ReturnByDeathFeature.onPlayerDisconnect(server, player);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ReturnByDeathFeature.abortAllActiveSessions(server);
			HardcoreStateManager.save(server);
			HardcoreStateManager.clearCache();
			ReturnByDeathFeature.clearAll();
		});

		LOGGER.info("Void Sovereign v1.7.5 initialized for Minecraft 1.21.1");
	}
}
