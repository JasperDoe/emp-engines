package dev.skybound.expresearch.research;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The loaded research tree. The server fills it from datapacks; the client gets a copy over the network. */
public final class ResearchRegistry {
    private static Map<ResourceLocation, ResearchCategory> categories = Map.of();
    private static Map<ResourceLocation, ResearchNode> nodes = Map.of();

    public static Map<ResourceLocation, ResearchCategory> categories() { return categories; }
    public static Map<ResourceLocation, ResearchNode> nodes() { return nodes; }
    public static ResearchNode node(ResourceLocation id) { return nodes.get(id); }

    public static void setCategories(Map<ResourceLocation, ResearchCategory> c) { categories = Collections.unmodifiableMap(new LinkedHashMap<>(c)); }
    public static void setNodes(Map<ResourceLocation, ResearchNode> n) { nodes = Collections.unmodifiableMap(new LinkedHashMap<>(n)); }

    private ResearchRegistry() {}
}
