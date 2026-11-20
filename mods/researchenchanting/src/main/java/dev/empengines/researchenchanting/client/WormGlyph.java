package dev.empengines.researchenchanting.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * One of vanilla's enchanting glyphs (same sprites and colours as the table's particles) travelling from a shelf
 * or chest to the table on a path that wriggles: it lifts in an arc and sways side to side and up and down like a
 * worm, faster at the start, settling as it reaches the book. Spawned with the target in the speed arguments.
 */
public final class WormGlyph extends TextureSheetParticle {
    private final double sx, sy, sz, ex, ey, ez;
    private final double sideX, sideZ, waves, phase, amp, lift;

    WormGlyph(ClientLevel level, double x, double y, double z, double tx, double ty, double tz, SpriteSet sprites) {
        super(level, x, y, z);
        sx = x; sy = y; sz = z;
        ex = tx; ey = ty; ez = tz;
        double dx = ex - sx, dz = ez - sz;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.0E-3) { sideX = 1; sideZ = 0; } else { sideX = -dz / flat; sideZ = dx / flat; }
        double dist = Math.sqrt(flat * flat + (ey - sy) * (ey - sy));
        double reach = Mth.clamp(dist / 2.0, 0.3, 1.5);
        lifetime = (int) (14 + dist * 9) + random.nextInt(8);
        waves = 0.8 + random.nextDouble() * 1.4;
        phase = random.nextDouble() * Math.PI * 2;
        amp = (0.10 + random.nextDouble() * 0.18) * reach;
        lift = (0.25 + random.nextDouble() * 0.45) * reach;
        quadSize = 0.07F + random.nextFloat() * 0.05F;
        float f = random.nextFloat() * 0.6F + 0.4F;
        rCol = 0.9F * f;
        gCol = 0.9F * f;
        bCol = f;
        alpha = 0;
        hasPhysics = false;
        xd = yd = zd = 0;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) { remove(); return; }
        double t = age / (double) lifetime;
        double e = 1 - (1 - t) * (1 - t);                 // quick off the shelf, slowing into the book
        double env = Math.sin(Math.PI * t);               // sway dies out at both ends
        double sway = Math.sin(Math.PI * 2 * waves * t + phase) * amp * env;
        double bob = Math.cos(Math.PI * 2 * waves * 0.7 * t + phase) * amp * 0.6 * env;
        x = sx + (ex - sx) * e + sideX * sway;
        z = sz + (ez - sz) * e + sideZ * sway;
        y = sy + (ey - sy) * e + lift * env + bob;
        alpha = (float) Math.min(1.0, Math.min(t * 6, (1 - t) * 5));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partial) {
        return 0xF000F0;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double tx, double ty, double tz) {
            return new WormGlyph(level, x, y, z, tx, ty, tz, sprites);
        }
    }
}
