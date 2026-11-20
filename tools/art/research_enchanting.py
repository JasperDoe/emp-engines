"""Makes the Research Enchanting textures and item models.

Run: python tools/art/research_enchanting.py
Focus books are vanilla's enchanted book with the band in their gear group's colour. The model draws vanilla's own
book (minecraft:item/book, so it is never copied into the jar) with an overlay on top: the band, vanilla's gold
clasp, and from tier II a gold outline, faint at II and solid at V. Tier is the enchantment level the book holds.

The band's pixel positions and shading come from BAND below (traced off the vanilla enchanted book, colours
replaced by shade levels), so this script needs no game files.

Writes into mods/researchenchanting/src/main/resources/assets/researchenchanting/:
  textures/item/focus/<gear>_<tier>.png    overlays, 6 gears x tiers 0-5
  models/item/research_focus.json           overrides on researchenchanting:look = (gear * 6 + tier) / 64
  models/item/focus/<gear>_<tier>.json      layer0 minecraft:item/book, layer1 the overlay
  textures/item/research_notes.png          a loose page of glyph notes
  textures/mob_effect/rended.png            Rending's effect icon: a cracked chestplate (18x18)
  textures/misc/rune_circle.png             the circle that turns over the table (64x64, drawn by rule)
"""
import json
import os

from PIL import Image

HERE = os.path.dirname(__file__)
ASSETS = os.path.join(HERE, "..", "..", "mods", "researchenchanting", "src", "main", "resources",
                      "assets", "researchenchanting")

# Same order as Focus.GEARS in the mod. Each colour is the band's brightest shade.
GEARS = {
    "sword": (206, 36, 52),
    "bow": (232, 122, 30),
    "armour": (64, 178, 66),
    "tools": (170, 180, 196),
    "trident": (48, 182, 206),
    "crossbow": (140, 72, 210),
}
GOLD, GOLD_DARK = (211, 162, 22), (212, 150, 26)
OUTLINE = (240, 196, 60)
OUTLINE_ALPHA = {0: 0, 1: 0, 2: 40, 3: 90, 4: 150, 5: 255}

# The band over vanilla's book: digits are shade levels (9 brightest), G/g the gold clasp, a/b/c the cover's
# shading around the band. BOOK is the book's outline, for the gold edge.
BAND = [
    "................",
    "................",
    "..........a.....",
    "....55..........",
    "....889.bb......",
    "...ac799.bb..a..",
    "..a..c799.b.....",
    "....b.c798......",
    ".....b.c8GG.....",
    ".....a...Gg.....",
    ".........86.....",
    ".........66.....",
    ".........64.....",
    ".........4......",
    "................",
    "................",
]
BOOK = [
    "................",
    "........###.....",
    "......######....",
    "....#########...",
    "..############..",
    "###############.",
    "################",
    "###############.",
    "###############.",
    "################",
    ".###############",
    "..############..",
    "...#########....",
    "....######......",
    ".....###........",
    "................",
]
SHADE = {"9": 1.0, "8": 0.83, "7": 0.80, "6": 0.70, "5": 0.55, "4": 0.49}
FIXED = {"a": (101, 75, 23), "b": (68, 51, 16), "c": (84, 62, 19)}

def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c)


