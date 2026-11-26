package com.wheelofadaptation.adaptation;

import com.wheelofadaptation.WheelOfAdaptationMod;
import com.wheelofadaptation.sound.ModSounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Handler damage untuk sistem adaptasi Wheel (Fase 3).
 *
 * Alur:
 *  - Tidak equip Wheel -> damage normal.
 *  - Lethal damage + RBD ready + adapted>=3 -> Return by Death (cancel damage,
 *    teleport ke posisi 30 detik lalu, heal 50%, consume 1 adapted, cooldown 60s).
 *  - Tipe belum adapted -> Analysing (+1 hit). Hit ke-3 -> Adapted.
 *  - Tipe sudah Adapted -> nullify + heal. Hit lanjutan -> Evolved (II).
 */
public class WheelDamageHandler {

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof Player player)) {
				return true;
			}
			if (!AdaptationManager.hasWheel(player.getUUID())) {
				return true;
			}

			PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());

			// --- Return by Death: cegah lethal damage ---
			if (player instanceof ServerPlayer serverPlayer
					&& player.getHealth() - amount <= 0
					&& data.getAdapted().size() >= 3
					&& data.isRbdReady(serverPlayer.server.getTickCount())) {
				doReturnByDeath(serverPlayer, data);
				return false;
			}

			AdaptationType type = AdaptationType.fromDamageSource(source);

			// Sudah adapted -> nullify damage
			if (data.isAdapted(type)) {
				float heal = data.getMode() == WheelMode.DEFENSE ? 2.0F : 1.0F;
				if (data.isEvolved(type)) {
					heal += 1.0F;
				}
				player.heal(heal);
				player.displayClientMessage(
						Component.literal((data.isEvolved(type) ? "Evolved: " : "Adapted: ")
								+ type.getDisplayName()),
						true
				);
				spawnAdaptParticles(player, data.isLowFx());
				player.level().playSound(
						null,
						player.blockPosition(),
						ModSounds.WHEEL_NULLIFY,
						SoundSource.PLAYERS,
						0.7F,
						1.0F
				);

				// --- Reflect (Bedrock: Evolved memantulkan sebagian damage) ---
				if (data.isEvolved(type) && type == AdaptationType.MOB_ATTACK
						&& source.getEntity() instanceof LivingEntity attacker
						&& attacker.isAlive()) {
					attacker.hurt(player.damageSources().playerAttack(player), 2.0F);
				}

				if (!data.isEvolved(type)) {
					boolean justEvolved = data.recordEvolvedHit(type);
					if (justEvolved) {
						player.displayClientMessage(
								Component.literal("Evolved (II): " + type.getDisplayName()),
								true
						);
						player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
						spawnAdaptParticles(player, data.isLowFx());
						player.level().playSound(
								null,
								player.blockPosition(),
								ModSounds.WHEEL_ADAPTED,
								SoundSource.PLAYERS,
								1.0F,
								1.2F
						);
						if (player instanceof ServerPlayer serverPlayer && !data.isLowFx()) {
							com.wheelofadaptation.network.WheelNetworking.shake(serverPlayer, 0.6F);
						}
						WheelOfAdaptationMod.LOGGER.info(
								"Player {} evolved {} adaptation", player.getName().getString(), type
						);
					}
				}
				return false;
			}

			// Belum adapted -> catat hit, biarkan damage masuk
			boolean justAdapted = data.recordHit(type);
			if (justAdapted) {
				player.displayClientMessage(
						Component.literal("Adapted: " + type.getDisplayName()),
						true
				);
				player.heal(4.0F);
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
				spawnAdaptParticles(player, data.isLowFx());
				player.level().playSound(
						null,
						player.blockPosition(),
						ModSounds.WHEEL_ADAPTED,
						SoundSource.PLAYERS,
						1.0F,
						1.0F
				);
				if (player instanceof ServerPlayer serverPlayer && !data.isLowFx()) {
					com.wheelofadaptation.network.WheelNetworking.shake(serverPlayer, 0.35F);
				}
				WheelOfAdaptationMod.LOGGER.info(
						"Player {} adapted to {}", player.getName().getString(), type
				);
			} else {
				player.displayClientMessage(
						Component.literal("Adapting: " + type.getDisplayName()
								+ " (" + data.getHits(type) + "/" + PlayerAdaptationData.HITS_TO_ADAPT + ")"),
						true
				);
				player.level().playSound(
						null,
						player.blockPosition(),
						ModSounds.WHEEL_SPIN,
						SoundSource.PLAYERS,
						0.5F,
						1.0F
				);
			}
			return true;
		});
	}

	private static void doReturnByDeath(ServerPlayer player, PlayerAdaptationData data) {
		long now = player.server.getTickCount();
		PlayerAdaptationData.PositionEntry rewind = data.getRewindPosition();

		// Teleport ke posisi 30 detik lalu (jika ada history)
		if (rewind != null) {
			ServerLevel targetLevel = player.server.getLevel(
					net.minecraft.resources.ResourceKey.create(
							net.minecraft.core.registries.Registries.DIMENSION,
							net.minecraft.resources.ResourceLocation.parse(rewind.dimension())
					)
			);
			if (targetLevel != null) {
				player.teleportTo(targetLevel, rewind.x(), rewind.y(), rewind.z(),
						player.getYRot(), player.getXRot());
			}
		}

		// Heal 50% + efek
		player.setHealth(player.getMaxHealth() * 0.5F);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));

		// Consume 1 adapted (hapus satu tipe)
		if (!data.getAdapted().isEmpty()) {
			AdaptationType toRemove = data.getAdapted().iterator().next();
			data.getAdapted().remove(toRemove);
			data.getEvolved().remove(toRemove);
		}

		data.setRbdCooldown(now);
		player.displayClientMessage(Component.literal("Return by Death! Adaptations spent"), true);
		player.level().playSound(null, player.blockPosition(), ModSounds.WHEEL_UNDYING, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.level().playSound(null, player.blockPosition(), ModSounds.TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
		spawnAdaptParticles(player, data.isLowFx());
		if (!data.isLowFx()) {
			com.wheelofadaptation.network.WheelNetworking.shake(player, 1.0F);
		}
		WheelOfAdaptationMod.LOGGER.info("Player {} returned by death", player.getName().getString());
	}

	private static void spawnAdaptParticles(Player player, boolean lowFx) {
		if (player.level() instanceof ServerLevel serverLevel) {
			int count = lowFx ? 3 : 10;
			// Particle custom adapt_wheel (texture dari RP Bedrock)
			serverLevel.sendParticles(
					com.wheelofadaptation.particle.ModParticles.ADAPT_WHEEL,
					player.getX(),
					player.getY() + 2.0,
					player.getZ(),
					count,
					0.5,
					0.5,
					0.5,
					0.05
			);
			int endRod = lowFx ? 5 : 10;
			serverLevel.sendParticles(
					ParticleTypes.END_ROD,
					player.getX(),
					player.getY() + 2.0,
					player.getZ(),
					endRod,
					0.5,
					0.5,
					0.5,
					0.1
			);
		}
	}
}
