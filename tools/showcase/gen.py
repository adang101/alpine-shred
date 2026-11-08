#!/usr/bin/env python3
"""
Generates the Alpine Shred showcase world as a data pack of plain commands (fill, setblock, place,
item, ...), plus the lift node list that ShowcaseWorld links (lifts cannot be linked by a command).

    python tools/showcase/gen.py <server run dir>      (default forge/build/showcase-server)
    ./gradlew :forge:runShowcaseServer

The world is a superflat snowy_slopes world (ground top block at y = -60) with two areas:

  Summit Mountain (x -110..110, z -280..0): a long groomed run from a summit plateau to the base,
      green, blue, black and rollers sections with slope markers, an ice and blue ice patch, a rock
      scrape, a powder bowl to the west, moguls and tree glades to the east, and Lift 1 (x = 48).
  Terrain Park (x 112..180, z -140..0): a stepped park hill with four terraces and three lanes,
      a jump line (quarter, half and full ramps, lips), a jib line (pipe, flat, copper and waxed
      rails, a kinked rail, rails on posts, a curve, wood, metal and plastic boxes), a radius-5
      halfpipe with coping, a radius-3 quarter pipe at the bottom, and Lift 2 (x = 172).

Between them, the base lodge (x 74..96, z 8..20): spawn, chests with every item, a Paint Bench,
and a button panel (gear kits, strap in, teleports).
"""
import json
import math
import os
import random
import sys

G = -60  # top block of the superflat ground
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join("forge", "build", "showcase-server")

GROOMED = "alpineshred:groomed_snow"
SNOW = "minecraft:snow_block"
POWDER = "minecraft:powder_snow"

S = {}    # (x, z) -> y of the top solid block
TOP = {}  # (x, z) -> top block id
NO_LAYER = set()  # columns that must not get a snow layer (lift corridors, run, park)
late = []  # commands after the terrain
rnd = random.Random(20261108)


def cmd(c):
    late.append(c)


def setb(x, y, z, block):
    cmd(f"setblock {x} {y} {z} {block}")


def props(block, **p):
    if not p:
        return block
    return block + "[" + ",".join(f"{k}={str(v).lower()}" for k, v in p.items()) + "]"


def set_col(x, z, y, top):
    """Sets a column, keeping the higher of what is there (areas overlap at their edges)."""
    y = int(y)
    if y < G:
        y = G
    if (x, z) in S and S[(x, z)] > y:
        return
    S[(x, z)] = y
    TOP[(x, z)] = top if y > G or top != SNOW else SNOW


# ---- Summit Mountain ------------------------------------------------------------------------------

# From the base north (uphill): (rows, drop per row, name).
MOUNTAIN = [(20, 0.0, "base"), (35, 0.15, "green runout"), (40, 0.30, "blue cruiser"), (30, 0.33, "rollers"),
            (24, 0.70, "black wall"), (30, 0.33, "blue upper"), (25, 1 / 6, "green summit"), (25, 0.0, "summit")]
P = {0: 0.0}
SEG_START = {}  # name -> z of its downhill end
z, h = 0, 0.0
for rows, grade, name in MOUNTAIN:
    SEG_START[name] = z
    for k in range(rows):
        z -= 1
        h += grade
        if name == "rollers":
            env = math.sin(math.pi * (k + 1) / rows)
            P[z] = h + 0.8 * env * math.sin(2 * math.pi * (k + 1) / 10)
        else:
            P[z] = h
SUMMIT_Z = z  # -229
SUMMIT = P[SUMMIT_Z]
RUN_HALF = 13
LIFT1_X = 48


def mountain_h(x, z):
    if z > 0:
        return 0.0
    if z < SUMMIT_Z:
        core = SUMMIT - (SUMMIT_Z - z) * 1.3
    else:
        core = P[z]
    ax = abs(x)
    if ax > 56:
        core -= (ax - 56) * 1.3
    elif -40 <= x < -RUN_HALF - 1 and -175 <= z <= -65:
        core -= 1  # the powder bowl sits a block lower
    return core


for x in range(-110, 111):
    for z in range(-280, 1):
        v = mountain_h(x, z)
        if v < 0:
            continue
        top = SNOW
        if abs(x) <= RUN_HALF and z <= 0:
            top = GROOMED
        elif -40 <= x < -RUN_HALF - 1 and -175 <= z <= -65:
            top = POWDER
        set_col(x, z, G + math.floor(v + 1e-9), top)

# Surface variety on the run.
for x in range(-RUN_HALF, -3):
    for z in range(-149, -139):
        TOP[(x, z)] = "minecraft:packed_ice"      # an ice sheet at the top of the black wall
for x in range(4, RUN_HALF + 1):
    for z in range(-75, -68):
        TOP[(x, z)] = "minecraft:blue_ice"        # a blue ice strip on the cruiser
for x in range(-RUN_HALF, -5):
    for z in range(-45, -40):
        TOP[(x, z)] = "minecraft:ice"             # plain ice on the runout
for x in range(8, RUN_HALF + 1):
    for z in range(-165, -158):
        TOP[(x, z)] = "minecraft:stone"           # a rock scrape (no surface profile: the scrape category)

