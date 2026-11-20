package dev.empengines.expresearch.research;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.empengines.expresearch.ExpResearch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

/** Datapack loaders for categories and research nodes. */
public final class ResearchLoader {
    private static final Gson GSON = new GsonBuilder().create();

    public static final class Categories extends SimpleJsonResourceReloadListener {
        public Categories() { super(GSON, "expresearch/category"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller p) {
            Map<ResourceLocation, ResearchCategory> out = new LinkedHashMap<>();
            files.forEach((id, json) -> { if (!retired(json)) ResearchCategory.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> ExpResearch.LOGGER.error("Bad research category {}: {}", id, err))
                    .ifPresent(c -> out.put(id, c)); });
            ResearchRegistry.setCategories(out);
            ExpResearch.LOGGER.info("Loaded {} research categories", out.size());
        }
    }

    public static final class Nodes extends SimpleJsonResourceReloadListener {
        public Nodes() { super(GSON, "expresearch/research"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller p) {
            Map<ResourceLocation, ResearchNode> out = new LinkedHashMap<>();
            files.forEach((id, json) -> { if (!retired(json)) ResearchNode.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> ExpResearch.LOGGER.error("Bad research {}: {}", id, err))
                    .ifPresent(n -> out.put(id, n)); });
            ResearchRegistry.setNodes(out);
            ExpResearch.LOGGER.info("Loaded {} research nodes", out.size());
            ResearchService.resyncAll();
        }
    }

    /** A file that only says {"retired": true}: research removed from the pack but left on disk by updates. */
    private static boolean retired(JsonElement json) {
        return json.isJsonObject() && json.getAsJsonObject().has("retired");
    }

    private ResearchLoader() {}
}
