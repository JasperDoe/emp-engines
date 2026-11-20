"""Placeholder dungeons for the Etheric Skies, and the Navigational Data that leads to them.

Run: python tools/worldgen/build_dungeons.py   (needs: pip install nbtlib)

Each dungeon is, for now, a 16 x 16 cobblestone-slab platform floating in the sky with a sign saying
its name and a chest holding its Ancient Eye Mold fragment. Replace a placeholder by saving your build
with a structure block over data/emp_engines/structure/dungeon/<name>.nbt (same name): the pools,
structures, placement and loot written here keep working.

Writes into mods/emp_engines/src/main/resources/data/emp_engines/:
  structure/dungeon/<name>.nbt                    the placeholder build
  worldgen/template_pool/dungeon/<name>.json      one-piece jigsaw pool
  worldgen/structure/dungeon/<name>.json          the structure (Navigational Data points at this id)
  worldgen/structure_set/dungeon/<name>.json      how often it appears
  loot_table/chests/dungeon/<name>.json           the chest: its fragment plus some loot
  loot_table/chests/navigational_data.json        a chance of one drive, for any dungeon
  loot_modifiers/navigational_data.json           puts that table into ordinary structure chests
"""
import json
import os

import nbtlib
from nbtlib.tag import Byte, Compound, Int, List, String

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "mods", "emp_engines", "src", "main", "resources", "data", "emp_engines")
NS = "emp_engines"

# name: (display name, fragment item, sky height, spacing and separation in chunks, salt)
DUNGEONS = {
    "astral_sanctum": ("Astral Sanctum", "astral_eye_mold_fragment", 165, 56, 20, 71230411),
    "umbral_crypt": ("Umbral Crypt", "umbral_eye_mold_fragment", 120, 56, 20, 71230412),
    "gilded_reliquary": ("Gilded Reliquary", "gilded_eye_mold_fragment", 145, 56, 20, 71230413),
}

# Ordinary chests that can hold a drive (chance in navigational_data.json)
NAV_DATA_CHESTS = [
    "minecraft:chests/simple_dungeon", "minecraft:chests/abandoned_mineshaft", "minecraft:chests/desert_pyramid",
    "minecraft:chests/jungle_temple", "minecraft:chests/shipwreck_treasure", "minecraft:chests/pillager_outpost",
    "minecraft:chests/woodland_mansion", "minecraft:chests/buried_treasure", "minecraft:chests/stronghold_corridor",
    "minecraft:chests/ancient_city", "minecraft:chests/bastion_treasure", "minecraft:chests/nether_bridge",
]
NAV_DATA_CHANCE = 0.25


def write(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def text(s):
    return String(json.dumps({"text": s}))


def placeholder(name, title):
    """A 16 x 16 cobblestone-slab floor, a sign with the dungeon's name and a chest with its loot."""
    palette, index, blocks = [], {}, []

    def state(block, **props):
        key = (block, tuple(sorted(props.items())))
        if key not in index:
            entry = Compound({"Name": String(block)})
            if props:
                entry["Properties"] = Compound({k: String(v) for k, v in props.items()})
            index[key] = len(palette)
            palette.append(entry)
        return index[key]

    def put(x, y, z, block, nbt=None, **props):
        b = Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(state(block, **props))})
        if nbt is not None:
            b["nbt"] = nbt
        blocks.append(b)

    for x in range(16):
        for z in range(16):
            if (x, z) in ((7, 7), (8, 7)):
                put(x, 0, z, "minecraft:chiseled_stone_bricks")   # full block, so the sign stays up
            else:
                put(x, 0, z, "minecraft:cobblestone_slab", type="bottom", waterlogged="false")
    words = title.split(" ", 1) + [""]
    lines = [text(""), text(words[0]), text(words[1]), text("(placeholder)")]
    put(7, 1, 7, "minecraft:oak_sign", rotation="8", waterlogged="false", nbt=Compound({
        "id": String("minecraft:sign"),
        "front_text": Compound({"messages": List[String](lines), "color": String("black"), "has_glowing_text": Byte(1)}),
        "back_text": Compound({"messages": List[String](lines), "color": String("black"), "has_glowing_text": Byte(1)}),
        "is_waxed": Byte(1),
    }))
    put(8, 1, 7, "minecraft:chest", facing="south", type="single", waterlogged="false", nbt=Compound({
        "id": String("minecraft:chest"),
        "LootTable": String(f"{NS}:chests/dungeon/{name}"),
    }))
    data = Compound({
        "DataVersion": Int(3955),
        "size": List[Int]([Int(16), Int(2), Int(16)]),
        "palette": List[Compound](palette),
        "blocks": List[Compound](blocks),
        "entities": List[Compound]([]),
    })
    full = os.path.join(ROOT, "structure", "dungeon", f"{name}.nbt")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    nbtlib.File(data).save(full, gzipped=True)


