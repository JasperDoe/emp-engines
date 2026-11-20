package dev.empengines.silentraids.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A raider that can't reach its target breaks through: it looks for the block between it and the player (wall at
 * head or foot height, or a ceiling when the player is above) and hits it until it breaks.
 */
public final class BreachGoal extends Goal {
    private final Mob mob;
    private BlockPos target;
    private int cooldown;
    private Vec3 lastPos = Vec3.ZERO;
    private int stillChecks;

    public BreachGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = 20;
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive() || !(mob.level() instanceof ServerLevel)) return false;
        if (mob.distanceToSqr(t) < 2.5 * 2.5 || mob.distanceToSqr(t) > 48 * 48) return false;

        // Stuck: no full path to the target, and not getting anywhere
        boolean moved = mob.position().distanceToSqr(lastPos) > 0.25;
        lastPos = mob.position();
        stillChecks = moved ? 0 : stillChecks + 1;
        Path path = mob.getNavigation().getPath();
        boolean noRoute = path == null || !path.canReach() || mob.getNavigation().isDone();
        if (!noRoute || stillChecks < 2) {
            if (noRoute && !moved) {
                // Ask for a path now; if there's none, we'll know next check
                mob.getNavigation().moveTo(t, 1.0);
            }
            return false;
        }
        target = findObstacle(t);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = mob.getTarget();
        if (target == null || t == null || !t.isAlive()) return false;
        BlockState state = mob.level().getBlockState(target);
        return Breaching.breakable(mob.level(), target, state) && mob.distanceToSqr(Vec3.atCenterOf(target)) < 3.5 * 3.5;
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        target = null;
        stillChecks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null || !(mob.level() instanceof ServerLevel level)) return;
        mob.getLookControl().setLookAt(Vec3.atCenterOf(target));
        if (mob.tickCount % 15 == 0) mob.swing(InteractionHand.MAIN_HAND);
        if (Breaching.hit(level, target, mob)) target = null;
    }

    private BlockPos findObstacle(LivingEntity t) {
        Vec3 eye = mob.getEyePosition();
        Vec3 to = t.getEyePosition().subtract(eye);
        if (to.lengthSqr() < 1e-4) return null;
        Vec3 dir = to.normalize();

        // 1) Whatever is directly between the raider's eyes and the player
        BlockHitResult hit = mob.level().clip(new ClipContext(eye, eye.add(dir.scale(2.5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        if (hit.getType() == HitResult.Type.BLOCK && ok(hit.getBlockPos())) return hit.getBlockPos();

        // 2) The wall in front, head height then feet
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        BlockPos feet = mob.blockPosition();
        if (flat.lengthSqr() > 1e-4) {
            flat = flat.normalize();
            BlockPos front = BlockPos.containing(mob.getX() + flat.x * 0.9, mob.getY(), mob.getZ() + flat.z * 0.9);
            if (ok(front.above())) return front.above();
            if (ok(front)) return front;
        }

        // 3) A ceiling, when the player is above
        if (t.getY() > mob.getY() + 2) {
            BlockPos up = feet.above((int) Math.ceil(mob.getBbHeight()));
            if (ok(up)) return up;
        }
        return null;
    }

    private boolean ok(BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        return !state.getCollisionShape(mob.level(), pos).isEmpty() && Breaching.breakable(mob.level(), pos, state);
    }
}
