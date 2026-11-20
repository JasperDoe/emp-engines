package dev.empengines.researchenchanting.focus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * One research focus (data/<ns>/research_enchanting/focus/<name>.json): a themed tree of enchantment lines.
 *
 * @param gear   sword, bow, crossbow, trident, armour or tools: the colour of the book's band, and its tooltip
 * @param icon   item drawn for the focus
 * @param stage  a Chapters stage the binding player's team needs before this focus can be drawn
 * @param lines  the enchantments, each researched level I up to its max
 */
public record Focus(String gear, Item icon, Optional<ResourceLocation> stage, List<Line> lines) {
    /** Gear groups in the order of the book textures' bands. */
    public static final List<String> GEARS = List.of("sword", "bow", "armour", "tools", "trident", "crossbow");

    public int gearIndex() {
        return Math.max(0, GEARS.indexOf(gear));
    }


    /**
     * One enchantment in a focus. Level I opens when {@code requires} is researched (or at once for a root);
     * each later level opens when the one before it is done.
     *
     * @param x,y grid position of level I in the tree; later levels go to the right
     */
    public record Line(ResourceLocation enchantment, int max, Item resource, Optional<Requirement> requires, int x, int y) {
        public static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("enchantment").forGetter(Line::enchantment),
                Codec.intRange(1, 10).fieldOf("max_level").forGetter(Line::max),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("resource").forGetter(Line::resource),
                Requirement.CODEC.optionalFieldOf("requires").forGetter(Line::requires),
                Codec.INT.fieldOf("x").forGetter(Line::x),
                Codec.INT.fieldOf("y").forGetter(Line::y)
        ).apply(i, Line::new));
    }

    public record Requirement(ResourceLocation enchantment, int level) {
        public static final Codec<Requirement> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("enchantment").forGetter(Requirement::enchantment),
                Codec.intRange(1, 10).fieldOf("level").forGetter(Requirement::level)
        ).apply(i, Requirement::new));
    }

    public static final Codec<Focus> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("gear").forGetter(Focus::gear),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("icon").forGetter(Focus::icon),
            ResourceLocation.CODEC.optionalFieldOf("stage").forGetter(Focus::stage),
            Line.CODEC.listOf().fieldOf("lines").forGetter(Focus::lines)
    ).apply(i, Focus::new));

    public Optional<Line> line(ResourceLocation enchantment) {
        return lines.stream().filter(l -> l.enchantment().equals(enchantment)).findFirst();
    }

    public int indexOf(ResourceLocation enchantment) {
        for (int n = 0; n < lines.size(); n++) if (lines.get(n).enchantment().equals(enchantment)) return n;
        return -1;
    }

    public static net.minecraft.network.chat.MutableComponent name(ResourceLocation id) {
        return Component.translatable("focus." + id.getNamespace() + "." + id.getPath());
    }

    public static net.minecraft.network.chat.MutableComponent gearName(String gear) {
        return Component.translatable("focus.researchenchanting.gear." + gear);
    }
}
