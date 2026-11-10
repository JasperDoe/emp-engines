package dev.skybound.createskybound.raid;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.skybound.createskybound.CreateSkybound;
import dev.skybound.createskybound.launch.SableBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs Sky Raiders: active raids, the /skyraid test commands, and clean-up of raider ships and
 * raiders left over from a restart (raids are not saved; a restart ends them).
 *
 * Prototype: raids only start from /skyraid. Natural spawning comes after the in-game tests.
 */
public final class RaidManager {
    public static final String TAG = "createskybound_raider";
    private static final List<Raid> RAIDS = new ArrayList<>();
    private static final Set<ServerLevel> CLEANED = new HashSet<>();

    // --- events -----------------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        for (Raid raid : new ArrayList<>(RAIDS)) {
            try {
                raid.tick();
            } catch (Exception ex) {
                CreateSkybound.LOGGER.error("Sky Raiders: raid {} crashed, ending it", raid.id, ex);
                raid.end("error");
            }
            if (raid.phase == Raid.Phase.DONE) RAIDS.remove(raid);
        }
    }

    /** Once per world, shortly after it starts: delete raider ships from before a restart. */
    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || CLEANED.contains(level) || level.getGameTime() % 100 != 0) return;
        CLEANED.add(level);
        for (ServerSubLevel ship : SableBridge.allShips(level)) {
            boolean active = RAIDS.stream().anyMatch(r -> r.sloop.ship() == ship);
            if (!active && RaiderShip.isRaiderShip(ship)) {
                CreateSkybound.LOGGER.info("Sky Raiders: removing a leftover raider ship");
                SableBridge.remove(ship);
            }
        }
    }

    /** Raiders loaded back in after a restart belong to no raid any more: remove them. */
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        Entity entity = e.getEntity();
        if (e.getLevel().isClientSide || !entity.getTags().contains(TAG)) return;
        if (RAIDS.stream().noneMatch(r -> r.owns(entity.getUUID()))) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (Raid raid : RAIDS) raid.end("server stopping");
        RAIDS.clear();
        CLEANED.clear();
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    // --- commands ---------------------------------------------------------------------------

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("skyraid").requires(s -> s.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(c -> start(c, 4, null))
                        .then(Commands.argument("crew", IntegerArgumentType.integer(1, 12))
                                .executes(c -> start(c, IntegerArgumentType.getInteger(c, "crew"), null))
                                .then(Commands.argument("ship", ResourceLocationArgument.id())
                                        .executes(c -> start(c, IntegerArgumentType.getInteger(c, "crew"), ResourceLocationArgument.getId(c, "ship"))))))
                .then(Commands.literal("overwhelm").executes(RaidManager::overwhelm))
                .then(Commands.literal("status").executes(RaidManager::status))
                .then(Commands.literal("stop").executes(RaidManager::stop)));
    }

    /** The ship the player is standing on, or the one right around them. */
    private static ServerSubLevel shipOf(ServerPlayer p) {
        ServerSubLevel s = SableBridge.shipUnder(p);
        if (s != null && !RaiderShip.isRaiderShip(s)) return s;
        s = SableBridge.findShip(p.serverLevel(), new AABB(p.blockPosition()).inflate(2, 3, 2));
        return s != null && !RaiderShip.isRaiderShip(s) ? s : null;
    }

    private static Raid raidOn(ServerSubLevel ship) {
        for (Raid r : RAIDS) if (r.playerShip.equals(ship.getUniqueId())) return r;
        return null;
    }

    private static int start(CommandContext<CommandSourceStack> c, int crew, ResourceLocation shipId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerSubLevel ship = shipOf(p);
        if (ship == null) { c.getSource().sendFailure(Component.literal("Stand on a ship first.")); return 0; }
        if (raidOn(ship) != null) { c.getSource().sendFailure(Component.literal("That ship is already being raided.")); return 0; }
        String[] why = new String[1];
        Raid raid = Raid.start(p.serverLevel(), ship, crew, shipId, why);
        if (raid == null) { c.getSource().sendFailure(Component.literal("No raid: " + why[0])); return 0; }
        RAIDS.add(raid);
        c.getSource().sendSuccess(() -> Component.literal("Raid started with " + crew + " raiders and " + raid.ropes.size() + " ropes."), true);
        return 1;
    }

    private static Raid raidHere(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerSubLevel ship = shipOf(c.getSource().getPlayerOrException());
        Raid raid = ship == null ? null : raidOn(ship);
        if (raid == null && RAIDS.size() == 1) raid = RAIDS.get(0);
        if (raid == null) c.getSource().sendFailure(Component.literal("No raid on this ship."));
        return raid;
    }

    private static int overwhelm(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Raid raid = raidHere(c);
        if (raid == null) return 0;
        raid.forceOverwhelm = true;
        c.getSource().sendSuccess(() -> Component.literal("Looters will go for the chests at the next check."), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Raid raid = raidHere(c);
        if (raid == null) return 0;
        c.getSource().sendSuccess(() -> Component.literal(raid.status()), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Raid raid = raidHere(c);
        if (raid == null) return 0;
        raid.discardAll();
        raid.end("stopped by command");
        RAIDS.remove(raid);
        c.getSource().sendSuccess(() -> Component.literal("Raid stopped."), true);
        return 1;
    }

    private RaidManager() {}
}