def overlay(gear_colour, tier, band_rows, book_mask):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(band_rows):
        for x, ch in enumerate(row):
            if ch in SHADE:
                px[x, y] = shade(gear_colour, SHADE[ch]) + (255,)
            elif ch == "G":
                px[x, y] = GOLD + (255,)
            elif ch == "g":
                px[x, y] = GOLD_DARK + (255,)
            elif ch in FIXED:
                px[x, y] = FIXED[ch] + (255,)
    alpha = OUTLINE_ALPHA[tier]
    if alpha:
        for y in range(16):
            for x in range(16):
                if (x, y) in book_mask:
                    continue
                if any((x + dx, y + dy) in book_mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    px[x, y] = OUTLINE + (alpha,)
    return im


# Loose page of glyph notes, and Rending's effect icon (unchanged pixel maps).
PAGE_MAP = [
    "................", "...KKKKKKKKK....", "...KPPPPPPPpK...", "...KPGGPGPPPpK..",
    "...KPPPPPPPPPK..", "...KPGPGGGPPPK..", "...KPPPPPPPPPK..", "...KPGGPGPGPPK..",
    "...KPPPPPPPPPK..", "...KPGPPGGPPPK..", "...KPPPPPPPPPK..", "...KPGGGPGPPPK..",
    "...KpPPPPPPPPK..", "....KppPPPPPpK..", ".....KKKKKKKK...", "................",
]
ICON_MAP = [
    "..................", "..................", "...KKK......KKK...", "..KAAAKKKKKKAAAK..",
    "..KAAAAAAAAAAAAK..", "..KAAAAAAxAAAAAK..", "...KAAAAAxAAAAK...", "....KAAAAxxAAK....",
    "....KAAAAAxAAK....", "....KAAAAxAAAK....", "....KAAAxxAAAK....", "....KAAAAxAAAK....",
    "....KaaaaxaaaK....", "....KaaaaaaaaK....", ".....KKKKKKKK.....", "..................",
    "..................", "..................",
]
MAP_COLOURS = {"K": (34, 22, 40, 255), "P": (236, 226, 196, 255), "p": (196, 180, 146, 255),
               "G": (88, 64, 120, 255), "A": (170, 170, 178, 255), "a": (120, 120, 130, 255), "x": (150, 40, 32, 255)}


def draw_map(rows):
    im = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                im.putpixel((x, y), MAP_COLOURS[ch])
    return im


def rune_circle():
    """Two rings with small marks between them, a six-point star inside, and a clear middle for the table."""
    import math
    n = 64
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    c = (n - 1) / 2

    def put(x, y, a):
        if 0 <= x < n and 0 <= y < n:
            old = px[x, y][3]
            px[x, y] = (255, 255, 255, max(old, a))

    for y in range(n):
        for x in range(n):
            r = math.hypot(x - c, y - c)
            if abs(r - 31.0) < 0.6 or abs(r - 23.5) < 0.6:
                put(x, y, 230)
            elif abs(r - 16.5) < 0.5:
                put(x, y, 150)
    # marks between the rings: 16 little three-stroke runes, each different
    shapes = [((0, -1), (0, 0), (0, 1)), ((-1, -1), (0, 0), (1, 1)), ((-1, 0), (0, 0), (1, 0)), ((-1, 1), (0, 0), (1, -1)),
              ((0, -1), (-1, 1), (1, 1)), ((-1, -1), (1, -1), (0, 1)), ((-1, 0), (1, -1), (1, 1)), ((1, 0), (-1, -1), (-1, 1))]
    for i in range(12):
        ang = math.tau * i / 12
        mx, my = c + math.cos(ang) * 27.3, c + math.sin(ang) * 27.3
        sh = shapes[(i * 5) % len(shapes)]
        for (ax, ay), (bx, by) in zip(sh, sh[1:]):
            for k in range(5):
                t = k / 4
                put(round(mx + (ax + (bx - ax) * t) * 2), round(my + (ay + (by - ay) * t) * 2), 255)
    # six-point star between the inner rings
    pts = [(c + math.cos(math.tau * k / 6 - math.pi / 2) * 23, c + math.sin(math.tau * k / 6 - math.pi / 2) * 23) for k in range(6)]
    for tri in ((0, 2, 4), (1, 3, 5)):
        for a, b in ((tri[0], tri[1]), (tri[1], tri[2]), (tri[2], tri[0])):
            (x0, y0), (x1, y1) = pts[a], pts[b]
            steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2)
            for k in range(steps + 1):
                t = k / steps
                x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
                if math.hypot(x - c, y - c) > 13:     # keep the middle clear over the table
                    put(round(x), round(y), 140)
    # small nodes where the star meets the ring
    for (x, y) in pts:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if abs(dx) + abs(dy) < 2:
                    put(round(x) + dx, round(y) + dy, 255)
    return im


def save_png(im, *parts):
    path = os.path.join(ASSETS, "textures", *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def save_json(obj, *parts):
    path = os.path.join(ASSETS, "models", *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    book_mask = {(x, y) for y, row in enumerate(BOOK) for x, ch in enumerate(row) if ch == "#"}
    overrides = []
    for g, (gear, colour) in enumerate(GEARS.items()):
        for tier in range(6):
            name = f"{gear}_{tier}"
            save_png(overlay(colour, tier, BAND, book_mask), "item", "focus", name + ".png")
            save_json({"parent": "minecraft:item/generated",
                       "textures": {"layer0": "minecraft:item/book", "layer1": f"researchenchanting:item/focus/{name}"}},
                      "item", "focus", name + ".json")
            if g or tier:
                overrides.append({"predicate": {"researchenchanting:look": round((g * 6 + tier) / 64.0, 6)},
                                  "model": f"researchenchanting:item/focus/{name}"})
    save_json({"parent": "minecraft:item/generated",
               "textures": {"layer0": "minecraft:item/book", "layer1": "researchenchanting:item/focus/sword_0"},
               "overrides": overrides}, "item", "research_focus.json")
    save_png(draw_map(PAGE_MAP), "item", "research_notes.png")
    save_png(draw_map(ICON_MAP), "mob_effect", "rended.png")
    save_png(rune_circle(), "misc", "rune_circle.png")
    print("wrote", len(GEARS) * 6, "focus books")
