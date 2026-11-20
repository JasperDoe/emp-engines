package dev.empengines.engines.raid;

import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity;
import dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverBlockEntity;
import dev.empengines.engines.launch.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The controls a raider airship already has, worked for show: engines kept burning, burners and
 * throttles up, steering wheels turned with the ship. {@link RaiderFlight} does the actual flying,
 * so a ship flies the same whatever its builder wired its controls to.
 */
final class ShipControls {
    final ServerLevel level;
    final ServerSubLevel ship;
    final List<BlockPos> engines = new ArrayList<>(), lift = new ArrayList<>(), thrust = new ArrayList<>(), wheels = new ArrayList<>();
    private int lastLift = -1, lastThrust = -1;
    private float lastWheel = Float.NaN;

    ShipControls(ServerLevel level, ServerSubLevel ship) {
        this.level = level;
        this.ship = ship;
        BoundingBox3ic box = SableBridge.plotBounds(ship);
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof PortableEngineBlockEntity) engines.add(p.immutable());
            else if (be instanceof SteeringWheelBlockEntity) wheels.add(p.immutable());
            else if (be instanceof ThrottleLeverBlockEntity) (nextToBurner(p) ? lift : thrust).add(p.immutable());
        }
    }

    private boolean nextToBurner(BlockPos p) {
        for (Direction d : Direction.values())
            if (level.getBlockEntity(p.relative(d)) instanceof HotAirBurnerBlockEntity) return true;
        return false;
    }

    /**
     * Called every tick or so: {@code throttle} 0..1 (how hard it's going), {@code turn} -1..1
     * (which way and how hard it's turning; positive is a positive yaw rate).
     */
    void show(double throttle, double turn, long tick) {
        if (tick % 40 == 0) {
            for (BlockPos p : engines)
                if (level.getBlockEntity(p) instanceof PortableEngineBlockEntity e && e.getCurrentBurnTime() < 400) e.setCurrentBurnTime(1200);
        }
        int l = 12;
        if (l != lastLift) { set(lift, l); lastLift = l; }
        int t = (int) Math.round(15 * Math.max(0, Math.min(1, throttle)));
        if (t != lastThrust) { set(thrust, t); lastThrust = t; }
        float a = Math.round((float) (Math.max(-1, Math.min(1, turn)) * 60) / 10f) * 10f;
        if (a != lastWheel) {
            for (BlockPos p : wheels) if (level.getBlockEntity(p) instanceof SteeringWheelBlockEntity w) w.updateTargetAngle(a);
            lastWheel = a;
        }
    }

    private void set(List<BlockPos> list, int signal) {
        for (BlockPos p : list) if (level.getBlockEntity(p) instanceof ThrottleLeverBlockEntity be) be.setSignal(signal);
    }

    String describe() {
        return engines.size() + " engines, " + (lift.size() + thrust.size()) + " throttles, " + wheels.size() + " wheels";
    }
}
