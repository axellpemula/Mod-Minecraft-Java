package com.wheelofadaptation.adaptation;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Tick handler server (Fase 2/3):
 * - Animasi scan: ring particle berputar di atas kepala saat Analysing.
 * - Effect flip: efek negatif -> positif jika sudah adapted ke EFFECT.
 * - Auto-save data tiap 100 tick.
 */
public class WheelTickHandler {

	private static int tickCounter = 0;

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			boolean doSave = tickCounter % 100 == 0;

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!AdaptationManager.hasWheel(player.getUUID())) {
					continue;
				}
				PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());

				// --- Animasi scan: ring particle di atas kepala ---
				// Tampilkan jika ada tipe yang sedang Analysing (hits > 0 dan belum adapted).
				boolean analysing = false;
				for (AdaptationType type : AdaptationType.values()) {
					if (data.getHits(type) > 0 && !data.isAdapted(type)) {
						analysing = true;
						break;
					}
				}
				if (analysing && !data.isLowFx() && player.level() instanceof ServerLevel serverLevel) {
					double cx = player.getX();
					double cy = player.getY() + 2.6;
					double cz = player.getZ();
					double radius = 0.9;
					// Ring 12 particle berputar (offset pakai tickCounter)
					for (int i = 0; i < 12; i++) {
						double angle = (tickCounter * 0.15) + (i * Math.PI * 2 / 12);
						double x = cx + Math.cos(angle) * radius;
						double z = cz + Math.sin(angle) * radius;
						serverLevel.sendParticles(
								ParticleTypes.ELECTRIC_SPARK,
								x, cy, z,
								1, 0, 0, 0, 0
						);
					}
					// Beam scan dari atas
					serverLevel.sendParticles(
							ParticleTypes.END_ROD,
							cx, cy + 0.5, cz,
							1, 0.1, 0.3, 0.1, 0
					);
				}

				// --- Effect flip (Bedrock: poison/wither -> regen, dll) ---
				// Hanya jika sudah adapted ke EFFECT.
				if (data.isAdapted(AdaptationType.EFFECT)) {
					flipEffect(player, MobEffects.POISON, MobEffects.REGENERATION, 100, 0);
					flipEffect(player, MobEffects.WITHER, MobEffects.REGENERATION, 100, 0);
					flipEffect(player, MobEffects.SLOWNESS, MobEffects.MOVEMENT_SPEED, 100, 0);
					flipEffect(player, MobEffects.WEAKNESS, MobEffects.DAMAGE_BOOST, 100, 0);
					flipEffect(player, MobEffects.BLINDNESS, MobEffects.NIGHT_VISION, 300, 0);
					flipEffect(player, MobEffects.DARKNESS, MobEffects.NIGHT_VISION, 300, 0);
					flipEffect(player, MobEffects.HUNGER, MobEffects.SATURATION, 5, 0);
				}
			}

			if (doSave) {
				AdaptationManager.saveAll(server);
			}
		});
	}

	private static void flipEffect(ServerPlayer player,
			net.minecraft.world.effect.MobEffect negative,
			net.minecraft.world.effect.MobEffect positive,
			int duration, int amplifier) {
		if (player.hasEffect(negative)) {
			player.removeEffect(negative);
			player.addEffect(new MobEffectInstance(positive, duration, amplifier));
		}
	}
}
