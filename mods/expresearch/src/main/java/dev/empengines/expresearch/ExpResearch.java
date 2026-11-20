package dev.empengines.expresearch;

import com.mojang.logging.LogUtils;
import dev.empengines.expresearch.command.ResearchCommands;
import dev.empengines.expresearch.net.Network;
import dev.empengines.expresearch.research.ResearchLoader;
import dev.empengines.expresearch.research.ResearchService;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(ExpResearch.MODID)
public final class ExpResearch {
    public static final String MODID = "expresearch";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ExpResearch(IEventBus modBus) {
        ModRegistry.register(modBus);
        modBus.addListener(Network::register);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(new ResearchLoader.Categories());
            e.addListener(new ResearchLoader.Nodes());
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> ResearchCommands.register(e.getDispatcher()));
        ResearchService.registerEvents();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
