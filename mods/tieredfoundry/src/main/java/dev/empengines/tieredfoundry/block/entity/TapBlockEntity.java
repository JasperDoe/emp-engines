package dev.empengines.tieredfoundry.block.entity;

import dev.empengines.tieredfoundry.Config;
import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.block.TapBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Pours from the block behind it into the block below until the target is full or the source is empty. */
public class TapBlockEntity extends BlockEntity {
    private boolean pouring;
    private boolean signal;
    /** The fluid currently falling, for the renderer (amount 1). */
    private FluidStack showing = FluidStack.EMPTY;

    public TapBlockEntity(BlockPos pos, BlockState state) {
        super(Registry.TAP_BE.get(), pos, state);
    }

    public FluidStack showing() { return showing; }

    public void toggle() {
        if (pouring) stop(); else pouring = true;
        setChanged();
    }

    public void redstone(boolean on) {
        if (on && !signal) pouring = true;
        signal = on;
        setChanged();
    }

    public void serverTick() {
        if (!pouring) return;
        Direction facing = getBlockState().getValue(TapBlock.FACING);
        IFluidHandler from = level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.relative(facing.getOpposite()), facing);
        IFluidHandler to = level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.below(), Direction.UP);
        if (from == null || to == null) { stop(); return; }
        FluidStack offer = from.drain(Config.TAP_RATE.get(), IFluidHandler.FluidAction.SIMULATE);
        if (offer.isEmpty()) { stop(); return; }
        int accepted = to.fill(offer, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) { stop(); return; }
        FluidStack moved = from.drain(offer.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (moved.isEmpty()) { stop(); return; }
        to.fill(moved, IFluidHandler.FluidAction.EXECUTE);
        if (!FluidStack.isSameFluidSameComponents(showing, moved)) {
            showing = moved.copyWithAmount(1);
            sync();
        }
    }

    private void stop() {
        pouring = false;
        if (!showing.isEmpty()) {
            showing = FluidStack.EMPTY;
            sync();
        }
    }

    private void sync() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putBoolean("Pouring", pouring);
        tag.putBoolean("Signal", signal);
        if (!showing.isEmpty()) tag.put("Showing", showing.save(reg));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        pouring = tag.getBoolean("Pouring");
        signal = tag.getBoolean("Signal");
        showing = tag.contains("Showing") ? FluidStack.parseOptional(reg, tag.getCompound("Showing")) : FluidStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        return saveWithoutMetadata(reg);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
