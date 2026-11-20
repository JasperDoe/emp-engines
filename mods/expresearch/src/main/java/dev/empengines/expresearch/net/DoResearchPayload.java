package dev.empengines.expresearch.net;

import dev.empengines.expresearch.ExpResearch;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: research the selected node at the open Research Table. */
public record DoResearchPayload(ResourceLocation research) implements CustomPacketPayload {
    public static final Type<DoResearchPayload> TYPE = new Type<>(ExpResearch.id("do_research"));
    public static final StreamCodec<ByteBuf, DoResearchPayload> STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC, DoResearchPayload::research, DoResearchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