# ---- Terrain Park ---------------------------------------------------------------------------------

PARK_X0, PARK_X1 = 112, 180
H0 = 19
# From the top deck south: (rows, drop per row, name).
PARK = [(10, 0.0, "T0"), (8, 0.5, "D1"), (22, 0.0, "T1"), (8, 0.5, "D2"), (22, 0.0, "T2"), (8, 0.5, "D3"),
        (22, 0.0, "T3"), (8, 0.5, "D4"), (22, 0.0, "T4"), (6, 0.5, "D5")]
Q = {}
TERRACE = {}  # name -> (z first, z last, height)
z, h = -140, float(H0)
for rows, drop, name in PARK:
    first = z
    for k in range(rows):
        if drop:
            h -= drop
        Q[z] = h
        z += 1
    TERRACE[name] = (first, z - 1, math.floor(h + 1e-9))
PARK_BASE_Z = z  # -4

JUMP = (114, 126, 120)        # lane x range and center
JIB_A, JIB_B = 131, 138       # jib line centers (lane 128..141)
PIPE_FLOOR = (151, 156)
PIPE_R = 5
PIPE_Z0, PIPE_Z1 = -125, -66
LIFT2_X = 172


def pipe_floor(z):
    if z <= -131:
        return H0
    if z <= -126:
        return H0 - (z + 131)          # the drop-in: a block a row, down to 14
    if z <= -62:
        return 14 - math.floor(7 * (z + 126) / 64 + 1e-9)
    return None


def park_h(x, z):
    if z > 0:
        return None
    zz = max(-140, z)
    if zz >= PARK_BASE_Z:
        core = 0.0
    else:
        core = Q[zz]
    if z < -140:
        core = H0 - (-140 - z) * 1.3
    if 143 <= x <= 164:
        f = pipe_floor(zz)
        if f is not None and z >= -140:
            if 146 <= x <= 161:
                core = f
            elif PIPE_Z0 <= z <= PIPE_Z1:
                core = f + PIPE_R           # the decks beside the pipe
            else:
                core = min(H0, f + PIPE_R) if z <= -126 else f
    if x < PARK_X0:
        core -= (PARK_X0 - x) * 1.3
    if x > PARK_X1:
        core -= (x - PARK_X1) * 1.3
    return core


for x in range(PARK_X0 - 20, PARK_X1 + 21):
    for z in range(-160, 1):
        v = park_h(x, z)
        if v is None or v < 0:
            continue
        inside = PARK_X0 <= x <= PARK_X1 and z >= -140
        set_col(x, z, G + math.floor(v + 1e-9), GROOMED if inside else SNOW)
        if inside:
            NO_LAYER.add((x, z))

# The base: groomed between the two areas.
for x in range(-20, PARK_X1 + 1):
    for z in range(-20, 36):
        if (x, z) not in S or S[(x, z)] == G:
            set_col(x, z, G, GROOMED if z <= 6 else SNOW)

# ---- terrain commands -----------------------------------------------------------------------------

terrain = []
xs = sorted({x for x, _ in S})
zs = sorted({z for _, z in S})
for z in zs:
    run = None
    for x in xs + [None]:
        key = (S[(x, z)], TOP[(x, z)]) if x is not None and (x, z) in S else None
        if run and key == run[2] and x == run[1] + 1:
            run[1] = x
            continue
        if run:
            x0, x1, (y, top) = run
            if y - 3 >= G + 1:
                terrain.append(f"fill {x0} {G + 1} {z} {x1} {y - 3} {z} minecraft:stone")
            if y - 1 >= G + 1:
                terrain.append(f"fill {x0} {max(G + 1, y - 2)} {z} {x1} {y - 1} {z} {SNOW}")
            if not (y == G and top == SNOW):
                terrain.append(f"fill {x0} {y} {z} {x1} {y} {z} {top}")
        run = [x, x, key] if key else None


def surf(x, z):
    return S.get((x, z), G)


# ---- slope markers and signs ----------------------------------------------------------------------

def sign(x, y, z, lines, rotation=0, wall=None):
    msgs = (lines + ["", "", "", ""])[:4]
    text = ",".join("'" + json.dumps({"text": m}) + "'" for m in msgs)
    nbt = "{front_text:{messages:[" + text + "]}}"
    if wall:
        setb(x, y, z, f"minecraft:spruce_wall_sign[facing={wall}]" + nbt)
    else:
        setb(x, y, z, f"minecraft:spruce_sign[rotation={rotation}]" + nbt)


def marker(color, z, label):
    for x in (-RUN_HALF - 1, RUN_HALF + 1):
        setb(x, surf(x, z) + 1, z, f"alpineshred:slope_marker_{color}[facing=north]")
    sign(RUN_HALF + 2, surf(RUN_HALF + 2, z) + 1, z, label, rotation=8)


