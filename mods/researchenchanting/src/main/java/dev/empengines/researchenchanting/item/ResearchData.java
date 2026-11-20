package dev.empengines.researchenchanting.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * What a Research Focus knows: its focus (which tree it can travel), the one enchantment it holds and at what
 * level, and the research running now (enchantment, level and items paid towards it). A book holds a single
 * enchantment: researching a different line replaces the one it had. Lives on the book, so it moves with it.
 */
public record ResearchData(ResourceLocation focus, Optional<ResourceLocation> enchantment, int level, Optional<Active> active) {

    public record Active(ResourceLocation enchantment, int level, int paid) {
        public static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("enchantment").forGetter(Active::enchantment),
                Codec.INT.fieldOf("level").forGetter(Active::level),
                Codec.INT.fieldOf("paid").forGetter(Active::paid)
        ).apply(i, Active::new));
    }

    public static final Codec<ResearchData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("focus").forGetter(ResearchData::focus),
            ResourceLocation.CODEC.optionalFieldOf("enchantment").forGetter(ResearchData::enchantment),
            Codec.INT.optionalFieldOf("level", 0).forGetter(ResearchData::level),
            Active.CODEC.optionalFieldOf("active").forGetter(ResearchData::active)
    ).apply(i, ResearchData::new));

    public static ResearchData fresh(ResourceLocation focus) {
        return new ResearchData(focus, Optional.empty(), 0, Optional.empty());
    }

    /** The level this book holds of that enchantment (0 unless it's the book's enchantment). */
    public int level(ResourceLocation e) {
        return enchantment.map(x -> x.equals(e) ? level : 0).orElse(0);
    }

    public boolean holds(ResourceLocation e) {
        return enchantment.map(x -> x.equals(e)).orElse(false);
    }

    public boolean hasResearch() {
        return enchantment.isPresent() && level > 0;
    }

    public ResearchData withActive(Active a) {
        return new ResearchData(focus, enchantment, level, Optional.ofNullable(a));
    }

    /** The book now holds this enchantment at this level (replacing whatever it held) and nothing is running. */
    public ResearchData complete(ResourceLocation e, int lvl) {
        return new ResearchData(focus, Optional.of(e), lvl, Optional.empty());
    }
}
