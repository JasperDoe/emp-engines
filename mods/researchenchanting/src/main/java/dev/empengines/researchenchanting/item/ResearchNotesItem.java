package dev.empengines.researchenchanting.item;

import dev.empengines.researchenchanting.Registry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * What enchanted books from loot and trades become. Put in a container next to the table while that enchantment
 * is being researched at or below the notes' level, one page pays for the whole level.
 */
public class ResearchNotesItem extends Item {
    public ResearchNotesItem(Properties props) {
        super(props);
    }

    public static ItemStack of(Notes notes) {
        ItemStack stack = new ItemStack(Registry.RESEARCH_NOTES.get());
        stack.set(Registry.NOTES.get(), notes);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        Notes n = stack.get(Registry.NOTES.get());
        if (n == null) return;
        lines.add(ResearchFocusItem.enchantName(n.enchantment(), n.level()).copy().withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("item.researchenchanting.research_notes.use").withStyle(ChatFormatting.GRAY));
    }
}
