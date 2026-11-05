package dev.skybound.expresearch.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** A tab in the research screen. File: data/<ns>/expresearch/category/<name>.json */
public record ResearchCategory(String title, ResourceLocation icon, int order) {
    public static final Codec<ResearchCategory> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("title").forGetter(ResearchCategory::title),
            ResourceLocation.CODEC.fieldOf("icon").forGetter(ResearchCategory::icon),
            Codec.INT.optionalFieldOf("order", 0).forGetter(ResearchCategory::order)
    ).apply(i, ResearchCategory::new));
}
