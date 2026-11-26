package com.wheelofadaptation.adaptation;

import com.wheelofadaptation.WheelOfAdaptationMod;
import com.wheelofadaptation.item.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ItemDisplay;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tick handler server (Fase 3/4):
 * - Roda 3D: ItemDisplay Wheel di atas kepala (model 3D emas, spin pelan idle, cepat saat Analysing).
 * - Animasi scan: ring particle berputar di atas kepala saat Analysing.
 * - Effect flip: efek negatif -> positif jika sudah adapted ke EFFECT.
 * - Record posisi untuk RBD tiap 20 tick.
 * - Auto-save data tiap 100 tick.
 */
public class WheelTickHandler {

	private static int tickCounter = 0;
	private static final Map<UUID, ItemDisplay> WHEEL_DISPLAYS = new ConcurrentHashMap<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			boolean doSave = tickCounter % 100 == 0;
			boolean doRecord = tickCounter % 20 == 0;

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				boolean hasWheel = AdaptationManager.hasWheel(player.getUUID());
				PlayerAdaptationData data = AdaptationManager.getOrCreate(player.getUUID());

				// --- Roda 3D di atas kepala ---
				updateWheelDisplay(player, hasWheel, data);

				if (!hasWheel) {
					continue;
				}

				// --- Record posisi untuk RBD ---
				if (doRecord) {
					data.recordPosition(
							player.getX(), player.getY(), player.getZ(),
							player.level().dimension().location().toString(),
							server.getTickCount()
					);
				}

				// --- Animasi scan: ring particle di atas kepala ---
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
					serverLevel.sendParticles(
							ParticleTypes.END_ROD,
							cx, cy + 0.5, cz,
							1, 0.1, 0.3, 0.1, 0
					);
					// Particle custom roda adaptasi (spin pelan mengikuti ring)
					if (tickCounter % 10 == 0) {
						serverLevel.sendParticles(
								com.wheelofadaptation.particle.ModParticles.ADAPT_WHEEL,
								cx, cy + 0.4, cz,
								1, 0.15, 0.05, 0.15, 0
						);
					}
				}

				// --- Effect flip (Bedrock: poison/wither -> regen, dll) ---
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

	private static void updateWheelDisplay(ServerPlayer player, boolean hasWheel, PlayerAdaptationData data) {
		UUID id = player.getUUID();
		ItemDisplay display = WHEEL_DISPLAYS.get(id);

		if (!hasWheel) {
			if (display != null && !display.isRemoved()) {
				display.discard();
			}
			WHEEL_DISPLAYS.remove(id);
			return;
		}

		// Buat display jika belum ada / sudah terbuang.
		// Roda 3D emas (model elements) di-scale 0.35 + recenter, jadi pas mengambang di atas kepala.
		if (display == null || display.isRemoved()) {
			display = new ItemDisplay(EntityType.ITEM_DISPLAY, player.level());
			spawnConfigured(display, player.getX(), player.getY() + 2.5, player.getZ());
			player.level().addFreshEntity(display);
			WHEEL_DISPLAYS.put(id, display);
			WheelOfAdaptationMod.LOGGER.debug("Spawned wheel display for {}", player.getName().getString());
		}

		// Update posisi + rotasi (spin cepat saat Analysing, pelan saat idle)
		boolean analysing = false;
		for (AdaptationType type : AdaptationType.values()) {
			if (data.getHits(type) > 0 && !data.isAdapted(type)) {
				analysing = true;
				break;
			}
		}
		float speed = analysing ? 12.0F : 2.0F;
		display.setPos(player.getX(), player.getY() + 2.5, player.getZ());
		display.setYRot((tickCounter * speed) % 360.0F);
		display.setXRot(analysing ? 30.0F : 0.0F);
	}

	/**
	 * Konfigurasi ItemDisplay via NBT (API vanilla yang stabil di 1.21.1):
	 * item Wheel, context none, transformation scale 0.35 + recenter.
	 * Fallback: sprite flat tanpa transformasi.
	 */
	private static void spawnConfigured(ItemDisplay display, double x, double y, double z) {
		CompoundTag nbt = new CompoundTag();

		ListTag pos = new ListTag();
		pos.add(DoubleTag.valueOf(x));
		pos.add(DoubleTag.valueOf(y));
		pos.add(DoubleTag.valueOf(z));
		nbt.put("Pos", pos);
		nbt.put("Motion", new ListTag());
		nbt.put("Rotation", new ListTag());

		CompoundTag itemTag = new CompoundTag();
		itemTag.putString("id", WheelOfAdaptationMod.MOD_ID + ":wheel_of_adaptation");
		itemTag.putInt("count", 1);
		nbt.put("item", itemTag);
		nbt.putString("display_context", "none");

		CompoundTag tr = new CompoundTag();
		tr.put("scale", floats(0.35F, 0.35F, 0.35F));
		// Recenter: model pusat (8,8,8) px = 0.5 block; setelah scale 0.35 -> offset 0.175
		tr.put("translation", floats(-0.175F, -0.175F, -0.175F));
		tr.put("left_rotation", identityRotation());
		tr.put("right_rotation", identityRotation());
		nbt.put("transformation", tr);

		try {
			display.load(nbt);
		} catch (Exception e) {
			WheelOfAdaptationMod.LOGGER.warn("Failed to configure wheel display NBT, using fallback", e);
		}
		if (display.getItemStack().isEmpty()) {
			display.setItemStack(new ItemStack(ModItems.WHEEL_OF_ADAPTATION));
			display.setPos(x, y, z);
		}
	}

	private static ListTag floats(float x, float y, float z) {
		ListTag list = new ListTag();
		list.add(FloatTag.valueOf(x));
		list.add(FloatTag.valueOf(y));
		list.add(FloatTag.valueOf(z));
		return list;
	}

	private static CompoundTag identityRotation() {
		CompoundTag rot = new CompoundTag();
		rot.putFloat("angle", 0.0F);
		rot.put("axis", floats(0.0F, 1.0F, 0.0F));
		rot.put("origin", floats(0.0F, 0.0F, 0.0F));
		return rot;
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
