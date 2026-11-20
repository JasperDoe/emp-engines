package dev.empengines.tieredfoundry.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.empengines.tieredfoundry.Config;
import dev.empengines.tieredfoundry.Registry;
import dev.empengines.tieredfoundry.TieredFoundry;
import dev.empengines.tieredfoundry.item.CastItem;
import dev.empengines.tieredfoundry.recipe.AlloyRecipe;
import dev.empengines.tieredfoundry.recipe.CastingRecipe;
import dev.empengines.tieredfoundry.recipe.MeltingRecipe;
import dev.empengines.tieredfoundry.recipe.PartCastingRecipe;
import dev.empengines.tieredfoundry.compat.silentgear.SgHook;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayList;
import java.util.List;

/** EMI pages: melting, alloying, table casting, basin casting, plus how casts are made. */
@EmiEntrypoint
public class FoundryEmiPlugin implements EmiPlugin {
    static EmiRecipeCategory MELTING, ALLOYING, TABLE, BASIN;

    @Override
    public void register(EmiRegistry registry) {
        EmiStack controller = EmiStack.of(Registry.CONTROLLER_ITEM.get());
        EmiStack table = EmiStack.of(Registry.CASTING_TABLE_ITEM.get());
        EmiStack basin = EmiStack.of(Registry.CASTING_BASIN_ITEM.get());
        MELTING = new EmiRecipeCategory(TieredFoundry.id("melting"), controller);
        ALLOYING = new EmiRecipeCategory(TieredFoundry.id("alloying"), EmiStack.of(Registry.AIR_DUCT_ITEM.get()));
        TABLE = new EmiRecipeCategory(TieredFoundry.id("casting_table"), table);
        BASIN = new EmiRecipeCategory(TieredFoundry.id("casting_basin"), basin);
        for (EmiRecipeCategory c : List.of(MELTING, ALLOYING, TABLE, BASIN)) registry.addCategory(c);
        registry.addWorkstation(MELTING, controller);
        registry.addWorkstation(ALLOYING, controller);
        registry.addWorkstation(ALLOYING, EmiStack.of(Registry.AIR_DUCT_ITEM.get()));
        registry.addWorkstation(TABLE, table);
        registry.addWorkstation(BASIN, basin);

        registry.addDeferredRecipes(out -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            var rm = level.getRecipeManager();
            for (RecipeHolder<MeltingRecipe> h : rm.getAllRecipesFor(Registry.MELTING.get())) out.accept(new Melt(h));
            for (RecipeHolder<AlloyRecipe> h : rm.getAllRecipesFor(Registry.ALLOYING.get())) out.accept(new Alloy(h));
            for (RecipeHolder<CastingRecipe> h : rm.getAllRecipesFor(Registry.CASTING.get())) out.accept(new Cast(h));
            // making casts: any ingot, nugget or plate under 180 mB of molten gold
            List<Fluid> gold = BuiltInRegistries.FLUID.getTag(dev.empengines.tieredfoundry.block.entity.CastingBlockEntity.MOLTEN_GOLD)
                    .map(t -> t.stream().map(holder -> holder.value()).toList()).orElse(List.of());
            for (String p : CastItem.creativePatterns()) {
                List<EmiStack> items = new ArrayList<>();
                if (p.startsWith("item:")) {
                    Item part = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(p.substring(5)));
                    if (part != Items.AIR) items.add(EmiStack.of(part));
                } else {
                    var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                            ResourceLocation.fromNamespaceAndPath("c", p + "s"));
                    for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                        items.add(EmiStack.of(holder.value()));
                        if (items.size() >= 24) break;
                    }
                }
                if (!items.isEmpty()) out.accept(new MakeCast(p, gold, items));
            }
            // Silent Gear parts, one page per part and metal
            for (RecipeHolder<PartCastingRecipe> h : rm.getAllRecipesFor(Registry.PART_CASTING.get())) {
                for (FluidStack f : unitFluids(rm)) {
                    SgHook.Result r = SgHook.part(level, h.value(), f);
                    if (r == null) continue;
                    String fid = BuiltInRegistries.FLUID.getKey(f.getFluid()).getPath();
                    out.accept(new PartPage(TieredFoundry.id("/part/" + h.value().part().getPath() + "/" + fid), h.value().pattern(),
                            EmiStack.of(f.getFluid(), r.amount()), r.amount(), EmiStack.of(r.stack())));
                }
            }
        });
        // coating Silent Gear gear on the table has its info page in Silent Apotheosis Raids

        List<EmiIngredient> sand = new ArrayList<>();
        for (String p : CastItem.PATTERNS) sand.add(EmiStack.of(CastItem.of(Registry.SAND_CAST.get(), p)));
        sand.add(EmiStack.of(Items.SAND));
        registry.addRecipe(new EmiInfoRecipe(sand, List.of(Component.translatable("emi.info.tieredfoundry.sand_cast")), TieredFoundry.id("/info/sand_cast")));
        registry.addRecipe(new EmiInfoRecipe(List.of(controller, EmiStack.of(Items.BRICKS), EmiStack.of(Registry.FIRE_BRICKS_ITEM.get()),
                EmiStack.of(Registry.SOLID_HEATER_ITEM.get()), EmiStack.of(Registry.LAVA_HEATER_ITEM.get()), EmiStack.of(Registry.AIR_DUCT_ITEM.get()),
                EmiStack.of(Registry.DRAIN_ITEM.get())), List.of(
                Component.translatable("emi.info.tieredfoundry.structure1"),
                Component.translatable("emi.info.tieredfoundry.structure2"),
                Component.translatable("emi.info.tieredfoundry.structure3"),
                Component.translatable("emi.info.tieredfoundry.structure4")), TieredFoundry.id("/info/structure")));
        registry.addRecipe(new EmiInfoRecipe(List.of(table, basin, EmiStack.of(Registry.TAP_ITEM.get())), List.of(
                Component.translatable("emi.info.tieredfoundry.casting1"),
                Component.translatable("emi.info.tieredfoundry.casting2")), TieredFoundry.id("/info/casting")));
    }

    /** Each fluid that one ingot's (or gem's) worth of something melts into: the metals a part can be cast from. */
    static List<FluidStack> unitFluids(net.minecraft.world.item.crafting.RecipeManager rm) {
        List<FluidStack> out = new ArrayList<>();
        for (RecipeHolder<MeltingRecipe> h : rm.getAllRecipesFor(Registry.MELTING.get())) {
            FluidStack f = h.value().result();
            if (h.value().ore() || f.getAmount() != 90) continue;
            if (out.stream().noneMatch(o -> FluidStack.isSameFluid(o, f))) out.add(f.copyWithAmount(90));
        }
        return out;
    }

    static EmiIngredient casts(String pattern) {
        return EmiIngredient.of(List.of(EmiStack.of(CastItem.of(Registry.GOLD_CAST.get(), pattern)),
                EmiStack.of(CastItem.of(Registry.SAND_CAST.get(), pattern))));
    }

    static EmiIngredient fluid(SizedFluidIngredient in) {
        List<EmiStack> list = new ArrayList<>();
        for (FluidStack f : in.getFluids()) list.add(EmiStack.of(f.getFluid(), in.amount()));
        return EmiIngredient.of(list);
    }

    static EmiStack fluid(FluidStack f) {
        return EmiStack.of(f.getFluid(), f.getAmount());
    }

    /**
     * Melting, laid out like a smeltery page: the heat source on the left (solid fuels that reach the temperature,
     * with a fan if they need one, or lava for fire brick foundries), the item, its temperature, and the molten metal.
     */
    static final class Melt extends BasicEmiRecipe {
        private final MeltingRecipe r;
        private final boolean lava, fan;
        private final EmiIngredient heat;

        Melt(RecipeHolder<MeltingRecipe> h) {
            super(MELTING, h.id(), 136, 54);
            r = h.value();
            inputs.add(EmiIngredient.of(r.ingredient()));
            outputs.add(fluid(r.result()));
            int t = r.temperature(), fanHeat = Config.safe(Config.FAN_MAX);
            lava = t > Config.safe(Config.CAP_T1);
            List<EmiStack> fuels = new ArrayList<>();
            boolean alone = false;
            for (var fuel : List.of(Items.COAL, Items.CHARCOAL, Items.BLAZE_ROD, Items.COAL_BLOCK)) {
                int ft = dev.empengines.tieredfoundry.block.entity.ControllerBlockEntity.fuelTemperature(new ItemStack(fuel));
                if (ft >= t) { fuels.add(EmiStack.of(fuel)); alone = true; }
            }
            if (!alone) {
                for (var fuel : List.of(Items.COAL, Items.CHARCOAL, Items.BLAZE_ROD, Items.COAL_BLOCK)) {
                    int ft = dev.empengines.tieredfoundry.block.entity.ControllerBlockEntity.fuelTemperature(new ItemStack(fuel));
                    if (ft + fanHeat >= t) fuels.add(EmiStack.of(fuel));
                }
            }
            fan = !lava && !alone;
            heat = lava ? EmiStack.of(net.minecraft.world.level.material.Fluids.LAVA, 1000) : EmiIngredient.of(fuels);
            if (!lava && !fuels.isEmpty()) catalysts.add(heat);
        }

        @Override public void addWidgets(WidgetHolder w) {
            if (lava) w.addTank(heat, 0, 0, 18, 54, 1000).catalyst(true);
            else w.addSlot(heat, 0, 18).catalyst(true);
            w.addSlot(inputs.get(0), 28, 10);
            w.addFillingArrow(56, 11, 2000);
            w.addTank(outputs.get(0), 92, 0, 44, 54, Math.max(1, r.result().getAmount())).recipeContext(this);
            w.addText(Component.translatable(fan ? "emi.tieredfoundry.temp_fan" : "emi.tieredfoundry.temp", r.temperature()),
                    20, 34, 0xFF404040, false);
            if (r.ore()) {
                w.addText(Component.translatable("emi.tieredfoundry.ore", String.format("%.1f", Config.safe(Config.YIELD_T1)),
                        String.format("%.1f", Config.safe(Config.YIELD_T2))), 20, 45, 0xFF707070, false);
            }
        }
    }

    static final class Alloy extends BasicEmiRecipe {
        private final AlloyRecipe r;
        Alloy(RecipeHolder<AlloyRecipe> h) {
            super(ALLOYING, h.id(), 150, 30);
            r = h.value();
            for (SizedFluidIngredient in : r.inputs()) inputs.add(fluid(in));
            outputs.add(fluid(r.result()));
        }
        @Override public void addWidgets(WidgetHolder w) {
            int x = 0;
            for (EmiIngredient in : inputs) { w.addSlot(in, x, 0); x += 18; }
            w.addFillingArrow(x + 4, 1, 2000);
            w.addSlot(outputs.get(0), x + 32, 0).recipeContext(this);
            w.addText(Component.translatable("emi.tieredfoundry.alloy_temp", r.temperature()), 0, 21, 0xFF404040, false);
        }
    }

    static final class Cast extends BasicEmiRecipe {
        private final CastingRecipe r;
        Cast(RecipeHolder<CastingRecipe> h) {
            super(h.value().basin() ? BASIN : TABLE, h.id(), 110, 22);
            r = h.value();
            inputs.add(fluid(r.fluid()));
            if (!r.basin() && r.pattern().isPresent()) catalysts.add(casts(r.pattern().get()));
            if (r.cast().isPresent()) inputs.add(EmiIngredient.of(r.cast().get()));
            outputs.add(EmiStack.of(r.result()));
        }
        @Override public void addWidgets(WidgetHolder w) {
            w.addSlot(inputs.get(0), 0, 2);
            if (!catalysts.isEmpty()) w.addSlot(catalysts.get(0), 22, 2).catalyst(true);
            else if (inputs.size() > 1) w.addSlot(inputs.get(1), 22, 2);
            w.addFillingArrow(46, 3, 1000 + r.fluid().amount() * 50 / 6);
            w.addSlot(outputs.get(0), 78, 2).recipeContext(this);
        }
    }

    /** A Silent Gear part cast from one metal. */
    static final class PartPage extends BasicEmiRecipe {
        private final int amount;
        PartPage(ResourceLocation id, String pattern, EmiStack fluid, int amount, EmiStack result) {
            super(TABLE, id, 110, 22);
            this.amount = amount;
            inputs.add(fluid);
            catalysts.add(casts(pattern));
            outputs.add(result);
        }
        @Override public void addWidgets(WidgetHolder w) {
            w.addSlot(inputs.get(0), 0, 2);
            w.addSlot(catalysts.get(0), 22, 2).catalyst(true);
            w.addFillingArrow(46, 3, 1000 + amount * 50 / 6);
            w.addSlot(outputs.get(0), 78, 2).recipeContext(this);
        }
    }

    static final class MakeCast extends BasicEmiRecipe {
        MakeCast(String pattern, List<Fluid> gold, List<EmiStack> items) {
            super(TABLE, TieredFoundry.id("/make_cast/" + pattern.replace(':', '/')), 110, 22);
            List<EmiStack> g = new ArrayList<>();
            for (Fluid f : gold) g.add(EmiStack.of(f, CastItem.GOLD_COST));
            inputs.add(EmiIngredient.of(g));
            inputs.add(EmiIngredient.of(items));
            outputs.add(EmiStack.of(CastItem.of(Registry.GOLD_CAST.get(), pattern)));
        }
        @Override public void addWidgets(WidgetHolder w) {
            w.addSlot(inputs.get(0), 0, 2);
            w.addSlot(inputs.get(1), 22, 2);
            w.addFillingArrow(46, 3, 1000 + CastItem.GOLD_COST * 50 / 6);
            w.addSlot(outputs.get(0), 78, 2).recipeContext(this);
        }
    }
}
