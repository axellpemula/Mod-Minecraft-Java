package com.axell.hardcoretools.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.axell.hardcoretools.state.HardcoreStateManager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

public final class WeaponDamageFeature {
	public static final double MAX_CUSTOM_DAMAGE = 1_000_000.0;
	public static final double MIN_CUSTOM_SPEED = 0.1;
	public static final double MAX_CUSTOM_SPEED = 100.0;

	private static final String NBT_CUSTOM_ITEM = "hc_custom_weapon";
	private static final String NBT_CUSTOM_DMG = "hc_custom_dmg";
	private static final String NBT_CUSTOM_SPD = "hc_custom_spd";
	private static final String NBT_CATALOG_SYNCED = "hc_catalog_synced";
	private static final String NBT_CATALOG_DMG = "hc_catalog_dmg";
	private static final String NBT_CATALOG_SPD = "hc_catalog_spd";

	public record WeaponStats(double damage, double speed, boolean hasAttackAttribute) {
	}

	public record CatalogWeaponEntry(
			String id,
			String name,
			String category,
			double vanillaDamage,
			double vanillaSpeed,
			double activeDamage,
			double activeSpeed,
			boolean overridden
	) {
	}

	public record BagWeaponEntry(
			int slot,
			String tag,
			String itemId,
			String name,
			double vanillaDamage,
			double vanillaSpeed,
			double currentDamage,
			double currentSpeed,
			boolean customItem,
			boolean catalogOverridden
	) {
	}

