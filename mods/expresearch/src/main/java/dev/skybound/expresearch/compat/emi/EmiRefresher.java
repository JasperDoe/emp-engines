package dev.skybound.expresearch.compat.emi;

import com.gabinx.chapters.stage.ClientStageCache;
import dev.skybound.expresearch.ExpResearch;

import java.util.Set;

/**
 * Chapters only knows how to hide things in JEI. When the player's stages change,
 * reload EMI so {@link ExpResearchEmiPlugin} hides or shows the right items.
 */
public final class EmiRefresher {
    private static int lastHash = Integer.MIN_VALUE;

    public static void checkForChanges() {
        Set<?> stages = ClientStageCache.snapshot();
        int hash = stages.hashCode();
        if (lastHash == Integer.MIN_VALUE) { lastHash = hash; return; }
        if (hash == lastHash) return;
        lastHash = hash;
        try {
            Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod("reload").invoke(null);
        } catch (ReflectiveOperationException ex) {
            ExpResearch.LOGGER.warn("Could not reload EMI after a research change", ex);
        }
    }

    private EmiRefresher() {}
}
