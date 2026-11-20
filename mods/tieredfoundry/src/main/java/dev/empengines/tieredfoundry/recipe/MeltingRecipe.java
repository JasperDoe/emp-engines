package dev.empengines.tieredfoundry.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.empengines.tieredfoundry.Registry;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * An item that melts in the foundry: {@code ingredient} melts to {@code result} once the foundry is at least
 * {@code temperature} degrees. With {@code ore} set (raw ores, ore blocks, crushed ores) the amount is multiplied by
 * the foundry's tier yield.
 */
public record MeltingRecipe(Ingredient ingredient, FluidStack result, int temperature, boolean ore) implements Recipe<SingleRecipeInput> {
    public static final MapCodec<MeltingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(MeltingRecipe::ingredient),
            FluidStack.CODEC.fieldOf("result").forGetter(MeltingRecipe::result),
            Codec.INT.fieldOf("temperature").forGetter(MeltingRecipe::temperature),
            Codec.BOOL.optionalFieldOf("ore", false).forGetter(MeltingRecipe::ore)
    ).apply(i, MeltingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeltingRecipe> STREAM = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, MeltingRecipe::ingredient,
            FluidStack.STREAM_CODEC, MeltingRecipe::result,
            ByteBufCodecs.VAR_INT, MeltingRecipe::temperature,
            ByteBufCodecs.BOOL, MeltingRecipe::ore,
            MeltingRecipe::new);
    public static final RecipeSerializer<MeltingRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override public MapCodec<MeltingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, MeltingRecipe> streamCodec() { return STREAM; }
    };

    /** The amount this melts to in a foundry of the given yield. */
    public int amount(double yield) {
        return ore ? (int) Math.round(result.getAmount() * yield) : result.getAmount();
    }

    @Override public boolean matches(SingleRecipeInput input, Level level) { return ingredient.test(input.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider reg) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int w, int h) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider reg) { return ItemStack.EMPTY; }
    @Override public RecipeSerializer<?> getSerializer() { return Registry.MELTING_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return Registry.MELTING.get(); }
    @Override public boolean isSpecial() { return true; }
}
