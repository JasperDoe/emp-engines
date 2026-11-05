package dev.skybound.chunkupkeep.net;

import dev.skybound.chunkupkeep.LedgerService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Network {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");
        reg.playToClient(LedgerSnapshotPayload.TYPE, LedgerSnapshotPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.skybound.chunkupkeep.client.ClientHooks.openOrUpdate(p)));
        reg.playToServer(LedgerActionPayload.TYPE, LedgerActionPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (p.action() < 0) LedgerService.send(sp, p.ledger(), p.viewX(), p.viewZ());
            else LedgerService.handle(sp, p.ledger(), p.chunkX(), p.chunkZ(), p.action(), p.viewX(), p.viewZ());
        }));
    }

    private Network() {}
}
