package com.wheelofadaptation.adaptation;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Tipe fenomena yang bisa diadaptasi Wheel.
 *
 * Dipetakan dari prefix Bedrock:
 *   env:  -> FIRE, LAVA, EXPLOSION, FALL, DROWNING, FREEZING, LIGHTNING, WITHER, NATURE
 *   mob:  -> MOB_ATTACK
 *   proj: -> PROJECTILE
 *   effect: -> EFFECT
 *   kind: -> WEAPON (sword/axe/spear/mace/trident/tool)
 *
 * Fase 1 menyederhanakan jadi 8 tipe. Detail per-entity (blaze, ghast, dll)
 * dan per-effect (poison, wither, dll) ditambah di Fase 2.
 */
public enum AdaptationType {
	FIRE("Fire"),
	LAVA("Lava"),
	EXPLOSION("Explosion"),
	FALL("Fall Damage"),
	PROJECTILE("Projectile"),
	MOB_ATTACK("Mob Attack"),
	EFFECT("Effect"),
	NATURE("Nature");

	private final String displayName;

	AdaptationType(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public static AdaptationType fromDamageSource(DamageSource source) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return FIRE;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return EXPLOSION;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			return FALL;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return PROJECTILE;
		}
		if (source.getEntity() != null) {
			return MOB_ATTACK;
		}
		return NATURE;
	}
}
