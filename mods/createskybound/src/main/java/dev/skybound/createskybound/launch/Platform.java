package dev.skybound.createskybound.launch;

import com.simibubi.create.AllBlocks;
import dev.skybound.createskybound.Config;
import dev.skybound.createskybound.block.PylonBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A launch platform: a flat rectangle with a brass casing ring, quartz blocks filling the
 * centre and a Resonance Pylon on each corner. The computer sits next to the ring.
 */
public record Platform(int minX, int minZ, int maxX, int maxZ, int y, int tier) {

    public BlockPos centre() { return new BlockPos((minX + maxX) / 2, y, (minZ + maxZ) / 2); }

    public List<BlockPos> corners() {
        return List.of(new BlockPos(minX, y, minZ), new BlockPos(maxX, y, minZ), new BlockPos(minX, y, maxZ), new BlockPos(maxX, y, maxZ));
    }

    /** Finds and checks the platform next to the computer, or returns null with the reason in {@code why[0]}. */
    public static Platform find(Level level, BlockPos computer, String[] why) {
        int max = Config.MAX_PLATFORM.get();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos start = computer.relative(d);
            if (!isRing(level.getBlockState(start))) continue;
            // Walk the ring edge in both directions along the side we touched.
            Direction along = d.getClockWise();
            int a = 0, b = 0;
            while (a < max && isRingOrPylon(level.getBlockState(start.relative(along, a + 1)))) a++;
            while (b < max && isRingOrPylon(level.getBlockState(start.relative(along.getOpposite(), b + 1)))) b++;
            BlockPos end1 = start.relative(along, a), end2 = start.relative(along.getOpposite(), b);
            if (!(level.getBlockState(end1).getBlock() instanceof PylonBlock) || !(level.getBlockState(end2).getBlock() instanceof PylonBlock)) continue;
            // Walk inward from one corner to find the depth.
            int depth = 0;
            while (depth < max && isRingOrPylon(level.getBlockState(end1.relative(d, depth + 1)))) depth++;
            BlockPos far = end1.relative(d, depth);
            int minX = Math.min(Math.min(end1.getX(), end2.getX()), far.getX());
            int maxX = Math.max(Math.max(end1.getX(), end2.getX()), far.getX());
            int minZ = Math.min(Math.min(end1.getZ(), end2.getZ()), far.getZ());
            int maxZ = Math.max(Math.max(end1.getZ(), end2.getZ()), far.getZ());
            Platform p = validate(level, minX, minZ, maxX, maxZ, start.getY(), why);
            if (p != null) return p;
        }
        if (why[0] == null) why[0] = "Place the computer next to the brass casing ring of a launch platform.";
        return null;
    }

    private static Platform validate(Level level, int minX, int minZ, int maxX, int maxZ, int y, String[] why) {
        if (maxX - minX < 2 || maxZ - minZ < 2) { why[0] = "The platform is too small (at least 3 by 3)."; return null; }
        int tier = 3;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockState s = level.getBlockState(new BlockPos(x, y, z));
                boolean cornerX = x == minX || x == maxX, cornerZ = z == minZ || z == maxZ;
                if (cornerX && cornerZ) {
                    if (!(s.getBlock() instanceof PylonBlock pylon)) { why[0] = "Each corner needs a Resonance Pylon."; return null; }
                    tier = Math.min(tier, pylon.tier());
                } else if (cornerX || cornerZ) {
                    if (!isRing(s)) { why[0] = "The edge must be brass casing (gap at " + x + ", " + y + ", " + z + ")."; return null; }
                } else if (!s.is(Blocks.QUARTZ_BLOCK)) {
                    why[0] = "The centre must be filled with quartz blocks (gap at " + x + ", " + y + ", " + z + ")."; return null;
                }
            }
        }
        return new Platform(minX, minZ, maxX, maxZ, y, tier);
    }

    private static boolean isRing(BlockState s) { return s.is(AllBlocks.BRASS_CASING.get()); }

    private static boolean isRingOrPylon(BlockState s) { return isRing(s) || s.getBlock() instanceof PylonBlock; }
}
