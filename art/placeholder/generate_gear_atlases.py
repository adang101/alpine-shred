#!/usr/bin/env python3
"""
Default looks of boards and skis in the design atlas layout (design doc section 22, "Rendering"):
textures/gear/<item>.png, 64 x 40 for boards and 64 x 24 for skis, with the top face in the first
h rows, the base in the next h and the edge strip in the last 8. Pixels outside the outline are
transparent (the renderer's cutout gives the gear its shape), using the same outline as
design/Outline.java. Resource packs can re-skin these. Re-run after editing:
    python3 art/placeholder/generate_gear_atlases.py
"""
from __future__ import annotations

import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "common", "src", "main", "resources", "assets", "alpineshred", "textures", "gear"))

# Same palettes as the item model sheets (generate_textures.py): deck, accent, base, binding strap.
BOARDS = {
    "snowboard": ("#7a5230", "#c43c32", "#2b2b2b"),
    "park_snowboard": ("#e6dcc0", "#5fb34a", "#3a3a3a"),
    "freeride_snowboard": ("#24477a", "#dff2ff", "#1a1a1a"),
    "pro_snowboard": ("#1d1b1f", "#e0b13a", "#4a3e3a"),
}
SKIS = {
    "skis": ("#e6dcc0", "#c43c32", "#2b2b2b"),
    "carving_skis": ("#9aa3ad", "#c43c32", "#2b2b2b"),
    "powder_skis": ("#f2f2f7", "#8a5cc8", "#3a3a3a"),
    "park_skis": ("#3f8f3a", "#f0e14a", "#2b2b2b"),
    "pro_skis": ("#1d1b1f", "#e0b13a", "#4a3e3a"),
}
STEEL = "#b9c2cc"
W = 64


def rgb(h, a=255):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def corner(d, py, rx, ry, h):
    if d >= rx:
        return True
    if py < ry:
        cy = ry
    elif py > h - ry:
        cy = h - ry
    else:
        return True
    ex, ey = (d - rx) / rx, (py - cy) / ry
    return ex * ex + ey * ey <= 1.0


def mask(h, tail, nose):
    return [[corner(x + 0.5, y + 0.5, tail[0], tail[1], h) and corner(W - (x + 0.5), y + 0.5, nose[0], nose[1], h)
             for x in range(W)] for y in range(h)]


BOARD_MASK = mask(16, (10, 7), (10, 7))
SKI_MASK = mask(8, (3, 2), (12, 4))


def atlas(h, m, deck, accent, base):
    im = Image.new("RGBA", (W, 2 * h + 8), (0, 0, 0, 0))
    d, a, b = rgb(deck), rgb(accent), rgb(base)
    for y in range(h):
        for x in range(W):
            if not m[y][x]:
                continue
            # Top: the deck, a darker rim at the sides, and a lengthwise accent band with a nose stripe.
            c = d
            if y == 0 or y == h - 1:
                c = shade(d, 0.82)
            mid = h // 2
            band = max(1, h // 8)
            if 12 <= x <= 51 and mid - band <= y < mid + band:
                c = a
            if 54 <= x <= 56:
                c = a
            im.putpixel((x, y), c)
            # Base: dark, with two accent stripes across it.
            cb = b
            if 18 <= x <= 20 or 43 <= x <= 45:
                cb = a
            im.putpixel((x, h + y), cb)
    for y in range(2 * h, 2 * h + 8):
        for x in range(W):
            im.putpixel((x, y), rgb(STEEL))
    return im


os.makedirs(OUT, exist_ok=True)
for item, (deck, accent, base) in BOARDS.items():
    atlas(16, BOARD_MASK, deck, accent, base).save(os.path.join(OUT, item + ".png"))
for item, (deck, accent, base) in SKIS.items():
    atlas(8, SKI_MASK, deck, accent, base).save(os.path.join(OUT, item + ".png"))
print("wrote", len(BOARDS) + len(SKIS), "atlases to", OUT)
