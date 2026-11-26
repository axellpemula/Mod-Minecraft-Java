package com.wheelofadaptation.adaptation;

import com.wheelofadaptation.WheelOfAdaptationMod;
import com.wheelofadaptation.sound.ModSounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Handler damage untuk sistem adaptasi Wheel (Fase 2).
 *
 * Alur (dari Bedrock, disesuaikan Java):
 *  - Tidak equip Wheel di slot khusus -> damage normal.
 *  - Equip Wheel + tipe belum adapted -> kena damage, progress Analysing (+1 hit).
 *    Hit ke-3 -> Adapted (toast, particle, sound, heal).
 *  - Equip Wheel + tipe sudah Adapted -> damage di-nullify, heal kecil.
 *    Hit lanjutan -> progress Evolved. Hit ke-5 -> Evolved (II) (efek lebih kuat).
 *
 * Mode:
 *  - DEFENSE: heal nullify lebih besar.
 *  - ATTACK/BALANCE: heal normal.
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

			AdaptationType type = AdaptationType.fromDamageSource(source);
			PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());

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

				// Progress Evolved (hanya jika belum evolved)
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

	private static void spawnAdaptParticles(Player player, boolean lowFx) {
		if (player.level() instanceof ServerLevel serverLevel) {
			int count = lowFx ? 8 : 20;
			serverLevel.sendParticles(
					ParticleTypes.END_ROD,
					player.getX(),
					player.getY() + 2.0,
					player.getZ(),
					count,
					0.5,
					0.5,
					0.5,
					0.1
			);
		}
	}
}
