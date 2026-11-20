package dev.empengines.researchenchanting.table;

import dev.empengines.researchenchanting.ResearchEnchanting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hidden advancements (no display, no toast) granted by code, so quests can check them with an advancement task:
 * the Exotic Age chapter uses all three.
 */
public final class Milestones {
    public static final String BIND_FOCUS = "bind_focus";
    public static final String RESEARCH_NODE = "research_node";
    public static final String ENCHANT_ITEM = "enchant_item";

    public static void award(ServerPlayer player, String name) {
        AdvancementHolder adv = player.server.getAdvancements().get(ResearchEnchanting.id(name));
        if (adv == null) return;
        if (player.getAdvancements().getOrStartProgress(adv).isDone()) return;
        player.getAdvancements().award(adv, "done");
    }

    private Milestones() {}
}
