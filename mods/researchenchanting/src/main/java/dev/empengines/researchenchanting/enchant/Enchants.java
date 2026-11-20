package dev.empengines.researchenchanting.enchant;

import dev.empengines.researchenchanting.ResearchEnchanting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/** The enchantments this mod adds or changes; their effects are code ({@link EnchantEvents}), their entries data. */
public final class Enchants {
    public static final ResourceKey<Enchantment> RENDING = key("rending");
    public static final ResourceKey<Enchantment> HOOKSHOT = key("hookshot");
    /** Reworked: lightning on hit instead of damage to undead. */
    public static final ResourceKey<Enchantment> SMITE = Enchantments.SMITE;
    /** Reworked as Boon of Arthropods: kills summon silverfish allies. */
    public static final ResourceKey<Enchantment> BOON = Enchantments.BANE_OF_ARTHROPODS;

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT, ResearchEnchanting.id(path));
    }

    /** Unbreaking and Mending are gone: Silent Gear's levels and repair kits cover durability. */
    public static boolean isRemoved(Holder<Enchantment> h) {
        return h.is(Enchantments.UNBREAKING) || h.is(Enchantments.MENDING);
    }

    public static boolean isRemoved(ResourceLocation id) {
        return id.equals(Enchantments.UNBREAKING.location()) || id.equals(Enchantments.MENDING.location());
    }

    public static int level(LivingEntity holder, ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty()) return 0;
        return holder.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(key)
                .map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
    }

    private Enchants() {}
}
