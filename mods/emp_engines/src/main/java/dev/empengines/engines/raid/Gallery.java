package dev.empengines.engines.raid;

import dev.empengines.engines.EmpyreanEngines;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The raider ship gallery: ships set out in the world (as plain blocks, not flying) inside a quartz
 * outline with a floating name, so they can be looked at and configured with the marker kit.
 * What's placed where, and any configuration in progress, is saved with the world.
 */
public final class Gallery extends SavedData {
    private static final String ID = "emp_engines_raider_gallery";
    static final int GAP = 8;

    /** A ship placed in the gallery: its structure's origin and size, and its name tag. */
    static final class Entry {
        final String ship;
        final BlockPos origin;
        final Vec3i size;
        UUID label;
        Entry(String ship, BlockPos origin, Vec3i size) { this.ship = ship; this.origin = origin; this.size = size; }
        BlockPos far() { return origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1); }
        boolean contains(BlockPos p) {
            return p.getX() >= origin.getX() && p.getY() >= origin.getY() && p.getZ() >= origin.getZ()
                    && p.getX() <= far().getX() && p.getY() <= far().getY() && p.getZ() <= far().getZ();
        }
    }

    /** A marker block placed during configuration. */
    record Marker(MarkerKit.Type type, Direction facing) {}

    /** A ship being configured: the markers placed so far and any speed or turn set before saving. */
    static final class Session {
        final Map<BlockPos, Marker> markers = new LinkedHashMap<>();
        double speed, turn;
    }

    final Map<String, Entry> entries = new LinkedHashMap<>();
    final Map<String, Session> sessions = new HashMap<>();

    public static Gallery get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Gallery::new, Gallery::load, null), ID);
    }

    // --- placing and clearing ----------------------------------------------------------------

    /** Places a ship's structure with its outline and name tag. Returns null with a reason if it can't. */
    Entry place(ServerLevel level, RaiderShipDef def, BlockPos origin, String[] why) {
        Optional<StructureTemplate> found = def.template(level);
        if (found.isEmpty()) { why[0] = "No structure " + def.structure + " (is its mod installed?)"; return null; }
        StructureTemplate t = found.get();
        Vec3i size = t.getSize();
        clear(level, def.name);
        t.placeInWorld(level, origin, origin, new StructurePlaceSettings().setIgnoreEntities(true), level.random, Block.UPDATE_CLIENTS);
        Entry e = new Entry(def.name, origin, size);
        frame(level, e, Blocks.QUARTZ_BLOCK.defaultBlockState());
        e.label = label(level, e, def);
        entries.put(def.name, e);
        setDirty();
        return e;
    }

    /** Where a row of ships starting near {@code p} should go: clear air, about 20 blocks above them. */
    static BlockPos rowStart(ServerLevel level, ServerPlayer p, int tallest) {
        int y = Math.min(level.getMaxBuildHeight() - tallest - 4, Math.max(level.getMinBuildHeight() + 2, (int) p.getY() + 20));
        return BlockPos.containing(p.getX() + 6, y, p.getZ() + 6);
    }

    /** True if the box (plus its outline) is all air. */
    static boolean clearAir(ServerLevel level, BlockPos origin, Vec3i size) {
        for (BlockPos q : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(size.getX(), size.getY(), size.getZ())))
            if (!level.getBlockState(q).isAir()) return false;
        return true;
    }

    void clear(ServerLevel level, String ship) {
        Entry e = entries.remove(ship);
        if (e == null) return;
        for (BlockPos q : BlockPos.betweenClosed(e.origin.offset(-1, -1, -1), e.far().offset(1, 1, 1)))
            if (!level.getBlockState(q).isAir()) level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (e.label != null) {
            Entity label = level.getEntity(e.label);
            if (label != null) label.discard();
        }
        // Any stray item entities the structure dropped while being cleared
        for (Entity it : level.getEntities((Entity) null, new AABB(e.origin).expandTowards(e.size.getX(), e.size.getY(), e.size.getZ()).inflate(2),
                x -> x.getType() == EntityType.ITEM)) it.discard();
        sessions.remove(ship);
        setDirty();
    }

    /** The edges of the box, one block outside the ship. */
    private static void frame(ServerLevel level, Entry e, net.minecraft.world.level.block.state.BlockState state) {
        BlockPos a = e.origin.offset(-1, -1, -1), b = e.far().offset(1, 1, 1);
        for (BlockPos q : BlockPos.betweenClosed(a, b)) {
            int edges = (q.getX() == a.getX() || q.getX() == b.getX() ? 1 : 0) + (q.getY() == a.getY() || q.getY() == b.getY() ? 1 : 0)
                    + (q.getZ() == a.getZ() || q.getZ() == b.getZ() ? 1 : 0);
            if (edges >= 2) level.setBlock(q, state, Block.UPDATE_CLIENTS);
        }
    }

    private static UUID label(ServerLevel level, Entry e, RaiderShipDef def) {
        Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(level);
        if (d == null) return null;
        MutableComponent text = Component.literal(def.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\n" + (def.configured() ? "configured" : "not configured")
                        + (def.configured() && !def.enabled ? ", disabled" : "")).withStyle(def.configured() ? ChatFormatting.GREEN : ChatFormatting.RED));
        CompoundTag tag = new CompoundTag();
        tag.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        tag.putString("billboard", "center");
        d.load(tag);
        d.setPos(e.origin.getX() + e.size.getX() / 2.0, e.origin.getY() + e.size.getY() + 2.5, e.origin.getZ() + e.size.getZ() / 2.0);
        level.addFreshEntity(d);
        return d.getUUID();
    }

    void relabel(ServerLevel level, RaiderShipDef def) {
        Entry e = entries.get(def.name);
        if (e == null) return;
        if (e.label != null) {
            Entity old = level.getEntity(e.label);
            if (old != null) old.discard();
        }
        e.label = label(level, e, def);
        setDirty();
    }

    Entry at(BlockPos p) {
        for (Entry e : entries.values()) if (e.contains(p)) return e;
        return null;
    }

    // --- configuring -----------------------------------------------------------------------

    /** Starts configuring: puts any saved markers back as blocks so they can be moved. */
    Session begin(ServerLevel level, RaiderShipDef def) {
        Entry e = entries.get(def.name);
        Session s = sessions.computeIfAbsent(def.name, k -> new Session());
        if (e != null && s.markers.isEmpty() && def.configured()) {
            put(level, s, e.origin.offset(def.helm), MarkerKit.Type.HELM, def.front);
            for (BlockPos p : def.hooksPort) put(level, s, e.origin.offset(p), MarkerKit.Type.HOOK_PORT, null);
            for (BlockPos p : def.hooksStarboard) put(level, s, e.origin.offset(p), MarkerKit.Type.HOOK_STARBOARD, null);
            for (BlockPos p : def.posts) put(level, s, e.origin.offset(p), MarkerKit.Type.POST, null);
            for (BlockPos p : def.witches) put(level, s, e.origin.offset(p), MarkerKit.Type.WITCH, null);
            if (def.hold != null) put(level, s, e.origin.offset(def.hold), MarkerKit.Type.HOLD, null);
        }
        setDirty();
        return s;
    }

    private static void put(ServerLevel level, Session s, BlockPos p, MarkerKit.Type t, Direction facing) {
        level.setBlock(p, t.block.defaultBlockState(), Block.UPDATE_CLIENTS);
        s.markers.put(p.immutable(), new Marker(t, facing));
    }

    /** A marker block was placed (called from the block-place event). */
    void placed(ServerPlayer p, BlockPos pos, ItemStack held) {
        MarkerKit.Type t = MarkerKit.typeOf(held);
        if (t == null) return;
        Entry e = at(pos);
        if (e == null) {
            p.displayClientMessage(Component.literal("Markers only count inside a gallery ship's quartz outline.").withStyle(ChatFormatting.RED), true);
            return;
        }
        Session s = sessions.get(e.ship);
        if (s == null) {
            p.displayClientMessage(Component.literal("Start with /skyraid config " + e.ship + " first.").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (t == MarkerKit.Type.HELM) s.markers.values().removeIf(m -> m.type() == MarkerKit.Type.HELM);   // only one helm
        if (t == MarkerKit.Type.HOLD) s.markers.values().removeIf(m -> m.type() == MarkerKit.Type.HOLD);
        Direction facing = t == MarkerKit.Type.HELM ? p.getDirection() : null;
        s.markers.put(pos.immutable(), new Marker(t, facing));
        setDirty();
        p.displayClientMessage(Component.literal(t.label + " placed" + (facing != null ? ", front: " + facing.getName() : "")).withStyle(ChatFormatting.GREEN), true);
    }

    void broken(BlockPos pos) {
        for (Session s : sessions.values()) if (s.markers.remove(pos) != null) setDirty();
    }

    /** Saves the markers into the ship's file and takes the marker blocks away. Returns the stats, or null with a reason. */
    List<Component> save(ServerLevel level, RaiderShipDef def, String[] why) {
        Entry e = entries.get(def.name);
        Session s = sessions.get(def.name);
        if (e == null || s == null) { why[0] = "Not configuring " + def.name + ". Run /skyraid config " + def.name + " first."; return null; }
        // Only markers still standing count
        s.markers.entrySet().removeIf(m -> !level.getBlockState(m.getKey()).is(m.getValue().type().block));
        List<Map.Entry<BlockPos, Marker>> helms = s.markers.entrySet().stream().filter(m -> m.getValue().type() == MarkerKit.Type.HELM).toList();
        if (helms.isEmpty()) { why[0] = "Place a Helm (red concrete), facing the front of the ship."; return null; }
        if (s.markers.values().stream().noneMatch(m -> m.type() == MarkerKit.Type.HOOK_PORT || m.type() == MarkerKit.Type.HOOK_STARBOARD)) {
            why[0] = "Place at least one Port Hook (black concrete) or Starboard Hook (green concrete).";
            return null;
        }
        if (s.markers.values().stream().noneMatch(m -> m.type() == MarkerKit.Type.POST)) { why[0] = "Place at least one Raider Post (light gray concrete)."; return null; }

        def.helm = helms.get(0).getKey().subtract(e.origin);
        def.front = helms.get(0).getValue().facing() == null ? Direction.NORTH : helms.get(0).getValue().facing();
        def.hooksPort.clear(); def.hooksStarboard.clear(); def.posts.clear(); def.witches.clear(); def.hold = null;
        for (Map.Entry<BlockPos, Marker> m : s.markers.entrySet()) {
            BlockPos rel = m.getKey().subtract(e.origin);
            switch (m.getValue().type()) {
                case HOOK_PORT -> def.hooksPort.add(rel);
                case HOOK_STARBOARD -> def.hooksStarboard.add(rel);
                case POST -> def.posts.add(rel);
                case WITCH -> def.witches.add(rel);
                case HOLD -> def.hold = rel;
                default -> { }
            }
        }
        // Take the marker blocks away, then keep the ship as it stands now, with any blocks added or taken away while configuring it
        for (BlockPos p : s.markers.keySet()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        StructureTemplate design = new StructureTemplate();
        design.fillFromWorld(level, e.origin, e.size, false, Blocks.AIR);
        try {
            def.saveDesign(design);
        } catch (IOException ex) {
            why[0] = "Couldn't write the ship's design: " + ex.getMessage();
            return null;
        }
        def.measure(level);
        def.blocks = countBlocks(design);
        if (s.speed > 0) def.speed = s.speed;
        if (s.turn > 0) def.turn = s.turn;
        try {
            def.save();
        } catch (IOException ex) {
            why[0] = "Couldn't write the ship's file: " + ex.getMessage();
            return null;
        }
        sessions.remove(def.name);
        relabel(level, def);
        setDirty();
        EmpyreanEngines.LOGGER.info("Sky Raiders: saved raider ship {}", def.name);
        return stats(def);
    }

    /** Ends configuring without saving: the marker blocks placed are taken away. */
    void cancel(ServerLevel level, String ship) {
        Session s = sessions.remove(ship);
        if (s == null) return;
        for (BlockPos p : s.markers.keySet())
            if (level.getBlockState(p).is(s.markers.get(p).type().block)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        setDirty();
    }

    // --- stats -------------------------------------------------------------------------------

    static List<Component> stats(RaiderShipDef d) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(d.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("  (" + d.structure + ")").withStyle(ChatFormatting.DARK_GRAY)));
        out.add(Component.literal(d.size[0] + " x " + d.size[1] + " x " + d.size[2] + ", " + d.blocks + " blocks"
                + (d.customDesign() ? " (your gallery design)" : " (as its structure file)")));
        if (!d.configured()) {
            out.add(Component.literal("Not configured: /skyraid config " + d.name).withStyle(ChatFormatting.RED));
            return out;
        }
        out.add(Component.literal(String.format("Front: %s   Top speed %.0f blocks/s   Turn %.0f deg/s   0 to top speed in 4 s",
                d.front.getName(), d.speed, d.turn)));
        int archers = Raid.archerCount(d.posts.size());
        out.add(Component.literal("Crew " + d.posts.size() + " + captain (" + archers + (archers == 1 ? " archer" : " archers") + " stay aboard, "
                + (d.posts.size() - archers) + " + captain board)" + (d.witches.isEmpty() ? "" : ", " + d.witches.size() + (d.witches.size() == 1 ? " witch" : " witches"))));
        out.add(Component.literal("Hooks: " + d.hooksPort.size() + " port, " + d.hooksStarboard.size() + " starboard"
                + (d.hooksPort.isEmpty() || d.hooksStarboard.isEmpty() ? " (always comes up on the " + (d.hooksPort.isEmpty() ? "starboard" : "port") + " side)" : " (comes up on either side)")
                + ", loot hold: " + (d.hold == null ? "first barrel or chest" : "marked")));
        out.add(Component.literal(d.enabled ? "Status: ready for raids" : "Status: configured but disabled (/skyraid ships enable " + d.name + ")")
                .withStyle(d.enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return out;
    }

    static int countBlocks(StructureTemplate t) {
        CompoundTag tag = t.save(new CompoundTag());
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        int n = 0;
        for (int i = 0; i < blocks.size(); i++) {
            int state = blocks.getCompound(i).getInt("state");
            String name = state < palette.size() ? palette.getCompound(state).getString("Name") : "";
            if (!name.equals("minecraft:air") && !name.equals("minecraft:structure_void") && !name.equals("minecraft:cave_air")) n++;
        }
        return n;
    }

    // --- saving with the world ---------------------------------------------------------------

    private static Gallery load(CompoundTag tag, HolderLookup.Provider lookup) {
        Gallery g = new Gallery();
        for (Tag t : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Entry e = new Entry(c.getString("Ship"), BlockPos.of(c.getLong("Origin")),
                    new Vec3i(c.getInt("SX"), c.getInt("SY"), c.getInt("SZ")));
            if (c.hasUUID("Label")) e.label = c.getUUID("Label");
            g.entries.put(e.ship, e);
        }
        for (Tag t : tag.getList("Sessions", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Session s = new Session();
            s.speed = c.getDouble("Speed");
            s.turn = c.getDouble("Turn");
            for (Tag mt : c.getList("Markers", Tag.TAG_COMPOUND)) {
                CompoundTag m = (CompoundTag) mt;
                try {
                    s.markers.put(BlockPos.of(m.getLong("Pos")), new Marker(MarkerKit.Type.valueOf(m.getString("Type")),
                            m.contains("Facing") ? Direction.byName(m.getString("Facing")) : null));
                } catch (IllegalArgumentException ignored) { }
            }
            g.sessions.put(c.getString("Ship"), s);
        }
        return g;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("Ship", e.ship);
            c.putLong("Origin", e.origin.asLong());
            c.putInt("SX", e.size.getX()); c.putInt("SY", e.size.getY()); c.putInt("SZ", e.size.getZ());
            if (e.label != null) c.putUUID("Label", e.label);
            list.add(c);
        }
        tag.put("Entries", list);
        ListTag ss = new ListTag();
        for (Map.Entry<String, Session> se : sessions.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("Ship", se.getKey());
            c.putDouble("Speed", se.getValue().speed);
            c.putDouble("Turn", se.getValue().turn);
            ListTag ms = new ListTag();
            for (Map.Entry<BlockPos, Marker> m : se.getValue().markers.entrySet()) {
                CompoundTag mc = new CompoundTag();
                mc.putLong("Pos", m.getKey().asLong());
                mc.putString("Type", m.getValue().type().name());
                if (m.getValue().facing() != null) mc.putString("Facing", m.getValue().facing().getName());
                ms.add(mc);
            }
            c.put("Markers", ms);
            ss.add(c);
        }
        tag.put("Sessions", ss);
        return tag;
    }
}
