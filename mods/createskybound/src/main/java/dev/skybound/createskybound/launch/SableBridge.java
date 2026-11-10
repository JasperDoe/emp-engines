package dev.skybound.createskybound.launch;

import dev.egg.dimensionalsable.SubLevelWarper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Everything that touches Sable (ship physics) and Dimensional Sable (moving ships between dimensions). */
public final class SableBridge {
    private static final Vector3d ZERO = new Vector3d();

    /** The first ship whose bounds overlap the given box. */
    public static ServerSubLevel findShip(ServerLevel level, AABB box) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        for (SubLevel sub : container.queryIntersecting(new BoundingBox3d(box))) {
            if (sub instanceof ServerSubLevel s && !s.isRemoved()) return s;
        }
        return null;
    }

    public static List<ServerSubLevel> allShips(ServerLevel level) {
        List<ServerSubLevel> out = new ArrayList<>();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return out;
        for (SubLevel sub : container.getAllSubLevels()) if (sub instanceof ServerSubLevel s && !s.isRemoved()) out.add(s);
        return out;
    }

    public static ServerSubLevel byId(ServerLevel level, UUID id) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || id == null) return null;
        return container.getSubLevel(id) instanceof ServerSubLevel s ? s : null;
    }

    public static double mass(ServerSubLevel ship) {
        return ship.getMassTracker().getMass();
    }

    public static Vec3 position(ServerSubLevel ship) {
        Vector3dc p = ship.logicalPose().position();
        return new Vec3(p.x(), p.y(), p.z());
    }

    public static AABB bounds(ServerSubLevel ship) {
        BoundingBox3dc b = ship.boundingBox();
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    public static Vec3 velocity(ServerSubLevel ship) {
        Vector3dc v = RigidBodyHandle.of(ship).getLinearVelocity();
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** Nudges the ship's velocity towards a target (blocks per second), with horizontal damping. */
    public static void steer(ServerSubLevel ship, double targetY, double horizontalDamping) {
        RigidBodyHandle handle = RigidBodyHandle.of(ship);
        if (handle == null || !handle.isValid()) return;
        Vector3dc v = handle.getLinearVelocity();
        Vector3d delta = new Vector3d(-v.x() * horizontalDamping, targetY - v.y(), -v.z() * horizontalDamping);
        handle.addLinearAndAngularVelocity(delta, ZERO);
    }

    public static Quaterniond orientation(ServerSubLevel ship) {
        return new Quaterniond(ship.logicalPose().orientation());
    }

    /**
     * Places the ship exactly (no physics), and cancels its velocity so gravity
     * can't fight the animation. Used for the whole launch sequence.
     */
    public static void place(ServerSubLevel ship, Vec3 pos, Quaterniondc orientation) {
        RigidBodyHandle handle = RigidBodyHandle.of(ship);
        if (handle == null || !handle.isValid()) return;
        handle.teleport(new Vector3d(pos.x, pos.y, pos.z), orientation);
        Vector3dc v = handle.getLinearVelocity(), w = handle.getAngularVelocity();
        handle.addLinearAndAngularVelocity(new Vector3d(-v.x(), -v.y(), -v.z()), new Vector3d(-w.x(), -w.y(), -w.z()));
    }

    /** Cancels all motion (used to let a ship settle after arriving). */
    public static void holdStill(ServerSubLevel ship) {
        RigidBodyHandle handle = RigidBodyHandle.of(ship);
        if (handle == null || !handle.isValid()) return;
        Vector3dc v = handle.getLinearVelocity(), w = handle.getAngularVelocity();
        handle.addLinearAndAngularVelocity(new Vector3d(-v.x(), -v.y(), -v.z()), new Vector3d(-w.x(), -w.y(), -w.z()));
    }

    /** The same heading with no pitch or roll: what "levelling out" turns the ship to. */
    public static Quaterniond level(Quaterniondc q) {
        Vector3d fwd = q.transform(new Vector3d(0, 0, 1));
        double yaw = Math.atan2(fwd.x, fwd.z);
        return new Quaterniond().rotationY(yaw);
    }

    /** Moves the ship (and anything connected to it) into another dimension. */
    public static int warp(ServerSubLevel ship, ServerLevel target, Vec3 pos) {
        return SubLevelWarper.warpConnectedSubLevels(ship, target, new Vector3d(pos.x, pos.y, pos.z));
    }

    // --- used by Sky Raiders -----------------------------------------------------------

    /** The ship an entity is standing on, if any. */
    public static ServerSubLevel shipUnder(Entity e) {
        return Sable.HELPER.getTrackingSubLevel(e) instanceof ServerSubLevel s && !s.isRemoved() ? s : null;
    }

    /** The ship whose blocks hold this position (positions inside a ship are "plot" positions). */
    public static ServerSubLevel shipContaining(ServerLevel level, BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel s && !s.isRemoved() ? s : null;
    }

    /** The block bounds of a ship, in its plot coordinates. */
    public static BoundingBox3ic plotBounds(ServerSubLevel ship) {
        return ship.getPlot().getBoundingBox();
    }

    /** A point on a ship (plot coordinates) to world coordinates. */
    public static Vec3 toWorld(ServerSubLevel ship, Vec3 plotPos) {
        return ship.logicalPose().transformPosition(plotPos);
    }

    /** A world point to a ship's plot coordinates. */
    public static Vec3 toPlot(ServerSubLevel ship, Vec3 worldPos) {
        return ship.logicalPose().transformPositionInverse(worldPos);
    }

    /** The point in plot coordinates that the ship's world position refers to. */
    public static Vec3 rotationPoint(ServerSubLevel ship) {
        Vector3dc r = ship.logicalPose().rotationPoint();
        return new Vec3(r.x(), r.y(), r.z());
    }

    /** Projects any position (plot or world) to world coordinates. */
    public static Vec3 project(ServerLevel level, Vec3 pos) {
        return Sable.HELPER.projectOutOfSubLevel(level, pos);
    }

    /** Turns blocks placed in the world into a ship. */
    public static ServerSubLevel assemble(ServerLevel level, BlockPos anchor, List<BlockPos> blocks) {
        return SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, BoundingBox3i.from(blocks));
    }

    /** Deletes a ship and everything on it. */
    public static void remove(ServerSubLevel ship) {
        if (ship == null || ship.isRemoved()) return;
        ship.getPlot().destroyAllBlocks();
        SubLevelContainer container = SubLevelContainer.getContainer(ship.getLevel());
        if (container != null) container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
    }

    private SableBridge() {}
}
