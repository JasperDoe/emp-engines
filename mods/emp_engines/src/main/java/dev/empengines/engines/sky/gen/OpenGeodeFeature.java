package dev.empengines.engines.sky.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * A geode broken open at the surface: a geode ({@code geode}, e.g. minecraft:amethyst_geode) set with its
 * middle {@code depth} blocks under the ground, then everything of it above the old ground level taken
 * away, so it's an open bowl lined with crystals.
 */
public class OpenGeodeFeature extends Feature<OpenGeodeFeature.Config> {
    public record Config(Holder<ConfiguredFeature<?, ?>> geode, int depth, int radius) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                ConfiguredFeature.CODEC.fieldOf("geode").forGetter(Config::geode),
                Codec.intRange(0, 16).fieldOf("depth").forGetter(Config::depth),
                Codec.intRange(4, 16).fieldOf("radius").forGetter(Config::radius)
        ).apply(i, Config::new));
    }

    public OpenGeodeFeature() { super(Config.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        Config c = ctx.config();
        BlockPos origin = ctx.origin();
        int r = c.radius();
        // The ground before the geode goes in
        int[][] ground = new int[r * 2 + 1][r * 2 + 1];
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                ground[dx + r][dz + r] = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX() + dx, origin.getZ() + dz);
        int surface = ground[r][r];
        if (surface <= level.getMinBuildHeight() + 4) return false;

        BlockPos centre = new BlockPos(origin.getX(), surface - c.depth(), origin.getZ());
        if (!c.geode().value().place(level, ctx.chunkGenerator(), ctx.random(), centre)) return false;

        // Open it: geode blocks above the old ground (and the top layer at it) are taken away
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int g = ground[dx + r][dz + r];
                for (int y = g - 1; y <= surface + r + 2; y++) {
                    BlockPos p = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                    if (isGeode(level.getBlockState(p))) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        return true;
    }

    private static boolean isGeode(BlockState s) {
        return s.is(Blocks.CALCITE) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.AMETHYST_BLOCK) || s.is(Blocks.BUDDING_AMETHYST)
                || s.is(Blocks.AMETHYST_CLUSTER) || s.is(Blocks.LARGE_AMETHYST_BUD) || s.is(Blocks.MEDIUM_AMETHYST_BUD)
                || s.is(Blocks.SMALL_AMETHYST_BUD);
    }
}
