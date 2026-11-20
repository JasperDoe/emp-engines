package dev.empengines.researchenchanting.mixin;

import dev.empengines.researchenchanting.menu.ResearchTableMenu;
import dev.empengines.researchenchanting.table.TableAccess;
import dev.empengines.researchenchanting.table.TableData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The vanilla enchanting table opens the research screen, ticks research on the server, drops its book and lapis
 * when broken, and leaves its glyph particles to client.Glyphs.
 */
@Mixin(EnchantingTableBlock.class)
public abstract class EnchantingTableBlockMixin extends BaseEntityBlock {
    protected EnchantingTableBlockMixin(Properties props) {
        super(props);
    }

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void researchenchanting$open(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (level.getBlockEntity(pos) instanceof EnchantingTableBlockEntity be && player instanceof ServerPlayer sp) {
            TableData table = ((TableAccess) be).researchenchanting$data();
            if (table.pending != null) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.researchenchanting.busy"), true);
                cir.setReturnValue(InteractionResult.CONSUME);
                return;
            }
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ResearchTableMenu(id, inv, ContainerLevelAccess.create(level, pos), table),
                    be.getDisplayName()), buf -> buf.writeBlockPos(pos));
        }
        cir.setReturnValue(InteractionResult.CONSUME);
    }

    @Inject(method = "getTicker", at = @At("RETURN"), cancellable = true)
    private <T extends BlockEntity> void researchenchanting$ticker(Level level, BlockState state, BlockEntityType<T> type,
                                                                  CallbackInfoReturnable<BlockEntityTicker<T>> cir) {
        if (!level.isClientSide && type == BlockEntityType.ENCHANTING_TABLE) {
            cir.setReturnValue((l, p, s, be) -> TableData.serverTick(l, p, s, be));
        }
    }

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void researchenchanting$particles(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        // Glyphs come from the table's own client tick instead (client.Glyphs), every tick and denser.
        ci.cancel();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof EnchantingTableBlockEntity be) {
            ((TableAccess) be).researchenchanting$data().dropAll(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
