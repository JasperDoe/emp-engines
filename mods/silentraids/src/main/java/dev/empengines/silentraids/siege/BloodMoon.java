package dev.empengines.silentraids.siege;

import dev.empengines.silentraids.ApothBridge;
import dev.empengines.silentraids.Config;
import dev.empengines.silentraids.SableCheck;
import dev.empengines.silentraids.tier.PlayerTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import dev.empengines.silentraids.net.BloodMoonSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The blood moon: every few nights (5 by default) in the Overworld. A warning at dusk the evening before, beds
 * don't work on the night, and from nightfall to dawn every player who is online, in the Overworld and on the
 * ground gets a siege: waves of raiders spawning 30-40 blocks away that come for them and break in through
 * plain blocks. Players on a Sable ship or flying don't get waves (and a siege pauses while they're up there).
 */
public final class BloodMoon {
    private static final String DONE_DAY = "silentraids.siegeDoneDay";
    private static final Map<UUID, Siege> SIEGES = new HashMap<>();
    /** The bar that fills at dusk on a blood moon day, until the moon is up. */
    private static final ServerBossEvent RISING = new ServerBossEvent(
            Component.literal("A Night Raid is coming - beds won't help you tonight").withStyle(ChatFormatting.RED),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private static boolean wasActive;
    /** Game time until which an admin's /silentraids bloodmoon start keeps the blood moon up. */
    private static long forcedUntil = -1;

    // --- saved bits (so a restart doesn't repeat the warning) ---------------------------------------

    static final class Data extends SavedData {
        long warnedDay = -1, risingDay = -1;

        static Data load(CompoundTag tag, HolderLookup.Provider p) {
            Data d = new Data();
            d.warnedDay = tag.getLong("warnedDay");
            d.risingDay = tag.getLong("risingDay");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
            tag.putLong("warnedDay", warnedDay);
            tag.putLong("risingDay", risingDay);
            return tag;
        }

        static Data get(ServerLevel overworld) {
            return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "silentraids_bloodmoon");
        }
    }

    // --- time -----------------------------------------------------------------------------------------

    private static long day(ServerLevel ow) { return ow.getDayTime() / 24000L; }
    private static int time(ServerLevel ow) { return (int) (ow.getDayTime() % 24000L); }

    private static boolean isBloodDay(long day) {
        int n = Config.INTERVAL.get();
        return (day + 1) % n == 0;
    }

    /** Nights until the next blood moon (0 = tonight). */
    public static int nightsUntil(ServerLevel ow) {
        long d = day(ow);
        if (time(ow) >= 23000) d++;
        for (int i = 0; i < 1000; i++) if (isBloodDay(d + i)) return i;
        return -1;
    }

    public static boolean active(ServerLevel ow) {
        if (forcedUntil > ow.getGameTime()) return true;
        int t = time(ow);
        return isBloodDay(day(ow)) && t >= 13000 && t < 23000;
    }

    /** From dusk on the blood moon day: beds are off. */
    private static boolean bedsOff(ServerLevel ow) {
        if (forcedUntil > ow.getGameTime()) return true;
        int t = time(ow);
        return isBloodDay(day(ow)) && t >= 12000 && t < 23000;
    }

    public static void force(ServerLevel ow, boolean on) {
        forcedUntil = on ? ow.getGameTime() + 12000 : -1;
    }

    // --- tick -----------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        ServerLevel ow = server.overworld();
        if (ow.getGameTime() % 20 != 0) return;
        long day = day(ow);
        int t = time(ow);
        Data data = Data.get(ow);

        // The evening before
        if (isBloodDay(day + 1) && t >= 12000 && t < 23000 && data.warnedDay != day) {
            data.warnedDay = day;
            data.setDirty();
            broadcast(ow, Component.literal("Raiders will come tomorrow night. Fortify your base, or take to the skies.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        }
        boolean active = active(ow);
        // Dusk on the night: the red bar fills until the moon is up
        boolean rising = !active && isBloodDay(day) && t >= 11000 && t < 13000;
        if (rising) {
            RISING.setProgress(Math.min(1f, (t - 11000) / 2000f));
            for (ServerPlayer p : ow.players()) RISING.addPlayer(p);
            for (ServerPlayer p : List.copyOf(RISING.getPlayers())) if (p.level() != ow) RISING.removePlayer(p);
            if (data.risingDay != day) {
                data.risingDay = day;
                data.setDirty();
                for (ServerPlayer p : ow.players()) p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.5f, 0.6f);
            }
        } else if (!RISING.getPlayers().isEmpty()) {
            RISING.removeAllPlayers();
        }
        BloodMoonSync.set(server, active || rising);

        if (active && !wasActive) started(ow);
        if (!active && wasActive) ended(ow);
        wasActive = active;
        if (active) run(ow, day);
        Breaching.tidy(ow);
    }

    private static void started(ServerLevel ow) {
        RISING.removeAllPlayers();
        for (ServerPlayer p : ow.players()) p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0f, 0.7f);
    }

