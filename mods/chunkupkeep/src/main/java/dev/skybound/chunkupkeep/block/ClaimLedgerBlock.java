package dev.skybound.chunkupkeep.block;

import com.mojang.serialization.MapCodec;
import dev.skybound.chunkupkeep.LedgerData;
import dev.skybound.chunkupkeep.LedgerService;
import dev.skybound.chunkupkeep.Teams;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Place it to claim land around it. Right-click to open the claim map. Claims stay if it's broken. */
public class ClaimLedgerBlock extends BaseEntityBlock {
    public static final MapCodec<ClaimLedgerBlock> CODEC = simpleCodec(ClaimLedgerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ClaimLedgerBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ClaimLedgerBlockEntity(pos, state); }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !(placer instanceof ServerPlayer sp)) return;
        if (level.getBlockEntity(pos) instanceof ClaimLedgerBlockEntity be) {
            be.setTeam(Teams.idOf(sp));
            LedgerData.get(sp.server).addLedger(be.team(), GlobalPos.of(level.dimension(), pos));
            sp.displayClientMessage(Component.translatable("chunkupkeep.msg.placed"), false);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof ClaimLedgerBlockEntity be) {
            if (be.team() == null || !be.team().equals(Teams.idOf(sp))) {
                sp.displayClientMessage(Component.translatable("chunkupkeep.msg.not_yours"), true);
                return InteractionResult.CONSUME;
            }
            LedgerService.open(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level.getServer() != null) {
            LedgerData.get(level.getServer()).removeLedger(GlobalPos.of(level.dimension(), pos));
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
