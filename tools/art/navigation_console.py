"""Draws the Vector Planar Computer (Navigation Console) as a Create-style typewriter with a monitor.

Run: python tools/art/navigation_console.py
Writes, under mods/emp_engines/src/main/resources/assets/emp_engines/:
- textures/block/navigation_console.png   (32x32: base, body, keys)
- textures/block/navigation_monitor.png   (32x32: monitor housing and bezel)
- models/block/vector_planar_computer.json

The model faces north (keys at the front). Textures are 2 texels per model pixel, like the
Linked Typewriter, painted in the palette of Create's brass casing. The screen keeps the
animated terminal_screen texture. Keep the hitbox in VectorPlanarComputerBlock in step.
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "mods", "emp_engines", "src", "main",
                    "resources", "assets", "emp_engines")
TEX = os.path.join(ROOT, "textures", "block")
MODEL = os.path.join(ROOT, "models", "block", "vector_planar_computer.json")
rng = random.Random(11)

# Create brass casing palette
OUT = (114, 71, 49)
OUT2 = (89, 36, 36)
RIM_HI = (255, 235, 140)
RIM = (247, 203, 108)
RIM_MID = (228, 183, 99)
RIM_LO = (206, 160, 90)
RIM_SH = (158, 105, 71)
W_DK = (48, 30, 14)
W_SEAM = (58, 36, 17)
WOOD = [(79, 50, 24), (83, 56, 26), (73, 47, 23), (68, 43, 21), (90, 62, 30)]
SCREEN_DK = (24, 22, 20)
STEEL = [(110, 110, 118), (88, 88, 96), (140, 140, 148)]
LAMP_G = (90, 255, 120)
LAMP_A = (255, 170, 60)


def new():
    return Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def put(im, x, y, c):
    if 0 <= x < 32 and 0 <= y < 32:
        im.putpixel((x, y), c + (255,))


def U(v):
    """Model units (0-16) to texels."""
    return int(round(v * 2))


def wood(im, u0, v0, u1, v1, seam_every=3):
    """Dark planks running across, like the inside of brass casing."""
    x0, y0, x1, y1 = U(u0), U(v0), U(u1), U(v1)
    for y in range(y0, y1):
        row = (y - y0) % seam_every
        for x in range(x0, x1):
            if row == 0:
                put(im, x, y, rng.choice([W_DK, W_SEAM]))
            else:
                put(im, x, y, rng.choice(WOOD))


def rim(im, u0, v0, u1, v1, top=True, bottom=True, left=True, right=True):
    """Outline plus a bright brass rim inside it, lit from the top left."""
    x0, y0, x1, y1 = U(u0), U(v0), U(u1) - 1, U(v1) - 1
    for x in range(x0, x1 + 1):
        if top:
            put(im, x, y0, OUT)
            put(im, x, y0 + 1, rng.choice([RIM_HI, RIM_HI, RIM]))
        if bottom:
            put(im, x, y1, OUT)
            put(im, x, y1 - 1, rng.choice([RIM_LO, RIM_MID, RIM_LO]))
    for y in range(y0, y1 + 1):
        if left:
            put(im, x0, y, OUT)
            if (not top or y > y0) and (not bottom or y < y1):
                put(im, x0 + 1, y, rng.choice([RIM_HI, RIM]))
        if right:
            put(im, x1, y, OUT2 if y in (y0, y1) else OUT)
            if (not top or y > y0) and (not bottom or y < y1):
                put(im, x1 - 1, y, rng.choice([RIM_MID, RIM_LO]))


def panel(im, u0, v0, u1, v1, **edges):
    wood(im, u0, v0, u1, v1)
    rim(im, u0, v0, u1, v1, **edges)


def brass(im, u0, v0, u1, v1):
    for y in range(U(v0), U(v1)):
        for x in range(U(u0), U(u1)):
            put(im, x, y, rng.choice([RIM, RIM_MID, RIM_MID, RIM_LO]))


def fill(im, u0, v0, u1, v1, colours):
    for y in range(U(v0), U(v1)):
        for x in range(U(u0), U(u1)):
            put(im, x, y, rng.choice(colours))


# ---------------------------------------------------------------- textures
def console_texture():
    im = new()
    panel(im, 0, 0, 16, 3)                       # base sides
    panel(im, 0, 3, 16, 7)                       # body front and back
    for x in (U(3), U(13) - 1):                  # rivets on the body front
        put(im, x, U(5), RIM_HI)
    panel(im, 0, 7, 8, 11)                       # body ends
    # key cap top: brass ring with a dark letter dot
    brass(im, 8, 7, 10, 9)
    for x in range(U(8), U(10)):
        put(im, x, U(7), RIM_HI)
        put(im, x, U(9) - 1, RIM_SH)
    put(im, U(9) - 1, U(8) - 1, W_DK)
    fill(im, 10, 7, 12, 7.5, [RIM_LO, RIM_SH])   # key cap side
    fill(im, 10, 8, 11, 12, [W_DK, W_SEAM])      # key stems
    brass(im, 0, 11, 10, 12)                     # space bar top
    for x in range(U(0), U(10)):
        put(im, x, U(11), RIM_HI)
    fill(im, 0, 12, 10, 12.5, [RIM_LO, RIM_SH])  # space bar side
    panel(im, 0, 12.5, 16, 16)                   # base top under the keys
    return im


def monitor_texture():
    im = new()
    # bezel, 12 x 9: brass frame, dark recess, lamps and a nameplate on the chin
    panel(im, 0, 0, 12, 9)
    brass(im, 1, 1, 11, 8)
    rim(im, 0, 0, 12, 9)
    fill(im, 1.5, 1.5, 10.5, 7.5, [SCREEN_DK])
    for x in range(U(1.5), U(10.5)):
        put(im, x, U(7.5), RIM_HI)              # lit lower lip of the recess
    for x in range(U(4), U(8)):
        put(im, x, U(8) , W_DK)                  # nameplate slot
    put(im, U(9), U(8), LAMP_G)
    put(im, U(10), U(8), LAMP_A)
    panel(im, 12, 0, 16, 9)                      # housing sides, 4 x 9
    panel(im, 0, 9, 12, 12.5)                    # housing top
    for x in range(U(2), U(10), 2):              # vent slots on the top
        for y in range(U(10), U(11.5)):
            put(im, x, y, W_DK)
    panel(im, 12, 9, 15, 12.5)                   # CRT back sides
    panel(im, 0, 12.5, 7, 16)                    # CRT back
    for x in range(U(1.5), U(5.5), 2):
        put(im, x, U(14), W_DK)
        put(im, x, U(14) + 1, W_DK)
    panel(im, 7, 12.5, 14, 15.5)                 # CRT back top
    return im


# ---------------------------------------------------------------- model
C = "#console"
M = "#monitor"


def face(tex, uv, rot=None):
    f = {"uv": [round(v, 3) for v in uv], "texture": tex}
    if rot:
        f["rotation"] = rot
    return f


def box(name, frm, to, faces):
    return {"name": name, "from": frm, "to": to, "faces": faces}


def model():
    el = []
    # base slab
    side = face(C, [0, 0, 16, 3])
    el.append(box("base", [0, 0, 0], [16, 3, 16], {
        "north": side, "south": side, "east": side, "west": side,
        "up": face(C, [0, 12.5, 16, 16]), "down": face("#casing", [0, 0, 16, 16])}))
    # rear body the keys lean against
    el.append(box("body", [0, 3, 8], [16, 7, 16], {
        "north": face(C, [0, 3, 16, 7]), "south": face(C, [0, 3, 16, 7]),
        "east": face(C, [0, 7, 8, 11]), "west": face(C, [0, 7, 8, 11]),
        "up": face("#casing", [0, 0, 16, 8])}))
    # three stepped rows of keys on stems, then the space bar
    rows = [(5.5, 5.0, 1.5, 2.25), (3.5, 4.5, 2.6, 2.1), (1.5, 4.0, 1.5, 2.25)]
    for r, (z, y, x0, step) in enumerate(rows):
        for i in range(6):
            x = x0 + i * step
            cap_side = face(C, [10, 7, 11.5, 7.5])
            el.append(box(f"key_{r}_{i}", [x, y, z], [x + 1.5, y + 0.5, z + 1.5], {
                "north": cap_side, "south": cap_side, "east": cap_side, "west": cap_side,
                "up": face(C, [8, 7, 10, 9]), "down": face(C, [10, 8, 11, 9])}))
            stem = face(C, [10, 8, 10.5, 8 + (y - 3)])
            el.append(box(f"stem_{r}_{i}", [x + 0.5, 3, z + 0.5], [x + 1, y, z + 1], {
                "north": stem, "south": stem, "east": stem, "west": stem}))
    bar_side = face(C, [0, 12, 10, 12.5])
    el.append(box("space_bar", [3, 3.5, 0.25], [13, 4, 1.25], {
        "north": bar_side, "south": bar_side,
        "east": face(C, [0, 12, 1, 12.5]), "west": face(C, [0, 12, 1, 12.5]),
        "up": face(C, [0, 11, 10, 12])}))
    for x in (4, 11.5):
        st = face(C, [10, 8, 10.5, 8.5])
        el.append(box("bar_stem", [x, 3, 0.5], [x + 0.5, 3.5, 1], {
            "north": st, "south": st, "east": st, "west": st}))

    # monitor housing; its front is the screen, framed by the bezel strips
    el.append(box("monitor", [2, 7, 8.5], [14, 16, 12], {
        "north": face("#screen", [0, 3, 16, 13.5]),
        "east": face(M, [12, 0, 15.5, 9]), "west": face(M, [12, 0, 15.5, 9]),
        "up": face(M, [0, 9, 12, 12.5]), "south": face("#casing", [2, 0, 14, 9])}))

    def bez(name, x0, y0, x1, y1):
        # the bezel image spans x 2..14, y 7..16 of the front; v runs top-down
        u0, u1 = x0 - 2, x1 - 2
        v0, v1 = 16 - y1, 16 - y0
        edge = face(M, [u0, v0, u1, min(v0 + 0.5, v1)])
        el.append(box(name, [x0, y0, 7.75], [x1, y1, 8.5], {
            "north": face(M, [u0, v0, u1, v1]),
            "east": face(M, [11.5, v0, 12, v1]), "west": face(M, [0, v0, 0.5, v1]),
            "up": edge, "down": face(M, [u0, v1 - 0.5, u1, v1])}))

    bez("bezel_top", 2, 14.5, 14, 16)
    bez("bezel_bottom", 2, 7, 14, 8.5)
    bez("bezel_left", 2, 8.5, 3.5, 14.5)
    bez("bezel_right", 12.5, 8.5, 14, 14.5)

    # CRT back
    el.append(box("crt", [4.5, 9.5, 12], [11.5, 13, 15], {
        "south": face(M, [0, 12.5, 7, 16]),
        "east": face(M, [12, 9, 15, 12.5]), "west": face(M, [12, 9, 15, 12.5]),
        "up": face(M, [7, 12.5, 14, 15.5]), "down": face(M, [7, 12.5, 14, 15.5])}))

    # Create redstone antenna at the back right, as on the Linked Typewriter
    ant = face("#antenna", [0, 0, 3, 10])
    el.append(box("antenna_x", [13, 7, 14], [16, 17, 14], {"north": ant, "south": ant}))
    el.append(box("antenna_z", [14.5, 7, 12.5], [14.5, 17, 15.5], {"east": ant, "west": ant}))
    el.append(box("antenna_tip", [14, 15, 13.5], [15, 16, 14.5], {
        "up": face("#antenna", [1, 1, 2, 2]), "north": face("#antenna", [1, 1, 2, 2]),
        "south": face("#antenna", [1, 1, 2, 2]), "east": face("#antenna", [1, 1, 2, 2]),
        "west": face("#antenna", [1, 1, 2, 2])}))

    return {
        "parent": "minecraft:block/block",
        "textures": {
            "console": "emp_engines:block/navigation_console",
            "monitor": "emp_engines:block/navigation_monitor",
            "screen": "emp_engines:block/terminal_screen",
            "casing": "create:block/brass_casing",
            "antenna": "create:block/redstone_antenna",
            "particle": "create:block/brass_casing",
        },
        "elements": el,
        "display": {
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_lefthand": {"rotation": [0, 135, 0], "scale": [0.4, 0.4, 0.4]},
            "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
            "fixed": {"scale": [0.5, 0.5, 0.5]},
        },
    }


if __name__ == "__main__":
    os.makedirs(TEX, exist_ok=True)
    console_texture().save(os.path.join(TEX, "navigation_console.png"))
    monitor_texture().save(os.path.join(TEX, "navigation_monitor.png"))
    with open(MODEL, "w", encoding="utf-8") as f:
        json.dump(model(), f, indent=1)
        f.write("\n")
    print("wrote navigation console textures and model")
