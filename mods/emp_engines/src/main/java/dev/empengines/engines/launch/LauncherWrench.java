package dev.empengines.engines.launch;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import dev.empengines.engines.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Wrenching a brass casing in the ring of an assembled launcher opens it into a shaft input,
 * on the axis of the face that was clicked. Wrench the input again to turn it, sneak-wrench to
 * take it back as brass casing.
 */
public final class LauncherWrench {

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock e) {
        Player player = e.getEntity();
        if (player.isShiftKeyDown() || !e.getItemStack().is(AllItems.WRENCH.get())) return;
        Level level = e.getLevel();
        BlockPos pos = e.getPos();
        Direction face = e.getFace();
        if (face == null || !level.getBlockState(pos).is(AllBlocks.BRASS_CASING.get())) return;
        if (Platform.containing(level, pos) == null) return;   // only rings of a finished launcher

        if (!level.isClientSide) {
            level.setBlock(pos, ModRegistry.LAUNCHER_INPUT.get().defaultBlockState()
                    .setValue(RotatedPillarKineticBlock.AXIS, face.getAxis()), 3);
            IWrenchable.playRotateSound(level, pos);
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }

    private LauncherWrench() {}
}
