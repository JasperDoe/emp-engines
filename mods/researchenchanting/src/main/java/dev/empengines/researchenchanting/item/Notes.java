package dev.empengines.researchenchanting.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** What a page of Research Notes covers: one enchantment, up to this level. */
public record Notes(ResourceLocation enchantment, int level) {
    public static final Codec<Notes> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("enchantment").forGetter(Notes::enchantment),
            Codec.INT.fieldOf("level").forGetter(Notes::level)
    ).apply(i, Notes::new));
}
