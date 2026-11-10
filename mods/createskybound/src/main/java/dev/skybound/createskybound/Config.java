package dev.skybound.createskybound;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** serverconfig/createskybound-server.toml */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue INPUT_STRESS = B
            .comment("Stress impact of each launcher shaft input (a wrenched ring casing), per RPM.")
            .defineInRange("inputStressImpact", 32.0, 0.0, 4096.0);
    public static final ModConfigSpec.IntValue BASE_CHARGE = B
            .comment("Charge every launch needs, before ship weight is added.")
            .defineInRange("baseCharge", 20000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue CHARGE_PER_MASS = B
            .comment("Extra charge needed per unit of ship mass (Sable mass).")
            .defineInRange("chargePerMass", 40.0, 0.0, 1_000_000.0);
    public static final ModConfigSpec.IntValue MAX_PLATFORM = B
            .comment("Largest launch platform, in blocks along one side (ring included).")
            .defineInRange("maxPlatformSize", 33, 5, 129);
    public static final ModConfigSpec.IntValue CLOUD_HEIGHT = B
            .comment("Height of the cloud layer the launch slows through (the pack's shader clouds sit at 224).")
            .defineInRange("cloudHeight", 224, 0, 2000);
    public static final ModConfigSpec.IntValue LAUNCH_APEX = B
            .comment("Height the thrown part of a launch carries the ship to, before it drifts and jumps.",
                     "Kept a little above cloudHeight so the ship breaks out over the clouds.")
            .defineInRange("launchApex", 256, 0, 2000);
    public static final ModConfigSpec.IntValue RUN_MINUTES_T1 = B.defineInRange("runMinutesTier1", 30, 1, 600);
    public static final ModConfigSpec.IntValue RUN_MINUTES_T2 = B.defineInRange("runMinutesTier2", 50, 1, 600);
    public static final ModConfigSpec.IntValue RUN_MINUTES_T3 = B.defineInRange("runMinutesTier3", 70, 1, 600);
    public static final ModConfigSpec.IntValue WARNING_SECONDS = B
            .comment("When the 'DIMENSION DESTABILISING' warnings start, in seconds before collapse.")
            .defineInRange("warningSeconds", 300, 10, 3600);
    public static final ModConfigSpec.IntValue EXIT_HEIGHT = B
            .comment("Falling below this height in the Chaotic Skies drops you back into the Overworld sky.")
            .defineInRange("exitHeight", 8, -64, 256);
    public static final ModConfigSpec.IntValue OVERWORLD_DROP_HEIGHT = B
            .comment("Height you reappear at in the Overworld after falling out of the Chaotic Skies.")
            .defineInRange("overworldDropHeight", 400, 0, 2000);

    // Sky Raiders (prototype: raids start from /skyraid start)
    public static final ModConfigSpec.IntValue RAID_ROPES = B
            .comment("Boarding ropes a raider ship fires (1 to 3).")
            .defineInRange("raidRopes", 2, 1, 3);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RAID_SHIPS = B
            .comment("Raider ships, as structure ids from installed mods; one is picked at random per raid.",
                     "If none can be placed, a small sloop built in code is used instead.")
            .defineListAllowEmpty("raidShips", List.of("cr_aero_structures:pillager_airballoon",
                    "aeronauticsdiscovery:airplane_pillager", "cr_aero_structures:pillager_airship"), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.IntValue RAID_GAP = B
            .comment("Gap in blocks between the raider ship and your hull while it is alongside.")
            .defineInRange("raidGap", 5, 2, 16);
    public static final ModConfigSpec.IntValue RAID_STACKS = B
            .comment("Stacks each looter can carry off.")
            .defineInRange("raidStacks", 3, 0, 27);
    public static final ModConfigSpec.IntValue RAID_OVERWHELM = B
            .comment("The crew is overwhelmed (looters go for the chests) when raiders on deck outnumber players aboard by this many.")
            .defineInRange("raidOverwhelmMargin", 3, 1, 20);

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
