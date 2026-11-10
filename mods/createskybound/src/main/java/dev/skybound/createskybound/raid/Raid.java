package dev.skybound.createskybound.raid;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.skybound.createskybound.Config;
import dev.skybound.createskybound.CreateSkybound;
import dev.skybound.createskybound.launch.SableBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.joml.Quaterniond;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One boarding raid against one player ship.
 *
 * APPROACH  the raider sloop closes in on a curve and settles alongside, 5 blocks off the player's starboard side
 * ANCHOR    it fires boarding anchors (rope connectors) onto deck spots that walk to loot, and ties ropes to them
 * BOARDING  raiders cross the ropes one by one (scripted along the rope's own points) and step onto the deck
 * FIGHT     on deck, normal mob AI fights; if the crew is overwhelmed, looters go for the chests and carry stacks home
 * RETREAT   when looting is done (or time runs out) raiders on deck head back along the ropes
 * FLEE      anchors are removed, the sloop pulls away (slowly if it got loot, so it can be chased) and is deleted
 *
 * Positions on the player ship are kept in its own plot coordinates, so they stay right while it moves.
 */
public final class Raid {
    public enum Phase { APPROACH, ANCHOR, BOARDING, FIGHT, RETREAT, FLEE, DONE }
    public enum Role { BRUTE, LOOTER }
    public enum State { CROSSING_IN, ON_DECK, RETURNING, CROSSING_OUT, FALLING, HOME }

    static final int APPROACH = 200, FIRE_GAP = 15, SEND_GAP = 30, RETREAT_MAX = 600, MAX_FIGHT = 6 * 60 * 20;
    static final double ROPE_SPEED = 0.12;

    /** One rope: its owner on the sloop, its anchor on the player's deck and where raiders step off. */
    static final class Rope {
        final BlockPos from;
        BlockPos anchor;
        DeckScan.Landing landing;
        boolean tied;
        Rope(BlockPos from) { this.from = from; }
    }

    /** A raider the raid controls. */
    final class Raider {
        final Mob mob;
        final Role role;
        State state = State.CROSSING_IN;
        int rope;
        double along;
        Vec3 lastPos;
        final List<ItemStack> carried = new ArrayList<>();
        DeckScan.Loot target;
        /** Where its goal walks it, in player-ship plot coordinates (null = let normal AI decide). */
        Vec3 navTarget;
        Raider(Mob mob, Role role, int rope) { this.mob = mob; this.role = role; this.rope = rope; this.lastPos = mob.position(); }
    }

    final UUID id = UUID.randomUUID();
    final ServerLevel level;
    final UUID playerShip;
    final RaiderShip sloop;
    Phase phase = Phase.APPROACH;
    int phaseTicks, totalTicks;
    final List<Rope> ropes = new ArrayList<>();
    final Map<UUID, Raider> raiders = new LinkedHashMap<>();
    final Deque<Role> toSend = new ArrayDeque<>();
    boolean looting, forceOverwhelm, gotLoot;
    List<DeckScan.Loot> lootList = new ArrayList<>();
    int lootIndex;
    int nobodyAboard, nextRope;
    /** The sloop's deck point, in player-ship plot coordinates: where it starts, where it holds, where it is now. */
    Vec3 startLocal, holdLocal, local;
    String outcome = "";

    private Raid(ServerLevel level, ServerSubLevel target, RaiderShip sloop, Vec3 startLocal, Vec3 holdLocal) {
        this.level = level;
        this.playerShip = target.getUniqueId();
        this.sloop = sloop;
        this.startLocal = startLocal;
        this.holdLocal = holdLocal;
        this.local = startLocal;
        for (BlockPos c : sloop.connectors()) ropes.add(new Rope(c));
    }

    /**
     * Starts a raid on {@code target}, or returns null with the reason in {@code why[0]}.
     * {@code shipId} picks the raider ship's structure; null picks one from the config at random.
     */
    static Raid start(ServerLevel level, ServerSubLevel target, int crew, ResourceLocation shipId, String[] why) {
        DeckScan deck = new DeckScan(level, target);
        BoundingBox3ic box = SableBridge.plotBounds(target);
        double midY = (box.minY() + box.maxY()) / 2.0, midZ = (box.minZ() + box.maxZ() + 1) / 2.0;
        int ropeCount = Math.min(3, Config.RAID_ROPES.get());
        List<DeckScan.Landing> landings = deck.landings(new Vec3(box.maxX() + 20, midY, midZ), ropeCount);
        if (landings.isEmpty()) { why[0] = "No open deck to land on."; return null; }

        // Bring in a real airship from one of the pack's structure mods, out of view; the built-in sloop if that fails.
        if (shipId == null) {
            List<? extends String> ships = Config.RAID_SHIPS.get();
            if (!ships.isEmpty()) shipId = ResourceLocation.tryParse(ships.get(level.random.nextInt(ships.size())));
        }
        Vec3 shipWorld = SableBridge.position(target);
        RaiderShip sloop = null;
        BlockPos spawn = null;
        for (int tries = 0; tries < 6 && sloop == null; tries++) {
            spawn = BlockPos.containing(shipWorld.add(90 + tries * 20, 30 + tries * 8, -70));
            sloop = shipId != null ? RaiderShip.fromTemplate(level, spawn, shipId, landings.size()) : RaiderShip.build(level, spawn);
        }
        if (sloop == null && shipId != null) {
            CreateSkybound.LOGGER.warn("Sky Raiders: couldn't bring in {}, using the built-in sloop", shipId);
            for (int tries = 0; tries < 4 && sloop == null; tries++) {
                spawn = BlockPos.containing(shipWorld.add(90, 60 + tries * 10, -70));
                sloop = RaiderShip.build(level, spawn);
            }
        }
        if (sloop == null || sloop.connectors().isEmpty()) { why[0] = "Couldn't find clear air to bring in the raider ship."; return null; }

        // Hold alongside: its connectors level with the anchors, centred on them, a gap off both hulls.
        double z = landings.stream().mapToDouble(l -> l.anchorSpot().getZ() + 0.5).average().orElse(midZ);
        double y = landings.get(0).anchorSpot().getY() + 1;
        Vec3 hold = new Vec3(box.maxX() + 1 + Config.RAID_GAP.get() + sloop.overhang(), y, z);
        Vec3 start = SableBridge.toPlot(target, spawn.getCenter());
        Raid raid = new Raid(level, target, sloop, start, hold);
        for (int i = 0; i < raid.ropes.size() && i < landings.size(); i++) raid.ropes.get(i).landing = landings.get(i);
        raid.ropes.removeIf(r -> r.landing == null);
        for (int i = 0; i < crew; i++) raid.toSend.add(i % 2 == 0 ? Role.BRUTE : Role.LOOTER);
        raid.announce(Component.literal("A raider ship is closing in!").withStyle(ChatFormatting.RED));
        return raid;
    }

    ServerSubLevel target() { return SableBridge.byId(level, playerShip); }

    // --- tick -----------------------------------------------------------------------------

    void tick() {
        ServerSubLevel target = target();
        if (target == null || sloop.ship().isRemoved()) { end("The ship was lost."); return; }
        totalTicks++;
        phaseTicks++;
        moveSloop(target);
        updateRaiders(target);

        switch (phase) {
            case APPROACH -> { if (phaseTicks >= APPROACH) setPhase(Phase.ANCHOR); }
            case ANCHOR -> fireAnchors(target);
            case BOARDING -> {
                if (phaseTicks % SEND_GAP == 0) sendNext(target);
                if (toSend.isEmpty() || ropes.stream().noneMatch(r -> r.tied)) setPhase(Phase.FIGHT);
            }
            case FIGHT -> fight(target);
            case RETREAT -> {
                boolean anyAboard = raiders.values().stream().anyMatch(r -> r.mob.isAlive()
                        && (r.state == State.ON_DECK || r.state == State.RETURNING || r.state == State.CROSSING_OUT));
                if (!anyAboard || phaseTicks > RETREAT_MAX) startFlee(target);
            }
            case FLEE -> {
                if (phaseTicks > (gotLoot ? 1200 : 200)) end(outcome);
            }
            default -> { }
        }
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseTicks = 0;
    }

    /** Keeps the sloop at its place in the player ship's frame: approaching, holding, or pulling away. */
    private void moveSloop(ServerSubLevel target) {
        if (phase == Phase.APPROACH) {
            double t = Math.min(1, phaseTicks / (double) APPROACH);
            double k = 1 - Math.pow(1 - t, 3);
            local = startLocal.lerp(holdLocal, k);
        } else if (phase == Phase.FLEE) {
            double speed = gotLoot ? 0.06 : 0.3;
            local = local.add(speed, speed * 0.4, 0);
        } else {
            local = holdLocal;
        }
        Quaterniond rot = SableBridge.level(SableBridge.orientation(target));
        Vec3 world = SableBridge.toWorld(target, local);
        Vec3 offset = sloop.deckPoint().subtract(SableBridge.rotationPoint(sloop.ship()));
        org.joml.Vector3d o = rot.transform(new org.joml.Vector3d(offset.x, offset.y, offset.z));
        SableBridge.place(sloop.ship(), world.subtract(o.x, o.y, o.z), rot);
    }

    private void fireAnchors(ServerSubLevel target) {
        int i = phaseTicks / FIRE_GAP;
        if (phaseTicks % FIRE_GAP != 1 || i >= ropes.size()) {
            if (i >= ropes.size() && phaseTicks % FIRE_GAP == 1) {
                if (ropes.stream().noneMatch(r -> r.tied)) { outcome = "The anchors missed."; startFlee(target); }
                else setPhase(Phase.BOARDING);
            }
            return;
        }
        Rope rope = ropes.get(i);
        BlockPos anchor = rope.landing.anchorSpot().above();
        if (!level.getBlockState(anchor).getCollisionShape(level, anchor).isEmpty()) return;
        level.setBlock(anchor, RopeBridge.connectorState(), 3);
        rope.anchor = anchor;
        rope.tied = RopeBridge.tie(level, rope.from, anchor);
        Vec3 at = SableBridge.toWorld(target, Vec3.atCenterOf(anchor));
        level.playSound(null, BlockPos.containing(at), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.5f, 0.7f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.5f, 0.8f);
        if (!rope.tied) RopeBridge.removeConnector(level, anchor);
    }

    /** True while both ends stand and the rope is there. */
    boolean ropeUp(Rope r) {
        if (!r.tied) return false;
        if (r.anchor == null || !RopeBridge.isConnector(level, r.anchor) || !RopeBridge.intact(level, r.from)) {
            r.tied = false;
            return false;
        }
        return true;
    }

    private void sendNext(ServerSubLevel target) {
        Role role = toSend.peek();
        if (role == null) return;
        for (int n = 0; n < ropes.size(); n++) {
            int idx = (nextRope + n) % ropes.size();
            Rope r = ropes.get(idx);
            if (!ropeUp(r)) continue;
            List<Vec3> path = RopeBridge.path(level, r.from, r.anchor);
            if (path.isEmpty()) continue;
            Mob mob = (role == Role.BRUTE ? EntityType.VINDICATOR : EntityType.PILLAGER).create(level);
            if (mob == null) return;
            Vec3 p = path.get(0);
            mob.moveTo(p.x, p.y, p.z, 0, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(p)), MobSpawnType.EVENT, null);
            mob.setPersistenceRequired();
            mob.addTag(RaidManager.TAG);
            mob.setNoGravity(true);
            Raider raider = new Raider(mob, role, idx);
            raiders.put(mob.getUUID(), raider);
            mob.goalSelector.addGoal(0, new RaiderGoal(this, raider));
            level.addFreshEntity(mob);
            toSend.poll();
            nextRope = idx + 1;
            return;
        }
    }

    private void fight(ServerSubLevel target) {
        if (phaseTicks % 20 == 0 && !looting) {
            int onDeck = (int) raiders.values().stream().filter(r -> r.state == State.ON_DECK && r.mob.isAlive()).count();
            int crew = crewAboard(target).size();
            nobodyAboard = crew == 0 && onDeck > 0 ? nobodyAboard + 20 : 0;
            if (forceOverwhelm || onDeck - crew >= Config.RAID_OVERWHELM.get() || nobodyAboard >= 400) startLooting(target);
        }
        boolean anyLeft = raiders.values().stream().anyMatch(r -> r.mob.isAlive() && r.state != State.HOME && r.state != State.FALLING);
        boolean canSendMore = !toSend.isEmpty() && ropes.stream().anyMatch(this::ropeUp);
        if (!anyLeft && !canSendMore) {
            outcome = gotLoot ? "The raiders got away with your cargo. Chase them down!" : "The raid was repelled.";
            startFlee(target);
            return;
        }
        if (canSendMore && phaseTicks % SEND_GAP == 0) sendNext(target);
        if (looting) {
            boolean lootersBusy = raiders.values().stream().anyMatch(r -> r.role == Role.LOOTER && r.mob.isAlive()
                    && (r.state == State.ON_DECK || r.state == State.RETURNING || r.state == State.CROSSING_OUT));
            if (!lootersBusy) { outcome = gotLoot ? "The raiders got away with your cargo. Chase them down!" : "The raid was repelled."; retreat(); }
        }
        if (phaseTicks > MAX_FIGHT) { outcome = "The raiders gave up."; retreat(); }
    }

    private void startLooting(ServerSubLevel target) {
        looting = true;
        lootList = new DeckScan(level, target).loot();
        for (Raider r : raiders.values()) if (r.role == Role.LOOTER && r.state == State.ON_DECK) assignLoot(r);
        announce(Component.literal("You're overwhelmed - the raiders are going for your cargo!").withStyle(ChatFormatting.GOLD));
    }

    /** Points a looter at the next chest, or sends it home if there's nothing to take. */
    private void assignLoot(Raider r) {
        if (lootList.isEmpty()) { sendHome(r); return; }
        r.target = lootList.get(lootIndex++ % lootList.size());
        r.navTarget = Vec3.atBottomCenterOf(r.target.standOn().above());
    }

    private void retreat() {
        for (Raider r : raiders.values()) if (r.state == State.ON_DECK) sendHome(r);
        setPhase(Phase.RETREAT);
    }

    private void sendHome(Raider r) {
        Rope rope = pickRope(r.rope);
        if (rope == null) { r.navTarget = null; r.state = State.ON_DECK; return; }   // stranded: it fights on
        r.rope = ropes.indexOf(rope);
        r.state = State.RETURNING;
        r.navTarget = Vec3.atBottomCenterOf(rope.landing.stepOff().above());
    }

    private Rope pickRope(int preferred) {
        if (preferred < ropes.size() && ropeUp(ropes.get(preferred))) return ropes.get(preferred);
        for (Rope r : ropes) if (ropeUp(r)) return r;
        return null;
    }

    private void startFlee(ServerSubLevel target) {
        for (Rope r : ropes) if (r.anchor != null) RopeBridge.removeConnector(level, r.anchor);
        for (Rope r : ropes) r.tied = false;
        if (!outcome.isEmpty()) announce(Component.literal(outcome).withStyle(gotLoot ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        setPhase(Phase.FLEE);
    }

    // --- raiders ----------------------------------------------------------------------------

    private void updateRaiders(ServerSubLevel target) {
        for (Raider r : raiders.values()) {
            Mob mob = r.mob;
            if (!mob.isAlive()) {
                if (!r.carried.isEmpty()) {
                    for (ItemStack s : r.carried) level.addFreshEntity(new ItemEntity(level, r.lastPos.x, r.lastPos.y, r.lastPos.z, s));
                    r.carried.clear();
                }
                continue;
            }
            r.lastPos = mob.position();
            switch (r.state) {
                case CROSSING_IN -> cross(r, target, true);
                case CROSSING_OUT -> cross(r, target, false);
                case ON_DECK -> {
                    if (looting && r.role == Role.LOOTER && r.carried.isEmpty()) {
                        if (r.target == null) assignLoot(r);
                        else tryTake(r, target);
                    }
                }
                case RETURNING -> {
                    Rope rope = r.rope < ropes.size() ? ropes.get(r.rope) : null;
                    if (rope == null || !ropeUp(rope)) { sendHome(r); break; }
                    Vec3 step = SableBridge.toWorld(target, Vec3.atBottomCenterOf(rope.landing.stepOff().above()));
                    if (mob.position().distanceTo(step) < 1.6) {
                        r.state = State.CROSSING_OUT;
                        r.along = 0;
                        r.navTarget = null;
                        mob.getNavigation().stop();
                        mob.setNoGravity(true);
                    }
                }
                default -> { }
            }
        }
    }

    /** Moves a raider along its rope: in from the sloop, or out from the anchor. */
    private void cross(Raider r, ServerSubLevel target, boolean in) {
        Rope rope = ropes.get(r.rope);
        Mob mob = r.mob;
        if (!ropeUp(rope)) {   // the rope was cut under them
            mob.setNoGravity(false);
            r.state = State.FALLING;
            return;
        }
        List<Vec3> path = RopeBridge.path(level, rope.from, rope.anchor);
        if (!in) java.util.Collections.reverse(path);
        double length = 0;
        for (int i = 1; i < path.size(); i++) length += path.get(i).distanceTo(path.get(i - 1));
        r.along += ROPE_SPEED;
        if (r.along >= length - 0.4) {
            mob.setNoGravity(false);
            mob.setDeltaMovement(Vec3.ZERO);
            if (in) {
                Vec3 land = SableBridge.toWorld(target, Vec3.atBottomCenterOf(rope.landing.stepOff().above()));
                mob.teleportTo(land.x, land.y, land.z);
                r.state = State.ON_DECK;
            } else {
                deposit(r);
                r.state = State.HOME;
                mob.discard();
            }
            return;
        }
        Vec3 at = pointAlong(path, r.along), ahead = pointAlong(path, r.along + 0.5);
        Vec3 sway = new Vec3(0, 0.08 * Math.sin((totalTicks + r.mob.getId()) * 0.3), 0);
        Vec3 feet = at.add(0, 0.1, 0).add(sway);
        mob.setDeltaMovement(feet.subtract(mob.position()));
        mob.setPos(feet.x, feet.y, feet.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(ahead.z - at.z, ahead.x - at.x)) - 90);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.fallDistance = 0;
    }

    private static Vec3 pointAlong(List<Vec3> path, double d) {
        for (int i = 1; i < path.size(); i++) {
            double seg = path.get(i).distanceTo(path.get(i - 1));
            if (d <= seg) return path.get(i - 1).lerp(path.get(i), seg == 0 ? 0 : d / seg);
            d -= seg;
        }
        return path.get(path.size() - 1);
    }

    /** A looter next to its chest takes up to the configured number of stacks. */
    private void tryTake(Raider r, ServerSubLevel target) {
        Vec3 chest = SableBridge.toWorld(target, Vec3.atCenterOf(r.target.inventory()));
        if (r.mob.position().add(0, 1, 0).distanceTo(chest) > 2.2) return;
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, r.target.inventory(), null);
        if (h == null) { sendHome(r); return; }
        int stacks = Config.RAID_STACKS.get();
        for (int i = 0; i < h.getSlots() && r.carried.size() < stacks; i++) {
            ItemStack taken = h.extractItem(i, 64, false);
            if (!taken.isEmpty()) r.carried.add(taken);
        }
        level.playSound(null, BlockPos.containing(chest), SoundEvents.BARREL_OPEN, SoundSource.HOSTILE, 1f, 0.8f);
        sendHome(r);
    }

    /** Stolen goods go into the sloop's hold, so catching the sloop gets them back. */
    private void deposit(Raider r) {
        if (r.carried.isEmpty()) return;
        gotLoot = true;
        IItemHandler hold = sloop.hold() == null ? null : level.getCapability(Capabilities.ItemHandler.BLOCK, sloop.hold(), null);
        for (ItemStack s : r.carried) {
            ItemStack left = hold == null ? s : ItemHandlerHelper.insertItemStacked(hold, s, false);
            if (!left.isEmpty()) {
                Vec3 p = SableBridge.project(level, Vec3.atCenterOf(sloop.hold() == null ? sloop.connectors().get(0) : sloop.hold()));
                level.addFreshEntity(new ItemEntity(level, p.x, p.y + 1, p.z, left));
            }
        }
        r.carried.clear();
    }

    // --- helpers ------------------------------------------------------------------------------

    List<ServerPlayer> crewAboard(ServerSubLevel target) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            if (SableBridge.shipUnder(p) == target) out.add(p);
        }
        return out;
    }

    void announce(Component msg) {
        ServerSubLevel target = target();
        Vec3 c = target == null ? Vec3.ZERO : SableBridge.position(target);
        for (ServerPlayer p : level.players())
            if (p.position().distanceTo(c) < 160) p.displayClientMessage(msg, true);
    }

    boolean owns(UUID mob) { return raiders.containsKey(mob); }

    /** Removes every raider of this raid, wherever it is (used by /skyraid stop). */
    void discardAll() {
        for (Raider r : raiders.values()) if (r.mob.isAlive()) r.mob.discard();
    }

    /** Ends the raid now: sloop deleted, any raiders still on a rope removed. */
    void end(String why) {
        if (phase == Phase.DONE) return;
        for (Rope r : ropes) if (r.anchor != null) RopeBridge.removeConnector(level, r.anchor);
        for (Raider r : raiders.values()) {
            if (r.state == State.CROSSING_IN || r.state == State.CROSSING_OUT) r.mob.discard();
        }
        SableBridge.remove(sloop.ship());
        phase = Phase.DONE;
        CreateSkybound.LOGGER.info("Sky Raiders: raid {} ended ({})", id, why);
    }

    String status() {
        long alive = raiders.values().stream().filter(r -> r.mob.isAlive() && r.state != State.HOME).count();
        long up = ropes.stream().filter(this::ropeUp).count();
        return "phase " + phase + ", " + up + "/" + ropes.size() + " ropes up, " + alive + " raiders alive, "
                + toSend.size() + " waiting, looting " + looting + ", loot taken " + gotLoot;
    }
}
