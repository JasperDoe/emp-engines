package dev.empengines.expresearch;

import dev.empengines.expresearch.block.ResearchTableBlock;
import dev.empengines.expresearch.item.ResearchedBlueprintItem;
import dev.empengines.expresearch.menu.ResearchTableMenu;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ExpResearch.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ExpResearch.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ExpResearch.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ExpResearch.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExpResearch.MODID);

    /** Which research a researched blueprint teaches. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> RESEARCH =
            COMPONENTS.registerComponentType("research", b -> b.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));

    public static final DeferredBlock<ResearchTableBlock> RESEARCH_TABLE = BLOCKS.register("research_table",
            () -> new ResearchTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD)));
    public static final DeferredItem<BlockItem> RESEARCH_TABLE_ITEM = ITEMS.registerSimpleBlockItem(RESEARCH_TABLE);

    /** Blank blueprint: the input of every research. */
    public static final DeferredItem<Item> BLUEPRINT = ITEMS.registerSimpleItem("blueprint", new Item.Properties());
    public static final DeferredItem<ResearchedBlueprintItem> RESEARCHED_BLUEPRINT = ITEMS.register("researched_blueprint",
            () -> new ResearchedBlueprintItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<MenuType<?>, MenuType<ResearchTableMenu>> RESEARCH_TABLE_MENU =
            MENUS.register("research_table", () -> IMenuTypeExtension.create(ResearchTableMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.expresearch"))
            .icon(() -> new ItemStack(RESEARCH_TABLE_ITEM.get()))
            .displayItems((params, out) -> {
                out.accept(RESEARCH_TABLE_ITEM.get());
                out.accept(BLUEPRINT.get());
            })
            .build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
        COMPONENTS.register(bus);
        TABS.register(bus);
    }

    private ModRegistry() {}
}
