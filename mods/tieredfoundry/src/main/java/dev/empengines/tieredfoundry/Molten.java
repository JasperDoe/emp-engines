package dev.empengines.tieredfoundry;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;

/**
 * The molten fluids. Each has a source, a flowing fluid, a liquid block and a bucket, tagged c:molten_&lt;name&gt; (by
 * the data files), drawn with one shared greyscale texture tinted per fluid. The temperature is only for display and
 * for hot-liquid behaviour; what a recipe needs is in the recipe.
 */
public final class Molten {
    /** name, colour (ARGB), temperature (for display) */
    public record Def(String name, int colour, int temperature) {}

    public static final List<Def> DEFS = List.of(
            new Def("iron", 0xFFE0703C, 950),
            new Def("gold", 0xFFFFC43A, 850),
            new Def("copper", 0xFFE5784E, 800),
            new Def("zinc", 0xFFB7CBC2, 420),
            new Def("brass", 0xFFE2B45A, 900),
            new Def("netherite", 0xFF5A4448, 1500),
            new Def("ancient_debris", 0xFF7A5040, 1500),
            new Def("crimson_iron", 0xFFE0465A, 1100),
            new Def("azure_silver", 0xFFC6A8FF, 1200),
            new Def("blaze_gold", 0xFFFF9A1F, 1300),
            new Def("crimson_steel", 0xFFB3243C, 1600),
            new Def("azure_electrum", 0xFF6FA8FF, 1500),
            new Def("tyrian_steel", 0xFF9A3FD0, 1800),
            new Def("diamond", 0xFF6FE8E0, 1450),
            new Def("shulker", 0xFFA07AAA, 1200),
            new Def("blaze", 0xFFFFB02B, 1000),
            new Def("magma_cream", 0xFFD9541E, 900),
            new Def("ender", 0xFF1E7A6E, 1000),
            new Def("glass", 0xFFFFB45E, 1000));

    public static final List<Molten> ALL = new ArrayList<>();

    public final Def def;
    public final DeferredHolder<FluidType, FluidType> type;
    public final DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
    public final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
    public final DeferredBlock<LiquidBlock> block;
    public final DeferredItem<BucketItem> bucket;
    private BaseFlowingFluid.Properties props;

    private Molten(Def def) {
        this.def = def;
        String n = "molten_" + def.name();
        type = Registry.FLUID_TYPES.register(n, () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid." + TieredFoundry.MODID + "." + n)
                .temperature(def.temperature() + 273).density(3000).viscosity(6000).lightLevel(12)
                .canSwim(false).canDrown(false).canExtinguish(false).supportsBoating(false)
                .canConvertToSource(false)));
        source = Registry.FLUIDS.register(n, () -> new BaseFlowingFluid.Source(props()));
        flowing = Registry.FLUIDS.register(n + "_flowing", () -> new BaseFlowingFluid.Flowing(props()));
        block = Registry.BLOCKS.register(n, () -> new LiquidBlock(source.get(),
                BlockBehaviour.Properties.ofFullCopy(Blocks.LAVA).lightLevel(s -> 12)));
        bucket = Registry.ITEMS.register(n + "_bucket", () -> new BucketItem(source.get(),
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    }

    private BaseFlowingFluid.Properties props() {
        if (props == null) {
            props = new BaseFlowingFluid.Properties(type, source, flowing).bucket(bucket).block(block)
                    .slopeFindDistance(2).levelDecreasePerBlock(2).tickRate(30).explosionResistance(100);
        }
        return props;
    }

    static void registerAll() {
        for (Def d : DEFS) ALL.add(new Molten(d));
    }
}
