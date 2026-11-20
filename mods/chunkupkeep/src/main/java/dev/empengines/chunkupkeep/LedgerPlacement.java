package dev.empengines.chunkupkeep;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;
import java.util.UUID;

/** Enforces the ledger-per-team limit. */
public final class LedgerPlacement {
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!e.getPlacedBlock().is(ModRegistry.CLAIM_LEDGER.get())) return;
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        UUID team = Teams.idOf(sp);
        LedgerData data = LedgerData.get(sp.server);
        // Forget ledgers that no longer exist (for example removed with commands).
        List<GlobalPos> existing = List.copyOf(data.ledgers(team));
        for (GlobalPos p : existing) {
            var level = sp.server.getLevel(p.dimension());
            if (level != null && level.isLoaded(p.pos()) && !level.getBlockState(p.pos()).is(ModRegistry.CLAIM_LEDGER.get()))
                data.removeLedger(p);
        }
        if (data.ledgers(team).size() >= Config.LEDGERS_PER_TEAM.get()) {
            e.setCanceled(true);
            sp.displayClientMessage(Component.translatable("chunkupkeep.msg.limit", Config.LEDGERS_PER_TEAM.get()), true);
        }
    }

    private LedgerPlacement() {}
}
