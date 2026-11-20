package dev.empengines.researchenchanting.mixin;

import dev.empengines.researchenchanting.table.TableAccess;
import dev.empengines.researchenchanting.table.TableData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives every enchanting table its research state (book and lapis slots), saved with the table and sent to clients. */
@Mixin(EnchantingTableBlockEntity.class)
public abstract class EnchantingTableBlockEntityMixin extends BlockEntity implements TableAccess {
    @Unique
    private TableData researchenchanting$table;

    protected EnchantingTableBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public TableData researchenchanting$data() {
        if (researchenchanting$table == null) researchenchanting$table = new TableData((EnchantingTableBlockEntity) (Object) this);
        return researchenchanting$table;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void researchenchanting$save(CompoundTag tag, HolderLookup.Provider reg, CallbackInfo ci) {
        researchenchanting$data().save(tag, reg);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void researchenchanting$load(CompoundTag tag, HolderLookup.Provider reg, CallbackInfo ci) {
        researchenchanting$data().load(tag, reg);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        return researchenchanting$data().clientTag(reg);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
