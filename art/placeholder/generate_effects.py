#!/usr/bin/env python3
"""Placeholder particle and screen-overlay textures (design doc sections 16 and 17).

Particles are 8 x 8 frames; overlays are stretched over the screen, so they are smooth gradients
rather than pixel art. Run from anywhere:  python3 art/placeholder/generate_effects.py
Requires Pillow and numpy.
"""
import json
import math
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "..", "common", "src", "main", "resources", "assets", "alpineshred"))
rng = np.random.default_rng(1729)


def save(img, rel):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def blob(radius, softness=1.0, base=(250, 252, 255), seed=0):
    """An 8 x 8 snow clump: a soft disc with a little speckle."""
    r = np.random.default_rng(seed)
    a = np.zeros((8, 8, 4), np.uint8)
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            alpha = max(0.0, min(1.0, (radius - d) / softness + 0.5))
            if alpha <= 0:
                continue
            shade = 1.0 - 0.10 * (d / max(radius, 0.1)) - r.uniform(0, 0.06)
            a[y, x] = [int(base[0] * shade), int(base[1] * shade), int(base[2] * shade), int(255 * alpha)]
    return Image.fromarray(a, "RGBA")


def particles():
    defs = {}
    spray = [blob(3.2 - i * 0.6, 0.9, seed=i) for i in range(4)]
    for i, im in enumerate(spray):
        save(im, f"textures/particle/snow_spray_{i}.png")
    defs["snow_spray"] = [f"alpineshred:snow_spray_{i}" for i in range(4)]

    poof = [blob(2.0 + i * 0.6, 1.6 + i * 0.4, base=(244, 248, 255), seed=10 + i) for i in range(4)]
    for i, im in enumerate(poof):
        save(im, f"textures/particle/snow_poof_{i}.png")
    defs["snow_poof"] = [f"alpineshred:snow_poof_{i}" for i in range(4)]

    # Trail: a short flat groove mark, lighter than snow at the rim, shadowed in the middle.
    a = np.zeros((8, 8, 4), np.uint8)
    for y in range(8):
        for x in range(8):
            edge = abs(x - 3.5)
            if edge > 3.5:
                continue
            v = 205 if edge < 1.5 else 235
            alpha = 150 if edge < 1.5 else 90
            a[y, x] = [v, v + 8, 255, alpha]
    save(Image.fromarray(a, "RGBA"), "textures/particle/snow_trail.png")
    defs["snow_trail"] = ["alpineshred:snow_trail"]

    for name, textures in defs.items():
        path = os.path.join(ASSETS, "particles", name + ".json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            json.dump({"textures": textures}, f, indent=2)
            f.write("\n")


def overlays():
    size = 256
    yy, xx = np.mgrid[0:size, 0:size]
    cx = cy = (size - 1) / 2
    # Normalized distance from center, 1 at the edge midpoints.
    d = np.hypot((xx - cx) / cx, (yy - cy) / cy)

    # Vignette: white at the edges, clear in the middle; tinted at draw time (white or red).
    v = np.clip((d - 0.55) / 0.75, 0, 1) ** 1.6
    img = np.zeros((size, size, 4), np.uint8)
    img[..., :3] = 255
    img[..., 3] = (v * 255).astype(np.uint8)
    save(Image.fromarray(img, "RGBA"), "textures/gui/overlay/vignette.png")

    # Speed lines: thin radial streaks, strongest near the edges, none in the middle third.
    angle = np.arctan2(yy - cy, xx - cx)
    streaks = np.zeros((size, size))
    for _ in range(70):
        a0 = rng.uniform(-math.pi, math.pi)
        width = rng.uniform(0.004, 0.012)
        start = rng.uniform(0.55, 0.85)
        delta = np.abs((angle - a0 + math.pi) % (2 * math.pi) - math.pi)
        streaks = np.maximum(streaks, np.clip(1 - delta / width, 0, 1) * np.clip((d - start) / 0.25, 0, 1))
    img = np.zeros((size, size, 4), np.uint8)
    img[..., :3] = 255
    img[..., 3] = (np.clip(streaks, 0, 1) * 150).astype(np.uint8)
    save(Image.fromarray(img, "RGBA"), "textures/gui/overlay/speed_lines.png")

    # Goggles frame: a rounded dark rim with a soft inner edge.
    fx = np.abs(xx - cx) / cx
    fy = np.abs(yy - cy) / cy
    rim = (fx ** 6 + (fy * 1.25) ** 6) ** (1 / 6)
    frame = np.clip((rim - 0.88) / 0.08, 0, 1)
    img = np.zeros((size, size, 4), np.uint8)
    img[..., 0] = 18
    img[..., 1] = 20
    img[..., 2] = 26
    img[..., 3] = (frame * 235).astype(np.uint8)
    save(Image.fromarray(img, "RGBA"), "textures/gui/overlay/goggles_frame.png")


def poles():
    """Held ski poles (drawn by SnowGearLayer from boxes): 32 x 16, one flat colour per box.

    Regions: shaft x 0-3, grip x 4-11 (rows 0-7), basket x 12-27 (rows 0-7), tip x 4-11 (rows 8-15).
    """
    for name, shaft in (("ski_poles", (176, 184, 196)), ("carbon_ski_poles", (40, 42, 48))):
        a = np.zeros((16, 32, 4), np.uint8)
        a[:, 0:4] = (*shaft, 255)
        a[:, 1, :3] = np.clip(np.array(shaft) + 30, 0, 255)  # a highlight down the shaft
        a[0:8, 4:12] = (22, 22, 26, 255)      # grip
        a[0:8, 12:28] = (196, 40, 36, 255)    # basket
        a[8:16, 4:12] = (70, 70, 76, 255)     # tip
        save(Image.fromarray(a, "RGBA"), f"textures/entity/{name}.png")


if __name__ == "__main__":
    particles()
    overlays()
    poles()
    print("wrote particle, overlay and pole textures")
