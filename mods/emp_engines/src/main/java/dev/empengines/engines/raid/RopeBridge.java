package dev.empengines.engines.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The boarding ropes' ends: Create Aeronautics' rope connector blocks (its bundled Simulated module).
 * The rope between them is a {@link BoardingRope} (drawn, not simulated); breaking either connector cuts it.
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

    /** Removes a connector without dropping it. */
    public static void removeConnector(ServerLevel level, BlockPos pos) {
        if (isConnector(level, pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private RopeBridge() {}
}
