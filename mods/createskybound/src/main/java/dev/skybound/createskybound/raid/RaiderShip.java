package dev.skybound.createskybound.raid;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.skybound.createskybound.CreateSkybound;
import dev.skybound.createskybound.launch.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The raider's ship: a small sloop built from blocks in open air, then assembled into a Sable ship.
 * Its rope connectors line its port side (-X), which is turned towards the player's ship; a barrel
 * in the hold takes whatever the looters bring back.
 *
 * Two ways to get one: {@link #fromTemplate} uses a real airship from another installed mod (the
 * default), and {@link #build} is a small sloop built in code, used if those files are missing.
 */
public record RaiderShip(ServerSubLevel ship, List<BlockPos> connectors, BlockPos hold, Vec3 deckPoint) {
    public static final String MARK = "createskybound_raider_ship";

    /**
     * Places an airship from another mod's structure file (for example
     * {@code cr_aero_structures:pillager_airballoon}) in open air at {@code at}, assembles it, and ties
     * {@code ropes} rope connectors along its port deck edge. Returns null if the file is missing,
     * the air isn't clear, or the ship has no deck to tie to.
     */
    public static RaiderShip fromTemplate(ServerLevel level, BlockPos at, ResourceLocation id, int ropes) {
        Optional<StructureTemplate> found = level.getStructureManager().get(id);
        if (found.isEmpty()) {
            CreateSkybound.LOGGER.warn("Sky Raiders: no structure {} (is its mod installed?)", id);
            return null;
        }
        StructureTemplate t = found.get();
        Vec3i size = t.getSize();
        BlockPos origin = at.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        BlockPos far = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        for (BlockPos p : BlockPos.betweenClosed(origin, far)) if (!level.getBlockState(p).isAir()) return null;

        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true);
        t.placeInWorld(level, origin, origin, settings, level.random, Block.UPDATE_CLIENTS);
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(origin, far)) if (!level.getBlockState(p).isAir()) blocks.add(p.immutable());
        if (blocks.isEmpty()) return null;

        ServerSubLevel ship = SableBridge.assemble(level, blocks.get(blocks.size() / 2), blocks);
        if (ship == null) return null;
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARK, true);
        ship.setUserDataTag(tag);

        // Our own boarding connectors along the port deck edge (the file's own connectors hold its rigging).
        DeckScan deck = new DeckScan(level, ship);
        List<BlockPos> spots = deck.edgeSpots(ropes);
        List<BlockPos> connectors = new ArrayList<>();
        for (BlockPos s : spots) {
            BlockPos c = s.above();
            level.setBlock(c, RopeBridge.connectorState(), 3);
            connectors.add(c);
        }
        if (connectors.isEmpty()) {
            SableBridge.remove(ship);
            return null;
        }
        connectors.sort(Comparator.comparingInt(BlockPos::getZ));
        BlockPos hold = null;
        BoundingBox3ic box = SableBridge.plotBounds(ship);
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(p).is(Blocks.BARREL) || level.getBlockState(p).is(Blocks.CHEST)) { hold = p.immutable(); break; }
        }
        double x = connectors.stream().mapToDouble(BlockPos::getX).average().orElse(0);
        double z = connectors.stream().mapToDouble(BlockPos::getZ).average().orElse(0) + 0.5;
        return new RaiderShip(ship, connectors, hold, new Vec3(x, connectors.get(0).getY(), z));
    }

    /** Builds and assembles the sloop with its deck centre at {@code at}. Returns null if the air isn't clear. */
    public static RaiderShip build(ServerLevel level, BlockPos at) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = -2; x <= 2; x++)
            for (int z = -4; z <= 4; z++) blocks.put(at.offset(x, 0, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int x = -1; x <= 1; x++)
            for (int z = -3; z <= 3; z++) blocks.put(at.offset(x, -1, z), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        List<BlockPos> ropeSpots = List.of(at.offset(-2, 1, -2), at.offset(-2, 1, 0), at.offset(-2, 1, 2));
        for (int x = -2; x <= 2; x++)
            for (int z = -4; z <= 4; z++) {
                boolean edge = x == -2 || x == 2 || z == -4 || z == 4;
                BlockPos p = at.offset(x, 1, z);
                if (edge && !ropeSpots.contains(p)) blocks.put(p, Blocks.SPRUCE_FENCE.defaultBlockState());
            }
        for (int y = 1; y <= 6; y++) blocks.put(at.offset(1, y, 0), Blocks.SPRUCE_LOG.defaultBlockState());
        for (int y = 3; y <= 5; y++) {
            blocks.put(at.offset(1, y, -1), Blocks.RED_WOOL.defaultBlockState());
            blocks.put(at.offset(1, y, 1), Blocks.RED_WOOL.defaultBlockState());
        }
        blocks.put(at.offset(1, 1, -3), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        blocks.put(at.offset(1, 1, 3), Blocks.LANTERN.defaultBlockState());
        for (BlockPos p : ropeSpots) blocks.put(p, RopeBridge.connectorState());

        for (BlockPos p : blocks.keySet()) if (!level.getBlockState(p).isAir()) return null;
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) level.setBlock(e.getKey(), e.getValue(), 3);

        ServerSubLevel ship = SableBridge.assemble(level, at, new ArrayList<>(blocks.keySet()));
        if (ship == null) return null;
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARK, true);
        ship.setUserDataTag(tag);
        return locate(level, ship);
    }

    /** Finds the connectors, the hold and the deck in the assembled ship's own coordinates. */
    static RaiderShip locate(ServerLevel level, ServerSubLevel ship) {
        BoundingBox3ic box = SableBridge.plotBounds(ship);
        List<BlockPos> connectors = new ArrayList<>();
        BlockPos hold = null;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    m.set(x, y, z);
                    if (RopeBridge.isConnector(level, m)) connectors.add(m.immutable());
                    else if (level.getBlockState(m).is(Blocks.BARREL)) hold = m.immutable();
                }
        connectors.sort(Comparator.comparingInt(BlockPos::getZ));
        double cx = (box.minX() + box.maxX() + 1) / 2.0, cz = (box.minZ() + box.maxZ() + 1) / 2.0;
        double deckTop = connectors.isEmpty() ? box.minY() + 2 : connectors.get(0).getY();
        return new RaiderShip(ship, connectors, hold, new Vec3(cx, deckTop, cz));
    }

    public static boolean isRaiderShip(ServerSubLevel ship) {
        CompoundTag tag = ship.getUserDataTag();
        return tag != null && tag.getBoolean(MARK);
    }

    /** How far the hull sticks out past its rope connectors towards the player's ship (x), in blocks. */
    public double overhang() {
        BoundingBox3ic box = SableBridge.plotBounds(ship);
        return Math.max(0, deckPoint.x - box.minX());
    }
}
