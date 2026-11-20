package dev.empengines.chunkupkeep.economy;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.empengines.chunkupkeep.Config;
import dev.empengines.chunkupkeep.LedgerData;
import dev.empengines.chunkupkeep.Teams;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Once per real day, charges each team's owner for their force-loaded chunks.
 * If they can't pay, the chunks stop being force-loaded (the claims stay).
 */
public final class Rent {
    private static int ticks;

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (++ticks % 1200 != 0) return;   // check once a minute
        MinecraftServer server = e.getServer();
        if (!FTBChunksAPI.api().isManagerLoaded()) return;
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        LedgerData data = LedgerData.get(server);
        int rent = Config.FORCE_LOAD_RENT.get();

        Map<UUID, Collection<ClaimedChunk>> byTeam = FTBChunksAPI.api().getManager().getClaimedChunksByTeam(ClaimedChunk::isForceLoaded);
        for (Map.Entry<UUID, Collection<ClaimedChunk>> entry : byTeam.entrySet()) {
            UUID team = entry.getKey();
            List<ClaimedChunk> loaded = new ArrayList<>(entry.getValue());
            if (loaded.isEmpty() || !data.rentDue(team, today)) continue;
            int bill = rent * loaded.size();
            UUID payer = Teams.payerOf(team);
            if (Bank.charge(payer, bill)) {
                data.markRentPaid(team, today);
                for (ServerPlayer p : Teams.online(team))
                    p.sendSystemMessage(Component.translatable("chunkupkeep.msg.rent_paid", Bank.format(bill), loaded.size()));
            } else {
                for (ClaimedChunk c : loaded) c.unload(server.createCommandSourceStack());
                data.markRentPaid(team, today);
                for (ServerPlayer p : Teams.online(team))
                    p.sendSystemMessage(Component.translatable("chunkupkeep.msg.rent_failed", Bank.format(bill)));
            }
        }
    }

    private Rent() {}
}
