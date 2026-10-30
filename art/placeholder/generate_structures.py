#!/usr/bin/env python3
"""
Structure templates for world generation (design doc section 12): the terrain park's jigsaw pieces
(entry, lines, ends), the natural snow features, and the resort lodge, written as vanilla
structure NBT into data/alpineshred/structures/. Re-run after editing:
    python3 art/placeholder/generate_structures.py

Park pieces run downhill toward +z (south) and are 9 wide (x 0 to 8). Each starts with its
in-jigsaw at (4, 1, 0) facing north and ends with an out-jigsaw at (4, 0, L - 1) facing south, so
the next piece starts one block lower: the snow pad drops one block over the first 8 blocks of
every piece (in snow layers, an eighth a block) and the feature sits on the flat after it.
"""
from __future__ import annotations

import gzip
import io
import math
import os
import struct

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "common", "src", "main", "resources", "data", "alpineshred", "structures"))
DATA_VERSION = 3465  # 1.20.1


# ---- NBT --------------------------------------------------------------------------------------------

class Int(int):
    pass


class Byte(int):
    pass


def _name(out, s):
    b = s.encode("utf-8")
    out.write(struct.pack(">H", len(b)))
    out.write(b)


def _payload(out, v):
    if isinstance(v, Byte):
        out.write(struct.pack(">b", v))
    elif isinstance(v, bool):
        out.write(struct.pack(">b", 1 if v else 0))
    elif isinstance(v, int):
        out.write(struct.pack(">i", v))
    elif isinstance(v, str):
        _name(out, v)
    elif isinstance(v, list):
        t = _tag(v[0]) if v else 0
        out.write(struct.pack(">bi", t, len(v)))
        for e in v:
            _payload(out, e)
    elif isinstance(v, dict):
        for k, e in v.items():
            out.write(struct.pack(">b", _tag(e)))
            _name(out, k)
            _payload(out, e)
        out.write(b"\x00")
    else:
        raise TypeError(v)


def _tag(v):
    if isinstance(v, (Byte, bool)):
        return 1
    if isinstance(v, int):
        return 3
    if isinstance(v, str):
        return 8
    if isinstance(v, list):
        return 9
    if isinstance(v, dict):
        return 10
    raise TypeError(v)


def write_nbt(path, root):
    buf = io.BytesIO()
    buf.write(b"\x0a")
    _name(buf, "")
    _payload(buf, root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as f:
        f.write(buf.getvalue())


# ---- templates --------------------------------------------------------------------------------------

def state(name, **props):
    return (name if ":" in name else "minecraft:" + name, tuple(sorted((k, str(v).lower()) for k, v in props.items())))


AIR = state("air")
SNOW = state("snow_block")


def layers(n):
    return SNOW if n >= 8 else state("snow", layers=n)


class Template:
    def __init__(self, w, h, l):
        self.size = (w, h, l)
        self.blocks = {}

    def set(self, x, y, z, st, nbt=None):
        w, h, l = self.size
        assert 0 <= x < w and 0 <= y < h and 0 <= z < l, (x, y, z, self.size)
        self.blocks[(x, y, z)] = (st, nbt)

    def fill(self, x0, y0, z0, x1, y1, z1, st):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, st)

    def save(self, rel, air=True):
        w, h, l = self.size
        if air:
            for x in range(w):
                for y in range(h):
                    for z in range(l):
                        self.blocks.setdefault((x, y, z), (AIR, None))
        palette, index, blocks = [], {}, []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            st, nbt = self.blocks[pos]
            if st not in index:
                index[st] = len(palette)
                entry = {"Name": st[0]}
                if st[1]:
                    entry["Properties"] = {k: v for k, v in st[1]}
                palette.append(entry)
            b = {"state": Int(index[st]), "pos": [Int(pos[0]), Int(pos[1]), Int(pos[2])]}
            if nbt is not None:
                b["nbt"] = nbt
            blocks.append(b)
        write_nbt(os.path.join(OUT, rel + ".nbt"), {
            "DataVersion": Int(DATA_VERSION),
            "size": [Int(w), Int(h), Int(l)],
            "palette": palette,
            "blocks": blocks,
            "entities": [],
        })


def jigsaw(t, x, y, z, facing, name, target, pool):
    t.set(x, y, z, state("jigsaw", orientation=facing + "_up"), {
        "id": "minecraft:jigsaw",
        "name": name,
        "target": target,
        "pool": pool,
        "joint": "aligned",
        "final_state": "minecraft:snow_block",
    })


