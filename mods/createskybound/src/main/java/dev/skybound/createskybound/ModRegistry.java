package dev.skybound.createskybound;

import com.mojang.serialization.MapCodec;
import dev.skybound.createskybound.block.LauncherInputBlock;
import dev.skybound.createskybound.block.LauncherInputBlockEntity;
import dev.skybound.createskybound.block.PylonBlock;
import dev.skybound.createskybound.block.ReturnAnchorBlock;
import dev.skybound.createskybound.block.VectorPlanarComputerBlock;
import dev.skybound.createskybound.block.VectorPlanarComputerBlockEntity;
import dev.skybound.createskybound.loot.AntennaLootModifier;
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
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateSkybound.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateSkybound.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateSkybound.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateSkybound.MODID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, CreateSkybound.MODID);

    // Resonant Antennas (dungeon loot) and the pylons made from them
    public static final DeferredItem<Item> ANTENNA_T1 = ITEMS.registerSimpleItem("resonant_antenna_t1", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ANTENNA_T2 = ITEMS.registerSimpleItem("resonant_antenna_t2", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> ANTENNA_T3 = ITEMS.registerSimpleItem("resonant_antenna_t3", new Item.Properties().rarity(Rarity.EPIC));

    private static BlockBehaviour.Properties pylonProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5f, 12f)
                .sound(SoundType.AMETHYST).lightLevel(s -> 7).requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<PylonBlock> PYLON_T1 = BLOCKS.register("resonance_pylon_t1", () -> new PylonBlock(1, pylonProps()));
    public static final DeferredBlock<PylonBlock> PYLON_T2 = BLOCKS.register("resonance_pylon_t2", () -> new PylonBlock(2, pylonProps()));
    public static final DeferredBlock<PylonBlock> PYLON_T3 = BLOCKS.register("resonance_pylon_t3", () -> new PylonBlock(3, pylonProps()));
    public static final DeferredItem<BlockItem> PYLON_T1_ITEM = ITEMS.registerSimpleBlockItem(PYLON_T1, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> PYLON_T2_ITEM = ITEMS.registerSimpleBlockItem(PYLON_T2, new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> PYLON_T3_ITEM = ITEMS.registerSimpleBlockItem(PYLON_T3, new Item.Properties().rarity(Rarity.EPIC));

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

    // The way home, placed at the arrival point in the Chaotic Skies
    public static final DeferredBlock<ReturnAnchorBlock> RETURN_ANCHOR = BLOCKS.register("return_anchor",
            () -> new ReturnAnchorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1f, 3600000f)
                    .sound(SoundType.AMETHYST).lightLevel(s -> 15).noLootTable()));

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AntennaLootModifier>> ANTENNA_LOOT =
            LOOT_MODIFIERS.register("antennas", () -> AntennaLootModifier.CODEC);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.createskybound"))
            .icon(() -> new ItemStack(VECTOR_PLANAR_COMPUTER_ITEM.get()))
            .displayItems((params, out) -> {
                out.accept(VECTOR_PLANAR_COMPUTER_ITEM.get());
                out.accept(ANTENNA_T1.get());
                out.accept(ANTENNA_T2.get());
                out.accept(ANTENNA_T3.get());
                out.accept(PYLON_T1_ITEM.get());
                out.accept(PYLON_T2_ITEM.get());
                out.accept(PYLON_T3_ITEM.get());
            })
            .build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
        LOOT_MODIFIERS.register(bus);
    }

    private ModRegistry() {}
}
