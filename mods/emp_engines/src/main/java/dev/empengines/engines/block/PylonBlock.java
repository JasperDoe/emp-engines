package dev.empengines.engines.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Resonance Pylon: stands on top of a launch platform's corner. A small gold base, and above it
 * (drawn by {@code client.PylonRenderer}) a floating crystal shard with a gold ring orbiting it. The
 * shard glows brighter and the ring spins faster as the launcher charges ({@link #CHARGE}, set by the
 * computer). The lowest pylon tier on a platform sets how long the sky run lasts.
 */
public class PylonBlock extends Block implements EntityBlock {
    /** 0 (no charge) to 4 (full or launching). */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    private static final VoxelShape SHAPE = Shapes.or(box(2, 0, 2, 14, 2, 14), box(4, 2, 4, 12, 4, 12), box(5.5, 5, 5.5, 10.5, 16, 10.5));

    private final int tier;

    public PylonBlock(int tier, Properties props) {
        super(props);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    public int tier() { return tier; }

    /** Light from the shard: brighter with charge. */
    public static int light(BlockState s) { return 5 + 2 * s.getValue(CHARGE); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(CHARGE); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PylonBlockEntity(pos, state); }
}
