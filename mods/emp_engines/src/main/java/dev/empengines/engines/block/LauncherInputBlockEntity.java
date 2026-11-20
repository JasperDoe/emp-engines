package dev.empengines.engines.block;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Plain kinetic block entity; the computer reads its speed and stress state. */
public class LauncherInputBlockEntity extends KineticBlockEntity {
    public LauncherInputBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
