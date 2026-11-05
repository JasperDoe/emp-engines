package dev.skybound.createskybound.block;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.skybound.createskybound.Config;
import dev.skybound.createskybound.CreateSkybound;
import dev.skybound.createskybound.ModRegistry;
import dev.skybound.createskybound.launch.LaunchEffects;
import dev.skybound.createskybound.launch.Platform;
import dev.skybound.createskybound.launch.SableBridge;
import dev.skybound.createskybound.sky.SkyRuns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Charges from rotation, then runs the launch:
 * COUNTDOWN (ship lifts and levels) -> BEAMS (light comes down onto the pylons)
 * -> SPIN (the beams circle the ship, faster and faster) -> LAUNCH (fired upwards until it leaves the world).
 */
public class VectorPlanarComputerBlockEntity extends KineticBlockEntity {
    public enum Phase { IDLE, COUNTDOWN, BEAMS, SPIN, LAUNCH }

    private static final int COUNTDOWN_TICKS = 100, BEAM_TICKS = 80, SPIN_TICKS = 80, LAUNCH_TIMEOUT = 400;
    private static final double LIFT_BLOCKS = 3.0;

    private long charge;
    private long required = Long.MAX_VALUE;
    private Platform platform;
    private String problem;
    private double shipMass;
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private boolean lastSignal;
    private UUID shipId;
    private double liftTargetY;

    public VectorPlanarComputerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    public VectorPlanarComputerBlockEntity(BlockPos pos, BlockState state) {
        this(ModRegistry.VECTOR_PLANAR_COMPUTER_BE.get(), pos, state);
    }

