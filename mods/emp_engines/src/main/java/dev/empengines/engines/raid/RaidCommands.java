package dev.empengines.engines.raid;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.empengines.engines.launch.SableBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /skyraid}: the raider ship gallery, configuring ships with the marker kit, the ship list, and
 * starting and steering test raids. Operators only.
 *
 * <pre>
 * /skyraid gallery                      every ship, in a row in the sky near you, in quartz outlines with name tags
 * /skyraid gallery &lt;ship&gt;               just that one
 * /skyraid gallery tp &lt;ship&gt;            go to it
 * /skyraid gallery clear                take the gallery down
 *
 * /skyraid config &lt;ship&gt;                start configuring: places it if needed, gives the marker kit, puts saved markers back
 * /skyraid config &lt;ship&gt; save           save the markers, take the marker blocks away, show the stats
 * /skyraid config &lt;ship&gt; cancel         stop without saving
 * /skyraid config &lt;ship&gt; reset          forget its configuration
 * /skyraid config &lt;ship&gt; speed &lt;b/s&gt;    top speed
 * /skyraid config &lt;ship&gt; turn &lt;deg/s&gt;   turn rate
 *
 * /skyraid ships                        every ship and whether it's ready
 * /skyraid ships info &lt;ship&gt;            its stats
 * /skyraid ships enable|disable &lt;ship&gt;  in or out of the raid pool
 *
 * /skyraid start [ship]                 raid the ship you're on (a random ready ship, or that one)
 * /skyraid status | overwhelm | stop    the raid on your ship
 * </pre>
 */
final class RaidCommands {
    private static final SuggestionProvider<CommandSourceStack> SHIPS =
            (c, b) -> SharedSuggestionProvider.suggest(RaiderShipDef.all().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> READY =
            (c, b) -> SharedSuggestionProvider.suggest(RaiderShipDef.all().values().stream().filter(RaiderShipDef::configured).map(d -> d.name).toList(), b);

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("skyraid").requires(s -> s.hasPermission(2))
                .then(Commands.literal("gallery")
                        .executes(RaidCommands::galleryAll)
                        .then(Commands.literal("clear").executes(RaidCommands::galleryClear))
                        .then(Commands.literal("tp").then(ship().executes(RaidCommands::galleryTp)))
                        .then(ship().executes(RaidCommands::galleryOne)))
                .then(Commands.literal("config")
                        .then(ship().executes(RaidCommands::configBegin)
                                .then(Commands.literal("save").executes(RaidCommands::configSave))
                                .then(Commands.literal("cancel").executes(RaidCommands::configCancel))
                                .then(Commands.literal("reset").executes(RaidCommands::configReset))
                                .then(Commands.literal("speed").then(Commands.argument("value", DoubleArgumentType.doubleArg(2, 60))
                                        .executes(c -> configSet(c, true))))
                                .then(Commands.literal("turn").then(Commands.argument("value", DoubleArgumentType.doubleArg(2, 120))
                                        .executes(c -> configSet(c, false))))))
                .then(Commands.literal("ships")
                        .executes(RaidCommands::shipsList)
                        .then(Commands.literal("info").then(ship().executes(RaidCommands::shipsInfo)))
                        .then(Commands.literal("enable").then(ship().executes(c -> shipsEnable(c, true))))
                        .then(Commands.literal("disable").then(ship().executes(c -> shipsEnable(c, false)))))
                .then(Commands.literal("start")
                        .executes(c -> start(c, null))
                        .then(Commands.argument("ship", StringArgumentType.word()).suggests(READY)
                                .executes(c -> start(c, StringArgumentType.getString(c, "ship")))))
                .then(Commands.literal("overwhelm").executes(RaidCommands::overwhelm))
                .then(Commands.literal("status").executes(RaidCommands::status))
                .then(Commands.literal("stop").executes(RaidCommands::stop)));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> ship() {
        return Commands.argument("ship", StringArgumentType.word()).suggests(SHIPS);
    }

