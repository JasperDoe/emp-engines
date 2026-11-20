package dev.empengines.silentraids.net;

import dev.empengines.silentraids.SilentRaids;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Tells clients whether the blood moon is up, so they can paint the moon and sky red. */
public final class BloodMoonSync {
    public record Payload(boolean active) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(SilentRaids.id("blood_moon"));
        public static final StreamCodec<ByteBuf, Payload> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Payload::active, Payload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client side: what the server last said. Read by the client visuals. */
    public static volatile boolean clientActive;
    /** Server side: what we last sent. */
    private static boolean serverActive;

    private BloodMoonSync() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(BloodMoonSync::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent e) {
        e.registrar("1").optional()
                .playToClient(Payload.TYPE, Payload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> clientActive = p.active()));
    }

    public static void set(MinecraftServer server, boolean active) {
        if (active == serverActive) return;
        serverActive = active;
        PacketDistributor.sendToAllPlayers(new Payload(active));
    }

    public static void reset() { serverActive = false; }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p, new Payload(serverActive));
    }
}