    // --- structure check and charging ---------------------------------------------

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide || phase != Phase.IDLE) return;
        String[] why = new String[1];
        platform = Platform.find(level, worldPosition, why);
        problem = why[0];
        shipMass = 0;
        if (platform != null && level instanceof ServerLevel sl) {
            ServerSubLevel ship = SableBridge.findShip(sl, shipSearchBox());
            if (ship != null) {
                shipMass = SableBridge.mass(ship);
                shipId = ship.getUniqueId();
            } else {
                shipId = null;
            }
        }
        required = Config.BASE_CHARGE.get() + Math.round(shipMass * Config.CHARGE_PER_MASS.get());
        setChanged();
    }

    private AABB shipSearchBox() {
        return new AABB(platform.minX(), platform.y() + 1, platform.minZ(), platform.maxX() + 1, platform.y() + 48, platform.maxZ() + 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        ServerLevel sl = (ServerLevel) level;
        if (phase == Phase.IDLE) {
            if (platform != null && !isOverStressed() && getSpeed() != 0 && charge < required) {
                charge = Math.min(required, charge + (long) Math.ceil(Math.abs(getSpeed()) / 8f));
                if (charge >= required) {
                    sl.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.7f);
                }
            }
            if (platform != null && charge > 0 && sl.getGameTime() % 10 == 0) LaunchEffects.chargingRumble(sl, platform, chargeFraction());
            return;
        }
        runSequence(sl);
    }

    public float chargeFraction() {
        return required <= 0 ? 1f : Math.min(1f, (float) charge / required);
    }

    // --- launch sequence ---------------------------------------------------------

    public void onRedstone(boolean powered) {
        boolean rising = powered && !lastSignal;
        lastSignal = powered;
        if (!rising || phase != Phase.IDLE || level == null || level.isClientSide) return;
        lazyTick();
        if (platform == null || charge < required) return;
        if (shipId == null) {
            problem = "No ship above the platform.";
            return;
        }
        ServerSubLevel ship = SableBridge.byId((ServerLevel) level, shipId);
        if (ship == null) return;
        liftTargetY = SableBridge.position(ship).y + LIFT_BLOCKS;
        setPhase(Phase.COUNTDOWN);
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseTicks = 0;
        setChanged();
        sendData();
    }

    private void runSequence(ServerLevel sl) {
        if (platform == null) {
            platform = Platform.find(sl, worldPosition, new String[1]);
            if (platform == null) { abort(sl, "The platform was broken."); return; }
        }
        phaseTicks++;
        ServerSubLevel ship = SableBridge.byId(sl, shipId);
        if (ship == null) {
            abort(sl, "The ship left the platform.");
            return;
        }
        Vec3 shipPos = SableBridge.position(ship);
        switch (phase) {
            case COUNTDOWN -> {
                // Rise a few blocks, then hold still and level out.
                SableBridge.steer(ship, shipPos.y < liftTargetY ? 1.5 : 0.0, 0.3);
                LaunchEffects.countdown(sl, platform, shipPos, phaseTicks, COUNTDOWN_TICKS);
                if (phaseTicks >= COUNTDOWN_TICKS) setPhase(Phase.BEAMS);
            }
            case BEAMS -> {
                SableBridge.steer(ship, (liftTargetY - shipPos.y) * 0.5, 0.5);
                LaunchEffects.beams(sl, platform, shipPos, phaseTicks / (float) BEAM_TICKS);
                if (phaseTicks >= BEAM_TICKS) setPhase(Phase.SPIN);
            }
            case SPIN -> {
                SableBridge.steer(ship, (liftTargetY - shipPos.y) * 0.5, 0.5);
                LaunchEffects.spin(sl, platform, shipPos, phaseTicks / (float) SPIN_TICKS);
                if (phaseTicks >= SPIN_TICKS) {
                    LaunchEffects.fire(sl, shipPos);
                    setPhase(Phase.LAUNCH);
                }
            }
            case LAUNCH -> {
                double speed = Math.min(160.0, 10.0 + phaseTicks * 2.0);
                SableBridge.steer(ship, speed, 0.5);
                LaunchEffects.trail(sl, shipPos);
                if (shipPos.y > sl.getMaxBuildHeight() - 8) {
                    List<ServerPlayer> crew = SkyRuns.playersAboard(sl, ship);
                    SkyRuns.start(sl, worldPosition, platform, ship, crew);
                    charge = 0;
                    setPhase(Phase.IDLE);
                } else if (phaseTicks > LAUNCH_TIMEOUT) {
                    abort(sl, "The ship didn't reach the sky.");
                }
            }
            default -> { }
        }
    }

    private void abort(ServerLevel sl, String why) {
        CreateSkybound.LOGGER.info("Launch at {} aborted: {}", worldPosition, why);
        problem = why;
        setPhase(Phase.IDLE);
    }

    public void report(Player player) {
        if (platform == null) {
            player.displayClientMessage(Component.literal(problem == null ? "No launch platform found." : problem).withStyle(ChatFormatting.RED), false);
            return;
        }
        int w = platform.maxX() - platform.minX() + 1, d = platform.maxZ() - platform.minZ() + 1;
        player.displayClientMessage(Component.literal("Platform " + w + " x " + d + ", pylon tier " + platform.tier()
                + " (" + Config.runMinutes(platform.tier()) + " minute run)").withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.literal(shipId == null ? "No ship above the platform."
                : String.format("Ship mass %.0f", shipMass)), false);
        player.displayClientMessage(Component.literal(String.format("Charge %d%%", Math.round(chargeFraction() * 100))
                + (charge >= required ? " - ready. Apply a redstone signal to launch." : "")), false);
    }

    // --- save and sync -----------------------------------------------------------

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.write(tag, lookup, clientPacket);
        tag.putLong("Charge", charge);
        tag.putString("Phase", phase.name());
        tag.putInt("PhaseTicks", phaseTicks);
        tag.putBoolean("LastSignal", lastSignal);
        if (shipId != null) tag.putUUID("Ship", shipId);
        tag.putDouble("LiftY", liftTargetY);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.read(tag, lookup, clientPacket);
        charge = tag.getLong("Charge");
        try { phase = Phase.valueOf(tag.getString("Phase")); } catch (IllegalArgumentException e) { phase = Phase.IDLE; }
        phaseTicks = tag.getInt("PhaseTicks");
        lastSignal = tag.getBoolean("LastSignal");
        shipId = tag.hasUUID("Ship") ? tag.getUUID("Ship") : null;
        liftTargetY = tag.getDouble("LiftY");
    }
}
