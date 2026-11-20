package dev.empengines.silentraids.siege;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One siege: a player, or players close together, and the waves coming for them. */
final class Siege {
    static final int APPROACH_TICKS = 200;   // the bar fills for 10 s before each wave

    final UUID id = UUID.randomUUID();
    final Set<UUID> members = new LinkedHashSet<>();
    UUID leader;
    double threat;
    int totalWaves;
    int wavesSpawned;
    int lastWaveSize;
    long waveStartTick;
    /** Counting down to the next wave (the bar fills); 0 while a wave is being fought. */
    int approachTicks = APPROACH_TICKS;
    boolean inWave;
    int elites;
    final List<UUID> mobs = new ArrayList<>();
    long lastBreachMessage = -100000;
    boolean done;
    long doneTick;
    /** Raid-style bar for the siege's players. */
    final ServerBossEvent bar = new ServerBossEvent(Component.literal("Night Raid"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    Siege(UUID leader, double threat) {
        this.leader = leader;
        members.add(leader);
        this.threat = threat;
        this.totalWaves = RaidMobs.wavesFor(threat);
        bar.setProgress(0f);
    }

    void join(UUID player, double playerThreat) {
        members.add(player);
        if (playerThreat > threat) {
            threat = playerThreat;
            totalWaves = Math.max(totalWaves, RaidMobs.wavesFor(threat));
        }
    }
}
