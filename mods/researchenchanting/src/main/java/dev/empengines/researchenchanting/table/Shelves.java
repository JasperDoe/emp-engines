package dev.empengines.researchenchanting.table;

import dev.empengines.researchenchanting.item.ResearchData;
import dev.empengines.researchenchanting.item.ResearchFocusItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The chiseled bookshelves around a table, up to 3 blocks out and up to 2 high, with clear blocks
 * between. Plain bookshelves count for nothing.
 */
public final class Shelves {
    /** How far shelves and containers can be from the table, in blocks (vanilla's shelves reach 2). */
    public static final int REACH = 3;

    /**
     * Chiseled bookshelves in reach: up to {@link #REACH} blocks out, at the table's level or one up, not touching
     * the table, and with nothing solid in between (every block on the way must let enchanting power through,
     * like vanilla's gap block).
     */
    public static List<BlockPos> shelves(Level level, BlockPos table) {
        List<BlockPos> out = new ArrayList<>();
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) continue;
                    BlockPos at = table.offset(dx, dy, dz);
                    if (!level.getBlockState(at).is(Blocks.CHISELED_BOOKSHELF)) continue;
                    if (clearBetween(level, table, dx, dy, dz)) out.add(at);
                }
            }
        }
        return out;
    }

    /** The blocks on the straight line from the table to the shelf (both excluded) let power through. */
    private static boolean clearBetween(Level level, BlockPos table, int dx, int dy, int dz) {
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        for (int k = 1; k < steps; k++) {
            int x = Math.round(dx * k / (float) steps), z = Math.round(dz * k / (float) steps);
            if (!level.getBlockState(table.offset(x, dy, z)).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER)) return false;
        }
        return true;
    }

    /** Every book (any item) in the shelves in reach. */
    public static List<ItemStack> books(Level level, BlockPos table) {
        List<ItemStack> out = new ArrayList<>();
        for (BlockPos at : shelves(level, table)) {
            if (!(level.getBlockEntity(at) instanceof ChiseledBookShelfBlockEntity shelf)) continue;
            for (int i = 0; i < shelf.getContainerSize(); i++) {
                ItemStack s = shelf.getItem(i);
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }

    /** The foci shelved around the table. */
    public static Set<ResourceLocation> shelvedFoci(Level level, BlockPos table) {
        Set<ResourceLocation> out = new HashSet<>();
        for (ItemStack s : books(level, table)) {
            ResearchData d = ResearchFocusItem.data(s);
            if (d != null) out.add(d.focus());
        }
        return out;
    }

    /** Highest researched level of each enchantment over all shelved foci. */
    public static Map<ResourceLocation, Integer> offered(Level level, BlockPos table) {
        Map<ResourceLocation, Integer> out = new HashMap<>();
        for (ItemStack s : books(level, table)) {
            ResearchData d = ResearchFocusItem.data(s);
            if (d == null || !d.hasResearch()) continue;
            out.merge(d.enchantment().get(), d.level(), Math::max);
        }
        return out;
    }

    /**
     * Blocks with an item inventory in the shelves' reach (up to 3 blocks out in x and z, from one below the table
     * to one above), nearest first: where research takes its resource from. Chiseled bookshelves don't count.
     */
    public static List<BlockPos> containers(Level level, BlockPos table) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(table.offset(-REACH, -1, -REACH), table.offset(REACH, 1, REACH))) {
            if (at.equals(table)) continue;
            BlockState st = level.getBlockState(at);
            if (st.isAir() || st.is(Blocks.CHISELED_BOOKSHELF) || !st.hasBlockEntity()) continue;
            out.add(at.immutable());
        }
        out.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(table)));
        return out;
    }

    /** The shelf holds at least one book. */
    public static boolean occupied(BlockState st) {
        for (BooleanProperty p : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) if (st.getValue(p)) return true;
        return false;
    }

    private Shelves() {}
}
