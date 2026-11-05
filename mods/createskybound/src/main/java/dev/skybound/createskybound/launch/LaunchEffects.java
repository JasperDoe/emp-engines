package dev.skybound.createskybound.launch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Launch visuals, done with server-sent particles and sounds for now.
 * A custom beam renderer (Orbital Railgun style) replaces these later.
 */
public final class LaunchEffects {
    private static final DustParticleOptions BEAM = new DustParticleOptions(new Vector3f(0.75f, 0.9f, 1.0f), 2.0f);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.8f, 0.3f), 1.5f);

    /** While charging: dust kicks up off the platform, more as the charge fills. */
    public static void chargingRumble(ServerLevel level, Platform p, float fraction) {
        int count = 2 + Math.round(fraction * 20);
        BlockPos c = p.centre();
        double rx = (p.maxX() - p.minX()) / 2.0, rz = (p.maxZ() - p.minZ()) / 2.0;
        level.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5, p.y() + 1.1, c.getZ() + 0.5, count, rx, 0.05, rz, 0.01);
        if (fraction > 0.5f && level.random.nextFloat() < fraction)
            level.playSound(null, c, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.6f, 0.5f + fraction);
    }

    public static void countdown(ServerLevel level, Platform p, Vec3 ship, int tick, int total) {
        if (tick % 20 == 1) {
            float pitch = 0.6f + (tick / (float) total);
            level.playSound(null, p.centre(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 2f, pitch);
        }
        BlockPos c = p.centre();
        double rx = (p.maxX() - p.minX()) / 2.0, rz = (p.maxZ() - p.minZ()) / 2.0;
        level.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, p.y() + 1.2, c.getZ() + 0.5, 12, rx, 0.1, rz, 0.05);
        level.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5, p.y() + 1.0, c.getZ() + 0.5, 8, rx, 0.05, rz, 0.05);
    }

    /** Columns of light come down from the sky onto the pylons, then lean in towards the ship. */
    public static void beams(ServerLevel level, Platform p, Vec3 ship, float progress) {
        if (progress < 0.02f) level.playSound(null, p.centre(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.6f);
        double top = Math.min(level.getMaxBuildHeight(), p.y() + 120);
        double reach = top - (top - p.y()) * Math.min(1f, progress * 2f);   // the beam descends over the first half
        for (BlockPos corner : p.corners()) {
            double cx = corner.getX() + 0.5, cz = corner.getZ() + 0.5;
            double lean = Math.max(0, (progress - 0.5f) * 2f);                // then leans in over the second half
            for (double y = reach; y < top; y += 1.5) {
                double t = (y - p.y()) / (top - p.y());
                double x = cx + (ship.x - cx) * lean * (1 - t);
                double z = cz + (ship.z - cz) * lean * (1 - t);
                level.sendParticles(BEAM, x, y, z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    /** The beams sweep round the ship, faster and faster. */
    public static void spin(ServerLevel level, Platform p, Vec3 ship, float progress) {
        double radius = Math.max(p.maxX() - p.minX(), p.maxZ() - p.minZ()) / 2.0 + 1;
        double angle = progress * progress * 40.0;   // speeds up
        for (int i = 0; i < 4; i++) {
            double a = angle + i * Math.PI / 2;
            double x = ship.x + Math.cos(a) * radius, z = ship.z + Math.sin(a) * radius;
            for (double y = p.y() + 1; y < ship.y + 12; y += 1.0) level.sendParticles(BEAM, x, y, z, 1, 0.02, 0.02, 0.02, 0);
        }
        level.sendParticles(GOLD, ship.x, p.y() + 1.5, ship.z, 20, radius / 2, 0.2, radius / 2, 0.1);
        if (level.getGameTime() % 5 == 0)
            level.playSound(null, BlockPos.containing(ship), SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 3f, 0.5f + progress * 1.5f);
    }

    public static void fire(ServerLevel level, Vec3 ship) {
        level.playSound(null, BlockPos.containing(ship), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 4f, 0.6f);
        level.playSound(null, BlockPos.containing(ship), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4f, 0.5f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ship.x, ship.y - 2, ship.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, ship.x, ship.y, ship.z, 3, 0, 0, 0, 0);
    }

    /** A trail of light behind the ship as it climbs. */
    public static void trail(ServerLevel level, Vec3 ship) {
        level.sendParticles(BEAM, ship.x, ship.y - 4, ship.z, 30, 1.5, 4, 1.5, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, ship.x, ship.y - 6, ship.z, 10, 1, 3, 1, 0.05);
    }

    private LaunchEffects() {}
}
