#!/usr/bin/env python3
"""
Placeholder art for the lift renderers (design doc sections 13 and 16): the cable strip that the tower
heads and stations scroll along the cable, and the 64x64 hardware sheet that the chair, the sheaves and the
bullwheels sample (eight 8x8 swatches in the top row, one flat colour each, so a cuboid picks its material).
Re-run after editing:  python3 art/placeholder/generate_lift_renderers.py
"""
from __future__ import annotations

import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "..", "common", "src", "main", "resources", "assets", "alpineshred"))
random.seed(29)

# Swatch index -> colour. The renderers (client/lift/Boxes) name the same indices.
SWATCHES = [
    ("#8c939c", "steel"),
    ("#4a4f57", "dark steel"),
    ("#7a2e2a", "seat"),
    ("#a8453c", "cushion"),
    ("#d9a21b", "bar"),
    ("#26282c", "rubber"),
    ("#e6b422", "accent"),
    ("#c8322a", "frame"),
]


def rgb(h):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def hardware():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for i, (colour, _) in enumerate(SWATCHES):
        base = rgb(colour)
        for y in range(8):
            for x in range(8):
                im.putpixel((i * 8 + x, y), shade(base, 0.94 + random.random() * 0.12))
    return im


def cable():
    """A strand pattern with long light and dark bands along v, so the scrolling shows."""
    im = Image.new("RGBA", (16, 16))
    light, dark = rgb("#9aa0a8"), rgb("#4a4f57")
    for y in range(16):
        for x in range(16):
            band = light if y < 8 else dark
            strand = 1.08 if (x + y) % 4 < 2 else 0.9
            im.putpixel((x, y), shade(band, strand))
    return im


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def main():
    save(hardware(), os.path.join(ASSETS, "textures", "lift", "hardware.png"))
    save(cable(), os.path.join(ASSETS, "textures", "lift", "cable.png"))
    print("wrote the lift renderer textures")


if __name__ == "__main__":
    main()
