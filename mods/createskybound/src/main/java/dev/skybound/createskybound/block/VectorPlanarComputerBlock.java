package dev.skybound.createskybound.block;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.skybound.createskybound.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The launcher controller. Takes rotation through a shaft from below, sits next to the
 * platform's brass casing ring, and fires the launch on a rising redstone signal.
 */
public class VectorPlanarComputerBlock extends KineticBlock implements IBE<VectorPlanarComputerBlockEntity> {
    public VectorPlanarComputerBlock(Properties props) { super(props); }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public Class<VectorPlanarComputerBlockEntity> getBlockEntityClass() { return VectorPlanarComputerBlockEntity.class; }

    @Override
    public BlockEntityType<? extends VectorPlanarComputerBlockEntity> getBlockEntityType() {
        return ModRegistry.VECTOR_PLANAR_COMPUTER_BE.get();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide) withBlockEntityDo(level, pos, be -> be.onRedstone(level.hasNeighborSignal(pos)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) withBlockEntityDo(level, pos, be -> be.report(player));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
