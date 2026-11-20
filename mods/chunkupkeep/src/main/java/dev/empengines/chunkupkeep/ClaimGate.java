package dev.empengines.chunkupkeep;

import dev.architectury.event.CompoundEventResult;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import net.minecraft.commands.CommandSourceStack;

import java.util.function.Supplier;

/**
 * Turns off FTB Chunks' normal claiming and force loading, so claims only happen
 * through a Claim Ledger (which pays for them). Unclaiming stays free.
 */
public final class ClaimGate {
    private static final ThreadLocal<Boolean> ALLOWED = ThreadLocal.withInitial(() -> false);

    public static void register() {
        ClaimedChunkEvent.BEFORE_CLAIM.register(ClaimGate::check);
        ClaimedChunkEvent.BEFORE_LOAD.register(ClaimGate::check);
    }

    private static CompoundEventResult<ClaimResult> check(CommandSourceStack source, ClaimedChunk chunk) {
        if (ALLOWED.get()) return CompoundEventResult.pass();
        if (Config.OPS_BYPASS.get() && source.hasPermission(2)) return CompoundEventResult.pass();
        return CompoundEventResult.interruptFalse(ClaimResult.customProblem("chunkupkeep.use_ledger"));
    }

    /** Runs an FTB Chunks call that the gate should let through. */
    public static <T> T allow(Supplier<T> action) {
        ALLOWED.set(true);
        try {
            return action.get();
        } finally {
            ALLOWED.set(false);
        }
    }

    private ClaimGate() {}
}
