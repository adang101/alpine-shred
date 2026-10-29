#!/usr/bin/env python3
"""Placeholder blockstates, models and textures for the park blocks (design doc sections 10 and 11).

Geometry follows the Java shapes exactly (ParkShapes, RailPath, RampSlope, PipeArc): pieces are
authored facing north and turned with the blockstate's y rotation (east 90, south 180, west 270),
which is the same clockwise turn Facing.rotateLocal makes. Real models made in Blockbench can
replace any file here under the same name.

Run from anywhere:  python3 art/placeholder/generate_park.py     (needs Pillow and numpy)
"""
import json
import math
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "..", "common", "src", "main", "resources", "assets", "alpineshred"))
NS = "alpineshred"
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
rng = np.random.default_rng(20261105)
written = {"blockstates": 0, "models": 0, "textures": 0}


# ---- files ----------------------------------------------------------------------------------------

def write_json(rel, obj):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=1)
        f.write("\n")


def model(name, obj):
    write_json(f"models/block/{name}.json", obj)
    written["models"] += 1
    return f"{NS}:block/{name}"


def item_model(name, obj):
    write_json(f"models/item/{name}.json", obj)


def blockstate(name, obj):
    write_json(f"blockstates/{name}.json", obj)
    written["blockstates"] += 1


def save_texture(rel, array):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(array, 0, 255).astype(np.uint8), "RGBA").save(path)
    written["textures"] += 1
    return f"{NS}:{rel}"


# ---- textures -------------------------------------------------------------------------------------

def swatch(color, noise=8, size=16):
    """A flat material with a little per-pixel noise."""
    a = np.zeros((size, size, 4), float)
    a[..., :3] = color
    a[..., :3] += rng.normal(0, noise, (size, size, 1))
    a[..., 3] = 255
    return a


def bevel(a, light=1.12, dark=0.8):
    """Lighter top and left edge, darker bottom and right: reads as a raised panel."""
    a = a.copy()
    a[0, :, :3] *= light
    a[:, 0, :3] *= light
    a[-1, :, :3] *= dark
    a[:, -1, :3] *= dark
    return a


def pipe_shading(color):
    """A round pipe: bright highlight down the middle, darker toward the edges (both directions)."""
    a = swatch(color, 4)
    for i in range(16):
        f = 0.72 + 0.45 * math.cos((i - 6.5) / 16 * math.pi) ** 2
        a[:, i, :3] *= f
        a[i, :, :3] *= 0.92 + 0.08 * math.cos((i - 6.5) / 16 * math.pi)
    return a


def planks(color):
    a = swatch(color, 6)
    for y in range(0, 16, 4):
        a[y, :, :3] *= 0.72  # plank seams
    for y, x in ((1, 5), (5, 11), (9, 3), (13, 9)):
        a[y:y + 3, x, :3] *= 0.8  # butt joints
    return a


