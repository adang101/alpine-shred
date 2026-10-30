#!/usr/bin/env python3
"""
Placeholder art for the Paint Bench (design doc section 22): a crafting-table-like block with paint
pots on top, brushes and a half-painted board on the front. Writes the block textures, blockstate
and models. Re-run after editing:  python3 art/placeholder/generate_bench.py
"""
from __future__ import annotations

import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "..", "common", "src", "main", "resources", "assets", "alpineshred"))
random.seed(22)


def rgb(h):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


PLANK = rgb("#a67c4e")
DARK = rgb("#6e4f2e")
PAINTS = [rgb("#d33a2c"), rgb("#2f6fd1"), rgb("#f2c230"), rgb("#3fa34d"), rgb("#f0f0f0")]


def planks():
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = PLANK
            if y % 4 == 3:
                c = DARK
            elif (x + (y // 4) * 5) % 8 == 0:
                c = shade(PLANK, 0.85)
            else:
                c = shade(PLANK, 0.93 + random.random() * 0.12)
            im.putpixel((x, y), c)
    return im


def top():
    im = planks()
    # A frame, and four paint pots.
    for i in range(16):
        im.putpixel((i, 0), DARK)
        im.putpixel((i, 15), DARK)
        im.putpixel((0, i), DARK)
        im.putpixel((15, i), DARK)
    for (px, py), c in zip([(2, 2), (9, 2), (2, 9), (9, 9)], PAINTS):
        for y in range(py, py + 5):
            for x in range(px, px + 5):
                edge = x in (px, px + 4) or y in (py, py + 4)
                im.putpixel((x, y), rgb("#3c3c3c") if edge else c)
    # Splatters.
    for _ in range(10):
        im.putpixel((random.randrange(1, 15), random.randrange(1, 15)), random.choice(PAINTS))
    return im


def side():
    im = planks()
    for _ in range(14):
        x, y = random.randrange(16), random.randrange(16)
        im.putpixel((x, y), random.choice(PAINTS))
    for y in range(16):
        im.putpixel((0, y), DARK)
        im.putpixel((15, y), DARK)
    return im


def front():
    im = side()
    # A board on a rack, its left half painted red, the right still bare wood.
    for x in range(2, 14):
        for y in range(5, 9):
            if (x, y) in ((2, 5), (13, 5), (2, 8), (13, 8)):
                continue
            im.putpixel((x, y), PAINTS[0] if x < 8 else rgb("#d9c4a0"))
    for x in range(2, 14):
        im.putpixel((x, 9), rgb("#3c3c3c"))
    # Two brushes.
    for y in range(11, 15):
        im.putpixel((4, y), DARK)
        im.putpixel((10, y), DARK)
    im.putpixel((4, 10), PAINTS[1])
    im.putpixel((10, 10), PAINTS[2])
    return im


os.makedirs(os.path.join(ASSETS, "textures", "block"), exist_ok=True)
top().save(os.path.join(ASSETS, "textures", "block", "paint_bench_top.png"))
side().save(os.path.join(ASSETS, "textures", "block", "paint_bench_side.png"))
front().save(os.path.join(ASSETS, "textures", "block", "paint_bench_front.png"))


def write(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


write("blockstates/paint_bench.json", {"variants": {"": {"model": "alpineshred:block/paint_bench"}}})
write("models/block/paint_bench.json", {
    "parent": "minecraft:block/cube",
    "textures": {
        "particle": "alpineshred:block/paint_bench_side",
        "north": "alpineshred:block/paint_bench_front",
        "south": "alpineshred:block/paint_bench_side",
        "east": "alpineshred:block/paint_bench_side",
        "west": "alpineshred:block/paint_bench_front",
        "up": "alpineshred:block/paint_bench_top",
        "down": "minecraft:block/oak_planks",
    },
})
write("models/item/paint_bench.json", {"parent": "alpineshred:block/paint_bench"})
print("paint bench written")
