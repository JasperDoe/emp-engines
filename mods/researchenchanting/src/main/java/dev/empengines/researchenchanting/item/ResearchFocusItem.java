package dev.empengines.researchenchanting.item;

import dev.empengines.researchenchanting.Config;
import dev.empengines.researchenchanting.Registry;
import dev.empengines.researchenchanting.focus.Foci;
import dev.empengines.researchenchanting.focus.Focus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

/**
 * A book bound to a research focus. In the enchanting table it opens that focus's research tree; shelved in a
 * chiseled bookshelf around the table it offers every enchantment level researched in it.
 */
public class ResearchFocusItem extends Item {
    public ResearchFocusItem(Properties props) {
        super(props);
    }

    public static ItemStack blank(ResourceLocation focus) {
        ItemStack stack = new ItemStack(Registry.RESEARCH_FOCUS.get());
        stack.set(Registry.RESEARCH.get(), ResearchData.fresh(focus));
        return stack;
    }

    public static ResearchData data(ItemStack stack) {
        return stack.is(Registry.RESEARCH_FOCUS.get()) ? stack.get(Registry.RESEARCH.get()) : null;
    }

    public static boolean isResearched(ItemStack stack) {
        ResearchData d = data(stack);
        return d != null && d.hasResearch();
    }

    @Override
    public Component getName(ItemStack stack) {
        ResearchData d = data(stack);
        if (d == null) return super.getName(stack);
        // "Sharpness III Focus", or "Melee Offensive Focus" before anything is researched.
        if (!d.hasResearch()) return Component.translatable("item.researchenchanting.research_focus.named", Focus.name(d.focus()));
        return Component.translatable("item.researchenchanting.research_focus.named",
                enchantName(d.enchantment().get(), d.level())).withStyle(ChatFormatting.AQUA);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;    // like an enchanted book
    }

    /** Texture choice for the item model: gear group * 6 + tier (the enchantment's level, 0-5). */
    public static float look(ItemStack stack) {
        ResearchData d = data(stack);
        if (d == null) return 0;
        Focus f = Foci.get(d.focus(), true);
        int gear = f == null ? 0 : f.gearIndex();
        int tier = Math.min(5, d.level());
        return (gear * 6 + tier) / 64.0F;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        ResearchData d = data(stack);
        if (d == null) {
            lines.add(Component.translatable("item.researchenchanting.research_focus.unbound").withStyle(ChatFormatting.GRAY));
            return;
        }
        Focus f = Foci.get(d.focus(), true);
        lines.add(Focus.name(d.focus()).withStyle(ChatFormatting.GRAY));
        if (f != null) lines.add(Focus.gearName(f.gear()).withStyle(ChatFormatting.DARK_GRAY));
        d.active().ifPresent(a -> lines.add(Component.translatable("item.researchenchanting.research_focus.researching",
                enchantName(a.enchantment(), a.level()), a.paid(), Config.cost(a.level())).withStyle(ChatFormatting.GRAY)));
        if (!d.hasResearch()) lines.add(Component.translatable("item.researchenchanting.research_focus.empty").withStyle(ChatFormatting.GRAY));
        else lines.add(Component.translatable("item.researchenchanting.research_focus.shelve").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** "Sharpness IV", from the client's or server's enchantment registry (falls back to the lang key). */
    public static Component enchantName(ResourceLocation id, int level) {
        Holder<Enchantment> h = holder(id);
        if (h != null) return Enchantment.getFullname(h, level);
        return Component.translatable("enchantment." + id.getNamespace() + "." + id.getPath())
                .append(" ").append(Component.translatable("enchantment.level." + level));
    }

    private static Holder<Enchantment> holder(ResourceLocation id) {
        var access = dev.empengines.researchenchanting.Sides.registries();
        if (access == null) return null;
        return access.registry(Registries.ENCHANTMENT)
                .flatMap(r -> r.getHolder(ResourceKey.create(Registries.ENCHANTMENT, id))).orElse(null);
    }
}
