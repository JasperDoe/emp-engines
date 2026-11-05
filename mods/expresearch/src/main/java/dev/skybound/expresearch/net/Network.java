package dev.skybound.expresearch.net;

import dev.skybound.expresearch.client.ClientResearch;
import dev.skybound.expresearch.menu.ResearchTableMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Network {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");
        reg.playToClient(SyncResearchPayload.TYPE, SyncResearchPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientResearch.apply(payload)));
        reg.playToServer(DoResearchPayload.TYPE, DoResearchPayload.STREAM_CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof ResearchTableMenu menu) {
                menu.tryResearch(sp, payload.research());
            }
        }));
    }

    private Network() {}
}
