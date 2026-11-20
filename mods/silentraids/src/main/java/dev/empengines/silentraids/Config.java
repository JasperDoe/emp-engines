package dev.empengines.silentraids;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * serverconfig/silentraids-server.toml (per world; new worlds copy defaultconfigs/silentraids-server.toml).
 * The comments are what the file shows above each setting, so they're written for a pack admin.
 */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static { B.comment("Night raids: when they come and what happens on the night.").push("bloodMoon"); }

    public static final ModConfigSpec.IntValue INTERVAL = B
            .comment("A night raid comes every this many nights (5 = nights 5, 10, 15...). Counted from the Overworld's day count.")
            .defineInRange("everyNights", 5, 1, 100);
    public static final ModConfigSpec.BooleanValue NO_BEDS = B
            .comment("Beds can't be slept in on a night raid night.")
            .define("noSleeping", true);
    public static final ModConfigSpec.IntValue MIN_RADIUS = B
            .comment("Raiders spawn at least this many blocks from the player...")
            .defineInRange("spawnRadiusMin", 30, 8, 128);
    public static final ModConfigSpec.IntValue MAX_RADIUS = B
            .comment("...and at most this many.")
            .defineInRange("spawnRadiusMax", 40, 8, 128);
    public static final ModConfigSpec.IntValue GROUP_RADIUS = B
            .comment("Players closer than this to each other share one siege (one set of waves, a bit bigger).")
            .defineInRange("shareRadius", 48, 0, 256);
    public static final ModConfigSpec.IntValue MAX_ALIVE = B
            .comment("Most raiders alive at once in one siege; the next wave waits until there's room.")
            .defineInRange("maxAlive", 30, 1, 200);
    public static final ModConfigSpec.IntValue WAVE_TIMEOUT = B
            .comment("Seconds before the next wave comes even if the last one isn't beaten.")
            .defineInRange("waveTimeoutSeconds", 150, 10, 3600);
    public static final ModConfigSpec.DoubleValue SURVIVE_SIGIL = B
            .comment("Chance of a Sigil of Socketing for each player who beats every wave of a night raid.")
            .defineInRange("surviveSigilChance", 1.0, 0.0, 1.0);

    static { B.pop(); B.comment("Raiders breaking into bases.").push("breaching"); }

    public static final ModConfigSpec.IntValue BREAK_SECONDS = B
            .comment("Seconds a raider takes to break one block. Only plain Minecraft blocks without a block entity can be",
                     "broken: never chests, furnaces, beds or other containers, and never blocks from Create, Create: New Age,",
                     "the foundry or any other mod. Obsidian and the blocks in the tag silentraids:siege_proof can't be broken either.")
            .defineInRange("secondsPerBlock", 30, 1, 3600);
    public static final ModConfigSpec.BooleanValue DROP_BLOCKS = B
            .comment("Broken blocks drop as items (so you can put them back).")
            .define("dropBrokenBlocks", true);

    static { B.pop(); B.comment("Automatic Apotheosis world tier. Every player moves up on their own; nobody can pick a tier by hand.",
            "A tier needs its hours of play AND its milestone.").push("tiers"); }

    public static final ModConfigSpec.DoubleValue FRONTIER_HOURS = B.comment("Hours of play for Frontier.").defineInRange("frontierHours", 5.0, 0.0, 10000.0);
    public static final ModConfigSpec.DoubleValue ASCENT_HOURS = B.comment("Hours of play for Ascent (and having been to the Nether).").defineInRange("ascentHours", 15.0, 0.0, 10000.0);
    public static final ModConfigSpec.DoubleValue SUMMIT_HOURS = B.comment("Hours of play for Summit (and having killed the Wither).").defineInRange("summitHours", 30.0, 0.0, 10000.0);
    public static final ModConfigSpec.DoubleValue PINNACLE_HOURS = B.comment("Hours of play for Pinnacle (and having been to the End).").defineInRange("pinnacleHours", 60.0, 0.0, 10000.0);
    public static final ModConfigSpec.DoubleValue PINNACLE_FULL_HOURS = B
            .comment("Hours of play at which a Pinnacle player's sieges are at their hardest.")
            .defineInRange("pinnacleMaxHours", 100.0, 0.0, 10000.0);

    static { B.pop(); B.comment("Silent Gear levels: tools, weapons and armour (not curios) gain XP by being used.").push("gearLevels"); }

    public static final ModConfigSpec.DoubleValue BONUS_PER_LEVEL = B
            .comment("Bonus to every stat per level (0.05 = +5%): durability, mining speed, damage, armour, toughness...")
            .defineInRange("statBonusPerLevel", 0.05, 0.0, 1.0);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> CAPS = B
            .comment("Highest level for each world tier: Haven, Frontier, Ascent, Summit, Pinnacle.")
            .defineList("levelCaps", List.of(3, 5, 7, 9, 10), () -> 5, o -> o instanceof Integer i && i >= 0 && i <= 100);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> SOCKET_LEVELS = B
            .comment("Levels that give the item a new Apotheosis gem socket.")
            .defineList("socketLevels", List.of(3, 6, 9), () -> 3, o -> o instanceof Integer i && i >= 1 && i <= 100);
    public static final ModConfigSpec.DoubleValue XP_SCALE = B
            .comment("XP needed per level = 100 x (level + 1)^1.5 x this. Higher = slower levelling.")
            .defineInRange("xpScale", 1.0, 0.01, 100.0);
    public static final ModConfigSpec.IntValue XP_BLOCK = B.comment("XP for mining a block with the right tool.").defineInRange("xpPerBlock", 1, 0, 1000);
    public static final ModConfigSpec.IntValue XP_ORE = B.comment("XP for mining an ore (#c:ores).").defineInRange("xpPerOre", 4, 0, 1000);
    public static final ModConfigSpec.DoubleValue XP_DAMAGE = B
            .comment("XP per point of damage dealt to a monster (weapons) or taken from a mob (each armour piece worn).")
            .defineInRange("xpPerDamage", 1.0, 0.0, 1000.0);

    public static final ModConfigSpec SPEC = B.pop().build();

    private Config() {}
}