	private WeaponDamageFeature() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 == 0) {
				syncAllOnlineInventories(server);
			}
		});
	}

	public static double clampDamage(double val) {
		if (Double.isNaN(val) || Double.isInfinite(val)) return 1.0;
		return Math.max(0.0, Math.min(MAX_CUSTOM_DAMAGE, Math.round(val * 100.0) / 100.0));
	}

	public static double clampSpeed(double val) {
		if (Double.isNaN(val) || Double.isInfinite(val)) return 1.6;
		return Math.max(MIN_CUSTOM_SPEED, Math.min(MAX_CUSTOM_SPEED, Math.round(val * 100.0) / 100.0));
	}

	public static String classifyWeaponCategory(Item item) {
		if (item instanceof SwordItem) return "sword";
		if (item instanceof AxeItem || item instanceof MaceItem) return "axe_mace";
		if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof RangedWeaponItem || item instanceof TridentItem) {
			return "ranged";
		}
		if (item instanceof PickaxeItem || item instanceof ShovelItem || item instanceof HoeItem) {
			return "tool";
		}
		WeaponStats proto = getPrototypeStats(item);
		if (proto.hasAttackAttribute() && proto.damage() > 1.0) {
			return "sword";
		}
		return "";
	}

	public static boolean isCatalogWeapon(Item item) {
		return !classifyWeaponCategory(item).isEmpty();
	}

	public static boolean isRangedOnlyItem(Item item) {
		return item instanceof BowItem || item instanceof CrossbowItem
				|| (item instanceof RangedWeaponItem && !(item instanceof TridentItem));
	}

	public static AttributeModifiersComponent getBasePrototypeModifiers(Item item) {
		return item.getComponents().getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
	}

	public static WeaponStats getPrototypeStats(Item item) {
		if (item instanceof BowItem || item instanceof CrossbowItem) {
			return new WeaponStats(9.0, 4.0, false);
		}
		if (item instanceof RangedWeaponItem && !(item instanceof TridentItem)) {
			return new WeaponStats(8.0, 4.0, false);
		}
		double[] acc = new double[] { 1.0, 4.0, 0.0 };
		AttributeModifiersComponent comp = getBasePrototypeModifiers(item);
		comp.applyModifiers(EquipmentSlot.MAINHAND, (attr, mod) -> {
			if (attr.equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
				acc[0] += mod.value();
				acc[2] = 1.0;
			} else if (attr.equals(EntityAttributes.GENERIC_ATTACK_SPEED)) {
				acc[1] += mod.value();
			}
		});
		double dmg = Math.max(0.0, Math.round(acc[0] * 100.0) / 100.0);
		double spd = Math.max(0.1, Math.round(acc[1] * 100.0) / 100.0);
		return new WeaponStats(dmg, spd, acc[2] > 0.5);
	}

	public static WeaponStats readCurrentStackStats(ItemStack stack, HardcoreStateManager.WorldData worldData) {
		if (stack.isEmpty()) {
			return new WeaponStats(1.0, 4.0, false);
		}
		Item item = stack.getItem();
		WeaponStats proto = getPrototypeStats(item);
		if (isCustomItemWeapon(stack)) {
			NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
			double dmg = nbt.contains(NBT_CUSTOM_DMG) ? nbt.getDouble(NBT_CUSTOM_DMG) : proto.damage();
			double spd = nbt.contains(NBT_CUSTOM_SPD) ? nbt.getDouble(NBT_CUSTOM_SPD) : proto.speed();
			return new WeaponStats(clampDamage(dmg), clampSpeed(spd), true);
		}
		String id = Registries.ITEM.getId(item).toString();
		if (worldData != null && worldData.weaponStudioEnabled) {
			Double catDmg = worldData.weaponDamageOverrides.get(id);
			Double catSpd = worldData.weaponSpeedOverrides.get(id);
			if (catDmg != null || catSpd != null) {
				double d = catDmg != null ? clampDamage(catDmg) : proto.damage();
				double s = catSpd != null ? clampSpeed(catSpd) : proto.speed();
				return new WeaponStats(d, s, true);
			}
		}
		if (isRangedOnlyItem(item)) {
			return proto;
		}
		double[] acc = new double[] { 1.0, 4.0, 0.0 };
		AttributeModifiersComponent comp = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
		comp.applyModifiers(EquipmentSlot.MAINHAND, (attr, mod) -> {
			if (attr.equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
				acc[0] += mod.value();
				acc[2] = 1.0;
			} else if (attr.equals(EntityAttributes.GENERIC_ATTACK_SPEED)) {
				acc[1] += mod.value();
			}
		});
		double dmg = Math.max(0.0, Math.round(acc[0] * 100.0) / 100.0);
		double spd = Math.max(0.1, Math.round(acc[1] * 100.0) / 100.0);
		return new WeaponStats(dmg, spd, acc[2] > 0.5);
	}

	public static boolean isCustomItemWeapon(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		NbtComponent custom = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		return !custom.isEmpty() && custom.copyNbt().getBoolean(NBT_CUSTOM_ITEM);
	}

	private static boolean isCatalogSyncedStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		NbtComponent custom = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		return !custom.isEmpty() && custom.copyNbt().getBoolean(NBT_CATALOG_SYNCED);
	}

	private static void writeWeaponModifiers(ItemStack stack, double targetDamage, double targetSpeed) {
		AttributeModifiersComponent base = getBasePrototypeModifiers(stack.getItem());
		AttributeModifiersComponent updated = base
				.with(
						EntityAttributes.GENERIC_ATTACK_DAMAGE,
						new EntityAttributeModifier(
								Item.BASE_ATTACK_DAMAGE_MODIFIER_ID,
								targetDamage - 1.0,
								EntityAttributeModifier.Operation.ADD_VALUE
						),
						AttributeModifierSlot.MAINHAND
				)
				.with(
						EntityAttributes.GENERIC_ATTACK_SPEED,
						new EntityAttributeModifier(
								Item.BASE_ATTACK_SPEED_MODIFIER_ID,
								targetSpeed - 4.0,
								EntityAttributeModifier.Operation.ADD_VALUE
						),
						AttributeModifierSlot.MAINHAND
				);
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, updated);
	}

	public static boolean applyCustomStatsToSlot(ServerPlayerEntity player, int slot, double rawDamage, double rawSpeed) {
		ItemStack stack = EnchantFeature.getStackAtSlot(player, slot);
		if (stack.isEmpty()) return false;

		double dmg = clampDamage(rawDamage);
		double spd = clampSpeed(rawSpeed);
		writeWeaponModifiers(stack, dmg, spd);

		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
			nbt.putBoolean(NBT_CUSTOM_ITEM, true);
			nbt.putDouble(NBT_CUSTOM_DMG, dmg);
			nbt.putDouble(NBT_CUSTOM_SPD, spd);
			nbt.remove(NBT_CATALOG_SYNCED);
			nbt.remove(NBT_CATALOG_DMG);
			nbt.remove(NBT_CATALOG_SPD);
		});

		player.getInventory().markDirty();
		player.getWorld().playSound(
				null, player.getBlockPos(),
				SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 0.85F, 1.15F
		);
		return true;
	}

	public static boolean resetSlotWeaponToDefault(ServerPlayerEntity player, int slot) {
		ItemStack stack = EnchantFeature.getStackAtSlot(player, slot);
		if (stack.isEmpty()) return false;

		stripWeaponTags(stack);
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, getBasePrototypeModifiers(stack.getItem()));

		MinecraftServer server = player.getServer();
		if (server != null) {
			syncSingleStackWithCatalog(stack, HardcoreStateManager.get(server));
		}

		player.getInventory().markDirty();
		player.getWorld().playSound(
				null, player.getBlockPos(),
				SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 0.8F, 1.1F
		);
		return true;
	}

	private static void stripWeaponTags(ItemStack stack) {
		NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		if (comp.isEmpty()) return;
		NbtCompound copy = comp.copyNbt();
		copy.remove(NBT_CUSTOM_ITEM);
		copy.remove(NBT_CUSTOM_DMG);
		copy.remove(NBT_CUSTOM_SPD);
		copy.remove(NBT_CATALOG_SYNCED);
		copy.remove(NBT_CATALOG_DMG);
		copy.remove(NBT_CATALOG_SPD);
		if (copy.isEmpty()) {
			stack.remove(DataComponentTypes.CUSTOM_DATA);
		} else {
			stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(copy));
		}
	}

	public static void setCatalogWeaponOverride(MinecraftServer server, String itemId, Double damageOverride, Double speedOverride) {
		if (server == null || itemId == null || itemId.isBlank()) return;
		Identifier parsed = Identifier.tryParse(itemId);
		if (parsed == null || !Registries.ITEM.containsId(parsed)) return;

		Item item = Registries.ITEM.get(parsed);
		WeaponStats proto = getPrototypeStats(item);
		HardcoreStateManager.WorldData worldData = HardcoreStateManager.get(server);

		if (damageOverride != null) {
			double clamped = clampDamage(damageOverride);
			if (Math.abs(clamped - proto.damage()) < 1e-4) {
				worldData.weaponDamageOverrides.remove(parsed.toString());
			} else {
				worldData.weaponDamageOverrides.put(parsed.toString(), clamped);
			}
		}
		if (speedOverride != null) {
			double clampedSpd = clampSpeed(speedOverride);
			if (Math.abs(clampedSpd - proto.speed()) < 1e-4) {
				worldData.weaponSpeedOverrides.remove(parsed.toString());
			} else {
				worldData.weaponSpeedOverrides.put(parsed.toString(), clampedSpd);
			}
		}

		HardcoreStateManager.save(server);
		syncAllOnlineInventories(server);
	}

	public static void resetCatalogWeaponOverride(MinecraftServer server, String itemId) {
		if (server == null || itemId == null || itemId.isBlank()) return;
		HardcoreStateManager.WorldData worldData = HardcoreStateManager.get(server);
		worldData.weaponDamageOverrides.remove(itemId);
		worldData.weaponSpeedOverrides.remove(itemId);
		HardcoreStateManager.save(server);
		syncAllOnlineInventories(server);
	}

	public static int resetAllCatalogOverrides(MinecraftServer server) {
		if (server == null) return 0;
		HardcoreStateManager.WorldData worldData = HardcoreStateManager.get(server);
		int cleared = worldData.weaponDamageOverrides.size() + worldData.weaponSpeedOverrides.size();
		worldData.weaponDamageOverrides.clear();
		worldData.weaponSpeedOverrides.clear();
		worldData.globalWeaponMultiplier = 1.0;
		HardcoreStateManager.save(server);
		syncAllOnlineInventories(server);
		return cleared;
	}

	public static void syncAllOnlineInventories(MinecraftServer server) {
		if (server == null) return;
		HardcoreStateManager.WorldData worldData = HardcoreStateManager.get(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			syncPlayerInventory(player, worldData);
		}
	}

	public static void syncPlayerInventory(ServerPlayerEntity player, HardcoreStateManager.WorldData worldData) {
		if (player == null || worldData == null) return;
		PlayerInventory inv = player.getInventory();
		boolean changed = false;
		for (ItemStack s : inv.main) {
			if (syncSingleStackWithCatalog(s, worldData)) changed = true;
		}
		for (ItemStack s : inv.offHand) {
			if (syncSingleStackWithCatalog(s, worldData)) changed = true;
		}
		if (changed) {
			inv.markDirty();
		}
	}

	public static boolean syncSingleStackWithCatalog(ItemStack stack, HardcoreStateManager.WorldData worldData) {
		if (stack == null || stack.isEmpty() || isCustomItemWeapon(stack)) {
			return false;
		}
		Item item = stack.getItem();
		String id = Registries.ITEM.getId(item).toString();
		Double catDmg = worldData.weaponStudioEnabled ? worldData.weaponDamageOverrides.get(id) : null;
		Double catSpd = worldData.weaponStudioEnabled ? worldData.weaponSpeedOverrides.get(id) : null;

		if (catDmg == null && catSpd == null) {
			if (isCatalogSyncedStack(stack)) {
				stripWeaponTags(stack);
				stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, getBasePrototypeModifiers(item));
				return true;
			}
			return false;
		}

		WeaponStats proto = getPrototypeStats(item);
		double targetDmg = catDmg != null ? clampDamage(catDmg) : proto.damage();
		double targetSpd = catSpd != null ? clampSpeed(catSpd) : proto.speed();

		if (isCatalogSyncedStack(stack)) {
			NbtCompound cur = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
			double prevDmg = cur.getDouble(NBT_CATALOG_DMG);
			double prevSpd = cur.getDouble(NBT_CATALOG_SPD);
			if (Math.abs(prevDmg - targetDmg) < 1e-4 && Math.abs(prevSpd - targetSpd) < 1e-4) {
				return false;
			}
		}

		writeWeaponModifiers(stack, targetDmg, targetSpd);
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
			nbt.putBoolean(NBT_CATALOG_SYNCED, true);
			nbt.putDouble(NBT_CATALOG_DMG, targetDmg);
			nbt.putDouble(NBT_CATALOG_SPD, targetSpd);
		});
		return true;
	}

	/**
	 * Live damage modifier invoked from LivingEntity#damage when a ServerPlayerEntity attacks.
	 * Handles:
	 *  - Ranged projectiles (Bow, Crossbow, Firework Crossbow, Thrown Trident) for both Per-Item and Catalog overrides
	 *  - First-tick unsynced melee weapons when a Catalog override is active
	 *  - Global Weapon Multiplier
	 */
	public static float modifyPlayerAttackDamage(ServerPlayerEntity attacker, LivingEntity target, DamageSource source, float amount) {
		if (attacker == null || target == null || source == null || amount <= 0.0F) {
			return amount;
		}
		if (TimeStopFeature.isFlushingDeferredDamage()) {
			return amount;
		}
		MinecraftServer server = attacker.getServer();
		if (server == null) return amount;

		HardcoreStateManager.WorldData worldData = HardcoreStateManager.get(server);
		boolean isDirectMelee = (source.getSource() == attacker);

		ItemStack weapon = source.getWeaponStack();
		if ((weapon == null || weapon.isEmpty()) && source.getSource() instanceof TridentEntity trident) {
			weapon = trident.getItemStack();
		}
		if ((weapon == null || weapon.isEmpty()) && isDirectMelee) {
			weapon = attacker.getMainHandStack();
		}

		float modified = amount;

		if (weapon != null && !weapon.isEmpty()) {
			Item item = weapon.getItem();
			String itemId = Registries.ITEM.getId(item).toString();
			WeaponStats proto = getPrototypeStats(item);

			Double desiredDmg = null;
			if (isCustomItemWeapon(weapon)) {
				NbtCompound nbt = weapon.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
				if (nbt.contains(NBT_CUSTOM_DMG)) {
					desiredDmg = clampDamage(nbt.getDouble(NBT_CUSTOM_DMG));
				}
			} else if (worldData.weaponStudioEnabled && worldData.weaponDamageOverrides.containsKey(itemId)) {
				desiredDmg = clampDamage(worldData.weaponDamageOverrides.get(itemId));
			}

			if (!isDirectMelee) {
				// Ranged / projectile hit (Arrow, Crossbow bolt/rocket, Thrown Trident)
				if (desiredDmg != null) {
					if (source.getSource() instanceof TridentEntity || item instanceof TridentItem) {
						// Vanilla thrown trident base is 8.0F + Impaling bonus
						modified = Math.max(0.0F, desiredDmg.floatValue() + (modified - 8.0F));
					} else {
						double baseRef = Math.max(1.0, proto.damage());
						modified = (float) (modified * (desiredDmg / baseRef));
					}
				}
			} else {
				// Direct melee hit: if stack had not yet been synced to catalog override on this tick, sync & apply delta now
				if (!isCustomItemWeapon(weapon) && worldData.weaponStudioEnabled && desiredDmg != null && !isCatalogSyncedStack(weapon)) {
					syncSingleStackWithCatalog(weapon, worldData);
					modified = Math.max(0.0F, modified + (float) (desiredDmg - proto.damage()));
				}
			}
		}

		if (worldData.weaponStudioEnabled && Math.abs(worldData.globalWeaponMultiplier - 1.0) > 1e-4) {
			if (isDirectMelee || (weapon != null && !weapon.isEmpty())) {
				modified = (float) (modified * worldData.globalWeaponMultiplier);
			}
		}

		return Math.max(0.0F, modified);
	}

	public static List<CatalogWeaponEntry> collectWeaponCatalog(HardcoreStateManager.WorldData worldData) {
		List<CatalogWeaponEntry> list = new ArrayList<>();
		for (Item item : Registries.ITEM) {
			String cat = classifyWeaponCategory(item);
			if (cat.isEmpty()) continue;

			Identifier id = Registries.ITEM.getId(item);
			String idStr = id.toString();
			WeaponStats proto = getPrototypeStats(item);
			Double ovDmg = worldData != null ? worldData.weaponDamageOverrides.get(idStr) : null;
			Double ovSpd = worldData != null ? worldData.weaponSpeedOverrides.get(idStr) : null;
			boolean overridden = ovDmg != null || ovSpd != null;
			double activeDmg = ovDmg != null ? clampDamage(ovDmg) : proto.damage();
			double activeSpd = ovSpd != null ? clampSpeed(ovSpd) : proto.speed();

			list.add(new CatalogWeaponEntry(
					idStr,
					item.getName().getString(),
					cat,
					proto.damage(),
					proto.speed(),
					activeDmg,
					activeSpd,
					overridden
			));
		}

		list.sort(Comparator
				.comparingInt((CatalogWeaponEntry e) -> categoryRank(e.category()))
				.thenComparingInt(e -> tierRank(e.id()))
				.thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
		return list;
	}

	private static int categoryRank(String cat) {
		return switch (cat) {
			case "sword" -> 0;
			case "axe_mace" -> 1;
			case "ranged" -> 2;
			case "tool" -> 3;
			default -> 4;
		};
	}

	private static int tierRank(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		if (path.startsWith("wooden_")) return 0;
		if (path.startsWith("stone_")) return 1;
		if (path.startsWith("golden_")) return 2;
		if (path.startsWith("iron_")) return 3;
		if (path.startsWith("diamond_")) return 4;
		if (path.startsWith("netherite_")) return 5;
		if (path.equals("mace")) return 6;
		if (path.equals("trident")) return 7;
		if (path.equals("bow")) return 8;
		if (path.equals("crossbow")) return 9;
		return 20;
	}

	public static List<BagWeaponEntry> collectBagWeapons(ServerPlayerEntity player) {
		PlayerInventory inv = player.getInventory();
		HardcoreStateManager.WorldData worldData = player.getServer() != null
				? HardcoreStateManager.get(player.getServer())
				: null;
		List<BagWeaponEntry> out = new ArrayList<>();

		int[] ordered = new int[37];
		int idx = 0;
		ordered[idx++] = inv.selectedSlot;
		ordered[idx++] = 40; // Offhand
		for (int i = 0; i < 9; i++) {
			if (i != inv.selectedSlot) ordered[idx++] = i;
		}
		for (int i = 9; i < 36; i++) {
			ordered[idx++] = i;
		}

		for (int slot : ordered) {
			ItemStack stack = EnchantFeature.getStackAtSlot(player, slot);
			if (stack.isEmpty()) continue;
			boolean isMainhand = (slot == inv.selectedSlot);
			boolean isWeapon = isCatalogWeapon(stack.getItem()) || isCustomItemWeapon(stack);
			if (!isWeapon && !isMainhand) continue;

			Item item = stack.getItem();
			String itemId = Registries.ITEM.getId(item).toString();
			WeaponStats proto = getPrototypeStats(item);
			WeaponStats cur = readCurrentStackStats(stack, worldData);
			boolean custom = isCustomItemWeapon(stack);
			boolean catOv = !custom && worldData != null && worldData.weaponStudioEnabled
					&& (worldData.weaponDamageOverrides.containsKey(itemId) || worldData.weaponSpeedOverrides.containsKey(itemId));

			out.add(new BagWeaponEntry(
					slot,
					EnchantFeature.shortSlotTag(inv, slot),
					itemId,
					stack.getName().getString(),
					proto.damage(),
					proto.speed(),
					cur.damage(),
					cur.speed(),
					custom,
					catOv
			));
		}
		return out;
	}

	public static int resolveSelectedWeaponSlot(ServerPlayerEntity player, int preferredSlot) {
		List<BagWeaponEntry> bag = collectBagWeapons(player);
		if (bag.isEmpty()) return -1;
		for (BagWeaponEntry b : bag) {
			if (b.slot() == preferredSlot) return preferredSlot;
		}
		return bag.get(0).slot();
	}
}
