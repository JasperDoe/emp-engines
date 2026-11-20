package dev.empengines.chunkupkeep;

import dev.empengines.chunkupkeep.block.ClaimLedgerBlock;
import dev.empengines.chunkupkeep.block.ClaimLedgerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ChunkUpkeep.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ChunkUpkeep.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ChunkUpkeep.MODID);

    public static final DeferredBlock<ClaimLedgerBlock> CLAIM_LEDGER = BLOCKS.register("claim_ledger",
            () -> new ClaimLedgerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, 1200f).sound(SoundType.WOOD)));
    public static final DeferredItem<BlockItem> CLAIM_LEDGER_ITEM = ITEMS.registerSimpleBlockItem(CLAIM_LEDGER);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ClaimLedgerBlockEntity>> CLAIM_LEDGER_BE =
            BLOCK_ENTITIES.register("claim_ledger", () -> BlockEntityType.Builder.of(ClaimLedgerBlockEntity::new, CLAIM_LEDGER.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) e.accept(CLAIM_LEDGER_ITEM.get());
        });
    }

    private ModRegistry() {}
}
