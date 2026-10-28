#!/usr/bin/env python3
"""
Placeholder pixel art for Alpine Shred, phase 1.

Every texture here is a stand-in so gear is visible in game while real art is made in Blockbench
(design doc section 16). Re-run after editing:  python3 art/placeholder/generate_textures.py
Writes into common/src/main/resources/assets. Requires Pillow.

Dyeable layers (boots, helmet, mitts, armor) are drawn in grayscale so the item color tints them.
"""
from __future__ import annotations

import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "..", "common", "src", "main", "resources", "assets"))


def rgb(hex_color: str, alpha: int = 255) -> tuple[int, int, int, int]:
    h = hex_color.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha


def shade(c, f: float):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def new(w=16, h=16):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def rect(im, x0, y0, x1, y1, c):
    """Inclusive pixel rectangle."""
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            if 0 <= x < im.width and 0 <= y < im.height:
                im.putpixel((x, y), c)


def line(im, x0, y0, x1, y1, c):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        if 0 <= x0 < im.width and 0 <= y0 < im.height:
            im.putpixel((x0, y0), c)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def save(im, rel):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    return path


# --------------------------------------------------------------------------------------------
# Gear model sheets (16 x 16), UV layout shared by template_snowboard.json / template_skis.json
#   x 0-3   deck top        (boards: 4 wide, skis: columns 0-1)
#   x 4-7   base            (boards: 4 wide, skis: columns 4-5)
#   x 8-11, y 0-2  nose/tail top     y 3-5  nose/tail base
#   x 8-11, y 8-11 binding
#   x 12-15 edges (steel)
# --------------------------------------------------------------------------------------------

BOARDS = {
    # id: (deck, accent, base, binding strap)
    "snowboard": ("#7a5230", "#c43c32", "#2b2b2b", "#c43c32"),
    "park_snowboard": ("#e6dcc0", "#5fb34a", "#3a3a3a", "#d07a3c"),
    "freeride_snowboard": ("#24477a", "#dff2ff", "#1a1a1a", "#9ad8f0"),
    "pro_snowboard": ("#1d1b1f", "#e0b13a", "#4a3e3a", "#e0b13a"),
}

SKIS = {
    "skis": ("#e6dcc0", "#c43c32", "#2b2b2b", "#c43c32"),
    "carving_skis": ("#9aa3ad", "#c43c32", "#2b2b2b", "#202020"),
    "powder_skis": ("#f2f2f7", "#8a5cc8", "#3a3a3a", "#8a5cc8"),
    "park_skis": ("#3f8f3a", "#f0e14a", "#2b2b2b", "#f0e14a"),
    "pro_skis": ("#1d1b1f", "#e0b13a", "#4a3e3a", "#e0b13a"),
}

STEEL = rgb("#b9c2cc")
BINDING = rgb("#262626")


def board_sheet(deck, accent, base, strap, width):
    im = new()
    d, a, b = rgb(deck), rgb(accent), rgb(base)
    # deck top with a lengthwise accent graphic
    rect(im, 0, 0, width - 1, 15, d)
    for y in range(16):
        im.putpixel((0, y), shade(d, 0.85))
    if width >= 4:
        rect(im, 1, 6, 2, 9, a)
        im.putpixel((1, 3), a)
        im.putpixel((2, 12), a)
    else:
        rect(im, 0, 5, width - 1, 10, a)
    # base: darker with a centre stripe
    rect(im, 4, 0, 4 + width - 1, 15, b)
    rect(im, 4, 4, 4 + width - 1, 5, a)
    rect(im, 4, 10, 4 + width - 1, 11, a)
    # nose/tail
    rect(im, 8, 0, 11, 2, d)
    rect(im, 8, 0, 11, 0, a)
    rect(im, 8, 3, 11, 5, b)
    # binding with strap
    rect(im, 8, 8, 11, 11, BINDING)
    rect(im, 8, 9, 11, 9, rgb(strap))
    # steel edges
    rect(im, 12, 0, 15, 15, STEEL)
    return im


