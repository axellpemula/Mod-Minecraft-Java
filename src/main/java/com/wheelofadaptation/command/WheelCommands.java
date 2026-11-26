package com.wheelofadaptation.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.wheelofadaptation.adaptation.AdaptationManager;
import com.wheelofadaptation.adaptation.AdaptationType;
import com.wheelofadaptation.adaptation.PlayerAdaptationData;
import com.wheelofadaptation.adaptation.WheelMode;
import com.wheelofadaptation.adaptation.WheelUseHandler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command /woa (Fase 2):
 * - /woa menu     -> buka Wheel Menu
 * - /woa list     -> list adaptasi
 * - /woa mode <attack|balance|defense>
 * - /woa reset    -> reset adaptasi
 * - /woa performance <low|medium|ultra> (lowFx on/off)
 * - /woa armor <on|off> (info saja Fase 2)
 * - /woa shake <on|off> (info saja Fase 2)
 */
public class WheelCommands {

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("woa")
					.then(Commands.literal("menu")
							.executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								WheelUseHandler.openWheelMenu(player);
								return 1;
							}))
					.then(Commands.literal("list")
							.executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());
								ctx.getSource().sendSuccess(() -> Component.literal("Adapted (" + data.getAdapted().size() + "):"), false);
								for (AdaptationType type : data.getAdapted()) {
									String label = (data.isEvolved(type) ? "II " : "") + type.getDisplayName();
									ctx.getSource().sendSuccess(() -> Component.literal(" - " + label), false);
								}
								return 1;
							}))
					.then(Commands.literal("mode")
							.then(Commands.argument("mode", StringArgumentType.word())
									.executes(ctx -> {
										ServerPlayer player = ctx.getSource().getPlayerOrException();
										String modeStr = StringArgumentType.getString(ctx, "mode").toUpperCase();
										try {
											WheelMode mode = WheelMode.valueOf(modeStr);
											PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());
											data.setMode(mode);
											ctx.getSource().sendSuccess(() -> Component.literal("Mode: " + mode.getDisplayName()), false);
										} catch (IllegalArgumentException e) {
											ctx.getSource().sendFailure(Component.literal("Mode harus attack/balance/defense"));
										}
										return 1;
									})))
					.then(Commands.literal("reset")
							.executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								AdaptationManager.resetAdaptations(player.getUUID());
								com.wheelofadaptation.adaptation.AdaptationSync.syncTo(player);
								ctx.getSource().sendSuccess(() -> Component.literal("Adaptations reset"), false);
								return 1;
							}))
					.then(Commands.literal("performance")
							.then(Commands.argument("level", StringArgumentType.word())
									.executes(ctx -> {
										ServerPlayer player = ctx.getSource().getPlayerOrException();
										String level = StringArgumentType.getString(ctx, "level").toLowerCase();
										PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());
										data.setLowFx(level.equals("low"));
										ctx.getSource().sendSuccess(() -> Component.literal("Effects: " + level.toUpperCase()), false);
										return 1;
									})))
				.then(Commands.literal("armor")
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							int pieces = 0;
							for (net.minecraft.world.item.ItemStack armorStack : player.getArmorSlots()) {
								if (armorStack.getItem() == com.wheelofadaptation.item.ModItems.MAHORAGA_CHEST
										|| armorStack.getItem() == com.wheelofadaptation.item.ModItems.MAHORAGA_LEGS
										|| armorStack.getItem() == com.wheelofadaptation.item.ModItems.MAHORAGA_BOOTS) {
									pieces++;
								}
							}
							int finalPieces = pieces;
							ctx.getSource().sendSuccess(() -> Component.literal(
									"Mahoraga armor terpasang: " + finalPieces + "/3 (texture & defense custom Bedrock)"), false);
							return 1;
						}))
				.then(Commands.literal("shake")
						.then(Commands.argument("state", StringArgumentType.word())
								.executes(ctx -> {
									CommandSourceStack src = ctx.getSource();
									String state = StringArgumentType.getString(ctx, "state").toLowerCase();
									ServerPlayer player = src.getPlayerOrException();
									boolean enabled = state.equals("on") || state.equals("true") || state.equals("1");
									com.wheelofadaptation.network.WheelNetworking.shakeConfig(player, enabled);
									src.sendSuccess(() -> Component.literal("Camera shake: " + (enabled ? "ON" : "OFF")), false);
									return 1;
								})))
			);
		});
	}
}
