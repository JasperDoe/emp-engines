package dev.empengines.chunkupkeep.net;

import dev.empengines.chunkupkeep.ChunkUpkeep;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the claim map around a view centre, plus prices and the player's balance. */
public record LedgerSnapshotPayload(BlockPos ledger, int viewX, int viewZ, int radius, byte[] cells,
                                    int balance, int claimCost, int rent, int refundPercent,
                                    int claimed, int forceLoaded) implements CustomPacketPayload {
    public static final Type<LedgerSnapshotPayload> TYPE = new Type<>(ChunkUpkeep.id("snapshot"));

    public static final StreamCodec<FriendlyByteBuf, LedgerSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.ledger);
                buf.writeVarInt(p.viewX);
                buf.writeVarInt(p.viewZ);
                buf.writeVarInt(p.radius);
                buf.writeByteArray(p.cells);
                buf.writeVarInt(p.balance);
                buf.writeVarInt(p.claimCost);
                buf.writeVarInt(p.rent);
                buf.writeVarInt(p.refundPercent);
                buf.writeVarInt(p.claimed);
                buf.writeVarInt(p.forceLoaded);
            },
            buf -> new LedgerSnapshotPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readByteArray(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
