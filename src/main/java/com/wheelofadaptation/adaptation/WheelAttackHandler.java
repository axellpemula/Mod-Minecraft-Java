package com.wheelofadaptation.adaptation;

import com.wheelofadaptation.WheelOfAdaptationMod;
import com.wheelofadaptation.item.ModItems;
import com.wheelofadaptation.sound.ModSounds;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.item.ItemStack;

/**
 * Handler serangan (Fase 3):
 * - Attack adaptation: pukul entity berulang -> pierce (bonus damage).
 * - Purify: Sword vs Zombie Villager -> cure jadi Villager.
 */
public class WheelAttackHandler {

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			if (!(entity instanceof LivingEntity living)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (held.isEmpty()) {
				return InteractionResult.PASS;
			}

			// --- Purify: Sword vs Zombie Villager ---
			if (held.getItem() == ModItems.SWORD_OF_EXTERMINATION
					&& entity instanceof ZombieVillager zombieVillager) {
				cureZombieVillager(serverPlayer, zombieVillager);
				return InteractionResult.PASS;
			}

			// --- Attack adaptation (butuh Wheel + Sword/Barehand) ---
			boolean isWeapon = held.getItem() == ModItems.SWORD_OF_EXTERMINATION
					|| held.getItem() == ModItems.BAREHAND;
			if (!isWeapon || !AdaptationManager.hasWheel(player.getUUID())) {
				return InteractionResult.PASS;
			}

			String entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
					.getKey(entity.getType()).toString();
			// Nama cantik dari lang file (jalan juga untuk mob custom mod lain — auto-detect)
			String displayName = entity.getType().getName().getString();
			PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());

			if (data.isAttackAdapted(entityType)) {
				// Pierce: bonus damage (Bedrock attackBonus 0.3 -> Attack mode lebih besar)
				float bonus = data.getMode() == WheelMode.ATTACK ? 3.0F : 2.0F;
				living.hurt(player.damageSources().playerAttack(player), bonus);
				if (living.level() instanceof ServerLevel serverLevel) {
					serverLevel.sendParticles(
							ParticleTypes.CRIT,
							living.getX(), living.getY() + 1, living.getZ(),
							5, 0.3, 0.3, 0.3, 0.1
					);
				}
			} else {
				boolean justAdapted = data.recordAttackHit(entityType);
				if (justAdapted) {
					player.displayClientMessage(
							Component.literal("Attack adapted: " + displayName + " (pierces armor)"),
							true
					);
					player.level().playSound(null, player.blockPosition(), ModSounds.WHEEL_ADAPTED, SoundSource.PLAYERS, 0.8F, 1.0F);
					WheelOfAdaptationMod.LOGGER.info("Player {} attack-adapted to {}",
							player.getName().getString(), entityType);
				} else {
					player.displayClientMessage(
							Component.literal("Attack adapting: " + displayName
									+ " (" + data.getAttackHits(entityType) + "/" + PlayerAdaptationData.HITS_TO_ATTACK_ADAPT + ")"),
							true
					);
				}
			}
			return InteractionResult.PASS;
		});
	}

	private static void cureZombieVillager(ServerPlayer player, ZombieVillager zombieVillager) {
		ServerLevel level = player.serverLevel();
		Villager villager = new Villager(EntityType.VILLAGER, level);
		villager.moveTo(zombieVillager.getX(), zombieVillager.getY(), zombieVillager.getZ(),
				zombieVillager.getYRot(), zombieVillager.getXRot());
		level.addFreshEntity(villager);
		zombieVillager.discard();
		player.displayClientMessage(Component.literal("Cured!"), true);
		player.level().playSound(null, player.blockPosition(), ModSounds.PURIFY, SoundSource.PLAYERS, 1.0F, 1.0F);
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(
					ParticleTypes.HAPPY_VILLAGER,
					villager.getX(), villager.getY() + 1, villager.getZ(),
					15, 0.5, 0.5, 0.5, 0.1
			);
		}
		WheelOfAdaptationMod.LOGGER.info("Player {} purified a zombie villager", player.getName().getString());
	}
}