for item, colors in BOARDS.items():
    save(board_sheet(*colors, width=4), f"alpineshred/textures/item/gear/{item}.png")
for item, colors in SKIS.items():
    save(board_sheet(*colors, width=2), f"alpineshred/textures/item/gear/{item}.png")

# --------------------------------------------------------------------------------------------
# Goggles: strap and frame sheet plus one lens texture per tint
# --------------------------------------------------------------------------------------------
gog = new()
rect(gog, 0, 0, 7, 3, rgb("#2a2a2a"))       # strap
rect(gog, 0, 1, 7, 1, rgb("#5a5a5a"))
rect(gog, 8, 0, 15, 7, rgb("#f0f0f0"))      # frame
rect(gog, 8, 7, 15, 7, rgb("#c8c8c8"))
save(gog, "alpineshred/textures/item/gear/goggles.png")

for lens, (c, hi) in {
    "clear": ("#cfe6ef", "#ffffff"),
    "amber": ("#e39a2b", "#ffd27a"),
    "blue_mirror": ("#2f6fd6", "#9fd2ff"),
    "black": ("#1b1b22", "#5a5a6a"),
}.items():
    im = new()
    rect(im, 0, 0, 15, 15, rgb(c, 235))
    line(im, 2, 2, 6, 2, rgb(hi))
    line(im, 2, 3, 3, 3, rgb(hi))
    save(im, f"alpineshred/textures/item/gear/lens_{lens}.png")

# --------------------------------------------------------------------------------------------
# Mitts: grayscale body (tinted), white cuff
# --------------------------------------------------------------------------------------------
mitt = new()
rect(mitt, 0, 0, 7, 7, rgb("#d8d8d8"))
for x in range(8):
    mitt.putpixel((x, 7), rgb("#a8a8a8"))
rect(mitt, 8, 0, 15, 3, rgb("#f4f1ea"))     # cuff
rect(mitt, 8, 4, 15, 4, rgb("#d9d4c8"))
save(mitt, "alpineshred/textures/item/gear/mitts.png")

# --------------------------------------------------------------------------------------------
# Flat item icons
# --------------------------------------------------------------------------------------------


def pole_icon(shaft, grip, accent):
    im = new()
    line(im, 4, 12, 14, 2, rgb(shaft))
    line(im, 5, 12, 14, 3, shade(rgb(shaft), 0.75))
    rect(im, 2, 12, 4, 14, rgb(grip))          # grip
    im.putpixel((1, 14), rgb(grip))
    line(im, 10, 4, 13, 7, rgb(accent))       # basket
    im.putpixel((14, 2), rgb("#e8e8e8"))     # tip
    return im


save(pole_icon("#aab3bd", "#262626", "#3a3a3a"), "alpineshred/textures/item/ski_poles.png")
save(pole_icon("#2a2830", "#262626", "#9a5fd6"), "alpineshred/textures/item/carbon_ski_poles.png")

wax = new()
rect(wax, 3, 5, 12, 11, rgb("#f2c14e"))
rect(wax, 3, 5, 12, 5, rgb("#ffe08a"))
rect(wax, 3, 11, 12, 11, rgb("#c9902c"))
rect(wax, 5, 7, 10, 9, rgb("#ffffff"))
rect(wax, 6, 8, 9, 8, rgb("#5aa7e0"))
save(wax, "alpineshred/textures/item/board_wax.png")

tuner = new()
line(tuner, 6, 9, 13, 2, rgb("#9aa3ad"))
line(tuner, 7, 9, 13, 3, rgb("#6c747d"))
for i in range(7, 13, 2):
    tuner.putpixel((i, 14 - i), rgb("#d8dee4"))
rect(tuner, 2, 10, 5, 13, rgb("#7a5230"))
rect(tuner, 3, 11, 4, 12, rgb("#9c6b3f"))
save(tuner, "alpineshred/textures/item/edge_tuner.png")

