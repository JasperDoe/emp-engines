package dev.empengines.engines.raid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.empengines.engines.Config;
import dev.empengines.engines.EmpyreanEngines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A raider ship, as configured with the gallery: which structure it is, which way its front faces,
 * where its anchors fire from, where its crew stands, its speed. Positions are relative to the
 * structure's own origin (as the file is, unrotated).
 *
 * Saved as {@code config/emp_engines/raider_ships/<name>.json}. The config folder ships with the pack,
 * so a ship configured on a test world goes to the server with {@code tools/pull-raider-ships.ps1}.
 */
public final class RaiderShipDef {
    public final String name;
    public ResourceLocation structure;
    public Direction front = Direction.NORTH;
    public BlockPos helm;
    /** Boarding hooks on the ship's left (port) and right (starboard); a raid uses one side's. */
    public final List<BlockPos> hooksPort = new ArrayList<>(), hooksStarboard = new ArrayList<>();
    public final List<BlockPos> posts = new ArrayList<>();
    public final List<BlockPos> witches = new ArrayList<>();
    public BlockPos hold;
    public double speed, turn;
    public boolean enabled = true;
    public int blocks;
    public int[] size = {0, 0, 0};

    RaiderShipDef(String name, ResourceLocation structure) {
        this.name = name;
        this.structure = structure;
    }

    /** Ready to raid with: a helm, at least one hook and one post. */
    public boolean configured() { return helm != null && (!hooksPort.isEmpty() || !hooksStarboard.isEmpty()) && !posts.isEmpty(); }

    /** The hooks on one side. */
    public List<BlockPos> hooks(boolean starboard) { return starboard ? hooksStarboard : hooksPort; }

    public int crew() { return posts.size() + 1; }   // plus the captain

    /** Default top speed by size: small ships are quick, big ones slower (blocks per second). */
    static double defaultSpeed(int blocks) { return blocks < 600 ? 22 : blocks < 1500 ? 18 : 15; }

    // --- the list of ships -----------------------------------------------------------------

    private static final Map<String, RaiderShipDef> SHIPS = new LinkedHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static Path folder() { return FMLPaths.CONFIGDIR.get().resolve("emp_engines").resolve("raider_ships"); }

