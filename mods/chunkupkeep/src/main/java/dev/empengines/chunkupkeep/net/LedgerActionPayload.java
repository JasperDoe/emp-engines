package dev.empengines.chunkupkeep.net;

import dev.empengines.chunkupkeep.ChunkUpkeep;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: claim, unclaim or toggle force loading of a chunk (or just move the view, action -1). */
public record LedgerActionPayload(BlockPos ledger, int chunkX, int chunkZ, int action, int viewX, int viewZ) implements CustomPacketPayload {
    public static final Type<LedgerActionPayload> TYPE = new Type<>(ChunkUpkeep.id("action"));

    public static final StreamCodec<FriendlyByteBuf, LedgerActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.ledger);
                buf.writeVarInt(p.chunkX);
                buf.writeVarInt(p.chunkZ);
                buf.writeVarInt(p.action);
                buf.writeVarInt(p.viewX);
                buf.writeVarInt(p.viewZ);
            },
            buf -> new LedgerActionPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
