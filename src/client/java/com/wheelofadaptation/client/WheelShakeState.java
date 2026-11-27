package com.wheelofadaptation.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.concurrent.ThreadLocalRandom;

/**
 * State camera shake sisi client (Fase 4).
 * Intensity diset dari payload server, di-decay tiap tick, dipakai CameraShakeMixin.
 */
public final class WheelShakeState {

	private static volatile boolean enabled = true;
	private static volatile float intensity = 0.0F;

	private WheelShakeState() {
	}

	public static void trigger(float amount) {
		if (!enabled) {
			return;
		}
		intensity = Math.min(1.5F, intensity + amount);
	}

	public static void setEnabled(boolean value) {
		enabled = value;
		if (!value) {
			intensity = 0.0F;
		}
	}

	public static float getIntensity() {
		return enabled ? intensity : 0.0F;
	}

	/** Offset acak -1..1 untuk satu frame. */
	public static float nextOffset() {
		return ThreadLocalRandom.current().nextFloat(-1.0F, 1.0F);
	}

	public static void registerTick() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (intensity > 0.0F) {
				intensity *= 0.82F;
				if (intensity < 0.005F) {
					intensity = 0.0F;
				}
			}
		});
	}
}
