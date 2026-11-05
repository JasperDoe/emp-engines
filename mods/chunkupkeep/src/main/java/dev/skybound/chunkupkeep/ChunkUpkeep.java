package dev.skybound.chunkupkeep;

import com.mojang.logging.LogUtils;
import dev.skybound.chunkupkeep.economy.Income;
import dev.skybound.chunkupkeep.economy.Rent;
import dev.skybound.chunkupkeep.net.Network;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ChunkUpkeep.MODID)
public final class ChunkUpkeep {
    public static final String MODID = "chunkupkeep";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChunkUpkeep(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        ModRegistry.register(modBus);
        modBus.addListener(Network::register);
        ClaimGate.register();
        NeoForge.EVENT_BUS.register(Income.class);
        NeoForge.EVENT_BUS.register(Rent.class);
        NeoForge.EVENT_BUS.register(LedgerPlacement.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
