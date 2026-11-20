package dev.empengines.tieredfoundry.block;

import com.mojang.serialization.MapCodec;
import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.block.entity.ControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.Containers;

/** The foundry's face: sits in a wall, facing out. Items go in through its screen (or any hopper or funnel). */
public class ControllerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ControllerBlock> CODEC = simpleCodec(ControllerBlock::new);

    public ControllerBlock(Properties p) {
        super(p.lightLevel(s -> s.getValue(FoundryProps.LIT) ? 13 : 0));
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(FoundryProps.TIER, 1).setValue(FoundryProps.LIT, false));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, FoundryProps.TIER, FoundryProps.LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControllerBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != Registry.CONTROLLER_BE.get()) return null;
        if (level.isClientSide) return (BlockEntityTicker<T>) (BlockEntityTicker<ControllerBlockEntity>) (l, p, s, be) -> be.clientTick();
        return (BlockEntityTicker<T>) (BlockEntityTicker<ControllerBlockEntity>) (l, p, s, be) -> be.serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ControllerBlockEntity be && player instanceof ServerPlayer sp) {
            be.recheckSoon();
            sp.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
                be.dropContents();
                Containers.dropContents(level, pos, be.dropList());
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
