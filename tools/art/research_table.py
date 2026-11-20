"""Draws the Research Table as a vanilla-style job-site table (16x16, spruce body, dark oak trim).

Run: python tools/art/research_table.py
Writes, under mods/expresearch/src/main/resources/assets/expresearch/:
- textures/block/research_table_{top,front,side,back}.png
- models/block/research_table.json
Top: a blueprint, an open book, an inkwell and quill. Front: a shelf of books over scroll
cubbies. Sides: a hanging magnifying glass. Built like the vanilla tables: a dark top band,
leg columns at the edges and plank seams every four rows.
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "mods", "expresearch", "src", "main",
                    "resources", "assets", "expresearch")
rng = random.Random(5)

OUTL = (41, 26, 12)
DO = [(83, 56, 26), (79, 50, 24), (73, 47, 23)]          # dark oak
DO_DK = (62, 41, 18)
SP = [(130, 97, 58), (122, 90, 52), (136, 101, 57), (122, 90, 52)]   # spruce
SP_DK = (112, 82, 46)
SP_SEAM = (90, 68, 36)
PAPER = (249, 232, 191)
PAPER_HI = (252, 252, 242)
PAPER_LN = (188, 161, 96)
BLUE = (58, 95, 176)
BLUE_DK = (44, 74, 140)
BLUE_LN = (143, 180, 234)
RED = (145, 17, 17)
RED_DK = (110, 12, 12)
INK = (27, 22, 13)
IRON = [(216, 216, 216), (160, 160, 160), (112, 112, 112)]
LENS = (191, 227, 240)
BOOKS = [(145, 17, 17), (44, 74, 154), (94, 114, 13), (119, 48, 7), (200, 160, 80), (90, 40, 110)]


def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 255))


def put(im, x, y, c):
    im.putpixel((x, y), c + (255,))


def table_side(im):
    """Top band, leg columns, spruce planks with seams every four rows."""
    for y in range(16):
        for x in range(16):
            if y == 0:
                c = rng.choice(DO[:2])
            elif y == 1:
                c = rng.choice(DO)
            elif y == 2:
                c = DO_DK
            elif x in (0, 15) or y == 15:
                c = OUTL
            elif x in (1, 14):
                c = rng.choice([DO_DK, DO[2]])
            elif y in (6, 10):
                c = SP_SEAM
            else:
                c = rng.choice(SP)
            put(im, x, y, c)
    for x in (0, 15):
        put(im, x, 0, OUTL)


def side():
    im = new()
    table_side(im)
    # magnifying glass on a nail
    put(im, 7, 3, INK)
    put(im, 7, 4, (90, 90, 90))
    ring = [(6, 5), (7, 5), (8, 5), (5, 6), (9, 6), (5, 7), (9, 7), (5, 8), (9, 8), (6, 9), (7, 9), (8, 9)]
    for (x, y) in ring:
        put(im, x, y, IRON[0] if (x < 7 and y < 8) else IRON[1] if y < 9 else IRON[2])
    for y in range(6, 9):
        for x in range(6, 9):
            put(im, x, y, LENS)
    put(im, 6, 6, PAPER_HI)
    for i, (x, y) in enumerate(((9, 10), (10, 11), (11, 12))):
        put(im, x, y, (58, 36, 17) if i else IRON[2])
    return im


def back():
    im = new()
    table_side(im)
    return im


def front():
    im = new()
    table_side(im)
    # shelf of books, rows 3-7
    x = 2
    while x < 14:
        c = rng.choice(BOOKS)
        top = rng.choice([3, 3, 4])
        for y in range(top, 8):
            put(im, x, y, c)
        put(im, x, top, tuple(min(255, v + 40) for v in c))
        x += 1
    for x in range(2, 14):
        put(im, x, 8, rng.choice(DO[:2]))           # shelf
        put(im, x, 9, DO_DK)
    # scroll cubbies, rows 10-13: dark backs with rolled paper ends
    for y in range(10, 14):
        for x in range(2, 14):
            put(im, x, y, (48, 32, 15))
    # rolled scrolls lying in the cubbies: light top, shaded underside, dark rolled ends
    for (x0, x1, y) in ((3, 8, 10), (9, 13, 10), (5, 11, 12)):
        for x in range(x0, x1):
            put(im, x, y, PAPER_HI if x == x0 + 1 else PAPER)
            put(im, x, y + 1, PAPER_LN)
        put(im, x0, y, (150, 120, 70))
        put(im, x0, y + 1, (120, 92, 50))
    for x in range(2, 14):
        put(im, x, 14, rng.choice(DO))
    return im


def top():
    im = new()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = OUTL
            elif x in (1, 14) or y in (1, 14):
                c = rng.choice(DO)
            elif y % 4 == 1:
                c = SP_SEAM
            else:
                c = rng.choice(SP)
            put(im, x, y, c)
    # blueprint sheet with a grid and a drawn part
    for y in range(2, 10):
        for x in range(2, 9):
            put(im, x, y, BLUE if (x + y) % 5 else BLUE_DK)
    for x in range(2, 9):
        put(im, x, 9, BLUE_DK)
    for y in range(2, 10):
        put(im, 8, y, BLUE_DK)
    for (x, y) in ((3, 3), (4, 3), (5, 3), (6, 3), (3, 4), (3, 5), (6, 4), (6, 5), (4, 5), (5, 5),
                   (4, 7), (5, 7), (6, 7), (5, 6)):
        put(im, x, y, BLUE_LN)
    put(im, 3, 3, PAPER_HI)
    # open book: red cover, two pages with text lines, dark spine
    for y in range(3, 9):
        for x in range(9, 14):
            put(im, x, y, RED if y in (3, 8) or x in (9, 13) else PAPER)
    for y in range(4, 8):
        put(im, 11, y, PAPER_LN)
        for x in (10, 12):
            put(im, x, y, PAPER_LN if y % 2 else PAPER_HI)
    put(im, 9, 8, RED_DK)
    put(im, 13, 8, RED_DK)
    # inkwell and quill
    for (x, y) in ((10, 11), (11, 11), (10, 12), (11, 12)):
        put(im, x, y, INK)
    put(im, 10, 11, (74, 74, 90))
    for (x, y, c) in ((12, 11, PAPER_HI), (12, 10, PAPER_HI), (13, 10, IRON[0]), (13, 9, PAPER_HI),
                      (11, 12, INK)):
        put(im, x, y, c)
    # a loose sheet in the corner
    for y in range(11, 14):
        for x in range(3, 7):
            put(im, x, y, PAPER if (x + y) % 3 else PAPER_LN)
    return im


if __name__ == "__main__":
    tex = os.path.join(ROOT, "textures", "block")
    os.makedirs(tex, exist_ok=True)
    for name, fn in (("top", top), ("front", front), ("side", side), ("back", back)):
        fn().save(os.path.join(tex, "research_table_" + name + ".png"))
    m = {
        "parent": "minecraft:block/cube",
        "textures": {
            "particle": "expresearch:block/research_table_front",
            "down": "minecraft:block/spruce_planks",
            "up": "expresearch:block/research_table_top",
            "north": "expresearch:block/research_table_front",
            "south": "expresearch:block/research_table_back",
            "east": "expresearch:block/research_table_side",
            "west": "expresearch:block/research_table_side",
        },
    }
    with open(os.path.join(ROOT, "models", "block", "research_table.json"), "w", encoding="utf-8") as f:
        json.dump(m, f, indent=2)
        f.write("\n")
    print("wrote research table textures and model")
