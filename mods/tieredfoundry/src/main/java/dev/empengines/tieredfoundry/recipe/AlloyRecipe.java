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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;

/**
 * Molten fluids that combine inside the foundry, in the ratio given, once it is at least {@code temperature} and has a
 * running fan (tier 1.5 and up). One reaction uses each input's amount and makes {@code result}.
 */
public record AlloyRecipe(List<SizedFluidIngredient> inputs, FluidStack result, int temperature) implements Recipe<RecipeInput> {
    public static final MapCodec<AlloyRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SizedFluidIngredient.FLAT_CODEC.listOf(2, 8).fieldOf("inputs").forGetter(AlloyRecipe::inputs),
            FluidStack.CODEC.fieldOf("result").forGetter(AlloyRecipe::result),
            Codec.INT.fieldOf("temperature").forGetter(AlloyRecipe::temperature)
    ).apply(i, AlloyRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AlloyRecipe> STREAM = StreamCodec.composite(
            SizedFluidIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), AlloyRecipe::inputs,
            FluidStack.STREAM_CODEC, AlloyRecipe::result,
            ByteBufCodecs.VAR_INT, AlloyRecipe::temperature,
            AlloyRecipe::new);
    public static final RecipeSerializer<AlloyRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override public MapCodec<AlloyRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, AlloyRecipe> streamCodec() { return STREAM; }
    };

    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider reg) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int w, int h) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider reg) { return ItemStack.EMPTY; }
    @Override public RecipeSerializer<?> getSerializer() { return Registry.ALLOYING_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return Registry.ALLOYING.get(); }
    @Override public boolean isSpecial() { return true; }
}
