package dev.empengines.researchenchanting.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.empengines.researchenchanting.item.Notes;
import dev.empengines.researchenchanting.item.ResearchNotesItem;
import dev.empengines.researchenchanting.enchant.Enchants;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

/** Every enchanted book in any loot becomes Research Notes for its strongest enchantment. */
public class NotesLootModifier extends LootModifier {
    public static final MapCodec<NotesLootModifier> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).apply(i, NotesLootModifier::new));

    public NotesLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        loot.replaceAll(NotesLootModifier::convert);
        return loot;
    }

    /** An enchanted book as Research Notes (a plain book if it held only curses or removed enchantments). */
    public static ItemStack convert(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return stack;
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        Holder<Enchantment> best = null;
        int bestLevel = 0;
        for (var e : stored.entrySet()) {
            Holder<Enchantment> h = e.getKey();
            if (h.is(EnchantmentTags.CURSE) || Enchants.isRemoved(h)) continue;
            if (e.getIntValue() > bestLevel) { best = h; bestLevel = e.getIntValue(); }
        }
        if (best == null || best.unwrapKey().isEmpty()) return new ItemStack(Items.BOOK, stack.getCount());
        ItemStack notes = ResearchNotesItem.of(new Notes(best.unwrapKey().get().location(), bestLevel));
        notes.setCount(Math.min(stack.getCount(), notes.getMaxStackSize()));
        return notes;
    }

    @Override
    public MapCodec<? extends LootModifier> codec() {
        return CODEC;
    }
}
