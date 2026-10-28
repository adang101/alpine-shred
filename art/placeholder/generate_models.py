#!/usr/bin/env python3
"""
Item and block models for Alpine Shred, phase 1 (design doc section 16).

Boards, skis, goggles and mitts are 3D element models, because SnowGearLayer draws the item model
itself on the player with the FIXED display context: identity transform, 16 model pixels = 1
block, centred on (8, 8, 8). Their gui/hand/ground transforms only affect inventory and held views.
Re-run after editing:  python3 art/placeholder/generate_models.py
"""
from __future__ import annotations

import gzip
import io
import json
import os
import struct

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, "..", "..", "common", "src", "main", "resources"))
MODELS = os.path.join(RES, "assets", "alpineshred", "models")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def faces(up, down, side, texture, tint=None):
    def face(uv):
        out = {"uv": uv, "texture": texture}
        if tint is not None:
            out["tintindex"] = tint
        return out
    return {"up": face(up), "down": face(down), "north": face(side), "south": face(side),
            "east": face(side), "west": face(side)}


def box(name, frm, to, up, down, side, texture="#board", tint=None):
    return {"name": name, "from": frm, "to": to, "faces": faces(up, down, side, texture, tint)}


EDGE = [12, 0, 16, 1]

HELD = {
    "thirdperson_righthand": {"rotation": [-60, 0, 0], "translation": [0, 3, 3], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [-60, 0, 0], "translation": [0, 3, 3], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [-25, -30, 0], "translation": [1, 3, -1], "scale": [0.45, 0.45, 0.45]},
    "firstperson_lefthand": {"rotation": [-25, 30, 0], "translation": [-1, 3, -1], "scale": [0.45, 0.45, 0.45]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    # FIXED is how SnowGearLayer draws gear on the player: keep it identity (section 16).
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}

# ---- snowboard: 4 x 23 px deck, stepped nose and tail kicks, two bindings -------------------
board_elements = [
    box("core", [6, 7.75, 0], [10, 8.25, 16], [0, 0, 4, 16], [4, 0, 8, 16], EDGE),
    box("nose", [6, 8.0, 16], [10, 8.5, 18.5], [8, 0, 12, 2.5], [8, 3, 12, 5.5], EDGE),
    box("nose_tip", [6.25, 8.35, 18.5], [9.75, 8.85, 19.75], [8, 0, 11.5, 1.25], [8, 3, 11.5, 4.25], EDGE),
    box("tail", [6, 8.0, -2.5], [10, 8.5, 0], [8, 0, 12, 2.5], [8, 3, 12, 5.5], EDGE),
    box("tail_tip", [6.25, 8.35, -3.75], [9.75, 8.85, -2.5], [8, 0, 11.5, 1.25], [8, 3, 11.5, 4.25], EDGE),
    box("binding_front", [6.4, 8.25, 10], [9.6, 9.6, 12.5], [8, 8, 12, 12], [8, 8, 12, 12], [8, 8, 12, 12]),
    box("binding_back", [6.4, 8.25, 3.5], [9.6, 9.6, 6], [8, 8, 12, 12], [8, 8, 12, 12], [8, 8, 12, 12]),
]
write(os.path.join(MODELS, "item", "template_snowboard.json"), {
    "gui_light": "front",
    "textures": {"particle": "#board"},
    "elements": board_elements,
    "display": {"gui": {"rotation": [90, -45, 0], "translation": [0, 0, 0], "scale": [0.68, 0.68, 0.68]}, **HELD},
})
for item in ["snowboard", "park_snowboard", "freeride_snowboard", "pro_snowboard"]:
    write(os.path.join(MODELS, "item", f"{item}.json"), {
        "parent": "alpineshred:item/template_snowboard",
        "textures": {"board": f"alpineshred:item/gear/{item}"},
    })

# ---- skis: a pair, 2 x 28 px each, centred 2 px either side so they sit under the legs -------
ski_elements = []
for side, x0 in (("right", 5.0), ("left", 9.0)):
    x1 = x0 + 2
    ski_elements += [
        box(f"{side}_core", [x0, 7.75, -4], [x1, 8.25, 19], [0, 0, 2, 16], [4, 0, 6, 16], EDGE),
        box(f"{side}_tip", [x0, 8.0, 19], [x1, 8.5, 21], [8, 0, 10, 2], [8, 3, 10, 5], EDGE),
        box(f"{side}_tip_end", [x0 + 0.2, 8.4, 21], [x1 - 0.2, 8.9, 22.5], [8, 0, 9.6, 1.5], [8, 3, 9.6, 4.5], EDGE),
        box(f"{side}_tail", [x0, 7.9, -5.5], [x1, 8.4, -4], [8, 0, 10, 1.5], [8, 3, 10, 4.5], EDGE),
        box(f"{side}_binding", [x0 + 0.2, 8.25, 6], [x1 - 0.2, 9.25, 10.5], [8, 8, 12, 12], [8, 8, 12, 12], [8, 8, 12, 12]),
    ]
write(os.path.join(MODELS, "item", "template_skis.json"), {
    "gui_light": "front",
    "textures": {"particle": "#board"},
    "elements": ski_elements,
    "display": {"gui": {"rotation": [90, -45, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]}, **HELD},
})
for item in ["skis", "carving_skis", "powder_skis", "park_skis", "pro_skis"]:
    write(os.path.join(MODELS, "item", f"{item}.json"), {
        "parent": "alpineshred:item/template_skis",
        "textures": {"board": f"alpineshred:item/gear/{item}"},
    })

# ---- goggles: head-sized (head = 4..12 on each axis), lens on the south (+z) face -----------
# After SnowGearLayer flips the model to y-up, item +z faces forward out of the face.
STRAP_UV = [0, 0, 8, 4]
FRAME_UV = [8, 0, 16, 8]


def gog_box(name, frm, to, uv, texture="#goggles"):
    return {"name": name, "from": frm, "to": to,
            "faces": {k: {"uv": uv, "texture": texture} for k in ("up", "down", "north", "south", "east", "west")}}


goggle_elements = [
    gog_box("strap_left", [3.6, 6.3, 3.6], [4.0, 7.7, 12.4], STRAP_UV),
    gog_box("strap_right", [12.0, 6.3, 3.6], [12.4, 7.7, 12.4], STRAP_UV),
    gog_box("strap_back", [4.0, 6.3, 3.6], [12.0, 7.7, 4.0], STRAP_UV),
    gog_box("frame", [3.6, 5.6, 12.4], [12.4, 8.6, 12.9], FRAME_UV),
    {"name": "lens", "from": [4.3, 6.0, 12.9], "to": [11.7, 8.2, 13.1],
     "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#lens"},
               "north": {"uv": [0, 0, 16, 16], "texture": "#lens"},
               "up": {"uv": FRAME_UV, "texture": "#goggles"}, "down": {"uv": FRAME_UV, "texture": "#goggles"},
               "east": {"uv": FRAME_UV, "texture": "#goggles"}, "west": {"uv": FRAME_UV, "texture": "#goggles"}}},
]
write(os.path.join(MODELS, "item", "template_goggles.json"), {
    "gui_light": "front",
    "textures": {"goggles": "alpineshred:item/gear/goggles", "particle": "alpineshred:item/gear/goggles"},
    "elements": goggle_elements,
    "display": {
        "gui": {"rotation": [15, 25, 0], "translation": [0, 1, 0], "scale": [1.5, 1.5, 1.5]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "head": {"rotation": [0, 180, 0], "translation": [0, -6.5, 0], "scale": [1.05, 1.05, 1.05]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
        "firstperson_righthand": {"rotation": [0, -30, 0], "translation": [1, 3, 0], "scale": [0.6, 0.6, 0.6]},
        "firstperson_lefthand": {"rotation": [0, 30, 0], "translation": [-1, 3, 0], "scale": [0.6, 0.6, 0.6]},
    },
})
lenses = {"clear": 0.0, "amber": 0.25, "blue_mirror": 0.5, "black": 0.75}
for lens in lenses:
    if lens == "clear":
        continue
    write(os.path.join(MODELS, "item", f"goggles_{lens}.json"), {
        "parent": "alpineshred:item/template_goggles",
        "textures": {"lens": f"alpineshred:item/gear/lens_{lens}"},
    })
write(os.path.join(MODELS, "item", "goggles.json"), {
    "parent": "alpineshred:item/template_goggles",
    "textures": {"lens": "alpineshred:item/gear/lens_clear"},
    "overrides": [
        {"predicate": {"alpineshred:lens": value}, "model": f"alpineshred:item/goggles_{lens}"}
        for lens, value in lenses.items() if lens != "clear"
    ],
})

# ---- mitts: a mitten around the hand (arm is 4 px wide), body tinted, cuff white ------------
mitt_body_uv = [0, 0, 8, 8]
cuff_uv = [8, 0, 16, 4]


def mitt_box(name, frm, to, uv, tint):
    f = {}
    for k in ("up", "down", "north", "south", "east", "west"):
        f[k] = {"uv": uv, "texture": "#mitts"}
        if tint:
            f[k]["tintindex"] = 0
    return {"name": name, "from": frm, "to": to, "faces": f}


write(os.path.join(MODELS, "item", "mitts.json"), {
    "gui_light": "side",
    "textures": {"mitts": "alpineshred:item/gear/mitts", "particle": "alpineshred:item/gear/mitts"},
    "elements": [
        mitt_box("hand", [5.5, 5.8, 5.5], [10.5, 9.4, 10.5], mitt_body_uv, True),
        # Thumb on the +z side: after the y-up flip that is forward, the right way for both hands.
        mitt_box("thumb", [7.0, 6.6, 10.5], [9.0, 8.4, 11.4], mitt_body_uv, True),
        mitt_box("cuff", [5.2, 9.2, 5.2], [10.8, 10.6, 10.8], cuff_uv, False),
    ],
    "display": {
        "gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [1.8, 1.8, 1.8]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
        "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
    },
})

# ---- flat items -------------------------------------------------------------------------------
for item in ["ski_poles", "carbon_ski_poles", "edge_tuner", "park_wrench", "lift_linker"]:
    write(os.path.join(MODELS, "item", f"{item}.json"), {
        "parent": "minecraft:item/handheld", "textures": {"layer0": f"alpineshred:item/{item}"}})
for item in ["board_wax", "lift_pass"]:
    write(os.path.join(MODELS, "item", f"{item}.json"), {
        "parent": "minecraft:item/generated", "textures": {"layer0": f"alpineshred:item/{item}"}})
for item in ["ski_boots", "snowboard_boots", "ski_helmet"]:
    write(os.path.join(MODELS, "item", f"{item}.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"alpineshred:item/{item}", "layer1": f"alpineshred:item/{item}_overlay"}})

# ---- groomed snow -----------------------------------------------------------------------------
write(os.path.join(RES, "assets", "alpineshred", "blockstates", "groomed_snow.json"),
      {"variants": {"": {"model": "alpineshred:block/groomed_snow"}}})
write(os.path.join(MODELS, "block", "groomed_snow.json"),
      {"parent": "minecraft:block/cube_all", "textures": {"all": "alpineshred:block/groomed_snow"}})
write(os.path.join(MODELS, "item", "groomed_snow.json"), {"parent": "alpineshred:block/groomed_snow"})


# ---- game-test structure: 5 x 3 x 5, a floor of snow blocks (section 19) ------------------------
def nbt_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def tag(tag_type, name, payload):
    return struct.pack(">b", tag_type) + nbt_string(name) + payload


def compound(entries):
    return b"".join(entries) + b"\x00"


def int_list(values):
    return struct.pack(">bi", 3, len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound_list(items):
    return struct.pack(">bi", 10, len(items)) + b"".join(compound(i) for i in items)


blocks = [[tag(9, "pos", int_list([x, 0, z])), tag(3, "state", struct.pack(">i", 0))] for x in range(5) for z in range(5)]
root = tag(10, "", compound([
    tag(3, "DataVersion", struct.pack(">i", 3465)),  # Minecraft 1.20.1
    tag(9, "size", int_list([5, 3, 5])),
    tag(9, "palette", compound_list([[tag(8, "Name", nbt_string("minecraft:snow_block"))]])),
    tag(9, "blocks", compound_list(blocks)),
    tag(9, "entities", struct.pack(">bi", 0, 0)),
]))
path = os.path.join(RES, "data", "alpineshred", "structures", "empty.nbt")
os.makedirs(os.path.dirname(path), exist_ok=True)
buf = io.BytesIO()
with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as gz:
    gz.write(root)
with open(path, "wb") as f:
    f.write(buf.getvalue())

print("models written under", MODELS)
