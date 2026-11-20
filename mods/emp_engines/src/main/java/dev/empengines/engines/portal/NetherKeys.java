package dev.empengines.engines.portal;

import dev.empengines.engines.Config;
import dev.empengines.engines.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.lang.reflect.Field;

/**
 * Nether portals need a key. Lighting a finished frame turns the frame block under the fire into an
 * Obsidian Keyhole and puts the fire out; once an Obsidian Key has been turned in it, the portal lights.
 */
public final class NetherKeys {
    private NetherKeys() {}

    @SubscribeEvent
    public static void onPortal(BlockEvent.PortalSpawnEvent e) {
        if (!Config.KEYHOLES.get() || !(e.getLevel() instanceof ServerLevel level)) return;
        if (Config.FREE_IN_NETHER.get() && level.dimension() == Level.NETHER) return;
        Frame frame = Frame.of(e.getPortalSize(), e.getPos(), level);
        BlockPos keyhole = null;
        for (BlockPos p : frame.blocks()) {
            BlockState s = level.getBlockState(p);
            if (s.is(ModRegistry.OBSIDIAN_KEYHOLE.get())) {
                if (s.getValue(ObsidianKeyholeBlock.UNLOCKED)) return;   // unlocked: let it light
                keyhole = p;
            }
        }
        e.setCanceled(true);
        BlockPos fire = e.getPos().immutable();
        BlockPos target = keyhole != null ? keyhole : frame.under(fire);
        // Change blocks after the fire has finished being placed
        level.getServer().execute(() -> {
            if (level.getBlockState(fire).getBlock() instanceof BaseFireBlock) level.removeBlock(fire, false);
            level.playSound(null, fire, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.2f);
            BlockState was = level.getBlockState(target);
            if (was.is(Blocks.OBSIDIAN) || was.isPortalFrame(level, target) && !was.is(ModRegistry.OBSIDIAN_KEYHOLE.get()))
                level.setBlock(target, ModRegistry.OBSIDIAN_KEYHOLE.get().defaultBlockState(), Block.UPDATE_ALL);
            Player who = level.getNearestPlayer(fire.getX() + 0.5, fire.getY() + 0.5, fire.getZ() + 0.5, 8, false);
            if (who != null)
                who.displayClientMessage(Component.translatable("block.emp_engines.obsidian_keyhole.wont_light")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        });
    }

    /** The frame round a portal (the blocks around its inside), from vanilla's PortalShape. */
    record Frame(BlockPos bottomLeft, Direction right, int width, int height) {
        private static Field fBottomLeft, fRight, fWidth, fHeight;

        static Frame of(PortalShape shape, BlockPos fire, Level level) {
            try {
                if (fBottomLeft == null) {
                    fBottomLeft = PortalShape.class.getDeclaredField("bottomLeft");
                    fRight = PortalShape.class.getDeclaredField("rightDir");
                    fWidth = PortalShape.class.getDeclaredField("width");
                    fHeight = PortalShape.class.getDeclaredField("height");
                    for (Field f : new Field[]{fBottomLeft, fRight, fWidth, fHeight}) f.setAccessible(true);
                }
                return new Frame((BlockPos) fBottomLeft.get(shape), (Direction) fRight.get(shape), fWidth.getInt(shape), fHeight.getInt(shape));
            } catch (ReflectiveOperationException | RuntimeException ex) {
                // Fallback: just the frame block under the fire
                BlockPos p = fire;
                for (int i = 0; i < 24 && (level.isEmptyBlock(p.below()) || level.getBlockState(p.below()).getBlock() instanceof BaseFireBlock); i++)
                    p = p.below();
                return new Frame(p, Direction.EAST, 1, 1);
            }
        }

        /** Every frame block: the bottom and top rows and both sides (corners not needed). */
        Iterable<BlockPos> blocks() {
            java.util.List<BlockPos> out = new java.util.ArrayList<>();
            for (int i = 0; i < width; i++) {
                out.add(bottomLeft.relative(right, i).below());
                out.add(bottomLeft.relative(right, i).above(height));
            }
            for (int j = 0; j < height; j++) {
                out.add(bottomLeft.relative(right, -1).above(j));
                out.add(bottomLeft.relative(right, width).above(j));
            }
            return out;
        }

        /** The bottom frame block under the fire's column. */
        BlockPos under(BlockPos fire) {
            return new BlockPos(fire.getX(), bottomLeft.getY() - 1, fire.getZ());
        }
    }
}