    private static void ended(ServerLevel ow) {
        int removed = 0;
        for (Siege s : SIEGES.values()) {
            for (UUID id : s.mobs) {
                Entity m = ow.getEntity(id);
                if (m != null && m.isAlive()) {
                    ow.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + 1, m.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                    m.discard();
                    removed++;
                }
            }
        }
        for (Siege s : SIEGES.values()) s.bar.removeAllPlayers();
        SIEGES.clear();
        Breaching.clearAll(ow);
        forcedUntil = -1;
        broadcast(ow, Component.literal("The night raid is over." + (removed > 0 ? " The last raiders melt into the dawn." : "")).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** Online, alive, survival/adventure, in the Overworld, on the ground and not on a ship. */
    static boolean eligible(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || p.isCreative()) return false;
        if (p.getAbilities().flying || p.isFallFlying()) return false;
        ServerLevel level = p.serverLevel();
        if (level.dimension() != Level.OVERWORLD) return false;
        if (SableCheck.onShip(level, p.getBoundingBox().inflate(1.5, 3, 1.5))) return false;
        // Something solid within 4 blocks below the feet
        BlockPos feet = p.blockPosition();
        for (int dy = 0; dy <= 4; dy++) {
            BlockPos b = feet.below(dy);
            if (!level.getBlockState(b).getCollisionShape(level, b).isEmpty()) return true;
        }
        return false;
    }

    private static void run(ServerLevel ow, long day) {
        long now = ow.getGameTime();
        boolean forced = forcedUntil > now;
        List<ServerPlayer> ready = new ArrayList<>();
        for (ServerPlayer p : ow.players()) {
            if (!forced && p.getPersistentData().getLong(DONE_DAY) == day + 1) continue;   // beat tonight's waves already
            if (eligible(p)) ready.add(p);
        }

        // Group players: each joins a nearby siege, or starts one
        int share = Config.GROUP_RADIUS.get();
        for (ServerPlayer p : ready) {
            if (siegeOf(p.getUUID()) != null) continue;
            Siege near = null;
            for (Siege s : SIEGES.values()) {
                if (s.done) continue;
                for (UUID m : s.members) {
                    ServerPlayer o = ow.getServer().getPlayerList().getPlayer(m);
                    if (o != null && o.level() == ow && o.distanceToSqr(p) < (double) share * share) { near = s; break; }
                }
                if (near != null) break;
            }
            double threat = PlayerTier.threat(p);
            if (near != null) {
                near.join(p.getUUID(), threat);
            } else {
                Siege s = new Siege(p.getUUID(), threat);
                SIEGES.put(s.id, s);
            }
        }

        for (Iterator<Siege> it = SIEGES.values().iterator(); it.hasNext(); ) {
            Siege s = it.next();
            // Living raiders
            s.mobs.removeIf(id -> {
                Entity m = ow.getEntity(id);
                return m == null || !m.isAlive();
            });
            List<ServerPlayer> present = new ArrayList<>();
            for (UUID m : s.members) {
                ServerPlayer o = ow.getServer().getPlayerList().getPlayer(m);
                if (o != null && ready.contains(o)) present.add(o);
            }
            retarget(ow, s);

            if (s.done) {
                // Keep the siege (so its players don't get a new one tonight); hide the bar after 10 s
                if (now - s.doneTick > 200) s.bar.removeAllPlayers();
                else updateBarPlayers(ow, s);
                continue;
            }
            updateBarPlayers(ow, s);
            if (s.inWave) {
                boolean last = s.wavesSpawned >= s.totalWaves;
                boolean beaten = s.mobs.size() <= Math.ceil(s.lastWaveSize * 0.25);
                boolean timedOut = now - s.waveStartTick > Config.WAVE_TIMEOUT.get() * 20L;
                if (last && s.mobs.isEmpty()) {
                    s.done = true;
                    s.doneTick = now;
                    s.inWave = false;
                    s.bar.setName(Component.literal("Night Raid - Survived!").withStyle(ChatFormatting.GOLD));
                    s.bar.setColor(BossEvent.BossBarColor.YELLOW);
                    s.bar.setProgress(1f);
                    survived(ow, s, day);
                    continue;
                }
                if (!last && (beaten || timedOut)) {
                    s.inWave = false;
                    s.approachTicks = Siege.APPROACH_TICKS;
                }
            } else if (!present.isEmpty() && s.mobs.size() < Config.MAX_ALIVE.get()) {
                s.approachTicks -= 20;
                if (s.approachTicks <= 0) spawnNext(ow, s, present, now);
            }
            refreshBar(s, present.isEmpty());
        }
    }

    private static void spawnNext(ServerLevel ow, Siege s, List<ServerPlayer> present, long now) {
        ServerPlayer anchor = present.stream().filter(p -> p.getUUID().equals(s.leader)).findFirst().orElse(present.get(ow.random.nextInt(present.size())));
        int wave = s.wavesSpawned;
        boolean last = wave == s.totalWaves - 1;
        int size = Math.min(RaidMobs.waveSize(s.threat, wave, s.members.size()), Config.MAX_ALIVE.get() - s.mobs.size());
        List<Entity> spawned = RaidMobs.spawnWave(ow, s, anchor, wave, Math.max(1, size), last);
        for (Entity m : spawned) s.mobs.add(m.getUUID());
        s.wavesSpawned++;
        s.lastWaveSize = Math.max(1, spawned.size());
        s.waveStartTick = now;
        s.inWave = true;
        for (ServerPlayer p : present) p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6f, 1.0f);
    }

