package dev.skybound.createskybound.launch;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Launch sounds. Everything is kept soft and ethereal: nothing explodes. The light is drawn
 * on the client by {@code client.LaunchRenderer}, and the charging hum is a client sound
 * ({@code client.ChargeHum}) that grows with the charge.
 */
public final class LaunchEffects {

    /** The launcher has just reached full charge. */
    public static void charged(ServerLevel level, Platform p) {
        level.playSound(null, p.centre(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.7f, 0.6f);
    }

    /** One soft chime a second, rising in pitch. */
    public static void countdown(ServerLevel level, Platform p, int tick, int total) {
        if (tick % 20 == 1) {
            float pitch = 0.6f + 0.8f * (tick / (float) total);
            level.playSound(null, p.centre(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8f, pitch);
        }
    }

    public static void beamsArrive(ServerLevel level, Platform p) {
        level.playSound(null, p.centre(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.5f, 0.6f);
    }

    public static void spin(ServerLevel level, Vec3 ship, int tick, int total) {
        if (tick % 5 == 0)
            level.playSound(null, BlockPos.containing(ship), SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 2.5f, 0.5f + 1.5f * tick / total);
    }

    /** The throw: a deep rush of air and a ringing note, no blast. */
    public static void launch(ServerLevel level, Vec3 ship) {
        BlockPos at = BlockPos.containing(ship);
        level.playSound(null, at, SoundEvents.ENDER_EYE_LAUNCH, SoundSource.BLOCKS, 4f, 0.5f);
        level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 3f, 0.5f);
        level.playSound(null, at, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2f, 1.4f);
    }

    /** Breaking out above the clouds. */
    public static void breach(ServerLevel level, Vec3 ship) {
        level.playSound(null, BlockPos.containing(ship), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2f, 0.7f);
    }

    private LaunchEffects() {}
}