for name, color, label in [("green summit", "green", ["Green", "Summit Glide"]),
                           ("blue upper", "blue", ["Blue", "Upper Cruiser"]),
                           ("black wall", "black", ["Black", "The Wall", "ice at the top"]),
                           ("rollers", "blue", ["Blue", "Rollers", "pop off the crests"]),
                           ("blue cruiser", "blue", ["Blue", "Cruiser", "blue ice strip"]),
                           ("green runout", "green", ["Green", "Runout", "ice patch"])]:
    seg = [m for m in MOUNTAIN if m[2] == name][0]
    marker(color, SEG_START[name] - seg[0] + 1, label)

sign(-RUN_HALF - 2, surf(-RUN_HALF - 2, -120) + 1, -120, ["Powder Bowl", "deep powder", "(walkers sink!)"], rotation=4)
sign(RUN_HALF + 2, surf(RUN_HALF + 2, -162) + 1, -162, ["Rock scrape", "edges grind"], rotation=12)
sign(15, surf(15, -152) + 1, -152, ["Moguls", "and glades", "east of here"], rotation=12)

# ---- moguls and glades ----------------------------------------------------------------------------

for x in range(16, 34):
    for z in range(-178, -150):
        NO_LAYER.add((x, z))
        b = 0.5 + 0.5 * math.sin(x * 0.9) * math.sin(z * 0.75)
        n = max(1, min(7, round(1 + 6 * b)))
        setb(x, surf(x, z) + 1, z, f"minecraft:snow[layers={n}]")

trees = []
for x in range(17, 40, 5):
    for z in range(-140, -25, 6):
        tx, tz = x + rnd.randint(-1, 1), z + rnd.randint(-2, 2)
        trees.append((tx, tz))
for _ in range(40):  # around the edges
    tx, tz = rnd.randint(-100, 100), rnd.randint(-240, -200)
    if abs(tx) > 60 and mountain_h(tx, tz) > 2:
        trees.append((tx, tz))
for tx in range(-15, 60, 7):
    trees.append((tx, 33))

# Lift corridors: no layers, no trees.
for lx, z0, z1 in [(LIFT1_X, SUMMIT_Z - 5, 4), (LIFT2_X, -145, 6)]:
    for x in range(lx - 6, lx + 7):
        for z in range(z0, z1 + 1):
            NO_LAYER.add((x, z))
trees = [(x, z) for x, z in trees if (x, z) not in NO_LAYER and abs(x) > RUN_HALF + 2]

# A snow layer over plain snow off the run.
layer_cmds = []
tree_set = set(trees)
for (x, z), top in TOP.items():
    if top == SNOW and (x, z) not in NO_LAYER and S[(x, z)] > G and (x, z) not in tree_set:
        layer_cmds.append(f"setblock {x} {S[(x, z)] + 1} {z} minecraft:snow[layers=2]")

tree_cmds = []
for x, z in trees:
    y = surf(x, z)
    tree_cmds.append(f"setblock {x} {y} {z} minecraft:dirt")
    tree_cmds.append(f"place feature minecraft:spruce {x} {y + 1} {z}")

# ---- park features --------------------------------------------------------------------------------

STRAIGHT = {"north": ((0, 1), (0, -1)), "south": ((0, -1), (0, 1)), "east": ((-1, 0), (1, 0)), "west": ((1, 0), (-1, 0))}
CURVE = {"north": ((0, 1), (1, 0)), "south": ((0, -1), (-1, 0)), "east": ((-1, 0), (0, 1)), "west": ((1, 0), (0, -1))}


def rail_line(pieces, block="alpineshred:pipe_rail", posts=False, waxed=False):
    """pieces: [(x, y, z, shape, facing)] in order; sets connected flags and legs like the structure generator."""
    occupied = {(p[0], p[2]) for p in pieces}
    for x, y, z, shape, facing in pieces:
        a, b = (CURVE if shape == "curve" else STRAIGHT)[facing]
        ca = (x + a[0], z + a[1]) in occupied
        cb = (x + b[0], z + b[1]) in occupied
        if posts:
            setb(x, y - 1, z, "alpineshred:rail_post")
        state = props(block, facing=facing, shape=shape, connected_a=ca, connected_b=cb, legs=not posts, waxed=waxed)
        setb(x, y, z, state)


def box_row(x, y, z0, z1, block, half="full", **extra):
    for z in range(z0, z1 + 1):
        setb(x, y, z, props(block, axis="z", half=half, end_a=(z == z0), end_b=(z == z1), waxed=False, **extra))


def kicker(cx, half_width, z_lip, layers, label):
    """A kicker rising south, its lip at row z_lip. layers: [(slope, blocks)] bottom up."""
    rows = []
    for level, slope in enumerate(layers):
        n = {"quarter": 4, "half": 2, "full": 1}[slope]
        for step in range(n):
            rows.append((level, slope, step))
    z0 = z_lip - len(rows) + 1
    for i, (level, slope, step) in enumerate(rows):
        z = z0 + i
        for x in range(cx - half_width, cx + half_width + 1):
            y = surf(x, z)
            if level:
                cmd(f"fill {x} {y + 1} {z} {x} {y + level} {z} {GROOMED}")
            setb(x, y + 1 + level, z, props("alpineshred:park_ramp", facing="south", slope=slope, step=step, lip=(i == len(rows) - 1)))
    sign(cx - half_width - 1, surf(cx - half_width - 1, z0 - 2) + 1, z0 - 2, label, rotation=8)


