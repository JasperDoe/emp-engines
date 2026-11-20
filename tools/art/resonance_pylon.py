"""Writes the Resonance Pylon base model in Create's style, from Create's own textures.

Run: python tools/art/resonance_pylon.py
Writes, under mods/emp_engines/src/main/resources/assets/emp_engines/models/:
- block/resonance_pylon_base.json (the placed block; tiers t1-t3 use it as parent)
- item/resonance_pylon_t{1,2,3}.json (the base plus a still shard and ring, coloured by tier)
A brass-casing plinth, an andesite collar, four brass posts with casing caps and a brass socket.
The floating shard and ring are drawn by PylonRenderer above it (shard middle at GEM_Y).
"""
import json
import os

MODELS = os.path.join(os.path.dirname(__file__), "..", "..", "mods", "emp_engines", "src", "main",
                      "resources", "assets", "emp_engines", "models")
OUT = os.path.join(MODELS, "block", "resonance_pylon_base.json")
GEMS = {1: "minecraft:block/shroomlight", 2: "minecraft:block/diamond_block", 3: "minecraft:block/amethyst_block"}


def f(tex, uv, cull=None):
    d = {"texture": tex, "uv": uv}
    if cull:
        d["cullface"] = cull
    return d


def box(name, frm, to, side, up, down, cull_down=False):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    return {"name": name, "from": frm, "to": to, "faces": {
        "north": side(x0, x1, y0, y1), "south": side(x0, x1, y0, y1),
        "east": side(z0, z1, y0, y1), "west": side(z0, z1, y0, y1),
        "up": up, "down": dict(down, cullface="down") if cull_down else down}}


def casing_band(tex):
    # the casing's lower edge (bright rim over the dark outline), squeezed to the band's height
    return lambda a, b, y0, y1: f(tex, [a, 16 - (y1 - y0) * 1.5, b, 16])


def plain(tex):
    return lambda a, b, y0, y1: f(tex, [a, 16 - y1, b, 16 - y0])


def model():
    el = []
    el.append(box("plinth", [1, 0, 1], [15, 2, 15], casing_band("#brass_casing"),
                  f("#brass_casing", [1, 1, 15, 15]), f("#brass_casing", [1, 1, 15, 15]), cull_down=True))
    el.append(box("collar", [3, 2, 3], [13, 4, 13], casing_band("#andesite_casing"),
                  f("#andesite_casing", [3, 3, 13, 13]), f("#andesite_casing", [3, 3, 13, 13])))
    for (x, z) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        el.append(box("post", [x, 2, z], [x + 2, 9, z + 2], plain("#brass"),
                      f("#brass", [x, z, x + 2, z + 2]), f("#brass", [x, z, x + 2, z + 2])))
        el.append(box("cap", [x - 0.5, 9, z - 0.5], [x + 2.5, 10, z + 2.5],
                      lambda a, b, y0, y1: f("#brass_casing", [1, 1, 4, 2]),
                      f("#brass_casing", [1, 1, 4, 4]), f("#brass_casing", [1, 1, 4, 4])))
    el.append(box("socket", [5.5, 4, 5.5], [10.5, 6, 10.5], plain("#brass"),
                  f("#brass_casing", [5.5, 5.5, 10.5, 10.5]), f("#brass", [5.5, 5.5, 10.5, 10.5])))
    el.append(box("socket_lip", [6.5, 6, 6.5], [9.5, 6.5, 9.5], plain("#andesite_casing"),
                  f("#andesite_casing", [0, 0, 3, 3]), f("#andesite_casing", [0, 0, 3, 3])))
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "create:block/brass_casing",
            "brass_casing": "create:block/brass_casing",
            "andesite_casing": "create:block/andesite_casing",
            "brass": "create:block/brass_block",
        },
        "elements": el,
    }


def item(tier):
    """In the inventory the block entity doesn't draw, so the shard and ring are modelled, still."""
    m = model()
    m["textures"]["gem"] = GEMS[tier]
    m["parent"] = "minecraft:block/block"
    spin = {"angle": 45, "axis": "y", "origin": [8, 8, 8]}
    gem = {d: {"texture": "#gem", "uv": [0, 0, 4, 8]} for d in ("north", "south", "east", "west")}
    m["elements"] += [
        {"name": "shard", "from": [6, 7, 6], "to": [10, 15, 10], "rotation": spin,
         "faces": dict(gem, up={"texture": "#gem", "uv": [0, 0, 4, 4]}, down={"texture": "#gem", "uv": [0, 0, 4, 4]})},
        {"name": "shard_tip", "from": [7, 15, 7], "to": [9, 16.5, 9], "rotation": spin,
         "faces": {d: {"texture": "#gem", "uv": [0, 0, 2, 2]} for d in ("north", "south", "east", "west", "up")}},
        {"name": "ring", "from": [3, 10.5, 3], "to": [13, 11.5, 13],
         "rotation": {"angle": 22.5, "axis": "x", "origin": [8, 11, 8]},
         "faces": {"north": {"texture": "#brass", "uv": [3, 0, 13, 1]}, "south": {"texture": "#brass", "uv": [3, 0, 13, 1]},
                   "east": {"texture": "#brass", "uv": [3, 0, 13, 1]}, "west": {"texture": "#brass", "uv": [3, 0, 13, 1]}}},
    ]
    return m


if __name__ == "__main__":
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(model(), fh, indent=1)
        fh.write("\n")
    for t in GEMS:
        with open(os.path.join(MODELS, "item", f"resonance_pylon_t{t}.json"), "w", encoding="utf-8") as fh:
            json.dump(item(t), fh, indent=1)
            fh.write("\n")
    print("wrote resonance pylon base model")
