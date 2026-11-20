package dev.empengines.chunkupkeep;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.empengines.chunkupkeep.block.ClaimLedgerBlockEntity;
import dev.empengines.chunkupkeep.economy.Bank;
import dev.empengines.chunkupkeep.net.LedgerSnapshotPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** Builds the claim map for the screen and carries out claim, unclaim and force-load clicks. */
public final class LedgerService {
    public static final int VIEW_RADIUS = 7;   // 15 x 15 chunks on screen
    public static final byte FREE = 0, OWN = 1, OWN_LOADED = 2, OTHER = 3, OUT_OF_REACH = 4;
    public static final int CLAIM = 0, UNCLAIM = 1, TOGGLE_LOAD = 2;

    public static void open(ServerPlayer player, BlockPos ledger) {
        ChunkPos c = new ChunkPos(ledger);
        send(player, ledger, c.x, c.z);
    }

    public static void send(ServerPlayer player, BlockPos ledger, int viewX, int viewZ) {
        ClaimedChunkManager mgr = FTBChunksAPI.api().getManager();
        UUID team = Teams.idOf(player);
        ChunkPos centre = new ChunkPos(ledger);
        int reach = Config.REACH.get();
        int size = VIEW_RADIUS * 2 + 1;
        byte[] cells = new byte[size * size];
        for (int dz = -VIEW_RADIUS; dz <= VIEW_RADIUS; dz++) {
            for (int dx = -VIEW_RADIUS; dx <= VIEW_RADIUS; dx++) {
                int cx = viewX + dx, cz = viewZ + dz;
                ClaimedChunk claimed = mgr.getChunk(new ChunkDimPos(player.level().dimension(), cx, cz));
                byte state;
                if (claimed == null) {
                    state = inReach(centre, cx, cz, reach) ? FREE : OUT_OF_REACH;
                } else if (claimed.getTeamData().getTeam().getId().equals(team)) {
                    state = claimed.isForceLoaded() ? OWN_LOADED : OWN;
                } else {
                    state = OTHER;
                }
                cells[(dz + VIEW_RADIUS) * size + (dx + VIEW_RADIUS)] = state;
            }
        }
        ChunkTeamData data = mgr.getOrCreateData(player);
        PacketDistributor.sendToPlayer(player, new LedgerSnapshotPayload(ledger, viewX, viewZ, VIEW_RADIUS, cells,
                Bank.balance(player.getUUID()), Config.CLAIM_COST.get(), Config.FORCE_LOAD_RENT.get(),
                Config.UNCLAIM_REFUND_PERCENT.get(), data.getClaimedChunks().size(), data.getForceLoadedChunks().size()));
    }

    public static boolean inReach(ChunkPos centre, int cx, int cz, int reach) {
        return Math.abs(cx - centre.x) <= reach && Math.abs(cz - centre.z) <= reach;
    }

    public static void handle(ServerPlayer player, BlockPos ledger, int cx, int cz, int action, int viewX, int viewZ) {
        if (!(player.level().getBlockEntity(ledger) instanceof ClaimLedgerBlockEntity be)) return;
        UUID team = Teams.idOf(player);
        if (!team.equals(be.team())) return;
        if (player.distanceToSqr(ledger.getX() + 0.5, ledger.getY() + 0.5, ledger.getZ() + 0.5) > 64) return;

        ClaimedChunkManager mgr = FTBChunksAPI.api().getManager();
        ChunkTeamData data = mgr.getOrCreateData(player);
        ChunkDimPos pos = new ChunkDimPos(player.level().dimension(), cx, cz);
        ChunkPos centre = new ChunkPos(ledger);
        Component msg = null;

        switch (action) {
            case CLAIM -> {
                int cost = Config.CLAIM_COST.get();
                if (!inReach(centre, cx, cz, Config.REACH.get())) msg = Component.translatable("chunkupkeep.msg.out_of_reach");
                else if (mgr.getChunk(pos) != null) msg = Component.translatable("chunkupkeep.msg.taken");
                else if (Bank.balance(player.getUUID()) < cost) msg = Component.translatable("chunkupkeep.msg.poor", Bank.format(cost));
                else {
                    ClaimResult r = ClaimGate.allow(() -> data.claim(player.createCommandSourceStack(), pos, false));
                    if (r.isSuccess()) {
                        Bank.charge(player.getUUID(), cost);
                        msg = Component.translatable("chunkupkeep.msg.claimed", cx, cz, Bank.format(cost));
                    } else {
                        msg = r.getMessage();
                    }
                }
            }
            case UNCLAIM -> {
                ClaimedChunk c = mgr.getChunk(pos);
                if (c == null || !c.getTeamData().getTeam().getId().equals(team)) msg = Component.translatable("chunkupkeep.msg.not_yours_chunk");
                else {
                    ClaimResult r = data.unclaim(player.createCommandSourceStack(), pos, false);
                    if (r.isSuccess()) {
                        int refund = Config.CLAIM_COST.get() * Config.UNCLAIM_REFUND_PERCENT.get() / 100;
                        Bank.pay(player.getUUID(), refund);
                        msg = Component.translatable("chunkupkeep.msg.unclaimed", cx, cz);
                    } else msg = r.getMessage();
                }
            }
            case TOGGLE_LOAD -> {
                ClaimedChunk c = mgr.getChunk(pos);
                if (c == null || !c.getTeamData().getTeam().getId().equals(team)) msg = Component.translatable("chunkupkeep.msg.not_yours_chunk");
                else if (c.isForceLoaded()) {
                    ClaimResult r = data.unForceLoad(player.createCommandSourceStack(), pos, false);
                    msg = r.isSuccess() ? Component.translatable("chunkupkeep.msg.unloaded", cx, cz) : r.getMessage();
                } else {
                    ClaimResult r = ClaimGate.allow(() -> data.forceLoad(player.createCommandSourceStack(), pos, false));
                    msg = r.isSuccess()
                            ? Component.translatable("chunkupkeep.msg.loaded", cx, cz, Bank.format(Config.FORCE_LOAD_RENT.get()))
                            : r.getMessage();
                }
            }
            default -> { }
        }
        if (msg != null) player.displayClientMessage(msg, true);
        send(player, ledger, viewX, viewZ);
    }

    private LedgerService() {}
}
