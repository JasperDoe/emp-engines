"""Draws the Obsidian Keyhole: vanilla obsidian with a small keyhole overlaid on the four sides.

Run: python tools/art/obsidian_keyhole.py
The block uses minecraft:block/obsidian for every face (so it matches placed obsidian exactly) and
these 16x16 overlays, which are transparent except for the keyhole. Writes, under
mods/emp_engines/src/main/resources/assets/emp_engines/:
- textures/block/obsidian_keyhole.png, obsidian_keyhole_unlocked.png
- models/block/obsidian_keyhole.json, obsidian_keyhole_unlocked.json
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "mods", "emp_engines", "src", "main",
                    "resources", "assets", "emp_engines")

# A 5 x 8 keyhole, centred: round head, narrow neck, flared foot.
#  r = rim, k = hole
SHAPE = [
    ".rrr.",
    "rkkkr",
    "rkkkr",
    "rkkkr",
    ".rkr.",
    ".rkr.",
    "rkkkr",
    ".rrr.",
]
X0, Y0 = 6, 4

LOCKED = {
    "r": [(156, 104, 214), (128, 82, 190), (98, 60, 152)],
    "k": [(2, 1, 4)] * 8,
}
UNLOCKED = {
    "r": [(122, 66, 196), (150, 86, 224), (108, 58, 176)],
    "k": [(244, 214, 255), (226, 170, 255), (208, 140, 255), (188, 112, 245), (176, 98, 238),
          (160, 82, 228), (144, 68, 214), (130, 58, 200)],
}


def overlay(pal):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(SHAPE):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch == "k":
                c = pal["k"][y]
            else:
                # lit from the top left: bright on the upper rim, dark on the lower right
                c = pal["r"][0 if y < 3 and x < 3 else 2 if (y > 4 or x > 2) else 1]
            im.putpixel((X0 + x, Y0 + y), c + (255,))
    return im


def model(tex):
    full = {"uv": [0, 0, 16, 16]}
    base = {d: dict(full, texture="#obsidian", cullface=d) for d in ("down", "up", "north", "south", "east", "west")}
    over = {d: dict(full, texture="#keyhole", cullface=d) for d in ("north", "south", "east", "west")}
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": "minecraft:block/obsidian",
            "obsidian": "minecraft:block/obsidian",
            "keyhole": "emp_engines:block/" + tex,
        },
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": base},
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": over},
        ],
    }


if __name__ == "__main__":
    for name, pal in (("obsidian_keyhole", LOCKED), ("obsidian_keyhole_unlocked", UNLOCKED)):
        overlay(pal).save(os.path.join(ROOT, "textures", "block", name + ".png"))
        with open(os.path.join(ROOT, "models", "block", name + ".json"), "w", encoding="utf-8") as f:
            json.dump(model(name), f, indent=1)
            f.write("\n")
    print("wrote obsidian keyhole overlays and models")
