"""Generates the Etheric Skies world generation (Create: Empyrean Engines's sky-run dimension).

Run: python tools/worldgen/build_etheric.py
Writes JSON into mods/emp_engines/src/main/resources/data/emp_engines/worldgen/ and the
dimension file. Everything is vanilla world generation data: no other mod is needed.

Terrain: floating islands spread evenly (one per cell of a bent, jittered grid, emp_engines:island_cells),
with jagged teardrop undersides and broken shards around their edges. All the numbers are in
etheric.toml (annotated there): layout, sizes, gaps; heights; shards; biomes; the ore table; structures.
Structures: vanilla buildings (temples, huts, ruined portals, outposts, beached wrecks, villages of every
kind, the odd mansion) on islands big enough for them, whatever the biome (emp_engines:on_island).
Testing: Silent Gear's endgame ores in the islands' stone (only with Silent Gear installed).
Biomes: seven, each with tinted grass and leaves, its own sky, fog, water, particles and rock, its own
island shape (blended by climate: domes, mesas, spires, rifts, ice shelves...) and its own features,
including points hanging from the island undersides.
"""
import json
import os
import tomllib

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "mods", "emp_engines", "src", "main", "resources", "data", "emp_engines")
NS = "emp_engines"

# All the numbers you'd tune live in etheric.toml next to this script (annotated there).
with open(os.path.join(os.path.dirname(__file__), "etheric.toml"), "rb") as _f:
    SETTINGS = tomllib.load(_f)
