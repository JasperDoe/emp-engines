package dev.empengines.engines.sky.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * A giant crystal shard bursting out of the ground at a slant, like an ice spike: a tapering cone of
 * {@code body} blocks (e.g. obsidian and amethyst) with a {@code core} through the middle, roots a few
 * blocks into the ground and a {@code tip} on top. With {@code hanging} it grows down from the underside
 * of the island instead (icicles, crystal stalactites, rock points).
 */
public class CrystalSpikeFeature extends Feature<CrystalSpikeFeature.Config> {
    public record Config(BlockStateProvider body, BlockStateProvider core, BlockStateProvider tip,
                         int minHeight, int maxHeight, int giantChance, boolean hanging) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("body").forGetter(Config::body),
                BlockStateProvider.CODEC.fieldOf("core").forGetter(Config::core),
                BlockStateProvider.CODEC.fieldOf("tip").forGetter(Config::tip),
                Codec.intRange(3, 128).fieldOf("min_height").forGetter(Config::minHeight),
                Codec.intRange(3, 128).fieldOf("max_height").forGetter(Config::maxHeight),
                Codec.intRange(0, 1000).fieldOf("giant_chance").forGetter(Config::giantChance),
                Codec.BOOL.optionalFieldOf("hanging", false).forGetter(Config::hanging)
        ).apply(i, Config::new));
    }

    public CrystalSpikeFeature() { super(Config.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        Config c = ctx.config();
        BlockPos origin = ctx.origin();
        while (origin.getY() > level.getMinBuildHeight() + 2 && level.isEmptyBlock(origin)) origin = origin.below();
        if (!level.getBlockState(origin).isSolid()) return false;
        int dir = 1;
        if (c.hanging()) {
            // Down through the island to its underside; only where it's thick enough to hang from
            int thickness = 0;
            while (origin.getY() > level.getMinBuildHeight() + 2 && !level.isEmptyBlock(origin.below()) && thickness < 200) {
                origin = origin.below();
                thickness++;
            }
            if (thickness < 6 || origin.getY() <= level.getMinBuildHeight() + 2) return false;
            dir = -1;
        }

        int height = c.minHeight() + rnd.nextInt(Math.max(1, c.maxHeight() - c.minHeight() + 1));
        if (c.giantChance() > 0 && rnd.nextInt(c.giantChance()) == 0) height = (int) (height * 1.8);
        double base = 1.6 + height / 7.0 + rnd.nextDouble();               // radius at the ground
        double angle = rnd.nextDouble() * Math.PI * 2, lean = (0.12 + rnd.nextDouble() * 0.3) * (c.hanging() ? 0.4 : 1);
        double lx = Math.cos(angle) * lean, lz = Math.sin(angle) * lean;   // how far it leans per block up

        // Features may only touch the chunk being decorated and the ring of chunks around it. Lean back
        // towards the middle if the spike would run out of that area, and skip any block that still would.
        ChunkPos centre = level instanceof WorldGenRegion region ? region.getCenter() : new ChunkPos(origin);
        int minX = centre.getMinBlockX() - 16, maxX = centre.getMaxBlockX() + 16;
        int minZ = centre.getMinBlockZ() - 16, maxZ = centre.getMaxBlockZ() + 16;
        double tipX = origin.getX() + lx * height, tipZ = origin.getZ() + lz * height;
        if (tipX - base < minX || tipX + base > maxX) lx = -lx;
        if (tipZ - base < minZ || tipZ + base > maxZ) lz = -lz;

        for (int y = -3; y <= height; y++) {
            double t = Math.max(0, y) / (double) height;
            double r = base * Math.pow(1 - t, 1.1) + (y < 0 ? 0.3 : 0);
            if (r < 0.35) break;
            double ox = lx * Math.max(0, y), oz = lz * Math.max(0, y);
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double ddx = dx - ox % 1, ddz = dz - oz % 1;
                    double d = Math.sqrt(ddx * ddx + ddz * ddz);
                    if (d > r) continue;
                    BlockPos p = origin.offset((int) Math.floor(ox) + dx, y * dir, (int) Math.floor(oz) + dz);
                    if (p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ) continue;
                    BlockState here = level.getBlockState(p);
                    if (y < 0 && here.isAir()) continue;   // roots only go into the island
                    BlockState state = d < r * 0.35 ? c.core().getState(rnd, p) : c.body().getState(rnd, p);
                    setBlock(level, p, state);
                }
            }
        }
        // The tip
        BlockPos top = origin.offset((int) Math.floor(lx * height), height * dir, (int) Math.floor(lz * height));
        boolean topInside = top.getX() >= minX && top.getX() <= maxX && top.getZ() >= minZ && top.getZ() <= maxZ;
        if (topInside && level.isEmptyBlock(top)) {
            BlockState tip = c.tip().getState(rnd, top);
            if (tip.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING))
                tip = tip.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, dir > 0 ? Direction.UP : Direction.DOWN);
            setBlock(level, top, tip);
        }
        return true;
    }
}