def nav_data_entry(name, title):
    return {"type": "minecraft:item", "name": f"{NS}:navigational_data", "functions": [
        {"function": "minecraft:set_custom_data",
         "tag": json.dumps({"target": f"{NS}:dungeon/{name}", "target_name": title})}]}


def main():
    for name, (title, fragment, height, spacing, separation, salt) in DUNGEONS.items():
        placeholder(name, title)
        write(f"worldgen/template_pool/dungeon/{name}.json", {
            "fallback": "minecraft:empty",
            "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element",
                                                   "location": f"{NS}:dungeon/{name}", "projection": "rigid",
                                                   "processors": "minecraft:empty"}}]})
        write(f"worldgen/structure/dungeon/{name}.json", {
            "type": "minecraft:jigsaw", "biomes": f"#{NS}:etheric", "step": "surface_structures",
            "spawn_overrides": {}, "terrain_adaptation": "none",
            "start_pool": f"{NS}:dungeon/{name}", "size": 1,
            "start_height": {"absolute": height}, "max_distance_from_center": 80, "use_expansion_hack": False})
        write(f"worldgen/structure_set/dungeon/{name}.json", {
            "structures": [{"structure": f"{NS}:dungeon/{name}", "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": separation, "salt": salt}})
        write(f"loot_table/chests/dungeon/{name}.json", {"type": "minecraft:chest", "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{fragment}"}]},
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": [
                {"type": "minecraft:item", "name": "minecraft:diamond", "weight": 2,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
                {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 5,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 8}}]},
                {"type": "minecraft:item", "name": "minecraft:ender_pearl", "weight": 4,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 4}}]},
                {"type": "minecraft:item", "name": "minecraft:experience_bottle", "weight": 4,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}]},
                {"type": "minecraft:item", "name": "minecraft:amethyst_shard", "weight": 4,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 3, "max": 10}}]}]}]})

    # A drive for any of the dungeons, now and then, in ordinary structure chests
    write("loot_table/chests/navigational_data.json", {"type": "minecraft:chest", "pools": [{
        "rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": NAV_DATA_CHANCE}],
        "entries": [nav_data_entry(name, d[0]) for name, d in DUNGEONS.items()]}]})
    write("loot_modifiers/navigational_data.json", {
        "type": "neoforge:add_table",
        "conditions": [{"condition": "minecraft:any_of", "terms": [
            {"condition": "neoforge:loot_table_id", "loot_table_id": t} for t in NAV_DATA_CHESTS]}],
        "table": f"{NS}:chests/navigational_data"})
    glm = os.path.join(ROOT, "..", "neoforge", "loot_modifiers", "global_loot_modifiers.json")
    with open(glm) as f:
        entries = json.load(f)
    if f"{NS}:navigational_data" not in entries["entries"]:
        entries["entries"].append(f"{NS}:navigational_data")
        with open(glm, "w", newline="\n") as f:
            json.dump(entries, f, indent=2)
            f.write("\n")
    print("Dungeons written:", ", ".join(DUNGEONS))


if __name__ == "__main__":
    main()
