package dev.skybound.createskybound.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.skybound.createskybound.ModRegistry;
import dev.skybound.createskybound.sky.SkyRuns;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.List;

/**
 * Adds Resonant Antennas to chest loot:
 * T1 in any chest, T2 in dungeon-type chests far from spawn, T3 in chests inside the Chaotic Skies.
 */
public class AntennaLootModifier extends LootModifier {
    public static final MapCodec<AntennaLootModifier> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).and(i.group(
            Codec.FLOAT.optionalFieldOf("t1_chance", 0.04f).forGetter(m -> m.t1Chance),
            Codec.FLOAT.optionalFieldOf("t2_chance", 0.35f).forGetter(m -> m.t2Chance),
            Codec.INT.optionalFieldOf("t2_min_distance", 3000).forGetter(m -> m.t2MinDistance),
            Codec.STRING.listOf().optionalFieldOf("t2_keywords", List.of("dungeon", "castle", "fort", "keep", "mansion", "stronghold", "ancient_city", "bastion", "airship", "trial")).forGetter(m -> m.t2Keywords),
            Codec.FLOAT.optionalFieldOf("t3_chance", 0.08f).forGetter(m -> m.t3Chance)
    )).apply(i, AntennaLootModifier::new));

    private final float t1Chance, t2Chance, t3Chance;
    private final int t2MinDistance;
    private final List<String> t2Keywords;

    public AntennaLootModifier(LootItemCondition[] conditions, float t1Chance, float t2Chance, int t2MinDistance, List<String> t2Keywords, float t3Chance) {
        super(conditions);
        this.t1Chance = t1Chance;
        this.t2Chance = t2Chance;
        this.t2MinDistance = t2MinDistance;
        this.t2Keywords = t2Keywords;
        this.t3Chance = t3Chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        String table = ctx.getQueriedLootTableId().getPath();
        if (!table.startsWith("chests/") && !table.contains("/chests/") && !table.contains("chest")) return loot;
        var level = ctx.getLevel();
        Vec3 origin = ctx.getParamOrNull(LootContextParams.ORIGIN);
        var rand = ctx.getRandom();

        if (level.dimension().equals(SkyRuns.CHAOTIC_SKIES)) {
            if (rand.nextFloat() < t3Chance) loot.add(new ItemStack(ModRegistry.ANTENNA_T3.get()));
            return loot;
        }
        boolean dungeon = t2Keywords.stream().anyMatch(table::contains);
        boolean far = origin != null && Math.max(Math.abs(origin.x), Math.abs(origin.z)) >= t2MinDistance;
        if (dungeon && far && rand.nextFloat() < t2Chance) loot.add(new ItemStack(ModRegistry.ANTENNA_T2.get()));
        else if (rand.nextFloat() < t1Chance) loot.add(new ItemStack(ModRegistry.ANTENNA_T1.get()));
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
