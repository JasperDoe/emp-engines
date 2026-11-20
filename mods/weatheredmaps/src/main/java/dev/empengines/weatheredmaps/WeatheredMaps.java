package dev.empengines.weatheredmaps;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Weathered Maps: sealed treasure maps found in peaceful places (villages, sky villages, crashed airships, traders'
 * carts...). Right-click one to unroll it into a filled map marking the nearest unclaimed place of its kind:
 * - Weathered Dungeon Map: YUNG's dungeons and trial chambers (tag weatheredmaps:dungeons)
 * - Weathered Pillager Map: pillager outposts and woodland mansions (tag weatheredmaps:pillager_bases)
 * - Weathered Fort Map: Wabi-Sabi's hostile keeps, garrisons and forts (tag weatheredmaps:hostile_forts)
 * The targets are structure tags and the chests are global loot modifiers, all plain data in this jar.
 */
@Mod(WeatheredMaps.MODID)
public final class WeatheredMaps {
    public static final String MODID = "weatheredmaps";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<WeatheredMapItem> DUNGEON_MAP = ITEMS.register("weathered_dungeon_map",
            () -> new WeatheredMapItem(tag("dungeons"), MapDecorationTypes.RED_X, props()));
    public static final DeferredItem<WeatheredMapItem> PILLAGER_MAP = ITEMS.register("weathered_pillager_map",
            () -> new WeatheredMapItem(tag("pillager_bases"), MapDecorationTypes.WOODLAND_MANSION, props()));
    public static final DeferredItem<WeatheredMapItem> FORT_MAP = ITEMS.register("weathered_fort_map",
            () -> new WeatheredMapItem(tag("hostile_forts"), MapDecorationTypes.TARGET_POINT, props()));

    public WeatheredMaps(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(WeatheredMaps::creativeTab);
    }

    private static Item.Properties props() {
        return new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON);
    }

    private static TagKey<Structure> tag(String path) {
        return TagKey.create(Registries.STRUCTURE, id(path));
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(DUNGEON_MAP.get());
            e.accept(PILLAGER_MAP.get());
            e.accept(FORT_MAP.get());
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
