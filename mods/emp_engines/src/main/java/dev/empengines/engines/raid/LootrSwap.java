package dev.empengines.engines.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Loot on raider ships is Lootr's, so each player gets their own: loot chests and barrels in a ship's
 * design become Lootr ones, and the Loot Hold marker becomes a Lootr barrel. Falls back to plain vanilla
 * containers if Lootr isn't installed.
 */
final class LootrSwap {
    static final ResourceKey<LootTable> HOLD_LOOT = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/pillager_outpost"));
    private static final ResourceLocation BARREL = ResourceLocation.fromNamespaceAndPath("lootr", "lootr_barrel");
    private static final ResourceLocation CHEST = ResourceLocation.fromNamespaceAndPath("lootr", "lootr_chest");

    /** If the block at pos is a vanilla chest or barrel with a loot table, makes it the Lootr one (same table and facing). */
    static void convert(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        boolean barrel = s.is(Blocks.BARREL), chest = s.is(Blocks.CHEST) || s.is(Blocks.TRAPPED_CHEST);
        if (!barrel && !chest) return;
        if (!(level.getBlockEntity(pos) instanceof RandomizableContainer c) || c.getLootTable() == null) return;
        ResourceKey<LootTable> table = c.getLootTable();
        long seed = c.getLootTableSeed();
        Block lootr = lootr(barrel ? BARREL : CHEST);
        if (lootr == null) return;
        level.setBlock(pos, copyProps(s, lootr.defaultBlockState()), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof RandomizableContainer n) n.setLootTable(table, seed);
    }

    /** Puts a Lootr barrel (or a plain one without Lootr) with the given loot table at pos. */
    static void place(ServerLevel level, BlockPos pos, ResourceKey<LootTable> table) {
        Block lootr = lootr(BARREL);
        level.setBlock(pos, (lootr == null ? Blocks.BARREL : lootr).defaultBlockState(), Block.UPDATE_CLIENTS);
        RandomizableContainer.setBlockEntityLootTable(level, level.random, pos, table);
    }

    private static Block lootr(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id) : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyProps(BlockState from, BlockState to) {
        for (Property p : from.getProperties())
            if (to.hasProperty(p)) to = to.setValue(p, from.getValue(p));
        return to;
    }

    private LootrSwap() {}
}
