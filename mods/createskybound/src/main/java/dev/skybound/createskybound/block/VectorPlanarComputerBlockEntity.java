package dev.skybound.createskybound.block;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.skybound.createskybound.Config;
import dev.skybound.createskybound.CreateSkybound;
import dev.skybound.createskybound.ModRegistry;
import dev.skybound.createskybound.launch.LaunchEffects;
import dev.skybound.createskybound.launch.LaunchPath;
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
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Charges from rotation fed into the ring's shaft inputs, then runs the launch on a rising redstone signal:
 * COUNTDOWN (ship lifts and levels) -> BEAMS (light comes down onto the pylons and closes in)
 * -> SPIN (the beams circle the ship faster and faster) -> LAUNCH (thrown upwards, slowing through the clouds,
 * a moment of drift above them, then into the Chaotic Skies).
 *
 * The ship follows a scripted path ({@link LaunchPath}) instead of being pushed by physics, so it can't
 * stall. The client gets the same timing and draws the effects ({@code client.LaunchRenderer}).
 */
public class VectorPlanarComputerBlockEntity extends SmartBlockEntity {
    public enum Phase { IDLE, COUNTDOWN, BEAMS, SPIN, LAUNCH }

    private long charge;
    private long required = Long.MAX_VALUE;
    private Platform platform;
    private String problem;
    private double shipMass;
    private UUID shipId;
    private boolean lastSignal;
    private final List<BlockPos> inputs = new ArrayList<>();
    private int inputRpm;

    // Sequence state (synced to the client)
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private Vec3 shipStart = Vec3.ZERO;
    private Quaterniond startRot = new Quaterniond();
    private Quaterniond levelRot = new Quaterniond();
    private double climb = 200;
    private double cloudY = 224;
    private boolean breached;
    private final Map<UUID, Vec3> crewOffsets = new HashMap<>();

    public VectorPlanarComputerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    public VectorPlanarComputerBlockEntity(BlockPos pos, BlockState state) {
        this(ModRegistry.VECTOR_PLANAR_COMPUTER_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    // --- read by the client renderer -----------------------------------------------

    public Phase phase() { return phase; }
    public int phaseTicks() { return phaseTicks; }
    public Platform platform() { return platform; }
    public Vec3 shipStart() { return shipStart; }
    public double climb() { return climb; }
    public double cloudY() { return cloudY; }
    public float chargeFraction() { return required <= 0 || required == Long.MAX_VALUE ? 0f : Math.min(1f, (float) charge / required); }

    /** Where the ship is now, from the shared path. */
    public Vec3 shipPosition(float partial) {
        double t = phaseTicks + partial;
        return shipStart.add(0, LaunchPath.offset(phase.ordinal() - 1, t, climb), 0);
    }

    @Override
    protected AABB createRenderBoundingBox() { return AABB.INFINITE; }

    /** For Ponder scenes: show a platform glowing at a given charge without a server behind it. */
    public void ponderPreview(Platform p, float fraction) {
        platform = p;
        required = 1000;
        charge = Math.round(fraction * 1000);
    }

    // --- structure check and charging --------------------------------------------------

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide || phase != Phase.IDLE) return;
        refresh();
    }

    private void refresh() {
        String[] why = new String[1];
        Platform found = Platform.find(level, worldPosition, why);
        boolean changed = !java.util.Objects.equals(found, platform);
        platform = found;
        problem = found != null ? null : why[0];
        inputs.clear();
        if (platform != null)
            for (BlockPos e : platform.edges())
                if (level.getBlockState(e).is(ModRegistry.LAUNCHER_INPUT.get())) inputs.add(e);
        if (platform != null && inputs.isEmpty() && problem == null)
            problem = "Wrench a brass casing in the ring to make a shaft input, then feed it rotation.";
        shipMass = 0;
        shipId = null;
        if (platform != null && level instanceof ServerLevel sl) {
            ServerSubLevel ship = SableBridge.findShip(sl, shipSearchBox());
            if (ship != null) {
                shipMass = SableBridge.mass(ship);
                shipId = ship.getUniqueId();
            }
        }
        required = Config.BASE_CHARGE.get() + Math.round(shipMass * Config.CHARGE_PER_MASS.get());
        setChanged();
        if (changed) sendData();
    }