_I, _S = SETTINGS["islands"], SETTINGS["shards"]
TERRAIN = {
    "layout": _I.get("layout", "noise"),
    "cells": {k: _I[k] for k in ("spacing", "jitter", "min_diameter", "max_diameter", "size_curve", "min_gap", "edge_roughness", "warp") if k in _I},
    "island_octave": _I["octave"], "island_layers": _I["layers"], "threshold": _I["threshold"],
    "split_large": _I.get("split_large", 0.0),
    "base_y": _I["base_y"], "height_spread": _I["height_spread"], "soil_depth": _I["soil_depth"],
    "hill_height": _I["hill_height"], "underside_depth": _I["underside_depth"], "underside_jag": _I["underside_jag"],
    "edge_crack": _I["edge_crack"], "shard_band": _S["band"], "shard_scale": _S["scale"],
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
def noise(name, xz=1.0, y=0.0): return {"type": "minecraft:noise", "noise": f"{NS}:etheric/{name}", "xz_scale": xz, "y_scale": y}
def cache2d(a): return {"type": "minecraft:cache_2d", "argument": a}
def ref(name): return f"{NS}:etheric/{name}"
def neg(a): return mul(-1.0, a)


Y = {"type": "minecraft:y_clamped_gradient", "from_y": 0, "to_y": 320, "from_value": 0.0, "to_value": 320.0}


def terrain():
    t = TERRAIN
    layers = ["islands"] + [f"islands_{c}" for c in "bcdefg"[:t["island_layers"] - 1]]
    for name in layers:   # same shape, different seeds (each noise id gets its own)
        write(f"worldgen/noise/etheric/{name}.json", {"firstOctave": t["island_octave"], "amplitudes": [1.0, 0.5, 0.25]})
    write("worldgen/noise/etheric/height.json", {"firstOctave": t["island_octave"] - 1, "amplitudes": [1.0]})
    write("worldgen/noise/etheric/hills.json", {"firstOctave": -6, "amplitudes": [1.0, 0.5]})
    write("worldgen/noise/etheric/jag.json", {"firstOctave": -4, "amplitudes": [1.0, 0.5]})
    write("worldgen/noise/etheric/crack.json", {"firstOctave": -5, "amplitudes": [1.0]})
    write("worldgen/noise/etheric/shards.json", {"firstOctave": -4, "amplitudes": [1.0, 0.5]})
    write("worldgen/noise/etheric/temperature.json", {"firstOctave": SETTINGS["biomes"]["temperature_octave"], "amplitudes": [1.5, 0.0, 1.0, 0.0, 0.0, 0.0]})
    write("worldgen/noise/etheric/humidity.json", {"firstOctave": SETTINGS["biomes"]["humidity_octave"], "amplitudes": [1.0, 1.0, 0.0, 0.0, 0.0, 0.0]})
    write("worldgen/noise/etheric/patches.json", {"firstOctave": -5, "amplitudes": [1.0, 1.0]})
    write("worldgen/noise/etheric/ground.json", {"firstOctave": -4, "amplitudes": [1.0, 0.7, 0.4]})
    write("worldgen/noise/etheric/spires.json", {"firstOctave": -4, "amplitudes": [1.0, 0.6]})
    write("worldgen/noise/etheric/rift.json", {"firstOctave": -7, "amplitudes": [1.0, 0.4]})

    T = t["threshold"]
    # Island mask (2D): above the threshold is island.
    if t["layout"] == "cells":
        c = t["cells"]
        write("worldgen/noise/etheric/coast.json", {"firstOctave": -6, "amplitudes": [1.0, 0.5]})
        mask = add(float(t["threshold"]), {
            "type": f"{NS}:island_cells", "spacing": float(c["spacing"]), "jitter": float(c["jitter"]),
            "min_diameter": float(c["min_diameter"]), "max_diameter": float(c["max_diameter"]), "size_curve": float(c["size_curve"]),
            "min_gap": float(c["min_gap"]), "edge_amplitude": float(c["edge_roughness"]), "warp": float(c.get("warp", 0)),
            "edge": noise("coast")})
        write("worldgen/density_function/etheric/mask.json", cache2d(mask))
        layers = []
    else:
        mask = noise(layers[0])
    for name in layers[1:]:
        mask = dmax(mask, noise(name))
    # Split islands that would merge: where a second layer is also nearly an island, push the mask down.
    if t["layout"] != "cells" and t["split_large"] > 0 and len(layers) > 1:
        second = None   # the second highest layer = the highest of the pairwise minimums
        for i in range(len(layers)):
            for j in range(i + 1, len(layers)):
                pair = dmin(noise(layers[i]), noise(layers[j]))
                second = pair if second is None else dmax(second, pair)
        mask = add(mask, neg(dmax(0.0, add(second, -(t["threshold"] - t["split_large"])))))
    if t["layout"] != "cells":
        write("worldgen/density_function/etheric/mask.json", cache2d(mask))
    # Each island's base height (2D), kept in a band below the arrival pad.
    spread = t["height_spread"] / 0.25
    write("worldgen/density_function/etheric/base.json",
          cache2d(add(float(t["base_y"]), mul(spread, clamp(noise("height"), -0.25, 0.25)))))
    # How deep inside an island a column is: 0 at the edge, 1 well inside.
    write("worldgen/density_function/etheric/heart.json", cache2d(clamp(mul(3.0, add(ref("mask"), -T)), 0.0, 1.0)))

    shape_by_biome()
    heart = ref("heart")
    top = add(ref("base"), float(t["soil_depth"]))
    top = add(top, mul(ref("shape_hills"), noise("hills")))
    core = clamp(mul(2.0, add(ref("mask"), -T)), 0.0, 1.0)                                  # 0 at the edge, 1 at the centre
    top = add(top, mul(ref("shape_dome"), mul(core, add(2.0, neg(core)))))                    # eased: rounded crown
    top = add(top, mul(ref("shape_plateau"), clamp(mul(4.0, heart), 0.0, 1.0)))             # cliffs near the edge
    top = add(top, mul(ref("shape_spires"), mul(clamp(mul(3.0, heart), 0.0, 1.0),
                                                clamp(mul(4.0, add(noise("spires"), -0.25)), 0.0, 1.0))))
    top = add(top, neg(mul(ref("shape_rift"), clamp(mul(1 / 0.07, add(0.07, neg(dabs(noise("rift"))))), 0.0, 1.0))))
    top = dmin(top, float(SHAPE_CFG.get("cap_y", 196)))
    depth = add(mul(heart, add(mul(ref("shape_underside"), float(t["underside_depth"])),
                               mul(mul(ref("shape_jag"), float(t["underside_jag"])), noise("jag", 1.0, 0.25)))), 4.0)
    above_bottom = mul(0.125, add(Y, neg(add(ref("base"), neg(depth)))))
    below_top = mul(0.125, add(top, neg(Y)))
    inside = mul(60.0, add(add(ref("mask"), -T), mul(t["edge_crack"], noise("crack", 1.0, 1.0))))
    island = dmin(dmin(below_top, above_bottom), inside)

    # Shards: small blobs floating in a ring just outside each island, around its height.
    k = 1.0 / t["shard_scale"]
    blob = mul(10.0, add(noise("shards", k, k), -0.35))
    ring = dmin(mul(25.0, add(add(ref("mask"), -T), mul(float(t["shard_band"]), ref("shape_shards")))),
                mul(25.0, add(T, neg(ref("mask")))))
    band = mul(0.15, add(22.0 * t["shard_scale"] ** 0.5, neg(dabs(add(Y, neg(add(ref("base"), -6.0)))))))
    shards = dmin(dmin(blob, ring), band)

    write("worldgen/density_function/etheric/density.json", dmax(island, shards))


SHAPE_CFG = SETTINGS.get("shape", {})
SHAPE_KEYS = {"hills": None, "dome": 0.0, "plateau": 0.0, "spires": 0.0, "rift": 0.0, "underside": 1.0, "jag": 1.0, "shards": 1.0}


def shape_by_biome():
    """Each biome's island shape settings, blended by the climate values that pick the biomes (see CLIMATE),
    written as density functions etheric/shape_<setting>."""
    def climate(name):
        return {"type": "minecraft:shifted_noise", "noise": f"{NS}:etheric/{name}", "shift_x": "minecraft:shift_x",
                "shift_y": 0.0, "shift_z": "minecraft:shift_z", "xz_scale": 1.0, "y_scale": 0.0}
    write("worldgen/density_function/etheric/temperature.json", cache2d(climate("temperature")))
    write("worldgen/density_function/etheric/humidity.json", cache2d(climate("humidity")))
    w = float(SHAPE_CFG.get("blend", 0.06))
    def up(x, edge):     # 0 below the edge, 1 above, a soft ramp 2w wide
        return clamp(add(mul(1 / (2 * w), add(x, -edge)), 0.5), 0.0, 1.0)
    def down(x, edge): return add(1.0, neg(up(x, edge)))
    T, H = ref("temperature"), ref("humidity")
    # One weight per biome: 1 inside its climate box, 0 outside, ramps at the shared edges (they add up to 1).
    for key, (tr, hr) in CLIMATE.items():
        parts = []
        if tr[0] > -1.0: parts.append(up(T, tr[0]))
        if tr[1] < 1.0: parts.append(down(T, tr[1]))
        if hr[0] > -1.0: parts.append(up(H, hr[0]))
        if hr[1] < 1.0: parts.append(down(H, hr[1]))
        weight = parts[0]
        for extra in parts[1:]:
            weight = mul(weight, extra)
        write(f"worldgen/density_function/etheric/weight_{key}.json", cache2d(weight))
    for setting, default in SHAPE_KEYS.items():
        if default is None:
            default = float(TERRAIN["hill_height"])
        total = None
        for key in CLIMATE:
            v = float(SHAPE_CFG.get(key, {}).get(setting, default))
            if v == 0.0:
                continue
            term = mul(v, ref(f"weight_{key}"))
            total = term if total is None else add(total, term)
        write(f"worldgen/density_function/etheric/shape_{setting}.json", cache2d(total if total is not None else 0.0))


# --- biomes -------------------------------------------------------------------------------

def rgb(h): return int(h.lstrip("#"), 16)


def block(name): return {"type": "minecraft:block", "result_state": {"Name": name}}


def cond(c, then): return {"type": "minecraft:condition", "if_true": c, "then_run": then}


def seq(*rules): return {"type": "minecraft:sequence", "sequence": list(rules)}


def depth_rule(surface, offset, add_depth, secondary=0):
    return {"type": "minecraft:stone_depth", "offset": offset, "surface_type": surface,
            "add_surface_depth": add_depth, "secondary_depth_range": secondary}


def patch(lo, hi=4.0): return {"type": "minecraft:noise_threshold", "noise": f"{NS}:etheric/patches", "min_threshold": lo, "max_threshold": hi}
def ground(lo, hi=4.0): return {"type": "minecraft:noise_threshold", "noise": f"{NS}:etheric/ground", "min_threshold": lo, "max_threshold": hi}


MONSTERS = [
    {"type": "minecraft:zombie", "weight": 95, "minCount": 2, "maxCount": 4},
    {"type": "minecraft:skeleton", "weight": 100, "minCount": 2, "maxCount": 4},
    {"type": "minecraft:spider", "weight": 90, "minCount": 2, "maxCount": 4},
    {"type": "minecraft:creeper", "weight": 90, "minCount": 2, "maxCount": 4},
    {"type": "minecraft:enderman", "weight": 15, "minCount": 1, "maxCount": 3},
]
MONSTER_SETS = {
    "desert": [   # husks instead of most zombies, like a vanilla desert
        {"type": "minecraft:husk", "weight": 80, "minCount": 2, "maxCount": 4},
        {"type": "minecraft:zombie", "weight": 15, "minCount": 2, "maxCount": 4},
        *MONSTERS[1:],
    ],
}

# Ores, set for floating islands: vanilla's are spread over the whole world height and only replace
# stone, so on islands (thin, high up, partly coloured rock) hardly any land. These sit in the island
# band and replace the islands' rock too (block tag emp_engines:etheric_ore_replaceables).
# name: (ore block, deepslate ore or None, vein size, veins a chunk, discard on air)
ORE_ROCK = ["#minecraft:stone_ore_replaceables", "minecraft:calcite", "minecraft:tuff", "minecraft:packed_ice",
            "minecraft:light_blue_terracotta", "minecraft:pink_terracotta", "minecraft:purple_terracotta",
            "minecraft:magenta_terracotta", "minecraft:yellow_terracotta", "minecraft:white_terracotta",
            "minecraft:sandstone", "minecraft:smooth_sandstone", "minecraft:orange_terracotta"]
ETHERIC_ORES = SETTINGS["ores"]["list"]
ORE_BAND = (SETTINGS["ores"]["band_min"], SETTINGS["ores"]["band_max"])
ORES = [f"{NS}:etheric/ore_{k}" for k, o in ETHERIC_ORES.items() if "biomes" not in o]   # ores for every biome

BIO = SETTINGS["biomes"]
E = f"{NS}:etheric/"   # our own features


def lighten(h, f, towards="#F2EEF8"):
    """Mixes a colour f of the way towards a soft white (lighter, gentler)."""
    a, b = int(h.lstrip("#"), 16), int(towards.lstrip("#"), 16)
    out = 0
    for sh in (16, 8, 0):
        ca, cb = (a >> sh) & 255, (b >> sh) & 255
        out |= round(ca + (cb - ca) * f) << sh
    return "#%06X" % out


BIOMES = {
    "amethyst_expanse": {
        "name": "Amethyst Expanse", "temp": 0.6, "rain": False,
        "colors": {"grass": "#9A6BD6", "foliage": "#B58CE8", "sky": "#8E7CC3", "fog": "#C9B5F0", "water": "#6B4FD1", "water_fog": "#2E1F66"},
        "particle": ("minecraft:end_rod", 0.0008),
        "under": "minecraft:calcite", "rock": "minecraft:calcite", "rock2": "minecraft:amethyst_block",
        "top_patches": [(0.45, 0.7, "minecraft:calcite"), (0.85, 4.0, "minecraft:amethyst_block")],
        "belly": "minecraft:purple_terracotta", "belly2": "minecraft:tuff",
        "vegetal": ["minecraft:trees_plains", "minecraft:patch_grass_plain", "minecraft:flower_meadow"],
        "extra": {"local_modifications": [E + "amethyst_geode", E + "open_geode"], "surface_structures": [E + "crystal_spikes"]},
        "creatures": [{"type": "minecraft:sheep", "weight": 8, "minCount": 2, "maxCount": 4}],
    },
    "azure_wilds": {
        "name": "Azure Wilds", "temp": 0.7, "rain": False,
        "colors": {"grass": "#3FA7C4", "foliage": "#2E6FD1", "sky": "#6FA8DC", "fog": "#A8D0F0", "water": "#2A7FD4", "water_fog": "#0E2A52"},
        "particle": ("minecraft:spore_blossom_air", 0.004),
        "top": "minecraft:moss_block", "under": ["minecraft:light_blue_terracotta", "minecraft:clay"],
        "rock": "minecraft:light_blue_terracotta", "rock2": "minecraft:moss_block",
        "top_patches": [(-4.0, -0.35, "minecraft:grass_block"), (0.6, 4.0, "minecraft:rooted_dirt")],
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
        "under": "minecraft:deepslate", "rock": "minecraft:tuff", "rock2": "minecraft:deepslate",
        "top_patches": [(-0.05, 0.45, "minecraft:tuff"), (0.45, 0.8, "minecraft:blackstone"), (0.8, 4.0, "minecraft:moss_block")],
        "belly": "minecraft:deepslate", "belly2": "minecraft:crying_obsidian",
        "vegetal": ["minecraft:trees_savanna", "minecraft:patch_dead_bush_2", "minecraft:patch_grass_badlands"],
        "extra": {"local_modifications": [E + "open_geode"], "surface_structures": [E + "crystal_spikes"]},
        "creatures": [],
    },
    "pale_remnant": {
        "name": "Pale Remnant", "temp": -0.5, "rain": True,
        "colors": {"grass": "#9FC3D6", "foliage": "#B8D4E3", "sky": "#A9C7E8", "fog": "#DCE8F5", "water": "#3D57D6", "water_fog": "#050533"},
        "particle": ("minecraft:snowflake", 0.004),
        "under": ["minecraft:snow_block", "minecraft:packed_ice"], "rock": "minecraft:packed_ice", "rock2": "minecraft:calcite",
        "top_patches": [(0.55, 4.0, "minecraft:packed_ice")],
        "belly": "minecraft:packed_ice", "belly2": "minecraft:blue_ice",
        "vegetal": ["minecraft:ice_spike", "minecraft:trees_grove", "minecraft:patch_grass_taiga"],
        "extra": {},
        "creatures": [{"type": "minecraft:fox", "weight": 4, "minCount": 1, "maxCount": 2}],
    },
    "cherry_rift": {
        "name": "Cherry Rift", "temp": 0.6, "rain": False,
        "colors": {"grass": "#B6DB61", "foliage": "#B6DB61", "sky": "#7BA4FF", "fog": "#C0D8FF", "water": "#5DB7EF", "water_fog": "#5DB7EF"},
        "particle": ("minecraft:cherry_leaves", 0.003),
        "under": "minecraft:pink_terracotta", "rock": "minecraft:pink_terracotta", "rock2": "minecraft:calcite",
        "top_patches": [(0.7, 4.0, "minecraft:mud")],
        "belly": "minecraft:pink_terracotta", "belly2": "minecraft:magenta_terracotta",
        "vegetal": ["minecraft:trees_cherry", E + "giant_cherry", "minecraft:flower_cherry", "minecraft:patch_grass_plain"],
        "extra": {},
        "creatures": [{"type": "minecraft:rabbit", "weight": 4, "minCount": 2, "maxCount": 3}],
    },
    "gilded_grove": {
        "name": "Gilded Grove", "temp": 0.8, "rain": False,
        "colors": {"grass": "#D6C46A", "foliage": "#F2C232", "sky": "#E9D7A6", "fog": "#F6E6BC", "water": "#5AA9C9", "water_fog": "#173447"},
        "particle": ("minecraft:wax_on", 0.0015),
        "under": ["minecraft:smooth_sandstone", "minecraft:yellow_terracotta"], "rock": "minecraft:yellow_terracotta", "rock2": "minecraft:calcite",
        "top_patches": [(0.45, 0.62, "minecraft:sand"), (0.62, 0.72, "minecraft:gravel"), (0.8, 4.0, "minecraft:gold_ore")],
        "belly": "minecraft:yellow_terracotta", "belly2": "minecraft:calcite",
        "vegetal": [E + "gold_trees", "minecraft:patch_sunflower", "minecraft:patch_grass_plain", "minecraft:flower_meadow"],
        "extra": {},
        "creatures": [{"type": "minecraft:rabbit", "weight": 4, "minCount": 2, "maxCount": 3}],
    },
    "pale_forest": {
        "name": "Pale Forest", "temp": 0.4, "rain": False,
        "colors": {"grass": "#D9E1DC", "foliage": "#EEF2F3", "sky": "#D4DDE6", "fog": "#EEF2F5", "water": "#9DB6C8", "water_fog": "#25343F"},
        "particle": ("minecraft:white_ash", 0.003),
        "under": ["minecraft:calcite", "minecraft:diorite"], "rock": "minecraft:calcite", "rock2": "minecraft:diorite",
        "top_patches": [(0.5, 0.75, "minecraft:calcite"), (0.75, 4.0, "minecraft:white_terracotta")],
        "belly": "minecraft:white_terracotta", "belly2": "minecraft:calcite",
        "vegetal": [E + "pale_trees", E + "white_flowers", "minecraft:patch_grass_forest"],
        "extra": {},
        "creatures": [{"type": "minecraft:fox", "weight": 2, "minCount": 1, "maxCount": 2}],
    },
    # Hot and dry / hot and wet, at the warm end next to Cherry Rift and Azure Wilds
    "sky_desert": {
        "name": "Sky Desert", "temp": 2.0, "downfall": 0.0, "rain": False,
        "colors": {"sky": "#6EB1FF", "fog": "#E8D9B0", "water": "#32A598", "water_fog": "#050533"},
        "particle": ("minecraft:white_ash", 0.0),
        "top": "minecraft:sand", "under": ["minecraft:sandstone", "minecraft:smooth_sandstone"],
        "rock": "minecraft:sandstone", "rock2": "minecraft:smooth_sandstone",
        "top_patches": [(0.78, 4.0, "minecraft:red_sand")],
        "belly": "minecraft:sandstone", "belly2": "minecraft:orange_terracotta",
        "vegetal": ["minecraft:patch_dead_bush_2", "minecraft:patch_cactus_desert"],
        "extra": {},
        "creatures": [{"type": "minecraft:rabbit", "weight": 4, "minCount": 2, "maxCount": 3},
                      {"type": "minecraft:camel", "weight": 1, "minCount": 1, "maxCount": 1}],
        "monsters": "desert",
    },
    "sky_jungle": {
        "name": "Sky Jungle", "temp": 0.95, "downfall": 0.9, "rain": True,
        "colors": {"sky": "#77A8FF", "fog": "#C0D8FF", "water": "#3F76E4", "water_fog": "#050533"},
        "particle": ("minecraft:white_ash", 0.0),
        "under": "minecraft:dirt", "rock": "minecraft:stone", "rock2": "minecraft:mossy_cobblestone",
        "top_patches": [(0.7, 4.0, "minecraft:podzol")],
        "belly": "minecraft:stone", "belly2": "minecraft:moss_block",
        "vegetal": ["minecraft:bamboo_light", "minecraft:trees_jungle", "minecraft:flower_warm", "minecraft:patch_grass_jungle",
                    "minecraft:patch_melon"],
        "extra": {},
        "creatures": [{"type": "minecraft:parrot", "weight": 40, "minCount": 1, "maxCount": 2},
                      {"type": "minecraft:chicken", "weight": 10, "minCount": 4, "maxCount": 4},
                      {"type": "minecraft:ocelot", "weight": 2, "minCount": 1, "maxCount": 3},
                      {"type": "minecraft:panda", "weight": 1, "minCount": 1, "maxCount": 2}],
    },
    # Vanilla-looking biomes, so not every island is strange
    "sky_meadow": {
        "name": "Sky Meadow", "temp": 0.8, "downfall": 0.4, "rain": True,
        "colors": {"sky": "#78A7FF", "fog": "#C0D8FF", "water": "#3F76E4", "water_fog": "#050533"},
        "particle": ("minecraft:white_ash", 0.0),
        "under": "minecraft:dirt", "rock": "minecraft:stone", "rock2": "minecraft:andesite",
        "belly": "minecraft:stone", "belly2": "minecraft:andesite",
        "vegetal": ["minecraft:trees_plains", "minecraft:flower_plains", "minecraft:patch_grass_plain"],
        "extra": {},
        "creatures": [{"type": "minecraft:sheep", "weight": 12, "minCount": 2, "maxCount": 4}, {"type": "minecraft:cow", "weight": 8, "minCount": 2, "maxCount": 4},
                      {"type": "minecraft:pig", "weight": 10, "minCount": 2, "maxCount": 4}, {"type": "minecraft:chicken", "weight": 10, "minCount": 2, "maxCount": 4},
                      {"type": "minecraft:horse", "weight": 3, "minCount": 2, "maxCount": 4}],
    },
    "sky_oakwood": {
        "name": "Sky Oakwood", "temp": 0.7, "downfall": 0.8, "rain": True,
        "colors": {"sky": "#79A6FF", "fog": "#C0D8FF", "water": "#3F76E4", "water_fog": "#050533"},
        "particle": ("minecraft:white_ash", 0.0),
        "under": "minecraft:dirt", "rock": "minecraft:stone", "rock2": "minecraft:andesite",
        "belly": "minecraft:stone", "belly2": "minecraft:andesite",
        "top_patches": [(0.75, 4.0, "minecraft:podzol")],
        "vegetal": ["minecraft:trees_birch_and_oak", "minecraft:flower_default", "minecraft:patch_grass_forest"],
        "extra": {},
        "creatures": [{"type": "minecraft:sheep", "weight": 8, "minCount": 2, "maxCount": 4}, {"type": "minecraft:pig", "weight": 8, "minCount": 2, "maxCount": 4},
                      {"type": "minecraft:wolf", "weight": 4, "minCount": 2, "maxCount": 4}, {"type": "minecraft:chicken", "weight": 8, "minCount": 2, "maxCount": 4}],
    },
}

# Gentler colours for the purple and blue biomes (etheric.toml: lighten_purple, lighten_blue)
for _key, _f in (("amethyst_expanse", BIO["lighten_purple"]), ("twilight_shards", BIO["lighten_purple"]), ("azure_wilds", BIO["lighten_blue"])):
    BIOMES[_key]["colors"] = {k: (v if k == "water_fog" else lighten(v, _f)) for k, v in BIOMES[_key]["colors"].items()}

# Which biome goes where: temperature x humidity, all other climate values flat.
CLIMATE = {   # (temperature range, humidity range); the outer edges stretch to wherever the noise goes
    "pale_remnant": ([-1.0, -0.45], [-1.0, 1.0]),
    "twilight_shards": ([-0.45, -0.1], [-1.0, -0.33]),
    "sky_oakwood": ([-0.45, -0.1], [-0.33, 0.33]),
    "pale_forest": ([-0.45, -0.1], [0.33, 1.0]),
    "gilded_grove": ([-0.1, 0.25], [-1.0, -0.33]),
    "sky_meadow": ([-0.1, 0.25], [-0.33, 0.33]),
    "amethyst_expanse": ([-0.1, 0.25], [0.33, 1.0]),
    # the warm end, driest to wettest (humidity rarely leaves -0.4..0.4, so these four get about 6-7% each)
    "sky_desert": ([0.25, 1.0], [-1.0, -0.16]),
    "cherry_rift": ([0.25, 1.0], [-0.16, 0.0]),
    "azure_wilds": ([0.25, 1.0], [0.0, 0.16]),
    "sky_jungle": ([0.25, 1.0], [0.16, 1.0]),
}

STEPS = ["raw_generation", "lakes", "local_modifications", "underground_structures", "surface_structures", "strongholds",
         "underground_ores", "underground_decoration", "fluid_springs", "vegetal_decoration", "top_layer_modification"]


def biome(key, b):
    feats = {s: [] for s in STEPS}
    feats["underground_ores"] += ORES + [f"{NS}:etheric/ore_{k}" for k, o in ETHERIC_ORES.items() if key in o.get("biomes", [])]
    feats["underground_decoration"].append("minecraft:glow_lichen")
    feats["vegetal_decoration"] += b["vegetal"]
    feats["top_layer_modification"].append("minecraft:freeze_top_layer")
    for step, extra in b["extra"].items():
        feats[step] += extra
    c = b["colors"]
    effects = {
        **({"grass_color": rgb(c["grass"])} if c.get("grass") else {}),
        **({"foliage_color": rgb(c["foliage"])} if c.get("foliage") else {}),
        "sky_color": rgb(c["sky"]), "fog_color": rgb(c["fog"]),
        "water_color": rgb(c["water"]), "water_fog_color": rgb(c["water_fog"]),
        "particle": {"options": {"type": b["particle"][0]}, "probability": b["particle"][1]},
        "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0},
        "additions_sound": {"sound": "minecraft:ambient.basalt_deltas.additions", "tick_chance": 0.0011},
    }
    return {
        "has_precipitation": b["rain"], "temperature": b["temp"], "downfall": b.get("downfall", 0.4),
        "effects": effects,
        "spawners": {"monster": MONSTER_SETS.get(b.get("monsters"), MONSTERS), "creature": b["creatures"], "ambient": [], "axolotls": [],
                     "underground_water_creature": [], "water_creature": [], "water_ambient": [], "misc": []},
        "spawn_costs": {},
        "carvers": {},
        "features": [feats[s] for s in STEPS],
    }


def surface_for(key, b):
    snowy = key == "pale_remnant"
    top = block(b.get("top", "minecraft:snow_block" if snowy else "minecraft:grass_block"))
    # patches of other blocks showing through the top (ground noise ranges), then the main top block
    top = seq(*[cond(ground(lo, hi), block(name)) for lo, hi, name in b.get("top_patches", [])], top)
    if snowy:   # powder snow traps
        top = seq(cond(patch(float(BIO["powder_snow"])), block("minecraft:powder_snow")), top)
    under = b["under"] if isinstance(b["under"], list) else [b["under"]]
    under_rule = block(under[0]) if len(under) == 1 else seq(cond(ground(0.15), block(under[1])), block(under[0]))
    return cond({"type": "minecraft:biome", "biome_is": [f"{NS}:{key}"]}, seq(
        # the top (grass, snow...) with the biome's patches, then the biome's own subsoil, then a coloured rock skin
        cond(depth_rule("floor", 0, False), top),
        cond(depth_rule("floor", 0, True), under_rule),
        cond(depth_rule("floor", 3, True, 4), seq(cond(patch(0.0), block(b["rock2"])), block(b["rock"]))),
        # the underside, seen from below: coloured bands
        cond(depth_rule("ceiling", 0, True, 3), seq(cond(patch(0.25), block(b["belly2"])), block(b["belly"]))),
        # cliffs and the body: patches of the biome's rock, the rest stays stone (so ores can generate)
        cond(patch(0.35), block(b["rock"])),
    ))


def ore(name, state, deep, size, count, discard=0.0, conditions=None, band=None):
    """An ore in the islands' rock (deepslate gets the deepslate ore), placed through the island band."""
    targets = [{"target": {"predicate_type": "minecraft:tag_match", "tag": f"{NS}:etheric_ore_replaceables"}, "state": {"Name": state}}]
    if deep:
        targets.append({"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:deepslate"}, "state": {"Name": deep}})
    cf = {"type": "minecraft:ore", "config": {"size": size, "discard_chance_on_air_exposure": discard, "targets": targets}}
    pf = {"feature": f"{NS}:etheric/{name}", "placement": [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid",
                                                      "min_inclusive": {"absolute": (band or ORE_BAND)[0]}, "max_inclusive": {"absolute": (band or ORE_BAND)[1]}}},
        {"type": "minecraft:biome"},
    ]}
    if conditions:
        cf = {"neoforge:conditions": conditions, **cf}
        pf = {"neoforge:conditions": conditions, **pf}
    write(f"worldgen/configured_feature/etheric/{name}.json", cf)
    write(f"worldgen/placed_feature/etheric/{name}.json", pf)


