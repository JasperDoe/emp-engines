package dev.empengines.chunkupkeep.client;

import dev.empengines.chunkupkeep.net.LedgerSnapshotPayload;
import net.minecraft.client.Minecraft;

public final class ClientHooks {
    public static void openOrUpdate(LedgerSnapshotPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LedgerScreen s && s.ledger().equals(p.ledger())) s.update(p);
        else mc.setScreen(new LedgerScreen(p));
    }

    private ClientHooks() {}
}
