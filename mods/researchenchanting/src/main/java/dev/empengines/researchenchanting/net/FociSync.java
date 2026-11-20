package dev.empengines.researchenchanting.net;

import com.mojang.serialization.Codec;
import dev.empengines.researchenchanting.ResearchEnchanting;
import dev.empengines.researchenchanting.focus.Foci;
import dev.empengines.researchenchanting.focus.Focus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Map;

/** Server to client: every research focus, so the table screen can draw trees and EMI can list costs. */
public record FociSync(Map<ResourceLocation, Focus> foci) implements CustomPacketPayload {
    public static final Type<FociSync> TYPE = new Type<>(ResearchEnchanting.id("foci"));
    public static final StreamCodec<ByteBuf, FociSync> CODEC = ByteBufCodecs
            .fromCodec(Codec.unboundedMap(ResourceLocation.CODEC, Focus.CODEC))
            .map(FociSync::new, FociSync::foci);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("1").playToClient(TYPE, CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            Foci.setClient(msg.foci());
            dev.empengines.researchenchanting.client.ClientHooks.fociChanged();
        }));
    }
}