W = 9


def park_piece(length, height=8, start_top=2):
    """A park piece: the snow pad dropping one block over z 0 to 7 (top 2 to top 1), flat after."""
    t = Template(W, height, length)
    for z in range(length):
        for x in range(W):
            t.set(x, 0, z, SNOW)
            drop = max(0, 8 - z) if start_top == 2 else 0
            if drop > 0:
                t.set(x, 1, z, layers(drop))
    jigsaw(t, 4, 1, 0, "north", "alpineshred:park_in", "minecraft:empty", "minecraft:empty")
    jigsaw(t, 4, 0, length - 1, "south", "alpineshred:park_out", "alpineshred:park_in", "alpineshred:park/lines")
    return t


# Rails: facing north puts end A on the south edge and end B on the north edge; facing east, A west
# and B east. Curves (authored south edge to east edge) turn with the facing.
CURVE_EDGES = {"north": ("south", "east"), "east": ("west", "south"), "south": ("north", "west"), "west": ("east", "north")}
STRAIGHT_EDGES = {"north": ("south", "north"), "south": ("north", "south"), "east": ("west", "east"), "west": ("east", "west")}
STEP = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


def rail_line(t, pieces, block="alpineshred:pipe_rail"):
    """pieces: [(x, y, z, shape, facing)] in order along the line; sets connected and legs."""
    occupied = {(p[0], p[2]) for p in pieces}
    for i, (x, y, z, shape, facing) in enumerate(pieces):
        a, b = (CURVE_EDGES if shape == "curve" else STRAIGHT_EDGES)[facing]
        def joined(edge):
            dx, dz = STEP[edge]
            return (x + dx, z + dz) in occupied
        below = t.blocks.get((x, y - 1, z), (AIR, None))[0]
        # Legs down to a solid top face; none over a post (it holds the rail) or over air or layers.
        legs = below[0] not in ("alpineshred:rail_post", "minecraft:air", "minecraft:snow") and "fence" not in below[0]
        t.set(x, y, z, state(block, facing=facing, shape=shape, connected_a=joined(a), connected_b=joined(b), legs=legs, waxed=False))


def box_row(t, x, y, z0, z1, block, half="full", **extra):
    for z in range(z0, z1 + 1):
        t.set(x, y, z, state(block, axis="z", half=half, end_a=(z == z0), end_b=(z == z1), waxed=False, **extra))


