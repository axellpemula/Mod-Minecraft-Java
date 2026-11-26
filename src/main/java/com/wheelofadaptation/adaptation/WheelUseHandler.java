package com.wheelofadaptation.adaptation;

import com.wheelofadaptation.WheelOfAdaptationMod;
import com.wheelofadaptation.item.ModItems;
import com.wheelofadaptation.screen.WheelScreenHandler;
import com.wheelofadaptation.sound.ModSounds;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Handler klik kanan (UseItem) untuk item Wheel (Fase 3):
 * - Mode Switcher: ganti mode Attack/Balance/Defense
 * - Sword: Slash (potong entity + block) / Sneak+Use: Air Blast / Di udara: Dash
 * - Barehand: Teleport Kick / Sneak+Use: Guard
 * - Seal: Domain sederhana (bind entity di radius)
 * - Adaptation Config: buka Wheel Menu
 */
public class WheelUseHandler {

	private static final Set<String> UNCUTTABLE = Set.of(
			"minecraft:bedrock", "minecraft:barrier", "minecraft:command_block",
			"minecraft:chain_command_block", "minecraft:repeating_command_block",
			"minecraft:structure_block", "minecraft:structure_void", "minecraft:jigsaw",
			"minecraft:end_portal", "minecraft:end_portal_frame", "minecraft:end_gateway",
			"minecraft:portal", "minecraft:reinforced_deepslate"
	);

