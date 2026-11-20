package dev.empengines.silentraids;

import net.neoforged.neoforge.common.NeoForge;

/** Kept in its own class so the dedicated server never loads client code. */
final class ClientHooks {
    private ClientHooks() {}

    static void init() {
        NeoForge.EVENT_BUS.register(dev.empengines.silentraids.client.BloodMoonClient.class);
    }
}
