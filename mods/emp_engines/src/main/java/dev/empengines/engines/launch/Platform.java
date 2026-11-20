package dev.empengines.engines.launch;

import com.simibubi.create.AllBlocks;
import dev.empengines.engines.Config;
import dev.empengines.engines.ModRegistry;
import dev.empengines.engines.block.PylonBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A launch platform: a flat rectangle with a brass casing ring (corners included), quartz blocks
 * filling the centre and a Resonance Pylon standing on top of each corner. The computer sits on top
 * of the ring, and wrenched ring casings ({@link dev.empengines.engines.block.LauncherInputBlock}) take rotation in.
 */
public record Platform(int minX, int minZ, int maxX, int maxZ, int y, int tier) {

    public BlockPos centre() { return new BlockPos((minX + maxX) / 2, y, (minZ + maxZ) / 2); }

    /** The four ring corners (the pylons stand on top of them). */
    public List<BlockPos> corners() {
        return List.of(new BlockPos(minX, y, minZ), new BlockPos(maxX, y, minZ), new BlockPos(minX, y, maxZ), new BlockPos(maxX, y, maxZ));
    }

    public List<BlockPos> pylons() {
        return corners().stream().map(BlockPos::above).toList();
    }

    /** True for ring blocks (edges and corners). */
    public boolean isEdge(BlockPos p) {
        if (p.getY() != y || p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ) return false;
        return p.getX() == minX || p.getX() == maxX || p.getZ() == minZ || p.getZ() == maxZ;
    }

    /** Width and depth multiplied (ring included). */
    public int area() { return (maxX - minX + 1) * (maxZ - minZ + 1); }

    /** Every ring block, corners included. */
    public List<BlockPos> edges() {
        List<BlockPos> out = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) { out.add(new BlockPos(x, y, minZ)); out.add(new BlockPos(x, y, maxZ)); }
        for (int z = minZ + 1; z < maxZ; z++) { out.add(new BlockPos(minX, y, z)); out.add(new BlockPos(maxX, y, z)); }
        return out;
    }

    static int maxSize() {
        try { return Config.MAX_PLATFORM.get(); } catch (IllegalStateException e) { return 33; }   // e.g. in a Ponder scene
    }

    /** Finds and checks the platform under the computer, or returns null with the reason in {@code why[0]}. */
    public static Platform find(Level level, BlockPos computer, String[] why) {
        BlockPos start = computer.below();
        if (!isRing(level.getBlockState(start))) {
            why[0] = "Place the computer on top of the brass casing ring of a launch platform.";
            return null;
        }
        int max = maxSize();
        for (Direction along : new Direction[]{Direction.EAST, Direction.SOUTH}) {
            // Walk the edge both ways to its last ring blocks: the corners, each with a pylon on top.
            int a = 0, b = 0;
            while (a < max && isRing(level.getBlockState(start.relative(along, a + 1)))) a++;
            while (b < max && isRing(level.getBlockState(start.relative(along.getOpposite(), b + 1)))) b++;
            BlockPos end1 = start.relative(along, a), end2 = start.relative(along.getOpposite(), b);
            if (end1.equals(end2) || !hasPylon(level, end1) || !hasPylon(level, end2)) continue;
            // Then walk along the other edge from a corner, trying both ways.
            for (Direction in : new Direction[]{along.getClockWise(), along.getCounterClockWise()}) {
                int depth = 0;
                while (depth < max && isRing(level.getBlockState(end1.relative(in, depth + 1)))) depth++;
                BlockPos far = end1.relative(in, depth);
                if (depth < 2 || !hasPylon(level, far)) continue;
                int minX = Math.min(Math.min(end1.getX(), end2.getX()), far.getX());
                int maxX = Math.max(Math.max(end1.getX(), end2.getX()), far.getX());
                int minZ = Math.min(Math.min(end1.getZ(), end2.getZ()), far.getZ());
                int maxZ = Math.max(Math.max(end1.getZ(), end2.getZ()), far.getZ());
                Platform p = validate(level, minX, minZ, maxX, maxZ, start.getY(), why);
                if (p != null) return p;
            }
        }
        if (why[0] == null) why[0] = "The ring under the computer needs a Resonance Pylon standing on each of its corners.";
        return null;
    }

    /** The assembled platform whose ring contains {@code ringPos}, found through a computer standing on it. */
    public static Platform containing(Level level, BlockPos ringPos) {
        int max = maxSize();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                m.set(ringPos.getX() + dx, ringPos.getY() + 1, ringPos.getZ() + dz);
                if (!level.getBlockState(m).is(ModRegistry.VECTOR_PLANAR_COMPUTER.get())) continue;
                Platform p = find(level, m.immutable(), new String[1]);
                if (p != null && p.isEdge(ringPos)) return p;
            }
        }
        return null;
    }

    private static Platform validate(Level level, int minX, int minZ, int maxX, int maxZ, int y, String[] why) {
        if (maxX - minX < 4 || maxZ - minZ < 4) { why[0] = "The platform is too small (at least 5 by 5)."; return null; }
        int tier = 3;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(pos);
                boolean edgeX = x == minX || x == maxX, edgeZ = z == minZ || z == maxZ;
                if (edgeX || edgeZ) {
                    if (!isRing(s)) { why[0] = "The ring must be brass casing all round, corners too (gap at " + x + ", " + y + ", " + z + ")."; return null; }
                    if (edgeX && edgeZ) {
                        if (!(level.getBlockState(pos.above()).getBlock() instanceof PylonBlock pylon)) {
                            why[0] = "Each corner of the ring needs a Resonance Pylon on top."; return null;
                        }
                        tier = Math.min(tier, pylon.tier());
                    }
                } else if (!s.is(Blocks.QUARTZ_BLOCK)) {
                    why[0] = "The centre must be filled with quartz blocks (gap at " + x + ", " + y + ", " + z + ")."; return null;
                }
            }
        }
        return new Platform(minX, minZ, maxX, maxZ, y, tier);
    }

    /** Brass casing, or a casing that has been wrenched into a shaft input. */
    public static boolean isRing(BlockState s) {
        return s.is(AllBlocks.BRASS_CASING.get()) || s.is(ModRegistry.LAUNCHER_INPUT.get());
    }

    private static boolean hasPylon(Level level, BlockPos corner) {
        return level.getBlockState(corner.above()).getBlock() instanceof PylonBlock;
    }
}
