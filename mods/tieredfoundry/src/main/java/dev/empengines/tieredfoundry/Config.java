package dev.empengines.tieredfoundry;

import net.neoforged.neoforge.common.ModConfigSpec;

/** serverconfig/tieredfoundry-server.toml (per world). Sent to clients, so the screen and EMI show the same numbers. */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static { B.comment("Ore yield: raw ores, ore blocks, raw metal and Create's crushed ores melt to this many times an",
            "ingot's worth. Everything else (ingots, nuggets, plates, blocks) melts 1:1.").push("yield"); }

    public static final ModConfigSpec.DoubleValue YIELD_T1 = B.comment("Tier 1: vanilla bricks.").defineInRange("tier1", 1.2, 1.0, 4.0);
    public static final ModConfigSpec.DoubleValue YIELD_T15 = B.comment("Tier 1.5: vanilla bricks with a running fan.").defineInRange("tier1_5", 1.4, 1.0, 4.0);
    public static final ModConfigSpec.DoubleValue YIELD_T2 = B.comment("Tier 2: fire bricks.").defineInRange("tier2", 1.6, 1.0, 4.0);

    static { B.pop(); B.comment("Heat, in degrees.").push("heat"); }

    public static final ModConfigSpec.IntValue CAP_T1 = B.comment("The most a vanilla brick foundry can take.").defineInRange("brickMax", 1300, 100, 5000);
    public static final ModConfigSpec.IntValue CAP_T2 = B.comment("The most a fire brick foundry can take.").defineInRange("fireBrickMax", 1900, 100, 5000);
    public static final ModConfigSpec.IntValue LAVA_HEAT = B.comment("What lava heaters reach.").defineInRange("lavaHeat", 1600, 100, 5000);
    public static final ModConfigSpec.IntValue LAVA_USE = B.comment("mB of lava each lit lava heater burns per second.").defineInRange("lavaPerSecond", 10, 0, 1000);
    public static final ModConfigSpec.IntValue FAN_MAX = B.comment("Heat a fan adds at full speed (256 RPM); slower fans add less.").defineInRange("fanHeat", 300, 0, 2000);
    public static final ModConfigSpec.DoubleValue WARM_RATE = B.comment("Degrees per second the foundry warms when it has enough heaters.").defineInRange("warmPerSecond", 40.0, 1.0, 1000.0);
    public static final ModConfigSpec.DoubleValue COOL_RATE = B.comment("Degrees per second it cools.").defineInRange("coolPerSecond", 20.0, 1.0, 1000.0);
    public static final ModConfigSpec.IntValue FLOOR_PER_HEATER = B.comment("The floor is all heaters; fuel and lava use go up by one heater's worth per this many heaters.").defineInRange("floorPerHeater", 9, 1, 49);
    public static final ModConfigSpec.DoubleValue FIRE_BRICK_LOSS = B.comment("Heat loss of a fire brick foundry, as a fraction of a clay brick one.").defineInRange("fireBrickHeatLoss", 0.6, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue FAN_LOSS = B.comment("Each fan blowing in divides heat loss by (1 + this × fans).").defineInRange("fanHeatLossCut", 0.4, 0.0, 10.0);

    static { B.pop(); B.comment("Melting.").push("melting"); }

    public static final ModConfigSpec.DoubleValue MELT_SLOW = B.comment("Seconds an item takes at exactly its melting point.").defineInRange("slowSeconds", 30.0, 0.5, 600.0);
    public static final ModConfigSpec.DoubleValue MELT_FAST = B.comment("Seconds it takes at 600 degrees or more above it.").defineInRange("fastSeconds", 5.0, 0.5, 600.0);
    public static final ModConfigSpec.IntValue SLOTS = B.comment("Most items a foundry holds (one per interior block up to this).").defineInRange("maxSlots", 63, 9, 63);
    public static final ModConfigSpec.IntValue MB_PER_BLOCK = B.comment("Molten capacity per interior block, in mB.").defineInRange("mbPerBlock", 1000, 100, 100000);

    static { B.pop(); B.comment("Pouring and casting.").push("casting"); }

    public static final ModConfigSpec.IntValue TAP_RATE = B.comment("mB a tap pours per tick.").defineInRange("tapPerTick", 20, 1, 1000);

    static { B.pop(); }

    public static final ModConfigSpec SPEC = B.build();

    /** A value that is safe to read before the world's config has loaded (EMI can ask early). */
    public static double safe(ModConfigSpec.DoubleValue v) {
        try { return v.get(); } catch (IllegalStateException e) { return v.getDefault(); }
    }

    public static int safe(ModConfigSpec.IntValue v) {
        try { return v.get(); } catch (IllegalStateException e) { return v.getDefault(); }
    }

    public static double yield(float tier) {
        if (tier >= 2) return YIELD_T2.get();
        if (tier >= 1.5F) return YIELD_T15.get();
        return YIELD_T1.get();
    }

    private Config() {}
}
