"""Draws the Vector Planar Computer textures (16x16, vanilla style, Create brass palette).

Run: python tools/art/terminal_textures.py
Writes into mods/createskybound/src/main/resources/assets/createskybound/textures/block/.
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "mods", "createskybound", "src", "main",
                   "resources", "assets", "createskybound", "textures", "block")
os.makedirs(OUT, exist_ok=True)
rng = random.Random(7)

# Create's brass palette
HI = (255, 235, 140)
B1 = (247, 203, 108)
B2 = (228, 183, 99)
B3 = (215, 170, 94)
B4 = (206, 160, 90)
B5 = (191, 142, 85)
SH = (158, 105, 71)
DK = (114, 71, 49)
DD = (79, 50, 24)
BK = (48, 30, 14)


def img(w=16, h=16, c=(0, 0, 0, 0)):
    return Image.new("RGBA", (w, h), c)


def px(im, x, y, c):
    im.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def brass_fill(im, x0, y0, x1, y1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, rng.choice([B3, B3, B4, B4, B2, B5]))


def frame(im, x0, y0, x1, y1, light=HI, dark=DK):
    for x in range(x0, x1 + 1):
        px(im, x, y0, light)
        px(im, x, y1, dark)
    for y in range(y0, y1 + 1):
        px(im, x0, y, light if y < y1 else dark)
        px(im, x1, y, dark)


def rivet(im, x, y):
    px(im, x, y, HI)
    px(im, x + 1, y + 1, DD)


def shell():
    im = img()
    brass_fill(im, 0, 0, 15, 15)
    frame(im, 0, 0, 15, 15, B1, DK)
    for y in (5, 10):
        for x in range(1, 15):
            px(im, x, y, SH)
    for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(im, x, y)
    return im


def bezel():
    im = shell()
    # dark recess the screen sits in
    for y in range(2, 12):
        for x in range(2, 14):
            px(im, x, y, BK)
    frame(im, 1, 1, 14, 12, DK, HI)
    # nameplate and two indicator lamps under the screen
    for x in range(4, 10):
        px(im, x, 14, DD)
    px(im, 11, 14, (90, 255, 120))
    px(im, 13, 14, (255, 170, 60))
    return im


GREEN = (100, 255, 130)
MID = (50, 170, 75)
DIM = (24, 90, 40)
BG = (8, 30, 14)
BG2 = (5, 22, 10)


def screen_frame(cursor, shift):
    im = img()
    for y in range(16):
        for x in range(16):
            px(im, x, y, BG if y % 2 == 0 else BG2)
    # rounded CRT corners
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15), (1, 0), (0, 1), (14, 0), (15, 1), (0, 14), (1, 15), (15, 14), (14, 15)):
        px(im, x, y, (3, 12, 5))
    lines = [
        [(2, 8)],                       # header "VPC-01"
        [(2, 4), (7, 11)],
        [(2, 6), (9, 12)],
        [(2, 3), (5, 10)],
        [(2, 9)],
    ]
    y = 2
    for i, segs in enumerate(lines[shift:] + lines[:shift]):
        for a, b in segs:
            for x in range(a, b + 1):
                px(im, x, y, GREEN if i == 0 else MID)
        y += 2
    # charge bar along the bottom
    for x in range(2, 14):
        px(im, x, 12, DIM)
    for x in range(2, 9):
        px(im, x, 12, GREEN)
    # prompt and cursor
    px(im, 2, 14, MID)
    px(im, 3, 14, MID)
    if cursor:
        px(im, 5, 14, GREEN)
        px(im, 6, 14, GREEN)
    return im


def screen():
    frames = [screen_frame(c, s) for s, c in ((0, True), (0, False), (0, True), (1, False), (1, True), (1, False))]
    sheet = img(16, 16 * len(frames))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, 16 * i))
    return sheet


def keys():
    im = img()
    brass_fill(im, 0, 0, 15, 15)
    frame(im, 0, 0, 15, 15, B1, DK)
    for row, y in enumerate((2, 6, 10)):
        x = 2 if row != 1 else 3
        while x + 1 < 14:
            for dx in range(2):
                px(im, x + dx, y, (205, 196, 176))
                px(im, x + dx, y + 1, (150, 140, 124))
                px(im, x + dx, y + 2, (60, 48, 36))
            x += 3
    for x in range(4, 12):     # space bar
        px(im, x, 13, (205, 196, 176))
        px(im, x, 14, (60, 48, 36))
    return im


def vent():
    im = shell()
    for y in range(3, 13, 2):
        for x in range(3, 13):
            px(im, x, y, BK)
            px(im, x, y + 1, SH)
    return im


def dark():
    im = img()
    for y in range(16):
        for x in range(16):
            px(im, x, y, rng.choice([DK, DD, DD, BK]))
    return im


for name, fn in (("terminal_shell", shell), ("terminal_bezel", bezel), ("terminal_screen", screen),
                 ("terminal_keys", keys), ("terminal_vent", vent), ("terminal_dark", dark)):
    fn().save(os.path.join(OUT, name + ".png"))

with open(os.path.join(OUT, "terminal_screen.png.mcmeta"), "w") as f:
    f.write('{ "animation": { "frametime": 12, "interpolate": false } }\n')
print("textures written to", os.path.abspath(OUT))
