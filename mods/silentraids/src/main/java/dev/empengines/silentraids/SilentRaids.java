package dev.empengines.silentraids;

import com.mojang.logging.LogUtils;
import dev.empengines.silentraids.gear.GearLevels;
import dev.empengines.silentraids.gear.VanillaToSilent;
import dev.empengines.silentraids.loot.LootRules;
import dev.empengines.silentraids.net.BloodMoonSync;
import dev.empengines.silentraids.siege.BloodMoon;
import dev.empengines.silentraids.tier.PlayerTier;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Silent Apotheosis Raids: blood moon sieges, an automatic Apotheosis world tier per player, and levels (with
 * gem sockets) for Silent Gear tools, weapons and armour.
 */
@Mod(SilentRaids.MODID)
public final class SilentRaids {
    public static final String MODID = "silentraids";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SilentRaids(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        Registry.register(modBus);
        NeoForge.EVENT_BUS.register(BloodMoon.class);
        NeoForge.EVENT_BUS.register(PlayerTier.Events.class);
        NeoForge.EVENT_BUS.register(GearLevels.Events.class);
        NeoForge.EVENT_BUS.register(LootRules.class);
        NeoForge.EVENT_BUS.register(Commands.class);
        NeoForge.EVENT_BUS.register(VanillaToSilent.class);
        BloodMoonSync.register(modBus);
        NeoForge.EVENT_BUS.register(BloodMoonSync.class);
        if (FMLEnvironment.dist.isClient()) ClientHooks.init();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