    private AABB shipSearchBox() {
        return new AABB(platform.minX(), platform.y() + 1, platform.minZ(), platform.maxX() + 1, platform.y() + 48, platform.maxZ() + 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide) {
            if (phase != Phase.IDLE) phaseTicks++;   // the server corrects this on every phase change
            dev.skybound.createskybound.launch.LaunchTracker.track(this);
            return;
        }
        if (!(level instanceof ServerLevel sl)) return;   // e.g. a Ponder scene
        if (phase == Phase.IDLE) {
            long rate = platform == null ? 0 : chargeRate(sl);
            if (rate > 0 && charge < required) {
                charge = Math.min(required, charge + rate);
                if (charge >= required) {
                    LaunchEffects.charged(sl, platform);
                    sendData();
                }
                if (sl.getGameTime() % 20 == 0) sendData();   // keeps the charge glow and hum up to date
            }
            return;
        }
        runSequence(sl);
    }

    /** Charge added per tick: every working shaft input on the ring adds its speed / 8. */
    private long chargeRate(ServerLevel sl) {
        long rate = 0;
        int rpm = 0;
        for (BlockPos p : inputs) {
            if (!(sl.getBlockEntity(p) instanceof LauncherInputBlockEntity in) || in.isOverStressed()) continue;
            float speed = Math.abs(in.getSpeed());
            if (speed == 0) continue;
            rate += (long) Math.ceil(speed / 8f);
            rpm += Math.round(speed);
        }
        inputRpm = rpm;
        return rate;
    }

    // --- launch sequence -----------------------------------------------------------------

    public void onRedstone(boolean powered) {
        boolean rising = powered && !lastSignal;
        lastSignal = powered;
        setChanged();
        if (!rising || phase != Phase.IDLE || level == null || level.isClientSide) return;
        refresh();
        if (platform == null) return;
        if (charge < required) { problem = "Not fully charged yet."; return; }
        if (shipId == null) { problem = "No ship above the platform."; return; }
        ServerSubLevel ship = SableBridge.byId((ServerLevel) level, shipId);
        if (ship == null) return;
        shipStart = SableBridge.position(ship);
        startRot = SableBridge.orientation(ship);
        levelRot = SableBridge.level(startRot);
        // How far the throw carries the ship: up to just above the clouds, within the world's height.
        double ceiling = level.getMaxBuildHeight() - 12 - LaunchPath.DRIFT_CLIMB;
        double apex = Math.min(Config.LAUNCH_APEX.get(), ceiling);
        double from = shipStart.y + LaunchPath.LIFT;
        climb = Math.max(Math.min(48, ceiling - from), apex - from);
        climb = Math.max(8, climb);
        cloudY = Config.CLOUD_HEIGHT.get();
        breached = false;
        crewOffsets.clear();
        for (ServerPlayer p : SkyRuns.playersAboard((ServerLevel) level, ship))
            crewOffsets.put(p.getUUID(), p.position().subtract(shipStart));
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
            if (platform == null) { abort("The platform was broken."); return; }
        }
        ServerSubLevel ship = SableBridge.byId(sl, shipId);
        if (ship == null) { abort("The ship left the platform."); return; }
        phaseTicks++;

        // Move the ship along the path: lift and level out during the countdown, then hold, then fire.
        Vec3 pos = shipPosition(0);
        Quaterniond rot = phase == Phase.COUNTDOWN
                ? new Quaterniond(startRot).slerp(levelRot, LaunchPath.easeInOut(phaseTicks / (double) LaunchPath.COUNTDOWN))
                : levelRot;
        SableBridge.place(ship, pos, rot);