jx = JUMP[2]
t = TERRACE
kicker(jx, 2, t["T1"][1] - 4, ["quarter"], ["Small kicker", "quarter slope"])
kicker(jx, 2, t["T2"][1] - 4, ["half", "half"], ["Medium kicker", "half slope", "lip"])
kicker(jx, 2, t["T3"][1] - 4, ["half", "half", "full"], ["Large kicker", "steep full lip"])
kicker(jx, 3, t["T4"][1] - 4, ["quarter", "half", "half"], ["Big booter", "quarter > half", "3 blocks"])

# Jib line A (x 131) and B (x 138), one feature per terrace.
yA = G + t["T1"][2] + 1
rail_line([(JIB_A, yA, z, "flat", "south") for z in range(-118, -108)], "alpineshred:pipe_rail")
rail_line([(JIB_B, yA, z, "flat", "south") for z in range(-118, -108)], "alpineshred:flat_rail")
sign(JIB_A - 1, yA, -121, ["Pipe rail"], rotation=8)
sign(JIB_B + 1, yA, -121, ["Flat rail"], rotation=8)

y2 = G + t["T2"][2] + 1
box_row(JIB_A, y2, -89, -85, "alpineshred:wood_box", half="low")
box_row(JIB_A, y2, -81, -77, "alpineshred:metal_box", half="full")
for x in (JIB_B - 1, JIB_B, JIB_B + 1):
    box_row(x, y2, -89, -82, "alpineshred:metal_box", half="low")
box_row(JIB_B, y2, -79, -76, "alpineshred:plastic_box", half="low", color="light_blue")
sign(JIB_A - 1, y2, -91, ["Wood box (low)", "then metal box", "(full)"], rotation=8)
sign(JIB_B + 2, y2, -91, ["Wide metal box", "then plastic box"], rotation=8)

