package dev.empengines.engines.raid;

import dev.empengines.engines.launch.SableBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * What a raider does while the raid has a job for it:
 * <ul>
 *   <li>on its post on the raider ship, waiting its turn to board (it stays put and watches the player's ship)</li>
 *   <li>an archer: stays on its post and shoots at players with its crossbow, aiming for the ship's motion;
 *       when the boarders are all dead, one goes to the helm to sail the ship away</li>
 *   <li>walking to a hook, or (on the player's deck) to a chest or back to its rope; holding still on a rope</li>
 * </ul>
 * Targets are in the plot coordinates of the ship the raider is on; Sable's pathfinding turns that into
 * a path on the moving deck. When the raid has nothing for it (fighting on deck), normal AI runs instead.
 */
public class RaiderGoal extends Goal {
    static final int SHOT_GAP = 50, CHARGE = 20;
    static final double ARCHER_RANGE = 40;

    private final Raid.RaiderMob raider;
    private int repath, shot;

    public RaiderGoal(Raid.RaiderMob raider) {
        this.raider = raider;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    private boolean wanted() {
        Raid raid = raider.raid();
        if (raid.phase == Raid.Phase.DONE) return false;
        return switch (raider.state) {
            case WAITING, ARCHER, PILOT, TO_HOOK, CROSSING_IN, CROSSING_OUT -> true;
            case RETURNING -> raider.navTarget != null;
            case ON_DECK -> raider.navTarget != null && raid.looting && raider.role == Raid.Role.LOOTER && raider.carried.isEmpty();
            default -> false;
        };
    }

    @Override public boolean canUse() { return wanted(); }
    @Override public boolean canContinueToUse() { return wanted(); }

    @Override
    public void start() {
        repath = 0;
        shot = 10 + raider.mob.getRandom().nextInt(SHOT_GAP);
    }

    @Override
    public void tick() {
        Mob mob = raider.mob;
        switch (raider.state) {
            case CROSSING_IN, CROSSING_OUT -> mob.getNavigation().stop();
            case WAITING -> { stayAtPost(); watch(nearestPlayer(ARCHER_RANGE)); }
            case ARCHER -> archer();
            case PILOT -> {   // at the helm, taking the ship away
                Vec3 helm = SableBridge.toWorld(raider.raid().raider.ship, Vec3.atBottomCenterOf(raider.raid().raider.helm));
                if (mob.position().distanceTo(helm) > 1.2) walk(raider.navTarget);
                else { mob.getNavigation().stop(); watch(nearestPlayer(ARCHER_RANGE)); }
            }
            default -> walk(raider.navTarget);
        }
    }

    private void walk(Vec3 t) {
        if (t == null) return;
        if (--repath <= 0 || raider.mob.getNavigation().isDone()) {
            repath = 20;
            raider.mob.getNavigation().moveTo(t.x, t.y, t.z, 1.15);
        }
    }

    private void stayAtPost() {
        Raid raid = raider.raid();
        Vec3 post = SableBridge.toWorld(raid.raider.ship, Vec3.atBottomCenterOf(raider.post));
        if (raider.mob.position().distanceTo(post) > 1.2) walk(Vec3.atBottomCenterOf(raider.post));
        else {
            raider.mob.getNavigation().stop();
            repath = 0;
        }
    }

    private void watch(ServerPlayer p) {
        if (p != null) raider.mob.getLookControl().setLookAt(p, 30, 30);
    }

    private ServerPlayer nearestPlayer(double range) {
        ServerPlayer best = null;
        double bestD = range * range;
        for (ServerPlayer p : ((ServerLevel) raider.mob.level()).players()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            double d = p.distanceToSqr(raider.mob);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    /** Stays on its post and shoots: charges for a second, fires, waits, again. */
    private void archer() {
        Mob mob = raider.mob;
        stayAtPost();
        ServerPlayer target = nearestPlayer(ARCHER_RANGE);
        watch(target);
        boolean canSee = target != null && mob.getSensing().hasLineOfSight(target);
        if (--shot == CHARGE && canSee && mob instanceof Pillager p) p.setChargingCrossbow(true);
        if (shot > 0) return;
        shot = SHOT_GAP + mob.getRandom().nextInt(20);
        if (mob instanceof Pillager p) p.setChargingCrossbow(false);
        if (!canSee) return;

        ItemStack weapon = mob.getMainHandItem();
        Arrow arrow = new Arrow(mob.level(), mob, new ItemStack(Items.ARROW), weapon.isEmpty() ? null : weapon);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        double dx = target.getX() - mob.getX(), dz = target.getZ() - mob.getZ();
        double dy = target.getY(0.33) - arrow.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.2, dz, 1.6f, 3f);
        // The ship carries the arrow along with it (otherwise it would fall behind both moving ships).
        Vec3 carry = RaiderFlight.pointVelocity(raider.raid().raider.ship, mob.position()).scale(1 / 20.0);
        arrow.setDeltaMovement(arrow.getDeltaMovement().add(carry));
        mob.level().addFreshEntity(arrow);
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1f, 1f);
    }

    @Override
    public void stop() {
        raider.mob.getNavigation().stop();
        if (raider.mob instanceof Pillager p) p.setChargingCrossbow(false);
    }
}
