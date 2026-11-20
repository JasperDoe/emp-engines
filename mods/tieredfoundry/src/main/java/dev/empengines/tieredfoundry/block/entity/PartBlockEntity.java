package dev.empengines.tieredfoundry.block.entity;

import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.block.PartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Objects;

/**
 * Heaters, ducts and drains: remembers its controller and hands out the controller's shared inventories, so a hopper on
 * any solid fuel heater feeds the one fuel store and a pipe into any lava heater fills the one lava tank.
 */
public class PartBlockEntity extends BlockEntity {
    private BlockPos controller;

    public PartBlockEntity(BlockPos pos, BlockState state) {
        super(Registry.PART_BE.get(), pos, state);
    }

    public BlockPos controllerPos() { return controller; }

    public void link(BlockPos c) {
        if (Objects.equals(controller, c)) return;
        controller = c;
        setChanged();
        if (level != null) level.invalidateCapabilities(worldPosition);
    }

    public ControllerBlockEntity controller() {
        if (controller == null || level == null) return null;
        return level.getBlockEntity(controller) instanceof ControllerBlockEntity c && c.isFormed() ? c : null;
    }

    private PartBlock.Kind kind() {
        return getBlockState().getBlock() instanceof PartBlock p ? p.kind : PartBlock.Kind.AIR_DUCT;
    }

    public IItemHandler items() {
        ControllerBlockEntity c = controller();
        return c != null && kind() == PartBlock.Kind.SOLID_HEATER ? c.fuel() : null;
    }

    public IFluidHandler fluids() {
        ControllerBlockEntity c = controller();
        if (c == null) return null;
        return switch (kind()) {
            case DRAIN -> c.moltenHandler();
            case LAVA_HEATER -> c.lava();
            default -> null;
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        // relative, so the link survives the foundry being moved (Sable ships, Create contraptions)
        if (controller != null) tag.putLong("ControllerOff", controller.subtract(worldPosition).asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("ControllerOff")) controller = worldPosition.offset(BlockPos.of(tag.getLong("ControllerOff")));
        else if (tag.contains("Controller")) {   // older saves: absolute, only trusted if still nearby
            BlockPos c = BlockPos.of(tag.getLong("Controller"));
            controller = c.distManhattan(worldPosition) <= 32 ? c : null;
        } else controller = null;
    }
}
