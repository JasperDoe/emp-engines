package dev.empengines.tieredfoundry.compat.sable;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Sable (the ship physics behind Create Aeronautics) moves an assembled ship's blocks into a far-off plot of the
 * same level and draws them where the ship is. This keeps Tiered Foundry working on ships: particles are moved from
 * the plot to where the ship really is. Safe without Sable (the Sable classes are only touched when it's loaded).
 */
public final class SableCompat {
    private static final boolean LOADED = ModList.get().isLoaded("sable");

    /** Where a point in a ship's plot really is in the world (unchanged if it isn't on a ship). */
    public static Vec3 toWorld(Level level, Vec3 pos) {
        if (!LOADED) return pos;
        try {
            return Impl.project(level, pos);
        } catch (Throwable t) {
            return pos;
        }
    }

    private static final class Impl {
        static Vec3 project(Level level, Vec3 pos) {
            return dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.projectOutOfSubLevel(level, pos);
        }
    }

    private SableCompat() {}
}