        switch (phase) {
            case COUNTDOWN -> {
                LaunchEffects.countdown(sl, platform, phaseTicks, LaunchPath.COUNTDOWN);
                if (phaseTicks >= LaunchPath.COUNTDOWN) setPhase(Phase.BEAMS);
            }
            case BEAMS -> {
                if (phaseTicks == 1) LaunchEffects.beamsArrive(sl, platform);
                if (phaseTicks >= LaunchPath.BEAMS) setPhase(Phase.SPIN);
            }
            case SPIN -> {
                LaunchEffects.spin(sl, pos, phaseTicks, LaunchPath.SPIN);
                if (phaseTicks >= LaunchPath.SPIN) {
                    LaunchEffects.launch(sl, pos);
                    setPhase(Phase.LAUNCH);
                }
            }
            case LAUNCH -> {
                carryCrew(sl, pos);
                if (!breached && pos.y >= cloudY) {
                    breached = true;
                    LaunchEffects.breach(sl, pos);
                }
                if (phaseTicks >= LaunchPath.LAUNCH) {
                    List<ServerPlayer> crew = new ArrayList<>();
                    for (UUID id : crewOffsets.keySet()) {
                        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
                        if (p != null && p.level() == sl) crew.add(p);
                    }
                    SkyRuns.start(sl, worldPosition, platform, ship, crew);
                    finish();
                }
            }
            default -> { }
        }
    }

    /** Keeps the crew on deck while the ship moves faster than physics would carry them. */
    private void carryCrew(ServerLevel sl, Vec3 shipPos) {
        for (Map.Entry<UUID, Vec3> e : crewOffsets.entrySet()) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || p.level() != sl) continue;
            Vec3 at = shipPos.add(e.getValue());
            p.teleportTo(sl, at.x, at.y, at.z, p.getYRot(), p.getXRot());
            p.setDeltaMovement(Vec3.ZERO);
            p.resetFallDistance();
        }
    }

    private void finish() {
        charge = 0;
        shipId = null;
        crewOffsets.clear();
        setPhase(Phase.IDLE);
        refresh();
    }

    private void abort(String why) {
        CreateSkybound.LOGGER.info("Launch at {} aborted: {}", worldPosition, why);
        problem = why;
        crewOffsets.clear();
        setPhase(Phase.IDLE);
    }

    public void report(Player player) {
        if (phase != Phase.IDLE) {
            player.displayClientMessage(Component.literal("Launch in progress.").withStyle(ChatFormatting.GOLD), false);
            return;
        }
        refresh();
        if (platform == null) {
            player.displayClientMessage(Component.literal(problem == null ? "No launch platform found." : problem).withStyle(ChatFormatting.RED), false);
            return;
        }
        int w = platform.maxX() - platform.minX() + 1, d = platform.maxZ() - platform.minZ() + 1;
        player.displayClientMessage(Component.literal("Platform " + w + " x " + d + ", pylon tier " + platform.tier()
                + " (" + Config.runMinutes(platform.tier()) + " minute run)").withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.literal(inputs.size() + (inputs.size() == 1 ? " shaft input" : " shaft inputs")
                + (inputRpm > 0 ? ", " + inputRpm + " RPM in" : ", no rotation")), false);
        player.displayClientMessage(Component.literal(shipId == null ? "No ship above the platform." : String.format("Ship mass %.0f", shipMass)), false);
        player.displayClientMessage(Component.literal(String.format("Charge %d%%", Math.round(chargeFraction() * 100))
                + (charge >= required ? " - ready. Apply a redstone signal to launch." : "")), false);
        if (problem != null) player.displayClientMessage(Component.literal(problem).withStyle(ChatFormatting.GRAY), false);
    }

    // --- save and sync -----------------------------------------------------------------

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.write(tag, lookup, clientPacket);
        tag.putLong("Charge", charge);
        tag.putLong("Required", required == Long.MAX_VALUE ? -1 : required);
        tag.putString("Phase", phase.name());
        tag.putInt("PhaseTicks", phaseTicks);
        tag.putBoolean("LastSignal", lastSignal);
        if (shipId != null) tag.putUUID("Ship", shipId);
        tag.putDouble("StartX", shipStart.x);
        tag.putDouble("StartY", shipStart.y);
        tag.putDouble("StartZ", shipStart.z);
        tag.putDouble("Climb", climb);
        tag.putDouble("CloudY", cloudY);
        tag.putBoolean("Breached", breached);
        tag.putDouble("RotX", startRot.x); tag.putDouble("RotY", startRot.y); tag.putDouble("RotZ", startRot.z); tag.putDouble("RotW", startRot.w);
        if (platform != null) {
            tag.putIntArray("Platform", new int[]{platform.minX(), platform.minZ(), platform.maxX(), platform.maxZ(), platform.y(), platform.tier()});
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider lookup, boolean clientPacket) {
        super.read(tag, lookup, clientPacket);
        charge = tag.getLong("Charge");
        long req = tag.getLong("Required");
        required = req < 0 ? Long.MAX_VALUE : req;
        Phase newPhase;
        try { newPhase = Phase.valueOf(tag.getString("Phase")); } catch (IllegalArgumentException e) { newPhase = Phase.IDLE; }
        phase = newPhase;
        phaseTicks = tag.getInt("PhaseTicks");
        lastSignal = tag.getBoolean("LastSignal");
        shipId = tag.hasUUID("Ship") ? tag.getUUID("Ship") : null;
        shipStart = new Vec3(tag.getDouble("StartX"), tag.getDouble("StartY"), tag.getDouble("StartZ"));
        if (tag.contains("Climb")) climb = tag.getDouble("Climb");
        if (tag.contains("CloudY")) cloudY = tag.getDouble("CloudY");
        breached = tag.getBoolean("Breached");
        startRot = new Quaterniond(tag.getDouble("RotX"), tag.getDouble("RotY"), tag.getDouble("RotZ"),
                tag.contains("RotW") ? tag.getDouble("RotW") : 1.0);
        levelRot = SableBridge.level(startRot);
        int[] p = tag.getIntArray("Platform");
        platform = p.length == 6 ? new Platform(p[0], p[1], p[2], p[3], p[4], p[5]) : (clientPacket ? null : platform);
    }
}