    /** Raid-style bar: fills while a wave approaches, drains as its raiders die. */
    private static void refreshBar(Siege s, boolean waiting) {
        int wave = s.inWave ? s.wavesSpawned : s.wavesSpawned + 1;
        String waves = "Wave " + Math.min(wave, s.totalWaves) + " of " + s.totalWaves;
        if (s.inWave) {
            s.bar.setName(Component.literal("Night Raid - " + waves + " - Raiders: " + s.mobs.size()).withStyle(ChatFormatting.RED));
            s.bar.setProgress(Math.max(0f, Math.min(1f, s.mobs.size() / (float) s.lastWaveSize)));
        } else {
            String tail = waiting ? " (they wait for you to land)" : (wave == s.totalWaves ? " - the final wave" : " approaching");
            s.bar.setName(Component.literal("Night Raid - " + waves + tail).withStyle(ChatFormatting.DARK_RED));
            s.bar.setProgress(Math.max(0f, Math.min(1f, 1f - s.approachTicks / (float) Siege.APPROACH_TICKS)));
        }
    }

    /** The siege's players in the Overworld see its bar. */
    private static void updateBarPlayers(ServerLevel ow, Siege s) {
        for (UUID m : s.members) {
            ServerPlayer o = ow.getServer().getPlayerList().getPlayer(m);
            if (o != null && o.level() == ow) s.bar.addPlayer(o);
        }
        for (ServerPlayer p : List.copyOf(s.bar.getPlayers())) if (p.level() != ow || !s.members.contains(p.getUUID())) s.bar.removePlayer(p);
    }

    /** Raiders always go for the nearest member of their siege. */
    private static void retarget(ServerLevel ow, Siege s) {
        for (UUID id : s.mobs) {
            if (!(ow.getEntity(id) instanceof Mob mob)) continue;
            LivingEntity t = mob.getTarget();
            if (t instanceof Player && t.isAlive() && !((Player) t).isCreative()) continue;
            ServerPlayer best = null;
            double bestD = Double.MAX_VALUE;
            for (UUID m : s.members) {
                ServerPlayer o = ow.getServer().getPlayerList().getPlayer(m);
                if (o == null || o.level() != ow || !o.isAlive() || o.isCreative() || o.isSpectator()) continue;
                double d = o.distanceToSqr(mob);
                if (d < bestD && d < 96 * 96) { bestD = d; best = o; }
            }
            if (best != null) mob.setTarget(best);
        }
    }

