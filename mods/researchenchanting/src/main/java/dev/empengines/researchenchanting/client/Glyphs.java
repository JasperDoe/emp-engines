package dev.empengines.researchenchanting.client;

import dev.empengines.researchenchanting.Registry;
import dev.empengines.researchenchanting.table.Shelves;
import dev.empengines.researchenchanting.table.TableAccess;
import dev.empengines.researchenchanting.table.TableData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The table's glyph streams, run from its client tick: vanilla's enchanting glyphs wriggle ({@link WormGlyph}) from
 * every chiseled shelf in reach that holds a book into whatever floats over the table. A trickle while you're
 * nearby; a flood while it enchants; and while research runs, glyphs also trail from the chests it pulls from.
 */
public final class Glyphs {
    /** Where glyphs aim: the floating item or the open book's pages. */
    public static Vec3 target(EnchantingTableBlockEntity be, TableData data, float partial) {
        BlockPos pos = be.getBlockPos();
        if (data.showsBook() || data.item().isEmpty()) return new Vec3(pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5);
        return new Vec3(pos.getX() + 0.5, pos.getY() + itemHeight(be, data, partial), pos.getZ() + 0.5);
    }

    /** Height of a floating item above the table's base: bobbing, and rising while it's enchanted. */
    public static double itemHeight(EnchantingTableBlockEntity be, TableData data, float partial) {
        float time = be.time + partial;
        float p = Math.max(0, data.enchantProgress(partial));
        return 1.15 + Mth.sin(time * 0.06F) * 0.06 * (1 - p) + rise(p) * 0.5;
    }

    /** Eases the item up over the first part of an enchantment and holds it there. */
    public static float rise(float p) {
        float t = Mth.clamp(p / 0.35F, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, EnchantingTableBlockEntity be) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(pos)) > 24 * 24) return;
        TableData data = ((TableAccess) be).researchenchanting$data();
        RandomSource r = level.random;
        float p = data.enchantProgress(0);
        boolean enchanting = p >= 0;
        boolean researching = data.researching();
        Vec3 to = target(be, data, 0);
        // the closer you stand, the livelier the shelves (vanilla only stirs within a few blocks)
        double near = mc.player.distanceToSqr(Vec3.atCenterOf(pos));
        int chance = enchanting ? 1 : researching ? 4 : near < 8 * 8 ? 7 : 18;
        int perShelf = enchanting ? 1 + (int) (p * 2) : 1;
        for (BlockPos shelf : Shelves.shelves(level, pos)) {
            if (!Shelves.occupied(level.getBlockState(shelf))) continue;
            for (int i = 0; i < perShelf; i++) {
                if (r.nextInt(chance) != 0) continue;
                Vec3 from = Vec3.atCenterOf(shelf).add((r.nextDouble() - 0.5) * 0.8, (r.nextDouble() - 0.5) * 0.8, (r.nextDouble() - 0.5) * 0.8);
                spawn(level, from, jitter(to, r, enchanting ? 0.12 : 0.2));
            }
        }
        if (researching) {
            for (BlockPos src : Flights.recentSources(pos, level.getGameTime())) {
                if (r.nextInt(2) != 0) continue;
                Vec3 from = Vec3.atCenterOf(src).add((r.nextDouble() - 0.5) * 0.6, 0.5, (r.nextDouble() - 0.5) * 0.6);
                spawn(level, from, jitter(to, r, 0.15));
            }
        }
    }

    /** A puff of glyphs bursting out of a point and curling back into it. */
    public static void burst(Level level, Vec3 at, int count) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 from = at.add((r.nextDouble() - 0.5) * 1.6, 0.3 + r.nextDouble() * 0.8, (r.nextDouble() - 0.5) * 1.6);
            spawn(level, from, at);
        }
    }

    static void spawn(Level level, Vec3 from, Vec3 to) {
        level.addAlwaysVisibleParticle(Registry.GLYPH.get(), true, from.x, from.y, from.z, to.x, to.y, to.z);
    }

    private static Vec3 jitter(Vec3 v, RandomSource r, double by) {
        return v.add((r.nextDouble() - 0.5) * by, (r.nextDouble() - 0.5) * by, (r.nextDouble() - 0.5) * by);
    }

    private Glyphs() {}
}