    /** Every ship: the structures listed in the config (raidShips) plus any saved file. Re-reads the files. */
    public static synchronized Map<String, RaiderShipDef> all() {
        SHIPS.clear();
        for (String s : Config.RAID_SHIPS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id == null) continue;
            String name = nameFor(id);
            SHIPS.putIfAbsent(name, new RaiderShipDef(name, id));
        }
        Path dir = folder();
        if (Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    String name = f.getFileName().toString().replace(".json", "");
                    try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                        RaiderShipDef d = read(name, JsonParser.parseReader(r).getAsJsonObject());
                        if (d != null) SHIPS.put(name, d);
                    } catch (Exception e) {
                        EmpyreanEngines.LOGGER.warn("Sky Raiders: couldn't read {}: {}", f, e.toString());
                    }
                }
            } catch (IOException e) {
                EmpyreanEngines.LOGGER.warn("Sky Raiders: couldn't list {}: {}", dir, e.toString());
            }
        }
        return new LinkedHashMap<>(SHIPS);
    }

    public static RaiderShipDef get(String name) { return all().get(name); }

    public static String nameFor(ResourceLocation id) { return id.getPath().replace('/', '_'); }

    // --- size and defaults from the structure file ----------------------------------------

    // --- the design: the structure file, or the gallery copy saved with its changes ---------------

    Path designFile() { return folder().resolve(name + ".nbt"); }

    /** True if the ship was changed in the gallery and saved with its changes. */
    public boolean customDesign() { return Files.isRegularFile(designFile()); }

    /** The ship's blocks: the design saved from the gallery if there is one, otherwise the structure file. */
    public Optional<StructureTemplate> template(ServerLevel level) {
        if (customDesign()) {
            try {
                CompoundTag tag = NbtIo.readCompressed(designFile(), NbtAccounter.unlimitedHeap());
                return Optional.of(level.getStructureManager().readStructure(tag));
            } catch (IOException | RuntimeException e) {
                EmpyreanEngines.LOGGER.warn("Sky Raiders: couldn't read {}, using {} instead: {}", designFile(), structure, e.toString());
            }
        }
        return level.getStructureManager().get(structure);
    }

    /** Saves the ship's blocks as they stand in the gallery (any changes made there go with it to raids). */
    void saveDesign(StructureTemplate t) throws IOException {
        Files.createDirectories(folder());
        NbtIo.writeCompressed(t.save(new CompoundTag()), designFile());
    }

    /** Fills in size, block count and a default speed and turn rate from the structure file. */
    public boolean measure(ServerLevel level) {
        Optional<StructureTemplate> t = template(level);
        if (t.isEmpty()) return false;
        var s = t.get().getSize();
        size = new int[]{s.getX(), s.getY(), s.getZ()};
        blocks = Gallery.countBlocks(t.get());
        if (speed <= 0) speed = defaultSpeed(blocks);
        if (turn <= 0) turn = 25;
        return true;
    }

    // --- JSON ------------------------------------------------------------------------------

    public void save() throws IOException {
        Files.createDirectories(folder());
        JsonObject o = new JsonObject();
        o.addProperty("structure", structure.toString());
        o.addProperty("front", front.getName());
        if (helm != null) o.add("helm", pos(helm));
        o.add("hooksPort", list(hooksPort));
        o.add("hooksStarboard", list(hooksStarboard));
        o.add("posts", list(posts));
        o.add("witches", list(witches));
        if (hold != null) o.add("hold", pos(hold));
        o.addProperty("speed", speed);
        o.addProperty("turn", turn);
        o.addProperty("enabled", enabled);
        o.addProperty("blocks", blocks);
        JsonArray sz = new JsonArray();
        for (int v : size) sz.add(v);
        o.add("size", sz);
        try (Writer w = Files.newBufferedWriter(folder().resolve(name + ".json"), StandardCharsets.UTF_8)) {
            GSON.toJson(o, w);
        }
    }

    public void deleteFile() throws IOException {
        Files.deleteIfExists(folder().resolve(name + ".json"));
        Files.deleteIfExists(designFile());
    }

    private static RaiderShipDef read(String name, JsonObject o) {
        ResourceLocation id = ResourceLocation.tryParse(o.get("structure").getAsString());
        if (id == null) return null;
        RaiderShipDef d = new RaiderShipDef(name, id);
        Direction f = o.has("front") ? Direction.byName(o.get("front").getAsString()) : null;
        d.front = f != null && f.getAxis().isHorizontal() ? f : Direction.NORTH;
        if (o.has("helm")) d.helm = pos(o.get("helm"));
        readList(o, "hooksPort", d.hooksPort);
        readList(o, "hooksStarboard", d.hooksStarboard);
        if (o.has("hooks")) {   // saved before hooks had sides: sort them by which side of the ship they're on
            List<BlockPos> old = new ArrayList<>();
            readList(o, "hooks", old);
            int[] sz = o.has("size") ? new int[]{o.getAsJsonArray("size").get(0).getAsInt(), 0, o.getAsJsonArray("size").get(2).getAsInt()} : new int[]{0, 0, 0};
            for (BlockPos h : old) {
                double rx = h.getX() + 0.5 - sz[0] / 2.0, rz = h.getZ() + 0.5 - sz[2] / 2.0;
                double right = -d.front.getStepZ() * rx + d.front.getStepX() * rz;
                (right >= 0 ? d.hooksStarboard : d.hooksPort).add(h);
            }
        }
        readList(o, "posts", d.posts);
        readList(o, "witches", d.witches);
        if (o.has("hold")) d.hold = pos(o.get("hold"));
        d.speed = o.has("speed") ? o.get("speed").getAsDouble() : 0;
        d.turn = o.has("turn") ? o.get("turn").getAsDouble() : 0;
        d.enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
        d.blocks = o.has("blocks") ? o.get("blocks").getAsInt() : 0;
        if (o.has("size")) {
            JsonArray a = o.getAsJsonArray("size");
            d.size = new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
        }
        return d;
    }

    private static JsonArray pos(BlockPos p) {
        JsonArray a = new JsonArray();
        a.add(p.getX()); a.add(p.getY()); a.add(p.getZ());
        return a;
    }

    private static BlockPos pos(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
    }

    private static JsonArray list(List<BlockPos> ps) {
        JsonArray a = new JsonArray();
        for (BlockPos p : ps) a.add(pos(p));
        return a;
    }

    private static void readList(JsonObject o, String key, List<BlockPos> out) {
        if (!o.has(key)) return;
        for (JsonElement e : o.getAsJsonArray(key)) out.add(pos(e));
    }
}
