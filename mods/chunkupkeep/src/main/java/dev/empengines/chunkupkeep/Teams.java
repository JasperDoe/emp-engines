package dev.empengines.chunkupkeep;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Teams {
    public static Optional<Team> of(ServerPlayer player) {
        return FTBTeamsAPI.api().isManagerLoaded() ? FTBTeamsAPI.api().getManager().getTeamForPlayer(player) : Optional.empty();
    }

    public static UUID idOf(ServerPlayer player) {
        return of(player).map(Team::getId).orElse(player.getUUID());
    }

    public static Optional<Team> byId(UUID id) {
        return FTBTeamsAPI.api().isManagerLoaded() ? FTBTeamsAPI.api().getManager().getTeamByID(id) : Optional.empty();
    }

    /** Who pays the team's rent: the team owner. */
    public static UUID payerOf(UUID teamId) {
        return byId(teamId).map(Team::getOwner).orElse(teamId);
    }

    public static Collection<ServerPlayer> online(UUID teamId) {
        return byId(teamId).map(t -> (Collection<ServerPlayer>) t.getOnlineMembers()).orElse(List.of());
    }

    private Teams() {}
}
