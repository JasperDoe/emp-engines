package dev.empengines.chunkupkeep;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config: serverconfig/chunkupkeep-server.toml (prices in spurs: cog = 64, crown = 512). */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CLAIM_COST = B
            .comment("Price to claim one chunk, in spurs (64 = 1 cog).")
            .defineInRange("claimCost", 64, 0, 1_000_000);
    public static final ModConfigSpec.IntValue UNCLAIM_REFUND_PERCENT = B
            .comment("Percent of the claim price refunded when a chunk is unclaimed.")
            .defineInRange("unclaimRefundPercent", 0, 0, 100);
    public static final ModConfigSpec.IntValue FORCE_LOAD_RENT = B
            .comment("Daily rent per force-loaded chunk, in spurs (8 = 1 bevel). Charged once per real day.")
            .defineInRange("forceLoadDailyRent", 8, 0, 1_000_000);
    public static final ModConfigSpec.IntValue REACH = B
            .comment("How many chunks away from the ledger can be claimed, in every direction.")
            .defineInRange("reachChunks", 48, 1, 512);
    public static final ModConfigSpec.IntValue LEDGERS_PER_TEAM = B
            .comment("How many Claim Ledgers one team may place.")
            .defineInRange("ledgersPerTeam", 1, 1, 64);
    public static final ModConfigSpec.IntValue JOIN_BONUS = B
            .comment("One-time money for a player's first join, in spurs (512 = 1 crown).")
            .defineInRange("joinBonus", 512, 0, 1_000_000);
    public static final ModConfigSpec.IntValue DAILY_BONUS = B
            .comment("Money for the first login of each real day, in spurs (16 = 1 sprocket).")
            .defineInRange("dailyLoginBonus", 16, 0, 1_000_000);
    public static final ModConfigSpec.BooleanValue OPS_BYPASS = B
            .comment("Let operators claim with FTB Chunks' own map and commands without a ledger.")
            .define("opsBypassLedger", true);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
