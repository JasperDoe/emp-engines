package dev.skybound.expresearch.client;

import dev.skybound.expresearch.ExpResearch;
import dev.skybound.expresearch.ModRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ClientSetup {
    @EventBusSubscriber(modid = ExpResearch.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent
        public static void screens(RegisterMenuScreensEvent e) {
            e.register(ModRegistry.RESEARCH_TABLE_MENU.get(), ResearchTableScreen::new);
        }
    }

    @EventBusSubscriber(modid = ExpResearch.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private static int ticks;

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            if (++ticks % 40 != 0) return;
            if (ModList.get().isLoaded("emi")) dev.skybound.expresearch.compat.emi.EmiRefresher.checkForChanges();
        }
    }

    private ClientSetup() {}
}
