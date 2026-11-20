package dev.empengines.tieredfoundry.compat.silentgear;

import dev.empengines.tieredfoundry.recipe.PartCastingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Silent Gear casting, safe to call without Silent Gear installed (no Silent Gear types here; {@link SgCasting} is only
 * loaded when it is).
 */
public final class SgHook {
    /** What a pour makes and how much fluid it takes. */
    public record Result(ItemStack stack, int amount) {}

    private static Boolean loaded;

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("silentgear");
        return loaded;
    }

    /** A Silent Gear part of the poured metal, or null if the metal isn't a material for that part. */
    public static Result part(Level level, PartCastingRecipe recipe, FluidStack fluid) {
        return loaded() ? SgCasting.part(level, recipe, fluid) : null;
    }

    /** Silent Gear gear coated with the poured metal (and repaired), or null. */
    public static Result coat(Level level, ItemStack gear, FluidStack fluid) {
        return loaded() ? SgCasting.coat(level, gear, fluid) : null;
    }

    private SgHook() {}
}
