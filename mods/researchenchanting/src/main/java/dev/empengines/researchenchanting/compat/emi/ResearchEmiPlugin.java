package dev.empengines.researchenchanting.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.empengines.researchenchanting.Config;
import dev.empengines.researchenchanting.Registry;
import dev.empengines.researchenchanting.ResearchEnchanting;
import dev.empengines.researchenchanting.client.ClientHooks;
import dev.empengines.researchenchanting.focus.Foci;
import dev.empengines.researchenchanting.focus.Focus;
import dev.empengines.researchenchanting.item.ResearchData;
import dev.empengines.researchenchanting.item.ResearchFocusItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One EMI page per researchable enchantment level (resource and amount), plus info pages for the table and notes. */
@EmiEntrypoint
public class ResearchEmiPlugin implements EmiPlugin {
    static EmiRecipeCategory CATEGORY;

    @Override
    public void register(EmiRegistry registry) {
        EmiStack table = EmiStack.of(Items.ENCHANTING_TABLE);
        CATEGORY = new EmiRecipeCategory(ResearchEnchanting.id("research"), table);
        registry.addCategory(CATEGORY);
        registry.addWorkstation(CATEGORY, table);
        registry.addWorkstation(CATEGORY, EmiStack.of(Items.CHISELED_BOOKSHELF));

        // Enchanted books don't exist in this pack any more (loot and trades give Research Notes): keep them out of EMI.
        registry.removeEmiStacks(st -> st.getItemStack().is(Items.ENCHANTED_BOOK));
        registry.addRecipe(new BindPage(ResearchEnchanting.id("/bind")));

        Map<ResourceLocation, Focus> foci = Foci.all(true);
        if (foci.isEmpty()) ClientHooks.emiMissedFoci = true;
        registry.addDeferredRecipes(out -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            var reg = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            for (Map.Entry<ResourceLocation, Focus> e : Foci.all(true).entrySet()) {
                for (Focus.Line line : e.getValue().lines()) {
                    var holder = reg.getHolder(ResourceKey.create(Registries.ENCHANTMENT, line.enchantment())).orElse(null);
                    if (holder == null) continue;
                    for (int lvl = 1; lvl <= line.max(); lvl++) {
                        ResourceLocation id = ResearchEnchanting.id("/research/" + e.getKey().getPath() + "/"
                                + line.enchantment().getNamespace() + "/" + line.enchantment().getPath() + "/" + lvl);
                        ItemStack book = ResearchFocusItem.blank(e.getKey());
                        book.set(Registry.RESEARCH.get(), ResearchData.fresh(e.getKey()).complete(line.enchantment(), lvl));
                        out.accept(new ResearchPage(id, e.getKey(), EmiStack.of(new ItemStack(line.resource()), Config.cost(lvl)),
                                EmiStack.of(book), lvl));
                    }
                }
            }
        });

        List<EmiIngredient> tableStacks = new ArrayList<>(List.of(table, EmiStack.of(Items.CHISELED_BOOKSHELF), EmiStack.of(Items.BOOK),
                EmiStack.of(Registry.RESEARCH_FOCUS.get())));
        registry.addRecipe(new EmiInfoRecipe(tableStacks, List.of(
                Component.translatable("emi.info.researchenchanting.table1"),
                Component.translatable("emi.info.researchenchanting.table2"),
                Component.translatable("emi.info.researchenchanting.table3"),
                Component.translatable("emi.info.researchenchanting.table4")), ResearchEnchanting.id("info/table")));
        registry.addRecipe(new EmiInfoRecipe(List.of(EmiStack.of(Registry.RESEARCH_NOTES.get())), List.of(
                Component.translatable("emi.info.researchenchanting.notes")), ResearchEnchanting.id("info/notes")));
    }

    /** Book + lapis at the enchanting table, Echo solved: a Research Focus. */
    static final class BindPage extends BasicEmiRecipe {
        BindPage(ResourceLocation id) {
            super(CATEGORY, id, 112, 44);
            inputs.add(EmiStack.of(Items.BOOK));
            inputs.add(EmiStack.of(Items.LAPIS_LAZULI));
            catalysts.add(EmiStack.of(Items.ENCHANTING_TABLE));
            outputs.add(EmiStack.of(Registry.RESEARCH_FOCUS.get()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addText(Component.translatable("emi.researchenchanting.bind"), 0, 0, 0x404040, false);
            widgets.addSlot(inputs.get(0), 0, 14);
            widgets.addSlot(inputs.get(1), 20, 14);
            widgets.addTexture(EmiTexture.EMPTY_ARROW, 44, 15);
            widgets.addSlot(outputs.get(0), 80, 10).large(true).recipeContext(this);
            widgets.addText(Component.translatable("emi.researchenchanting.bind_how"), 0, 33, 0x808080, false);
        }
    }

    /** Resource × amount at the table makes a focus learn this enchantment level. */
    static final class ResearchPage extends BasicEmiRecipe {
        private final ResourceLocation focus;
        private final int level;

        ResearchPage(ResourceLocation id, ResourceLocation focus, EmiStack resource, EmiStack book, int level) {
            super(CATEGORY, id, 112, 44);
            this.focus = focus;
            this.level = level;
            inputs.add(resource);
            catalysts.add(EmiStack.of(ResearchFocusItem.blank(focus)));
            outputs.add(book);
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addText(Focus.name(focus), 0, 0, 0x404040, false);
            widgets.addSlot(inputs.get(0), 0, 14);
            widgets.addSlot(catalysts.get(0), 20, 14).catalyst(true);
            widgets.addTexture(EmiTexture.EMPTY_ARROW, 44, 15);
            widgets.addSlot(outputs.get(0), 80, 10).large(true).recipeContext(this);
            int xp = level * Config.XP_PER_LEVEL.get();
            if (xp > 0) widgets.addText(Component.translatable("emi.researchenchanting.xp", xp), 0, 33, 0x80FF20, true);
        }
    }
}
