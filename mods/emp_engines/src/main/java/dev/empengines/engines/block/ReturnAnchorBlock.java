package dev.empengines.engines.block;

import dev.empengines.engines.sky.SkyRuns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Stands at the arrival point in the Etheric Skies. Right-click to go home (with your ship if it's moored nearby). */
public class ReturnAnchorBlock extends Block {
    public ReturnAnchorBlock(Properties props) { super(props); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) SkyRuns.returnHome(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
