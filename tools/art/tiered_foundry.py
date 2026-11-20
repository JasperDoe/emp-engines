#!/usr/bin/env python3
"""Tiered Foundry block faces: a copper-framed window, a drain hole and an air grate, drawn onto the foundry's own
bricks (vanilla bricks for tier 1, the fire brick texture for tier 2), so the controller, drains and ducts sit
in the wall like part of it. Placeholder art.

    python tools/art/tiered_foundry.py <minecraft 1.21.1 client jar>

Also the Fire Brick item: the vanilla brick item in the fire brick block's colours.
The jar is only read for bricks.png, lava_still.png and the brick item (the lit window shows vanilla lava, animated like it).
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
TEX = os.path.join(ROOT, 'mods', 'tieredfoundry', 'src', 'main', 'resources', 'assets', 'tieredfoundry', 'textures', 'block')

COPPER_LIGHT, COPPER, COPPER_DARK, COPPER_DEEP = (227, 130, 108), (192, 107, 78), (144, 73, 49), (106, 52, 36)
IRON_LIGHT, IRON, IRON_DARK = (197, 197, 197), (156, 156, 156), (84, 84, 84)
HOLE = (16, 11, 10)


def px(im, x, y, c):
    im.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def rect(im, x1, y1, x2, y2, c):
    for x in range(x1, x2 + 1):
        for y in range(y1, y2 + 1):
            px(im, x, y, c)


def bevel(im, x1, y1, x2, y2, light, dark, mid):
    """A 1 px frame, lit from the top left."""
    for x in range(x1, x2 + 1):
        px(im, x, y1, light)
        px(im, x, y2, dark)
    for y in range(y1, y2 + 1):
        px(im, x1, y, light)
        px(im, x2, y, dark)
    px(im, x2, y1, mid)
    px(im, x1, y2, mid)


def window(bg, lava_frame=None):
    im = bg.copy()
    # copper frame around a 10 x 8 window, with a riveted copper sill underneath
    bevel(im, 2, 2, 13, 11, COPPER_LIGHT, COPPER_DARK, COPPER)
    if lava_frame is None:
        rect(im, 3, 3, 12, 10, (30, 22, 20))
        for i in range(4):   # glass glint
            px(im, 4 + i, 6 - i, (70, 56, 52))
            px(im, 5 + i, 6 - i, (52, 40, 37))
        rect(im, 3, 10, 12, 10, (22, 16, 14))
    else:
        for x in range(3, 13):
            for y in range(3, 11):
                c = lava_frame.getpixel((x, y))
                px(im, x, y, c[:3])
        for x in range(3, 13):   # shade under the frame
            r, g, b, _ = im.getpixel((x, 3))
            px(im, x, 3, (r * 3 // 4, g * 3 // 4, b * 3 // 4))
    rect(im, 2, 12, 13, 13, COPPER)
    for x in range(2, 14):
        px(im, x, 12, COPPER_LIGHT)
        px(im, x, 13, COPPER_DARK)
    px(im, 3, 13, COPPER_DEEP)
    px(im, 12, 13, COPPER_DEEP)
    return im


def drain(bg, lit):
    im = bg.copy()
    # octagonal copper rim around a hole
    rim = [(5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (4, 4), (11, 4), (3, 5), (12, 5), (3, 6), (12, 6),
           (3, 7), (12, 7), (3, 8), (12, 8), (3, 9), (12, 9), (3, 10), (12, 10), (4, 11), (11, 11),
           (5, 12), (6, 12), (7, 12), (8, 12), (9, 12), (10, 12)]
    for x, y in rim:
        top_left = y < 8 and x < 8 or y < 5 or x < 4
        px(im, x, y, COPPER_LIGHT if top_left else COPPER_DARK)
    for x, y in [(4, 3), (11, 3), (3, 4), (12, 4), (3, 11), (12, 11), (4, 12), (11, 12)]:
        px(im, x, y, COPPER)
    for y in range(4, 12):
        lo, hi = (5, 10) if y in (4, 11) else (4, 11)
        for x in range(lo, hi + 1):
            px(im, x, y, HOLE)
    if lit:
        for x, y, c in [(5, 10, (196, 70, 12)), (6, 10, (232, 120, 24)), (7, 10, (250, 170, 50)), (8, 10, (250, 170, 50)),
                        (9, 10, (232, 120, 24)), (10, 10, (196, 70, 12)), (6, 11, (196, 70, 12)), (7, 11, (232, 120, 24)),
                        (8, 11, (232, 120, 24)), (9, 11, (196, 70, 12)), (6, 9, (120, 40, 10)), (9, 9, (120, 40, 10))]:
            px(im, x, y, c)
    else:
        for x in range(5, 11):
            px(im, x, 11 if 5 < x < 10 else 10, (34, 26, 24))
    return im


def duct(bg, lit):
    im = bg.copy()
    bevel(im, 2, 2, 13, 13, IRON_DARK, IRON_LIGHT, IRON)
    back = (34, 42, 52) if lit else (22, 20, 20)
    rect(im, 3, 3, 12, 12, back)
    for x in (4, 7, 10):   # three iron bars with a lit edge
        for y in range(3, 13):
            px(im, x, y, IRON_LIGHT)
            px(im, x + 1, y, IRON_DARK)
    for x in range(3, 13):   # cross brace
        px(im, x, 7, IRON)
        px(im, x, 8, IRON_DARK)
    if lit:
        for x, y in [(6, 4), (9, 5), (12, 10), (6, 11), (3, 5)]:
            px(im, x, y, (120, 160, 200))
    return im


def save(im, rel, mcmeta=None):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    if mcmeta is not None:
        with open(path + '.mcmeta', 'w', newline='\n') as f:
            json.dump(mcmeta, f, indent=2)
            f.write('\n')
    elif os.path.exists(path + '.mcmeta'):
        os.remove(path + '.mcmeta')


def fire_brick_item(brick_item, fire_bricks):
    """The vanilla brick item recoloured with the fire brick block's palette (shade for shade, by brightness)."""
    pal = sorted({c[:3] for c in fire_bricks.getdata() if c[3]}, key=sum)
    src = sorted({c[:3] for c in brick_item.getdata() if c[3]}, key=sum)
    to = {c: pal[min(len(pal) - 1, round(i * (len(pal) - 1) / max(1, len(src) - 1)))] for i, c in enumerate(src)}
    out = Image.new('RGBA', brick_item.size)
    for x in range(brick_item.width):
        for y in range(brick_item.height):
            c = brick_item.getpixel((x, y))
            if c[3]:
                out.putpixel((x, y), to[c[:3]] + (c[3],))
    return out


