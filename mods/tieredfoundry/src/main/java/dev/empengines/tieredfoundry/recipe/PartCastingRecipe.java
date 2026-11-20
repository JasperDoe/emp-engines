package dev.empengines.tieredfoundry.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.empengines.tieredfoundry.Registry;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Silent Gear part casting: a cast shaped like {@code part} (made by pouring gold over any such part) on a casting
 * table, filled with {@code material_count} ingots' worth of a molten metal that is a valid {@code part_type} material
 * for {@code gear_type}, cools into that part made of that metal. Plain data, so it loads without Silent Gear; the
 * part itself is built by the Silent Gear compat code.
 */
public record PartCastingRecipe(ResourceLocation part, ResourceLocation partType, ResourceLocation gearType, int materialCount)
        implements Recipe<RecipeInput> {
    public static final MapCodec<PartCastingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("part").forGetter(PartCastingRecipe::part),
            ResourceLocation.CODEC.optionalFieldOf("part_type", ResourceLocation.fromNamespaceAndPath("silentgear", "main")).forGetter(PartCastingRecipe::partType),
            ResourceLocation.CODEC.fieldOf("gear_type").forGetter(PartCastingRecipe::gearType),
            Codec.INT.fieldOf("material_count").forGetter(PartCastingRecipe::materialCount)
    ).apply(i, PartCastingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PartCastingRecipe> STREAM = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, PartCastingRecipe::part,
            ResourceLocation.STREAM_CODEC, PartCastingRecipe::partType,
            ResourceLocation.STREAM_CODEC, PartCastingRecipe::gearType,
            ByteBufCodecs.VAR_INT, PartCastingRecipe::materialCount,
            PartCastingRecipe::new);
    public static final RecipeSerializer<PartCastingRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override public MapCodec<PartCastingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, PartCastingRecipe> streamCodec() { return STREAM; }
    };

    /** The cast pattern this recipe pours into. */
    public String pattern() { return "item:" + part; }

    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider reg) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int w, int h) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider reg) { return ItemStack.EMPTY; }
    @Override public RecipeSerializer<?> getSerializer() { return Registry.PART_CASTING_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return Registry.PART_CASTING.get(); }
    @Override public boolean isSpecial() { return true; }
}
