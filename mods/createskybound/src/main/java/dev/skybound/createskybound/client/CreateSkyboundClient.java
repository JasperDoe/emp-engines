package dev.skybound.createskybound.client;

import dev.skybound.createskybound.CreateSkybound;
import dev.skybound.createskybound.client.ponder.SkyboundPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** Client-only entry point. Ponder plugins have to be added before the first resource reload. */
@Mod(value = CreateSkybound.MODID, dist = Dist.CLIENT)
public final class CreateSkyboundClient {
    public CreateSkyboundClient() {
        PonderIndex.addPlugin(new SkyboundPonderPlugin());
    }
}
