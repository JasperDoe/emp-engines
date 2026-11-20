package dev.empengines.expresearch.client;

import dev.empengines.expresearch.net.SyncResearchPayload;
import dev.empengines.expresearch.research.ResearchRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/** Client copy of the research tree and the team's unlocks. */
public final class ClientResearch {
    private static Set<ResourceLocation> unlocked = Set.of();

    public static void apply(SyncResearchPayload p) {
        ResearchRegistry.setCategories(p.categories());
        ResearchRegistry.setNodes(p.nodes());
        unlocked = new HashSet<>(p.unlocked());
    }

    public static Set<ResourceLocation> unlocked() { return unlocked; }

    private ClientResearch() {}
}