wrench = new()
copper = rgb("#c8743c")
line(wrench, 3, 13, 10, 6, rgb("#9aa3ad"))
line(wrench, 4, 13, 11, 6, rgb("#6c747d"))
rect(wrench, 10, 2, 13, 5, copper)
rect(wrench, 11, 2, 12, 3, (0, 0, 0, 0))
rect(wrench, 2, 12, 4, 14, copper)
save(wrench, "alpineshred/textures/item/park_wrench.png")

linker = new()
line(linker, 3, 13, 11, 5, rgb("#9aa3ad"))
line(linker, 4, 13, 12, 5, rgb("#6c747d"))
rect(linker, 11, 2, 13, 4, rgb("#d02a2a"))
linker.putpixel((12, 3), rgb("#ff7070"))
line(linker, 2, 6, 6, 2, rgb("#e8e8e8"))
line(linker, 6, 2, 9, 4, rgb("#e8e8e8"))
save(linker, "alpineshred/textures/item/lift_linker.png")

ticket = new()
rect(ticket, 2, 4, 13, 11, rgb("#f4efe1"))
rect(ticket, 2, 4, 13, 4, rgb("#ffffff"))
rect(ticket, 2, 11, 13, 11, rgb("#cfc6b0"))
rect(ticket, 10, 4, 13, 11, rgb("#e0b13a"))
ticket.putpixel((11, 7), (0, 0, 0, 0))
ticket.putpixel((11, 8), (0, 0, 0, 0))
rect(ticket, 4, 6, 8, 6, rgb("#2f6fd6"))
rect(ticket, 4, 8, 7, 8, rgb("#8a8a8a"))
save(ticket, "alpineshred/textures/item/lift_pass.png")


def boot_icon(cuff_rows, buckles):
    base, over = new(), new()
    g, dark = rgb("#d0d0d0"), rgb("#9a9a9a")
    rect(base, 4, 3, 9, 11, g)          # shaft
    rect(base, 4, 11, 13, 13, g)        # foot
    rect(base, 4, 13, 13, 13, dark)
    for y in range(3, 12):
        base.putpixel((4, y), dark)
    rect(over, 3, 13, 13, 14, rgb("#2a2a2a"))   # sole
    rect(over, 4, 2, 9, 2 + cuff_rows - 1, rgb("#f4f1ea"))
    for y in buckles:
        rect(over, 7, y, 10, y, rgb("#b9c2cc"))
    return base, over


b, o = boot_icon(2, [6, 9])
save(b, "alpineshred/textures/item/ski_boots.png")
save(o, "alpineshred/textures/item/ski_boots_overlay.png")
b, o = boot_icon(1, [7])
save(b, "alpineshred/textures/item/snowboard_boots.png")
save(o, "alpineshred/textures/item/snowboard_boots_overlay.png")

helm, helm_over = new(), new()
g = rgb("#e0e0e0")
rect(helm, 4, 3, 11, 3, g)
rect(helm, 3, 4, 12, 9, g)
rect(helm, 2, 10, 13, 11, shade(g, 0.8))
rect(helm_over, 5, 4, 5, 6, rgb("#5a5a5a"))
rect(helm_over, 10, 4, 10, 6, rgb("#5a5a5a"))
rect(helm_over, 3, 8, 12, 9, rgb("#2a2a2a"))        # goggle strap band
rect(helm_over, 6, 8, 9, 9, rgb("#2f6fd6"))         # lens hint
save(helm, "alpineshred/textures/item/ski_helmet.png")
save(helm_over, "alpineshred/textures/item/ski_helmet_overlay.png")

# --------------------------------------------------------------------------------------------
# Groomed snow: packed white with corduroy ridges
# --------------------------------------------------------------------------------------------
random.seed(1850301)
snow = new()
for y in range(16):
    for x in range(16):
        v = 244 - (10 if y % 2 == 0 else 0) + random.randint(-3, 3)
        snow.putpixel((x, y), (v - 6, v - 2, v + 4 if v + 4 < 256 else 255, 255))
