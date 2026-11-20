package dev.empengines.engines.raid;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ItemLike;

import java.util.List;

/**
 * The marker kit for configuring a raider ship in the gallery: renamed concrete, tagged so that
 * ordinary renamed concrete in survival never counts as a marker.
 */
public final class MarkerKit {
    public static final String TAG = "emp_engines_marker";

    public enum Type {
        HELM("Helm", Items.RED_CONCRETE, Blocks.RED_CONCRETE, ChatFormatting.RED,
                "The front is the way you face when you place it.", "The captain (with the banner) stands here."),
        HOOK_PORT("Port Hook", Items.BLACK_CONCRETE, Blocks.BLACK_CONCRETE, ChatFormatting.DARK_GRAY,
                "A boarding rope fires from here, on the ship's left (port).", "Used when the raider comes up with its left side to you."),
        HOOK_STARBOARD("Starboard Hook", Items.GREEN_CONCRETE, Blocks.GREEN_CONCRETE, ChatFormatting.DARK_GREEN,
                "A boarding rope fires from here, on the ship's right (starboard).", "Used when the raider comes up with its right side to you."),
        POST("Raider Post", Items.LIGHT_GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, ChatFormatting.GRAY,
                "One raider stands here before boarding.", "The number of posts is the crew size."),
        WITCH("Witch Post", Items.PURPLE_CONCRETE, Blocks.PURPLE_CONCRETE, ChatFormatting.DARK_PURPLE,
                "A witch stands here and throws Mining Fatigue.", "Optional."),
        HOLD("Loot Hold", Items.BROWN_CONCRETE, Blocks.BROWN_CONCRETE, ChatFormatting.GOLD,
                "A barrel goes here for stolen loot.", "Optional (else the first barrel or chest).");

        public final String label;
        public final ItemLike item;
        public final Block block;
        final ChatFormatting colour;
        final String line1, line2;

        Type(String label, ItemLike item, Block block, ChatFormatting colour, String line1, String line2) {
            this.label = label; this.item = item; this.block = block; this.colour = colour; this.line1 = line1; this.line2 = line2;
        }
    }

    public static ItemStack stack(Type t, int count) {
        ItemStack s = new ItemStack(t.item, count);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(t.label).withStyle(st -> st.withItalic(false).withColor(t.colour)));
        s.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(t.line1).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)),
                Component.literal(t.line2).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.DARK_GRAY)))));
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, t.name());
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return s;
    }

    /** The marker type of an item, or null if it isn't one of ours. */
    public static Type typeOf(ItemStack s) {
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(TAG)) return null;
        try { return Type.valueOf(tag.getString(TAG)); } catch (IllegalArgumentException e) { return null; }
    }

    public static List<ItemStack> kit() {
        return List.of(stack(Type.HELM, 1), stack(Type.HOOK_PORT, 6), stack(Type.HOOK_STARBOARD, 6), stack(Type.POST, 16), stack(Type.WITCH, 4), stack(Type.HOLD, 1));
    }

    private MarkerKit() {}
}
