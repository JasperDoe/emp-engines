package dev.skybound.createskybound.raid;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Walks a raider to where the raid wants it (a chest, or its rope home). The target is in the
 * player ship's plot coordinates; Sable's pathfinding turns that into a path on the moving deck.
 * When the raid has nowhere to send it, normal AI (fighting players) runs instead.
 */
public class RaiderGoal extends Goal {
    private final Raid raid;
    private final Raid.Raider raider;
    private int repath;

    public RaiderGoal(Raid raid, Raid.Raider raider) {
        this.raid = raid;
        this.raider = raider;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    private boolean wanted() {
        if (raid.phase == Raid.Phase.DONE) return false;
        return switch (raider.state) {
            case CROSSING_IN, CROSSING_OUT -> true;   // hold still while the raid moves them along the rope
            case RETURNING -> raider.navTarget != null;
            case ON_DECK -> raider.navTarget != null && raid.looting && raider.role == Raid.Role.LOOTER && raider.carried.isEmpty();
            default -> false;
        };
    }

    @Override
    public boolean canUse() { return wanted(); }

    @Override
    public boolean canContinueToUse() { return wanted(); }

    @Override
    public void start() {
        repath = 0;
        raider.mob.setTarget(null);
    }

    @Override
    public void tick() {
        if (raider.state == Raid.State.CROSSING_IN || raider.state == Raid.State.CROSSING_OUT) {
            raider.mob.getNavigation().stop();
            return;
        }
        Vec3 t = raider.navTarget;
        if (t == null) return;
        if (--repath <= 0 || raider.mob.getNavigation().isDone()) {
            repath = 20;
            raider.mob.getNavigation().moveTo(t.x, t.y, t.z, 1.15);
        }
    }

    @Override
    public void stop() { raider.mob.getNavigation().stop(); }
}