save(snow, "alpineshred/textures/block/groomed_snow.png")

# --------------------------------------------------------------------------------------------
# Trinkets slot icons (grayscale silhouettes)
# --------------------------------------------------------------------------------------------
icon_c = rgb("#8b8b8b", 160)
ic = new()
line(ic, 3, 12, 12, 3, icon_c)
line(ic, 4, 12, 12, 4, icon_c)
line(ic, 3, 11, 11, 3, icon_c)
save(ic, "alpineshred/textures/gui/slots/snow_gear.png")
ic = new()
rect(ic, 2, 6, 13, 9, icon_c)
rect(ic, 3, 7, 6, 8, (0, 0, 0, 0))
rect(ic, 9, 7, 12, 8, (0, 0, 0, 0))
save(ic, "alpineshred/textures/gui/slots/goggles.png")
ic = new()
rect(ic, 4, 4, 10, 12, icon_c)
rect(ic, 11, 7, 12, 9, icon_c)
rect(ic, 4, 12, 10, 13, rgb("#a8a8a8", 160))
save(ic, "alpineshred/textures/gui/slots/mitts.png")

# --------------------------------------------------------------------------------------------
# Armor: vanilla humanoid layout, 64 x 32. Base is grayscale (dyed), overlay is untinted.
# Head cube UV origin (0,0): top 8..15 x 0..7, right 0..7 / front 8..15 / left 16..23 / back 24..31 at y 8..15.
# Leg cube UV origin (0,16): right 0..3 / front 4..7 / left 8..11 / back 12..15 at y 20..31.
# --------------------------------------------------------------------------------------------
armor, armor_over = new(64, 32), new(64, 32)
shell, shell_dark = rgb("#e6e6e6"), rgb("#bdbdbd")
# helmet: top, upper sides, brim across the forehead, face left open
rect(armor, 8, 0, 15, 7, shell)
rect(armor, 0, 8, 7, 12, shell)
rect(armor, 16, 8, 23, 12, shell)
rect(armor, 24, 8, 31, 13, shell)
rect(armor, 8, 8, 15, 9, shell)
rect(armor, 8, 9, 15, 9, shell_dark)
rect(armor_over, 10, 1, 10, 6, rgb("#5a5a5a"))     # vents
rect(armor_over, 13, 1, 13, 6, rgb("#5a5a5a"))
rect(armor_over, 24, 11, 31, 11, rgb("#2a2a2a"))   # goggle strap around the back
rect(armor_over, 0, 11, 7, 11, rgb("#2a2a2a"))
rect(armor_over, 16, 11, 23, 11, rgb("#2a2a2a"))
# boots: lower 6 rows of each leg face, plus the sole
rect(armor, 0, 26, 15, 31, shell)
rect(armor, 4, 16, 11, 19, shell_dark)                 # top/bottom caps
rect(armor_over, 0, 31, 15, 31, rgb("#2a2a2a"))      # sole line
rect(armor_over, 4, 28, 7, 28, rgb("#b9c2cc"))       # front buckle
rect(armor_over, 4, 26, 7, 26, rgb("#f4f1ea"))       # cuff
save(armor, "minecraft/textures/models/armor/alpineshred_alpine_layer_1.png")
save(armor_over, "minecraft/textures/models/armor/alpineshred_alpine_layer_1_overlay.png")

# Leggings are never made from this material, but vanilla asks for layer 2 when a mod or
# datapack adds any. Ship transparent sheets so nothing renders as missing texture.
save(new(64, 32), "minecraft/textures/models/armor/alpineshred_alpine_layer_2.png")
save(new(64, 32), "minecraft/textures/models/armor/alpineshred_alpine_layer_2_overlay.png")

print("textures written under", ASSETS)
