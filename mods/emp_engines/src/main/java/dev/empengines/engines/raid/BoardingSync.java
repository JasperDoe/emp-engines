package dev.empengines.engines.raid;

import dev.empengines.engines.EmpyreanEngines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tells clients where the boarding connectors and ropes are (positions in ship plots, which don't change
 * as the ships move): the connectors so breaking one takes the same fixed time on the client as on the
 * server, the ropes so the client can draw them and let players walk on them.
 */
public record BoardingSync(List<BlockPos> connectors, List<BoardingRope> ropes) implements CustomPacketPayload {
    public static final Type<BoardingSync> TYPE = new Type<>(EmpyreanEngines.id("boarding"));
    public static final StreamCodec<? super RegistryFriendlyByteBuf, BoardingSync> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.<io.netty.buffer.ByteBuf, BlockPos>list()), BoardingSync::connectors,
            BoardingRope.CODEC.apply(ByteBufCodecs.<io.netty.buffer.ByteBuf, BoardingRope>list()), BoardingSync::ropes,
            BoardingSync::new);

    /** The client's copies (empty on a dedicated server). */
    public static volatile Set<BlockPos> CLIENT = Set.of();
    public static volatile List<BoardingRope> CLIENT_ROPES = List.of();

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("2").optional().playToClient(TYPE, CODEC, BoardingSync::handle);
    }

    private static void handle(BoardingSync msg, IPayloadContext ctx) {
        CLIENT = new HashSet<>(msg.connectors());
        CLIENT_ROPES = List.copyOf(msg.ropes());
    }
}
