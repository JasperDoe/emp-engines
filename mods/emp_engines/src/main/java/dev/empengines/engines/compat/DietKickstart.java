package dev.empengines.engines.compat;

import dev.empengines.engines.EmpyreanEngines;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Diet - AppleSeed Edition only works out the nutrition of modded foods (Farmer's Delight meals and the rest)
 * after a /reload: on a normal world start it skips the calculation, so every modded food has no diet value.
 * This runs it once the server has started. It only fills in foods that don't have values yet (cached in
 * config/apple_seed_foods), so it's quick after the first time. After changing food mods, an admin can run
 * /diet cache regenerate to recalculate everything.
 */
public final class DietKickstart {
    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        if (!ModList.get().isLoaded("appleseed")) return;
        MinecraftServer server = e.getServer();
        try {
            Class.forName("net.appleseed.appleseed.common.data.food.FoodNutritionAutoCalculator")
                    .getMethod("calculateAllAsync", MinecraftServer.class, boolean.class)
                    .invoke(null, server, false);
            EmpyreanEngines.LOGGER.info("Started Diet's food nutrition calculation (modded foods)");
        } catch (Throwable t) {
            EmpyreanEngines.LOGGER.warn("Couldn't start Diet's food nutrition calculation; run /reload once to do it by hand", t);
        }
    }

    private DietKickstart() {}
}
