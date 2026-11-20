package dev.empengines.engines.raid;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.empengines.engines.Config;
import dev.empengines.engines.EmpyreanEngines;
import dev.empengines.engines.launch.SableBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs Sky Raiders: active raids, the gallery's marker blocks, clean-up after a restart (raids are not
 * saved; a restart ends them), and the protection of the boarding ropes:
 *
 * <ul>
 *   <li>the rope itself can't be cut (it's drawn, not a block or a Simulated rope), nor sheared off a connector</li>
 *   <li>the connectors at either end can't be blown up</li>
 *   <li>breaking a connector takes a fixed time whatever the tool: 3 seconds, or 10 with Mining Fatigue (the witch's potion)</li>
 * </ul>
 */
public final class RaidManager {
    public static final String TAG = "emp_engines_raider";
    static final List<Raid> RAIDS = new ArrayList<>();
    private static final Set<ServerLevel> CLEANED = new HashSet<>();
    /** The raid whose crew is being spawned right now (not in RAIDS yet). */
    static Raid starting;

    public static final Component ROPE_HOLDS = Component.literal("The boarding line won't give - break the anchor!").withStyle(ChatFormatting.RED);

    // --- raids ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        for (Raid raid : new ArrayList<>(RAIDS)) {
            try {
                raid.tick();
            } catch (Exception ex) {
                EmpyreanEngines.LOGGER.error("Sky Raiders: raid {} crashed, ending it", raid.id, ex);
                raid.end("error", false);
            }
            if (raid.phase == Raid.Phase.DONE) RAIDS.remove(raid);
        }
    }

    static Raid start(ServerLevel level, ServerSubLevel target, RaiderShipDef def, String[] why) {
        Raid raid = Raid.start(level, target, def, why);
        if (raid != null) RAIDS.add(raid);
        return raid;
    }

    static Raid raidOn(ServerSubLevel ship) {
        for (Raid r : RAIDS) if (r.playerShip.equals(ship.getUniqueId())) return r;
        return null;
    }

    static Raid raidWithConnector(ServerLevel level, BlockPos pos) {
        for (Raid r : RAIDS) if (r.level == level && r.isBoardingConnector(pos)) return r;
        return null;
    }

    /** Sends the boarding connectors and ropes to everyone in that dimension. */
    static void syncConnectors(ServerLevel level) {
        PacketDistributor.sendToPlayersInDimension(level, sync(level));
    }

    private static BoardingSync sync(ServerLevel level) {
        List<BoardingRope> ropes = new ArrayList<>();
        for (Raid r : RAIDS) if (r.level == level) r.allRopes(ropes);
        return new BoardingSync(connectors(level), ropes);
    }

    /** True if an entity is standing on (or right by) a boarding rope: it isn't "flying" there. */
    public static boolean onBoardingRope(Entity e) {
        if (RAIDS.isEmpty() || !(e.level() instanceof ServerLevel level)) return false;
        List<BoardingRope> ropes = new ArrayList<>();
        for (Raid r : RAIDS) if (r.level == level) r.allRopes(ropes);
        for (BoardingRope rope : ropes) {
            java.util.List<net.minecraft.world.phys.Vec3> pts = rope.points(level, dev.ryanhcode.sable.sublevel.SubLevel::logicalPose);
            double[] at = BoardingRope.nearest(pts, e.position(), 1.5);
            if (at != null && Math.abs(BoardingRope.at(pts, at).y - e.getY()) < 2) return true;
        }
        return false;
    }

    private static List<BlockPos> connectors(ServerLevel level) {
        List<BlockPos> out = new ArrayList<>();
        for (Raid r : RAIDS) if (r.level == level) r.allConnectors(out);
        return out;
    }

    /** Once per world, shortly after it starts: delete raider ships from before a restart. */
    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || CLEANED.contains(level) || level.getGameTime() % 100 != 0) return;
        CLEANED.add(level);
        for (ServerSubLevel ship : SableBridge.allShips(level)) {
            boolean active = RAIDS.stream().anyMatch(r -> r.raider.ship == ship);
            if (!active && RaiderShip.isRaiderShip(ship)) {
                EmpyreanEngines.LOGGER.info("Sky Raiders: removing a leftover raider ship");
                SableBridge.remove(ship);
            }
        }
    }

    /** Raiders loaded back in after a restart belong to no raid any more: remove them. */
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        Entity entity = e.getEntity();
        if (e.getLevel().isClientSide) return;
        if (entity instanceof ServerPlayer p && e.getLevel() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayer(p, sync(level));
            return;
        }
        if (!entity.getTags().contains(TAG)) return;
        if (starting != null && starting.owns(entity.getUUID())) return;
        for (Raid r : RAIDS) {
            if (r.owns(entity.getUUID())) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) r.rebind(mob);
                return;
            }
        }
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (Raid raid : RAIDS) raid.end("server stopping", false);
        RAIDS.clear();
        CLEANED.clear();
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        RaidCommands.register(e.getDispatcher());
    }

    // --- protecting the boarding ropes ----------------------------------------------------------

    private static boolean isBoarding(Player p, BlockPos pos) {
        if (p.level().isClientSide) return BoardingSync.CLIENT.contains(pos);
        return p.level() instanceof ServerLevel level && raidWithConnector(level, pos) != null;
    }

    /** A fixed time to break a boarding connector, whatever the tool: 3 s, or 10 s with Mining Fatigue. */
    @SubscribeEvent
    public static void breakSpeed(PlayerEvent.BreakSpeed e) {
        Optional<BlockPos> at = e.getPosition();
        Player p = e.getEntity();
        if (at.isEmpty() || p.isCreative() || !isBoarding(p, at.get())) return;
        if (p.level() instanceof ServerLevel level) {
            Raid raid = raidWithConnector(level, at.get());
            if (raid != null) raid.breaking.put(p.getUUID(), level.getGameTime());
        }
        BlockState s = e.getState();
        float hardness = s.getDestroySpeed(p.level(), at.get());
        if (hardness <= 0) return;
        int divisor = s.requiresCorrectToolForDrops() && !p.hasCorrectToolForDrops(s) ? 100 : 30;
        double seconds = p.hasEffect(MobEffects.DIG_SLOWDOWN) ? seconds(true) : seconds(false);
        e.setNewSpeed((float) (hardness * divisor / (seconds * 20)));
    }

    private static double seconds(boolean fatigue) {
        try {
            return fatigue ? Config.RAID_ANCHOR_FATIGUE_SECONDS.get() : Config.RAID_ANCHOR_SECONDS.get();
        } catch (IllegalStateException notLoaded) {
            return fatigue ? 10 : 3;
        }
    }

    /** Shears can't cut a boarding rope off its connector. */
    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getItemStack().getItem() instanceof ShearsItem) || !isBoarding(e.getEntity(), e.getPos())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
        if (!e.getLevel().isClientSide) e.getEntity().displayClientMessage(ROPE_HOLDS, true);
    }

    /** Explosions don't take boarding connectors with them. */
    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level) || RAIDS.isEmpty()) return;
        e.getAffectedBlocks().removeIf(pos -> raidWithConnector(level, pos) != null);
    }

    // --- the gallery's marker blocks ------------------------------------------------------------

    @SubscribeEvent
    public static void placed(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            var held = p.getItemInHand(hand);
            MarkerKit.Type t = MarkerKit.typeOf(held);
            if (t != null && e.getPlacedBlock().is(t.block)) {
                Gallery.get(level).placed(p, e.getPos(), held);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void broken(BlockEvent.BreakEvent e) {
        if (e.getLevel() instanceof ServerLevel level) Gallery.get(level).broken(e.getPos());
    }

    private RaidManager() {}
}
