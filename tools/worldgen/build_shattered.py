"""Generates the Shattered Skies world generation (Create: Skybound's sky-run dimension).

Run: python tools/worldgen/build_shattered.py
Writes JSON into mods/createskybound/src/main/resources/data/createskybound/worldgen/ and the
dimension file. Everything is vanilla world generation data: no other mod is needed.

Terrain: big floating islands, far apart, with jagged teardrop undersides and broken shards
around their edges. Tuning (see TERRAIN below):
  - island size and spacing come from the island noise's octave and the threshold:
    octave -8 with threshold 0.30 gives islands about 60-290 blocks across (median ~155),
    about 450-500 blocks apart centre to centre. Octave -9 doubles both.
  - island height: TOP_Y +/- HEIGHT_SPREAD, kept below the arrival pad at Y 200.
Colour: five biomes with tinted grass and leaves (purple, blue, indigo, pale, pink), their own sky,
fog, water, particles and rock patches.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "mods", "createskybound", "src", "main", "resources", "data", "createskybound")
NS = "createskybound"

TERRAIN = {
    "island_octave": -8,       # lower = bigger islands, further apart
    "threshold": 0.30,         # higher = smaller islands, more sky between them
    "base_y": 140,             # middle of the island band
    "height_spread": 35,       # islands sit at base_y +/- this
    "soil_depth": 6,           # flat-ish top above the island's base level
    "hill_height": 10,         # bumps on top
    "underside_depth": 60,     # how far the teardrop hangs below (at an island's heart)
    "underside_jag": 30,       # stalactite variation on the underside
    "edge_crack": 0.08,        # how broken the edges are
    "shard_band": 0.12,        # how far out from an island shards float
}


def write(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# --- small helpers for density functions -------------------------------------------------

def add(a, b): return {"type": "minecraft:add", "argument1": a, "argument2": b}
def mul(a, b): return {"type": "minecraft:mul", "argument1": a, "argument2": b}
def dmin(a, b): return {"type": "minecraft:min", "argument1": a, "argument2": b}
def dmax(a, b): return {"type": "minecraft:max", "argument1": a, "argument2": b}
def dabs(a): return {"type": "minecraft:abs", "argument": a}
def clamp(a, lo, hi): return {"type": "minecraft:clamp", "input": a, "min": lo, "max": hi}
def noise(name, xz=1.0, y=0.0): return {"type": "minecraft:noise", "noise": f"{NS}:shattered/{name}", "xz_scale": xz, "y_scale": y}
def cache2d(a): return {"type": "minecraft:cache_2d", "argument": a}
def ref(name): return f"{NS}:shattered/{name}"
def neg(a): return mul(-1.0, a)


Y = {"type": "minecraft:y_clamped_gradient", "from_y": 0, "to_y": 320, "from_value": 0.0, "to_value": 320.0}


def terrain():
    t = TERRAIN
    write("worldgen/noise/shattered/islands.json", {"firstOctave": t["island_octave"], "amplitudes": [1.0, 0.5, 0.25]})
    write("worldgen/noise/shattered/height.json", {"firstOctave": t["island_octave"] - 1, "amplitudes": [1.0]})
    write("worldgen/noise/shattered/hills.json", {"firstOctave": -6, "amplitudes": [1.0, 0.5]})
    write("worldgen/noise/shattered/jag.json", {"firstOctave": -4, "amplitudes": [1.0, 0.5]})
    write("worldgen/noise/shattered/crack.json", {"firstOctave": -5, "amplitudes": [1.0]})
    write("worldgen/noise/shattered/shards.json", {"firstOctave": -4, "amplitudes": [1.0, 0.5]})
    write("worldgen/noise/shattered/temperature.json", {"firstOctave": -10, "amplitudes": [1.5, 0.0, 1.0, 0.0, 0.0, 0.0]})
    write("worldgen/noise/shattered/humidity.json", {"firstOctave": -9, "amplitudes": [1.0, 1.0, 0.0, 0.0, 0.0, 0.0]})
    write("worldgen/noise/shattered/patches.json", {"firstOctave": -5, "amplitudes": [1.0, 1.0]})

    T = t["threshold"]
    # Island mask (2D): above the threshold is island.
    write("worldgen/density_function/shattered/mask.json", cache2d(noise("islands")))
    # Each island's base height (2D), kept in a band below the arrival pad.
    spread = t["height_spread"] / 0.25
    write("worldgen/density_function/shattered/base.json",
          cache2d(add(float(t["base_y"]), mul(spread, clamp(noise("height"), -0.25, 0.25)))))
    # How deep inside an island a column is: 0 at the edge, 1 well inside.
    write("worldgen/density_function/shattered/heart.json", cache2d(clamp(mul(3.0, add(ref("mask"), -T)), 0.0, 1.0)))

    top = add(add(ref("base"), float(t["soil_depth"])), mul(float(t["hill_height"]), noise("hills")))
    depth = add(mul(ref("heart"), add(float(t["underside_depth"]), mul(float(t["underside_jag"]), noise("jag", 1.0, 0.25)))), 4.0)
    above_bottom = mul(0.125, add(Y, neg(add(ref("base"), neg(depth)))))
    below_top = mul(0.125, add(top, neg(Y)))
    inside = mul(60.0, add(add(ref("mask"), -T), mul(t["edge_crack"], noise("crack", 1.0, 1.0))))
    island = dmin(dmin(below_top, above_bottom), inside)

    # Shards: small blobs floating in a ring just outside each island, around its height.
    blob = mul(10.0, add(noise("shards", 1.0, 1.0), -0.35))
    ring = dmin(mul(25.0, add(ref("mask"), -(T - t["shard_band"]))), mul(25.0, add(T, neg(ref("mask")))))
    band = mul(0.15, add(22.0, neg(dabs(add(Y, neg(add(ref("base"), -6.0)))))))
    shards = dmin(dmin(blob, ring), band)

    write("worldgen/density_function/shattered/density.json", dmax(island, shards))


# --- biomes -------------------------------------------------------------------------------

def rgb(h): return int(h.lstrip("#"), 16)


def block(name): return {"type": "minecraft:block", "result_state": {"Name": name}}


def cond(c, then): return {"type": "minecraft:condition", "if_true": c, "then_run": then}


def seq(*rules): return {"type": "minecraft:sequence", "sequence": list(rules)}


def depth_rule(surface, offset, add_depth, secondary=0):
    return {"type": "minecraft:stone_depth", "offset": offset, "surface_type": surface,
            "add_surface_depth": add_depth, "secondary_depth_range": secondary}


def patch(lo, hi=4.0): return {"type": "minecraft:noise_threshold", "noise": f"{NS}:shattered/patches", "min_threshold": lo, "max_threshold": hi}


MONSTERS = [
    {"type": "minecraft:zombie", "weight": 95, "minCount": 2, "maxCount": 4},
    {"type": "minecraft:skeleton", "weight": 100, "minCount": 2, "maxCount": 4},
    {"type": "minecraft:spider", "weight": 90, "minCount": 2, "maxCount": 4},
    {"type": "minecraft:creeper", "weight": 90, "minCount": 2, "maxCount": 4},
    {"type": "minecraft:enderman", "weight": 15, "minCount": 1, "maxCount": 3},
]

ORES = ["minecraft:ore_coal_upper", "minecraft:ore_iron_upper", "minecraft:ore_copper"]

BIOMES = {
    "amethyst_expanse": {
        "name": "Amethyst Expanse", "temp": 0.6, "rain": False,
        "colors": {"grass": "#9A6BD6", "foliage": "#B58CE8", "sky": "#8E7CC3", "fog": "#C9B5F0", "water": "#6B4FD1", "water_fog": "#2E1F66"},
        "particle": ("minecraft:end_rod", 0.0008),
        "under": "minecraft:dirt", "rock": "minecraft:calcite", "rock2": "minecraft:amethyst_block",
        "belly": "minecraft:purple_terracotta", "belly2": "minecraft:tuff",
        "vegetal": ["minecraft:trees_plains", "minecraft:patch_grass_plain", "minecraft:flower_meadow"],
        "extra": {"local_modifications": [f"{NS}:shattered/amethyst_geode"]},
        "creatures": [{"type": "minecraft:sheep", "weight": 8, "minCount": 2, "maxCount": 4}],
    },
    "azure_wilds": {
        "name": "Azure Wilds", "temp": 0.7, "rain": False,
        "colors": {"grass": "#3FA7C4", "foliage": "#2E6FD1", "sky": "#6FA8DC", "fog": "#A8D0F0", "water": "#2A7FD4", "water_fog": "#0E2A52"},
        "particle": ("minecraft:spore_blossom_air", 0.004),
        "under": "minecraft:dirt", "rock": "minecraft:light_blue_terracotta", "rock2": "minecraft:moss_block",
        "belly": "minecraft:light_blue_terracotta", "belly2": "minecraft:moss_block",
        "vegetal": ["minecraft:dark_forest_vegetation", "minecraft:patch_grass_forest", "minecraft:spore_blossom",
                    "minecraft:lush_caves_ceiling_vegetation"],
        "extra": {},
        "creatures": [{"type": "minecraft:rabbit", "weight": 4, "minCount": 2, "maxCount": 3}],
    },
    "twilight_shards": {
        "name": "Twilight Shards", "temp": 0.5, "rain": False,
        "colors": {"grass": "#5B3E8C", "foliage": "#3B2A6E", "sky": "#4B3F7A", "fog": "#6E5E9E", "water": "#3A2E7A", "water_fog": "#140F2E"},
        "particle": ("minecraft:white_ash", 0.006),
        "under": "minecraft:coarse_dirt", "rock": "minecraft:tuff", "rock2": "minecraft:deepslate",
        "belly": "minecraft:deepslate", "belly2": "minecraft:crying_obsidian",
        "vegetal": ["minecraft:trees_savanna", "minecraft:patch_dead_bush_2", "minecraft:patch_grass_badlands"],
        "extra": {"underground_ores": ["minecraft:ore_emerald"]},
        "creatures": [],
    },
    "pale_remnant": {
        "name": "Pale Remnant", "temp": -0.5, "rain": True,
        "colors": {"grass": "#9FC3D6", "foliage": "#B8D4E3", "sky": "#A9C7E8", "fog": "#DCE8F5", "water": "#3D57D6", "water_fog": "#050533"},
        "particle": ("minecraft:snowflake", 0.004),
        "under": "minecraft:dirt", "rock": "minecraft:packed_ice", "rock2": "minecraft:calcite",
        "belly": "minecraft:packed_ice", "belly2": "minecraft:blue_ice",
        "vegetal": ["minecraft:ice_spike", "minecraft:trees_grove", "minecraft:patch_grass_taiga"],
        "extra": {"underground_ores": ["minecraft:ore_emerald"]},
        "creatures": [{"type": "minecraft:fox", "weight": 4, "minCount": 1, "maxCount": 2}],
    },
    "cherry_rift": {
        "name": "Cherry Rift", "temp": 0.6, "rain": False,
        "colors": {"grass": "#C792D6", "foliage": "#E8A6D8", "sky": "#B49AE0", "fog": "#F0C8E6", "water": "#8A6BE0", "water_fog": "#2A1A4A"},
        "particle": ("minecraft:cherry_leaves", 0.003),
        "under": "minecraft:dirt", "rock": "minecraft:pink_terracotta", "rock2": "minecraft:calcite",
        "belly": "minecraft:pink_terracotta", "belly2": "minecraft:magenta_terracotta",
        "vegetal": ["minecraft:trees_cherry", "minecraft:flower_cherry", "minecraft:patch_grass_plain"],
        "extra": {},
        "creatures": [{"type": "minecraft:rabbit", "weight": 4, "minCount": 2, "maxCount": 3}],
    },
}

# Which biome goes where: temperature x humidity, all other climate values flat.
CLIMATE = {
    "pale_remnant": ([-1.0, -0.35], [-1.0, 1.0]),
    "twilight_shards": ([-0.35, 0.1], [-1.0, 0.0]),
    "amethyst_expanse": ([-0.35, 0.1], [0.0, 1.0]),
    "azure_wilds": ([0.1, 1.0], [0.0, 1.0]),
    "cherry_rift": ([0.1, 1.0], [-1.0, 0.0]),
}

STEPS = ["raw_generation", "lakes", "local_modifications", "underground_structures", "surface_structures", "strongholds",
         "underground_ores", "underground_decoration", "fluid_springs", "vegetal_decoration", "top_layer_modification"]


def biome(key, b):
    feats = {s: [] for s in STEPS}
    feats["underground_ores"] += ORES
    feats["underground_decoration"].append("minecraft:glow_lichen")
    feats["vegetal_decoration"] += b["vegetal"]
    feats["top_layer_modification"].append("minecraft:freeze_top_layer")
    for step, extra in b["extra"].items():
        feats[step] += extra
    c = b["colors"]
    effects = {
        "sky_color": rgb(c["sky"]), "fog_color": rgb(c["fog"]),
        "water_color": rgb(c["water"]), "water_fog_color": rgb(c["water_fog"]),
        "grass_color": rgb(c["grass"]), "foliage_color": rgb(c["foliage"]),
        "particle": {"options": {"type": b["particle"][0]}, "probability": b["particle"][1]},
        "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0},
        "additions_sound": {"sound": "minecraft:ambient.basalt_deltas.additions", "tick_chance": 0.0011},
    }
    return {
        "has_precipitation": b["rain"], "temperature": b["temp"], "downfall": 0.4,
        "effects": effects,
        "spawners": {"monster": MONSTERS, "creature": b["creatures"], "ambient": [], "axolotls": [],
                     "underground_water_creature": [], "water_creature": [], "water_ambient": [], "misc": []},
        "spawn_costs": {},
        "carvers": {},
        "features": [feats[s] for s in STEPS],
    }


def surface_for(key, b):
    snowy = key == "pale_remnant"
    top = block("minecraft:snow_block") if snowy else block("minecraft:grass_block")
    return cond({"type": "minecraft:biome", "biome_is": [f"{NS}:{key}"]}, seq(
        # the top: grass (snow on the Pale Remnant), then soil, then a coloured rock skin
        cond(depth_rule("floor", 0, False), top),
        cond(depth_rule("floor", 0, True), block(b["under"])),
        cond(depth_rule("floor", 3, True, 4), seq(cond(patch(0.0), block(b["rock2"])), block(b["rock"]))),
        # the underside, seen from below: coloured bands
        cond(depth_rule("ceiling", 0, True, 3), seq(cond(patch(0.25), block(b["belly2"])), block(b["belly"]))),
        # cliffs and the body: patches of the biome's rock, the rest stays stone (so ores can generate)
        cond(patch(0.35), block(b["rock"])),
    ))


def biomes():
    for key, b in BIOMES.items():
        write(f"worldgen/biome/{key}.json", biome(key, b))
    # Geodes at island height (vanilla's only generate deep underground).
    write("worldgen/placed_feature/shattered/amethyst_geode.json", {
        "feature": "minecraft:amethyst_geode",
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": 10},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                          "min_inclusive": {"absolute": 100}, "max_inclusive": {"absolute": 180}}},
            {"type": "minecraft:biome"},
        ],
    })


def noise_settings():
    def shifted(name):
        return {"type": "minecraft:shifted_noise", "noise": f"{NS}:shattered/{name}", "shift_x": "minecraft:shift_x",
                "shift_y": 0.0, "shift_z": "minecraft:shift_z", "xz_scale": 1.0, "y_scale": 0.0}
    router = {
        "barrier": 0.0, "fluid_level_floodedness": 0.0, "fluid_level_spread": 0.0, "lava": 0.0,
        "temperature": shifted("temperature"), "vegetation": shifted("humidity"),
        "continents": 0.0, "erosion": 0.0, "depth": 0.0, "ridges": 0.0,
        "initial_density_without_jaggedness": 0.0,
        "final_density": {"type": "minecraft:interpolated", "argument": ref("density")},
        "vein_toggle": 0.0, "vein_ridged": 0.0, "vein_gap": 0.0,
    }
    write("worldgen/noise_settings/shattered_skies.json", {
        "sea_level": -64,
        "disable_mob_generation": False,
        "aquifers_enabled": False,
        "ore_veins_enabled": False,
        "legacy_random_source": False,
        "default_block": {"Name": "minecraft:stone"},
        "default_fluid": {"Name": "minecraft:water", "Properties": {"level": "0"}},
        "noise": {"min_y": 0, "height": 320, "size_horizontal": 2, "size_vertical": 1},
        "noise_router": router,
        "spawn_target": [],
        "surface_rule": seq(*[surface_for(k, b) for k, b in BIOMES.items()]),
    })


def dimension():
    entries = []
    for key, (temp, hum) in CLIMATE.items():
        entries.append({"biome": f"{NS}:{key}", "parameters": {
            "temperature": temp, "humidity": hum, "continentalness": 0.0, "erosion": 0.0,
            "weirdness": 0.0, "depth": 0.0, "offset": 0.0}})
    write("dimension/chaotic_skies.json", {
        "type": f"{NS}:chaotic_skies",
        "generator": {"type": "minecraft:noise", "settings": f"{NS}:shattered_skies",
                      "biome_source": {"type": "minecraft:multi_noise", "biomes": entries}},
    })


def lang():
    path = os.path.join(ROOT, "..", "..", "assets", NS, "lang", "en_us.json")
    with open(path) as f:
        data = json.load(f)
    for key, b in BIOMES.items():
        data[f"biome.{NS}.{key}"] = b["name"]
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    terrain()
    biomes()
    noise_settings()
    dimension()
    lang()
    print("Shattered Skies worldgen written to", os.path.abspath(ROOT))
