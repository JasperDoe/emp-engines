package dev.empengines.researchenchanting.net;

import dev.empengines.researchenchanting.ResearchEnchanting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server to nearby clients: one copy of a research resource flies from a container into the table's book. */
public record ResearchFly(BlockPos table, BlockPos from, ItemStack stack) implements CustomPacketPayload {
    public static final Type<ResearchFly> TYPE = new Type<>(ResearchEnchanting.id("fly"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchFly> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ResearchFly::table,
            BlockPos.STREAM_CODEC, ResearchFly::from,
            ItemStack.STREAM_CODEC, ResearchFly::stack,
            ResearchFly::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("1").optional().playToClient(TYPE, CODEC, (msg, ctx) -> ctx.enqueueWork(() ->
                dev.empengines.researchenchanting.client.Flights.add(msg.table(), msg.from(), msg.stack())));
    }
}
