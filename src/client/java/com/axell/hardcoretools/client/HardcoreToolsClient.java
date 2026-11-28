package com.axell.hardcoretools.client;

import org.lwjgl.glfw.GLFW;

import com.axell.hardcoretools.HardcoreToolsMod;
import com.axell.hardcoretools.client.gui.HardcoreDashboardScreen;
import com.axell.hardcoretools.client.gui.RadialWheelScreen;
import com.axell.hardcoretools.client.mixin.ClientWorldPropertiesAccessor;
import com.axell.hardcoretools.network.HardcoreNetworking;
import com.google.gson.JsonObject;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class HardcoreToolsClient implements ClientModInitializer {
	public static KeyBinding openDashboardKey;
	public static KeyBinding toggleFullbrightKey;
	public static KeyBinding radialWheelKey;

	public static KeyBinding getRadialWheelKey() {
		return radialWheelKey;
	}

	public static String getKeyLabel(KeyBinding kb, String fallback) {
		if (kb == null || kb.isUnbound()) {
			return "Unbound";
		}
		String raw = kb.getBoundKeyLocalizedText().getString();
		return (raw == null || raw.isBlank()) ? fallback : raw.toUpperCase();
	}

	@Override
	public void onInitializeClient() {
		openDashboardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.hardcore_tools.open_menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				"category.hardcore_tools"
		));

		toggleFullbrightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.hardcore_tools.toggle_fullbright",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				"category.hardcore_tools"
		));

		radialWheelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.hardcore_tools.radial_wheel",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				"category.hardcore_tools"
		));

		ClientPlayNetworking.registerGlobalReceiver(HardcoreNetworking.SyncStateS2CPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				ClientStateSnapshot.INSTANCE.applyJson(payload.json());
				applyClientWorldHardcore(context.client());
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(HardcoreNetworking.OpenScreenS2CPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				ClientStateSnapshot.INSTANCE.applyJson(payload.json());
				applyClientWorldHardcore(context.client());
				if (!ReturnByDeathClientController.isActive()
						&& !(context.client().currentScreen instanceof HardcoreDashboardScreen)) {
					context.client().setScreen(new HardcoreDashboardScreen());
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(HardcoreNetworking.RewindEventS2CPayload.ID, (payload, context) -> {
			context.client().execute(() -> ReturnByDeathClientController.handleServerPayload(payload.json()));
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			client.execute(() -> {
				ReturnByDeathClientController.reset();
				CombatAimClientController.reset();
				ClientStateSnapshot.INSTANCE.resetOnDisconnect();
			});
		});

		CoreShaderRegistrationCallback.EVENT.register(context -> {
			context.register(
					Identifier.of(HardcoreToolsMod.MOD_ID, "chrono_grayscale"),
					VertexFormats.POSITION_TEXTURE_COLOR,
					ReturnByDeathClientController::setChronoGrayscaleShader
			);
		});

		HudRenderCallback.EVENT.register(ReturnByDeathClientController::renderAct3HudOverlay);
		HudRenderCallback.EVENT.register(CombatAimClientController::renderAimHud);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			applyClientWorldHardcore(client);
			ReturnByDeathClientController.onClientTick(client);
			if (ReturnByDeathClientController.isActive()) {
				return;
			}

			while (openDashboardKey.wasPressed()) {
				if (client.player == null) continue;
				if (client.currentScreen instanceof HardcoreDashboardScreen) {
					client.setScreen(null);
				} else if (ClientPlayNetworking.canSend(HardcoreNetworking.ActionC2SPayload.ID)) {
					ClientPlayNetworking.send(new HardcoreNetworking.ActionC2SPayload("request_open", "{}"));
				}
			}

			while (radialWheelKey.wasPressed()) {
				if (client.player == null) continue;
				if (client.currentScreen instanceof RadialWheelScreen) {
					client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 0.95F));
					client.setScreen(null);
				} else if (client.currentScreen == null) {
					if (ClientPlayNetworking.canSend(HardcoreNetworking.ActionC2SPayload.ID)) {
						ClientPlayNetworking.send(new HardcoreNetworking.ActionC2SPayload("refresh", "{}"));
					}
					client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.25F));
					client.setScreen(new RadialWheelScreen());
				}
			}

			while (toggleFullbrightKey.wasPressed()) {
				if (client.player == null) continue;
				boolean next = !ClientStateSnapshot.INSTANCE.fullbright;
				ClientStateSnapshot.INSTANCE.fullbright = next;
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, next ? 1.2F : 0.9F));
				client.inGameHud.setOverlayMessage(
						Text.literal("☀ Fullbright: ").formatted(Formatting.GOLD)
								.append(Text.literal(next ? "ON" : "OFF").formatted(next ? Formatting.GREEN : Formatting.RED)),
						false
				);
				if (ClientPlayNetworking.canSend(HardcoreNetworking.ActionC2SPayload.ID)) {
					JsonObject obj = new JsonObject();
					obj.addProperty("value", next);
					ClientPlayNetworking.send(new HardcoreNetworking.ActionC2SPayload("toggle_fullbright", obj.toString()));
				}
			}
		});
	}

	private static void applyClientWorldHardcore(MinecraftClient client) {
		if (client == null || client.world == null || !ClientStateSnapshot.INSTANCE.hasSyncedWorldHardcore) {
			return;
		}
		if (client.world.getLevelProperties() instanceof ClientWorldPropertiesAccessor accessor) {
			accessor.hardcoreTools$setHardcore(ClientStateSnapshot.INSTANCE.worldHardcore);
		}
	}
}
