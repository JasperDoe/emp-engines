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
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.Optional;

/**
 * Pouring into a casting table (with a cast of the given {@code pattern}) or a casting basin ({@code basin}, no cast):
 * {@code fluid} fills it and cools into {@code result}. With {@code cast} instead of a pattern, that item itself sits
 * on the table and is used up (e.g. the Obsidian Key fragment).
 */
public record CastingRecipe(boolean basin, Optional<String> pattern, Optional<Ingredient> cast, SizedFluidIngredient fluid, ItemStack result) implements Recipe<RecipeInput> {
    public static final MapCodec<CastingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.xmap("basin"::equals, b -> b ? "basin" : "table").fieldOf("block").forGetter(CastingRecipe::basin),
            Codec.STRING.optionalFieldOf("pattern").forGetter(CastingRecipe::pattern),
            Ingredient.CODEC_NONEMPTY.optionalFieldOf("cast").forGetter(CastingRecipe::cast),
            SizedFluidIngredient.FLAT_CODEC.fieldOf("fluid").forGetter(CastingRecipe::fluid),
            ItemStack.CODEC.fieldOf("result").forGetter(CastingRecipe::result)
    ).apply(i, CastingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CastingRecipe> STREAM = StreamCodec.composite(
            ByteBufCodecs.BOOL, CastingRecipe::basin,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), CastingRecipe::pattern,
            ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC), CastingRecipe::cast,
            SizedFluidIngredient.STREAM_CODEC, CastingRecipe::fluid,
            ItemStack.STREAM_CODEC, CastingRecipe::result,
            CastingRecipe::new);
    public static final RecipeSerializer<CastingRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override public MapCodec<CastingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, CastingRecipe> streamCodec() { return STREAM; }
    };

    /** Whether this recipe takes the given fluid in a block of this kind holding a cast of this pattern. */
    public boolean accepts(boolean inBasin, String castPattern, FluidStack in) {
        if (basin != inBasin || in.isEmpty() || cast.isPresent()) return false;
        if (!basin && (castPattern == null || !pattern.map(castPattern::equals).orElse(false))) return false;
        return fluid.ingredient().test(in);
    }

    /** Whether this recipe pours over this exact item on a table, using it up (an item-shaped one-off "cast"). */
    public boolean acceptsItem(ItemStack onTable, FluidStack in) {
        return !basin && cast.isPresent() && cast.get().test(onTable) && !in.isEmpty() && fluid.ingredient().test(in);
    }

    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider reg) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int w, int h) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider reg) { return result; }
    @Override public RecipeSerializer<?> getSerializer() { return Registry.CASTING_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return Registry.CASTING.get(); }
    @Override public boolean isSpecial() { return true; }
}
