package com.axell.hardcoretools.command;

import com.axell.hardcoretools.feature.ActionDispatcher;
import com.axell.hardcoretools.feature.TeleportFeature;
import com.axell.hardcoretools.feature.WeaponDamageFeature;
import com.axell.hardcoretools.network.HardcoreNetworking;
import com.axell.hardcoretools.state.HardcoreStateManager;
import com.axell.hardcoretools.state.WaypointEntry;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
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
			// /vs & /hc (permission level 0 — works in Hardcore & Survival without Cheats!)
			for (String rootName : new String[]{"vs", "hc"}) {
				LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal(rootName)
						.executes(HardcoreCommands::openDashboard)
						.then(CommandManager.literal("config").executes(HardcoreCommands::openDashboard))
						.then(CommandManager.literal("back").executes(HardcoreCommands::warpBack))
						.then(CommandManager.literal("damage")
								.then(CommandManager.literal("reset").executes(HardcoreCommands::resetHeldDamage))
								.then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.0, WeaponDamageFeature.MAX_CUSTOM_DAMAGE))
										.executes(ctx -> setHeldDamage(ctx, DoubleArgumentType.getDouble(ctx, "amount"), null))
										.then(CommandManager.argument("speed", DoubleArgumentType.doubleArg(WeaponDamageFeature.MIN_CUSTOM_SPEED, WeaponDamageFeature.MAX_CUSTOM_SPEED))
												.executes(ctx -> setHeldDamage(ctx,
														DoubleArgumentType.getDouble(ctx, "amount"),
														DoubleArgumentType.getDouble(ctx, "speed"))))))
						.then(buildGameModeBranch("gamemode"))
						.then(buildGameModeBranch("gm"));
				dispatcher.register(root);
			}

			// /vs:config & /hc:config parity aliases
			dispatcher.register(CommandManager.literal("vs:config").executes(HardcoreCommands::openDashboard));
			dispatcher.register(CommandManager.literal("hc:config").executes(HardcoreCommands::openDashboard));

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
			ctx.getSource().sendError(Text.literal("You do not have permission to use Void Sovereign on this server.").formatted(Formatting.RED));
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
			ctx.getSource().sendError(Text.literal("Only players can open Void Sovereign."));
			return 0;
		}
		if (!HardcoreStateManager.canUseTools(player)) {
			ctx.getSource().sendError(Text.literal("You do not have permission to use Void Sovereign on this server.").formatted(Formatting.RED));
			return 0;
		}
		HardcoreNetworking.sendOpenScreen(player);
		return 1;
	}

	private static int warpBack(CommandContext<ServerCommandSource> ctx) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendError(Text.literal("Only players can use /vs back."));
			return 0;
		}
		if (!HardcoreStateManager.canUseTools(player)) {
			ctx.getSource().sendError(Text.literal("You do not have permission to use Void Sovereign on this server.").formatted(Formatting.RED));
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

	private static int setHeldDamage(CommandContext<ServerCommandSource> ctx, double amount, Double speedOverride) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendError(Text.literal("Only players can customize held weapon damage."));
			return 0;
		}
		if (!HardcoreStateManager.canUseTools(player)) {
			ctx.getSource().sendError(Text.literal("You do not have permission to use Void Sovereign on this server.").formatted(Formatting.RED));
			return 0;
		}
		int slot = player.getInventory().selectedSlot;
		ItemStack held = player.getMainHandStack();
		if (held.isEmpty()) {
			player.sendMessage(Text.literal("✦ Hold a weapon or item in your main hand first.").formatted(Formatting.RED), true);
			return 0;
		}
		WeaponDamageFeature.WeaponStats cur = WeaponDamageFeature.readCurrentStackStats(
				held,
				player.getServer() != null ? HardcoreStateManager.get(player.getServer()) : null
		);
		double spd = speedOverride != null ? speedOverride : cur.speed();
		WeaponDamageFeature.applyCustomStatsToSlot(player, slot, amount, spd);
		String msg = "⚔ Forged " + held.getName().getString() + " → " + WeaponDamageFeature.clampDamage(amount)
				+ " DMG · " + WeaponDamageFeature.clampSpeed(spd) + " SPD";
		player.sendMessage(Text.literal(msg).formatted(Formatting.GOLD), true);
		HardcoreNetworking.sendSyncState(player, msg, true);
		return 1;
	}

	private static int resetHeldDamage(CommandContext<ServerCommandSource> ctx) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendError(Text.literal("Only players can reset held weapon damage."));
			return 0;
		}
		if (!HardcoreStateManager.canUseTools(player)) {
			ctx.getSource().sendError(Text.literal("You do not have permission to use Void Sovereign on this server.").formatted(Formatting.RED));
			return 0;
		}
		int slot = player.getInventory().selectedSlot;
		ItemStack held = player.getMainHandStack();
		if (held.isEmpty()) {
			player.sendMessage(Text.literal("✦ Hold a weapon or item in your main hand first.").formatted(Formatting.RED), true);
			return 0;
		}
		WeaponDamageFeature.resetSlotWeaponToDefault(player, slot);
		String msg = "↺ Reset " + held.getName().getString() + " to default stats";
		player.sendMessage(Text.literal(msg).formatted(Formatting.AQUA), true);
		HardcoreNetworking.sendSyncState(player, msg, true);
		return 1;
	}
}
