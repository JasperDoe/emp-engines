package dev.skybound.expresearch.net;

import com.mojang.serialization.Codec;
import dev.skybound.expresearch.ExpResearch;
import dev.skybound.expresearch.research.ResearchCategory;
import dev.skybound.expresearch.research.ResearchNode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Server to client: the whole research tree plus what the player's team has unlocked. */
public record SyncResearchPayload(Map<ResourceLocation, ResearchCategory> categories,
                                  Map<ResourceLocation, ResearchNode> nodes,
                                  List<ResourceLocation> unlocked) implements CustomPacketPayload {
    public static final Type<SyncResearchPayload> TYPE = new Type<>(ExpResearch.id("sync"));

    private static final StreamCodec<ByteBuf, Map<ResourceLocation, ResearchCategory>> CATS =
            ByteBufCodecs.fromCodec(Codec.unboundedMap(ResourceLocation.CODEC, ResearchCategory.CODEC));
    private static final StreamCodec<ByteBuf, Map<ResourceLocation, ResearchNode>> NODES =
            ByteBufCodecs.fromCodec(Codec.unboundedMap(ResourceLocation.CODEC, ResearchNode.CODEC));
    private static final StreamCodec<ByteBuf, List<ResourceLocation>> IDS =
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, SyncResearchPayload> STREAM_CODEC = StreamCodec.composite(
            CATS, SyncResearchPayload::categories,
            NODES, SyncResearchPayload::nodes,
            IDS, SyncResearchPayload::unlocked,
            SyncResearchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
