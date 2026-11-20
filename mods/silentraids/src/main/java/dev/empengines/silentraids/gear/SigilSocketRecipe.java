package dev.empengines.silentraids.gear;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.empengines.silentraids.ApothBridge;
import dev.empengines.silentraids.Registry;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Replaces Apotheosis' "add sockets" smithing recipe (data/apotheosis/recipe/sigil_add_sockets.json is overridden
 * by the pack to use this type): a Sigil of Socketing in the addition slot, the gear in the base slot, no template.
 * Adds one gem socket, once per item. The other sockets come from Silent Gear levels.
 */
public final class SigilSocketRecipe implements SmithingRecipe {
    private final Ingredient input;

    public SigilSocketRecipe(Ingredient input) {
        this.input = input;
    }

    public Ingredient input() {
        return input;
    }

    public static boolean canTakeSigil(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() != 1 || ApothBridge.socketsComponent() == null) return false;
        if (Boolean.TRUE.equals(stack.get(Registry.SIGIL_USED.get()))) return false;
        if (ApothBridge.sockets(stack) >= 16) return false;
        return GearLevels.isLevelledGear(stack) || (stack.getMaxStackSize() == 1 && stack.isDamageableItem());
    }

    @Override
    public boolean matches(SmithingRecipeInput in, Level level) {
        return in.template().isEmpty() && input.test(in.addition()) && canTakeSigil(in.base());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput in, HolderLookup.Provider registries) {
        ItemStack out = in.base().copy();
        if (out.isEmpty()) return ItemStack.EMPTY;
        ApothBridge.setSockets(out, ApothBridge.sockets(out) + 1);
        out.set(Registry.SIGIL_USED.get(), true);
        return out;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return canTakeSigil(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return input.test(stack);
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean isIncomplete() {
        return false;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Registry.SIGIL_SOCKET.get();
    }

    public static final class Serializer implements RecipeSerializer<SigilSocketRecipe> {
        public static final Serializer INSTANCE = new Serializer();
        private static final MapCodec<SigilSocketRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(SigilSocketRecipe::input)).apply(i, SigilSocketRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, SigilSocketRecipe> STREAM_CODEC =
                Ingredient.CONTENTS_STREAM_CODEC.map(SigilSocketRecipe::new, SigilSocketRecipe::input);

        @Override
        public MapCodec<SigilSocketRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SigilSocketRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
