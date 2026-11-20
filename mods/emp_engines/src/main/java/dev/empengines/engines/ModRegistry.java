package dev.empengines.engines;

import com.mojang.serialization.MapCodec;
import dev.empengines.engines.block.LauncherInputBlock;
import dev.empengines.engines.block.LauncherInputBlockEntity;
import dev.empengines.engines.block.PylonBlock;
import dev.empengines.engines.block.PylonBlockEntity;
import dev.empengines.engines.sky.OnIslandStructure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import dev.empengines.engines.block.ReturnAnchorBlock;
import dev.empengines.engines.block.VectorPlanarComputerBlock;
import dev.empengines.engines.block.VectorPlanarComputerBlockEntity;
import dev.empengines.engines.loot.AntennaLootModifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EmpyreanEngines.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EmpyreanEngines.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EmpyreanEngines.MODID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, EmpyreanEngines.MODID);
    /** Etheric Skies: a vanilla structure set down on a big enough island, whatever the biome. */
    public static final DeferredHolder<StructureType<?>, StructureType<OnIslandStructure>> ON_ISLAND =
            STRUCTURE_TYPES.register("on_island", () -> () -> OnIslandStructure.CODEC);
    // Etheric Skies world generation: island layout, crystal spikes, open geodes, a biome-middle filter
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, EmpyreanEngines.MODID);
    public static final DeferredHolder<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.DensityFunction>, com.mojang.serialization.MapCodec<dev.empengines.engines.sky.gen.IslandCells>> ISLAND_CELLS =
            DENSITY_FUNCTION_TYPES.register("island_cells", () -> dev.empengines.engines.sky.gen.IslandCells.MAP_CODEC);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, EmpyreanEngines.MODID);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.feature.Feature<?>, dev.empengines.engines.sky.gen.CrystalSpikeFeature> CRYSTAL_SPIKE =
            FEATURES.register("crystal_spike", dev.empengines.engines.sky.gen.CrystalSpikeFeature::new);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.feature.Feature<?>, dev.empengines.engines.sky.gen.OpenGeodeFeature> OPEN_GEODE =
            FEATURES.register("open_geode", dev.empengines.engines.sky.gen.OpenGeodeFeature::new);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, EmpyreanEngines.MODID);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>, net.minecraft.world.level.levelgen.placement.PlacementModifierType<dev.empengines.engines.sky.gen.BiomeHeartFilter>> BIOME_HEART =
            PLACEMENT_MODIFIERS.register("biome_heart", () -> () -> dev.empengines.engines.sky.gen.BiomeHeartFilter.CODEC);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, EmpyreanEngines.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EmpyreanEngines.MODID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, EmpyreanEngines.MODID);

    // Resonant Antennas (dungeon loot) and the pylons made from them
    public static final DeferredItem<Item> ANTENNA_T1 = ITEMS.registerSimpleItem("resonant_antenna_t1", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ANTENNA_T2 = ITEMS.registerSimpleItem("resonant_antenna_t2", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> ANTENNA_T3 = ITEMS.registerSimpleItem("resonant_antenna_t3", new Item.Properties().rarity(Rarity.EPIC));

    private static BlockBehaviour.Properties pylonProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5f, 12f)
                .sound(SoundType.AMETHYST).lightLevel(PylonBlock::light).requiresCorrectToolForDrops().noOcclusion();
    }

    public static final DeferredBlock<PylonBlock> PYLON_T1 = BLOCKS.register("resonance_pylon_t1", () -> new PylonBlock(1, pylonProps()));
    public static final DeferredBlock<PylonBlock> PYLON_T2 = BLOCKS.register("resonance_pylon_t2", () -> new PylonBlock(2, pylonProps()));
    public static final DeferredBlock<PylonBlock> PYLON_T3 = BLOCKS.register("resonance_pylon_t3", () -> new PylonBlock(3, pylonProps()));
    public static final DeferredItem<BlockItem> PYLON_T1_ITEM = ITEMS.registerSimpleBlockItem(PYLON_T1, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> PYLON_T2_ITEM = ITEMS.registerSimpleBlockItem(PYLON_T2, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> PYLON_T3_ITEM = ITEMS.registerSimpleBlockItem(PYLON_T3, new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PylonBlockEntity>> PYLON_BE =
            BLOCK_ENTITIES.register("resonance_pylon", () -> BlockEntityType.Builder
                    .of(PylonBlockEntity::new, PYLON_T1.get(), PYLON_T2.get(), PYLON_T3.get()).build(null));

    // The launcher controller
    public static final DeferredBlock<VectorPlanarComputerBlock> VECTOR_PLANAR_COMPUTER = BLOCKS.register("vector_planar_computer",
            () -> new VectorPlanarComputerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW)
                    .strength(3f, 6f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(s -> 4)));
    public static final DeferredItem<BlockItem> VECTOR_PLANAR_COMPUTER_ITEM = ITEMS.registerSimpleBlockItem(VECTOR_PLANAR_COMPUTER, new Item.Properties().rarity(Rarity.RARE));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VectorPlanarComputerBlockEntity>> VECTOR_PLANAR_COMPUTER_BE =
            BLOCK_ENTITIES.register("vector_planar_computer", () -> BlockEntityType.Builder
                    .of(VectorPlanarComputerBlockEntity::new, VECTOR_PLANAR_COMPUTER.get()).build(null));

    // A ring casing wrenched open into a shaft input (no item: it is made in place and drops brass casing)
    public static final DeferredBlock<LauncherInputBlock> LAUNCHER_INPUT = BLOCKS.register("launcher_input",
            () -> new LauncherInputBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN)
                    .strength(2f, 6f).sound(SoundType.WOOD).noOcclusion()));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LauncherInputBlockEntity>> LAUNCHER_INPUT_BE =
            BLOCK_ENTITIES.register("launcher_input", () -> BlockEntityType.Builder
                    .of((pos, state) -> new LauncherInputBlockEntity(ModRegistry.LAUNCHER_INPUT_BE.get(), pos, state), LAUNCHER_INPUT.get()).build(null));

    // The way home, placed at the arrival point in the Etheric Skies
    public static final DeferredBlock<ReturnAnchorBlock> RETURN_ANCHOR = BLOCKS.register("return_anchor",
            () -> new ReturnAnchorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1f, 3600000f)
                    .sound(SoundType.AMETHYST).lightLevel(s -> 15).noLootTable()));

    // Nether portal keys: the keyhole a portal frame gets, the key, and the fragment found in ruined portals
    public static final DeferredBlock<dev.empengines.engines.portal.ObsidianKeyholeBlock> OBSIDIAN_KEYHOLE = BLOCKS.register("obsidian_keyhole",
            () -> new dev.empengines.engines.portal.ObsidianKeyholeBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OBSIDIAN)
                    .lightLevel(s -> s.getValue(dev.empengines.engines.portal.ObsidianKeyholeBlock.UNLOCKED) ? 6 : 0)));
    public static final DeferredItem<BlockItem> OBSIDIAN_KEYHOLE_ITEM = ITEMS.registerSimpleBlockItem(OBSIDIAN_KEYHOLE);
    public static final DeferredItem<dev.empengines.engines.portal.KeyItem> OBSIDIAN_KEY = ITEMS.register("obsidian_key",
            () -> new dev.empengines.engines.portal.KeyItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE),
                    "item.emp_engines.obsidian_key.tooltip", net.minecraft.ChatFormatting.LIGHT_PURPLE));
    public static final DeferredItem<dev.empengines.engines.portal.KeyItem> FRAGMENTED_OBSIDIAN_KEY = ITEMS.register("fragmented_obsidian_key",
            () -> new dev.empengines.engines.portal.KeyItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON),
                    "item.emp_engines.fragmented_obsidian_key.tooltip", net.minecraft.ChatFormatting.AQUA));

    // The Navigation Console's screen, the drive that sets a run's destination, and the eye mold fragments
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<dev.empengines.engines.nav.NavigationConsoleMenu>> NAVIGATION_CONSOLE_MENU =
            MENUS.register("navigation_console", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(dev.empengines.engines.nav.NavigationConsoleMenu::new));
    public static final DeferredItem<dev.empengines.engines.nav.NavigationalDataItem> NAVIGATIONAL_DATA = ITEMS.register("navigational_data",
            () -> new dev.empengines.engines.nav.NavigationalDataItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<dev.empengines.engines.portal.KeyItem> ASTRAL_EYE_MOLD_FRAGMENT = fragment("astral");
    public static final DeferredItem<dev.empengines.engines.portal.KeyItem> UMBRAL_EYE_MOLD_FRAGMENT = fragment("umbral");
    public static final DeferredItem<dev.empengines.engines.portal.KeyItem> GILDED_EYE_MOLD_FRAGMENT = fragment("gilded");

    private static DeferredItem<dev.empengines.engines.portal.KeyItem> fragment(String kind) {
        return ITEMS.register(kind + "_eye_mold_fragment", () -> new dev.empengines.engines.portal.KeyItem(
                new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), "item.emp_engines.eye_mold_fragment.tooltip", net.minecraft.ChatFormatting.LIGHT_PURPLE));
    }

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AntennaLootModifier>> ANTENNA_LOOT =
            LOOT_MODIFIERS.register("antennas", () -> AntennaLootModifier.CODEC);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.emp_engines"))
            .icon(() -> new ItemStack(VECTOR_PLANAR_COMPUTER_ITEM.get()))
            .displayItems((params, out) -> {
                out.accept(VECTOR_PLANAR_COMPUTER_ITEM.get());
                out.accept(ANTENNA_T1.get());
                out.accept(ANTENNA_T2.get());
                out.accept(ANTENNA_T3.get());
                out.accept(PYLON_T1_ITEM.get());
                out.accept(PYLON_T2_ITEM.get());
                out.accept(PYLON_T3_ITEM.get());
                out.accept(FRAGMENTED_OBSIDIAN_KEY.get());
                out.accept(OBSIDIAN_KEY.get());
                out.accept(OBSIDIAN_KEYHOLE_ITEM.get());
                out.accept(NAVIGATIONAL_DATA.get());
                out.accept(ASTRAL_EYE_MOLD_FRAGMENT.get());
                out.accept(UMBRAL_EYE_MOLD_FRAGMENT.get());
                out.accept(GILDED_EYE_MOLD_FRAGMENT.get());
            })
            .build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        STRUCTURE_TYPES.register(bus);
        DENSITY_FUNCTION_TYPES.register(bus);
        FEATURES.register(bus);
        PLACEMENT_MODIFIERS.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        LOOT_MODIFIERS.register(bus);
    }

    private ModRegistry() {}
}
