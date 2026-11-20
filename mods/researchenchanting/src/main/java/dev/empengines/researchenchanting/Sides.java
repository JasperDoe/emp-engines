package dev.empengines.researchenchanting;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** The registries in use right now: the running server's, or the connected client's. */
public final class Sides {
    public static RegistryAccess registries() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        if (FMLEnvironment.dist.isClient()) return dev.empengines.researchenchanting.client.ClientHooks.registries();
        return null;
    }

    private Sides() {}
}
