package dev.empengines.silentraids;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

/** Is something standing on (or in) a Sable ship? Kept apart so the mod still loads without Sable. */
public final class SableCheck {
    private static Boolean loaded;
    private static boolean failed;

    public static boolean onShip(ServerLevel level, AABB box) {
        if (loaded == null) loaded = ModList.get().isLoaded("sable");
        if (!loaded || failed) return false;
        try {
            return Impl.any(level, box);
        } catch (Throwable t) {
            failed = true;
            SilentRaids.LOGGER.warn("Silent Apotheosis Raids: couldn't check for Sable ships (players on ships will get sieges too)", t);
            return false;
        }
    }

    private static final class Impl {
        static boolean any(ServerLevel level, AABB box) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) return false;
            for (SubLevel sub : container.queryIntersecting(new BoundingBox3d(box))) {
                if (sub instanceof ServerSubLevel s && !s.isRemoved()) return true;
            }
            return false;
        }
    }

    private SableCheck() {}
}
