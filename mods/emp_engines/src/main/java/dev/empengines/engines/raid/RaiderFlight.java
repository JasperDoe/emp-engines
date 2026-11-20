package dev.empengines.engines.raid;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.empengines.engines.launch.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/**
 * Flies a raider ship by setting its velocity directly ("magic" physics): it speeds up to its top
 * speed in about four seconds, turns no faster than its turn rate, stays level, doesn't sag under
 * gravity, and climbs over anything in front of it. Its own engines and wheel only put on a show.
 *
 * <ul>
 *   <li><b>Approach</b>: from far off it flies nose-first at a point ahead of the spot alongside the
 *       player's ship; close in it slides onto the spot and turns parallel.</li>
 *   <li><b>Lock</b>: once alongside, where it sits relative to the player's ship is captured, and from
 *       then on it is put exactly there every tick and given the player's velocity and spin. The player
 *       can't shake it off and the ropes never yank either ship.</li>
 *   <li><b>Flee</b>: away from the player and climbing.</li>
 * </ul>
 */
final class RaiderFlight {
    static final int NEAR = 32;
    static final double GRAVITY_TICK = 0.25;

    final RaiderShip r;
    final ServerLevel level;
    final double top, accelTick, turn, turnAccelTick;
    int envelopeTicks;
    boolean locked;
    private Vec3 lockPlot;
    private Quaterniond lockRel;
    private double lastGap, lastYawErr, lastSpeed, lastTurn;
    private boolean climbing;

    RaiderFlight(ServerLevel level, RaiderShip r) {
        this.level = level;
        this.r = r;
        this.top = r.def.speed > 0 ? r.def.speed : RaiderShipDef.defaultSpeed(r.def.blocks);
        this.accelTick = top / 80.0;                                   // 0 to top in 4 s
        this.turn = Math.toRadians(r.def.turn > 0 ? r.def.turn : 25);
        this.turnAccelTick = turn / 20.0;                              // full turn rate in 1 s
    }

    // --- approach -------------------------------------------------------------------------------

    /** Steers towards {@code holdPlot} (the player ship's plot coordinates), facing along {@code axisPlot}. */
    void approach(ServerSubLevel target, Vec3 holdPlot, Direction axisPlot) {
        Vec3 hold = SableBridge.toWorld(target, holdPlot);
        Vec3 here = SableBridge.toWorld(r.ship, r.ref);
        Vec3 holdVel = pointVelocity(target, hold);
        Vec3 axis = flat(SableBridge.toWorldDir(target, step(axisPlot)));
        Vec3 err = hold.subtract(here);
        Vec3 errH = new Vec3(err.x, 0, err.z);
        double dist = errH.length();
        Vec3 holdVelH = new Vec3(holdVel.x, 0, holdVel.z);

        Vec3 vCmd;
        double yawCmd;
        boolean slide;
        if (dist > NEAR) {
            // Lead the spot by roughly the time it takes to get there.
            double t = Math.min(15, dist / top);
            Vec3 aim = errH.add(holdVelH.scale(t));
            yawCmd = yaw(aim);
            double speed = Math.min(top, holdVelH.length() + 0.35 * dist);
            vCmd = flat(aim).scale(speed);
            slide = false;
        } else {
            vCmd = holdVelH.add(clamp(errH.scale(0.35), top * 0.6));
            yawCmd = yaw(axis);
            slide = true;
        }
        double vy = clamp(0.6 * err.y, 5) + holdVel.y;
        fly(vCmd, vy, yawCmd, slide);

        Vec3 v = SableBridge.velocity(r.ship);
        lastGap = err.length();
        lastYawErr = Math.toDegrees(wrap(yaw(axis) - yaw(r.heading())));
        lastSpeed = v.subtract(holdVel).length();
        boolean inside = dist < 4 && Math.abs(err.y) < 3 && Math.abs(lastYawErr) < 15 && lastSpeed < 2.5;
        envelopeTicks = inside ? envelopeTicks + 1 : 0;
    }

    // --- lock ---------------------------------------------------------------------------------------

    /** Remembers where the raider sits relative to the player's ship, from now on it's held there. */
    void capture(ServerSubLevel target) {
        lockPlot = SableBridge.toPlot(target, SableBridge.position(r.ship));
        lockRel = new Quaterniond(SableBridge.orientation(target)).conjugate().mul(SableBridge.orientation(r.ship));
        locked = true;
    }

    /** Puts the raider exactly at its locked spot, moving and turning with the player's ship. */
    void hold(ServerSubLevel target) {
        if (!locked) return;
        Vec3 pos = SableBridge.toWorld(target, lockPlot);
        Quaterniond q = SableBridge.orientation(target).mul(lockRel);
        SableBridge.moveWith(r.ship, pos, q, pointVelocity(target, pos), SableBridge.angularVelocity(target));
        Vec3 pv = SableBridge.velocity(target);
        r.controls.show(Math.min(1, Math.hypot(pv.x, pv.z) / top), 0, level.getGameTime());
        lastGap = 0;
    }

    void unlock() { locked = false; envelopeTicks = 0; }

    // --- flee ---------------------------------------------------------------------------------------

