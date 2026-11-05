package dev.skybound.createskybound;

import net.neoforged.neoforge.common.ModConfigSpec;

/** serverconfig/createskybound-server.toml */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue COMPUTER_STRESS = B
            .comment("Stress impact of the Vector Planar Computer, per RPM (like other Create machines).")
            .defineInRange("computerStressImpact", 32.0, 0.0, 4096.0);
    public static final ModConfigSpec.IntValue BASE_CHARGE = B
            .comment("Charge every launch needs, before ship weight is added.")
            .defineInRange("baseCharge", 20000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue CHARGE_PER_MASS = B
            .comment("Extra charge needed per unit of ship mass (Sable mass).")
            .defineInRange("chargePerMass", 40.0, 0.0, 1_000_000.0);
    public static final ModConfigSpec.IntValue MAX_PLATFORM = B
            .comment("Largest launch platform, in blocks along one side (ring included).")
            .defineInRange("maxPlatformSize", 33, 5, 129);
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