def main():
    # Entry: a raised start with the gate and two flags, then down onto the first line.
    t = Template(W, 8, 12)
    for z in range(12):
        for x in range(W):
            t.set(x, 0, z, SNOW)
            if z < 4:
                t.set(x, 1, z, SNOW)
            elif z < 11:
                t.set(x, 1, z, layers(8 - (z - 3)))
    t.set(4, 2, 1, state("alpineshred:start_gate", facing="south"))
    t.set(0, 2, 2, state("alpineshred:park_flag", facing="south"))
    t.set(8, 2, 2, state("alpineshred:park_flag", facing="south"))
    jigsaw(t, 4, 0, 11, "south", "alpineshred:park_out", "alpineshred:park_in", "alpineshred:park/lines")
    t.save("park/entry_gate")

    # A ten-piece flat rail.
    t = park_piece(22)
    rail_line(t, [(4, 1, z, "flat", "north") for z in range(9, 19)])
    t.save("park/rail_flat")

    # A kinked rail: high on posts, a step down, low.
    t = park_piece(22)
    for z in range(9, 13):
        t.set(4, 1, z, state("alpineshred:rail_post"))
    pieces = [(4, 2, z, "flat", "north") for z in range(9, 12)] + [(4, 2, 12, "rise_b", "north"), (4, 1, 13, "rise_a", "north")]
    pieces += [(4, 1, z, "flat", "north") for z in range(14, 19)]
    rail_line(t, pieces)
    t.save("park/rail_kinked")

    # A curved rail: an S across the line.
    t = park_piece(22)
    pieces = [(4, 1, 9, "flat", "north"), (4, 1, 10, "flat", "north"), (4, 1, 11, "curve", "west"), (5, 1, 11, "flat", "east"),
              (6, 1, 11, "curve", "east")]
    pieces += [(6, 1, z, "flat", "north") for z in range(12, 16)]
    pieces += [(6, 1, 16, "curve", "south"), (5, 1, 16, "flat", "east"), (4, 1, 16, "curve", "north"), (4, 1, 17, "flat", "north"),
               (4, 1, 18, "flat", "north")]
    rail_line(t, pieces)
    t.save("park/rail_curved")

    # A box line: a full wood box, then a low metal one.
    t = park_piece(22)
    box_row(t, 4, 1, 9, 12, "alpineshred:wood_box")
    box_row(t, 4, 1, 15, 19, "alpineshred:metal_box", half="low")
    t.save("park/box_line")

    # A kicker line: a half-slope kicker with a lip onto a table, then the landing.
    t = park_piece(26)
    for x in range(1, 8):
        t.set(x, 1, 10, state("alpineshred:park_ramp", facing="south", slope="half", step=0, lip=False))
        t.set(x, 1, 11, state("alpineshred:park_ramp", facing="south", slope="half", step=1, lip=True))
        for z in range(12, 15):
            t.set(x, 1, z, SNOW)
        for z in range(15, 23):
            t.set(x, 1, z, layers(8 - (z - 15)))
    t.save("park/kicker_line")

    # A pipe pair: radius-3 quarter pipes on both sides of a three-wide channel.
    t = park_piece(22)
    cells = []
    for i in range(3):
        top = 3 - math.sqrt(9 - (i + 1) ** 2)
        for j in range(3):
            if top > j + 1e-9:
                cells.append((i, j))
    for z in range(9, 21):
        for index, (i, j) in enumerate(cells):
            t.set(2 - i, 1 + j, z, state("alpineshred:quarter_pipe", facing="west", radius=3, index=index))
            t.set(6 + i, 1 + j, z, state("alpineshred:quarter_pipe", facing="east", radius=3, index=index))
    t.save("park/pipe_pair")

    # A jib line: a low wood box and a short flat rail off line.
    t = park_piece(22)
    box_row(t, 4, 1, 9, 11, "alpineshred:wood_box", half="low")
    rail_line(t, [(4, 1, z, "flat", "north") for z in range(14, 18)], block="alpineshred:flat_rail")
    t.save("park/jib_line")

    # Ends: a bench and a fence; flags.
    t = park_piece(12)
    t.blocks.pop((4, 0, 11))  # no way on from an end
    t.set(4, 0, 11, SNOW)
    for x in (2, 3, 5, 6):
        t.set(x, 1, 9, state("spruce_stairs", facing="south", half="bottom", shape="straight", waterlogged=False))
    for x in range(W):
        t.set(x, 1, 11, state("spruce_fence", north=False, south=False, east=(x < W - 1), west=(x > 0), waterlogged=False))
    t.save("park/end_bench")
    t = park_piece(12)
    t.blocks.pop((4, 0, 11))
    t.set(4, 0, 11, SNOW)
    for x in (0, 2, 4, 6, 8):
        t.set(x, 1, 10, state("alpineshred:park_flag", facing="north"))
    t.save("park/end_flags")

    snow_features()
    lodge()


def snow_features():
    # A natural kicker: snow rising two blocks over six, five wide.
    t = Template(7, 4, 9)
    for z in range(9):
        for x in range(7):
            t.set(x, 0, z, SNOW)
    for x in range(1, 6):
        for z in range(1, 7):
            eighths = (z) * 16 // 6  # up to two blocks
            if x in (1, 5):
                eighths = max(1, eighths - 4)
            full, part = divmod(eighths, 8)
            for y in range(1, 1 + full):
                t.set(x, y, z, SNOW)
            if part:
                t.set(x, 1 + full, z, layers(part))
    t.save("snow/kicker", air=False)

    # A roller: up one block and down again over ten.
    t = Template(7, 3, 12)
    for z in range(12):
        for x in range(7):
            t.set(x, 0, z, SNOW)
    for x in range(7):
        for z in range(1, 11):
            eighths = round(8 * math.sin(math.pi * (z - 0.5) / 10))
            if x in (0, 6):
                eighths = eighths // 2
            if eighths >= 8:
                t.set(x, 1, z, SNOW)
            elif eighths > 0:
                t.set(x, 1, z, layers(eighths))
    t.save("snow/roller", air=False)

    # A cornice: a short snow wall with an overhanging lip.
    t = Template(9, 5, 7)
    for z in range(7):
        for x in range(9):
            t.set(x, 0, z, SNOW)
    for x in range(9):
        for z in range(0, 4):
            for y in range(1, 4):
                t.set(x, y, z, SNOW)
        t.set(x, 3, 4, SNOW)  # the lip
        t.set(x, 4, 1, layers(3))
        t.set(x, 4, 2, layers(2))
    t.save("snow/cornice", air=False)


