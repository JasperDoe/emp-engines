package dev.empengines.researchenchanting;

import com.mojang.serialization.MapCodec;
import dev.empengines.researchenchanting.enchant.RendedEffect;
import dev.empengines.researchenchanting.focus.Foci;
import dev.empengines.researchenchanting.item.Notes;
import dev.empengines.researchenchanting.item.ResearchData;
import dev.empengines.researchenchanting.item.ResearchFocusItem;
import dev.empengines.researchenchanting.item.ResearchNotesItem;
import dev.empengines.researchenchanting.loot.NotesLootModifier;
import dev.empengines.researchenchanting.menu.ResearchTableMenu;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class Registry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ResearchEnchanting.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ResearchEnchanting.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ResearchEnchanting.MODID);
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, ResearchEnchanting.MODID);
    /** Vanilla's enchanting glyphs on a curving path; x/y/z is the start, the speed arguments are the target. */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, net.minecraft.core.particles.SimpleParticleType> GLYPH =
            PARTICLES.register("glyph", () -> new net.minecraft.core.particles.SimpleParticleType(true));
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ResearchEnchanting.MODID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ResearchEnchanting.MODID);

    /** A Research Focus's focus and research. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResearchData>> RESEARCH =
            COMPONENTS.registerComponentType("research", b -> b.persistent(ResearchData.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(ResearchData.CODEC)));
    /** On a plain book in the table: the focus it will become, fixed so taking it out can't reroll it. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> PENDING_FOCUS =
            COMPONENTS.registerComponentType("pending_focus", b -> b.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Notes>> NOTES =
            COMPONENTS.registerComponentType("notes", b -> b.persistent(Notes.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(Notes.CODEC)));

    public static final DeferredItem<ResearchFocusItem> RESEARCH_FOCUS = ITEMS.register("research_focus",
            () -> new ResearchFocusItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<ResearchNotesItem> RESEARCH_NOTES = ITEMS.register("research_notes",
            () -> new ResearchNotesItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<MenuType<?>, MenuType<ResearchTableMenu>> TABLE_MENU =
            MENUS.register("research_table", () -> IMenuTypeExtension.create(ResearchTableMenu::new));

    /** Rending: armour points stripped; amplifier + 1 = points. */
    public static final DeferredHolder<MobEffect, MobEffect> RENDED = EFFECTS.register("rended",
            () -> new RendedEffect(MobEffectCategory.HARMFUL, 0x9A4B3C)
                    .addAttributeModifier(Attributes.ARMOR, ResearchEnchanting.id("rended"), -1.0, AttributeModifier.Operation.ADD_VALUE));

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<NotesLootModifier>> NOTES_LOOT =
            LOOT_MODIFIERS.register("enchanted_books_to_notes", () -> NotesLootModifier.CODEC);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        MENUS.register(bus);
        COMPONENTS.register(bus);
        EFFECTS.register(bus);
        PARTICLES.register(bus);
        LOOT_MODIFIERS.register(bus);
        bus.addListener(Registry::tabs);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CreativeModeTabs.TOOLS_AND_UTILITIES) return;
        // Creative tabs are filled on the client, from the foci the server sent.
        for (ResourceLocation id : Foci.all(true).keySet()) e.accept(ResearchFocusItem.blank(id));
    }

    private Registry() {}
}
