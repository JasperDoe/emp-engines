package dev.empengines.engines.raid;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.empengines.engines.Config;
import dev.empengines.engines.EmpyreanEngines;
import dev.empengines.engines.launch.SableBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One boarding raid against one player ship, by one raider ship from the gallery ({@link RaiderShipDef}).
 *
 * APPROACH  the raider ship is brought in out of view with its crew on their posts, and flies (by magic
 *           physics, {@link RaiderFlight}) onto a spot beside the player's ship with its hooks facing it;
 *           its archers shoot as it comes
 * ANCHOR    once alongside it locks on (it can't be shaken off) and fires its boarding anchors, tying real
 *           slack ropes from its hooks to the player's deck
 * BOARDING  boarders walk from their posts to the hooks and cross the ropes one by one; the captain last
 * FIGHT     on deck, normal mob AI fights; if the boarders overwhelm the crew, looters go for the chests and carry
 *           stacks home; the witch throws Mining Fatigue at whoever tries to break an anchor
 * RETREAT   when looting is done (or time runs out) raiders on deck head back along the ropes
 * FLEE      the anchors are cut (by the players, or by the raiders on leaving) and the raider flies off, then is
 *           deleted; slower if it got loot, so it can be chased
 *
 * The anchors are the ropes' only weak point: see {@link RaidManager} for how they're protected.
 */
public final class Raid {
    public enum Phase { WARNING, APPROACH, ANCHOR, BOARDING, FIGHT, RETREAT, ESCAPE, DERELICT, FLEE, DONE }
    public enum Role { BRUTE, LOOTER, ARCHER, CAPTAIN, WITCH }
    public enum State { WAITING, ARCHER, WITCH, PILOT, TO_HOOK, CROSSING_IN, ON_DECK, RETURNING, CROSSING_OUT, FALLING, HOME }

    static final int APPROACH_MAX = 4 * 60 * 20, ENVELOPE_HOLD = 20, FIRE_GAP = 15, SEND_GAP = 40, TO_HOOK_MAX = 200,
            RETREAT_MAX = 600, MAX_FIGHT = 6 * 60 * 20;
    static final double ROPE_SPEED = 0.12;
    static final int CREW_AT = 5;
    static final double MAX_ROPE = 28;
    /** Ticks an archer at the helm takes to cast off (time to stop them), and a derelict lasts once it's adrift. */
    static final int ESCAPE_TICKS = 200, DERELICT_TICKS = 3 * 60 * 20;
    /** The raid horn and the filling bar before the raider ship comes in. */
    static final int WARNING_TICKS = 200;

    /** One rope: its owner (a hook on the raider ship), its anchor on the player's deck and where raiders step off. */
    static final class Rope {
        final BlockPos from;
        BlockPos anchor;
        DeckScan.Landing landing;
        boolean tied;
        Rope(BlockPos from) { this.from = from; }
        BoardingRope line() { return new BoardingRope(from, anchor, Config.RAID_ROPE_SAG.get().floatValue()); }
    }

    /** A raider the raid controls. */
    final class RaiderMob {
        Mob mob;   // replaced if the mob is unloaded and loaded again (the ship crossing into new chunks)
        final Role role;
        final BlockPos post;   // on the raider ship (plot coordinates)
        State state;
        int rope = -1, stateTicks;
        double along;
        Vec3 lastPos;
        final List<ItemStack> carried = new ArrayList<>();
        DeckScan.Loot target;
        /** Where its goal walks it, in the plot coordinates of whichever ship it's on (null = let normal AI decide). */
        Vec3 navTarget;
        RaiderMob(Mob mob, Role role, BlockPos post, State state) {
            this.mob = mob; this.role = role; this.post = post; this.state = state; this.lastPos = mob.position();
        }
        void set(State s) { state = s; stateTicks = 0; }
        /** Unloaded with its chunk, not dead: it comes back (see {@link Raid#rebind}). */
        boolean unloaded() { return mob.isRemoved() && mob.getRemovalReason() != null && !mob.getRemovalReason().shouldDestroy(); }
        boolean alive() { return mob.isAlive() || unloaded(); }
        boolean boards() { return role == Role.BRUTE || role == Role.LOOTER || role == Role.CAPTAIN; }
        Raid raid() { return Raid.this; }
    }

    final UUID id = UUID.randomUUID();
    final ServerLevel level;
    final UUID playerShip;
    final RaiderShip raider;
    final RaiderFlight flight;
    Phase phase = Phase.WARNING;
    int phaseTicks, totalTicks;
    final List<Rope> ropes = new ArrayList<>();
    final Map<UUID, RaiderMob> raiders = new LinkedHashMap<>();
    final List<RaiderMob> boardQueue = new ArrayList<>();
    /** Players seen breaking a boarding connector, and the game tick they were last seen at it. */
    final Map<UUID, Long> breaking = new HashMap<>();
    boolean looting, forceOverwhelm, gotLoot, everTied;
    List<DeckScan.Loot> lootList = new ArrayList<>();
    int lootIndex, nobodyAboard, nextRope, shotsFired;
    /** In the player ship's frame: the way it was heading when the raid began, and the side the raider comes alongside. */
    final Direction axis, side;
    final List<DeckScan.Landing> landings;
    final Vec3 holdPlot;
    String outcome = "";
    /** The raid bar, like a village raid's: fills during the warning, then shows the crew's health. */
    final ServerBossEvent bar = new ServerBossEvent(Component.literal("Sky Raid"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    float crewHealth;
    boolean crewSpawned;
    boolean escaped;
    int barHideAt = -1;

    private Raid(ServerLevel level, ServerSubLevel target, RaiderShip raider, Direction axis, Direction side,
                 List<DeckScan.Landing> landings, Vec3 holdPlot) {
        this.level = level;
        this.playerShip = target.getUniqueId();
        this.raider = raider;
        this.flight = new RaiderFlight(level, raider);
        this.axis = axis;
        this.side = side;
        this.landings = landings;
        this.holdPlot = holdPlot;
    }

    static int archerCount(int posts) { return Math.max(1, (int) Math.round(posts * 0.25)); }

    /** Starts a raid on {@code target} with the given raider ship, or returns null with the reason in {@code why[0]}. */
    static Raid start(ServerLevel level, ServerSubLevel target, RaiderShipDef def, String[] why) {
        if (!def.configured()) { why[0] = def.name + " isn't configured (/skyraid config " + def.name + ")."; return null; }
        DeckScan deck = new DeckScan(level, target);
        BoundingBox3ic box = SableBridge.plotBounds(target);
        Vec3 centre = new Vec3((box.minX() + box.maxX() + 1) / 2.0, (box.minY() + box.maxY()) / 2.0, (box.minZ() + box.maxZ() + 1) / 2.0);

        // "Along" is where the ship is going (or its long axis if it's sitting still).
        Vec3 vel = SableBridge.velocity(target);
        Vec3 velLocal = SableBridge.toLocalDir(target, new Vec3(vel.x, 0, vel.z));
        Direction axis = Math.hypot(vel.x, vel.z) > 1.5 ? horizontal(velLocal)
                : (box.maxX() - box.minX()) > (box.maxZ() - box.minZ()) ? Direction.EAST : Direction.SOUTH;

        // Which side of the raider faces the player: one that has hooks (either, at random, if both do).
        // Starboard (its right) to the player means it sits on the player's left, and the other way round.
        boolean starboard = def.hooksPort.isEmpty() || (!def.hooksStarboard.isEmpty() && level.random.nextBoolean());
        Direction side = starboard ? axis.getCounterClockWise() : axis.getClockWise();
        double halfSide = halfWidth(box, side);

        int ropeCount = Math.min(4, def.hooks(starboard).size());
        List<DeckScan.Landing> landings = deck.landings(centre.add(step(side).scale(halfSide + 20)), ropeCount);
        if (landings.isEmpty()) { why[0] = "No open deck to land on."; return null; }

        // Bring it in out of view, off to that side and behind, already pointing the way the player is going.
        Vec3 heading = flat(SableBridge.toWorldDir(target, step(axis)));
        RaiderShip raider = null;
        for (int tries = 0; tries < 6 && raider == null; tries++) {
            Vec3 plot = centre.add(step(side).scale(halfSide + 110 + tries * 15)).subtract(step(axis).scale(70));
            Vec3 world = SableBridge.toWorld(target, plot).add(0, 12 + tries * 8, 0);
            raider = RaiderShip.spawn(level, def, BlockPos.containing(world), heading, starboard, why);
        }
        if (raider == null) return null;

        // The spot alongside: level with the anchors, the raider's middle beside the middle of the landings.
        double deckY = landings.get(0).anchorSpot().getY() + 1;
        double along = landings.stream().mapToDouble(l -> Vec3.atCenterOf(l.anchorSpot()).dot(step(axis))).average().orElse(0);
        Vec3 mid = new Vec3(centre.x, deckY, centre.z);
        mid = mid.add(step(axis).scale(along - mid.dot(step(axis))));
        Vec3 hold = mid.add(step(side).scale(halfSide + Config.RAID_GAP.get() + raider.hookReach()));

        Raid raid = new Raid(level, target, raider, axis, side, landings, hold);
        raid.pairRopes(target);
        raid.bar.setProgress(0);
        return raid;
    }

    /** Pairs hooks and landings front-to-back so the ropes don't cross. */
    private void pairRopes(ServerSubLevel target) {
        Vec3 a = flat(SableBridge.toWorldDir(target, step(axis)));
        List<BlockPos> from = new ArrayList<>(raider.hooks);
        from.sort(Comparator.comparingDouble(p -> SableBridge.toWorld(raider.ship, Vec3.atCenterOf(p)).dot(a)));
        if (from.size() > landings.size()) from = from.subList((from.size() - landings.size()) / 2, (from.size() - landings.size()) / 2 + landings.size());
        List<DeckScan.Landing> to = new ArrayList<>(landings);
        to.sort(Comparator.comparingDouble(l -> SableBridge.toWorld(target, Vec3.atCenterOf(l.anchorSpot())).dot(a)));
        for (int i = 0; i < Math.min(from.size(), to.size()); i++) {
            Rope r = new Rope(from.get(i));
            r.landing = to.get(i);
            ropes.add(r);
        }
    }

    /** The crew on their posts: about a quarter archers (the posts furthest from the hooks), the rest boarders; the captain at the helm. */
    private void spawnCrew() {
        List<BlockPos> posts = new ArrayList<>(raider.posts);
        posts.sort(Comparator.comparingDouble((BlockPos p) -> -raider.hooks.stream().mapToDouble(h -> h.distSqr(p)).min().orElse(0)));
        int archers = archerCount(posts.size());
        List<RaiderMob> brutes = new ArrayList<>();
        for (int i = 0; i < posts.size(); i++) {
            boolean archer = i < archers;
            Role role = archer ? Role.ARCHER : (brutes.size() % 2 == 0 ? Role.BRUTE : Role.LOOTER);
            RaiderMob m = spawn(role == Role.BRUTE ? EntityType.VINDICATOR : EntityType.PILLAGER, role, posts.get(i), archer ? State.ARCHER : State.WAITING);
            if (m != null && !archer) brutes.add(m);
        }
        boardQueue.addAll(brutes);
        if (raider.helm != null) {
            RaiderMob captain = spawn(EntityType.PILLAGER, Role.CAPTAIN, raider.helm, State.WAITING);
            if (captain != null) {
                ItemStack banner = net.minecraft.world.entity.raid.Raid.getLeaderBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));
                captain.mob.setItemSlot(EquipmentSlot.HEAD, banner);
                captain.mob.setDropChance(EquipmentSlot.HEAD, 2.0f);
                if (captain.mob instanceof Raider rd) rd.setPatrolLeader(true);
                boardQueue.add(captain);
            }
        }
        for (BlockPos w : raider.witches) spawn(EntityType.WITCH, Role.WITCH, w, State.WITCH);
    }

    private RaiderMob spawn(EntityType<? extends Mob> type, Role role, BlockPos post, State state) {
        Mob mob = type.create(level);
        if (mob == null) return null;
        Vec3 p = SableBridge.toWorld(raider.ship, Vec3.atBottomCenterOf(post));
        Vec3 h = raider.heading();
        mob.moveTo(p.x, p.y + 0.05, p.z, (float) Math.toDegrees(Math.atan2(-h.x, h.z)), 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(p)), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        mob.addTag(RaidManager.TAG);
        if (mob instanceof Raider rd) rd.setCanJoinRaid(false);
        RaiderMob m = new RaiderMob(mob, role, post, state);
        if (m.boards()) {   // hard to knock off the ropes and decks
            var kb = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(Math.max(kb.getBaseValue(), 0.7));
        }
        raiders.put(mob.getUUID(), m);
        if (mob instanceof Witch witch) WitchGoal.install(witch, m);
        else mob.goalSelector.addGoal(0, new RaiderGoal(m));
        level.addFreshEntity(mob);
        return m;
    }

    private static Direction horizontal(Vec3 v) {
        return Math.abs(v.x) > Math.abs(v.z) ? (v.x > 0 ? Direction.EAST : Direction.WEST) : (v.z > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    static Vec3 step(Direction d) { return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ()); }

    private static Vec3 flat(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : h.normalize();
    }

    private static double halfWidth(BoundingBox3ic box, Direction d) {
        return d.getAxis() == Direction.Axis.X ? (box.maxX() - box.minX() + 1) / 2.0 : (box.maxZ() - box.minZ() + 1) / 2.0;
    }

    ServerSubLevel target() { return SableBridge.byId(level, playerShip); }

    // --- tick -----------------------------------------------------------------------------

    void tick() {
        ServerSubLevel target = target();
        if (target == null || raider.isRemoved()) { end("The ship was lost.", false); return; }
        totalTicks++;
        phaseTicks++;
        // Once the ship has settled into its turned pose: it waits out of sight, keeping pace, until the warning's over.
        if (totalTicks == CREW_AT && phase == Phase.WARNING) flight.capture(target);
        // The crew goes aboard once the ship is close enough for mobs to be ticking there (a mob in a chunk that isn't
        // ticking stays put while the ship flies on without it), or when it locks on at the latest.
        if (!crewSpawned && phase != Phase.WARNING
                && (phase != Phase.APPROACH || level.isPositionEntityTicking(BlockPos.containing(SableBridge.toWorld(raider.ship, raider.ref))))) {
            crewSpawned = true;
            RaidManager.starting = this;   // so the crew isn't turned away as leftovers from before a restart
            try { spawnCrew(); } finally { RaidManager.starting = null; }
            for (RaiderMob m : raiders.values()) crewHealth += m.mob.getMaxHealth();
        }
        updateBar(target);

        // Fly: approach until alongside, then locked on while the anchors fire and any rope is up.
        if (phase == Phase.FLEE) flight.flee(target, gotLoot);
        else if (phase == Phase.WARNING) {
            if (totalTicks == 1) horn(target);
            if (flight.locked) flight.hold(target);
            if (phaseTicks >= WARNING_TICKS) {
                flight.unlock();
                announce(Component.literal("A raider ship is closing in!").withStyle(ChatFormatting.RED));
                setPhase(Phase.APPROACH);
            }
        } else if (flight.locked) {
            boolean anyUp = false;
            for (Rope r : ropes) anyUp |= ropeUp(r);
            if (anyUp || phase == Phase.ANCHOR) flight.hold(target);
            else {
                flight.unlock();
                if (phase == Phase.DERELICT) phaseTicks = 0;   // adrift now: the clock to its removal starts
                else {
                    outcome = gotLoot ? "The raiders got away with your cargo. Chase them down!" : "The boarding lines are cut - the raider ship is breaking off!";
                    startFlee();
                }
            }
        } else if (phase != Phase.DERELICT) flight.approach(target, holdPlot, axis);
        updateRaiders(target);

        switch (phase) {
            case APPROACH -> {
                if (flight.envelopeTicks >= ENVELOPE_HOLD) { flight.capture(target); setPhase(Phase.ANCHOR); }
                else if (phaseTicks > APPROACH_MAX) { outcome = "The raider ship couldn't catch you."; startFlee(); }
            }
            case ANCHOR -> fireAnchors(target);
            case BOARDING -> {
                if (phaseTicks % SEND_GAP == 1) sendNext();
                if (boardQueue.stream().noneMatch(m -> m.alive() && m.state == State.WAITING)) setPhase(Phase.FIGHT);
            }
            case FIGHT -> fight(target);
            case RETREAT -> {
                boolean anyAboard = raiders.values().stream().anyMatch(r -> r.alive()
                        && (r.state == State.ON_DECK || r.state == State.RETURNING || r.state == State.CROSSING_OUT));
                if (!anyAboard || phaseTicks > RETREAT_MAX) startFlee();
            }
            case ESCAPE -> escape();
            case DERELICT -> {
                if (!flight.locked) end("derelict, now an ordinary ship", false);
            }
            case FLEE -> {
                if (phaseTicks > (gotLoot ? 1800 : 400)) end(outcome, false);
            }
            default -> { }
        }
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseTicks = 0;
    }

    /**
     * Now that the raider is locked alongside: each hook gets the open deck spot nearest to where it
     * actually is, so every rope is as short as it can be.
     */
    private void repairRopes(ServerSubLevel target) {
        DeckScan deck = new DeckScan(level, target);
        List<BlockPos> taken = new ArrayList<>();
        List<Rope> fresh = new ArrayList<>();
        for (BlockPos hook : raider.hooks) {
            Vec3 at = SableBridge.toPlot(target, SableBridge.toWorld(raider.ship, Vec3.atCenterOf(hook)));
            for (DeckScan.Landing l : deck.landings(at, 8)) {
                if (taken.stream().anyMatch(t -> t.distSqr(l.anchorSpot()) < 9)) continue;
                Rope r = new Rope(hook);
                r.landing = l;
                fresh.add(r);
                taken.add(l.anchorSpot());
                break;
            }
        }
        // Shortest ropes first, up to four.
        fresh.sort(Comparator.comparingDouble(r -> SableBridge.toWorld(raider.ship, Vec3.atCenterOf(r.from))
                .distanceTo(SableBridge.toWorld(target, Vec3.atCenterOf(r.landing.anchorSpot())))));
        ropes.clear();
        ropes.addAll(fresh.subList(0, Math.min(4, fresh.size())));
    }

    /** How far the hooks are, on average, from the player ship's middle, compared with the raider's middle (negative = facing it). */
    double hooksFacing(ServerSubLevel target) {
        Vec3 p = SableBridge.position(target), c = SableBridge.toWorld(raider.ship, raider.ref);
        double hooks = raider.hooks.stream().mapToDouble(h -> SableBridge.toWorld(raider.ship, Vec3.atCenterOf(h)).distanceTo(p)).average().orElse(0);
        return hooks - c.distanceTo(p);
    }

    private void fireAnchors(ServerSubLevel target) {
        if (phaseTicks == 1) {
            repairRopes(target);
            if (hooksFacing(target) > 0)
                EmpyreanEngines.LOGGER.warn("Sky Raiders: {}'s hooks are on the far side from the player's ship (check its helm facing)", raider.def.name);
        }
        if (phaseTicks % FIRE_GAP != 1) return;
        int i = shotsFired++;
        if (i >= ropes.size()) {
            if (ropes.stream().noneMatch(r -> r.tied)) { outcome = "The anchors missed."; startFlee(); }
            else setPhase(Phase.BOARDING);
            return;
        }
        Rope rope = ropes.get(i);
        BlockPos anchor = rope.landing.anchorSpot().above();
        if (!level.getBlockState(anchor).getCollisionShape(level, anchor).isEmpty()) return;
        // Too far for a rope (the hook and the spot drifted apart)? Skip it.
        Vec3 hookAt = SableBridge.toWorld(raider.ship, Vec3.atCenterOf(rope.from));
        if (hookAt.distanceTo(SableBridge.toWorld(target, Vec3.atCenterOf(anchor))) > MAX_ROPE) return;
        level.setBlock(anchor, RopeBridge.connectorState(), 3);
        rope.anchor = anchor;
        rope.tied = RopeBridge.isConnector(level, rope.from);
        if (rope.tied) {
            everTied = true;
            RaidManager.syncConnectors(level);
        }
        Vec3 at = SableBridge.toWorld(target, Vec3.atCenterOf(anchor));
        level.playSound(null, BlockPos.containing(at), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.5f, 0.7f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.5f, 0.8f);
        if (!rope.tied) { RopeBridge.removeConnector(level, anchor); rope.anchor = null; }
        else if (i == 0) announce(Component.literal("Boarding lines! Break the anchors!").withStyle(ChatFormatting.RED));
    }

    /** True while both ends stand and the rope is there. */
    boolean ropeUp(Rope r) {
        if (!r.tied) return false;
        if (r.anchor == null || !RopeBridge.isConnector(level, r.anchor) || !RopeBridge.isConnector(level, r.from)) {
            r.tied = false;
            RaidManager.syncConnectors(level);
            return false;
        }
        return true;
    }

    boolean anyRopeUp() {
        for (Rope r : ropes) if (r.tied && ropeUp(r)) return true;
        return false;
    }

    /** The next boarder leaves its post for a hook. */
    private void sendNext() {
        RaiderMob next = null;
        for (RaiderMob m : boardQueue) if (m.alive() && m.state == State.WAITING) { next = m; break; }
        if (next == null) return;
        Rope rope = pickRope(nextRope);
        if (rope == null) return;
        next.rope = ropes.indexOf(rope);
        nextRope = next.rope + 1;
        next.set(State.TO_HOOK);
        next.navTarget = Vec3.atBottomCenterOf(rope.from);
    }

    private void fight(ServerSubLevel target) {
        if (phaseTicks % SEND_GAP == 1) sendNext();
        if (phaseTicks % 20 == 0 && !looting) {
            int onDeck = (int) raiders.values().stream().filter(r -> r.boards() && r.state == State.ON_DECK && r.alive()).count();
            int crew = crewAboard(target).size();
            nobodyAboard = crew == 0 && onDeck > 0 ? nobodyAboard + 20 : 0;
            if (forceOverwhelm || onDeck - crew >= Config.RAID_OVERWHELM.get() || nobodyAboard >= 400) startLooting(target);
        }
        boolean anyLeft = raiders.values().stream().anyMatch(r -> r.boards() && r.alive()
                && r.state != State.HOME && r.state != State.FALLING);
        if (!anyLeft) {
            outcome = gotLoot ? "The raiders got away with your cargo. Chase them down!" : "The raid was repelled.";
            startEscape();
            return;
        }
        if (looting) {
            boolean lootersBusy = raiders.values().stream().anyMatch(r -> r.role == Role.LOOTER && r.alive()
                    && (r.state == State.ON_DECK || r.state == State.RETURNING || r.state == State.CROSSING_OUT));
            if (!lootersBusy) { outcome = gotLoot ? "The raiders got away with your cargo. Chase them down!" : "The raid was repelled."; retreat(); }
        }
        if (phaseTicks > MAX_FIGHT) { outcome = "The raiders gave up."; retreat(); }
    }

    private void startLooting(ServerSubLevel target) {
        looting = true;
        lootList = new DeckScan(level, target).loot();
        for (RaiderMob r : raiders.values()) if (r.role == Role.LOOTER && r.state == State.ON_DECK) assignLoot(r);
        announce(Component.literal("You're overwhelmed - the raiders are going for your cargo!").withStyle(ChatFormatting.GOLD));
    }

    /** Points a looter at the next chest, or sends it home if there's nothing to take. */
    private void assignLoot(RaiderMob r) {
        if (lootList.isEmpty()) { sendHome(r); return; }
        r.target = lootList.get(lootIndex++ % lootList.size());
        r.navTarget = Vec3.atBottomCenterOf(r.target.standOn().above());
    }

    private void retreat() {
        for (RaiderMob r : raiders.values()) if (r.state == State.ON_DECK) sendHome(r);
        setPhase(Phase.RETREAT);
    }

    private void sendHome(RaiderMob r) {
        Rope rope = pickRope(r.rope);
        if (rope == null) { r.navTarget = null; r.set(State.ON_DECK); return; }   // stranded: it fights on
        r.rope = ropes.indexOf(rope);
        r.set(State.RETURNING);
        r.navTarget = Vec3.atBottomCenterOf(rope.landing.stepOff().above());
    }

    private Rope pickRope(int preferred) {
        if (preferred >= 0 && preferred < ropes.size() && ropeUp(ropes.get(preferred))) return ropes.get(preferred);
        for (int n = 0; n < ropes.size(); n++) {
            Rope r = ropes.get((Math.max(0, preferred) + n) % ropes.size());
            if (ropeUp(r)) return r;
        }
        return null;
    }

    /**
     * The boarders are all dead. If an archer is left, it goes to the helm and casts off after 10 seconds
     * (kill it first, or cut the ropes). With nobody left to sail her, the ship is a derelict: it stays
     * alongside while a rope is up, so it can be looted, then drifts and is gone after a few minutes.
     */
    private void startEscape() {
        RaiderMob pilot = nextPilot();
        if (pilot == null) { startDerelict(); return; }
        pilot.set(State.PILOT);
        pilot.navTarget = Vec3.atBottomCenterOf(raider.helm);
        announce(Component.literal("The raiders are getting away!").withStyle(ChatFormatting.RED));
        setPhase(Phase.ESCAPE);
    }

    private RaiderMob nextPilot() {
        RaiderMob best = null;
        double bestD = Double.MAX_VALUE;
        Vec3 helm = SableBridge.toWorld(raider.ship, Vec3.atBottomCenterOf(raider.helm));
        for (RaiderMob m : raiders.values()) {
            if (!m.alive() || m.role != Role.ARCHER) continue;
            double d = m.mob.position().distanceToSqr(helm);
            if (d < bestD) { bestD = d; best = m; }
        }
        return best;
    }

    private void escape() {
        boolean piloted = raiders.values().stream().anyMatch(m -> m.state == State.PILOT && m.alive());
        if (!piloted) {
            RaiderMob next = nextPilot();
            if (next == null) { startDerelict(); return; }
            next.set(State.PILOT);
            next.navTarget = Vec3.atBottomCenterOf(raider.helm);
        }
        if (phaseTicks >= ESCAPE_TICKS) {
            escaped = true;
            outcome = gotLoot ? "The raiders got away with your cargo. Chase them down!" : "The raiders got away.";
            startFlee();
        }
    }

    private void startDerelict() {
        announce(Component.literal("The raider crew is gone - her hold is yours for the taking.").withStyle(ChatFormatting.GREEN));
        finishBar("Sky Raid - Victory", true);
        setPhase(Phase.DERELICT);
    }

    private void startFlee() {
        for (Rope r : ropes) {
            if (r.anchor != null) RopeBridge.removeConnector(level, r.anchor);
            r.tied = false;
        }
        flight.unlock();
        // Boarders still waiting to cross go back to their posts.
        for (RaiderMob m : raiders.values()) if (m.state == State.TO_HOOK) { m.set(State.WAITING); m.navTarget = null; }
        if (!outcome.isEmpty()) announce(Component.literal(outcome).withStyle(gotLoot ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        finishBar(gotLoot ? "Sky Raid - Defeat" : escaped ? "Sky Raid - Raiders Escaped" : "Sky Raid - Victory", !gotLoot && !escaped);
        setPhase(Phase.FLEE);
        RaidManager.syncConnectors(level);
    }

    // --- the raid bar and horn -------------------------------------------------------------

    /** Like a village raid: the bar fills during the warning, then falls as the crew is killed. */
    private void updateBar(ServerSubLevel target) {
        if (barHideAt >= 0) {
            if (totalTicks >= barHideAt) bar.removeAllPlayers();
            return;
        }
        if (phase == Phase.WARNING) bar.setProgress(Math.min(1f, phaseTicks / (float) WARNING_TICKS));
        else if (crewHealth > 0) {
            float left = 0;
            int count = 0;
            for (RaiderMob m : raiders.values()) {
                if (!m.alive() || m.state == State.HOME) continue;
                left += m.mob.getHealth();
                count++;
            }
            bar.setProgress(Math.max(0f, Math.min(1f, left / crewHealth)));
            String name = phase == Phase.ESCAPE ? "Sky Raid - The raiders are getting away!" : "Sky Raid - Raiders Remaining: " + count;
            if (!bar.getName().getString().equals(name)) bar.setName(Component.literal(name));
        }
        if (totalTicks % 20 == 1) {   // who sees it: everyone near the ship being raided
            Vec3 c = SableBridge.position(target);
            for (ServerPlayer p : level.players()) {
                boolean near = p.position().distanceTo(c) < 160;
                if (near && !bar.getPlayers().contains(p)) bar.addPlayer(p);
                else if (!near && bar.getPlayers().contains(p)) bar.removePlayer(p);
            }
            for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) if (p.level() != level || p.isRemoved()) bar.removePlayer(p);
        }
    }

    /** The raid's over (one way or the other): the bar says so for a few seconds, then goes. */
    private void finishBar(String title, boolean won) {
        bar.setName(Component.literal(title));
        if (won) bar.setProgress(0);
        barHideAt = totalTicks + 100;
    }

    /** The raid horn, as a village raid sounds it: from the direction the raiders will come, for everyone nearby. */
    private void horn(ServerSubLevel target) {
        Vec3 from = SableBridge.toWorld(raider.ship, raider.ref), c = SableBridge.position(target);
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(c) >= 160) continue;
            Vec3 dir = from.subtract(p.position()).normalize().scale(13);
            p.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.NEUTRAL,
                    p.getX() + dir.x, p.getY() + dir.y, p.getZ() + dir.z, 64f, 1f, level.random.nextLong()));
        }
    }

    // --- raiders ----------------------------------------------------------------------------

    private void updateRaiders(ServerSubLevel target) {
        for (RaiderMob r : raiders.values()) {
            Mob mob = r.mob;
            r.stateTicks++;
            if (r.unloaded()) continue;   // its chunk unloaded; it's back when the chunk loads
            if (!mob.isAlive()) {
                if (!r.carried.isEmpty()) {
                    for (ItemStack s : r.carried) level.addFreshEntity(new ItemEntity(level, r.lastPos.x, r.lastPos.y, r.lastPos.z, s));
                    r.carried.clear();
                }
                continue;
            }
            r.lastPos = mob.position();
            // Fell (or was knocked) well clear of both ships: no longer part of the raid.
            if (r.state != State.HOME && lost(mob, target)) {
                r.carried.clear();
                mob.discard();
                continue;
            }
            switch (r.state) {
                case TO_HOOK -> {
                    Rope rope = r.rope >= 0 ? ropes.get(r.rope) : null;
                    if (rope == null || !ropeUp(rope)) {
                        rope = pickRope(r.rope);
                        if (rope == null) { r.set(State.WAITING); r.navTarget = null; break; }
                        r.rope = ropes.indexOf(rope);
                        r.navTarget = Vec3.atBottomCenterOf(rope.from);
                    }
                    Vec3 hook = SableBridge.toWorld(raider.ship, Vec3.atBottomCenterOf(rope.from));
                    if (mob.position().distanceTo(hook) < 2.2 || r.stateTicks > TO_HOOK_MAX) {
                        r.set(State.CROSSING_IN);
                        r.along = 0;
                        r.navTarget = null;
                        mob.getNavigation().stop();
                        mob.setNoGravity(true);
                    }
                }
                case CROSSING_IN -> cross(r, target, true);
                case CROSSING_OUT -> cross(r, target, false);
                case ON_DECK -> {
                    if (looting && r.role == Role.LOOTER && r.carried.isEmpty()) {
                        if (r.target == null) assignLoot(r);
                        else tryTake(r, target);
                    }
                }
                case RETURNING -> {
                    Rope rope = r.rope >= 0 && r.rope < ropes.size() ? ropes.get(r.rope) : null;
                    if (rope == null || !ropeUp(rope)) { sendHome(r); break; }
                    Vec3 step = SableBridge.toWorld(target, Vec3.atBottomCenterOf(rope.landing.stepOff().above()));
                    if (mob.position().distanceTo(step) < 1.6) {
                        r.set(State.CROSSING_OUT);
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

    private boolean lost(Mob mob, ServerSubLevel target) {
        double d = Config.RAID_LOST_DISTANCE.get();
        double d2 = d * d;
        return SableBridge.bounds(raider.ship).distanceToSqr(mob.position()) > d2 && SableBridge.bounds(target).distanceToSqr(mob.position()) > d2;
    }

    /** Moves a raider along its rope: in from the raider ship, or out from the anchor. */
    private void cross(RaiderMob r, ServerSubLevel target, boolean in) {
        Rope rope = ropes.get(r.rope);
        Mob mob = r.mob;
        if (!ropeUp(rope)) {   // the rope was cut under them
            mob.setNoGravity(false);
            r.set(State.FALLING);
            return;
        }
        List<Vec3> path = rope.line().points(level, dev.ryanhcode.sable.sublevel.SubLevel::logicalPose);
        if (path.isEmpty()) return;
        if (!in) Collections.reverse(path);
        double length = 0;
        for (int i = 1; i < path.size(); i++) length += path.get(i).distanceTo(path.get(i - 1));
        r.along += ROPE_SPEED;
        if (r.along >= length - 0.4) {
            mob.setNoGravity(false);
            mob.setDeltaMovement(Vec3.ZERO);
            if (in) {
                Vec3 land = SableBridge.toWorld(target, Vec3.atBottomCenterOf(rope.landing.stepOff().above()));
                mob.teleportTo(land.x, land.y, land.z);
                r.set(State.ON_DECK);
            } else {
                deposit(r);
                r.set(State.HOME);
                mob.discard();
            }
            return;
        }
        Vec3 at = pointAlong(path, r.along), ahead = pointAlong(path, r.along + 0.5);
        Vec3 sway = new Vec3(0, 0.08 * Math.sin((totalTicks + mob.getId()) * 0.3), 0);
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
    private void tryTake(RaiderMob r, ServerSubLevel target) {
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

    /** Stolen goods go into the raider ship's hold, so catching it gets them back. */
    private void deposit(RaiderMob r) {
        if (r.carried.isEmpty()) return;
        gotLoot = true;
        IItemHandler hold = raider.cargo == null ? null : level.getCapability(Capabilities.ItemHandler.BLOCK, raider.cargo, null);
        for (ItemStack s : r.carried) {
            ItemStack left = hold == null ? s : ItemHandlerHelper.insertItemStacked(hold, s, false);
            if (!left.isEmpty()) {
                BlockPos at = raider.cargo != null ? raider.cargo : raider.hooks.get(0);
                Vec3 p = SableBridge.toWorld(raider.ship, Vec3.atCenterOf(at));
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

    /** World positions of the anchors on the player's deck that are still up. */
    List<Vec3> anchorPositions() {
        List<Vec3> out = new ArrayList<>();
        ServerSubLevel target = target();
        if (target == null) return out;
        for (Rope r : ropes) if (r.tied && r.anchor != null) out.add(SableBridge.toWorld(target, Vec3.atCenterOf(r.anchor)));
        return out;
    }

    /** True for either end of any of this raid's ropes (or its hooks before they're tied). */
    boolean isBoardingConnector(BlockPos pos) {
        if (phase == Phase.DONE) return false;
        for (Rope r : ropes) if (pos.equals(r.anchor) || pos.equals(r.from)) return true;
        return raider.hooks.contains(pos);
    }

    /** The ropes that are tied. */
    void allRopes(List<BoardingRope> out) {
        if (phase == Phase.DONE) return;
        for (Rope r : ropes) if (r.tied && r.anchor != null) out.add(r.line());
    }

    void allConnectors(List<BlockPos> out) {
        if (phase == Phase.DONE) return;
        out.addAll(raider.hooks);
        for (Rope r : ropes) if (r.anchor != null) out.add(r.anchor);
    }

    void announce(Component msg) {
        ServerSubLevel target = target();
        Vec3 c = target == null ? Vec3.ZERO : SableBridge.position(target);
        for (ServerPlayer p : level.players())
            if (p.position().distanceTo(c) < 160) p.displayClientMessage(msg, true);
    }

    boolean owns(UUID mob) { return raiders.containsKey(mob); }

    /** Removes every raider of this raid, wherever it is (used by /skyraid raids stop). */
    void discardAll() {
        for (RaiderMob r : raiders.values()) if (r.alive()) r.mob.discard();
    }

    /**
     * Ends the raid. The raider ship is left as an ordinary ship (no longer the raiders', not deleted by the
     * clean-up), with whoever is still alive aboard as ordinary mobs, unless {@code removeShip} (/skyraid stop).
     */
    void end(String why, boolean removeShip) {
        if (phase == Phase.DONE) return;
        for (Rope r : ropes) {
            if (r.anchor != null) RopeBridge.removeConnector(level, r.anchor);
        }
        for (RaiderMob r : raiders.values()) {
            if (!r.mob.isAlive()) continue;
            if (removeShip && r.state != State.ON_DECK && r.state != State.RETURNING && r.state != State.FALLING) { r.mob.discard(); continue; }
            r.mob.removeTag(RaidManager.TAG);   // an ordinary mob now
            r.mob.setNoGravity(false);
        }
        if (removeShip) SableBridge.remove(raider.ship);
        else if (!raider.isRemoved()) {
            net.minecraft.nbt.CompoundTag tag = raider.ship.getUserDataTag();
            if (tag != null) { tag = tag.copy(); tag.remove(RaiderShip.MARK); raider.ship.setUserDataTag(tag); }
        }
        bar.removeAllPlayers();
        phase = Phase.DONE;
        RaidManager.syncConnectors(level);
        EmpyreanEngines.LOGGER.info("Sky Raiders: raid {} ended ({})", id, why);
    }

    /** A raider of ours loaded back in (its chunk unloaded as the ship flew): carry on with the new entity. */
    void rebind(Mob mob) {
        RaiderMob m = raiders.get(mob.getUUID());
        if (m == null || m.mob == mob) return;
        m.mob = mob;
        if (mob instanceof Witch witch) WitchGoal.install(witch, m);
        else mob.goalSelector.addGoal(0, new RaiderGoal(m));
        if (m.state == State.CROSSING_IN || m.state == State.CROSSING_OUT) mob.setNoGravity(true);
    }

    String status() {
        long alive = raiders.values().stream().filter(r -> r.alive() && r.state != State.HOME).count();
        long up = ropes.stream().filter(this::ropeUp).count();
        long waiting = boardQueue.stream().filter(m -> m.alive() && m.state == State.WAITING).count();
        return raider.def.name + ": phase " + phase + ", " + up + "/" + ropes.size() + " ropes up, " + alive + " raiders alive, "
                + waiting + " waiting to board, looting " + looting + ", loot taken " + gotLoot + "\nflight: " + flight.status()
                + (target() == null ? "" : String.format(", hooks %s", hooksFacing(target()) <= 0 ? "face you" : "FACE AWAY (helm facing wrong?)"));
    }
}