	public static void register() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (world.isClientSide) {
				return net.minecraft.util.TypedInteractionResult.pass(stack);
			}
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return net.minecraft.util.TypedInteractionResult.pass(stack);
			}

			// --- Mode Switcher ---
			if (stack.getItem() == ModItems.MODE_SWITCHER) {
				PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());
				if (!data.hasWheel()) {
					player.displayClientMessage(Component.literal("Equip the Wheel first (press R)"), true);
					return net.minecraft.util.TypedInteractionResult.consume(stack);
				}
				WheelMode next = data.getMode().next();
				data.setMode(next);
				player.displayClientMessage(Component.literal("Mode: " + next.getDisplayName()), true);
				player.level().playSound(null, player.blockPosition(), ModSounds.WHEEL_SPIN, SoundSource.PLAYERS, 0.8F, 1.0F);
				return net.minecraft.util.TypedInteractionResult.consume(stack);
			}

			// --- Adaptation Config: buka menu ---
			if (stack.getItem() == ModItems.ADAPTATION_CONFIG) {
				openWheelMenu(serverPlayer);
				return net.minecraft.util.TypedInteractionResult.consume(stack);
			}

			// --- Sword: Slash / Air Blast / Dash ---
			if (stack.getItem() == ModItems.SWORD_OF_EXTERMINATION) {
				PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());
				if (player.isCrouching()) {
					airBlast(serverPlayer, data);
				} else if (!player.onGround()) {
					dash(serverPlayer);
				} else {
					slash(serverPlayer, data);
				}
				return net.minecraft.util.TypedInteractionResult.consume(stack);
			}

			// --- Barehand: Teleport Kick / Guard ---
			if (stack.getItem() == ModItems.BAREHAND) {
				if (player.isCrouching()) {
					guard(serverPlayer);
				} else {
					teleportKick(serverPlayer);
				}
				return net.minecraft.util.TypedInteractionResult.consume(stack);
			}

			// --- Seal of Judgment ---
			if (stack.getItem() == ModItems.SEAL_OF_JUDGMENT) {
				seal(serverPlayer);
				return net.minecraft.util.TypedInteractionResult.consume(stack);
			}

			return net.minecraft.util.TypedInteractionResult.pass(stack);
		});
	}

	public static void openWheelMenu(ServerPlayer player) {
		player.openMenu(new MenuProvider() {
			@Override
			public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player menuPlayer) {
				return new WheelScreenHandler(syncId, inv, player);
			}

			@Override
			public Component getDisplayName() {
				return Component.literal("Wheel of Adaptation");
			}
		});
	}

	// --- Sword Slash: damage area di depan + potong block ---
	private static void slash(ServerPlayer player, PlayerAdaptationData data) {
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		double range = 12.0;
		double radius = 1.8;
		float damage = data.getMode() == WheelMode.ATTACK ? 13.0F : 10.0F;

		// Damage entity di cone depan
		for (Entity entity : player.level().getEntities(player,
				player.getBoundingBox().inflate(range))) {
			if (!(entity instanceof LivingEntity living) || entity == player) {
				continue;
			}
			Vec3 toEntity = entity.getEyePosition().subtract(eye);
			double dist = toEntity.length();
			if (dist > range) {
				continue;
			}
			// Cek sudut (dot product > 0.7 ~ 45 derajat)
			if (toEntity.normalize().dot(look) < 0.7) {
				continue;
			}
			living.hurt(player.damageSources().playerAttack(player), damage);
			// Knockback
			Vec3 kb = toEntity.normalize().scale(1.2);
			living.push(kb.x, 0.3, kb.z);
		}

		// Potong block di garis depan (jika setting aktif)
		if (data.isSlashCutsBlocks()) {
			int cut = 0;
			for (double d = 1.5; d <= 6.0 && cut < 32; d += 0.5) {
				BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
				BlockState state = player.level().getBlockState(pos);
				if (state.isAir()) {
					continue;
				}
				String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
				if (UNCUTTABLE.contains(blockId)) {
					continue;
				}
				player.level().destroyBlock(pos, true, player);
				cut++;
			}
		}

		// FX
		if (player.level() instanceof ServerLevel serverLevel) {
			for (int i = 0; i < 20; i++) {
				Vec3 p = eye.add(look.scale(2 + i * 0.4));
				serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
		}
		player.level().playSound(null, player.blockPosition(), ModSounds.SWORD_SLASH, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.level().playSound(null, player.blockPosition(), ModSounds.SLASH_HIT, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	// --- Air Blast: knockback besar di depan ---
	private static void airBlast(ServerPlayer player, PlayerAdaptationData data) {
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		double range = 10.0;

		for (Entity entity : player.level().getEntities(player,
				player.getBoundingBox().inflate(range))) {
			if (!(entity instanceof LivingEntity living) || entity == player) {
				continue;
			}
			Vec3 toEntity = entity.getEyePosition().subtract(eye);
			if (toEntity.length() > range || toEntity.normalize().dot(look) < 0.5) {
				continue;
			}
			Vec3 kb = toEntity.normalize().scale(2.5);
			living.push(kb.x, 0.8, kb.z);
			living.hurt(player.damageSources().playerAttack(player), 4.0F);
		}

		if (player.level() instanceof ServerLevel serverLevel) {
			for (int i = 0; i < 30; i++) {
				Vec3 p = eye.add(look.scale(1 + i * 0.3));
				serverLevel.sendParticles(ParticleTypes.GUST, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0.1);
			}
		}
		player.level().playSound(null, player.blockPosition(), ModSounds.AIR_BLAST_FIRE, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	// --- Seal of Judgment: domain sederhana ---
	private static void seal(ServerPlayer player) {
		PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());
		if (!data.hasWheel()) {
			player.displayClientMessage(Component.literal("Wear the Wheel first"), true);
			return;
		}
		if (data.getAdapted().size() < 3) {
			player.displayClientMessage(Component.literal("Needs 3 adapted phenomena (" + data.getAdapted().size() + "/3)"), true);
			return;
		}

		double radius = 8.0;
		// Bind entity di radius: slowness + quake damage (Bedrock: quake)
		float quakeDamage = data.getMode() == WheelMode.ATTACK ? 6.0F : 4.0F;
		for (Entity entity : player.level().getEntities(player,
				player.getBoundingBox().inflate(radius))) {
			if (!(entity instanceof LivingEntity living) || entity == player) {
				continue;
			}
			if (entity.distanceTo(player) > radius) {
				continue;
			}
			living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 4));
			living.hurt(player.damageSources().playerAttack(player), quakeDamage);
		}

		// FX: ring barrier
		if (player.level() instanceof ServerLevel serverLevel) {
			for (int i = 0; i < 40; i++) {
				double angle = i * Math.PI * 2 / 40;
				double x = player.getX() + Math.cos(angle) * radius;
				double z = player.getZ() + Math.sin(angle) * radius;
				serverLevel.sendParticles(ParticleTypes.END_ROD, x, player.getY() + 1, z, 1, 0, 0.5, 0, 0);
			}
		}
		player.level().playSound(null, player.blockPosition(), ModSounds.SEAL_OPEN, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.level().playSound(null, player.blockPosition(), ModSounds.SEAL_CHAIN, SoundSource.PLAYERS, 0.8F, 1.0F);
		if (!data.isLowFx()) {
			com.wheelofadaptation.network.WheelNetworking.shake(player, 0.5F);
		}
		player.displayClientMessage(Component.literal("Domain open"), true);
		WheelOfAdaptationMod.LOGGER.info("Player {} opened Seal domain", player.getName().getString());
	}

	// --- Dash: melesat ke depan (saat di udara + klik kanan Sword) ---
	private static void dash(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		player.push(look.x * 2.0, 0.2, look.z * 2.0);
		player.hurtMarked = true;
		if (player.level() instanceof ServerLevel serverLevel) {
			for (int i = 0; i < 15; i++) {
				Vec3 p = player.getEyePosition().subtract(look.scale(i * 0.3));
				serverLevel.sendParticles(ParticleTypes.GUST, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0.1);
			}
		}
		player.level().playSound(null, player.blockPosition(), ModSounds.DASH, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.displayClientMessage(Component.literal("Dash!"), true);
	}

	// --- Guard: Resistance + slowness sesaat (Barehand sneak+use) ---
	private static void guard(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 2));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
		if (player.level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(
					ParticleTypes.CLOUD,
					player.getX(), player.getY() + 1.2, player.getZ(),
					10, 0.5, 0.5, 0.5, 0.05
			);
		}
		player.level().playSound(null, player.blockPosition(), ModSounds.WHEEL_NULLIFY, SoundSource.PLAYERS, 0.8F, 1.0F);
		player.displayClientMessage(Component.literal("Guard!"), true);
	}

	// --- Teleport Kick: teleport ke musuh terdekat + tendang (Barehand use) ---
	private static void teleportKick(ServerPlayer player) {
		Entity target = null;
		double bestDist = 16.0;
		for (Entity entity : player.level().getEntities(player,
				player.getBoundingBox().inflate(16.0))) {
			if (!(entity instanceof LivingEntity) || entity == player) {
				continue;
			}
			double dist = entity.distanceTo(player);
			if (dist < bestDist) {
				bestDist = dist;
				target = entity;
			}
		}
		if (target == null) {
			player.displayClientMessage(Component.literal("No target in range"), true);
			return;
		}

		// Teleport ke posisi target (di belakangnya)
		Vec3 targetPos = target.getEyePosition();
		player.teleportTo(targetPos.x, targetPos.y - 1.5, targetPos.z);
		// Tendang: damage + knockback
		if (target instanceof LivingEntity living) {
			living.hurt(player.damageSources().playerAttack(player), 8.0F);
			Vec3 look = player.getLookAngle();
			living.push(look.x * 1.5, 0.5, look.z * 1.5);
		}
		if (player.level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(
					ParticleTypes.CRIT,
					target.getX(), target.getY() + 1, target.getZ(),
					10, 0.3, 0.3, 0.3, 0.1
			);
		}
		player.level().playSound(null, player.blockPosition(), ModSounds.TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.displayClientMessage(Component.literal("Teleport Kick!"), true);
	}
}
