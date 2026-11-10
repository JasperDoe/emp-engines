package dev.skybound.createskybound.raid;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.skybound.createskybound.launch.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a ship's deck in its own (plot) coordinates: where a mob can stand, which inventories
 * hold items, and which standing spots connect to them. Used to choose where boarding anchors land,
 * so every rope leads somewhere raiders can actually walk to loot.
 *
 * A "spot" is the solid block a mob stands on; the mob's feet are at spot + 1.
 */
public final class DeckScan {
    /** An inventory with items, and the spot a mob stands on to reach it. */
    public record Loot(BlockPos inventory, BlockPos standOn) {}

    /** Where an anchor goes (on top of {@code anchorSpot}) and the spot next to it where raiders step off. */
    public record Landing(BlockPos anchorSpot, BlockPos stepOff, Loot loot) {}

    private final ServerLevel level;
    private final BoundingBox3ic box;
    private final Set<BlockPos> walkable = new HashSet<>();
    private final List<Loot> loot = new ArrayList<>();
    /** Walking distance from each reachable spot to its nearest loot, and which loot that is. */
    private final Map<BlockPos, Integer> distance = new HashMap<>();
    private final Map<BlockPos, Loot> nearestLoot = new HashMap<>();

    public DeckScan(ServerLevel level, ServerSubLevel ship) {
        this.level = level;
        this.box = SableBridge.plotBounds(ship);
        scan();
    }

    public boolean hasLoot() { return !loot.isEmpty(); }

    private void scan() {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    m.set(x, y, z);
                    if (isWalkable(m)) walkable.add(m.immutable());
                    if (level.getBlockEntity(m) != null && hasItems(m)) {
                        BlockPos inv = m.immutable();
                        BlockPos stand = standingSpotNear(inv);
                        if (stand != null) loot.add(new Loot(inv, stand));
                    }
                }
        // Breadth-first from every loot spot over the walkable deck.
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Loot l : loot) {
            if (distance.putIfAbsent(l.standOn(), 0) == null) {
                nearestLoot.put(l.standOn(), l);
                queue.add(l.standOn());
            }
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            int d = distance.get(p);
            for (BlockPos n : neighbours(p)) {
                if (distance.containsKey(n)) continue;
                distance.put(n, d + 1);
                nearestLoot.put(n, nearestLoot.get(p));
                queue.add(n);
            }
        }
    }

    /** Solid enough to stand on, with two blocks of headroom. */
    public boolean isWalkable(BlockPos p) {
        if (!box.contains(p.getX(), p.getY(), p.getZ())) return false;
        if (!level.getBlockState(p).isFaceSturdy(level, p, Direction.UP)) return false;
        return level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getBlockState(p.above(2)).getCollisionShape(level, p.above(2)).isEmpty();
    }

    private boolean hasItems(BlockPos p) {
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
        if (h == null) return false;
        for (int i = 0; i < h.getSlots(); i++) if (!h.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    /** A walkable spot beside the inventory (its feet level with the inventory, or one below). */
    private BlockPos standingSpotNear(BlockPos inv) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int dy = -1; dy >= -2; dy--) {
                BlockPos s = inv.relative(d).offset(0, dy, 0);
                if (walkable.contains(s) || isWalkable(s)) return s;
            }
        }
        return null;
    }

    /** Spots a mob can walk to from p: four sides, one step up or down. */
    private List<BlockPos> neighbours(BlockPos p) {
        List<BlockPos> out = new ArrayList<>(4);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos n = p.relative(d).offset(0, dy, 0);
                if (walkable.contains(n)) { out.add(n); break; }
            }
            BlockPos up = p.relative(d).above();
            if (walkable.contains(up) && level.getBlockState(p.above(3)).getCollisionShape(level, p.above(3)).isEmpty()) out.add(up);
        }
        return out;
    }

    /**
     * Up to {@code count} anchor landings: an open spot for the anchor, with a neighbouring spot that
     * walks to loot. Prefers the spots closest to {@code from} (the raider ship, in this ship's plot
     * coordinates), kept at least 3 blocks apart.
     */
    public List<Landing> landings(Vec3 from, int count) {
        List<Landing> candidates = new ArrayList<>();
        for (BlockPos a : walkable) {
            if (distance.containsKey(a) && distance.get(a) == 0) continue;   // keep loot spots clear
            for (BlockPos n : neighbours(a)) {
                // With loot aboard, the step-off spot must walk to it; with none, any open neighbour will do.
                if (n.getY() != a.getY() || (!loot.isEmpty() && !distance.containsKey(n))) continue;
                candidates.add(new Landing(a, n, nearestLoot.get(n)));
                break;
            }
        }
        candidates.sort(Comparator.comparingDouble(l -> Vec3.atCenterOf(l.anchorSpot()).distanceToSqr(from)));
        List<Landing> chosen = new ArrayList<>();
        for (Landing l : candidates) {
            boolean spaced = chosen.stream().allMatch(c -> c.anchorSpot().distSqr(l.anchorSpot()) >= 9);
            if (spaced) chosen.add(l);
            if (chosen.size() >= count) break;
        }
        return chosen;
    }

    /**
     * Spots along this ship's port edge (lowest x) where a rope can be tied: on the main deck (the
     * walkable layer with the most room in the lower part of the hull, so not the top of a balloon),
     * open on the outside, at least 3 apart.
     */
    public List<BlockPos> edgeSpots(int count) {
        int top = box.minY() + (int) Math.ceil((box.maxY() - box.minY()) * 0.65);
        Map<Integer, Integer> perLayer = new HashMap<>();
        for (BlockPos p : walkable) if (p.getY() <= top) perLayer.merge(p.getY(), 1, Integer::sum);
        int deckY = perLayer.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Integer.MIN_VALUE);
        if (deckY == Integer.MIN_VALUE) return List.of();
        // For each row along the hull (z), the outermost walkable spot towards -x.
        Map<Integer, BlockPos> outermost = new HashMap<>();
        for (BlockPos p : walkable) {
            if (p.getY() != deckY) continue;
            outermost.merge(p.getZ(), p, (a, b) -> a.getX() <= b.getX() ? a : b);
        }
        double midZ = (box.minZ() + box.maxZ()) / 2.0;
        List<BlockPos> rows = new ArrayList<>(outermost.values());
        rows.sort(Comparator.comparingDouble((BlockPos p) -> p.getX()).thenComparingDouble(p -> Math.abs(p.getZ() - midZ)));
        List<BlockPos> chosen = new ArrayList<>();
        for (BlockPos p : rows) {
            if (chosen.stream().allMatch(c -> Math.abs(c.getZ() - p.getZ()) >= 3)) chosen.add(p);
            if (chosen.size() >= count) break;
        }
        return chosen;
    }

    /** Where to stand to reach this loot again later (the inventory may have moved by then: re-scan if so). */
    public List<Loot> loot() { return loot; }
}
