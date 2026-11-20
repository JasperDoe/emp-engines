package dev.empengines.researchenchanting;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * serverconfig/researchenchanting-server.toml (per world; new worlds copy defaultconfigs/researchenchanting-server.toml).
 * Server configs are sent to clients when they join, so the research screen and EMI show the same costs.
 */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static { B.comment("Researching enchantment levels at the enchanting table.").push("research"); }

    public static final ModConfigSpec.IntValue COST_BASE = B
            .comment("Items of its resource that level I of an enchantment costs to research.")
            .defineInRange("costLevelOne", 10, 1, 100000);
    public static final ModConfigSpec.DoubleValue COST_MULTIPLIER = B
            .comment("Each level costs this many times the level before (4 = 10, 40, 160, 640, 2560).")
            .defineInRange("costMultiplier", 4.0, 1.0, 10.0);
    public static final ModConfigSpec.IntValue XP_PER_LEVEL = B
            .comment("XP levels paid when a research starts, per enchantment level (Sharpness III costs 3x this).")
            .defineInRange("xpPerResearchLevel", 1, 0, 30);
    public static final ModConfigSpec.IntValue PULL_INTERVAL = B
            .comment("Ticks between pulls of resource from the containers next to the table.")
            .defineInRange("pullIntervalTicks", 10, 1, 200);
    public static final ModConfigSpec.IntValue MIN_PULLS = B
            .comment("A research takes at least this many pulls, however much is waiting in the containers,",
                     "so 2560 iron at 100 pulls of 10 ticks takes 50 seconds with a full chest.")
            .defineInRange("minimumPulls", 100, 1, 10000);

    static { B.pop(); B.comment("Binding a book into a research focus with the Echo minigame: the table lights its buttons",
            "in a sequence and the player repeats it.").push("binding"); }

    public static final ModConfigSpec.IntValue BIND_XP = B
            .comment("XP levels paid when the book is bound.")
            .defineInRange("xpCost", 3, 0, 30);
    public static final ModConfigSpec.IntValue ECHO_ROUNDS = B
            .comment("Rounds to repeat; each is one step longer than the last.")
            .defineInRange("rounds", 3, 1, 6);
    public static final ModConfigSpec.IntValue ECHO_START = B
            .comment("Steps in the first round.")
            .defineInRange("firstRoundSteps", 3, 1, 5);
    public static final ModConfigSpec.IntValue ECHO_STEP_TICKS = B
            .comment("Ticks each step of the sequence lights for (20 = one second).")
            .defineInRange("stepTicks", 12, 4, 60);
    public static final ModConfigSpec.IntValue ECHO_BOOKS_PER_SLOWER = B
            .comment("Every this many books in the chiseled bookshelves around the table play the sequence 3 ticks a step slower,",
                     "up to 4 times.")
            .defineInRange("booksPerSlowerStep", 6, 1, 100);

    static { B.pop(); B.comment("Enchanting an item with researched books.").push("enchanting"); }

    public static final ModConfigSpec.IntValue LAPIS_PER_ENCHANT = B
            .comment("Lapis lazuli per enchantment added or raised. XP levels paid are the levels added.")
            .defineInRange("lapisPerEnchantment", 1, 0, 64);

    public static final ModConfigSpec.IntValue ENCHANT_TICKS = B
            .comment("Ticks an enchantment takes. The menu closes and the table works on the item while everyone watches.")
            .defineInRange("enchantTicks", 100, 1, 1200);

    static { B.pop(); }

    public static final ModConfigSpec SPEC = B.build();

    /** Items a research of this level costs. */
    public static int cost(int level) {
        double c = COST_BASE.get() * Math.pow(COST_MULTIPLIER.get(), Math.max(0, level - 1));
        return (int) Math.min(Integer.MAX_VALUE / 2, Math.round(c));
    }

    private Config() {}
}
