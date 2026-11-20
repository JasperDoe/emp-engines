package dev.empengines.engines.raid;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A boarding rope: drawn to look like Create Aeronautics' own rope, but with no physics behind it, so it
 * can never pull on either ship. It hangs in a shallow curve between two rope connectors (one on each
 * ship, given by their plot positions), raiders cross it, and players can walk along it.
 *
 * The same curve is worked out on the server (raiders crossing) and the client (drawing it, walking on it).
 */
public record BoardingRope(BlockPos from, BlockPos to, float sag) {
    public static final StreamCodec<io.netty.buffer.ByteBuf, BoardingRope> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BoardingRope::from, BlockPos.STREAM_CODEC, BoardingRope::to, ByteBufCodecs.FLOAT, BoardingRope::sag,
            BoardingRope::new);

    /**
     * Where the rope leaves a connector, in world space, given the pose of the ship it's on. An upright
     * connector's knot sits 0.6 up its block; a sideways one's sticks out of its face.
     */
    static Vec3 knot(Level level, BlockPos plot, Function<SubLevel, Pose3dc> pose) {
        BlockState state = level.getBlockState(plot);
        Direction facing = state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING) : Direction.UP;
        Vec3 p = switch (facing) {
            case UP -> new Vec3(plot.getX() + 0.5, plot.getY() + 0.6, plot.getZ() + 0.5);
            case DOWN -> new Vec3(plot.getX() + 0.5, plot.getY() + 0.4, plot.getZ() + 0.5);
            default -> Vec3.atCenterOf(plot).add(facing.getStepX() * 0.3, 0, facing.getStepZ() * 0.3);
        };
        SubLevel ship = Sable.HELPER.getContaining(level, plot);
        return ship == null ? p : pose.apply(ship).transformPosition(p);
    }

    /** The rope's points in world space, one about every block, sagging by {@code sag} of its length in the middle. */
    public List<Vec3> points(Level level, Function<SubLevel, Pose3dc> pose) {
        return curve(knot(level, from, pose), knot(level, to, pose), sag);
    }

    public static List<Vec3> curve(Vec3 a, Vec3 b, double sag) {
        double d = a.distanceTo(b);
        int n = Math.max(2, (int) Math.ceil(d));
        double drop = sag * d;
        List<Vec3> out = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            out.add(a.lerp(b, t).add(0, -4 * drop * t * (1 - t), 0));
        }
        return out;
    }

    /** Where along a polyline is closest to {@code p} horizontally: segment index and fraction, or null if further than {@code max}. */
    public static double[] nearest(List<Vec3> pts, Vec3 p, double max) {
        double best = max * max;
        double[] out = null;
        for (int i = 1; i < pts.size(); i++) {
            Vec3 a = pts.get(i - 1), b = pts.get(i);
            double dx = b.x - a.x, dz = b.z - a.z, len2 = dx * dx + dz * dz;
            double t = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.z - a.z) * dz) / len2));
            double qx = a.x + dx * t - p.x, qz = a.z + dz * t - p.z;
            double d2 = qx * qx + qz * qz;
            if (d2 < best) { best = d2; out = new double[]{i - 1, t}; }
        }
        return out;
    }

    public static Vec3 at(List<Vec3> pts, double[] where) {
        int i = (int) where[0];
        return pts.get(i).lerp(pts.get(i + 1), where[1]);
    }
}
