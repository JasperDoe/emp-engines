package dev.empengines.researchenchanting;

import com.mojang.logging.LogUtils;
import dev.empengines.researchenchanting.enchant.EnchantEvents;
import dev.empengines.researchenchanting.focus.Foci;
import dev.empengines.researchenchanting.net.FociSync;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Research Enchanting: the vanilla enchanting table becomes a research desk. Books are bound into research foci with
 * the Echo minigame, foci are researched one enchantment level at a time by feeding farmable resources from the
 * containers next to the table, and researched foci shelved in chiseled bookshelves around it let any item be
 * enchanted with exactly the enchantments chosen. Replaces Apothic Enchanting and random enchanting.
 */
@Mod(ResearchEnchanting.MODID)
public final class ResearchEnchanting {
    public static final String MODID = "researchenchanting";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ResearchEnchanting(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        Registry.register(modBus);
        modBus.addListener(FociSync::register);
        modBus.addListener(dev.empengines.researchenchanting.net.ResearchFly::register);
        NeoForge.EVENT_BUS.register(Foci.Events.class);
        NeoForge.EVENT_BUS.register(EnchantEvents.class);
        if (FMLEnvironment.dist.isClient()) dev.empengines.researchenchanting.client.ClientHooks.init(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
