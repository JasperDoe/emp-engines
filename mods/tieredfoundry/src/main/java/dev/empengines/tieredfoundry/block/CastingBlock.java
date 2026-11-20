package dev.empengines.tieredfoundry.block;

import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.block.entity.CastingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Casting table (with a cast: ingots, nuggets, plates, new casts) and casting basin (blocks, fire bricks). */
public class CastingBlock extends Block implements EntityBlock {
    private static final VoxelShape TABLE = Shapes.or(box(0, 10, 0, 16, 16, 16),
            box(0, 0, 0, 4, 10, 4), box(12, 0, 0, 16, 10, 4), box(0, 0, 12, 4, 10, 16), box(12, 0, 12, 16, 10, 16));
    private static final VoxelShape BASIN = Shapes.join(Shapes.or(box(0, 2, 0, 16, 16, 16),
            box(0, 0, 0, 5, 2, 5), box(11, 0, 0, 16, 2, 5), box(0, 0, 11, 5, 2, 16), box(11, 0, 11, 16, 2, 16)),
            box(2, 4, 2, 14, 16, 14), BooleanOp.ONLY_FIRST);

    public final boolean basin;

    public CastingBlock(boolean basin, Properties p) {
        super(p);
        this.basin = basin;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return basin ? BASIN : TABLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CastingBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != Registry.CASTING_BE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<CastingBlockEntity>) (l, p, s, be) -> be.serverTick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CastingBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return be.use(player, hand, stack) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CastingBlockEntity be) || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        return be.use(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CastingBlockEntity be) {
            Containers.dropContents(level, pos, be.dropList());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
