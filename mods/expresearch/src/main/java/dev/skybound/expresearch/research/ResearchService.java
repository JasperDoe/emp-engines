package dev.skybound.expresearch.research;

import com.gabinx.chapters.api.ChaptersAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.skybound.expresearch.ExpResearch;
import dev.skybound.expresearch.net.SyncResearchPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-side research logic: who has what, unlocking, and keeping Chapters stages in step. */
public final class ResearchService {

    public static void registerEvents() {
        // FTB Teams fires this once the player's team is known, so stages can be granted reliably.
        TeamEvent.PLAYER_LOGGED_IN.register(e -> grantAndSync(e.getPlayer()));
        TeamEvent.PLAYER_JOINED_PARTY.register(e -> grantAndSync(e.getPlayer()));
    }

    /** The FTB team a player belongs to (their personal team if they're not in a party). */
    public static UUID teamOf(ServerPlayer player) {
        if (FTBTeamsAPI.api().isManagerLoaded()) {
            Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
            if (team.isPresent()) return team.get().getId();
        }
        return player.getUUID();
    }

    public static Collection<ServerPlayer> onlineMembers(MinecraftServer server, UUID team) {
        if (FTBTeamsAPI.api().isManagerLoaded()) {
            Optional<Team> t = FTBTeamsAPI.api().getManager().getTeamByID(team);
            if (t.isPresent()) return t.get().getOnlineMembers();
        }
        ServerPlayer p = server.getPlayerList().getPlayer(team);
        return p == null ? List.of() : List.of(p);
    }

    public static Set<ResourceLocation> unlocked(ServerPlayer player) {
        return TeamResearchData.get(player.server).unlocked(teamOf(player));
    }

    public static boolean parentsMet(ResearchNode node, Set<ResourceLocation> unlocked) {
        return unlocked.containsAll(node.parents());
    }

    /** Unlocks a research for the player's whole team. Returns false if the team already had it. */
    public static boolean unlock(ServerPlayer player, ResourceLocation id) {
        ResearchNode node = ResearchRegistry.node(id);
        if (node == null) return false;
        UUID team = teamOf(player);
        if (!TeamResearchData.get(player.server).add(team, id)) return false;
        for (ServerPlayer member : onlineMembers(player.server, team)) {
            grantStages(member, node);
            member.sendSystemMessage(Component.translatable("expresearch.msg.unlocked", player.getDisplayName(), node.title()));
            sync(member);
        }
        return true;
    }

    public static boolean revoke(ServerPlayer player, ResourceLocation id) {
        ResearchNode node = ResearchRegistry.node(id);
        UUID team = teamOf(player);
        if (!TeamResearchData.get(player.server).remove(team, id)) return false;
        if (node != null) {
            for (ServerPlayer member : onlineMembers(player.server, team)) {
                node.stages().forEach(s -> ChaptersAPI.removeStage(member, s));
                sync(member);
            }
        }
        return true;
    }

    private static void grantStages(ServerPlayer player, ResearchNode node) {
        for (ResourceLocation stage : node.stages()) {
            if (!ChaptersAPI.hasStage(player, stage)) ChaptersAPI.addStage(player, stage);
        }
    }

    /** Gives a player every stage their team's research grants, then sends them the tree. */
    public static void grantAndSync(ServerPlayer player) {
        for (ResourceLocation id : unlocked(player)) {
            ResearchNode node = ResearchRegistry.node(id);
            if (node != null) grantStages(player, node);
        }
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncResearchPayload(
                ResearchRegistry.categories(), ResearchRegistry.nodes(), new ArrayList<>(unlocked(player))));
    }

    /** After a datapack reload, resend the tree to everyone online. */
    public static void resyncAll() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> server.getPlayerList().getPlayers().forEach(ResearchService::grantAndSync));
    }

    // --- XP and samples -------------------------------------------------------

    /** Total experience points a player has, from their level and progress bar. */
    public static int totalXp(Player p) {
        return pointsForLevel(p.experienceLevel) + Math.round(p.experienceProgress * p.getXpNeededForNextLevel());
    }

    public static int pointsForLevel(int level) {
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    public static int countItem(Player p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    public static boolean hasSamples(Player p, ResearchNode node) {
        for (ResearchNode.Sample s : node.samples()) {
            Item item = BuiltInRegistries.ITEM.get(s.item());
            if (countItem(p, item) < s.count()) return false;
        }
        return true;
    }

    public static void takeSamples(Player p, ResearchNode node) {
        for (ResearchNode.Sample s : node.samples()) {
            Item item = BuiltInRegistries.ITEM.get(s.item());
            int left = s.count();
            for (ItemStack stack : p.getInventory().items) {
                if (left <= 0) break;
                if (!stack.is(item)) continue;
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        p.getInventory().setChanged();
    }

    static void log(String msg, Object... args) { ExpResearch.LOGGER.info(msg, args); }

    private ResearchService() {}
}
