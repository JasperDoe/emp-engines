package dev.empengines.engines;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * serverconfig/emp_engines-server.toml (per world; new worlds copy defaultconfigs/emp_engines-server.toml).
 * The comments here are what the file shows above each setting, so they're written for a pack admin.
 */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // =============================================================================================
    static { B.comment("The Skyward Launcher: building it, charging it and the launch itself.").push("launcher"); }

    public static final ModConfigSpec.DoubleValue INPUT_STRESS = B
            .comment("Stress (SU) each Launcher Shaft Input (a wrenched ring casing) takes, per RPM fed into it.",
                     "Higher = launchers need bigger kinetic setups to charge. 0 = free.")
            .defineInRange("inputStressImpact", 32.0, 0.0, 4096.0);
    public static final ModConfigSpec.IntValue BASE_CHARGE = B
            .comment("Charge every launch needs, before the ship's weight is added.",
                     "Each input adds (its RPM / 8) charge every tick, so 20000 is about 40 s with one input at 256 RPM.")
            .defineInRange("baseCharge", 20000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue CHARGE_PER_MASS = B
            .comment("Extra charge needed per unit of ship mass (Sable's mass, shown when you right-click the computer).",
                     "Higher = big ships take much longer to charge than small ones.")
            .defineInRange("chargePerMass", 40.0, 0.0, 1_000_000.0);
    public static final ModConfigSpec.DoubleValue MASS_PER_TILE = B
            .comment("Weight limit: how much ship mass (Sable's mass) each platform tile lifts. The limit is the",
                     "platform's area times this: 6.4 gives 160 on the smallest (5 x 5) platform, 518 on 9 x 9, ~7000 on 33 x 33.",
                     "A ship over the limit can't launch.")
            .defineInRange("massPerTile", 6.4, 0.0, 1_000_000.0);
    public static final ModConfigSpec.IntValue MAX_PLATFORM = B
            .comment("Largest launch platform, in blocks along one side (ring included). Bigger platforms take bigger ships.")
            .defineInRange("maxPlatformSize", 33, 5, 129);
    public static final ModConfigSpec.IntValue CLOUD_HEIGHT = B
            .comment("Height of the cloud layer: where the launch breaks through (with a ripple) and the beams switch to",
                     "drawing through clouds. The pack's shader clouds sit at 224.")
            .defineInRange("cloudHeight", 224, 0, 2000);
    public static final ModConfigSpec.IntValue LAUNCH_APEX = B
            .comment("Height the thrown part of a launch carries the ship to, before it coasts and fades to white.",
                     "Keep it a little above cloudHeight so the ship breaks out over the clouds. Capped by the world's height.")
            .defineInRange("launchApex", 256, 0, 2000);

    static { B.pop(); B.comment("Runs in the Etheric Skies: how long they last and how you leave.").push("skyRuns"); }

    public static final ModConfigSpec.IntValue RUN_MINUTES_T1 = B
            .comment("Minutes a run lasts when the launcher's weakest pylon is tier 1 (Resonance Pylon).")
            .defineInRange("runMinutesTier1", 30, 1, 600);
    public static final ModConfigSpec.IntValue RUN_MINUTES_T2 = B
            .comment("Minutes a run lasts when the weakest pylon is tier 2.")
            .defineInRange("runMinutesTier2", 50, 1, 600);
    public static final ModConfigSpec.IntValue RUN_MINUTES_T3 = B
            .comment("Minutes a run lasts when all pylons are tier 3.")
            .defineInRange("runMinutesTier3", 70, 1, 600);
    public static final ModConfigSpec.IntValue WARNING_SECONDS = B
            .comment("When the 'DIMENSION DESTABILISING' warnings start, in seconds before the run collapses.")
            .defineInRange("warningSeconds", 300, 10, 3600);
    public static final ModConfigSpec.IntValue EXIT_HEIGHT = B
            .comment("Falling below this height in the Etheric Skies drops you back into the Overworld sky.")
            .defineInRange("exitHeight", 8, -64, 256);
    public static final ModConfigSpec.IntValue OVERWORLD_DROP_HEIGHT = B
            .comment("Height you reappear at in the Overworld after falling out of the Etheric Skies.")
            .defineInRange("overworldDropHeight", 400, 0, 2000);

    static { B.pop(); B.comment("Sky Raiders: boarding raids by raider airships. They start on their own against players on a ship high up,",
                         "with Bad Omen at once, or by /skyraid start.").push("raids"); }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RAID_SHIPS = B
            .comment("Raider ships offered in the gallery (/skyraid gallery), as structure ids from installed mods. Any airship",
                     "file works: raiders fly by magic and its controls only put on a show. A ship only raids once it's been set",
                     "up with /skyraid config, which saves config/emp_engines/raider_ships/<name>.json (and .nbt with its blocks);",
                     "ships saved there are offered even if they're not listed here.")
            .defineListAllowEmpty("raidShips", List.of("cr_aero_structures:pillager_airballoon",
                    "cr_aero_structures:pillager_airship"), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.IntValue RAID_GAP = B
            .comment("Gap in blocks between the raider ship's hull and yours while it's locked alongside.",
                     "Smaller = shorter ropes, easier to jump across; bigger = longer rope walk.")
            .defineInRange("raidGap", 5, 2, 16);
    public static final ModConfigSpec.IntValue RAID_OVERWHELM = B
            .comment("Your crew is overwhelmed (looters go for your chests) when boarders on your deck outnumber the players",
                     "aboard by this many. (Nobody aboard for 20 s also counts.)")
            .defineInRange("raidOverwhelmMargin", 3, 1, 20);
    public static final ModConfigSpec.IntValue RAID_STACKS = B
            .comment("Stacks each looter can carry off per trip. 0 = they never take anything.")
            .defineInRange("raidStacks", 3, 0, 27);
    public static final ModConfigSpec.DoubleValue RAID_ROPE_SAG = B
            .comment("How far a boarding rope dips in the middle, as a fraction of its length (0.06 = a 20 block rope dips 1.2).",
                     "Keep it small: players can walk along the ropes, and a deep dip makes the ends too steep.")
            .defineInRange("raidRopeSag", 0.06, 0.0, 0.3);
    public static final ModConfigSpec.DoubleValue RAID_ANCHOR_SECONDS = B
            .comment("Seconds to break a boarding connector (either end of a rope), whatever the tool. Creative is instant.")
            .defineInRange("raidAnchorSeconds", 3.0, 0.5, 60.0);
    public static final ModConfigSpec.DoubleValue RAID_ANCHOR_FATIGUE_SECONDS = B
            .comment("Seconds to break one with Mining Fatigue on you (the raiders' witch throws it). Milk clears it.")
            .defineInRange("raidAnchorFatigueSeconds", 10.0, 0.5, 120.0);
    public static final ModConfigSpec.IntValue RAID_LOST_DISTANCE = B
            .comment("A raider this many blocks clear of both ships (fallen or knocked off) is no longer part of the raid:",
                     "it's removed and counts as dead for the raid bar.")
            .defineInRange("raidLostDistance", 32, 8, 256);
    public static final ModConfigSpec.BooleanValue RAID_NATURAL = B
            .comment("Raids start on their own against players on a ship high enough (see raidMinY). false = only by command",
                     "(and Bad Omen still does nothing).")
            .define("naturalRaids", true);
    public static final ModConfigSpec.DoubleValue RAID_AVERAGE_MINUTES = B
            .comment("Average minutes of time on a ship, high enough, between raids for one player. The chance starts at zero",
                     "after a raid and rises the longer you go without one, so raids are rare at first and close to certain",
                     "after about twice this. Each player keeps their own clock; any one of a crew can bring a raid on the",
                     "whole ship, so a crew of several is raided more often (2 players about every 42 min, 3 about every 35).")
            .defineInRange("raidAverageMinutes", 60.0, 5.0, 1440.0);
    public static final ModConfigSpec.IntValue RAID_MIN_Y = B
            .comment("Raids only start on players at or above this height in the Overworld (and other dimensions), so low",
                     "transport and test flights are left alone. The clouds are at 224.")
            .defineInRange("raidMinY", 230, -64, 448);
    public static final ModConfigSpec.IntValue RAID_MIN_Y_ETHERIC = B
            .comment("The same height in the Etheric Skies, where the islands sit between Y 60 and 196 (0 = any height).")
            .defineInRange("raidMinYEtheric", 0, -64, 448);
    public static final ModConfigSpec.BooleanValue RAID_BAD_OMEN = B
            .comment("A player with Bad Omen (an Ominous Bottle) on a ship high enough brings a raid at once, using up the effect.")
            .define("raidBadOmen", true);
    public static final ModConfigSpec.IntValue RAID_MAX_ACTIVE = B
            .comment("Most raids at once on the whole server (each raider ship is another physics body).")
            .defineInRange("raidMaxActive", 2, 1, 16);

    static { B.pop(); B.comment("Nether portal keys: a portal frame gets a keyhole and won't light until an Obsidian Key is used on it.").push("netherKeys"); }

    public static final ModConfigSpec.BooleanValue KEYHOLES = B
            .comment("Nether portals need a key. false = portals light as normal.")
            .define("keyholes", true);
    public static final ModConfigSpec.BooleanValue FREE_IN_NETHER = B
            .comment("Portals built in the Nether light without a key (so nobody is stranded there).")
            .define("freeInNether", true);

    static { B.pop(); }

    public static final ModConfigSpec SPEC = B.build();

    public static int runMinutes(int tier) {
        return switch (tier) {
            case 3 -> RUN_MINUTES_T3.get();
            case 2 -> RUN_MINUTES_T2.get();
            default -> RUN_MINUTES_T1.get();
        };
    }

    private Config() {}
}
