package dev.empengines.silentraids.siege;

import dev.empengines.silentraids.Config;
import dev.empengines.silentraids.SilentRaids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Raiders breaking blocks: which blocks they may break, and the shared progress on each (several raiders on one
 * block don't break it faster; the progress is kept for a while if they stop).
 */
public final class Breaching {
    public static final TagKey<Block> SIEGE_PROOF = TagKey.create(Registries.BLOCK, SilentRaids.id("siege_proof"));

    /** Plain Minecraft blocks only, without a block entity (so never containers, beds, signs...), and not siege-proof. */
    public static boolean breakable(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        if (state.hasBlockEntity() || state.is(SIEGE_PROOF)) return false;
        if (!"minecraft".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) return false;
        return state.getDestroySpeed(level, pos) >= 0;
    }

    private static final class Progress {
        int ticks;
        long lastTick;
        final int breakerId;
        Progress(int breakerId) { this.breakerId = breakerId; }
    }

    private static final Map<BlockPos, Progress> PROGRESS = new HashMap<>();

    /** One tick of a raider hitting the block. True when the block broke. */
    static boolean hit(ServerLevel level, BlockPos pos, Mob mob) {
        BlockState state = level.getBlockState(pos);
        if (!breakable(level, pos, state)) {
            clear(level, pos);
            return true;
        }
        long now = level.getGameTime();
        Progress p = PROGRESS.get(pos);
        if (p == null) {
            p = new Progress(-1000 - (pos.hashCode() & 0xFFFFF));
            PROGRESS.put(pos.immutable(), p);
            BloodMoon.breachStarted(mob, pos);
        }
        if (p.lastTick == now) return false;   // one raider's worth of progress per tick
        p.lastTick = now;
        p.ticks++;
        int total = Config.BREAK_SECONDS.get() * 20;
        if (p.ticks % 20 == 1) {
            level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.6f, 0.8f + level.random.nextFloat() * 0.3f);
        }
        level.destroyBlockProgress(p.breakerId, pos, Math.min(9, p.ticks * 10 / total));
        if (p.ticks >= total) {
            level.destroyBlockProgress(p.breakerId, pos, -1);
            PROGRESS.remove(pos);
            level.destroyBlock(pos, Config.DROP_BLOCKS.get(), mob);
            return true;
        }
        return false;
    }

    static void clear(ServerLevel level, BlockPos pos) {
        Progress p = PROGRESS.remove(pos);
        if (p != null) level.destroyBlockProgress(p.breakerId, pos, -1);
    }

    /** Forgets blocks nobody has hit for 30 seconds. */
    static void tidy(ServerLevel level) {
        long now = level.getGameTime();
        for (Iterator<Map.Entry<BlockPos, Progress>> it = PROGRESS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Progress> e = it.next();
            if (now - e.getValue().lastTick > 600) {
                level.destroyBlockProgress(e.getValue().breakerId, e.getKey(), -1);
                it.remove();
            }
        }
    }

    static void clearAll(ServerLevel level) {
        PROGRESS.forEach((pos, p) -> level.destroyBlockProgress(p.breakerId, pos, -1));
        PROGRESS.clear();
    }

    private Breaching() {}
}
