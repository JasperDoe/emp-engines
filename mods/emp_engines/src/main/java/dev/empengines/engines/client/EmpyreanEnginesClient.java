package dev.empengines.engines.client;

import dev.empengines.engines.EmpyreanEngines;
import dev.empengines.engines.client.ponder.EmpyreanPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** Client-only entry point. Ponder plugins have to be added before the first resource reload. */
@Mod(value = EmpyreanEngines.MODID, dist = Dist.CLIENT)
public final class EmpyreanEnginesClient {
    public EmpyreanEnginesClient(net.neoforged.bus.api.IEventBus modBus) {
        PonderIndex.addPlugin(new EmpyreanPonderPlugin());
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e) ->
                e.register(dev.empengines.engines.ModRegistry.NAVIGATION_CONSOLE_MENU.get(), NavigationConsoleScreen::new));
    }
}