def ores():
    write("tags/block/etheric_ore_replaceables.json", {"replace": False, "values": ORE_ROCK})
    for name, o in ETHERIC_ORES.items():
        ore(f"ore_{name}", o["block"], o.get("deepslate"), o["vein_size"], o["veins_per_chunk"],
            band=(o.get("min_y", ORE_BAND[0]), o.get("max_y", ORE_BAND[1])))


def state(name, **props):
    return {"Name": name, **({"Properties": {k: str(v) for k, v in props.items()}} if props else {})}


def simple(name, **props): return {"type": "minecraft:simple_state_provider", "state": state(name, **props)}


LEAVES = dict(distance=7, persistent="false", waterlogged="false")
SAPLING_OK = {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": state("minecraft:oak_sapling", stage=0)}}


def tree(trunk_log, trunk_placer, leaves, foliage_placer, size, decorators=None):
    return {"type": "minecraft:tree", "config": {
        "trunk_provider": simple(trunk_log, axis="y"), "trunk_placer": trunk_placer,
        "foliage_provider": simple(leaves, **LEAVES), "foliage_placer": foliage_placer,
        "minimum_size": size, "dirt_provider": simple("minecraft:dirt"),
        "decorators": decorators or [], "ignore_vines": True, "force_dirt": False}}


def placed_trees(name, feature, count, extra=None):
    write(f"worldgen/placed_feature/etheric/{name}.json", {"feature": feature, "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"}] + (extra or []) + [SAPLING_OK, {"type": "minecraft:biome"}]})


