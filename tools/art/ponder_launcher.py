"""Writes the Ponder schematic for the launcher scene and its lang lines.

Run: python tools/art/ponder_launcher.py   (needs: pip install nbtlib)
- assets/createskybound/ponder/launcher.nbt  (structure template, 7 x 6 x 7)
- the createskybound.ponder.launcher.* lines in lang/en_us.json, read from LauncherScenes.java
"""
import json
import os
import re

import nbtlib
from nbtlib.tag import Compound, Int, List, String

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "mods", "createskybound", "src", "main")
ASSETS = os.path.join(ROOT, "resources", "assets", "createskybound")

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


for x in range(7):
    for z in range(7):
        put(x, 0, z, "minecraft:snow_block" if (x + z) % 2 == 0 else "minecraft:white_concrete")
for x in range(1, 6):
    for z in range(1, 6):
        corner = x in (1, 5) and z in (1, 5)
        edge = x in (1, 5) or z in (1, 5)
        if corner:
            put(x, 1, z, "createskybound:resonance_pylon_t1")
        elif edge:
            put(x, 1, z, "create:brass_casing")
        else:
            put(x, 1, z, "minecraft:quartz_block")
put(6, 1, 3, "create:shaft", axis="x", waterlogged="false")
put(3, 2, 5, "createskybound:vector_planar_computer", facing="south")
put(2, 2, 5, "minecraft:lever", face="floor", facing="south", powered="false")
# a little ship: spruce deck, a mast and a sail
for x in range(2, 5):
    for z in range(2, 5):
        put(x, 3, z, "minecraft:spruce_planks")
put(3, 4, 3, "minecraft:spruce_fence", east="false", north="false", south="false", west="false", waterlogged="false")
put(3, 5, 3, "minecraft:white_wool")

root = Compound({
    "DataVersion": Int(3955),
    "size": List[Int]([Int(7), Int(6), Int(7)]),
    "palette": List[Compound]([
        Compound({"Name": String(name), **({"Properties": Compound({k: String(v) for k, v in props.items()})} if props else {})})
        for _, name, props in palette]),
    "blocks": List[Compound]([
        Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(s)}) for x, y, z, s in blocks]),
    "entities": List[Compound]([]),
})
os.makedirs(os.path.join(ASSETS, "ponder"), exist_ok=True)
nbtlib.File(root).save(os.path.join(ASSETS, "ponder", "launcher.nbt"), gzipped=True)

# Lang: Ponder looks texts up as <modid>.ponder.<scene>.text_<n>, numbered in the order they appear.
src = open(os.path.join(ROOT, "java", "dev", "skybound", "createskybound", "client", "ponder", "LauncherScenes.java"), encoding="utf-8").read()
title = re.search(r'title\("launcher", "([^"]+)"\)', src).group(1)
texts = re.findall(r'\.text\("([^"]+)"\)', src)
lang_path = os.path.join(ASSETS, "lang", "en_us.json")
lang = json.load(open(lang_path, encoding="utf-8"))
lang = {k: v for k, v in lang.items() if not k.startswith("createskybound.ponder.launcher.")}
lang["createskybound.ponder.launcher.header"] = title
for i, t in enumerate(texts, 1):
    lang["createskybound.ponder.launcher.text_" + str(i)] = t
json.dump(lang, open(lang_path, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
open(lang_path, "a").write("\n")
print("schematic and", len(texts), "ponder lines written")
