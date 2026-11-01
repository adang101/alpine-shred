#!/usr/bin/env python3
"""
Placeholder art for the lift blocks (design doc section 13): textures, blockstates and models for the
tower pole, tower head, drive station, return station, loading gate and stop button.
Re-run after editing:  python3 art/placeholder/generate_lift_blocks.py
"""
from __future__ import annotations

import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "..", "common", "src", "main", "resources", "assets", "alpineshred"))
random.seed(13)


def rgb(h):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


STEEL = rgb("#8c939c")
DARK = rgb("#4a4f57")
YELLOW = rgb("#e6b422")
RED = rgb("#c8322a")
BLACK = rgb("#26282c")


def steel(base=STEEL):
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade(base, 0.92 + random.random() * 0.14))
    return im


def frame(im, color=DARK):
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            im.putpixel(p, color)
    return im


def rivets(im):
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        im.putpixel(p, shade(STEEL, 1.3))
    return im


def hazard(im, y0, y1):
    for y in range(y0, y1):
        for x in range(16):
            im.putpixel((x, y), YELLOW if ((x + y) // 2) % 2 == 0 else BLACK)
    return im


def pole():
    im = steel()
    for y in range(0, 16, 4):
        for x in range(16):
            im.putpixel((x, y), DARK)
    return im


def head():
    im = frame(steel())
    for y in range(6, 10):
        for x in range(3, 13):
            im.putpixel((x, y), BLACK)
    return rivets(im)


def station_side():
    return rivets(frame(steel()))


def station_top():
    im = frame(steel(shade(STEEL, 0.9)))
    for y in range(3, 13):
        for x in range(3, 13):
            im.putpixel((x, y), shade(DARK, 0.9 + random.random() * 0.2))
    for x in range(4, 12):
        im.putpixel((x, 8), shade(STEEL, 1.2))
    return im


def drive_front():
    im = station_side()
    hazard(im, 12, 15)
    # A motor housing with a red lamp.
    for y in range(3, 10):
        for x in range(3, 13):
            im.putpixel((x, y), shade(DARK, 0.8 + 0.05 * ((x + y) % 3)))
    for y in range(4, 6):
        for x in range(4, 6):
            im.putpixel((x, y), RED)
    return im


def return_front():
    im = station_side()
    hazard(im, 12, 15)
    # A tensioner: a wheel in a frame.
    for y in range(3, 11):
        for x in range(4, 12):
            d = (x - 7.5) ** 2 + (y - 6.5) ** 2
            im.putpixel((x, y), BLACK if d < 5 else shade(DARK, 0.9) if d < 14 else shade(STEEL, 0.9))
    return im


def gate():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade(YELLOW, 0.9 + random.random() * 0.12))
    for y in range(0, 16, 4):
        for x in range(16):
            im.putpixel((x, y), BLACK)
    return im


def button():
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade(RED, 0.9 + random.random() * 0.15))
    for i in range(16):
        im.putpixel((i, 0), shade(RED, 0.6))
        im.putpixel((i, 15), shade(RED, 0.6))
        im.putpixel((0, i), shade(RED, 0.6))
        im.putpixel((15, i), shade(RED, 0.6))
    for y in range(6, 10):
        for x in range(6, 10):
            im.putpixel((x, y), rgb("#f2f2f2"))
    return im


os.makedirs(os.path.join(ASSETS, "textures", "block"), exist_ok=True)
for name, image in {
    "lift_tower_pole": pole(),
    "lift_tower_head": head(),
    "lift_station_side": station_side(),
    "lift_station_top": station_top(),
    "lift_drive_station_front": drive_front(),
    "lift_return_station_front": return_front(),
    "lift_loading_gate": gate(),
    "lift_stop_button": button(),
}.items():
    image.save(os.path.join(ASSETS, "textures", "block", name + ".png"))


def write(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def box(frm, to, tex):
    return {"from": frm, "to": to, "faces": {d: {"texture": "#" + tex} for d in ("north", "south", "east", "west", "up", "down")}}


def single(name):
    write(f"blockstates/{name}.json", {"variants": {"": {"model": f"alpineshred:block/{name}"}}})
    write(f"models/item/{name}.json", {"parent": f"alpineshred:block/{name}"})


def facing(name):
    write(f"blockstates/{name}.json", {"variants": {
        "facing=north": {"model": f"alpineshred:block/{name}"},
        "facing=east": {"model": f"alpineshred:block/{name}", "y": 90},
        "facing=south": {"model": f"alpineshred:block/{name}", "y": 180},
        "facing=west": {"model": f"alpineshred:block/{name}", "y": 270},
    }})
    write(f"models/item/{name}.json", {"parent": f"alpineshred:block/{name}"})


# Tower pole: a 4x4 shaft.
single("lift_tower_pole")
write("models/block/lift_tower_pole.json", {
    "textures": {"all": "alpineshred:block/lift_tower_pole", "particle": "alpineshred:block/lift_tower_pole"},
    "elements": [box([6, 0, 6], [10, 16, 10], "all")],
})

# Tower head: a short shaft, a cross arm and two sheave blocks.
single("lift_tower_head")
write("models/block/lift_tower_head.json", {
    "textures": {"all": "alpineshred:block/lift_tower_head", "particle": "alpineshred:block/lift_tower_head"},
    "elements": [
        box([6, 0, 6], [10, 10, 10], "all"),
        box([0, 10, 5], [16, 13, 11], "all"),
        box([2, 13, 3], [14, 16, 13], "all"),
    ],
})

# Stations: cubes with a machine front (north).
for name, front in (("lift_drive_station", "lift_drive_station_front"), ("lift_return_station", "lift_return_station_front")):
    single(name)
    write(f"models/block/{name}.json", {
        "parent": "minecraft:block/cube",
        "textures": {
            "particle": "alpineshred:block/lift_station_side",
            "north": f"alpineshred:block/{front}",
            "south": "alpineshred:block/lift_station_side",
            "east": "alpineshred:block/lift_station_side",
            "west": "alpineshred:block/lift_station_side",
            "up": "alpineshred:block/lift_station_top",
            "down": "alpineshred:block/lift_station_side",
        },
    })

# Loading gate (faces north): two posts and two bars.
facing("lift_loading_gate")
write("models/block/lift_loading_gate.json", {
    "textures": {"all": "alpineshred:block/lift_loading_gate", "particle": "alpineshred:block/lift_loading_gate"},
    "elements": [
        box([0, 0, 6], [2, 14, 10], "all"),
        box([14, 0, 6], [16, 14, 10], "all"),
        box([2, 4, 7], [14, 6, 9], "all"),
        box([2, 9, 7], [14, 11, 9], "all"),
    ],
})

# Stop button (faces north, so it sits on the south side of its block).
facing("lift_stop_button")
write("models/block/lift_stop_button.json", {
    "textures": {"all": "alpineshred:block/lift_stop_button", "particle": "alpineshred:block/lift_stop_button"},
    "elements": [box([5, 5, 14], [11, 11, 16], "all")],
})
print("lift blocks written")
