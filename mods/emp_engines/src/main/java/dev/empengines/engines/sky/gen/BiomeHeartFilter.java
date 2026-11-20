package dev.empengines.engines.sky.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.empengines.engines.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** Only places near the middle of a biome: the same biome {@code radius} blocks away in all four directions. */
public class BiomeHeartFilter extends PlacementFilter {
    public static final MapCodec<BiomeHeartFilter> CODEC = Codec.intRange(4, 512).fieldOf("radius")
            .xmap(BiomeHeartFilter::new, f -> f.radius);

    private final int radius;

    public BiomeHeartFilter(int radius) { this.radius = radius; }

    @Override
    protected boolean shouldPlace(PlacementContext ctx, RandomSource random, BlockPos pos) {
        // Ask the biome generator itself: the chunks out at the radius may not exist yet while this one is
        // being decorated, and reading them through the level crashes ("Requested chunk unavailable").
        BiomeSource source = ctx.generator().getBiomeSource();
        Climate.Sampler sampler = ctx.getLevel().getLevel().getChunkSource().randomState().sampler();
        Holder<Biome> here = biomeAt(source, sampler, pos.getX(), pos.getY(), pos.getZ());
        int[][] offsets = {{radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}};
        for (int[] o : offsets)
            if (!biomeAt(source, sampler, pos.getX() + o[0], pos.getY(), pos.getZ() + o[1]).is(here)) return false;
        return true;
    }

    private static Holder<Biome> biomeAt(BiomeSource source, Climate.Sampler sampler, int x, int y, int z) {
        return source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
    }

    @Override
    public PlacementModifierType<?> type() { return ModRegistry.BIOME_HEART.get(); }
}
