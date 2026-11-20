package dev.empengines.engines.portal;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** The Obsidian Key and its fragment: an item with a line of tooltip. */
public class KeyItem extends Item {
    private final String tooltip;
    private final ChatFormatting colour;

    public KeyItem(Properties props, String tooltip, ChatFormatting colour) {
        super(props);
        this.tooltip = tooltip;
        this.colour = colour;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(tooltip).withStyle(colour));
    }
}
