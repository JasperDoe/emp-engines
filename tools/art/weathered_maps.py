"""Makes the Weathered Maps item icons (16x16): a rolled parchment map tied with a ribbon.

Run: python tools/art/weathered_maps.py
Starts from tools/art/weathered_map_base.png (the purple-ribbon map, the look asked for) and recolours the ribbon
for the other two maps. Writes
mods/weatheredmaps/src/main/resources/assets/weatheredmaps/textures/item/weathered_{dungeon,pillager,fort}_map.png:
purple for dungeons, red for pillager bases, green for hostile forts.
"""
import colorsys
import os

from PIL import Image

HERE = os.path.dirname(__file__)
BASE = os.path.join(HERE, "weathered_map_base.png")
OUT = os.path.join(HERE, "..", "..", "mods", "weatheredmaps", "src", "main", "resources",
                   "assets", "weatheredmaps", "textures", "item")

# target hue (0-1) and saturation scale for each ribbon; None keeps the base purple
RIBBONS = {"dungeon": None, "pillager": (0.0, 1.05), "fort": (0.31, 0.9)}


def is_ribbon(r, g, b):
    """The ribbon is the only purple in the picture: blue and red both above green."""
    return b > g + 12 and r > g


def recolour(im, hue, sat_scale):
    out = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if not a or not is_ribbon(r, g, b):
                continue
            _, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            nr, ng, nb = colorsys.hls_to_rgb(hue, l, min(1.0, s * sat_scale))
            out.putpixel((x, y), (round(nr * 255), round(ng * 255), round(nb * 255), a))
    return out


if __name__ == "__main__":
    base = Image.open(BASE).convert("RGBA")
    os.makedirs(OUT, exist_ok=True)
    for name, colour in RIBBONS.items():
        icon = base if colour is None else recolour(base, *colour)
        icon.save(os.path.join(OUT, f"weathered_{name}_map.png"))
    print("wrote weathered map icons")
