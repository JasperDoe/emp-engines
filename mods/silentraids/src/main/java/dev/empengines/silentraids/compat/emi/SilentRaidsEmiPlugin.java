package dev.empengines.silentraids.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.empengines.silentraids.SilentRaids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * EMI additions: Silent Gear recipes per material (see {@link GearRecipesEmi}) and info pages for things recipe
 * screens can't show: coating on the casting table, and vanilla gear converting. Part casting per material is on
 * Tiered Foundry's own table casting pages.
 */
@EmiEntrypoint
public class SilentRaidsEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        GearRecipesEmi.register(registry);

        List<EmiStack> table = stacks("tieredfoundry:casting_table");
        List<EmiStack> gear = stacks("silentgear:pickaxe", "silentgear:sword", "silentgear:axe", "silentgear:helmet", "silentgear:chestplate");
        if (!table.isEmpty()) {
            List<EmiIngredient> shown = new ArrayList<>(table);
            shown.add(EmiIngredient.of(gear));
            registry.addRecipe(new EmiInfoRecipe(shown, List.of(
                    Component.literal("Coating: put a Silent Gear tool, weapon or armour piece on a casting table and pour molten metal over it."),
                    Component.literal("It comes out coated with that metal and fully repaired, for one ingot's (or gem's) worth of fluid (90 mB)."),
                    Component.literal("This is the only way to coat gear. The foundry has to be hot enough to melt the metal first.")),
                    SilentRaids.id("info/cast_coating")));
        }
        if (!gear.isEmpty()) {
            registry.addRecipe(new EmiInfoRecipe(List.of(EmiIngredient.of(gear)), List.of(
                    Component.literal("Vanilla tools and armour can't be made. Any you find turn into their Silent Gear versions in your inventory, keeping enchantments and wear."),
                    Component.literal("Iron tier and below: craft Silent Gear tools and armour from the material and sticks, in the vanilla shapes."),
                    Component.literal("Above iron, the head (or plates) is cast at the foundry; the tool is then head + rod in the crafting grid."),
                    Component.literal("Upgrade finished gear in the crafting grid: gear + a rod, tip, grip or binding swaps that part. Rods and tips of any castable metal are cast at the foundry.")),
                    SilentRaids.id("info/silent_gear_only")));
        }
    }

    private static List<EmiStack> stacks(String... ids) {
        List<EmiStack> out = new ArrayList<>();
        for (String id : ids) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item != Items.AIR) out.add(EmiStack.of(item));
        }
        return out;
    }
}