def main(jar):
    z = zipfile.ZipFile(jar)
    read = lambda p: Image.open(io.BytesIO(z.read('assets/minecraft/textures/block/' + p))).convert('RGBA')
    lava = read('lava_still.png')
    lava_meta = json.loads(z.read('assets/minecraft/textures/block/lava_still.png.mcmeta'))
    frames = [lava.crop((0, i * 16, 16, i * 16 + 16)) for i in range(lava.height // 16)]
    backgrounds = {1: read('bricks.png'), 2: Image.open(os.path.join(TEX, 'fire_bricks.png')).convert('RGBA')}
    for tier, bg in backgrounds.items():
        save(window(bg), f'tier{tier}/controller_off.png')
        strip = Image.new('RGBA', (16, 16 * len(frames)))
        for i, f in enumerate(frames):
            strip.paste(window(bg, f), (0, i * 16))
        save(strip, f'tier{tier}/controller_on.png', lava_meta)
        save(drain(bg, False), f'tier{tier}/drain_off.png')
        save(drain(bg, True), f'tier{tier}/drain_on.png')
        save(duct(bg, False), f'tier{tier}/duct_off.png')
        save(duct(bg, True), f'tier{tier}/duct_on.png')
    item = Image.open(io.BytesIO(z.read('assets/minecraft/textures/item/brick.png'))).convert('RGBA')
    path = os.path.join(TEX, '..', 'item', 'fire_brick.png')
    fire_brick_item(item, backgrounds[2]).save(path)
    print('wrote controller, drain and duct faces for tiers 1 and 2, and the fire brick item')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
