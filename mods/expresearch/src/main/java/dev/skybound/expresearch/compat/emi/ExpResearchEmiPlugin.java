package dev.skybound.expresearch.compat.emi;

import com.gabinx.chapters.logic.ClientStageView;
import com.gabinx.chapters.stage.ClientStageCache;
import com.gabinx.chapters.stage.ClientStageIndices;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Hides items (and recipes that make them) that the player hasn't researched yet. */
@EmiEntrypoint
public class ExpResearchEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        Set<ResourceLocation> locked = ClientStageView.computeFlagRestricted(
                ClientStageIndices.itemsView(), ClientStageIndices.itemJeiView(), ClientStageCache.snapshot());
        if (locked.isEmpty()) return;
        registry.removeEmiStacks(stack -> isLocked(stack, locked));
        registry.removeRecipes(recipe -> recipe.getOutputs().stream().anyMatch(out -> isLocked(out, locked)));
    }

    private static boolean isLocked(EmiStack stack, Set<ResourceLocation> locked) {
        ItemStack item = stack.getItemStack();
        return !item.isEmpty() && locked.contains(BuiltInRegistries.ITEM.getKey(item.getItem()));
    }
}
