package dev.empengines.researchenchanting.focus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.empengines.researchenchanting.ResearchEnchanting;
import dev.empengines.researchenchanting.net.FociSync;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The loaded research foci: the server's from datapacks, the client's from {@link FociSync}. */
public final class Foci {
    private static volatile Map<ResourceLocation, Focus> server = Map.of();
    private static volatile Map<ResourceLocation, Focus> client = Map.of();

    /** The foci for this side (pass level.isClientSide()). */
    public static Map<ResourceLocation, Focus> all(boolean clientSide) {
        return clientSide ? client : server;
    }

    public static Focus get(ResourceLocation id, boolean clientSide) {
        return id == null ? null : all(clientSide).get(id);
    }

    public static void setClient(Map<ResourceLocation, Focus> foci) {
        client = Collections.unmodifiableMap(new LinkedHashMap<>(foci));
    }

    private static final Gson GSON = new GsonBuilder().create();

    static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() { super(GSON, "research_enchanting/focus"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller p) {
            Map<ResourceLocation, Focus> out = new LinkedHashMap<>();
            // A file that only says {"retired": true} is a focus removed from the pack (left on disk by updates).
            files.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .filter(e -> !(e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("retired")))
                    .forEach(e -> Focus.CODEC.parse(JsonOps.INSTANCE, e.getValue())
                    .resultOrPartial(err -> ResearchEnchanting.LOGGER.error("Bad research focus {}: {}", e.getKey(), err))
                    .ifPresent(f -> out.put(e.getKey(), f)));
            server = Collections.unmodifiableMap(out);
            ResearchEnchanting.LOGGER.info("Loaded {} research foci", out.size());
        }
    }

    public static final class Events {
        @SubscribeEvent
        public static void reload(AddReloadListenerEvent e) {
            e.addListener(new Loader());
        }

        @SubscribeEvent
        public static void sync(OnDatapackSyncEvent e) {
            FociSync msg = new FociSync(server);
            if (e.getPlayer() != null) PacketDistributor.sendToPlayer(e.getPlayer(), msg);
            else PacketDistributor.sendToAllPlayers(msg);
        }

        private Events() {}
    }

    private Foci() {}
}
