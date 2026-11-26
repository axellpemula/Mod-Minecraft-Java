package com.wheelofadaptation.adaptation;

import com.wheelofadaptation.WheelOfAdaptationMod;
import com.wheelofadaptation.item.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * Handler damage untuk sistem adaptasi Wheel (Fase 1).
 *
 * Alur (sesuai Bedrock):
 *  - Tidak pakai Wheel di kepala -> damage normal.
 *  - Pakai Wheel + tipe belum adapted -> kena damage, progress Analysing (+1 hit).
 *    Hit ke-3 -> Adapted (toast "Adapted", particle, sound, heal).
 *  - Pakai Wheel + tipe sudah Adapted -> damage di-nullify (return false),
 *    heal kecil + toast "Adapted".
 *
 * Fase 2: Evolved, effect flip, attack adaptation, persistensi, HUD.
 */
public class WheelDamageHandler {

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof Player player)) {
				return true;
			}
			if (player.getItemBySlot(EquipmentSlot.HEAD).getItem() != ModItems.WHEEL_OF_ADAPTATION) {
				return true;
			}

			AdaptationType type = AdaptationType.fromDamageSource(source);
			PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());

			// Sudah adapted -> nullify damage
			if (data.isAdapted(type)) {
				player.heal(1.0F);
				player.displayClientMessage(
						Component.literal("Adapted: " + type.getDisplayName()),
						true
				);
				spawnAdaptParticles(player);
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
				spawnAdaptParticles(player);
				player.level().playSound(
						null,
						player.blockPosition(),
						SoundEvents.BEACON_ACTIVATE,
						net.minecraft.sounds.SoundSource.PLAYERS,
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
			}
			return true;
		});
	}

	private static void spawnAdaptParticles(Player player) {
		if (player.level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(
					ParticleTypes.END_ROD,
					player.getX(),
					player.getY() + 2.0,
					player.getZ(),
					20,
					0.5,
					0.5,
					0.5,
					0.1
			);
		}
	}
}
