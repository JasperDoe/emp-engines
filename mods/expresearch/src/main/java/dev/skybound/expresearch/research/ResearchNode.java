package dev.skybound.expresearch.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One research. File: data/<ns>/expresearch/research/<name>.json
 *
 * x/y place the node in its category's tree (grid cells). Researching costs
 * {@code xp} experience points plus the sample items, and grants every Chapters
 * stage in {@code stages} to the whole FTB team.
 */
public record ResearchNode(ResourceLocation category, String title, String description, ResourceLocation icon,
                           int x, int y, List<ResourceLocation> parents, int xp, List<Sample> samples,
                           List<ResourceLocation> stages) {

    public record Sample(ResourceLocation item, int count) {
        public static final Codec<Sample> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(Sample::item),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Sample::count)
        ).apply(i, Sample::new));
    }

    public static final Codec<ResearchNode> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("category").forGetter(ResearchNode::category),
            Codec.STRING.fieldOf("title").forGetter(ResearchNode::title),
            Codec.STRING.optionalFieldOf("description", "").forGetter(ResearchNode::description),
            ResourceLocation.CODEC.fieldOf("icon").forGetter(ResearchNode::icon),
            Codec.INT.optionalFieldOf("x", 0).forGetter(ResearchNode::x),
            Codec.INT.optionalFieldOf("y", 0).forGetter(ResearchNode::y),
            ResourceLocation.CODEC.listOf().optionalFieldOf("parents", List.of()).forGetter(ResearchNode::parents),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(ResearchNode::xp),
            Sample.CODEC.listOf().optionalFieldOf("samples", List.of()).forGetter(ResearchNode::samples),
            ResourceLocation.CODEC.listOf().optionalFieldOf("stages", List.of()).forGetter(ResearchNode::stages)
    ).apply(i, ResearchNode::new));
}