y3 = G + t["T3"][2] + 1
copper = ["copper_pipe_rail", "exposed_copper_pipe_rail", "weathered_copper_pipe_rail", "oxidized_copper_pipe_rail"]
pieces = [(JIB_A, y3, z, "flat", "south") for z in range(-58, -42)]
occupied = {(p[0], p[2]) for p in pieces}
for i, (x, y, z, shape, facing) in enumerate(pieces):
    a, b = STRAIGHT[facing]
    block = "alpineshred:" + (copper[i // 3] if i < 12 else "waxed_copper_pipe_rail")
    setb(x, y, z, props(block, facing=facing, shape=shape, connected_a=(x + a[0], z + a[1]) in occupied,
                        connected_b=(x + b[0], z + b[1]) in occupied, legs=True, waxed=i >= 12))
sign(JIB_A - 1, y3, -61, ["Copper rails", "fresh > oxidized", "then waxed"], rotation=8)
# The kinked rail: flat, up, flat on a step, down, flat.
for z in range(-57, -51):
    setb(JIB_B, y3, z, GROOMED)
kink = [(JIB_B, y3, -60, "flat", "south"), (JIB_B, y3, -59, "flat", "south"), (JIB_B, y3, -58, "rise_a", "south"),
        (JIB_B, y3 + 1, -57, "rise_b", "south")] + [(JIB_B, y3 + 1, z, "flat", "south") for z in range(-56, -52)] + \
       [(JIB_B, y3 + 1, -52, "rise_b", "north"), (JIB_B, y3, -51, "rise_a", "north"),
        (JIB_B, y3, -50, "flat", "south"), (JIB_B, y3, -49, "flat", "south")]
rail_line(kink, "alpineshred:pipe_rail")
sign(JIB_B + 1, y3, -62, ["Up-flat-down", "kinked rail"], rotation=8)

y4 = G + t["T4"][2] + 1
rail_line([(JIB_A, y4 + 1, z, "flat", "south") for z in range(-29, -19)], "alpineshred:pipe_rail", posts=True)
# A low run-up deck to the post rail.
for z in range(-32, -30):
    setb(JIB_A, y4, z, props("alpineshred:park_ramp", facing="south", slope="half", step=z + 32, lip=False))
setb(JIB_A, y4, -30, GROOMED)
curve = [(JIB_B, y4, z, "flat", "south") for z in range(-30, -24)] + [(JIB_B, y4, -24, "curve", "south")] + \
        [(x, y4, -24, "flat", "east") for x in range(JIB_B - 1, JIB_B - 5, -1)]
rail_line(curve, "alpineshred:flat_rail")
sign(JIB_A - 1, y4, -33, ["Rail on posts", "ramp up to it"], rotation=8)
sign(JIB_B + 1, y4, -33, ["Curve rail", "turns west"], rotation=8)

# The halfpipe: radius-5 quarter pipes facing west and east, coping behind each wall.
cells = []
for i in range(PIPE_R):
    top = PIPE_R - math.sqrt(PIPE_R * PIPE_R - (i + 1) ** 2)
    for j in range(PIPE_R):
        if top > j + 1e-9:
            cells.append((i, j))
west_o, east_o = PIPE_FLOOR[0] - 1, PIPE_FLOOR[1] + 1
for z in range(PIPE_Z0, PIPE_Z1 + 1):
    f = G + pipe_floor(z)
    for idx, (i, j) in enumerate(cells):
        setb(west_o - i, f + 1 + j, z, props("alpineshred:quarter_pipe", facing="west", radius=5, index=idx))
        setb(east_o + i, f + 1 + j, z, props("alpineshred:quarter_pipe", facing="east", radius=5, index=idx))
    setb(west_o - PIPE_R, f + PIPE_R, z, props("alpineshred:pipe_coping", facing="west"))
    setb(east_o + PIPE_R, f + PIPE_R, z, props("alpineshred:pipe_coping", facing="east"))
sign(153, G + H0 + 1, -138, ["Halfpipe", "radius 5, coping", "drop in south"], rotation=8)

# A radius-3 quarter pipe at the bottom of the jib line, with a deck behind it.
QP_Z = 4
qcells = []
for i in range(3):
    top = 3 - math.sqrt(9 - (i + 1) ** 2)
    for j in range(3):
        if top > j + 1e-9:
            qcells.append((i, j))
for x in range(127, 143):
    for idx, (i, j) in enumerate(qcells):
        setb(x, G + 1 + j, QP_Z + i, props("alpineshred:quarter_pipe", facing="south", radius=3, index=idx))
    cmd(f"fill {x} {G + 1} {QP_Z + 3} {x} {G + 3} {QP_Z + 5} {GROOMED}")
    setb(x, G + 3, QP_Z + 3, props("alpineshred:pipe_coping", facing="south"))
sign(126, G + 1, QP_Z - 2, ["Quarter pipe", "radius 3"], rotation=8)

# Start gates, flags and lane signs on the top deck.
deck_y = G + H0 + 1
for x in (JUMP[0], JUMP[1], 128, 141):
    setb(x, deck_y, -134, "alpineshred:start_gate[facing=south]")
for x in (PARK_X0, 127, 142, 165):
    for name in ("T1", "T2", "T3", "T4"):
        setb(x, G + t[name][2] + 1, t[name][0], "alpineshred:park_flag[facing=south]")
setb(PARK_X0, deck_y, -138, "alpineshred:park_flag[facing=south]")
sign(JUMP[2], deck_y, -138, ["Jump line", "4 kickers"], rotation=8)
sign(134, deck_y, -138, ["Jib line", "rails + boxes"], rotation=8)
sign(JUMP[2] + 3, deck_y, -138, ["TERRAIN PARK", "drop in south"], rotation=8)

# ---- lifts ----------------------------------------------------------------------------------------

lift_nodes = []


def platform(cx, y, cz):
    cmd(f"fill {cx - 4} {y} {cz - 4} {cx + 4} {y} {cz + 4} {GROOMED}")
    cmd(f"fill {cx - 4} {y + 1} {cz - 4} {cx + 4} {y + 5} {cz + 4} minecraft:air")


def build_lift(x, z_drive, z_heads, z_return, clearance, label):
    drive = (x, surf(x, z_drive) + 1, z_drive)
    ret = (x, surf(x, z_return) + 1, z_return)
    heads = [(x, surf(x, z) + clearance, z) for z in z_heads]
    nodes = [drive] + heads + [ret]
    # Raise heads until the chair (and a rider's legs) clear the snow on every span.
    for _ in range(20):
        bad = False
        for i in range(len(nodes) - 1):
            (ax, ay, az), (bx, by, bz) = nodes[i], nodes[i + 1]
            L = abs(bz - az)
            sag = min(4.0, L * L / 600.0)
            for k in range(1, 40):
                tt = k / 40
                zc = az + (bz - az) * tt
                if min(abs(zc - drive[2]), abs(zc - ret[2])) < 10:
                    continue
                cable = ay + (by - ay) * tt - sag * 4 * tt * (1 - tt) + 2.5
                ground = surf(x, round(zc)) + 1
                if cable - 1.6 - 2.0 < ground:
                    bad = True
                    for j in (i, i + 1):
                        if 0 < j < len(nodes) - 1:
                            nx, ny, nz = nodes[j]
                            nodes[j] = (nx, ny + 1, nz)
                    break
        if not bad:
            break
    platform(drive[0], drive[1] - 1, drive[2])
    platform(ret[0], ret[1] - 1, ret[2])
    for nx, ny, nz in nodes[1:-1]:
        cmd(f"fill {nx} {surf(nx, nz) + 1} {nz} {nx} {ny - 1} {nz} alpineshred:lift_tower_pole")
        setb(nx, ny, nz, "alpineshred:lift_tower_head")
    setb(drive[0], drive[1] - 1, drive[2], "minecraft:redstone_block")
    setb(*drive, "alpineshred:lift_drive_station")
    setb(*ret, "alpineshred:lift_return_station")
    # Loading gates either side of the boarding lane, a stop button and signs at the bottom.
    by = ret[1]
    setb(x - 3, by, z_return - 2, "alpineshred:lift_loading_gate[facing=south]")
    setb(x + 3, by, z_return - 2, "alpineshred:lift_loading_gate[facing=south]")
    setb(x + 4, by, z_return + 4, "minecraft:polished_andesite")
    setb(x + 4, by, z_return + 3, "alpineshred:lift_stop_button[facing=south]")
    sign(x - 4, by, z_return + 4, [label, "stand by the", "station, strapped", "in: auto-boards"], rotation=0)
    sign(x + 3, by, z_return + 4, ["Stop button", "/alpineshred lift", "start <id>", "to restart"], rotation=0)
    sign(x - 4, drive[1], drive[2] + 4, [label + " top", "right-click the", "drive: speed"], rotation=0)
    lift_nodes.append([list(n) for n in nodes])
    return nodes


lift1 = build_lift(LIFT1_X, SUMMIT_Z + 20, [-176, -134, -92, -50], -8, 12, "Summit Lift")
lift2 = build_lift(LIFT2_X, -136, [-100, -66, -32], 0, 11, "Park Lift")

# ---- the lodge ------------------------------------------------------------------------------------

LX0, LX1, LZ0, LZ1 = 74, 96, 8, 20
gy = G + 1
lodge = []
L = lodge.append
L(f"fill {LX0 - 2} {gy} {LZ0 - 2} {LX1 + 2} {gy + 12} {LZ1 + 8} minecraft:air")
L(f"fill {LX0} {G} {LZ0} {LX1} {G} {LZ1} minecraft:spruce_planks")
L(f"fill {LX0} {gy} {LZ0} {LX1} {gy + 4} {LZ1} minecraft:spruce_planks hollow")
L(f"fill {LX0} {gy} {LZ0} {LX0} {gy + 4} {LZ0} minecraft:stripped_spruce_log")
for cx, cz in [(LX0, LZ0), (LX1, LZ0), (LX0, LZ1), (LX1, LZ1)]:
    L(f"fill {cx} {gy} {cz} {cx} {gy + 4} {cz} minecraft:stripped_spruce_log")
for k in range(0, 7):  # a stepped roof
    L(f"fill {LX0 - 1 + k} {gy + 5 + k} {LZ0 - 1} {LX1 + 1 - k} {gy + 5 + k} {LZ1 + 1} minecraft:dark_oak_planks")
    L(f"fill {LX0 - 1 + k} {gy + 6 + k} {LZ0 - 1} {LX1 + 1 - k} {gy + 6 + k} {LZ1 + 1} minecraft:snow[layers=3]")
L(f"fill {LX0 + 1} {gy + 5} {LZ0 + 1} {LX1 - 1} {gy + 5} {LZ1 - 1} minecraft:air")
for wx in range(LX0 + 2, LX1 - 1, 4):
    L(f"fill {wx} {gy + 1} {LZ0} {wx + 1} {gy + 2} {LZ0} minecraft:glass_pane")
for wz in range(LZ0 + 3, LZ1 - 2, 4):
    L(f"fill {LX0} {gy + 1} {wz} {LX0} {gy + 2} {wz + 1} minecraft:glass_pane")
    L(f"fill {LX1} {gy + 1} {wz} {LX1} {gy + 2} {wz + 1} minecraft:glass_pane")
DOOR_X = 85
L(f"fill {DOOR_X - 1} {gy} {LZ1} {DOOR_X + 1} {gy + 2} {LZ1} minecraft:air")
for lx in range(LX0 + 3, LX1 - 1, 5):
    L(f"setblock {lx} {gy + 5} {(LZ0 + LZ1) // 2} minecraft:lantern[hanging=true]")


def item(i, slot, count=1, tag=None):
    s = f'{{Slot:{slot}b,id:"{i}",Count:{count}b'
    if tag:
        s += f",tag:{tag}"
    return s + "}"


def chest(x, y, z, facing, items, name):
    nbt = "{CustomName:'" + json.dumps({"text": name}) + "',Items:[" + ",".join(items) + "]}"
    L(f"setblock {x} {y} {z} minecraft:chest[facing={facing}]{nbt}")


boards = ["snowboard", "park_snowboard", "freeride_snowboard", "pro_snowboard", "skis", "carving_skis", "powder_skis",
          "park_skis", "pro_skis"]
gear = [item("alpineshred:" + b, i) for i, b in enumerate(boards)]
extra = [("snowboard_boots", 1), ("ski_boots", 1), ("ski_helmet", 1), ("mitts", 1), ("ski_poles", 1), ("carbon_ski_poles", 1),
         ("board_wax", 16), ("edge_tuner", 1), ("park_wrench", 1), ("lift_pass", 1), ("goggles", 1)]
slot = len(gear)
for name, n in extra:
    gear.append(item("alpineshred:" + name, slot, n))
    slot += 1
for lens in ("amber", "blue_mirror", "black"):
    gear.append(item("alpineshred:goggles", slot, 1, f'{{Lens:"{lens}"}}'))
    slot += 1
chest(LX0 + 2, gy, LZ0 + 1, "south", gear, "Gear: boards, skis, kit")
park_items = ["pipe_rail", "flat_rail", "copper_pipe_rail", "exposed_copper_pipe_rail", "weathered_copper_pipe_rail",
              "oxidized_copper_pipe_rail", "waxed_copper_pipe_rail", "waxed_exposed_copper_pipe_rail",
              "waxed_weathered_copper_pipe_rail", "waxed_oxidized_copper_pipe_rail", "rail_post", "wood_box", "metal_box",
              "plastic_box", "park_ramp", "quarter_pipe", "pipe_coping", "slope_marker_green", "slope_marker_blue",
              "slope_marker_black", "park_flag", "start_gate", "park_wrench", "groomed_snow"]
chest(LX0 + 4, gy, LZ0 + 1, "south", [item("alpineshred:" + p, i, 64 if p != "park_wrench" else 1) for i, p in enumerate(park_items)],
      "Park building blocks")
lift_items = ["lift_tower_pole", "lift_tower_head", "lift_drive_station", "lift_return_station", "lift_loading_gate",
              "lift_stop_button", "lift_linker", "minecraft:redstone_block"]
chest(LX0 + 6, gy, LZ0 + 1, "south", [item(p if ":" in p else "alpineshred:" + p, i, 64 if "pole" in p else 8) for i, p in enumerate(lift_items)],
      "Lift building blocks")
L(f"setblock {LX0 + 10} {gy} {LZ0 + 1} alpineshred:paint_bench")
L(f"setblock {LX0 + 12} {gy} {LZ0 + 1} minecraft:crafting_table")
L(f"setblock {LX0 + 14} {gy} {LZ0 + 1} minecraft:honeycomb_block")
L(f"setblock {LX0 + 15} {gy} {LZ0 + 1} minecraft:bookshelf")
for bx in range(LX0 + 3, LX1 - 2, 4):  # benches
    L(f"fill {bx} {gy} {LZ1 - 4} {bx + 1} {gy} {LZ1 - 4} minecraft:spruce_stairs[facing=south]")
for line_, x in [(["Gear chest", "every board, ski", "and goggles"], LX0 + 2), (["Park blocks", "build your own"], LX0 + 4),
                 (["Lift blocks", "+ lift linker"], LX0 + 6), (["Paint Bench", "design boards", "and skis"], LX0 + 10)]:
    text = ",".join("'" + json.dumps({"text": m}) + "'" for m in (line_ + ["", "", ""])[:4])
    L(f"setblock {x} {gy + 1} {LZ0 + 1} minecraft:spruce_wall_sign[facing=south]{{front_text:{{messages:[{text}]}}}}")

# The button panel on the front wall (south face, z = LZ1): a command block in the wall, a button on it, a sign above.
panel = [
    ("Snowboard kit", "boots helmet", "goggles mitts", "function showcase:kit_board"),
    ("Ski kit", "boots helmet", "goggles poles", "function showcase:kit_ski"),
    ("Strap in / out", "toggle", "", "alpineshred strap @p"),
    ("Clear gear", "empties slots", "", "function showcase:kit_clear"),
    ("To the Summit", "Summit Mountain", "top of the run", f"tp @p 0 {surf(0, SUMMIT_Z + 6) + 1} {SUMMIT_Z + 6} 0 10"),
    ("To the Park", "top deck", "", f"tp @p {JUMP[2]} {deck_y} -136 0 10"),
    ("To the Halfpipe", "drop-in deck", "", f"tp @p 153 {deck_y} -136 0 10"),
    ("Back to Lodge", "spawn", "", "tp @p {sx} {sy} {sz} 180 0"),
]
DOOR_X = 85
SPAWN = (DOOR_X, gy, LZ1 + 6)
panel = [(a, b, c, command.format(sx=SPAWN[0], sy=SPAWN[1], sz=SPAWN[2]) if command.startswith("tp @p {") else command)
         for a, b, c, command in panel]
px = [77, 79, 81, 83, 87, 89, 91, 93]
for (a, b, c, command), x in zip(panel, px):
    L(f"setblock {x} {gy + 1} {LZ1} minecraft:command_block{{Command:\"{command}\",TrackOutput:0b}}")
    L(f"setblock {x} {gy + 1} {LZ1 + 1} minecraft:stone_button[face=wall,facing=south]")
    msgs = [a, b, c, "[press]"]
    text = ",".join("'" + json.dumps({"text": m}) + "'" for m in msgs)
    L(f"setblock {x} {gy + 2} {LZ1 + 1} minecraft:spruce_wall_sign[facing=south]{{front_text:{{messages:[{text}]}}}}")
L(f"setblock {DOOR_X - 2} {gy} {LZ1 + 4} minecraft:spruce_sign[rotation=0]{{front_text:{{messages:['{json.dumps({'text': 'ALPINE SHRED'})}','{json.dumps({'text': 'showcase world'})}','{json.dumps({'text': 'Summit Lift: west'})}','{json.dumps({'text': 'Park Lift: east'})}']}}}}")
L(f"setblock {DOOR_X + 2} {gy} {LZ1 + 4} minecraft:spruce_sign[rotation=0]{{front_text:{{messages:['{json.dumps({'text': 'Buttons on the'})}','{json.dumps({'text': 'lodge wall'})}','{json.dumps({'text': 'Chests + Paint'})}','{json.dumps({'text': 'Bench inside'})}']}}}}")
# Groomed paths from the lodge to both lifts.
L(f"fill {LIFT1_X} {G} {LZ1 + 2} {LX0} {G} {LZ1 + 4} {GROOMED}")
L(f"fill {LX1} {G} {LZ1 + 2} {LIFT2_X} {G} {LZ1 + 4} {GROOMED}")

# ---- write the data pack ------------------------------------------------------------------------

pack = os.path.join(OUT, "world", "datapacks", "showcase")
fn = os.path.join(pack, "data", "showcase", "functions")
os.makedirs(fn, exist_ok=True)
with open(os.path.join(pack, "pack.mcmeta"), "w") as f:
    json.dump({"pack": {"pack_format": 15, "description": "Alpine Shred showcase world: builder and kit functions"}}, f)

all_x = [x for x, _ in S]
all_z = [z for _, z in S]
cx0, cx1 = min(all_x) >> 4, max(all_x) >> 4
cz0, cz1 = min(all_z) >> 4, max(all_z) >> 4
mid = (cx0 + cx1) // 2


def write(name, lines):
    with open(os.path.join(fn, name + ".mcfunction"), "w", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


write("forceload", [f"forceload add {cx0 * 16} {cz0 * 16} {mid * 16 + 15} {cz1 * 16 + 15}",
                    f"forceload add {(mid + 1) * 16} {cz0 * 16} {cx1 * 16 + 15} {cz1 * 16 + 15}"])
parts = {"terrain": terrain, "layers": layer_cmds, "features": late, "trees": tree_cmds, "lodge": lodge}
build = []
for name, lines in parts.items():
    write("build_" + name, lines)
    build.append(f"function showcase:build_{name}")
write("build", build)
write("finish", [
    f"setworldspawn {SPAWN[0]} {SPAWN[1]} {SPAWN[2]} 180",
    "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
    "gamerule doInsomnia false", "gamerule doTraderSpawning false", "gamerule doPatrolSpawning false",
    "gamerule keepInventory true", "gamerule snowAccumulationHeight 0", "gamerule commandBlockOutput false",
    "time set 6000", "weather clear 1000000", "difficulty peaceful", "kill @e[type=item]",
])
write("kit_board", [
    "item replace entity @p armor.feet with alpineshred:snowboard_boots",
    "item replace entity @p armor.head with alpineshred:ski_helmet",
    "curios replace snow_gear 0 @p with alpineshred:snowboard",
    'curios replace goggles 0 @p with alpineshred:goggles{Lens:"amber"}',
    "curios replace hands 0 @p with alpineshred:mitts",
    'tellraw @p {"text":"Snowboard kit on. Press the Strap button (or your strap key) to ride.","color":"aqua"}',
])
write("kit_ski", [
    "item replace entity @p armor.feet with alpineshred:ski_boots",
    "item replace entity @p armor.head with alpineshred:ski_helmet",
    "curios replace snow_gear 0 @p with alpineshred:skis",
    'curios replace goggles 0 @p with alpineshred:goggles{Lens:"blue_mirror"}',
    "item replace entity @p weapon.mainhand with alpineshred:ski_poles",
    'tellraw @p {"text":"Ski kit on. Press the Strap button (or your strap key) to ride.","color":"aqua"}',
])
write("kit_clear", [
    "alpineshred strap @p off",
    "item replace entity @p armor.feet with minecraft:air",
    "item replace entity @p armor.head with minecraft:air",
    "curios clear @p",
])

with open(os.path.join(OUT, "showcase-lifts.json"), "w") as f:
    json.dump({"chunkBounds": [cx0, cz0, cx1, cz1], "lifts": lift_nodes}, f, indent=1)

props_text = """online-mode=false
level-name=world
level-type=minecraft\\:flat
generator-settings={"layers"\\:[{"block"\\:"minecraft\\:bedrock","height"\\:1},{"block"\\:"minecraft\\:stone","height"\\:3},{"block"\\:"minecraft\\:snow_block","height"\\:1}],"biome"\\:"minecraft\\:snowy_slopes","features"\\:false,"lakes"\\:false,"structure_overrides"\\:[]}
generate-structures=false
gamemode=survival
difficulty=peaceful
spawn-protection=0
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
view-distance=10
enable-command-block=true
initial-enabled-packs=vanilla,file/showcase
server-port=25599
motd=Alpine Shred showcase builder
"""
with open(os.path.join(OUT, "server.properties"), "w", newline="\n") as f:
    f.write(props_text)
with open(os.path.join(OUT, "eula.txt"), "w") as f:
    f.write("eula=true\n")

total = sum(len(v) for v in parts.values())
print(f"terrain {len(terrain)}, layers {len(layer_cmds)}, features {len(late)}, trees {len(tree_cmds)}, lodge {len(lodge)}: {total} commands")
print(f"summit y {G + math.floor(SUMMIT)}, park top y {G + H0}, chunks x {cx0}..{cx1} z {cz0}..{cz1}")
for i, nodes in enumerate(lift_nodes):
    print(f"lift {i + 1}: {nodes}")
