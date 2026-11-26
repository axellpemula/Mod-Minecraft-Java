package com.wheelofadaptation.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/**
 * Particle "adapt wheel" — texture dari RP Bedrock (adapt_wheel.png).
 * Berputar di tempat, sedikit terangkat, untuk efek Adapted/Evolved/RBD.
 */
public class AdaptWheelParticle extends TextureSheetParticle {

	private final SpriteSet sprites;

	protected AdaptWheelParticle(ClientLevel level, double x, double y, double z,
			double vx, double vy, double vz, SpriteSet sprites) {
		super(level, x, y, z, vx, vy, vz);
		this.sprites = sprites;
		this.lifetime = 20 + this.random.nextInt(15);
		this.gravity = -0.02F; // terangkat pelan
		this.quadSize = 0.45F;
		this.setSpriteFromAge(sprites);
		this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0);
		this.oRoll = this.roll;
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
		this.oRoll = this.roll;
		this.roll += 0.35F; // spin roda
		// fade-out di akhir hidup
		if (this.age >= this.lifetime - 5) {
			this.quadSize *= 0.85F;
		}
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}
}
