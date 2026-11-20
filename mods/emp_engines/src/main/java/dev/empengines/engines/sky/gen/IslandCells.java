package dev.empengines.engines.sky.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Where the Etheric Skies' islands are: one island per cell of a jittered grid, so they're spread evenly
 * (no clumps, no huge voids). Each island gets a size from its cell's random number, its edge is roughed
 * up by the {@code edge} noise, and neighbouring islands always keep at least {@code min_gap} of sky between
 * them (they're cut back along the line halfway between their centres).
 *
 * The value is 0 at an island's edge, rising to about 0.5 at its heart and negative outside, so the terrain
 * can use it like a noise with a threshold (mask = threshold + this).
 */
public record IslandCells(double spacing, double jitter, double minDiameter, double maxDiameter, double sizeCurve,
                          double minGap, double edgeAmplitude, double warp, DensityFunction edge) implements DensityFunction {
    public static final MapCodec<IslandCells> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.doubleRange(16, 4096).fieldOf("spacing").forGetter(IslandCells::spacing),
            Codec.doubleRange(0, 1).fieldOf("jitter").forGetter(IslandCells::jitter),
            Codec.doubleRange(1, 4096).fieldOf("min_diameter").forGetter(IslandCells::minDiameter),
            Codec.doubleRange(1, 4096).fieldOf("max_diameter").forGetter(IslandCells::maxDiameter),
            Codec.doubleRange(0.1, 10).fieldOf("size_curve").forGetter(IslandCells::sizeCurve),
            Codec.doubleRange(0, 512).fieldOf("min_gap").forGetter(IslandCells::minGap),
            Codec.doubleRange(0, 512).fieldOf("edge_amplitude").forGetter(IslandCells::edgeAmplitude),
            Codec.doubleRange(0, 512).optionalFieldOf("warp", 0.0).forGetter(IslandCells::warp),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("edge").forGetter(IslandCells::edge)
    ).apply(i, IslandCells::new));
    public static final KeyDispatchDataCodec<IslandCells> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext ctx) {
        // Bend the grid (so the lines between neighbouring islands curve instead of running straight)
        double x = ctx.blockX(), z = ctx.blockZ();
        if (warp > 0) {
            x += warp * edge.compute(new SinglePointContext(ctx.blockX() + 10007, 0, ctx.blockZ()));
            z += warp * edge.compute(new SinglePointContext(ctx.blockX(), 0, ctx.blockZ() + 10007));
        }
        long salt = salt();
        int cx = (int) Math.floor(x / spacing), cz = (int) Math.floor(z / spacing);
        double f1 = Double.MAX_VALUE, f2 = Double.MAX_VALUE, r1 = 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int gx = cx + dx, gz = cz + dz;
                long h = hash(gx, gz, salt);
                double px = (gx + 0.5 + (unit(h) - 0.5) * jitter) * spacing;
                double pz = (gz + 0.5 + (unit(h >>> 21) - 0.5) * jitter) * spacing;
                double d = Math.hypot(x - px, z - pz);
                if (d < f1) {
                    f2 = f1;
                    f1 = d;
                    double u = unit(h >>> 42);
                    r1 = (minDiameter + (maxDiameter - minDiameter) * Math.pow(u, sizeCurve)) / 2;
                } else if (d < f2) f2 = d;
            }
        }
        double e = edgeAmplitude * edge.compute(ctx);
        double inside = (r1 + e - f1) / r1;                              // 1 at the centre, 0 at the edge
        double apart = ((f2 - f1) / 2 - minGap / 2) / r1;                // 0 at the gap kept from the neighbour
        // The smaller of the two, with the corners rounded off
        double k = 0.12;
        double m = Math.min(inside, apart) - k * Math.log1p(Math.exp(-Math.abs(inside - apart) / k));
        return 0.5 * m;
    }

    /** A per-world number, so each world's islands are laid out differently (taken from the seeded edge noise). */
    private long salt() {
        try {
            return Double.doubleToLongBits(edge.compute(new SinglePointContext(1234567, 0, -7654321)));
        } catch (RuntimeException notReady) {
            return 0;
        }
    }

    private static long hash(int x, int z, long salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt;
        h ^= h >>> 33; h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33; h *= 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 33);
    }

    private static double unit(long h) { return (h & 0x1FFFFF) / (double) 0x200000; }

    @Override
    public void fillArray(double[] out, ContextProvider ctx) { ctx.fillAllDirectly(out, this); }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new IslandCells(spacing, jitter, minDiameter, maxDiameter, sizeCurve, minGap, edgeAmplitude, warp, edge.mapAll(visitor)));
    }

    // Generous bounds (the edge noise can go a little past 1)
    @Override public double minValue() { return -0.5 * (spacing * 3 + 2 * edgeAmplitude + 2 * warp + minGap) / Math.max(1, minDiameter / 2) - 1; }
    @Override public double maxValue() { return 0.5 * (1 + 2 * edgeAmplitude / Math.max(1, minDiameter / 2)); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
