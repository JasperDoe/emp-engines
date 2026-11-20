"""Writes the Ponder schematic for the Tiered Foundry scenes (building a foundry, the air intake).

Run: python tools/art/ponder_foundry.py   (needs: pip install nbtlib)
- mods/tieredfoundry/src/main/resources/assets/tieredfoundry/ponder/foundry.nbt  (structure template, 7 x 5 x 7)
The scenes' texts go into the lang file from tools/foundry/gen.py, which reads FoundryScenes.java.
Layout: see the comment at the top of FoundryScenes.java.
"""
import os

import nbtlib
from nbtlib.tag import Compound, Int, List, String

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "mods", "tieredfoundry", "src", "main")
OUT = os.path.join(ROOT, "resources", "assets", "tieredfoundry", "ponder", "foundry.nbt")

palette, blocks = [], []


def state(name, **props):
    key = (name, tuple(sorted(props.items())))
    for i, p in enumerate(palette):
        if p[0] == key:
            return i
    palette.append((key, name, props))
    return len(palette) - 1


def put(x, y, z, name, **props):
    blocks.append((x, y, z, state(name, **props)))


T = dict(tier="1", lit="false")
for x in range(7):
    for z in range(7):
        put(x, 0, z, "minecraft:polished_andesite" if (x + z) % 2 == 0 else "minecraft:andesite")
for x in range(1, 6):
    for z in range(1, 6):
        inside = 2 <= x <= 4 and 2 <= z <= 4
        corner = x in (1, 5) and z in (1, 5)
        if corner:   # brick walls up every corner, joined to the bricks beside them (tall below another wall)
            for y in (1, 2, 3):
                h = 'tall' if y < 3 else 'low'
                put(x, y, z, 'minecraft:brick_wall', up='true', waterlogged='false',
                    east=h if x == 1 else 'none', west=h if x == 5 else 'none',
                    south=h if z == 1 else 'none', north=h if z == 5 else 'none')
            continue
        if inside:
            put(x, 1, z, "tieredfoundry:solid_fuel_heater", **T)
        elif (x, z) == (5, 3):
            put(x, 1, z, "tieredfoundry:air_duct", **T)
        else:
            put(x, 1, z, "minecraft:bricks")
        for y in (2, 3):
            if inside:
                continue
            if (x, y, z) == (3, 2, 1):
                put(x, y, z, "tieredfoundry:foundry_controller", facing="north", **T)
            elif (x, y, z) == (3, 2, 5):
                put(x, y, z, "tieredfoundry:foundry_drain", **T)
            else:
                put(x, y, z, "minecraft:bricks")
put(6, 1, 3, "create:encased_fan", facing="west")
put(3, 2, 6, "tieredfoundry:foundry_tap", facing="south", tier="1")
put(3, 1, 6, "tieredfoundry:casting_table")

root = Compound({
    "DataVersion": Int(3955),
    "size": List[Int]([Int(7), Int(5), Int(7)]),
    "palette": List[Compound]([
        Compound({"Name": String(name), **({"Properties": Compound({k: String(v) for k, v in props.items()})} if props else {})})
        for _, name, props in palette]),
    "blocks": List[Compound]([
        Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(s)}) for x, y, z, s in blocks]),
    "entities": List[Compound]([]),
})
os.makedirs(os.path.dirname(OUT), exist_ok=True)
nbtlib.File(root).save(OUT, gzipped=True)
print("schematic written:", len(blocks), "blocks")
