package dev.skybound.createskybound.block;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.skybound.createskybound.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/**
 * A brass casing in a launcher ring that has been wrenched open into a shaft input.
 * Looks like a brass encased shaft and takes rotation along its axis; the computer
 * charges from every input on its ring. Breaking it gives the brass casing back.
 */
public class LauncherInputBlock extends RotatedPillarKineticBlock implements IBE<LauncherInputBlockEntity> {
    public LauncherInputBlock(Properties props) { super(props); }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(AXIS); }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(AllBlocks.BRASS_CASING.get());
    }

    @Override
    public Class<LauncherInputBlockEntity> getBlockEntityClass() { return LauncherInputBlockEntity.class; }

    @Override
    public BlockEntityType<? extends LauncherInputBlockEntity> getBlockEntityType() { return ModRegistry.LAUNCHER_INPUT_BE.get(); }
}
