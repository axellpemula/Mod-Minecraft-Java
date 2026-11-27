package com.axell.hardcoretools.command;

import com.axell.hardcoretools.feature.ActionDispatcher;
import com.axell.hardcoretools.feature.TeleportFeature;
import com.axell.hardcoretools.network.HardcoreNetworking;
import com.axell.hardcoretools.state.HardcoreStateManager;
import com.axell.hardcoretools.state.WaypointEntry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class HardcoreCommands {
	private static final String[] MODES = {"creative", "survival", "hardcore", "spectator", "adventure"};

	private HardcoreCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			// /hc, /hc config, /hc back, /hc gm <mode>, /hc gamemode <mode>
			// (permission level 0 — works in Hardcore & Survival without Cheats!)
			LiteralArgumentBuilder<ServerCommandSource> hcRoot = CommandManager.literal("hc")
					.executes(HardcoreCommands::openDashboard)
					.then(CommandManager.literal("config").executes(HardcoreCommands::openDashboard))
					.then(CommandManager.literal("back").executes(HardcoreCommands::warpBack))
					.then(buildGameModeBranch("gamemode"))
					.then(buildGameModeBranch("gm"));

			dispatcher.register(hcRoot);

			// /hc:config parity alias with Bedrock Edition
			dispatcher.register(
					CommandManager.literal("hc:config")
							.executes(HardcoreCommands::openDashboard)
			);

			// Extend Vanilla's /gamemode with a native "hardcore" literal!
			CommandNode<ServerCommandSource> vanillaGm = dispatcher.getRoot().getChild("gamemode");
			if (vanillaGm != null && vanillaGm.getChild("hardcore") == null) {
				vanillaGm.addChild(
						CommandManager.literal("hardcore")
								.executes(ctx -> switchMode(ctx, "hardcore"))
								.build()
				);
			}
		});
	}

	private static LiteralArgumentBuilder<ServerCommandSource> buildGameModeBranch(String branchLiteral) {
		LiteralArgumentBuilder<ServerCommandSource> branch = CommandManager.literal(branchLiteral);
		for (String m : MODES) {
			final String targetMode = m;
			branch.then(CommandManager.literal(targetMode).executes(ctx -> switchMode(ctx, targetMode)));
		}
		return branch;
	}

	private static int switchMode(CommandContext<ServerCommandSource> ctx, String mode) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendError(Text.literal("Only players can switch game mode."));
			return 0;
		}
		if (!HardcoreStateManager.canUseTools(player)) {
			ctx.getSource().sendError(Text.literal("You do not have permission to use Hardcore Tools on this server.").formatted(Formatting.RED));
			return 0;
		}
		String msg = ActionDispatcher.applyGameMode(player, mode);
		if (msg == null) {
			return 0;
		}
		player.sendMessage(Text.literal(msg).formatted("hardcore".equals(mode) ? Formatting.RED : Formatting.GOLD), true);
		HardcoreNetworking.sendSyncState(player, msg, true);
		return 1;
	}

	private static int openDashboard(CommandContext<ServerCommandSource> ctx) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendError(Text.literal("Only players can open Hardcore Tools."));
			return 0;
		}
		if (!HardcoreStateManager.canUseTools(player)) {
			ctx.getSource().sendError(Text.literal("You do not have permission to use Hardcore Tools on this server.").formatted(Formatting.RED));
			return 0;
		}
		HardcoreNetworking.sendOpenScreen(player);
		return 1;
	}

	private static int warpBack(CommandContext<ServerCommandSource> ctx) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendError(Text.literal("Only players can use /hc back."));
			return 0;
		}
		if (!HardcoreStateManager.canUseTools(player)) {
			ctx.getSource().sendError(Text.literal("You do not have permission to use Hardcore Tools on this server.").formatted(Formatting.RED));
			return 0;
		}
		WaypointEntry target = TeleportFeature.teleportBack(player);
		if (target == null) {
			player.sendMessage(Text.literal("✦ No previous teleport location saved yet.").formatted(Formatting.RED), true);
			return 0;
		}
		player.sendMessage(
				Text.literal("↩ Returned to ")
						.formatted(Formatting.GOLD)
						.append(Text.literal((int) target.x + ", " + (int) target.y + ", " + (int) target.z + " (" + target.dimension.replace("minecraft:", "") + ")").formatted(Formatting.YELLOW)),
				true
		);
		HardcoreNetworking.sendSyncState(player, "", true);
		return 1;
	}
}
