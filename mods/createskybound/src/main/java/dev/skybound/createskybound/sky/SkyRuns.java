package dev.skybound.createskybound.sky;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.skybound.createskybound.Config;
import dev.skybound.createskybound.CreateSkybound;
import dev.skybound.createskybound.ModRegistry;
import dev.skybound.createskybound.launch.Platform;
import dev.skybound.createskybound.launch.SableBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Timed runs into the Chaotic Skies.
 *
 * For now every run gets its own unexplored region of one shared dimension
 * (runs are spaced 30,000 blocks apart), which plays like a fresh world per launch.
 * Separate per-launch dimensions come later.
 */
public final class SkyRuns extends SavedData {
    public static final ResourceKey<Level> CHAOTIC_SKIES = ResourceKey.create(Registries.DIMENSION, CreateSkybound.id("chaotic_skies"));
    private static final int REGION_SPACING = 30_000;
    private static final int ARRIVAL_Y = 200;

    static final class Run {
        int index;
        Set<UUID> players = new HashSet<>();
        ResourceKey<Level> originDim;
        BlockPos origin;          // the launcher (Vector Planar Computer)
        BlockPos arrival;         // the return anchor in the Chaotic Skies
        long endTick;             // server game time when it collapses
        int tier;
        transient ServerBossEvent bar;
        transient int settleTicks;    // hold the ship still for a moment after it arrives
    }

    private final List<Run> runs = new ArrayList<>();
    private int nextIndex = 1;