def biome_features():
    two_layers = {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1}
    # Cherry Rift: fewer ordinary cherry trees, and now and then a giant one near the middle of the biome
    placed_trees("trees_cherry_sparse", "minecraft:cherry_bees_005", BIO["cherry_trees_per_chunk"])
    write("worldgen/configured_feature/etheric/giant_cherry.json", tree(
        "minecraft:cherry_log",
        {"type": "minecraft:cherry_trunk_placer", "base_height": 14, "height_rand_a": 5, "height_rand_b": 0,
         "branch_count": {"type": "minecraft:weighted_list", "distribution": [{"data": 2, "weight": 1}, {"data": 3, "weight": 2}]},
         "branch_horizontal_length": {"type": "minecraft:uniform", "min_inclusive": 6, "max_inclusive": 9},
         "branch_start_offset_from_top": {"min_inclusive": -9, "max_inclusive": -5},
         "branch_end_offset_from_top": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 2}},
        "minecraft:cherry_leaves",
        {"type": "minecraft:cherry_foliage_placer", "radius": 7, "offset": 0, "height": 7,
         "wide_bottom_layer_hole_chance": 0.25, "corner_hole_chance": 0.25, "hanging_leaves_chance": 0.2,
         "hanging_leaves_extension_chance": 0.4},
        {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 2}))
    write("worldgen/placed_feature/etheric/giant_cherry.json", {"feature": E + "giant_cherry", "placement": [
        {"type": "minecraft:rarity_filter", "chance": BIO["giant_cherry_chance"]}, {"type": "minecraft:in_square"},
        {"type": f"{NS}:biome_heart", "radius": 40},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": state("minecraft:cherry_sapling", stage=0)}},
        {"type": "minecraft:biome"}]})

    # Amethyst Expanse and Twilight Shards: giant obsidian-and-amethyst shards, and geodes broken open at the surface
    lo, hi = BIO["crystal_spike_height"]
    write("worldgen/configured_feature/etheric/crystal_spike.json", {"type": f"{NS}:crystal_spike", "config": {
        "body": {"type": "minecraft:weighted_state_provider", "entries": [
            {"weight": 6, "data": state("minecraft:obsidian")}, {"weight": 1, "data": state("minecraft:crying_obsidian")},
            {"weight": 3, "data": state("minecraft:amethyst_block")}]},
        "core": simple("minecraft:amethyst_block"),
        "tip": simple("minecraft:amethyst_cluster", facing="up", waterlogged="false"),
        "min_height": lo, "max_height": hi, "giant_chance": 8}})
    write("worldgen/placed_feature/etheric/crystal_spikes.json", {"feature": E + "crystal_spike", "placement": [
        {"type": "minecraft:rarity_filter", "chance": BIO["crystal_spike_chance"]}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})
    write("worldgen/configured_feature/etheric/open_geode.json", {"type": f"{NS}:open_geode", "config": {
        "geode": "minecraft:amethyst_geode", "depth": 2, "radius": 9}})
    write("worldgen/placed_feature/etheric/open_geode.json", {"feature": E + "open_geode", "placement": [
        {"type": "minecraft:rarity_filter", "chance": BIO["open_geode_chance"]}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})

    # Gilded Grove: white-barked trees with golden leaves (oak leaves take the biome's gold tint), some with beehives
    bees = [{"type": "minecraft:beehive", "probability": float(BIO["beehive_chance"])}]
    write("worldgen/configured_feature/etheric/gold_tree.json", tree(
        "minecraft:birch_log", {"type": "minecraft:straight_trunk_placer", "base_height": 6, "height_rand_a": 3, "height_rand_b": 0},
        "minecraft:oak_leaves", {"type": "minecraft:blob_foliage_placer", "radius": 2, "offset": 0, "height": 3}, two_layers, bees))
    write("worldgen/configured_feature/etheric/gold_tree_tall.json", tree(
        "minecraft:birch_log", {"type": "minecraft:fancy_trunk_placer", "base_height": 9, "height_rand_a": 5, "height_rand_b": 0},
        "minecraft:oak_leaves", {"type": "minecraft:fancy_foliage_placer", "radius": 2, "offset": 4, "height": 4},
        {"type": "minecraft:two_layers_feature_size", "limit": 0, "lower_size": 0, "upper_size": 0, "min_clipped_height": 4}, bees))
    write("worldgen/configured_feature/etheric/gold_trees.json", {"type": "minecraft:random_selector", "config": {
        "features": [{"feature": {"feature": E + "gold_tree_tall", "placement": []}, "chance": 0.35}],
        "default": {"feature": E + "gold_tree", "placement": []}}})
    placed_trees("gold_trees", E + "gold_trees", BIO["gold_trees_per_chunk"])

    # Pale Forest: white trees, thick dark-oak shaped ones and tall thin ones, white flowers
    write("worldgen/configured_feature/etheric/pale_tree_thick.json", tree(
        "minecraft:birch_log", {"type": "minecraft:dark_oak_trunk_placer", "base_height": 6, "height_rand_a": 2, "height_rand_b": 1},
        "minecraft:oak_leaves", {"type": "minecraft:dark_oak_foliage_placer", "radius": 0, "offset": 0},
        {"type": "minecraft:three_layers_feature_size", "limit": 1, "upper_limit": 1, "lower_size": 0, "middle_size": 1, "upper_size": 2}))
    write("worldgen/configured_feature/etheric/pale_tree_tall.json", tree(
        "minecraft:birch_log", {"type": "minecraft:straight_trunk_placer", "base_height": 8, "height_rand_a": 4, "height_rand_b": 0},
        "minecraft:oak_leaves", {"type": "minecraft:blob_foliage_placer", "radius": 2, "offset": 0, "height": 3}, two_layers))
    write("worldgen/configured_feature/etheric/pale_trees.json", {"type": "minecraft:random_selector", "config": {
        "features": [{"feature": {"feature": E + "pale_tree_thick", "placement": []}, "chance": 0.45}],
        "default": {"feature": E + "pale_tree_tall", "placement": []}}})
    placed_trees("pale_trees", E + "pale_trees", BIO["pale_trees_per_chunk"])
    write("worldgen/configured_feature/etheric/white_flowers.json", {"type": "minecraft:flower", "config": {
        "tries": 64, "xz_spread": 6, "y_spread": 2, "feature": {"feature": {"type": "minecraft:simple_block", "config": {
            "to_place": {"type": "minecraft:weighted_state_provider", "entries": [
                {"weight": 2, "data": state("minecraft:lily_of_the_valley")}, {"weight": 2, "data": state("minecraft:oxeye_daisy")},
                {"weight": 1, "data": state("minecraft:azure_bluet")}, {"weight": 1, "data": state("minecraft:white_tulip")}]}}},
            "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}}]}}})
    write("worldgen/placed_feature/etheric/white_flowers.json", {"feature": E + "white_flowers", "placement": [
        {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}]})


def scatter(name, block_state, tries, xz, y, placement, extra_predicates=()):
    """A scatter of one block (random_patch), placed where there's air (and anything else asked)."""
    preds = [{"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}, *extra_predicates]
    write(f"worldgen/configured_feature/etheric/{name}.json", {"type": "minecraft:random_patch", "config": {
        "tries": tries, "xz_spread": xz, "y_spread": y, "feature": {
            "feature": {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:simple_state_provider", "state": block_state}}},
            "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": preds}}]}}})
    write(f"worldgen/placed_feature/etheric/{name}.json", {"feature": E + name, "placement": placement})


def often(per_chunk):
    """Placement for 'this many a chunk': a count, or a 1-in-N chance below 1."""
    if per_chunk >= 1:
        return [{"type": "minecraft:count", "count": int(round(per_chunk))}]
    return [{"type": "minecraft:rarity_filter", "chance": max(1, int(round(1 / per_chunk)))}]


SURFACE = [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]

# Hanging points under the islands: (feature name, body, core, tip)
def _w(*pairs): return {"type": "minecraft:weighted_state_provider", "entries": [{"weight": w, "data": d} for w, d in pairs]}
CRYSTAL = (_w((6, state("minecraft:obsidian")), (1, state("minecraft:crying_obsidian")), (3, state("minecraft:amethyst_block"))),
           simple("minecraft:amethyst_block"), simple("minecraft:amethyst_cluster", facing="down", waterlogged="false"))
HANGING = {
    "amethyst_expanse": ("hanging_crystal", *CRYSTAL),
    "twilight_shards": ("hanging_crystal", *CRYSTAL),
    "pale_remnant": ("icicles", _w((5, state("minecraft:packed_ice")), (1, state("minecraft:blue_ice"))),
                     simple("minecraft:blue_ice"), simple("minecraft:packed_ice")),
    "azure_wilds": ("hanging_roots", _w((3, state("minecraft:rooted_dirt")), (2, state("minecraft:moss_block")), (1, state("minecraft:dirt"))),
                    simple("minecraft:rooted_dirt"), simple("minecraft:hanging_roots", waterlogged="false")),
    "cherry_rift": ("hanging_pink", _w((3, state("minecraft:pink_terracotta")), (1, state("minecraft:calcite"))),
                    simple("minecraft:magenta_terracotta"), simple("minecraft:pink_terracotta")),
    "gilded_grove": ("hanging_gold", _w((4, state("minecraft:yellow_terracotta")), (2, state("minecraft:calcite"))),
                     _w((1, state("minecraft:gold_ore")), (3, state("minecraft:yellow_terracotta"))), simple("minecraft:yellow_terracotta")),
    "sky_desert": ("hanging_sandstone", _w((4, state("minecraft:sandstone")), (2, state("minecraft:smooth_sandstone")), (1, state("minecraft:orange_terracotta"))),
                   simple("minecraft:orange_terracotta"), simple("minecraft:sandstone")),
    "sky_jungle": ("hanging_jungle", _w((3, state("minecraft:moss_block")), (2, state("minecraft:mossy_cobblestone")), (2, state("minecraft:dirt"))),
                   simple("minecraft:mossy_cobblestone"), simple("minecraft:moss_block")),
    "pale_forest": ("hanging_bone", _w((2, state("minecraft:white_terracotta")), (2, state("minecraft:calcite")), (1, state("minecraft:bone_block", axis="y"))),
                    simple("minecraft:bone_block", axis="y"), simple("minecraft:calcite")),
}


def biome_extras():
    """The features that make each biome its own (settings in etheric.toml: [biomes] and [hanging_spikes])."""
    hs = SETTINGS.get("hanging_spikes", {})
    done = set()
    for key, (name, body, core, tip) in HANGING.items():
        cfg = hs.get(key, {"per_chunk": 0.5, "height": [6, 18]})
        if name not in done:
            write(f"worldgen/configured_feature/etheric/{name}.json", {"type": f"{NS}:crystal_spike", "config": {
                "body": body, "core": core, "tip": tip, "min_height": cfg["height"][0], "max_height": cfg["height"][1],
                "giant_chance": 10, "hanging": True}})
            write(f"worldgen/placed_feature/etheric/{name}.json", {"feature": E + name, "placement": often(cfg["per_chunk"]) + SURFACE})
            done.add(name)
        BIOMES[key]["extra"].setdefault("underground_decoration", []).append(E + name)

    # Amethyst Expanse: glowing amethyst clusters on the ground
    scatter("amethyst_clusters", state("minecraft:amethyst_cluster", facing="up", waterlogged="false"), 20, 5, 1,
          [{"type": "minecraft:count", "count": int(BIO["amethyst_clusters_per_chunk"])}, {"type": "minecraft:in_square"},
           {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"}, {"type": "minecraft:biome"}])
    BIOMES["amethyst_expanse"]["vegetal"].append(E + "amethyst_clusters")

    # Twilight Shards: clusters of basalt columns
    write("worldgen/placed_feature/etheric/basalt_columns.json", {"feature": "minecraft:small_basalt_columns", "placement":
          [{"type": "minecraft:rarity_filter", "chance": int(BIO["basalt_chance"])}] + SURFACE})
    BIOMES["twilight_shards"]["extra"].setdefault("surface_structures", []).append(E + "basalt_columns")

    # Azure Wilds: moss and glow-berry vines on the undersides (found by searching up from below an island)
    def under(count):
        return [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 60}, "max_inclusive": {"absolute": 200}}},
                {"type": "minecraft:environment_scan", "direction_of_search": "up", "max_steps": 16,
                 "target_condition": {"type": "minecraft:solid"},
                 "allowed_search_condition": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}},
                {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": -1}, {"type": "minecraft:biome"}]
    write("worldgen/placed_feature/etheric/underside_moss.json", {"feature": "minecraft:moss_patch_ceiling", "placement": under(30)})
    write("worldgen/placed_feature/etheric/underside_vines.json", {"feature": "minecraft:cave_vine", "placement": under(60)})
    veg = BIOMES["azure_wilds"]["vegetal"]
    veg[:] = [f for f in veg if f != "minecraft:lush_caves_ceiling_vegetation"] + [E + "underside_moss", E + "underside_vines"]
    BIOMES["sky_jungle"]["vegetal"].append(E + "underside_moss")

    # Azure Wilds and Cherry Rift: waterfalls pouring off cliffs into the void
    rock = ["minecraft:stone", "minecraft:dirt", "minecraft:grass_block", "minecraft:calcite", "minecraft:moss_block",
            "minecraft:light_blue_terracotta", "minecraft:pink_terracotta", "minecraft:magenta_terracotta", "minecraft:rooted_dirt"]
    write("worldgen/configured_feature/etheric/waterfall.json", {"type": "minecraft:spring_feature", "config": {
        "state": {"Name": "minecraft:water", "Properties": {"falling": "true"}},
        "requires_block_below": True, "rock_count": 4, "hole_count": 1, "valid_blocks": rock}})
    write("worldgen/placed_feature/etheric/waterfall.json", {"feature": E + "waterfall", "placement": [
        {"type": "minecraft:count", "count": int(BIO["waterfall_tries"])}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 95}, "max_inclusive": {"absolute": 195}}},
        {"type": "minecraft:biome"}]})
    for key in ("azure_wilds", "cherry_rift", "sky_jungle"):
        BIOMES[key]["extra"].setdefault("fluid_springs", []).append(E + "waterfall")

    # Sky Desert: desert wells now and then
    write("worldgen/placed_feature/etheric/desert_well.json", {"feature": "minecraft:desert_well", "placement":
          [{"type": "minecraft:rarity_filter", "chance": int(BIO.get("desert_well_chance", 40))}] + SURFACE})
    BIOMES["sky_desert"]["extra"].setdefault("surface_structures", []).append(E + "desert_well")

    # Cherry Rift: small ponds with lily pads
    write("worldgen/configured_feature/etheric/pond.json", {"type": "minecraft:lake", "config": {
        "fluid": simple("minecraft:water", level=0), "barrier": simple("minecraft:pink_terracotta")}})
    write("worldgen/placed_feature/etheric/pond.json", {"feature": E + "pond", "placement":
          [{"type": "minecraft:rarity_filter", "chance": int(BIO["pond_chance"])}] + SURFACE})
    BIOMES["cherry_rift"]["extra"].setdefault("lakes", []).append(E + "pond")
    BIOMES["cherry_rift"]["vegetal"].append("minecraft:patch_waterlily")

    # Gilded Grove: small knobbly outcrops of gold ore and yellow terracotta
    write("worldgen/configured_feature/etheric/gold_outcrop.json", {"type": f"{NS}:crystal_spike", "config": {
        "body": _w((3, state("minecraft:yellow_terracotta")), (2, state("minecraft:gold_ore")), (1, state("minecraft:raw_gold_block"))),
        "core": simple("minecraft:gold_ore"), "tip": simple("minecraft:gold_ore"),
        "min_height": 3, "max_height": 6, "giant_chance": 0}})
    write("worldgen/placed_feature/etheric/gold_outcrop.json", {"feature": E + "gold_outcrop", "placement":
          [{"type": "minecraft:rarity_filter", "chance": int(BIO.get("gold_outcrop_chance", 3))}] + SURFACE})
    BIOMES["gilded_grove"]["extra"].setdefault("surface_structures", []).append(E + "gold_outcrop")

    # Pale Forest: giant bones sticking out of the ground, cobwebs
    write("worldgen/configured_feature/etheric/bone_ribs.json", {"type": f"{NS}:crystal_spike", "config": {
        "body": simple("minecraft:bone_block", axis="y"), "core": simple("minecraft:bone_block", axis="y"),
        "tip": simple("minecraft:bone_block", axis="y"), "min_height": 5, "max_height": 12, "giant_chance": 0}})
    write("worldgen/placed_feature/etheric/bone_ribs.json", {"feature": E + "bone_ribs", "placement":
          [{"type": "minecraft:rarity_filter", "chance": int(BIO["bone_ribs_chance"])}] + SURFACE})
    BIOMES["pale_forest"]["extra"].setdefault("surface_structures", []).append(E + "bone_ribs")
    scatter("cobwebs", state("minecraft:cobweb"), 8, 6, 3,
          [{"type": "minecraft:rarity_filter", "chance": int(BIO["cobweb_chance"])}, {"type": "minecraft:in_square"},
           {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}],
          [{"type": "minecraft:solid", "offset": [0, -1, 0]}])
    BIOMES["pale_forest"]["vegetal"].append(E + "cobwebs")


def check_feature_order(built):
    """Minecraft needs one order of features that every biome in a dimension agrees with; if two biomes
    list the same two features the other way round, chunks crash with "Feature order cycle found"."""
    edges, who = {}, {}
    for key, data in built.items():
        flat = [(i, f) for i, step in enumerate(data["features"]) for f in step]
        for a, b in zip(flat, flat[1:]):
            edges.setdefault(a, set()).add(b)
            who.setdefault((a, b), key)
    state, path = {}, []
    def visit(n):
        state[n] = 1; path.append(n)
        for m in edges.get(n, ()):
            if state.get(m) == 1:
                loop = path[path.index(m):] + [m]
                biomes_in = sorted({who[(x, y)] for x, y in zip(loop, loop[1:])})
                raise SystemExit("Feature order cycle: " + " -> ".join(f for _, f in loop)
                                 + "\n  Biomes involved: " + ", ".join(biomes_in)
                                 + "\n  List these features in the same order in every biome.")
            if m not in state: visit(m)
        state[n] = 2; path.pop()
    for n in list(edges):
        if n not in state: visit(n)


def biomes():
    ores()
    biome_features()
    biome_extras()
    built = {key: biome(key, b) for key, b in BIOMES.items()}
    check_feature_order(built)
    for key, data in built.items():
        write(f"worldgen/biome/{key}.json", data)
    # Geodes at island height (vanilla's only generate deep underground).
    write("worldgen/placed_feature/etheric/amethyst_geode.json", {
        "feature": "minecraft:amethyst_geode",
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": 10},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                          "min_inclusive": {"absolute": 100}, "max_inclusive": {"absolute": 180}}},
            {"type": "minecraft:biome"},
        ],
    })


# --- structures: vanilla buildings on islands, biomes mismatched on purpose -----------------

# name: (vanilla structure, min island radius around the chunk middle, max unevenness, spawn overrides)
WITCH_SPAWNS = {"monster": {"bounding_box": "piece", "spawns": [{"type": "minecraft:witch", "weight": 1, "minCount": 1, "maxCount": 1}]},
                "creature": {"bounding_box": "piece", "spawns": [{"type": "minecraft:cat", "weight": 1, "minCount": 1, "maxCount": 1}]}}
OUTPOST_SPAWNS = {"monster": {"bounding_box": "full", "spawns": [{"type": "minecraft:pillager", "weight": 1, "minCount": 1, "maxCount": 1}]}}
SPAWNS = {"witch_hut": WITCH_SPAWNS, "pillager_outpost": OUTPOST_SPAWNS}
# name: (vanilla structure, min island radius around the chunk middle, max unevenness, spawn overrides)
ISLAND_STRUCTURES = {k: (v["structure"], v["min_radius"], v["max_slope"], SPAWNS.get(k, {})) for k, v in SETTINGS["structures"].items()}
# set: (structures with weights, spacing and separation in chunks, salt)
ISLAND_SETS = {k: (v["weights"], v["spacing"], v["separation"], v["salt"]) for k, v in SETTINGS["structure_sets"].items()}


def structures():
    write("tags/worldgen/biome/etheric.json", {"replace": False, "values": [f"{NS}:{k}" for k in BIOMES]})
    for name, (inner, radius, slope, spawns) in ISLAND_STRUCTURES.items():
        write(f"worldgen/structure/etheric/{name}.json", {
            "type": f"{NS}:on_island",
            "biomes": f"#{NS}:etheric",
            "step": "surface_structures",
            "terrain_adaptation": "none",
            "spawn_overrides": spawns,
            "structure": inner,
            "min_radius": radius,
            "max_slope": slope,
        })
    for name, (members, spacing, separation, salt) in ISLAND_SETS.items():
        write(f"worldgen/structure_set/etheric/{name}.json", {
            "structures": [{"structure": f"{NS}:etheric/{k}", "weight": w} for k, w in members.items()],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": separation, "salt": salt},
        })


# --- Silent Gear's endgame ores, for testing ------------------------------------------------
# Its own features put them in end stone and netherrack; these put them in the islands' rock.
# Only loaded with Silent Gear installed. Remove when Silent Gear's ores get a proper home.
SILENT = SETTINGS["silent_gear_test_ores"]


def silent_gear_test_ores():
    when = [{"type": "neoforge:mod_loaded", "modid": "silentgear"}]
    placed = []
    if not SILENT.get("enabled", True):
        return
    for name, o in SILENT["list"].items():
        ore(f"test_silentgear_{name}", f"silentgear:{name}", o.get("deepslate"), o["vein_size"], o["veins_per_chunk"], conditions=when)
        placed.append(f"{NS}:etheric/test_silentgear_{name}")
    write("neoforge/biome_modifier/test_silentgear_ores.json", {
        "neoforge:conditions": when,
        "type": "neoforge:add_features",
        "biomes": f"#{NS}:etheric",
        "features": placed,
        "step": "underground_ores",
    })


def noise_settings():
    def shifted(name):
        return {"type": "minecraft:shifted_noise", "noise": f"{NS}:etheric/{name}", "shift_x": "minecraft:shift_x",
                "shift_y": 0.0, "shift_z": "minecraft:shift_z", "xz_scale": 1.0, "y_scale": 0.0}
    router = {
        "barrier": 0.0, "fluid_level_floodedness": 0.0, "fluid_level_spread": 0.0, "lava": 0.0,
        "temperature": shifted("temperature"), "vegetation": shifted("humidity"),
        "continents": 0.0, "erosion": 0.0, "depth": 0.0, "ridges": 0.0,
        "initial_density_without_jaggedness": 0.0,
        "final_density": {"type": "minecraft:interpolated", "argument": ref("density")},
        "vein_toggle": 0.0, "vein_ridged": 0.0, "vein_gap": 0.0,
    }
    write("worldgen/noise_settings/etheric_skies.json", {
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
    write("dimension/etheric_skies.json", {
        "type": f"{NS}:etheric_skies",
        "generator": {"type": "minecraft:noise", "settings": f"{NS}:etheric_skies",
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
    structures()
    silent_gear_test_ores()
    noise_settings()
    dimension()
    lang()
    print("Etheric Skies worldgen written to", os.path.abspath(ROOT))
