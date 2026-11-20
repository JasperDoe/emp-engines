package dev.empengines.engines.block;

import dev.empengines.engines.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Only there so the floating shard and its ring can be drawn ({@code client.PylonRenderer}). */
public class PylonBlockEntity extends BlockEntity {
    public PylonBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PYLON_BE.get(), pos, state);
    }

    public AABB renderBox() { return new AABB(worldPosition).inflate(0.5, 1, 0.5); }
}
