package dev.empengines.engines.raid;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.empengines.engines.EmpyreanEngines;
import dev.empengines.engines.launch.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The raider's ship: a real airship from another installed mod's structure file, configured in the
 * gallery ({@link RaiderShipDef}), placed in open air and assembled into a Sable ship. Its boarding
 * connectors and loot barrel go in where the markers were before it's assembled, so they're part of it.
 *
 * It is flown by {@link RaiderFlight}; its own engines, throttles and wheel only put on a show.
 */
final class RaiderShip {
    public static final String MARK = "emp_engines_raider_ship";

    final RaiderShipDef def;
    final ServerSubLevel ship;
    final ShipControls controls;
    /** Marker spots, in the ship's plot coordinates. */
    final BlockPos helm;
    final List<BlockPos> hooks = new ArrayList<>(), posts = new ArrayList<>(), witches = new ArrayList<>();
    final BlockPos hold;
    /** Where stolen cargo goes (a plain barrel), in plot coordinates; null if there's nowhere. */
    BlockPos cargo;
    /** The point the flight lines up with the player's deck: box centre across, hook height up (plot coordinates). */
    Vec3 ref;
    /** The ship's front in its own frame. */
    final Direction front;
    /** +1 if the hooks in use are on the ship's right (starboard), -1 if on its left (port). */
    int hookSide = 1;

    private RaiderShip(RaiderShipDef def, ServerSubLevel ship, ShipControls controls, BlockPos helm, BlockPos hold) {
        this.def = def;
        this.ship = ship;
        this.controls = controls;
        this.helm = helm;
        this.hold = hold;
        this.front = def.front;
        BoundingBox3ic box = SableBridge.plotBounds(ship);
        this.ref = new Vec3((box.minX() + box.maxX() + 1) / 2.0, box.minY() + 2, (box.minZ() + box.maxZ() + 1) / 2.0);
    }

    /**
     * Places the ship's structure centred on {@code at}, puts in its connectors and hold, assembles it and
     * turns it so its front points along {@code heading} (a flat world direction). Null (with the reason) if
     * the file is missing or the air isn't clear.
     */
    static RaiderShip spawn(ServerLevel level, RaiderShipDef def, BlockPos at, Vec3 heading, boolean starboard, String[] why) {
        Optional<StructureTemplate> found = def.template(level);
        if (found.isEmpty()) { why[0] = "No structure " + def.structure + " (is its mod installed?)"; return null; }
        StructureTemplate t = found.get();
        Vec3i size = t.getSize();
        BlockPos origin = at.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        BlockPos far = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        for (BlockPos p : BlockPos.betweenClosed(origin, far)) if (!level.getBlockState(p).isAir()) { why[0] = "The air there isn't clear."; return null; }

        t.placeInWorld(level, origin, origin, new StructurePlaceSettings().setIgnoreEntities(true), level.random, Block.UPDATE_CLIENTS);
        for (BlockPos h : def.hooks(starboard)) level.setBlock(origin.offset(h), RopeBridge.connectorState(), Block.UPDATE_CLIENTS);
        // Loot is Lootr's (everyone gets their own): the design's own loot chests and barrels, and the hold.
        for (BlockPos p : BlockPos.betweenClosed(origin, far)) LootrSwap.convert(level, p.immutable());
        BlockPos cargoAt = null;
        if (def.hold != null) {
            BlockPos h = origin.offset(def.hold);
            LootrSwap.place(level, h, LootrSwap.HOLD_LOOT);
            // Stolen cargo goes in a plain barrel on top of the hold (a Lootr barrel can't take it).
            if (level.getBlockState(h.above()).isAir()) {
                level.setBlock(h.above(), Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
                cargoAt = h.above();
            }
        }

        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(origin, far)) if (!level.getBlockState(p).isAir()) blocks.add(p.immutable());
        if (blocks.isEmpty()) { why[0] = "The structure is empty."; return null; }
        ServerSubLevel ship = SableBridge.assemble(level, blocks.get(blocks.size() / 2), blocks);
        if (ship == null) { why[0] = "Sable couldn't assemble it."; return null; }
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARK, true);
        ship.setUserDataTag(tag);

