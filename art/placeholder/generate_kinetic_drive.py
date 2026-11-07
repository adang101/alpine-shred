#!/usr/bin/env python3
"""
Placeholder art for the kinetic lift drive (phase 9 plan, decision 2; README 78): a gearbox with a cable wheel on
every side face and a shaft hole in both ends. Writes two 16x16 textures, the blockstate (the log-style axis
variants), the block model and the item model.
Re-run after editing:  python3 art/placeholder/generate_kinetic_drive.py
"""
from __future__ import annotations

import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "..", "common", "src", "main", "resources", "assets", "alpineshred"))
random.seed(29)


def rgb(h):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


STEEL = rgb("#8c939c")
DARK = rgb("#4a4f57")
BLACK = rgb("#26282c")
CABLE = rgb("#b8956a")
BRASS = rgb("#c9a227")


def steel():
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade(STEEL, 0.92 + random.random() * 0.14))
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            im.putpixel(p, DARK)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        im.putpixel(p, shade(STEEL, 1.3))
    return im


def side():
    """The cable wheel: a dark groove ring wrapped in rope, with a brass hub."""
    im = steel()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if 4.0 <= d < 6.2:
                # rope: alternating light and dark pixels around the rim
                im.putpixel((x, y), CABLE if (x + y) % 2 == 0 else shade(CABLE, 0.7))
            elif 3.0 <= d < 4.0:
                im.putpixel((x, y), BLACK)
            elif d < 3.0:
                im.putpixel((x, y), shade(DARK, 1.15))
    for y in range(6, 10):
        for x in range(6, 10):
            im.putpixel((x, y), BRASS)
    return im


def end():
    """A shaft hole: a dark socket with a brass shaft in it."""
    im = steel()
    for y in range(5, 11):
        for x in range(5, 11):
            im.putpixel((x, y), BLACK)
    for y in range(6, 10):
        for x in range(6, 10):
            im.putpixel((x, y), BRASS)
    for p in ((6, 6), (7, 6), (6, 7)):
        im.putpixel(p, shade(BRASS, 1.25))
    return im


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    tex = os.path.join(ASSETS, "textures", "block")
    os.makedirs(tex, exist_ok=True)
    side().save(os.path.join(tex, "kinetic_lift_drive_side.png"))
    end().save(os.path.join(tex, "kinetic_lift_drive_end.png"))
    write_json(os.path.join(ASSETS, "models", "block", "kinetic_lift_drive.json"), {
        "parent": "minecraft:block/cube_column",
        "textures": {
            "particle": "alpineshred:block/kinetic_lift_drive_side",
            "end": "alpineshred:block/kinetic_lift_drive_end",
            "side": "alpineshred:block/kinetic_lift_drive_side",
        },
    })
    write_json(os.path.join(ASSETS, "models", "item", "kinetic_lift_drive.json"), {"parent": "alpineshred:block/kinetic_lift_drive"})
    model = "alpineshred:block/kinetic_lift_drive"
    write_json(os.path.join(ASSETS, "blockstates", "kinetic_lift_drive.json"), {"variants": {
        "axis=y": {"model": model},
        "axis=z": {"model": model, "x": 90},
        "axis=x": {"model": model, "x": 90, "y": 90},
    }})


if __name__ == "__main__":
    main()