def stripes(c1, c2, width=2):
    a = np.zeros((16, 16, 4), float)
    for y in range(16):
        for x in range(16):
            a[y, x, :3] = c1 if ((x + y) // width) % 2 == 0 else c2
    a[..., 3] = 255
    return a


def snow_top():
    a = swatch((236, 242, 250), 5)
    a[rng.random((16, 16)) > 0.93, :3] = (252, 254, 255)
    return a


def disc_icon(shape, color):
    """A slope marker sign: white plate with a colored circle, square or diamond."""
    a = swatch((238, 238, 236), 3)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            inside = {"circle": math.hypot(dx, dy) <= 5.2,
                      "square": max(abs(dx), abs(dy)) <= 4.6,
                      "diamond": abs(dx) + abs(dy) <= 6.2}[shape]
            if inside:
                a[y, x, :3] = color
    return bevel(a)


def textures():
    t = {}
    t["pipe_rail"] = save_texture("block/pipe_rail", pipe_shading((168, 174, 184)))
    t["flat_rail"] = save_texture("block/flat_rail", bevel(swatch((120, 126, 136), 6)))
    copper = {"": (196, 108, 72), "exposed_": (168, 122, 98), "weathered_": (104, 150, 112), "oxidized_": (82, 164, 140)}
    for stage, c in copper.items():
        t[stage + "copper_pipe_rail"] = save_texture(f"block/{stage}copper_pipe_rail", pipe_shading(c))
    t["rail_leg"] = save_texture("block/rail_leg", swatch((62, 64, 70), 6))
    t["rail_cap"] = save_texture("block/rail_cap", bevel(swatch((30, 30, 32), 4)))
    t["rail_post"] = save_texture("block/rail_post", pipe_shading((84, 88, 96)))

    t["wood_box_top"] = save_texture("block/wood_box_top", bevel(planks((176, 134, 84))))
    t["wood_box_side"] = save_texture("block/wood_box_side", bevel(planks((150, 110, 66))))
    t["metal_box_top"] = save_texture("block/metal_box_top", bevel(swatch((178, 184, 192), 5)))
    t["metal_box_side"] = save_texture("block/metal_box_side", bevel(swatch((138, 144, 152), 5)))
    t["plastic_box_top"] = save_texture("block/plastic_box_top", bevel(swatch((236, 236, 236), 3), 1.04, 0.86))
    t["plastic_box_side"] = save_texture("block/plastic_box_side", bevel(swatch((214, 214, 214), 3), 1.04, 0.86))
    t["box_end"] = save_texture("block/box_end", stripes((236, 196, 40), (32, 32, 34)))

    t["park_ramp_top"] = save_texture("block/park_ramp_top", snow_top())
    t["park_ramp_side"] = save_texture("block/park_ramp_side", planks((160, 120, 76)))
    t["park_ramp_lip"] = save_texture("block/park_ramp_lip", stripes((214, 44, 40), (244, 244, 244), 3))
    t["quarter_pipe_surface"] = save_texture("block/quarter_pipe_surface", snow_top())
    t["quarter_pipe_side"] = save_texture("block/quarter_pipe_side", planks((150, 112, 72)))

    coping_top = swatch((196, 196, 192), 5)  # concrete deck
    coping_top[13:16, :, :3] = pipe_shading((170, 176, 186))[13:16, :, :3]
    t["pipe_coping_top"] = save_texture("block/pipe_coping_top", coping_top)
    coping_front = swatch((186, 186, 182), 5)
    coping_front[0:3, :, :3] = pipe_shading((170, 176, 186))[0:3, :, :3]
    t["pipe_coping_front"] = save_texture("block/pipe_coping_front", coping_front)
    t["pipe_coping_side"] = save_texture("block/pipe_coping_side", swatch((186, 186, 182), 5))

    t["marker_post"] = save_texture("block/marker_post", swatch((60, 62, 66), 4))
    t["slope_marker_green"] = save_texture("block/slope_marker_green", disc_icon("circle", (40, 150, 60)))
    t["slope_marker_blue"] = save_texture("block/slope_marker_blue", disc_icon("square", (40, 90, 200)))
    t["slope_marker_black"] = save_texture("block/slope_marker_black", disc_icon("diamond", (24, 24, 26)))
    cloth = swatch((206, 40, 40), 5)
    cloth[6:10, 3:13, :3] = (244, 244, 244)
    t["park_flag_cloth"] = save_texture("block/park_flag_cloth", cloth)
    t["start_gate_post"] = save_texture("block/start_gate_post", planks((120, 90, 58)))
    t["start_gate_bar"] = save_texture("block/start_gate_bar", stripes((236, 120, 30), (34, 34, 36), 3))
    return t


def icon(name, painter):
    a = np.zeros((16, 16, 4), float)
    painter(a)
    save_texture(f"item/{name}", a)
    item_model(name, {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})


def rail_icon(color, flat):
    def paint(a):
        # A rail across the icon, low left to high right, on two legs.
        for x in range(1, 15):
            y = 11 - (x - 1) * 6 // 13
            thick = 2 if flat else 3
            for k in range(thick):
                shade = 1.25 if k == 0 else 0.85 if k == thick - 1 else 1.0
                a[y + k, x, :3] = np.clip(np.array(color) * shade, 0, 255)
                a[y + k, x, 3] = 255
        for x in (4, 11):
            top = 11 - (x - 1) * 6 // 13 + 3
            a[top:15, x, :3] = (62, 64, 70)
            a[top:15, x, 3] = 255
    return paint


def pipe_icon(a):
    # The quarter pipe's profile: an arc rising to the right, filled below, with the coping on top.
    r = 13.0
    for x in range(1, 15):
        u = x - 1
        h = r - math.sqrt(max(0.0, r * r - u * u))
        top = int(round(15 - h))
        for y in range(max(1, top), 16):
            surface = y == max(1, top)
            a[y, x, :3] = (236, 242, 250) if surface else (150, 112, 72) if (x + y) % 5 else (126, 92, 58)
            a[y, x, 3] = 255
    a[0:2, 13:16, :3] = (170, 176, 186)
    a[0:2, 13:16, 3] = 255


# ---- geometry helpers -----------------------------------------------------------------------------

def element(frm, to, textures, faces=("north", "east", "south", "west", "up", "down"), tint=None):
    frm = [round(max(0.0, min(16.0, v)), 4) for v in frm]
    to = [round(max(0.0, min(16.0, v)), 4) for v in to]
    out = {}
    for face in faces:
        tex = textures.get(face, textures.get("side")) if isinstance(textures, dict) else textures
        f = {"texture": tex}
        if tint is not None:
            f["tintindex"] = tint
        out[face] = f
    return {"from": frm, "to": to, "faces": out}


def rail_points(shape):
    """North-facing path points in pixels (RailPath.authored)."""
    if shape == "flat":
        return [(8, 8, 16), (8, 8, 0)]
    if shape == "rise_a":
        return [(8, 8, 16), (8, 16, 0)]
    if shape == "rise_b":
        return [(8, 0, 16), (8, 8, 0)]
    pts = []
    for i in range(6):
        phi = math.radians(180 + 90.0 * i / 5)
        x, z = 16 + 8 * math.cos(phi), 16 + 8 * math.sin(phi)
        if i == 0:
            x = 8
        if i == 5:
            z = 8
        pts.append((x, 8, z))
    return pts


def resample(points, count):
    """Points every 1/count of the path length."""
    seg = [math.dist(points[i], points[i + 1]) for i in range(len(points) - 1)]
    total = sum(seg)
    out = []
    for k in range(count + 1):
        s = total * k / count
        i = 0
        while i < len(seg) - 1 and s > seg[i]:
            s -= seg[i]
            i += 1
        t = 0 if seg[i] == 0 else min(1.0, s / seg[i])
        out.append(tuple(points[i][j] + (points[i + 1][j] - points[i][j]) * t for j in range(3)))
    return out


def rail_bar(shape, half_width, height, tex):
    """Like ParkShapes.bar: boxes between path samples, the top on the path."""
    pts = rail_points(shape)
    straight = shape == "flat"
    samples = pts if straight else resample(pts, 8)
    curve = shape == "curve"
    els = []
    for p0, p1 in zip(samples, samples[1:]):
        top = max(p0[1], p1[1])
        x0, x1 = min(p0[0], p1[0]), max(p0[0], p1[0])
        z0, z1 = min(p0[2], p1[2]), max(p0[2], p1[2])
        along_z = abs(p1[2] - p0[2]) >= abs(p1[0] - p0[0])
        if along_z or curve:
            x0, x1 = x0 - half_width, x1 + half_width
        if not along_z or curve:
            z0, z1 = z0 - half_width, z1 + half_width
        bottom = max(0.0, top - height)
        top = max(top, bottom + 0.25)
        els.append(element((x0, bottom, z0), (x1, top, z1), "#rail"))
    return els


def rail_legs(shape):
    """One leg under the middle of the piece: a line of them reads as a rail on stands, not a fence."""
    pts = resample(rail_points(shape), 2)
    els = []
    for p in (pts[1],):
        bottom = p[1] - 2
        if bottom < 1.5:
            continue
        els.append(element((p[0] - 1, 0, p[2] - 1), (p[0] + 1, bottom, p[2] + 1), "#leg"))
    return els


def rail_cap(shape, end):
    pts = rail_points(shape)
    p = pts[0] if end == "a" else pts[-1]
    q = pts[1] if end == "a" else pts[-2]
    y0, y1 = p[1] - 2.5, p[1] + 0.5
    if abs(p[2] - q[2]) >= abs(p[0] - q[0]):  # end on a north or south edge
        z0, z1 = (p[2] - 1, p[2]) if p[2] >= 8 else (p[2], p[2] + 1)
        return [element((p[0] - 1.5, y0, z0), (p[0] + 1.5, y1, z1), "#cap")]
    x0, x1 = (p[0] - 1, p[0]) if p[0] >= 8 else (p[0], p[0] + 1)
    return [element((x0, y0, p[2] - 1.5), (x1, y1, p[2] + 1.5), "#cap")]


# ---- rails (section 10) ---------------------------------------------------------------------------

SHAPES = ("flat", "rise_a", "rise_b", "curve")


def rails(t):
    looks = {"pipe": (1.0, 2.0), "bar": (2.0, 1.0)}  # half width and height in pixels, as the collision
    for look, (hw, h) in looks.items():
        for shape in SHAPES:
            model(f"template_rail_{look}_{shape}", {"parent": "minecraft:block/block", "textures": {"particle": "#rail"},
                                                    "elements": rail_bar(shape, hw, h, "#rail")})
    for shape in SHAPES:
        model(f"rail_legs_{shape}", {"textures": {"leg": t["rail_leg"], "particle": t["rail_leg"]}, "elements": rail_legs(shape)})
        for end in ("a", "b"):
            model(f"rail_cap_{end}_{shape}", {"textures": {"cap": t["rail_cap"], "particle": t["rail_cap"]}, "elements": rail_cap(shape, end)})

    blocks = {"pipe_rail": ("pipe", "pipe_rail"), "flat_rail": ("bar", "flat_rail")}
    for stage in ("", "exposed_", "weathered_", "oxidized_"):
        blocks[stage + "copper_pipe_rail"] = ("pipe", stage + "copper_pipe_rail")
        blocks["waxed_" + stage + "copper_pipe_rail"] = ("pipe", stage + "copper_pipe_rail")
    for block, (look, skin) in blocks.items():
        if block == skin:
            for shape in SHAPES:
                model(f"{skin}_{shape}", {"parent": f"{NS}:block/template_rail_{look}_{shape}",
                                          "textures": {"rail": t[skin], "particle": t[skin]}})
        parts = []
        for shape in SHAPES:
            for facing, y in FACINGS.items():
                apply = lambda m: {"model": m, "y": y} if y else {"model": m}
                parts.append({"when": {"shape": shape, "facing": facing}, "apply": apply(f"{NS}:block/{skin}_{shape}")})
                parts.append({"when": {"shape": shape, "facing": facing, "connected_a": "false"}, "apply": apply(f"{NS}:block/rail_cap_a_{shape}")})
                parts.append({"when": {"shape": shape, "facing": facing, "connected_b": "false"}, "apply": apply(f"{NS}:block/rail_cap_b_{shape}")})
                parts.append({"when": {"shape": shape, "facing": facing, "legs": "true"}, "apply": apply(f"{NS}:block/rail_legs_{shape}")})
        blockstate(block, {"multipart": parts})
        icon_name = block
        color = {"pipe_rail": (168, 174, 184), "flat_rail": (120, 126, 136)}.get(skin)
        if color is None:
            color = {"copper_pipe_rail": (196, 108, 72), "exposed_copper_pipe_rail": (168, 122, 98),
                     "weathered_copper_pipe_rail": (104, 150, 112), "oxidized_copper_pipe_rail": (82, 164, 140)}[skin]
        icon(icon_name, rail_icon(color, look == "bar"))

    post = model("rail_post", {"parent": "minecraft:block/block", "textures": {"post": t["rail_post"], "particle": t["rail_post"]},
                               "elements": [element((6, 0, 6), (10, 15, 10), "#post"), element((5, 15, 5), (11, 16, 11), "#post")]})
    blockstate("rail_post", {"variants": {"": {"model": post}}})
    item_model("rail_post", {"parent": post})


# ---- boxes (section 11) ---------------------------------------------------------------------------

def boxes(t):
    for name in ("wood_box", "metal_box", "plastic_box"):
        tint = 0 if name == "plastic_box" else None
        tex = {"top": t[name + "_top"], "side": t[name + "_side"], "end": t["box_end"], "particle": t[name + "_side"]}
        variants = {}
        for half, h in (("low", 8), ("full", 16)):
            for end_a in (False, True):
                for end_b in (False, True):
                    # Authored along z: end_a is the north (negative) end, end_b the south. Open ends
                    # bevel by a two-pixel step; collision stays full (BoxBlock).
                    z0 = 1 if end_a else 0
                    z1 = 15 if end_b else 16
                    els = [element((0, 0, z0), (16, h, z1),
                                   {"up": "#top", "down": "#side", "side": "#side",
                                    "north": "#end" if end_a else "#side", "south": "#end" if end_b else "#side"}, tint=tint)]
                    if end_a:
                        els.append(element((0, 0, 0), (16, h - 2, 1), {"up": "#top", "side": "#end"}, tint=tint))
                    if end_b:
                        els.append(element((0, 0, 15), (16, h - 2, 16), {"up": "#top", "side": "#end"}, tint=tint))
                    m = model(f"{name}_{half}_{'a' if end_a else 'x'}{'b' if end_b else 'x'}",
                              {"parent": "minecraft:block/block", "textures": tex, "elements": els})
                    for axis, y in (("z", 0), ("x", 270)):
                        key = f"axis={axis},end_a={str(end_a).lower()},end_b={str(end_b).lower()},half={half}"
                        variants[key] = {"model": m, "y": y} if y else {"model": m}
        blockstate(name, {"variants": variants})
        item_model(name, {"parent": f"{NS}:block/{name}_full_ab"})


# ---- ramps (section 11) ---------------------------------------------------------------------------

SLOPES = {"quarter": (0.25, 3), "half": (0.5, 1), "full": (1.0, 0)}


def ramps(t):
    tex = {"top": t["park_ramp_top"], "side": t["park_ramp_side"], "lip": t["park_ramp_lip"], "particle": t["park_ramp_side"]}
    variants = {}
    for slope, (rise, max_step) in SLOPES.items():
        for step in range(max_step + 1):
            for lip in (False, True):
                els = []
                for col in range(8):
                    top = min(16.0, rise * 16 * (step + (col + 1) / 8))
                    z1 = 16 - col * 2
                    lip_col = lip and col == 7
                    faces = {"up": "#lip" if lip_col else "#top", "north": "#lip" if lip_col else "#top",
                             "down": "#side", "side": "#side"}
                    els.append(element((0, 0, z1 - 2), (16, top, z1), faces))
                name = f"park_ramp_{slope}_{step}" + ("_lip" if lip else "")
                m = model(name, {"parent": "minecraft:block/block", "textures": tex, "elements": els})
                for s in range(4):
                    if min(s, max_step) != step:
                        continue
                    for facing, y in FACINGS.items():
                        key = f"facing={facing},lip={str(lip).lower()},slope={slope},step={s}"
                        variants[key] = {"model": m, "y": y} if y else {"model": m}
    blockstate("park_ramp", {"variants": variants})
    item_model("park_ramp", {"parent": f"{NS}:block/park_ramp_half_0"})


# ---- quarter pipes (section 11) -------------------------------------------------------------------

def arc_height(r, u):
    c = max(0.0, min(u, r))
    return r - math.sqrt(r * r - c * c)


def pipe_cells(r):
    cells = []
    for i in range(r):
        top = arc_height(r, i + 1)
        for j in range(r):
            if top > j + 1e-9:
                cells.append((i, j))
    return cells


def pipes(t):
    tex = {"surface": t["quarter_pipe_surface"], "side": t["quarter_pipe_side"], "particle": t["quarter_pipe_side"]}
    variants = {}
    for r in (3, 5):
        cells = pipe_cells(r)
        for index in range(len(cells)):
            i, j = cells[index]
            els = []
            for col in range(16):
                top = max(0.0, min(1.0, arc_height(r, i + col / 16) - j)) * 16
                if top <= 0:
                    continue
                z1 = 16 - col
                els.append(element((0, 0, z1 - 1), (16, top, z1), {"up": "#surface", "south": "#surface", "side": "#side"}))
            if not els:
                els.append(element((0, 0, 0), (16, 0.25, 16), {"up": "#surface", "side": "#side"}))
            m = model(f"quarter_pipe_{r}_{index}", {"parent": "minecraft:block/block", "textures": tex, "elements": els})
            for idx in range(10):
                if min(idx, len(cells) - 1) != index:
                    continue
                for facing, y in FACINGS.items():
                    key = f"facing={facing},index={idx},radius={r}"
                    variants[key] = {"model": m, "y": y} if y else {"model": m}
    blockstate("quarter_pipe", {"variants": variants})
    icon("quarter_pipe", pipe_icon)


# ---- coping and decor -----------------------------------------------------------------------------

def coping_and_decor(t):
    m = model("pipe_coping", {"parent": "minecraft:block/block",
                              "textures": {"top": t["pipe_coping_top"], "front": t["pipe_coping_front"], "side": t["pipe_coping_side"],
                                           "particle": t["pipe_coping_side"]},
                              "elements": [element((0, 0, 0), (16, 16, 16), {"up": "#top", "south": "#front", "side": "#side"})]})
    facing_variants("pipe_coping", m)
    item_model("pipe_coping", {"parent": m})

    for color in ("green", "blue", "black"):
        name = f"slope_marker_{color}"
        m = model(name, {"parent": "minecraft:block/block",
                         "textures": {"post": t["marker_post"], "sign": t[name], "particle": t[name]},
                         "elements": [element((7, 0, 7), (9, 16, 9), "#post"),
                                      element((4, 10, 7), (12, 15, 9), {"north": "#sign", "south": "#sign", "side": "#post"})]})
        facing_variants(name, m)
        item_model(name, {"parent": m})

    m = model("park_flag", {"parent": "minecraft:block/block",
                            "textures": {"post": t["marker_post"], "cloth": t["park_flag_cloth"], "particle": t["park_flag_cloth"]},
                            "elements": [element((7, 0, 7), (9, 16, 9), "#post"),
                                         element((9, 8, 8), (16, 15, 8), {"north": "#cloth", "south": "#cloth"}, faces=("north", "south"))]})
    facing_variants("park_flag", m)
    item_model("park_flag", {"parent": m})

    m = model("start_gate", {"parent": "minecraft:block/block",
                             "textures": {"post": t["start_gate_post"], "bar": t["start_gate_bar"], "particle": t["start_gate_post"]},
                             "elements": [element((0, 0, 7), (2, 16, 9), "#post"), element((14, 0, 7), (16, 16, 9), "#post"),
                                          element((2, 13, 7), (14, 16, 9), "#bar")]})
    facing_variants("start_gate", m)
    item_model("start_gate", {"parent": m})


def facing_variants(name, m):
    blockstate(name, {"variants": {f"facing={f}": ({"model": m, "y": y} if y else {"model": m}) for f, y in FACINGS.items()}})


def main():
    t = textures()
    rails(t)
    boxes(t)
    ramps(t)
    pipes(t)
    coping_and_decor(t)
    print(f"wrote {written['blockstates']} blockstates, {written['models']} block models, {written['textures']} textures")


if __name__ == "__main__":
    main()
