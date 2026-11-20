package dev.empengines.tieredfoundry.block;

import com.mojang.serialization.MapCodec;
import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.block.entity.TapBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * A tap on the outside of a drain (or any tank), pouring down into the block below. Click it or give it a redstone
 * pulse to pour; it stops when the cast below is full or the source runs dry. FACING points away from the source.
 */
public class TapBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<TapBlock> CODEC = simpleCodec(TapBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // The Tinkers faucet model faces the source to the south: x 4..12, y 4..10, z 10..16
        SHAPES.put(Direction.NORTH, box(4, 4, 10, 12, 10, 16));
        SHAPES.put(Direction.SOUTH, box(4, 4, 0, 12, 10, 6));
        SHAPES.put(Direction.EAST, box(0, 4, 4, 6, 10, 12));
        SHAPES.put(Direction.WEST, box(10, 4, 4, 16, 10, 12));
    }

    public TapBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FoundryProps.TIER, 1));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, FoundryProps.TIER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        BlockState s = defaultBlockState().setValue(FACING, facing);
        return withTier(s, ctx.getLevel().getBlockState(ctx.getClickedPos().relative(facing.getOpposite())));
    }

    private static BlockState withTier(BlockState tap, BlockState source) {
        return source.hasProperty(FoundryProps.TIER) ? tap.setValue(FoundryProps.TIER, source.getValue(FoundryProps.TIER)) : tap;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos npos) {
        if (dir == state.getValue(FACING).getOpposite()) return withTier(state, neighbour);
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TapBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != Registry.TAP_BE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<TapBlockEntity>) (l, p, s, be) -> be.serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TapBlockEntity tap) tap.toggle();
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof TapBlockEntity tap)) return;
        tap.redstone(level.hasNeighborSignal(pos));
    }
}