    private static RaiderShipDef def(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "ship");
        RaiderShipDef d = RaiderShipDef.get(name);
        if (d == null) c.getSource().sendFailure(Component.literal("No raider ship called " + name + ". See /skyraid ships."));
        else if (d.blocks == 0) d.measure(c.getSource().getLevel());
        return d;
    }

    private static void ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
    }

    private static void lines(CommandContext<CommandSourceStack> c, List<Component> lines) {
        for (Component l : lines) c.getSource().sendSuccess(() -> l, false);
    }

    // --- gallery ----------------------------------------------------------------------------------

    private static int galleryAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        Gallery g = Gallery.get(level);
        List<RaiderShipDef> defs = new ArrayList<>(RaiderShipDef.all().values());
        int tallest = 0;
        for (RaiderShipDef d : defs) { d.measure(level); tallest = Math.max(tallest, d.size[1]); }
        BlockPos at = Gallery.rowStart(level, p, tallest);
        int placed = 0;
        List<String> failed = new ArrayList<>();
        for (RaiderShipDef d : defs) {
            String[] why = new String[1];
            if (d.size[0] == 0) { failed.add(d.name + " (no structure file)"); continue; }
            if (g.place(level, d, at, why) != null) {
                placed++;
                at = at.offset(d.size[0] + Gallery.GAP, 0, 0);
            } else failed.add(d.name + " (" + why[0] + ")");
        }
        ok(c, "Gallery: " + placed + " ships placed in a row east of you. /skyraid config <ship> to set one up.");
        if (!failed.isEmpty()) c.getSource().sendFailure(Component.literal("Couldn't place: " + String.join(", ", failed)));
        return placed;
    }

    private static int galleryOne(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        RaiderShipDef d = def(c);
        if (d == null) return 0;
        String[] why = new String[1];
        if (Gallery.get(p.serverLevel()).place(p.serverLevel(), d, Gallery.rowStart(p.serverLevel(), p, d.size[1]), why) == null) {
            c.getSource().sendFailure(Component.literal("Couldn't place " + d.name + ": " + why[0]));
            return 0;
        }
        lines(c, Gallery.stats(d));
        return 1;
    }

    private static int galleryTp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        RaiderShipDef d = def(c);
        if (d == null) return 0;
        Gallery.Entry e = Gallery.get(p.serverLevel()).entries.get(d.name);
        if (e == null) { c.getSource().sendFailure(Component.literal(d.name + " isn't in the gallery. /skyraid gallery " + d.name)); return 0; }
        p.teleportTo(p.serverLevel(), e.origin.getX() + e.size.getX() / 2.0, e.origin.getY() + e.size.getY() + 2, e.origin.getZ() - 3, 0, 30);
        return 1;
    }

    private static int galleryClear(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        Gallery g = Gallery.get(level);
        int n = g.entries.size();
        for (String ship : new ArrayList<>(g.entries.keySet())) g.clear(level, ship);
        ok(c, "Gallery cleared (" + n + " ships).");
        return n;
    }

    // --- config -----------------------------------------------------------------------------------

    private static int configBegin(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        RaiderShipDef d = def(c);
        if (d == null) return 0;
        Gallery g = Gallery.get(level);
        if (!g.entries.containsKey(d.name)) {
            String[] why = new String[1];
            if (g.place(level, d, Gallery.rowStart(level, p, d.size[1]), why) == null) {
                c.getSource().sendFailure(Component.literal("Couldn't place " + d.name + ": " + why[0]));
                return 0;
            }
        }
        g.begin(level, d);
        for (ItemStack s : MarkerKit.kit()) if (!p.getInventory().add(s)) p.drop(s, false);
        ok(c, "Configuring " + d.name + ". Place the marker blocks on it:");
        c.getSource().sendSuccess(() -> Component.literal(" Helm (red): face the ship's front as you place it; the captain stands here").withStyle(ChatFormatting.RED), false);
        c.getSource().sendSuccess(() -> Component.literal(" Boarding Hook (black): where each rope fires from, on the side to face the player").withStyle(ChatFormatting.DARK_GRAY), false);
        c.getSource().sendSuccess(() -> Component.literal(" Raider Post (light gray): one per crew member (about a quarter stay aboard as archers)").withStyle(ChatFormatting.GRAY), false);
        c.getSource().sendSuccess(() -> Component.literal(" Witch Post (purple, optional), Loot Hold (brown, optional)").withStyle(ChatFormatting.DARK_PURPLE), false);
        ok(c, "Then /skyraid config " + d.name + " save");
        if (d.configured()) g.relabel(level, d);
        return 1;
    }

    private static int configSave(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        RaiderShipDef d = def(c);
        if (d == null) return 0;
        String[] why = new String[1];
        List<Component> stats = Gallery.get(level).save(level, d, why);
        if (stats == null) { c.getSource().sendFailure(Component.literal("Not saved: " + why[0])); return 0; }
        ok(c, "Saved to config/emp_engines/raider_ships/" + d.name + ".json");
        lines(c, stats);
        return 1;
    }

    private static int configCancel(CommandContext<CommandSourceStack> c) {
        RaiderShipDef d = def(c);
        if (d == null) return 0;
        Gallery.get(c.getSource().getLevel()).cancel(c.getSource().getLevel(), d.name);
        ok(c, "Stopped configuring " + d.name + " (nothing saved).");
        return 1;
    }

    private static int configReset(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        RaiderShipDef d = def(c);
        if (d == null) return 0;
        try { d.deleteFile(); } catch (IOException e) { c.getSource().sendFailure(Component.literal("Couldn't delete: " + e.getMessage())); return 0; }
        Gallery g = Gallery.get(level);
        g.cancel(level, d.name);
        RaiderShipDef fresh = RaiderShipDef.get(d.name);
        if (fresh != null) g.relabel(level, fresh);
        ok(c, d.name + " forgotten" + (fresh == null ? " (it was only in the saved files, so it's gone from the list)." : "; it can't raid until configured again."));
        return 1;
    }

    private static int configSet(CommandContext<CommandSourceStack> c, boolean speed) {
        RaiderShipDef d = def(c);
        if (d == null) return 0;
        double v = DoubleArgumentType.getDouble(c, "value");
        if (speed) d.speed = v; else d.turn = v;
        try { d.save(); } catch (IOException e) { c.getSource().sendFailure(Component.literal("Couldn't save: " + e.getMessage())); return 0; }
        ok(c, d.name + (speed ? " top speed " + v + " blocks/s" : " turn rate " + v + " deg/s") + ".");
        return 1;
    }

    // --- ships ------------------------------------------------------------------------------------

    private static int shipsList(CommandContext<CommandSourceStack> c) {
        var all = RaiderShipDef.all();
        ok(c, all.size() + " raider ships:");
        for (RaiderShipDef d : all.values()) {
            String status = !d.configured() ? "not configured" : d.enabled ? "ready" : "disabled";
            ChatFormatting colour = !d.configured() ? ChatFormatting.RED : d.enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
            c.getSource().sendSuccess(() -> Component.literal(" " + d.name + ": ").append(Component.literal(status).withStyle(colour))
                    .append(Component.literal(d.configured() ? String.format("  (%.0f b/s, crew %d + captain)", d.speed, d.posts.size()) : "").withStyle(ChatFormatting.GRAY)), false);
        }
        return all.size();
    }

    private static int shipsInfo(CommandContext<CommandSourceStack> c) {
        RaiderShipDef d = def(c);
        if (d == null) return 0;
        lines(c, Gallery.stats(d));
        return 1;
    }

    private static int shipsEnable(CommandContext<CommandSourceStack> c, boolean on) {
        RaiderShipDef d = def(c);
        if (d == null) return 0;
        d.enabled = on;
        try { d.save(); } catch (IOException e) { c.getSource().sendFailure(Component.literal("Couldn't save: " + e.getMessage())); return 0; }
        Gallery.get(c.getSource().getLevel()).relabel(c.getSource().getLevel(), d);
        ok(c, d.name + (on ? " is in the raid pool" + (d.configured() ? "." : " (once it's configured).") : " is out of the raid pool."));
        return 1;
    }

    // --- raids ------------------------------------------------------------------------------------

    /** The ship the player is standing on, or the one right around them. */
    private static ServerSubLevel shipOf(ServerPlayer p) {
        ServerSubLevel s = SableBridge.shipUnder(p);
        if (s != null && !RaiderShip.isRaiderShip(s)) return s;
        s = SableBridge.findShip(p.serverLevel(), new AABB(p.blockPosition()).inflate(2, 3, 2));
        return s != null && !RaiderShip.isRaiderShip(s) ? s : null;
    }

    private static int start(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerSubLevel ship = shipOf(p);
        if (ship == null) { c.getSource().sendFailure(Component.literal("Stand on a ship first.")); return 0; }
        if (RaidManager.raidOn(ship) != null) { c.getSource().sendFailure(Component.literal("That ship is already being raided.")); return 0; }
        RaiderShipDef def;
        if (name != null) {
            def = RaiderShipDef.get(name);
            if (def == null) { c.getSource().sendFailure(Component.literal("No raider ship called " + name + ".")); return 0; }
        } else {
            List<RaiderShipDef> pool = RaiderShipDef.all().values().stream().filter(d -> d.configured() && d.enabled).toList();
            if (pool.isEmpty()) { c.getSource().sendFailure(Component.literal("No raider ship is ready. Set one up with /skyraid gallery and /skyraid config.")); return 0; }
            def = pool.get(p.getRandom().nextInt(pool.size()));
        }
        String[] why = new String[1];
        Raid raid = RaidManager.start(p.serverLevel(), ship, def, why);
        if (raid == null) { c.getSource().sendFailure(Component.literal("No raid: " + why[0])); return 0; }
        int archers = Raid.archerCount(raid.raider.posts.size());
        c.getSource().sendSuccess(() -> Component.literal("Raid started: " + def.name + ", " + (raid.raider.posts.size() - archers + 1)
                + " boarders (with the captain), " + archers + " archers, " + raid.raider.witches.size() + " witches, up to "
                + Math.min(4, raid.raider.hooks.size()) + " ropes."), true);
        return 1;
    }

    private static Raid raidHere(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerSubLevel ship = shipOf(c.getSource().getPlayerOrException());
        Raid raid = ship == null ? null : RaidManager.raidOn(ship);
        if (raid == null && RaidManager.RAIDS.size() == 1) raid = RaidManager.RAIDS.get(0);
        if (raid == null) c.getSource().sendFailure(Component.literal("No raid on this ship."));
        return raid;
    }

    private static int overwhelm(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Raid raid = raidHere(c);
        if (raid == null) return 0;
        raid.forceOverwhelm = true;
        ok(c, "Looters will go for the chests at the next check.");
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Raid raid = raidHere(c);
        if (raid == null) return 0;
        ok(c, raid.status());
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Raid raid = raidHere(c);
        if (raid == null) return 0;
        raid.discardAll();
        raid.end("stopped by command", true);
        RaidManager.RAIDS.remove(raid);
        c.getSource().sendSuccess(() -> Component.literal("Raid stopped."), true);
        return 1;
    }

    private RaidCommands() {}
}
