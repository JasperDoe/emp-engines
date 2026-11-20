package dev.empengines.engines.raid;

import dev.empengines.engines.Config;
import dev.empengines.engines.EmpyreanEngines;
import dev.empengines.engines.launch.SableBridge;
import dev.empengines.engines.sky.SkyRuns;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sky raids that start on their own.
 *
 * Every player keeps a clock of the time they've spent on a ship high enough to be raided (raidMinY in the Overworld,
 * raidMinYEtheric in the Etheric Skies), kept in their player data so logging out doesn't reset it. Every 10 seconds
 * each such player rolls for a raid with a chance that rises with their clock (a linearly rising hazard: rare just
 * after a raid, close to certain after about twice raidAverageMinutes; the average is raidAverageMinutes). A raid
 * hits the ship they're on and resets the clock of everyone aboard.
 *
 * A player with Bad Omen on such a ship brings a raid at once (the effect is used up), so an Ominous Bottle calls one.
 * Creative and spectator players never roll; ships already raided, raider ships and a full server (raidMaxActive) are
 * skipped.
 */
public final class NaturalRaids {
    private NaturalRaids() {}

    private static final String CLOCK = "emp_engines_skyraid_seconds";
    private static final int CHECK_TICKS = 200;
    private static final double CHECK_SECONDS = CHECK_TICKS / 20.0;
    /** A ship a raid couldn't start on (no open deck, no room to bring the raider in) isn't tried again for a minute. */
    private static final Map<UUID, Integer> FAILED = new HashMap<>();
    private static final int RETRY_TICKS = 1200;

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0 || !Config.RAID_NATURAL.get()) return;
        double mean = Config.RAID_AVERAGE_MINUTES.get() * 60.0;
        // Rayleigh waiting time: mean = sigma * sqrt(pi / 2)
        double sigma = mean / Math.sqrt(Math.PI / 2.0);
        Set<ServerSubLevel> handled = new HashSet<>();

        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            ServerSubLevel ship = SableBridge.shipUnder(p);
            if (ship == null || !highEnough(p) || isRaider(ship)) continue;
            CompoundTag data = p.getPersistentData();

            if (RaidManager.raidOn(ship) != null) {
                data.putDouble(CLOCK, 0);   // no build-up while fighting one off
                continue;
            }
            boolean omen = Config.RAID_BAD_OMEN.get() && p.hasEffect(MobEffects.BAD_OMEN);
            double t = data.getDouble(CLOCK);
            double t2 = t + CHECK_SECONDS;
            data.putDouble(CLOCK, t2);
            if (handled.contains(ship)) continue;
            Integer failedAt = FAILED.get(ship.getUniqueId());
            if (failedAt != null && server.getTickCount() - failedAt < RETRY_TICKS) continue;

            boolean roll;
            if (omen) {
                roll = true;
            } else {
                double chance = 1.0 - Math.exp(-(t2 * t2 - t * t) / (2.0 * sigma * sigma));
                roll = p.getRandom().nextDouble() < chance;
            }
            if (!roll) continue;
            if (RaidManager.RAIDS.size() >= Config.RAID_MAX_ACTIVE.get()) continue;
            handled.add(ship);
            if (start(p, ship)) {
                FAILED.remove(ship.getUniqueId());
                if (omen) p.removeEffect(MobEffects.BAD_OMEN);
            } else {
                FAILED.put(ship.getUniqueId(), server.getTickCount());
            }
        }
    }

    private static boolean highEnough(ServerPlayer p) {
        boolean sky = p.level().dimension() == SkyRuns.ETHERIC_SKIES;
        if (!sky && p.level().dimension() != Level.OVERWORLD) return false;
        int minY = sky ? Config.RAID_MIN_Y_ETHERIC.get() : Config.RAID_MIN_Y.get();
        return p.getY() >= minY;
    }

    private static boolean isRaider(ServerSubLevel ship) {
        for (Raid r : RaidManager.RAIDS) if (r.raider != null && r.raider.ship == ship) return true;
        return false;
    }

    /** Starts a raid with a random ready raider ship; resets the clock of everyone aboard if it starts. */
    private static boolean start(ServerPlayer p, ServerSubLevel ship) {
        List<RaiderShipDef> pool = RaiderShipDef.all().values().stream().filter(d -> d.configured() && d.enabled).toList();
        if (pool.isEmpty()) return false;
        RaiderShipDef def = pool.get(p.getRandom().nextInt(pool.size()));
        ServerLevel level = p.serverLevel();
        String[] why = new String[1];
        Raid raid;
        try {
            raid = RaidManager.start(level, ship, def, why);
        } catch (Exception ex) {
            EmpyreanEngines.LOGGER.error("Sky Raiders: natural raid on {}'s ship failed to start", p.getName().getString(), ex);
            return false;
        }
        if (raid == null) {
            EmpyreanEngines.LOGGER.debug("Sky Raiders: no natural raid on {}'s ship: {}", p.getName().getString(), why[0]);
            return false;
        }
        for (ServerPlayer crew : level.players()) {
            if (SableBridge.shipUnder(crew) == ship) crew.getPersistentData().putDouble(CLOCK, 0);
        }
        EmpyreanEngines.LOGGER.info("Sky Raiders: {} raids {}'s ship", def.name, p.getName().getString());
        return true;
    }
}
