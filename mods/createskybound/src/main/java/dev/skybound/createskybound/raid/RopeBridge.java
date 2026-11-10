package dev.skybound.createskybound.raid;

import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.skybound.createskybound.CreateSkybound;
import dev.skybound.createskybound.launch.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The boarding ropes: Create Aeronautics' own rope connectors and rope strands (its bundled
 * Simulated module). A rope is real physics, tied to both ships; breaking either connector cuts it.
 */
public final class RopeBridge {
    public static final ResourceLocation CONNECTOR = ResourceLocation.fromNamespaceAndPath("simulated", "rope_connector");

    public static Block connectorBlock() {
        return BuiltInRegistries.BLOCK.get(CONNECTOR);
    }

    /** A rope connector standing upright, as placed on a deck. */
    public static BlockState connectorState() {
        BlockState s = connectorBlock().defaultBlockState();
        if (s.hasProperty(BlockStateProperties.FACING)) s = s.setValue(BlockStateProperties.FACING, Direction.UP);
        return s;
    }

    public static boolean isConnector(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(connectorBlock());
    }

    static RopeStrandHolderBehavior holder(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RopeStrandHolderBlockEntity be ? be.getBehavior() : null;
    }

    /** Ties a rope from {@code from} (it owns the rope) to {@code to}. Both must be connectors. */
    public static boolean tie(ServerLevel level, BlockPos from, BlockPos to) {
        RopeStrandHolderBehavior a = holder(level, from), b = holder(level, to);
        if (a == null || b == null) return false;
        boolean ok = a.createRope(b, false);
        if (!ok) CreateSkybound.LOGGER.info("Sky Raiders: rope from {} to {} refused (too long, or already tied)", from, to);
        return ok;
    }

    /** True while the rope owned by {@code from} still exists. */
    public static boolean intact(ServerLevel level, BlockPos from) {
        RopeStrandHolderBehavior a = holder(level, from);
        return a != null && a.getOwnedStrand() != null;
    }

    /** Removes a connector without dropping it; its rope goes with it. */
    public static void removeConnector(ServerLevel level, BlockPos pos) {
        if (isConnector(level, pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    /**
     * The rope as a path in world coordinates, starting at {@code from}'s end. Uses the rope's own
     * simulated points; if they're missing, a straight line between the two connectors.
     */
    public static List<Vec3> path(ServerLevel level, BlockPos from, BlockPos to) {
        Vec3 start = attachment(level, from), end = attachment(level, to);
        List<Vec3> out = new ArrayList<>();
        RopeStrandHolderBehavior a = holder(level, from);
        ServerRopeStrand strand = a == null ? null : a.getOwnedStrand();
        if (strand != null && start != null && end != null) {
            for (Vector3d p : strand.getPoints()) out.add(new Vec3(p.x, p.y, p.z));
            // The points should run between the two ends in world space; if not, don't trust them.
            if (out.size() >= 2 && (out.get(0).distanceTo(start) < 3 || out.get(out.size() - 1).distanceTo(start) < 3)) {
                if (out.get(0).distanceTo(start) > out.get(out.size() - 1).distanceTo(start)) java.util.Collections.reverse(out);
                return out;
            }
            out.clear();
        }
        if (start != null && end != null) { out.add(start); out.add(end); }
        return out;
    }

    /** A connector's rope attachment point in world coordinates. */
    public static Vec3 attachment(ServerLevel level, BlockPos pos) {
        RopeStrandHolderBehavior h = holder(level, pos);
        if (h == null) return null;
        return SableBridge.project(level, h.getAttachmentPoint());
    }

    private RopeBridge() {}
}
