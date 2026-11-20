package dev.empengines.engines.nav;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * A drive holding the way to one structure in the Etheric Skies. Put in a Navigation Console before launch,
 * the ship arrives next to that structure; the drive is used up. The target lives in the item's custom data:
 * {@code {target: "emp_engines:dungeon/astral_sanctum", target_name: "Astral Sanctum"}} (loot tables set it
 * with set_custom_data).
 */
public class NavigationalDataItem extends Item {
    public NavigationalDataItem(Properties props) { super(props); }

    /** The structure the drive leads to, or null if it's blank. */
    public static ResourceLocation target(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("target") ? ResourceLocation.tryParse(tag.getString("target")) : null;
    }

    /** What to call the destination. */
    public static String targetName(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("target_name")) return tag.getString("target_name");
        ResourceLocation t = target(stack);
        return t == null ? null : t.getPath();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        String name = targetName(stack);
        lines.add(name == null
                ? Component.translatable("item.emp_engines.navigational_data.blank").withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.emp_engines.navigational_data.target", name).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("item.emp_engines.navigational_data.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