        // Where the markers are now, in the ship's plot (it hasn't moved or turned yet).
        BlockPos helm = plot(ship, origin.offset(def.helm));
        BlockPos hold = def.hold == null ? null : plot(ship, origin.offset(def.hold));
        BlockPos cargo = cargoAt == null ? null : plot(ship, cargoAt);
        if (cargo == null) {   // no hold marked (or no room above it): the first plain barrel or chest aboard
            BoundingBox3ic box = SableBridge.plotBounds(ship);
            for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (level.getBlockState(p).is(Blocks.BARREL) || level.getBlockState(p).is(Blocks.CHEST)) { cargo = p.immutable(); break; }
            }
        }
        RaiderShip r = new RaiderShip(def, ship, new ShipControls(level, ship), helm, hold);
        r.cargo = cargo;
        for (BlockPos h : def.hooks(starboard)) r.hooks.add(plot(ship, origin.offset(h)));
        for (BlockPos p : def.posts) r.posts.add(plot(ship, origin.offset(p)));
        for (BlockPos p : def.witches) r.witches.add(plot(ship, origin.offset(p)));

        // The flight's reference point sits at hook height in the middle of the ship; the hooks used are on one side.
        BoundingBox3ic box = SableBridge.plotBounds(ship);
        Vec3 centre = new Vec3((box.minX() + box.maxX() + 1) / 2.0, 0, (box.minZ() + box.maxZ() + 1) / 2.0);
        double hookY = r.hooks.stream().mapToDouble(BlockPos::getY).average().orElse(box.minY() + 2);
        r.ref = new Vec3(centre.x, hookY, centre.z);
        r.hookSide = starboard ? 1 : -1;

        // Turn it to face along the heading.
        Vec3 f = new Vec3(def.front.getStepX(), 0, def.front.getStepZ());
        double angle = Math.atan2(heading.x, heading.z) - Math.atan2(f.x, f.z);
        SableBridge.place(ship, SableBridge.position(ship), new Quaterniond().rotationY(angle));
        EmpyreanEngines.LOGGER.info("Sky Raiders: brought in {} ({} hooks, {} posts, {})", def.name, r.hooks.size(), r.posts.size(), r.controls.describe());
        return r;
    }

    private static BlockPos plot(ServerSubLevel ship, BlockPos world) {
        return BlockPos.containing(SableBridge.toPlot(ship, Vec3.atCenterOf(world)));
    }

    /** The ship's front in world space, flat. */
    Vec3 heading() {
        Vec3 h = SableBridge.toWorldDir(ship, new Vec3(front.getStepX(), 0, front.getStepZ()));
        Vec3 flat = new Vec3(h.x, 0, h.z);
        return flat.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    /** Half the ship's length (along its front) and width, in blocks. */
    double halfLength() {
        BoundingBox3ic b = SableBridge.plotBounds(ship);
        return (front.getAxis() == Direction.Axis.X ? b.maxX() - b.minX() + 1 : b.maxZ() - b.minZ() + 1) / 2.0;
    }

    double halfWidth() {
        BoundingBox3ic b = SableBridge.plotBounds(ship);
        return (front.getAxis() == Direction.Axis.X ? b.maxZ() - b.minZ() + 1 : b.maxX() - b.minX() + 1) / 2.0;
    }

    /** From the reference point to the hull's outer edge on the hook side, in blocks. */
    double hookReach() {
        Vec3 right = new Vec3(-front.getStepZ(), 0, front.getStepX()).scale(hookSide);
        double m = halfWidth();
        for (BlockPos h : hooks) m = Math.max(m, new Vec3(h.getX() + 0.5 - ref.x, 0, h.getZ() + 0.5 - ref.z).dot(right) + 0.5);
        return m;
    }

    double height() {
        BoundingBox3ic b = SableBridge.plotBounds(ship);
        return b.maxY() - b.minY() + 1;
    }

    /** Height from the bottom of the ship to the reference point. */
    double refAboveBottom() { return ref.y - SableBridge.plotBounds(ship).minY(); }

    boolean isRemoved() { return ship.isRemoved(); }

    public static boolean isRaiderShip(ServerSubLevel ship) {
        CompoundTag tag = ship.getUserDataTag();
        return tag != null && tag.getBoolean(MARK);
    }
}