    public static SkyRuns get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SkyRuns::new, SkyRuns::load, null), "createskybound_runs");
    }

    // --- starting a run --------------------------------------------------------------

    public static List<ServerPlayer> playersAboard(ServerLevel level, ServerSubLevel ship) {
        AABB box = SableBridge.bounds(ship).inflate(2);
        return level.getEntitiesOfClass(ServerPlayer.class, box);
    }

    public static void start(ServerLevel from, BlockPos launcher, Platform platform, ServerSubLevel ship, List<ServerPlayer> crew) {
        MinecraftServer server = from.getServer();
        ServerLevel sky = server.getLevel(CHAOTIC_SKIES);
        if (sky == null) {
            CreateSkybound.LOGGER.error("The Chaotic Skies dimension is missing");
            return;
        }
        SkyRuns data = get(server);
        Run run = new Run();
        run.index = data.nextIndex++;
        run.originDim = from.dimension();
        run.origin = launcher;
        run.arrival = new BlockPos(run.index * REGION_SPACING, ARRIVAL_Y, 0);
        run.tier = platform.tier();
        run.endTick = server.overworld().getGameTime() + Config.runMinutes(run.tier) * 60L * 20L;
        buildArrival(sky, run.arrival);

        // Move the ship, then put the crew back where they were on it.
        Vec3 shipPos = SableBridge.position(ship);
        Vec3 target = Vec3.atCenterOf(run.arrival).add(0, 12, 0);
        List<Vec3> offsets = new ArrayList<>();
        for (ServerPlayer p : crew) offsets.add(p.position().subtract(shipPos));
        SableBridge.warp(ship, sky, target);
        run.settleTicks = 60;
        for (int i = 0; i < crew.size(); i++) {
            ServerPlayer p = crew.get(i);
            Vec3 at = target.add(offsets.get(i));
            p.teleportTo(sky, at.x, at.y, at.z, p.getYRot(), p.getXRot());
            p.resetFallDistance();
            run.players.add(p.getUUID());
            title(p, Component.literal("THE CHAOTIC SKIES").withStyle(ChatFormatting.AQUA),
                    Component.literal(Config.runMinutes(run.tier) + " minutes until the dimension destabilises"));
        }
        data.runs.add(run);
        data.setDirty();
        CreateSkybound.LOGGER.info("Sky run {} started with {} players", run.index, crew.size());
    }

    private static void buildArrival(ServerLevel sky, BlockPos at) {
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                sky.setBlockAndUpdate(at.offset(dx, -1, dz), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        sky.setBlockAndUpdate(at, ModRegistry.RETURN_ANCHOR.get().defaultBlockState());
    }

    // --- leaving ------------------------------------------------------------------

    private Run runOf(UUID player) {
        for (Run r : runs) if (r.players.contains(player)) return r;
        return null;
    }

    /** Right-clicking the return anchor: back to the launcher you left from. Your ship comes too if it's close. */
    public static void returnHome(ServerPlayer player, BlockPos anchor) {
        SkyRuns data = get(player.server);
        Run run = data.runOf(player.getUUID());
        ServerLevel home = run == null ? player.server.overworld() : player.server.getLevel(run.originDim);
        if (home == null) home = player.server.overworld();
        BlockPos dest = run == null ? home.getSharedSpawnPos() : run.origin.above(2);

        if (player.level() instanceof ServerLevel sky) {
            ServerSubLevel ship = SableBridge.findShip(sky, new AABB(anchor).inflate(24));
            if (ship != null) {
                List<ServerPlayer> crew = playersAboard(sky, ship);
                Vec3 shipPos = SableBridge.position(ship);
                Vec3 target = Vec3.atCenterOf(dest).add(0, 10, 0);
                List<Vec3> offsets = new ArrayList<>();
                for (ServerPlayer p : crew) offsets.add(p.position().subtract(shipPos));
                SableBridge.warp(ship, home, target);
                for (int i = 0; i < crew.size(); i++) {
                    ServerPlayer p = crew.get(i);
                    Vec3 at = target.add(offsets.get(i));
                    p.teleportTo(home, at.x, at.y, at.z, p.getYRot(), p.getXRot());
                    data.leave(p);
                }
            }
        }
        if (player.level().dimension().equals(CHAOTIC_SKIES)) {
            player.teleportTo(home, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, player.getYRot(), player.getXRot());
            data.leave(player);
        }
    }

    /** Where the Overworld sky is "below" a point in a run's region. */
    private static Vec3 dropPoint(Run run, Vec3 pos) {
        double x = (run == null ? pos.x : run.origin.getX() + (pos.x - run.arrival.getX()));
        double z = (run == null ? pos.z : run.origin.getZ() + (pos.z - run.arrival.getZ()));
        return new Vec3(x, Config.OVERWORLD_DROP_HEIGHT.get(), z);
    }

    private void leave(ServerPlayer player) {
        Run run = runOf(player.getUUID());
        if (run == null) return;
        run.players.remove(player.getUUID());
        if (run.bar != null) run.bar.removePlayer(player);
        setDirty();
    }

    // --- ticking -------------------------------------------------------------------

    public static final class Events {
        @SubscribeEvent
        public static void onTick(ServerTickEvent.Post e) {
            MinecraftServer server = e.getServer();
            ServerLevel sky = server.getLevel(CHAOTIC_SKIES);
            if (sky == null) return;
            SkyRuns data = get(server);
            long now = server.overworld().getGameTime();
            int exitY = Config.EXIT_HEIGHT.get();

            // Falling out: ships first (with their crew), then anyone else.
            if (now % 10 == 0) {
                for (ServerSubLevel ship : SableBridge.allShips(sky)) {
                    Vec3 pos = SableBridge.position(ship);
                    if (pos.y >= exitY) continue;
                    List<ServerPlayer> crew = playersAboard(sky, ship);
                    Run run = crew.isEmpty() ? null : data.runOf(crew.get(0).getUUID());
                    ServerLevel home = run == null ? server.overworld() : server.getLevel(run.originDim);
                    if (home == null) home = server.overworld();
                    Vec3 drop = dropPoint(run, pos);
                    List<Vec3> offsets = new ArrayList<>();
                    for (ServerPlayer p : crew) offsets.add(p.position().subtract(pos));
                    SableBridge.warp(ship, home, drop);
                    for (int i = 0; i < crew.size(); i++) {
                        ServerPlayer p = crew.get(i);
                        Vec3 at = drop.add(offsets.get(i));
                        p.teleportTo(home, at.x, at.y, at.z, p.getYRot(), p.getXRot());
                        data.leave(p);
                    }
                }
                for (ServerPlayer p : new ArrayList<>(sky.players())) {
                    if (p.getY() >= exitY) continue;
                    Run run = data.runOf(p.getUUID());
                    ServerLevel home = run == null ? server.overworld() : server.getLevel(run.originDim);
                    if (home == null) home = server.overworld();
                    Vec3 drop = dropPoint(run, p.position());
                    p.teleportTo(home, drop.x, drop.y, drop.z, p.getYRot(), p.getXRot());
                    p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 20));
                    data.leave(p);
                }
            }

            // Timers, warnings and collapse.
            int warn = Config.WARNING_SECONDS.get();
            for (Iterator<Run> it = data.runs.iterator(); it.hasNext(); ) {
                Run run = it.next();
                if (run.settleTicks > 0) {
                    run.settleTicks--;
                    ServerSubLevel arrived = SableBridge.findShip(sky, new AABB(run.arrival).inflate(24, 32, 24));
                    if (arrived != null) SableBridge.holdStill(arrived);
                }
                long left = run.endTick - now;
                List<ServerPlayer> inside = new ArrayList<>();
                for (UUID id : run.players) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null && p.level() == sky) inside.add(p);
                }
                if (run.bar == null) {
                    run.bar = new ServerBossEvent(Component.literal("Dimension stability"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
                }
                for (ServerPlayer p : inside) if (!run.bar.getPlayers().contains(p)) run.bar.addPlayer(p);
                long total = Config.runMinutes(run.tier) * 60L * 20L;
                run.bar.setProgress(Math.max(0f, Math.min(1f, left / (float) total)));
                long secs = Math.max(0, left / 20);
                run.bar.setName(Component.literal(String.format("Dimension stability  %d:%02d", secs / 60, secs % 60)));

                if (left <= warn * 20L) {
                    run.bar.setColor(BossEvent.BossBarColor.RED);
                    if (now % 40 == 0) {
                        for (ServerPlayer p : inside) {
                            title(p, Component.literal("DIMENSION DESTABILISING").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                                    Component.literal("ESCAPE NOW").withStyle(ChatFormatting.RED));
                            sky.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 2f, 0.5f);
                        }
                    }
                    if (left <= 60 * 20L && now % 20 == 0) {
                        for (ServerPlayer p : inside) sky.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 2f, 0.5f);
                    }
                }
                if (left <= 0) {
                    for (ServerPlayer p : inside) {
                        p.sendSystemMessage(Component.literal("The Chaotic Skies collapsed around you.").withStyle(ChatFormatting.DARK_RED));
                        p.kill();
                    }
                    run.bar.removeAllPlayers();
                    it.remove();
                    data.setDirty();
                    CreateSkybound.LOGGER.info("Sky run {} collapsed", run.index);
                } else if (inside.isEmpty() && now % 200 == 0) {
                    // Everyone got out (or logged off): keep the run until it collapses, so logging out can't dodge it.
                    boolean anyOnline = run.players.stream().anyMatch(id -> server.getPlayerList().getPlayer(id) != null);
                    if (!anyOnline && run.players.isEmpty()) {
                        run.bar.removeAllPlayers();
                        it.remove();
                        data.setDirty();
                    }
                }
            }
        }

        private Events() {}
    }

    private static void title(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    // --- save ------------------------------------------------------------------------

    private static SkyRuns load(CompoundTag tag, HolderLookup.Provider lookup) {
        SkyRuns d = new SkyRuns();
        d.nextIndex = Math.max(1, tag.getInt("NextIndex"));
        ListTag list = tag.getList("Runs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Run r = new Run();
            r.index = t.getInt("Index");
            ListTag players = t.getList("Players", Tag.TAG_INT_ARRAY);
            for (Tag p : players) r.players.add(NbtUtils.loadUUID(p));
            r.originDim = ResourceKey.create(Registries.DIMENSION, net.minecraft.resources.ResourceLocation.parse(t.getString("OriginDim")));
            r.origin = BlockPos.of(t.getLong("Origin"));
            r.arrival = BlockPos.of(t.getLong("Arrival"));
            r.endTick = t.getLong("EndTick");
            r.tier = t.getInt("Tier");
            d.runs.add(r);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("NextIndex", nextIndex);
        ListTag list = new ListTag();
        for (Run r : runs) {
            CompoundTag t = new CompoundTag();
            t.putInt("Index", r.index);
            ListTag players = new ListTag();
            for (UUID id : r.players) players.add(NbtUtils.createUUID(id));
            t.put("Players", players);
            t.putString("OriginDim", r.originDim.location().toString());
            t.putLong("Origin", r.origin.asLong());
            t.putLong("Arrival", r.arrival.asLong());
            t.putLong("EndTick", r.endTick);
            t.putInt("Tier", r.tier);
            list.add(t);
        }
        tag.put("Runs", list);
        return tag;
    }
}
