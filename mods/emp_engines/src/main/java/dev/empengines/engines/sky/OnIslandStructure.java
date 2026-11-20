package dev.empengines.engines.sky;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.empengines.engines.EmpyreanEngines;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * A vanilla structure, set down on a floating island of the Etheric Skies whatever the biome (a desert
 * temple in a cherry forest, a village on a shard of amethyst...), which sells how unstable the place is.
 *
 * It only goes where the island is big enough: the ground must be there all round the chunk's middle
 * out to {@code min_radius} blocks, and not too uneven. Then the wrapped structure ({@code structure})
 * picks its own spot and pieces as it would anywhere. Its own biome list is ignored; this one's applies.
 */
public class OnIslandStructure extends Structure {
    public static final MapCodec<OnIslandStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Structure.CODEC.fieldOf("structure").forGetter(s -> s.inner),
            Codec.INT.optionalFieldOf("min_radius", 20).forGetter(s -> s.minRadius),
            Codec.INT.optionalFieldOf("max_slope", 16).forGetter(s -> s.maxSlope)
    ).apply(i, OnIslandStructure::new));

    private static final int SAMPLES = 12;
    private static Method findGenerationPoint;

    private final Holder<Structure> inner;
    private final int minRadius, maxSlope;

    public OnIslandStructure(StructureSettings settings, Holder<Structure> inner, int minRadius, int maxSlope) {
        super(settings);
        this.inner = inner;
        this.minRadius = minRadius;
        this.maxSlope = maxSlope;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        if (!bigEnough(ctx)) return Optional.empty();
        try {
            @SuppressWarnings("unchecked")
            Optional<GenerationStub> stub = (Optional<GenerationStub>) method().invoke(inner.value(), ctx);
            return stub;
        } catch (ReflectiveOperationException | RuntimeException e) {
            EmpyreanEngines.LOGGER.warn("Etheric Skies: {} couldn't place {}: {}", this, inner, e.toString());
            return Optional.empty();
        }
    }

    /** Island ground all round the chunk's middle (two rings of samples), and not too uneven. */
    private boolean bigEnough(GenerationContext ctx) {
        ChunkPos c = ctx.chunkPos();
        int x = c.getMiddleBlockX(), z = c.getMiddleBlockZ();
        int floor = ctx.heightAccessor().getMinBuildHeight() + 8;
        int centre = height(ctx, x, z);
        if (centre <= floor) return false;
        int lo = centre, hi = centre;
        for (double ring : new double[]{0.5, 1.0}) {
            for (int s = 0; s < SAMPLES; s++) {
                double a = 2 * Math.PI * (s + ring) / SAMPLES;
                int h = height(ctx, x + (int) Math.round(Math.cos(a) * minRadius * ring), z + (int) Math.round(Math.sin(a) * minRadius * ring));
                if (h <= floor) return false;
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
        }
        return hi - lo <= maxSlope;
    }

    private static int height(GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
    }

    private static Method method() throws NoSuchMethodException {
        if (findGenerationPoint == null) {
            Method m = Structure.class.getDeclaredMethod("findGenerationPoint", GenerationContext.class);
            m.setAccessible(true);
            findGenerationPoint = m;
        }
        return findGenerationPoint;
    }

    @Override
    public StructureType<?> type() { return dev.empengines.engines.ModRegistry.ON_ISLAND.get(); }
}
