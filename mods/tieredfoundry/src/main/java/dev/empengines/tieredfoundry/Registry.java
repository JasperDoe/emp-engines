package dev.empengines.tieredfoundry;

import com.mojang.serialization.Codec;
import dev.empengines.tieredfoundry.block.CastingBlock;
import dev.empengines.tieredfoundry.block.ControllerBlock;
import dev.empengines.tieredfoundry.block.PartBlock;
import dev.empengines.tieredfoundry.block.TapBlock;
import dev.empengines.tieredfoundry.block.entity.CastingBlockEntity;
import dev.empengines.tieredfoundry.block.entity.ControllerBlockEntity;
import dev.empengines.tieredfoundry.block.entity.PartBlockEntity;
import dev.empengines.tieredfoundry.block.entity.TapBlockEntity;
import dev.empengines.tieredfoundry.item.CastItem;
import dev.empengines.tieredfoundry.menu.ControllerMenu;
import dev.empengines.tieredfoundry.recipe.AlloyRecipe;
import dev.empengines.tieredfoundry.recipe.CastingRecipe;
import dev.empengines.tieredfoundry.recipe.MeltingRecipe;
import dev.empengines.tieredfoundry.recipe.PartCastingRecipe;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class Registry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TieredFoundry.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TieredFoundry.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TieredFoundry.MODID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, TieredFoundry.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, TieredFoundry.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, TieredFoundry.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, TieredFoundry.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, TieredFoundry.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TieredFoundry.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TieredFoundry.MODID);

    /** Light while the block's LIT is on: heaters while burning, the controller and drains while there's metal or heat. */
    private static java.util.function.ToIntFunction<net.minecraft.world.level.block.state.BlockState> lit(int level) {
        return s -> s.hasProperty(dev.empengines.tieredfoundry.block.FoundryProps.LIT) && s.getValue(dev.empengines.tieredfoundry.block.FoundryProps.LIT) ? level : 0;
    }

    private static BlockBehaviour.Properties bricks() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS);
    }

    // ---- blocks ------------------------------------------------------------------------------------------------
    public static final DeferredBlock<Block> FIRE_BRICKS = BLOCKS.register("fire_bricks", () -> new Block(bricks().strength(3.0F, 9.0F)));
    /** A tier 2 foundry's corners (tier 1 uses vanilla's Brick Wall). */
    public static final DeferredBlock<net.minecraft.world.level.block.WallBlock> FIRE_BRICK_WALL = BLOCKS.register("fire_brick_wall",
            () -> new net.minecraft.world.level.block.WallBlock(bricks().strength(3.0F, 9.0F).forceSolidOn()));
    public static final DeferredBlock<ControllerBlock> CONTROLLER = BLOCKS.register("foundry_controller", () -> new ControllerBlock(bricks().lightLevel(lit(13))));
    public static final DeferredBlock<PartBlock> SOLID_HEATER = BLOCKS.register("solid_fuel_heater", () -> new PartBlock(PartBlock.Kind.SOLID_HEATER, bricks().lightLevel(lit(12))));
    public static final DeferredBlock<PartBlock> LAVA_HEATER = BLOCKS.register("lava_heater", () -> new PartBlock(PartBlock.Kind.LAVA_HEATER, bricks().lightLevel(lit(14))));
    public static final DeferredBlock<PartBlock> AIR_DUCT = BLOCKS.register("air_duct", () -> new PartBlock(PartBlock.Kind.AIR_DUCT, bricks()));
    public static final DeferredBlock<PartBlock> DRAIN = BLOCKS.register("foundry_drain", () -> new PartBlock(PartBlock.Kind.DRAIN, bricks().lightLevel(lit(10))));
    public static final DeferredBlock<TapBlock> TAP = BLOCKS.register("foundry_tap", () -> new TapBlock(bricks().noOcclusion().strength(1.5F)));
    public static final DeferredBlock<CastingBlock> CASTING_TABLE = BLOCKS.register("casting_table", () -> new CastingBlock(false, bricks().noOcclusion()));
    public static final DeferredBlock<CastingBlock> CASTING_BASIN = BLOCKS.register("casting_basin", () -> new CastingBlock(true, bricks().noOcclusion()));

    // ---- items ---------------------------------------------------------------------------------------------------
    public static final DeferredItem<BlockItem> FIRE_BRICKS_ITEM = ITEMS.registerSimpleBlockItem(FIRE_BRICKS);
    public static final DeferredItem<BlockItem> FIRE_BRICK_WALL_ITEM = ITEMS.registerSimpleBlockItem(FIRE_BRICK_WALL);
    public static final DeferredItem<BlockItem> CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(CONTROLLER);
    public static final DeferredItem<BlockItem> SOLID_HEATER_ITEM = ITEMS.registerSimpleBlockItem(SOLID_HEATER);
    public static final DeferredItem<BlockItem> LAVA_HEATER_ITEM = ITEMS.registerSimpleBlockItem(LAVA_HEATER);
    public static final DeferredItem<BlockItem> AIR_DUCT_ITEM = ITEMS.registerSimpleBlockItem(AIR_DUCT);
    public static final DeferredItem<BlockItem> DRAIN_ITEM = ITEMS.registerSimpleBlockItem(DRAIN);
    public static final DeferredItem<BlockItem> TAP_ITEM = ITEMS.registerSimpleBlockItem(TAP);
    public static final DeferredItem<BlockItem> CASTING_TABLE_ITEM = ITEMS.registerSimpleBlockItem(CASTING_TABLE);
    public static final DeferredItem<BlockItem> CASTING_BASIN_ITEM = ITEMS.registerSimpleBlockItem(CASTING_BASIN);
    public static final DeferredItem<Item> FIRE_CLAY = ITEMS.registerSimpleItem("fire_clay");
    public static final DeferredItem<Item> FIRE_BRICK = ITEMS.registerSimpleItem("fire_brick");
    public static final DeferredItem<CastItem> GOLD_CAST = ITEMS.register("gold_cast", () -> new CastItem(false, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<CastItem> SAND_CAST = ITEMS.register("sand_cast", () -> new CastItem(true, new Item.Properties().stacksTo(16)));

    // ---- data components -----------------------------------------------------------------------------------------
    /** What a cast is shaped like: "ingot", "nugget", "plate", "gem", "rod", "gear", or "item:<id>". */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PATTERN = COMPONENTS.registerComponentType("pattern",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    // ---- block entities ------------------------------------------------------------------------------------------
    public static final Supplier<BlockEntityType<ControllerBlockEntity>> CONTROLLER_BE = BLOCK_ENTITIES.register("controller",
            () -> BlockEntityType.Builder.of(ControllerBlockEntity::new, CONTROLLER.get()).build(null));
    public static final Supplier<BlockEntityType<PartBlockEntity>> PART_BE = BLOCK_ENTITIES.register("part",
            () -> BlockEntityType.Builder.of(PartBlockEntity::new, SOLID_HEATER.get(), LAVA_HEATER.get(), AIR_DUCT.get(), DRAIN.get()).build(null));
    public static final Supplier<BlockEntityType<TapBlockEntity>> TAP_BE = BLOCK_ENTITIES.register("tap",
            () -> BlockEntityType.Builder.of(TapBlockEntity::new, TAP.get()).build(null));
    public static final Supplier<BlockEntityType<CastingBlockEntity>> CASTING_BE = BLOCK_ENTITIES.register("casting",
            () -> BlockEntityType.Builder.of(CastingBlockEntity::new, CASTING_TABLE.get(), CASTING_BASIN.get()).build(null));

    // ---- menu ------------------------------------------------------------------------------------------------------
    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> CONTROLLER_MENU = MENUS.register("controller",
            () -> IMenuTypeExtension.create(ControllerMenu::new));

    // ---- recipes ---------------------------------------------------------------------------------------------------
    public static final DeferredHolder<RecipeType<?>, RecipeType<MeltingRecipe>> MELTING = RECIPE_TYPES.register("melting", () -> RecipeType.simple(TieredFoundry.id("melting")));
    public static final DeferredHolder<RecipeType<?>, RecipeType<AlloyRecipe>> ALLOYING = RECIPE_TYPES.register("alloying", () -> RecipeType.simple(TieredFoundry.id("alloying")));
    public static final DeferredHolder<RecipeType<?>, RecipeType<CastingRecipe>> CASTING = RECIPE_TYPES.register("casting", () -> RecipeType.simple(TieredFoundry.id("casting")));
    public static final DeferredHolder<RecipeType<?>, RecipeType<PartCastingRecipe>> PART_CASTING = RECIPE_TYPES.register("part_casting", () -> RecipeType.simple(TieredFoundry.id("part_casting")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PartCastingRecipe>> PART_CASTING_SERIALIZER = SERIALIZERS.register("part_casting", () -> PartCastingRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MeltingRecipe>> MELTING_SERIALIZER = SERIALIZERS.register("melting", () -> MeltingRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlloyRecipe>> ALLOYING_SERIALIZER = SERIALIZERS.register("alloying", () -> AlloyRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CastingRecipe>> CASTING_SERIALIZER = SERIALIZERS.register("casting", () -> CastingRecipe.SERIALIZER);

    static {
        Molten.registerAll();
    }

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tieredfoundry"))
            .icon(() -> new ItemStack(CONTROLLER_ITEM.get()))
            .displayItems((params, out) -> {
                for (DeferredHolder<Item, ? extends Item> i : ITEMS.getEntries()) {
                    if (i.get() instanceof CastItem) continue;
                    out.accept(i.get());
                }
                for (String p : CastItem.creativePatterns()) {
                    out.accept(CastItem.of(GOLD_CAST.get(), p));
                    out.accept(CastItem.of(SAND_CAST.get(), p));
                }
            }).build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
        MENUS.register(bus);
        RECIPE_TYPES.register(bus);
        SERIALIZERS.register(bus);
        COMPONENTS.register(bus);
        TABS.register(bus);
    }

    static void capabilities(RegisterCapabilitiesEvent e) {
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CONTROLLER_BE.get(), (be, side) -> be.input());
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PART_BE.get(), (be, side) -> be.items());
        e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PART_BE.get(), (be, side) -> be.fluids());
        e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CASTING_BE.get(), (be, side) -> be.tank());
        e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CASTING_BE.get(), (be, side) -> be.items());
    }

    private Registry() {}
}
