package dev.empengines.silentraids.compat.emi;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.empengines.silentraids.SilentRaids;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.item.GearType;
import net.silentchaos512.gear.api.part.PartType;
import net.silentchaos512.gear.crafting.ingredient.PartMaterialIngredient;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.item.CompoundPartItem;
import net.silentchaos512.gear.setup.gear.PartTypes;
import net.silentchaos512.gear.util.GearData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Silent Gear in EMI, one material at a time.
 *
 * EMI normally treats every Silent Gear sword as the same item, so a diamond sword listed every sword recipe,
 * including the iron-tier material recipes it can't be made with. Here gear and parts are told apart by their
 * main material, and every gear recipe is shown again for each material it accepts, with the real result:
 * - parts: blueprint + materials (iron tier and below), or cast at the foundry (any castable material);
 * - gear: head (or plates) + rod and the other parts, or the direct and quick recipes for iron tier and below;
 * - upgrades: gear + a rod, tip, grip, binding... in the crafting grid swaps that part, and gear + a head or plates
 *   of a material above iron swaps the main part (iron helmet + diamond plates = diamond helmet), one page each;
 * - coatings: gear on a casting table + molten metal (info page; the casting is Tiered Foundry's).
 * So a diamond sword shows diamond blade + rod, and the diamond blade shows its casting.
 */
final class GearRecipesEmi {
    private GearRecipesEmi() {}

    /** Part types that can be swapped onto finished gear in the crafting grid. */
    private static final List<PartType> SWAPPABLE = new ArrayList<>();

    static void register(EmiRegistry registry) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof GearItem || item instanceof CompoundPartItem) {
                registry.setDefaultComparison(EmiStack.of(item), Comparison.compareData(GearRecipesEmi::materialKey));
            }
        }
        registry.addDeferredRecipes(GearRecipesEmi::addRecipes);
    }

    /** The main material's id, or "" for a stack without one. */
    private static String materialKey(EmiStack emi) {
        ItemStack stack = emi.getItemStack();
        if (stack.isEmpty()) return "";
        MaterialInstance mat = null;
        if (stack.getItem() instanceof CompoundPartItem) {
            mat = CompoundPartItem.getPrimaryMaterial(stack);
        } else if (stack.getItem() instanceof GearItem) {
            PartInstance main = GearData.getPartOfType(stack, PartTypes.MAIN.get());
            if (main != null) mat = main.getPrimaryMaterial();
        }
        return mat == null || mat.getId() == null ? "" : mat.getId().toString();
    }

    private static void addRecipes(Consumer<EmiRecipe> out) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        HolderLookup.Provider access = level.registryAccess();
        SWAPPABLE.clear();
        SWAPPABLE.addAll(List.of(PartTypes.ROD.get(), PartTypes.TIP.get(), PartTypes.GRIP.get(), PartTypes.BINDING.get(),
                PartTypes.CORD.get(), PartTypes.FLETCHING.get(), PartTypes.LINING.get(), PartTypes.SETTING.get()));
        int count = 0;
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            try {
                if (recipe instanceof CraftingRecipe crafting) count += crafting(holder.id(), crafting, access, out);
            } catch (Throwable t) {
                SilentRaids.LOGGER.debug("Skipped {} in EMI: {}", holder.id(), t.toString());
            }
        }
        count += swaps(access, out);
        count += mainSwaps(out);
        SilentRaids.LOGGER.info("Added {} per-material Silent Gear recipes to EMI", count);
    }

    // ------------------------------------------------------------------ crafting grid recipes

    /**
     * One copy of a Silent Gear crafting recipe per material: material slots get that material, the main-part slot
     * (for head + rod recipes) a part made of it, other slots stay as they are. Returns how many were added.
     */
    private static int crafting(ResourceLocation id, CraftingRecipe recipe, HolderLookup.Provider access, Consumer<EmiRecipe> out) {
        ItemStack example = recipe.getResultItem(access);
        if (example.isEmpty()) return 0;
        Item result = example.getItem();
        if (!(result instanceof GearItem) && !(result instanceof CompoundPartItem)) return 0;
        List<Ingredient> ings = recipe.getIngredients();
        if (ings.isEmpty()) return 0;

        // Which slots take the material: material ingredients, or a main part
        List<Integer> materialSlots = new ArrayList<>();
        int mainSlot = -1;
        CompoundPartItem mainItem = null;
        for (int i = 0; i < ings.size(); i++) {
            Ingredient ing = ings.get(i);
            if (ing.getCustomIngredient() instanceof PartMaterialIngredient pmi && pmi.getPartType() == PartTypes.MAIN.get()) {
                materialSlots.add(i);
            } else if (mainSlot < 0 && !ing.isEmpty() && !ing.isCustom()) {
                for (ItemStack s : ing.getItems()) {
                    if (s.getItem() instanceof CompoundPartItem cpi && cpi.getPartType() == PartTypes.MAIN.get()) {
                        mainSlot = i;
                        mainItem = cpi;
                        break;
                    }
                }
            }
        }

        Map<ResourceLocation, ItemStack> materials;
        if (!materialSlots.isEmpty()) {
            Ingredient first = ings.get(materialSlots.get(0));
            materials = byMaterial(first.getItems());
            materials.values().removeIf(stack -> materialSlots.stream().anyMatch(i -> !ings.get(i).test(stack)));
        } else if (mainItem != null) {
            materials = byMaterial(PartMaterialIngredient.of(PartTypes.MAIN.get(), mainItem.getGearType()).getItems().toArray(ItemStack[]::new));
        } else {
            return 0;
        }

        boolean shaped = recipe instanceof ShapedRecipe;
        int w = shaped ? ((ShapedRecipe) recipe).getWidth() : 0;
        int added = 0;
        for (Map.Entry<ResourceLocation, ItemStack> e : materials.entrySet()) {
            ItemStack matStack = e.getValue().copyWithCount(1);
            MaterialInstance mat = MaterialInstance.from(matStack);
            if (mat == null) continue;
            ItemStack mainPart = mainItem != null ? mainItem.create(mat) : ItemStack.EMPTY;

            List<ItemStack> grid = new ArrayList<>();
            List<EmiIngredient> shown = new ArrayList<>();
            for (int i = 0; i < ings.size(); i++) {
                Ingredient ing = ings.get(i);
                ItemStack put;
                EmiIngredient show;
                if (materialSlots.contains(i)) {
                    put = matStack;
                    show = EmiStack.of(matStack);
                } else if (i == mainSlot) {
                    put = mainPart;
                    show = EmiStack.of(mainPart);
                } else if (ing.isEmpty()) {
                    put = ItemStack.EMPTY;
                    show = EmiStack.EMPTY;
                } else {
                    ItemStack[] items = ing.getItems();
                    put = items.length > 0 ? items[0].copyWithCount(1) : ItemStack.EMPTY;
                    show = EmiIngredient.of(ing);
                }
                grid.add(put);
                shown.add(show);
            }
            ItemStack output;
            if (shaped) {
                output = recipe.assemble(CraftingInput.of(w, ((ShapedRecipe) recipe).getHeight(), grid), access);
            } else {
                output = recipe.assemble(CraftingInput.of(grid.size(), 1, grid), access);
            }
            if (output.isEmpty() || output.getItem() != result) continue;

            List<EmiIngredient> inputs = shaped ? toThreeWide(shown, w) : shown;
            out.accept(new EmiCraftingRecipe(inputs, EmiStack.of(output), synthetic(id, e.getKey()), !shaped));
            added++;
        }
        return added;
    }

    /** EMI lays crafting inputs out three to a row. */
    private static List<EmiIngredient> toThreeWide(List<EmiIngredient> shown, int width) {
        List<EmiIngredient> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) grid.add(EmiStack.EMPTY);
        for (int i = 0; i < shown.size(); i++) grid.set((i / width) * 3 + i % width, shown.get(i));
        return grid;
    }

    // ------------------------------------------------------------------ part swaps

    /**
     * Gear + a part swaps that part (Silent Gear's swap recipe, which recipe viewers don't show). One page per gear
     * item and part type: the part slot cycles through every material that part can be made of.
     */
    private static int swaps(HolderLookup.Provider access, Consumer<EmiRecipe> out) {
        int added = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof GearItem gear)) continue;
            try {
                ItemStack base = exampleGear(gear);
                if (base.isEmpty()) continue;
                GearType type = gear.getGearType();
                for (PartType partType : SWAPPABLE) {
                    CompoundPartItem partItem = partType.getCompoundPartItem(type).orElse(null);
                    if (partItem == null) continue;
                    List<EmiStack> parts = new ArrayList<>();
                    ItemStack shownResult = ItemStack.EMPTY;
                    for (ItemStack m : byMaterial(PartMaterialIngredient.of(partType, type).getItems().toArray(ItemStack[]::new)).values()) {
                        MaterialInstance mat = MaterialInstance.from(m);
                        if (mat == null) continue;
                        ItemStack part = partItem.create(mat);
                        PartInstance inst = PartInstance.from(part);
                        if (inst == null || !gear.supportsPart(base, inst)) continue;
                        parts.add(EmiStack.of(part));
                        if (shownResult.isEmpty()) {
                            ItemStack r = base.copy();
                            GearData.addOrReplacePart(r, inst);
                            GearData.recalculateGearData(r, null);
                            if (GearData.getPartOfType(r, partType) != null) shownResult = r;
                        }
                    }
                    if (parts.isEmpty() || shownResult.isEmpty()) continue;
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                    ResourceLocation partId = BuiltInRegistries.ITEM.getKey(partItem);
                    out.accept(new EmiCraftingRecipe(List.of(EmiStack.of(base), EmiIngredient.of(parts)), EmiStack.of(shownResult),
                            SilentRaids.id("/gear_swap/" + itemId.getNamespace() + "/" + itemId.getPath() + "/" + partId.getPath()), true));
                    added++;
                }
            } catch (Throwable t) {
                SilentRaids.LOGGER.debug("No part swaps shown for {}: {}", item, t.toString());
            }
        }
        return added;
    }

    /**
     * Tier upgrades: an iron example of each gear item + a head (or plates) of a material above iron = that material's
     * gear, through Silent Gear's swap recipe, which keeps enchantments, name and levels. One page per material so the
     * diamond helmet shows "iron helmet + diamond plates".
     */
    private static int mainSwaps(Consumer<EmiRecipe> out) {
        int added = 0;
        List<net.silentchaos512.gear.api.material.IMaterialCategory> castOnly =
                List.of(net.silentchaos512.gear.gear.material.MaterialCategories.get("emp_cast_only"));
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof GearItem gear)) continue;
            try {
                ItemStack base = exampleGear(gear);
                if (base.isEmpty()) continue;
                GearType type = gear.getGearType();
                CompoundPartItem mainItem = PartTypes.MAIN.get().getCompoundPartItem(type).orElse(null);
                if (mainItem == null) continue;
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                for (Map.Entry<ResourceLocation, ItemStack> e : byMaterial(PartMaterialIngredient.of(PartTypes.MAIN.get(), type)
                        .getItems().toArray(ItemStack[]::new)).entrySet()) {
                    MaterialInstance mat = MaterialInstance.from(e.getValue());
                    if (mat == null || !mat.hasAnyCategory(castOnly)) continue;
                    ItemStack part = mainItem.create(mat);
                    PartInstance inst = PartInstance.from(part);
                    if (inst == null) continue;
                    ItemStack result = base.copy();
                    GearData.addOrReplacePart(result, inst);
                    GearData.recalculateGearData(result, null);
                    out.accept(new EmiCraftingRecipe(List.of(EmiStack.of(base), EmiStack.of(part)), EmiStack.of(result),
                            SilentRaids.id(("/gear_upgrade/" + itemId.getNamespace() + "/" + itemId.getPath() + "/"
                                    + e.getKey().getNamespace() + "/" + e.getKey().getPath()).toLowerCase(Locale.ROOT)), true));
                    added++;
                }
            } catch (Throwable t) {
                SilentRaids.LOGGER.debug("No tier upgrades shown for {}: {}", item, t.toString());
            }
        }
        return added;
    }

    /** An iron (or the first hand-craftable material's) example of a gear item, with its required parts. */
    static ItemStack exampleGear(GearItem gear) {
        GearType type = gear.getGearType();
        List<PartInstance> parts = new ArrayList<>();
        for (PartType required : gear.getRequiredParts()) {
            CompoundPartItem partItem = required.getCompoundPartItem(type).orElse(null);
            if (partItem == null) return ItemStack.EMPTY;
            Map<ResourceLocation, ItemStack> mats = byMaterial(PartMaterialIngredient.of(required, type).getItems().toArray(ItemStack[]::new));
            ItemStack pick = null;
            for (Map.Entry<ResourceLocation, ItemStack> e : mats.entrySet()) {
                String p = e.getKey().getPath();
                if (p.equals("iron") || p.equals("wood")) { pick = e.getValue(); break; }
            }
            if (pick == null && !mats.isEmpty()) pick = mats.values().iterator().next();
            if (pick == null) return ItemStack.EMPTY;
            MaterialInstance mat = MaterialInstance.from(pick);
            if (mat == null) return ItemStack.EMPTY;
            PartInstance inst = PartInstance.from(partItem.create(mat));
            if (inst == null) return ItemStack.EMPTY;
            parts.add(inst);
        }
        return parts.isEmpty() ? ItemStack.EMPTY : gear.construct(parts);
    }

    // ------------------------------------------------------------------ helpers

    /** One item stack per material, in the order given. */
    static Map<ResourceLocation, ItemStack> byMaterial(ItemStack[] stacks) {
        Map<ResourceLocation, ItemStack> out = new LinkedHashMap<>();
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            MaterialInstance mat = MaterialInstance.from(s);
            if (mat == null || mat.getId() == null) continue;
            out.putIfAbsent(mat.getId(), s);
        }
        return out;
    }

    /** A made-up recipe id under silentraids (EMI's convention: the path starts with a slash). */
    static ResourceLocation synthetic(ResourceLocation recipe, ResourceLocation material) {
        String path = "/per_material/" + recipe.getNamespace() + "/" + recipe.getPath() + "/"
                + material.getNamespace() + "/" + material.getPath();
        return SilentRaids.id(path.toLowerCase(Locale.ROOT));
    }
}