    void flee(ServerSubLevel target, boolean slow) {
        Vec3 here = SableBridge.toWorld(r.ship, r.ref);
        Vec3 away = target == null ? r.heading() : flat(here.subtract(SableBridge.position(target)));
        double speed = slow ? top * 0.75 : top;
        fly(away.scale(speed), 3, yaw(away), true);   // slides away while it turns, rather than sitting still
    }

    // --- the flying itself ----------------------------------------------------------------------

    /**
     * Moves the ship towards a horizontal velocity {@code vCmd}, a climb rate {@code vy} and a heading
     * {@code yawCmd}, within its limits. Unless {@code slide}, it only goes where its nose points.
     */
    private void fly(Vec3 vCmd, double vy, double yawCmd, boolean slide) {
        Vec3 v = SableBridge.velocity(r.ship), w = SableBridge.angularVelocity(r.ship);
        Vec3 heading = r.heading();
        Vec3 vH = new Vec3(v.x, 0, v.z);
        Vec3 cmd = new Vec3(vCmd.x, 0, vCmd.z);
        if (!slide) cmd = heading.scale(Math.max(-top * 0.2, Math.min(top, cmd.dot(heading))));
        if (cmd.length() > top) cmd = cmd.scale(top / cmd.length());

        // Something in the way: climb over it, and slow down if it's close.
        double ahead = obstacle(heading, vH.length());
        climbing = ahead >= 0;
        if (climbing) {
            vy = Math.max(vy, 6);
            if (ahead < 8) cmd = cmd.scale(0.4);
        }

        Vec3 dv = cmd.subtract(vH);
        double lim = slide ? accelTick * 2 : accelTick;
        if (dv.length() > lim) dv = dv.scale(lim / dv.length());
        Vec3 vNew = vH.add(dv).add(0, vy + GRAVITY_TICK, 0);

        double yawErr = wrap(yawCmd - yaw(heading));
        double wyCmd = clamp(1.5 * yawErr, turn);
        double wy = w.y + clamp(wyCmd - w.y, turnAccelTick);
        // Stay level: turn "up" back to straight up.
        Vec3 up = SableBridge.toWorldDir(r.ship, new Vec3(0, 1, 0));
        Vec3 level = clamp(up.cross(new Vec3(0, 1, 0)).scale(3), 0.6);
        Vec3 wNew = new Vec3(level.x, wy, level.z);
        SableBridge.nudge(r.ship, vNew.subtract(v), wNew.subtract(w));

        lastTurn = wy / turn;
        r.controls.show(vH.length() / top, lastTurn, this.level.getGameTime());
    }

    /** Distance to the first solid block in a box sweeping ahead of the ship, or -1 if clear. */
    private double obstacle(Vec3 heading, double speed) {
        Vec3 c = SableBridge.toWorld(r.ship, r.ref);
        double bottom = c.y - r.refAboveBottom() + 0.5, topY = bottom + r.height() - 1;
        Vec3 side = new Vec3(-heading.z, 0, heading.x);
        double hl = r.halfLength(), hw = r.halfWidth();
        double reach = Math.max(12, speed * 1.5);
        for (double d = 4; d <= reach; d += 4) {
            for (double s = -hw; s <= hw + 0.01; s += Math.max(1, hw)) {
                for (double y = bottom; y <= topY + 0.01; y += Math.max(1, (topY - bottom) / 2)) {
                    Vec3 p = c.add(heading.scale(hl + d)).add(side.scale(s));
                    BlockPos b = BlockPos.containing(p.x, y, p.z);
                    if (!level.isLoaded(b)) continue;
                    if (!level.getBlockState(b).getCollisionShape(level, b).isEmpty()) return d;
                }
            }
        }
        // Also the ground straight below, so it never settles onto an island.
        BlockPos below = BlockPos.containing(c.x, bottom - 3, c.z);
        if (level.isLoaded(below) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return 0;
        return -1;
    }

    // --- maths ----------------------------------------------------------------------------------

    /** How fast a point fixed to the ship is moving: its velocity plus its spin. */
    static Vec3 pointVelocity(ServerSubLevel ship, Vec3 world) {
        return SableBridge.velocity(ship).add(SableBridge.angularVelocity(ship).cross(world.subtract(SableBridge.position(ship))));
    }

    static Vec3 step(Direction d) { return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ()); }

    static Vec3 flat(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : h.normalize();
    }

    /** Heading angle, chosen so that its rate of change is the angular velocity about +Y. */
    static double yaw(Vec3 h) { return Math.atan2(-h.z, h.x); }

    static double wrap(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    static double clamp(double v, double max) { return Math.max(-max, Math.min(max, v)); }

    static Vec3 clamp(Vec3 v, double max) {
        double l = v.length();
        return l > max ? v.scale(max / l) : v;
    }

    String status() {
        return (locked ? "LOCKED alongside" : String.format("%.1f blocks off, heading off by %.0f deg, rel speed %.1f", lastGap, lastYawErr, lastSpeed))
                + String.format(", top %.0f b/s, turn %.0f deg/s", top, Math.toDegrees(turn)) + (climbing ? ", climbing over something" : "");
    }
}
