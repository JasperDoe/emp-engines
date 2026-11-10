package dev.skybound.createskybound.client;

import dev.skybound.createskybound.block.VectorPlanarComputerBlockEntity;
import dev.skybound.createskybound.block.VectorPlanarComputerBlockEntity.Phase;
import dev.skybound.createskybound.launch.LaunchPath;
import dev.skybound.createskybound.launch.LaunchTracker;
import dev.skybound.createskybound.launch.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A quiet, low nether-portal hum over a charging launcher. It swells gently with the charge
 * (still quiet when full), holds through the countdown and beams, and fades out as the ship is thrown.
 */
public final class ChargeHum extends AbstractTickableSoundInstance {
    private static final Map<VectorPlanarComputerBlockEntity, ChargeHum> PLAYING = new WeakHashMap<>();

    private final VectorPlanarComputerBlockEntity be;
    private float level;

    private ChargeHum(VectorPlanarComputerBlockEntity be) {
        super(SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, RandomSource.create());
        this.be = be;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.pitch = 0.5f;
        place();
    }

    /** Called every client tick: start a hum for any charged launcher in this world that lacks one. */
    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { PLAYING.clear(); return; }
        PLAYING.entrySet().removeIf(e -> e.getValue().isStopped());
        for (VectorPlanarComputerBlockEntity be : LaunchTracker.charged()) {
            if (be.getLevel() != mc.level || PLAYING.containsKey(be) || be.platform() == null) continue;
            ChargeHum hum = new ChargeHum(be);
            PLAYING.put(be, hum);
            mc.getSoundManager().play(hum);
        }
    }

    private float target() {
        if (be.platform() == null) return 0;
        float charge = be.chargeFraction();
        float t = be.phaseTicks();
        return switch (be.phase()) {
            case IDLE -> charge <= 0 ? 0 : 0.04f + 0.22f * charge;
            case COUNTDOWN, BEAMS, SPIN -> 0.3f;
            case LAUNCH -> Math.max(0, 0.3f * (1 - t / 30f));
        };
    }

    private void place() {
        Platform p = be.platform();
        BlockPos c = p == null ? be.getBlockPos() : p.centre();
        x = c.getX() + 0.5;
        y = c.getY() + 1.0;
        z = c.getZ() + 0.5;
    }

    @Override
    public void tick() {
        if (be.isRemoved() || be.getLevel() != Minecraft.getInstance().level) { stop(); return; }
        float goal = target();
        level += (goal - level) * 0.05f;                  // slow swell, no jumps
        if (goal <= 0 && level < 0.005f) { stop(); return; }
        place();
        volume = Math.max(0.001f, level);
        float charge = be.phase() == Phase.IDLE ? be.chargeFraction() : 1f;
        pitch = 0.5f + 0.2f * charge;
        if (be.phase() == Phase.SPIN) pitch += 0.15f * be.phaseTicks() / LaunchPath.SPIN;
    }

    @Override
    public boolean canStartSilent() { return true; }
}
