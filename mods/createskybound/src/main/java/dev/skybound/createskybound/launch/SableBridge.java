package dev.skybound.createskybound.launch;

import dev.egg.dimensionalsable.SubLevelWarper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

    /** Moves the ship (and anything connected to it) into another dimension. */
    public static int warp(ServerSubLevel ship, ServerLevel target, Vec3 pos) {
        return SubLevelWarper.warpConnectedSubLevels(ship, target, new Vector3d(pos.x, pos.y, pos.z));
    }

    private SableBridge() {}
}
