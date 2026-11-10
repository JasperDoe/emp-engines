package dev.skybound.createskybound.launch;

import dev.skybound.createskybound.block.VectorPlanarComputerBlockEntity;
import dev.skybound.createskybound.block.VectorPlanarComputerBlockEntity.Phase;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Client side: launchers in a sequence (for camera shake and the white-out) and launchers
 * holding charge (for the charging hum). Kept free of client classes so the block entity can call it.
 */
public final class LaunchTracker {
    private static final Set<VectorPlanarComputerBlockEntity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<VectorPlanarComputerBlockEntity> CHARGED = Collections.newSetFromMap(new WeakHashMap<>());

    public static void track(VectorPlanarComputerBlockEntity be) {
        if (be.phase() == Phase.IDLE || be.isRemoved()) ACTIVE.remove(be);
        else ACTIVE.add(be);
        if (be.isRemoved() || (be.phase() == Phase.IDLE && be.chargeFraction() <= 0)) CHARGED.remove(be);
        else CHARGED.add(be);
    }

    public static Set<VectorPlanarComputerBlockEntity> active() {
        ACTIVE.removeIf(be -> be.isRemoved() || be.phase() == Phase.IDLE);
        return ACTIVE;
    }

    /** Launchers with some charge, or mid-launch. */
    public static Set<VectorPlanarComputerBlockEntity> charged() {
        CHARGED.removeIf(VectorPlanarComputerBlockEntity::isRemoved);
        return CHARGED;
    }

    private LaunchTracker() {}
}
