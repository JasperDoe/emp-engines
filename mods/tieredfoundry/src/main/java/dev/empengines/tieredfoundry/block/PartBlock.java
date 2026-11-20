package dev.empengines.tieredfoundry.block;

import dev.empengines.tieredfoundry.block.entity.ControllerBlockEntity;
import dev.empengines.tieredfoundry.block.entity.PartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A foundry block that links to a controller: floor heaters and ducts, and wall drains. Its textures follow the
 * foundry's tier (TIER) and whether it is burning (LIT).
 */
public class PartBlock extends Block implements EntityBlock {
    public enum Kind { SOLID_HEATER, LAVA_HEATER, AIR_DUCT, DRAIN;
        public boolean heater() { return this == SOLID_HEATER || this == LAVA_HEATER; }
    }

    public final Kind kind;

    public PartBlock(Kind kind, Properties p) {
        super(p);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FoundryProps.TIER, 1).setValue(FoundryProps.LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FoundryProps.TIER, FoundryProps.LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PartBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PartBlockEntity part)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (kind == Kind.SOLID_HEATER && !stack.isEmpty()) {
            IItemHandler h = part.items();
            if (h == null) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.tieredfoundry.not_linked"), true);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            ItemStack left = ItemHandlerHelper.insertItemStacked(h, stack.copy(), false);
            if (left.getCount() == stack.getCount()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, left);
            return ItemInteractionResult.SUCCESS;
        }
        if ((kind == Kind.LAVA_HEATER || kind == Kind.DRAIN) && part.fluids() != null
                && FluidUtil.getFluidHandler(stack).isPresent()) {
            if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // any linked part opens the controller's screen
        if (!(level.getBlockEntity(pos) instanceof PartBlockEntity part) || part.controller() == null) return net.minecraft.world.InteractionResult.PASS;
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            ControllerBlockEntity c = part.controller();
            sp.openMenu(c, buf -> buf.writeBlockPos(c.getBlockPos()));
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) ControllerBlockEntity.Watch.changed(level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}
