package dev.empengines.engines.portal;

import dev.empengines.engines.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Obsidian with a keyhole, put into a nether portal's frame the first time someone tries to light it.
 * Counts as obsidian for the portal frame. Locked, the portal won't light; an Obsidian Key unlocks it.
 */
public class ObsidianKeyholeBlock extends Block {
    public static final BooleanProperty UNLOCKED = BooleanProperty.create("unlocked");

    public ObsidianKeyholeBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(UNLOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(UNLOCKED); }

    @Override
    public boolean isPortalFrame(BlockState state, BlockGetter level, BlockPos pos) { return true; }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        boolean key = stack.is(ModRegistry.OBSIDIAN_KEY.get());
        boolean fragment = stack.is(ModRegistry.FRAGMENTED_OBSIDIAN_KEY.get());
        if (!key && !fragment) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (state.getValue(UNLOCKED)) {
            if (hand == InteractionHand.MAIN_HAND) tell(player, "block.emp_engines.obsidian_keyhole.already_open");
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (fragment) {
            level.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM_FAIL, SoundSource.BLOCKS, 1f, 0.8f);
            tell(player, "block.emp_engines.obsidian_keyhole.fragment");
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(UNLOCKED, true), Block.UPDATE_ALL);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS, 1f, 0.9f);
            level.playSound(null, pos, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1f, 0.7f);
            if (level instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.05);
            player.displayClientMessage(Component.translatable("block.emp_engines.obsidian_keyhole.unlocked").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(UNLOCKED)) return InteractionResult.PASS;   // nothing to say (also stops the off hand repeating it)
        tell(player, "block.emp_engines.obsidian_keyhole.locked");
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void tell(Player player, String key) {
        if (!player.level().isClientSide)
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
    }
}