def lodge():
    """The resort lodge: 24 by 18, spruce, its double door in the middle of the north wall (z = 0)."""
    w, d, h = 24, 18, 6 + 18 // 2
    t = Template(w, h, d)
    planks, log = state("spruce_planks"), state("spruce_log", axis="y")
    t.fill(0, 0, 0, w - 1, 0, d - 1, state("stone_bricks"))
    t.fill(1, 1, 1, w - 2, 1, d - 2, planks)  # floor
    # Walls, logs at the corners and every 6 blocks.
    for y in range(1, 6):
        for x in range(w):
            for z in (0, d - 1):
                t.set(x, y, z, log if x % 6 == 0 or x == w - 1 else planks)
        for z in range(d):
            for x in (0, w - 1):
                t.set(x, y, z, log if z % 6 == 0 or z == d - 1 else planks)
    # Windows.
    for x in range(2, w - 2):
        if x % 6 in (2, 3) and not (10 <= x <= 13):
            for z in (0, d - 1):
                t.set(x, 3, z, state("glass_pane", east=True, west=True, north=False, south=False, waterlogged=False))
    for z in range(2, d - 2):
        if z % 6 in (2, 3):
            for x in (0, w - 1):
                t.set(x, 3, z, state("glass_pane", north=True, south=True, east=False, west=False, waterlogged=False))
    # The door.
    for x, hinge in ((11, "left"), (12, "right")):
        t.set(x, 2, 0, state("spruce_door", facing="north", half="lower", hinge=hinge, open=False, powered=False))
        t.set(x, 3, 0, state("spruce_door", facing="north", half="upper", hinge=hinge, open=False, powered=False))
    t.set(11, 4, 0, planks)
    t.set(12, 4, 0, planks)
    # A plank ceiling over the room, then the roof: spruce stairs stepping up from both long walls
    # to the ridge, with plank gables.
    t.fill(1, 6, 1, w - 2, 6, d - 2, planks)
    for step in range(d // 2):
        y = 6 + step
        for x in range(w):
            t.set(x, y, step, state("spruce_stairs", facing="south", half="bottom", shape="straight", waterlogged=False))
            t.set(x, y, d - 1 - step, state("spruce_stairs", facing="north", half="bottom", shape="straight", waterlogged=False))
        for z in range(step + 1, d - 1 - step):
            for x in (0, w - 1):
                t.set(x, y, z, planks)
    # Inside: a fireplace on the south wall, benches, tables, lanterns, and the chest's data marker.
    for x in range(10, 14):
        t.set(x, 1, d - 2, state("stone_bricks"))
        for y in range(2, 6):
            t.set(x, y, d - 2, state("stone_bricks") if x in (10, 13) or y > 3 else AIR)
    t.set(11, 2, d - 2, state("campfire", facing="north", lit=True, signal_fire=False, waterlogged=False))
    t.set(12, 2, d - 2, state("campfire", facing="north", lit=True, signal_fire=False, waterlogged=False))
    for (cx, cz) in ((5, 5), (18, 5), (5, 11), (18, 11)):
        t.set(cx, 2, cz, state("spruce_fence", north=False, south=False, east=False, west=False, waterlogged=False))
        t.set(cx, 3, cz, state("spruce_pressure_plate", powered=False))
        t.set(cx - 1, 2, cz, state("spruce_stairs", facing="east", half="bottom", shape="straight", waterlogged=False))
        t.set(cx + 1, 2, cz, state("spruce_stairs", facing="west", half="bottom", shape="straight", waterlogged=False))
    for (lx, lz) in ((6, 8), (17, 8), (12, 4)):
        t.set(lx, 5, lz, state("lantern", hanging=True, waterlogged=False))
    # The chest's data marker, against the south wall (the piece turns it into a chest facing the room).
    t.set(2, 2, d - 2, state("structure_block", mode="data"), {
        "id": "minecraft:structure_block", "mode": "DATA", "metadata": "lodge_chest", "name": "", "author": "",
        "rotation": "NONE", "mirror": "NONE", "ignoreEntities": Byte(1), "powered": Byte(0), "showair": Byte(0),
        "showboundingbox": Byte(1)})
    # Wax and boards on display: a crafting corner.
    t.set(w - 3, 2, d - 3, state("alpineshred:paint_bench"))
    t.save("resort/lodge")


if __name__ == "__main__":
    main()
    print("structures written to", OUT)
