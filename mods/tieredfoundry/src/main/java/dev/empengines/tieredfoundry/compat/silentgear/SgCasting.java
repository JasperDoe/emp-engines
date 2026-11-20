package dev.empengines.tieredfoundry.compat.silentgear;

import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.TieredFoundry;
import dev.empengines.tieredfoundry.recipe.MeltingRecipe;
import dev.empengines.tieredfoundry.recipe.PartCastingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.silentchaos512.gear.api.item.GearType;
import net.silentchaos512.gear.api.part.PartType;
import net.silentchaos512.gear.crafting.ingredient.PartMaterialIngredient;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.item.CompoundPartItem;
import net.silentchaos512.gear.setup.SgRegistries;
import net.silentchaos512.gear.setup.gear.PartTypes;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.GearHelper;

import java.util.Collections;

/**
 * Silent Gear on the casting table. The poured metal is matched to a Silent Gear material through the foundry's own
 * melting recipes: whatever melts into one ingot's worth (90 mB) of that fluid, an ingot or a gem, is the material.
 * <ul>
 * <li>Part casting: cast + material count x 90 mB makes the part of that material (any material valid for the
 * part, so iron-tier metals can be cast too; above iron, casting is the only way).</li>
 * <li>Coating: Silent Gear gear on the table + 90 mB coats it with that metal and repairs it; the gear is the cast.</li>
 * </ul>
 */
final class SgCasting {
    static final int UNIT = 90;

    /** The material this fluid stands for that fits this part, or null. */
    static MaterialInstance material(Level level, FluidStack fluid, PartType partType, GearType gearType) {
        if (fluid.isEmpty() || partType == null || gearType == null) return null;
        PartMaterialIngredient fits = PartMaterialIngredient.of(partType, gearType);
        for (RecipeHolder<MeltingRecipe> h : level.getRecipeManager().getAllRecipesFor(Registry.MELTING.get())) {
            MeltingRecipe r = h.value();
            if (r.ore() || r.result().getAmount() != UNIT || !FluidStack.isSameFluid(r.result(), fluid)) continue;
            for (ItemStack s : r.ingredient().getItems()) {
                if (!fits.test(s)) continue;
                MaterialInstance m = MaterialInstance.from(s);
                if (m != null) return m;
            }
        }
        return null;
    }

    static SgHook.Result part(Level level, PartCastingRecipe recipe, FluidStack fluid) {
        try {
            Item item = BuiltInRegistries.ITEM.get(recipe.part());
            if (!(item instanceof CompoundPartItem part)) return null;
            MaterialInstance m = material(level, fluid, SgRegistries.PART_TYPE.get(recipe.partType()), SgRegistries.GEAR_TYPE.get(recipe.gearType()));
            if (m == null) return null;
            ItemStack out = part.create(Collections.nCopies(recipe.materialCount(), m));
            return out.isEmpty() ? null : new SgHook.Result(out, recipe.materialCount() * UNIT);
        } catch (Throwable t) {
            TieredFoundry.LOGGER.debug("No {} from {}: {}", recipe.part(), fluid, t.toString());
            return null;
        }
    }

    static SgHook.Result coat(Level level, ItemStack gear, FluidStack fluid) {
        try {
            if (gear.isEmpty() || gear.getCount() != 1 || !GearHelper.isGear(gear)) return null;
            GearType type = GearHelper.getType(gear);
            if (type == null || !type.isGear() || GearData.getPartOfType(gear, PartTypes.MAIN.get()) == null) return null;
            PartType coating = PartTypes.COATING.get();
            CompoundPartItem coatingItem = coating.getCompoundPartItem(type).orElse(null);
            if (coatingItem == null) return null;
            MaterialInstance m = material(level, fluid, coating, type);
            if (m == null) return null;
            PartInstance part = PartInstance.from(coatingItem.create(m, 1));
            if (part == null) return null;
            ItemStack out = gear.copy();
            GearData.addOrReplacePart(out, part);
            out.setDamageValue(0);
            GearData.recalculateGearData(out, null);
            return new SgHook.Result(out, UNIT);
        } catch (Throwable t) {
            TieredFoundry.LOGGER.debug("Couldn't coat {} with {}: {}", gear, fluid, t.toString());
            return null;
        }
    }

    private SgCasting() {}
}