    private static void survived(ServerLevel ow, Siege s, long day) {
        for (UUID m : s.members) {
            ServerPlayer p = ow.getServer().getPlayerList().getPlayer(m);
            if (p == null) continue;
            p.getPersistentData().putLong(DONE_DAY, day + 1);
            p.sendSystemMessage(Component.literal("You held out against the night raid!").withStyle(ChatFormatting.GOLD));
            if (p.getRandom().nextDouble() < Config.SURVIVE_SIGIL.get()) {
                ItemStack sigil = ApothBridge.sigil(1);
                if (!sigil.isEmpty()) {
                    if (!p.getInventory().add(sigil)) p.drop(sigil, false);
                    p.sendSystemMessage(Component.literal("You found a Sigil of Socketing among the fallen.").withStyle(ChatFormatting.AQUA));
                }
            }
        }
    }

    private static Siege siegeOf(UUID player) {
        for (Siege s : SIEGES.values()) if (s.members.contains(player)) return s;
        return null;
    }

    /** First hit on a block: tell the siege's players (at most every 30 s). */
    static void breachStarted(Mob mob, BlockPos pos) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        String id = mob.getPersistentData().getString(RaidMobs.RAIDER);
        Siege s;
        try { s = SIEGES.get(UUID.fromString(id)); } catch (IllegalArgumentException ex) { return; }
        if (s == null || level.getGameTime() - s.lastBreachMessage < 600) return;
        s.lastBreachMessage = level.getGameTime();
        for (UUID m : s.members) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(m);
            if (p != null && p.level() == level && p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 64 * 64) {
                p.sendSystemMessage(Component.literal("You hear them breaking into your walls...").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    public static Component status(ServerPlayer p) {
        ServerLevel ow = p.server.overworld();
        int n = nightsUntil(ow);
        String moon = active(ow) ? "A night raid is under way." : n == 0 ? "Raiders come tonight." : n == 1 ? "The next night raid is tomorrow night." : "The next night raid is in " + n + " nights.";
        int tier = PlayerTier.tier(p);
        double threat = PlayerTier.threat(p);
        return Component.literal(moon + "\n").withStyle(ChatFormatting.RED)
                .append(Component.literal("World tier: ").withStyle(ChatFormatting.GRAY)).append(PlayerTier.name(tier))
                .append(Component.literal(String.format("  (threat %.2f, %d waves, gear up to level %d, %.1f h played)\n", threat, RaidMobs.wavesFor(threat), PlayerTier.levelCap(p), PlayerTier.hours(p))).withStyle(ChatFormatting.GRAY))
                .append(PlayerTier.nextGoal(p).copy().withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void broadcast(ServerLevel ow, Component msg) {
        for (ServerPlayer p : ow.getServer().getPlayerList().getPlayers()) p.sendSystemMessage(msg);
    }

    // --- other events ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void sleep(CanPlayerSleepEvent e) {
        if (!Config.NO_BEDS.get() || e.getProblem() != null) return;
        if (e.getLevel().dimension() == Level.OVERWORLD && bedsOff(e.getEntity().server.overworld())) {
            e.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            e.getEntity().displayClientMessage(Component.literal("You can't sleep through a night raid.").withStyle(ChatFormatting.RED), true);
        }
    }

    /** Raider creepers only blow up blocks raiders could break by hand: no containers, no machines. */
    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate e) {
        Entity source = e.getExplosion().getDirectSourceEntity();
        if (source == null || !RaidMobs.isRaider(source)) return;
        e.getAffectedBlocks().removeIf(pos -> !Breaching.breakable(e.getLevel(), pos, e.getLevel().getBlockState(pos)));
    }

    /** Raiders left over from before a restart (their siege is gone): remove them as they load. */
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !RaidMobs.isRaider(e.getEntity())) return;
        String id = e.getEntity().getPersistentData().getString(RaidMobs.RAIDER);
        boolean known;
        try { known = SIEGES.containsKey(UUID.fromString(id)); } catch (IllegalArgumentException ex) { known = false; }
        if (!known) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        for (Siege s : SIEGES.values()) s.bar.removeAllPlayers();
        RISING.removeAllPlayers();
        BloodMoonSync.reset();
        SIEGES.clear();
        wasActive = false;
        forcedUntil = -1;
    }

    private BloodMoon() {}
}
