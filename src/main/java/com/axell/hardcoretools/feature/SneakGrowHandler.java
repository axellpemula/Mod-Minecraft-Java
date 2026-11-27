package com.axell.hardcoretools.feature;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.axell.hardcoretools.state.HardcoreStateManager;
import com.axell.hardcoretools.state.PlayerProfile;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class SneakGrowHandler {
	private static final Map<UUID, Boolean> WAS_SNEAKING = new HashMap<>();

	private SneakGrowHandler() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SneakGrowHandler::onServerTick);
	}

	private static void onServerTick(MinecraftServer server) {
		int tick = server.getTicks();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!player.isAlive() || player.isSpectator()) continue;
			PlayerProfile profile = HardcoreStateManager.getProfile(player);
			if (!profile.sneakGrow) {
				WAS_SNEAKING.remove(player.getUuid());
				continue;
			}

			boolean sneaking = player.isSneaking();
			boolean prev = WAS_SNEAKING.getOrDefault(player.getUuid(), false);
			WAS_SNEAKING.put(player.getUuid(), sneaking);

			if (!sneaking) {
				continue;
			}

			int interval = Math.max(1, 21 - profile.sneakGrowSpeed);
			boolean justTapped = !prev;
			if (!justTapped && (tick % interval != 0)) {
				continue;
			}

			pulseSneakGrow(player.getServerWorld(), player, profile);
		}
	}

	private static void pulseSneakGrow(ServerWorld world, ServerPlayerEntity player, PlayerProfile profile) {
		BlockPos origin = player.getBlockPos();
		int r = profile.sneakGrowRadius;
		int grownCount = 0;

		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if (grownCount >= 32) return;
					BlockPos pos = origin.add(dx, dy, dz);
					BlockState state = world.getBlockState(pos);
					if (state.isAir()) continue;
					// Skip grass block turf spam so only real crops, saplings, bushes, canes & modded plants grow
					if (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.NETHERRACK) || state.isOf(Blocks.MOSS_BLOCK)) {
						continue;
					}

					if (growBlockAt(world, pos, state, profile.sneakGrowInstant)) {
						grownCount++;
						if (profile.sneakGrowParticles) {
							world.spawnParticles(
									ParticleTypes.HAPPY_VILLAGER,
									pos.getX() + 0.5,
									pos.getY() + 0.5,
									pos.getZ() + 0.5,
									4,
									0.25,
									0.25,
									0.25,
									0.02
							);
						}
					}
				}
			}
		}
	}

	private static boolean growBlockAt(ServerWorld world, BlockPos pos, BlockState state, boolean instant) {
		Block block = state.getBlock();

		// 1. Standard Vanilla + Modded Fertilizable crops, saplings, fungi, berries, bamboo, cocoa, etc.
		if (block instanceof Fertilizable fertilizable) {
			if (!fertilizable.isFertilizable(world, pos, state)) {
				return false;
			}
			int passes = instant ? 6 : 1;
			boolean changed = false;
			for (int i = 0; i < passes; i++) {
				BlockState cur = world.getBlockState(pos);
				if (!(cur.getBlock() instanceof Fertilizable fert) || !fert.isFertilizable(world, pos, cur)) {
					break;
				}
				fert.grow(world, world.random, pos, cur);
				changed = true;
			}
			return changed;
		}

		// 2. Nether Wart
		if (block instanceof NetherWartBlock) {
			int age = state.get(NetherWartBlock.AGE);
			if (age < NetherWartBlock.MAX_AGE) {
				int next = instant ? NetherWartBlock.MAX_AGE : age + 1;
				world.setBlockState(pos, state.with(NetherWartBlock.AGE, next), Block.NOTIFY_LISTENERS);
				return true;
			}
			return false;
		}

		// 3. Sugar Cane & Cactus vertical column growth (up to 3 blocks high)
		if (block instanceof SugarCaneBlock || block instanceof CactusBlock) {
			BlockPos base = pos;
			while (world.getBlockState(base.down()).isOf(block)) {
				base = base.down();
			}
			int height = 1;
			BlockPos top = base;
			while (world.getBlockState(top.up()).isOf(block)) {
				top = top.up();
				height++;
			}
			if (height >= 3 || !world.getBlockState(top.up()).isAir()) {
				return false;
			}
			if (instant) {
				while (height < 3 && world.getBlockState(top.up()).isAir()) {
					top = top.up();
					world.setBlockState(top, block.getDefaultState(), Block.NOTIFY_ALL);
					height++;
				}
			} else {
				world.setBlockState(top.up(), block.getDefaultState(), Block.NOTIFY_ALL);
			}
			return true;
		}

		return false;
	}
}
