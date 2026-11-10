package dev.skybound.createskybound.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.skybound.createskybound.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * The launcher controller: a brass terminal that sits on top of the platform's ring.
 * It is not kinetic itself; it charges from the ring's shaft inputs and fires on a rising redstone signal.
 */
public class VectorPlanarComputerBlock extends HorizontalDirectionalBlock implements IBE<VectorPlanarComputerBlockEntity>, IWrenchable {
    public static final MapCodec<VectorPlanarComputerBlock> CODEC = simpleCodec(VectorPlanarComputerBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // Base and keyboard deck, then the monitor housing behind them (model faces north).
        double[][] boxes = {{0, 0, 0, 16, 4.5, 16}, {1.5, 4.5, 7, 14.5, 14.5, 16}};
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape s = Shapes.empty();
            for (double[] b : boxes) s = Shapes.or(s, rotated(b, d));
            SHAPES.put(d, s);
        }
    }

    /** Rotates a north-facing box (in pixels) to face {@code d}. */
    private static VoxelShape rotated(double[] b, Direction d) {
        double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
        double[] r = switch (d) {
            case SOUTH -> new double[]{16 - x2, 16 - z2, 16 - x1, 16 - z1};
            case EAST -> new double[]{16 - z2, x1, 16 - z1, x2};
            case WEST -> new double[]{z1, 16 - x2, z2, 16 - x1};
            default -> new double[]{x1, z1, x2, z2};
        };
        return Block.box(r[0], b[1], r[1], r[2], b[4], r[3]);
    }

    public VectorPlanarComputerBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public Class<VectorPlanarComputerBlockEntity> getBlockEntityClass() { return VectorPlanarComputerBlockEntity.class; }

    @Override
    public BlockEntityType<? extends VectorPlanarComputerBlockEntity> getBlockEntityType() {
        return ModRegistry.VECTOR_PLANAR_COMPUTER_BE.get();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide) withBlockEntityDo(level, pos, be -> be.onRedstone(level.hasNeighborSignal(pos)));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) withBlockEntityDo(level, pos, be -> be.report(player));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
