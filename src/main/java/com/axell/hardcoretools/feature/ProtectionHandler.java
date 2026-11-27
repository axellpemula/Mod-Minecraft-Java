package com.axell.hardcoretools.feature;

import java.util.ArrayList;
import java.util.List;

import com.axell.hardcoretools.state.HardcoreStateManager;
import com.axell.hardcoretools.state.PlayerProfile;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ProtectionHandler {
	private ProtectionHandler() {
	}

	public static void register() {
		// 1. Immortal Mode & Return by Death Active Rewind / Grace Shield
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity player) {
				PlayerProfile profile = HardcoreStateManager.getProfile(player);
				if (profile.immortal || ReturnByDeathFeature.isProtectedOrRewinding(player)) {
					player.setFireTicks(0);
					player.fallDistance = 0.0F;
					return false;
				}
			}
			return true;
		});

		// 2. Lethal Hit Priority Chain:
		//    (A) Immortal / Active Rewind Grace
		//    (B) Held Totem in Offhand or Mainhand (even when Auto-Totem is OFF -> let vanilla tryUseTotem pop it first!)
		//    (C) Auto-Totem from anywhere in inventory (when Auto-Totem is ON)
		//    (D) Return by Death (True 60s Chrono Rewind when no Totem could save the player!)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity player)) {
				return true;
			}
			PlayerProfile profile = HardcoreStateManager.getProfile(player);
			if (profile.immortal || ReturnByDeathFeature.isProtectedOrRewinding(player)) {
				player.setHealth(profile.immortal ? player.getMaxHealth() : Math.max(1.0F, player.getHealth()));
				return false;
			}

			boolean canUseTotemAgainstSource = !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
			boolean holdingTotemInHand = player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
					|| player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);

			// Priority 1: If player is holding a Totem in Offhand/Mainhand against normal lethal damage,
			// return true so vanilla LivingEntity#tryUseTotem consumes & pops that held Totem first!
			if (canUseTotemAgainstSource && holdingTotemInHand) {
				return true;
			}

			// Priority 2: Auto-Totem (searches entire inventory)
			if (profile.autoTotem && (canUseTotemAgainstSource || player.getY() < player.getWorld().getBottomY() - 64)) {
				ItemStack consumed = consumeOneTotem(player);
				if (!consumed.isEmpty()) {
					applyVanillaTotemPop(player, consumed);
					if (profile.autoTotemOffhand) {
						refillOffhandTotem(player);
					}
					int remaining = countTotems(player);
					player.sendMessage(
							Text.literal("✦ Auto-Totem ")
									.formatted(Formatting.GOLD)
									.append(Text.literal("saved your life! (" + remaining + " left)").formatted(Formatting.YELLOW)),
							true
					);
					return false;
				}
			}

			// Priority 3: Return by Death (Chrono Rewind to 1 minute ago!)
			if (profile.returnByDeath && ReturnByDeathFeature.tryTriggerReturnByDeath(player, source)) {
				return false;
			}

			return true;
		});

		// 3. Per-tick upkeep: Immortal full-restore + Auto-Totem Offhand Refill & Panic Low-HP Saver
		ServerTickEvents.END_SERVER_TICK.register(ProtectionHandler::onServerTick);
	}

	private static void onServerTick(MinecraftServer server) {
		int tick = server.getTicks();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!player.isAlive() || player.isSpectator()) continue;
			PlayerProfile profile = HardcoreStateManager.getProfile(player);

			if (profile.immortal) {
				if (player.getHealth() < player.getMaxHealth()) {
					player.setHealth(player.getMaxHealth());
				}
				player.setFireTicks(0);
				player.setAir(player.getMaxAir());
				player.fallDistance = 0.0F;
				if (tick % 10 == 0) {
					if (player.getHungerManager().getFoodLevel() < 20) {
						player.getHungerManager().setFoodLevel(20);
						player.getHungerManager().setSaturationLevel(20.0F);
					}
					clearHarmfulEffects(player);
				}
				// Rescue from void if falling deep below world bottom
				if (player.getY() < player.getWorld().getBottomY() - 32) {
					player.teleport(
							player.getServerWorld(),
							player.getX(),
							player.getWorld().getTopY() + 2,
							player.getZ(),
							player.getYaw(),
							player.getPitch()
					);
				}
			}

			if (profile.autoTotem && !ReturnByDeathFeature.isRewinding(player.getUuid())) {
				if (profile.autoTotemOffhand && tick % 5 == 0) {
					refillOffhandTotem(player);
				}
				if (profile.panicSaver && player.getHealth() > 0.0F && player.getHealth() <= profile.panicHpThreshold) {
					if (!player.hasStatusEffect(StatusEffects.REGENERATION) || player.getAbsorptionAmount() <= 1.0F) {
						ItemStack consumed = consumeOneTotem(player);
						if (!consumed.isEmpty()) {
							applyVanillaTotemPop(player, consumed);
							if (profile.autoTotemOffhand) {
								refillOffhandTotem(player);
							}
							int remaining = countTotems(player);
							player.sendMessage(
									Text.literal("✦ Panic Totem ")
											.formatted(Formatting.RED)
											.append(Text.literal("triggered at low HP! (" + remaining + " left)").formatted(Formatting.GRAY)),
									true
							);
						}
					}
				}
			}
		}
	}

	public static void clearHarmfulEffects(ServerPlayerEntity player) {
		List<RegistryEntry<net.minecraft.entity.effect.StatusEffect>> toRemove = new ArrayList<>();
		for (StatusEffectInstance inst : player.getStatusEffects()) {
			if (inst.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL) {
				toRemove.add(inst.getEffectType());
			}
		}
		for (var effect : toRemove) {
			player.removeStatusEffect(effect);
		}
	}

	private static void applyVanillaTotemPop(ServerPlayerEntity player, ItemStack totemCopy) {
		player.incrementStat(Stats.USED.getOrCreateStat(Items.TOTEM_OF_UNDYING));
		Criteria.USED_TOTEM.trigger(player, totemCopy);

		player.setHealth(Math.max(player.getHealth(), 4.0F));
		player.clearStatusEffects();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 1));
		player.setFireTicks(0);
		player.fallDistance = 0.0F;

		player.getWorld().sendEntityStatus(player, EntityStatuses.USE_TOTEM_OF_UNDYING);
		player.getWorld().playSound(
				null,
				player.getX(),
				player.getY(),
				player.getZ(),
				SoundEvents.ITEM_TOTEM_USE,
				SoundCategory.PLAYERS,
				1.0F,
				1.0F
		);
	}

	public static void refillOffhandTotem(ServerPlayerEntity player) {
		PlayerInventory inv = player.getInventory();
		ItemStack offhand = inv.offHand.get(0);
		if (!offhand.isEmpty()) {
			return;
		}
		// Prefer non-selected main inventory slots first so we don't steal active hotbar weapon unless needed
		for (int i = 9; i < inv.main.size(); i++) {
			ItemStack stack = inv.main.get(i);
			if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
				inv.offHand.set(0, stack.split(1));
				return;
			}
		}
		for (int i = 0; i < 9; i++) {
			if (i == inv.selectedSlot) continue;
			ItemStack stack = inv.main.get(i);
			if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
				inv.offHand.set(0, stack.split(1));
				return;
			}
		}
	}

	private static ItemStack consumeOneTotem(ServerPlayerEntity player) {
		PlayerInventory inv = player.getInventory();
		ItemStack offhand = inv.offHand.get(0);
		if (offhand.isOf(Items.TOTEM_OF_UNDYING)) {
			ItemStack copy = offhand.copyWithCount(1);
			offhand.decrement(1);
			return copy;
		}
		for (int i = 0; i < inv.main.size(); i++) {
			ItemStack stack = inv.main.get(i);
			if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
				ItemStack copy = stack.copyWithCount(1);
				stack.decrement(1);
				return copy;
			}
		}
		return ItemStack.EMPTY;
	}

	public static int countTotems(PlayerEntity player) {
		int total = 0;
		PlayerInventory inv = player.getInventory();
		for (ItemStack s : inv.offHand) {
			if (s.isOf(Items.TOTEM_OF_UNDYING)) total += s.getCount();
		}
		for (ItemStack s : inv.main) {
			if (s.isOf(Items.TOTEM_OF_UNDYING)) total += s.getCount();
		}
		return total;
	}
}
