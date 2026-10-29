#!/usr/bin/env python3
"""Writes the placeholder rider clips (design doc section 9) as PlayerAnimator keyframe JSON.

Each clip is a few hand-set poses. Real clips authored in Blockbench (player rig, torso bone named
"torso") replace these files one by one under the same names.

Conventions, all PlayerAnimator's (model space, 1/16 block units, y down):
  * values are absolute, not offsets: a part a clip touches has every axis driven by the clip, so
    every keyframe here writes every axis of every part it uses;
  * the head is never animated, so it keeps following the player's look;
  * pitch < 0 swings an arm or leg forward; positive torso pitch leans forward; positive roll moves
    a right limb outward and a left limb inward;
  * snowboarders stand sideways: the board runs along the model's x axis. Regular riders lead with
    the left side (+x), goofy riders with the right; goofy clips are mirrors of regular ones.
  * "body" is the whole-model root (blocks, about a pivot 0.7 above the feet); only wipeouts use it.

Run from the repository root:  python3 art/placeholder/generate_animations.py
"""
import copy
import json
import os

OUT = os.path.join("common", "src", "main", "resources", "assets", "alpineshred", "player_animation")

DEFAULT = {
    "torso": dict(x=0, y=0, z=0, pitch=0, yaw=0, roll=0),
    "rightArm": dict(x=-5, y=2, z=0, pitch=0, yaw=0, roll=0),
    "leftArm": dict(x=5, y=2, z=0, pitch=0, yaw=0, roll=0),
    "rightLeg": dict(x=-1.9, y=12, z=0.1, pitch=0, yaw=0, roll=0),
    "leftLeg": dict(x=1.9, y=12, z=0.1, pitch=0, yaw=0, roll=0),
}
ROOT = dict(x=0, y=0, z=0, pitch=0, yaw=0, roll=0)
ARMS = ("rightArm", "leftArm")


def pose(crouch=0.0, torso=None, rarm=None, larm=None, rleg=None, lleg=None, body=None, arms_only=False):
    """A full pose. crouch lowers the torso and arms by that many pixels."""
    p = copy.deepcopy(DEFAULT)
    p["torso"]["y"] += crouch
    for arm in ARMS:
        p[arm]["y"] += crouch
    for part, over in (("torso", torso), ("rightArm", rarm), ("leftArm", larm), ("rightLeg", rleg), ("leftLeg", lleg)):
        if over:
            p[part].update(over)
    if body is not None:
        b = dict(ROOT)
        b.update(body)
        p["body"] = b
    if arms_only:
        p = {k: v for k, v in p.items() if k in ARMS}
    return p


def mirror(p):
    """Left-right mirror: swap limbs, negate x, yaw and roll."""
    out = {}
    swap = {"rightArm": "leftArm", "leftArm": "rightArm", "rightLeg": "leftLeg", "leftLeg": "rightLeg"}
    for part, axes in p.items():
        a = dict(axes)
        a["x"] = -a["x"]
        a["yaw"] = -a["yaw"]
        a["roll"] = -a["roll"]
        out[swap.get(part, part)] = a
    return out


def clip(name, frames, end, loop=False, description=""):
    """frames: list of (tick, pose, easing)."""
    moves = []
    for tick, p, easing in frames:
        entry = {"tick": tick, "easing": easing}
        for part, axes in p.items():
            entry[part] = {k: round(v, 3) for k, v in axes.items()}
        moves.append(entry)
    return {
        "version": 3,
        "name": name,
        "author": "Alpine Shred placeholder",
        "description": description,
        "emote": {
            "beginTick": 0,
            "endTick": end,
            "stopTick": end + 1,
            "isLoop": loop,
            "returnTick": 0,
            "degrees": True,
            "moves": moves,
        },
    }


def breathing(name, a, b, length=40, description=""):
    """A loop that eases between two close poses and back."""
    return clip(name, [(0, a, "INOUTSINE"), (length // 2, b, "INOUTSINE"), (length, a, "INOUTSINE")], length, True, description)


def board_stance(crouch, torso_pitch, lead_arm, rear_arm, legs=12, leg_pitch=0):
    """Regular stance: left (+x) side leads. Arms are (pitch, roll) with roll outward-positive."""
    lp, lr = lead_arm
    rp, rr = rear_arm
    return pose(crouch,
                torso=dict(pitch=torso_pitch),
                larm=dict(pitch=lp, roll=-lr),
                rarm=dict(pitch=rp, roll=rr),
                rleg=dict(roll=legs, pitch=leg_pitch),
                lleg=dict(roll=-legs, pitch=leg_pitch))


def ski_stance(crouch, torso_pitch, arm_pitch, arm_roll, leg_pitch, leg_roll=3):
    return pose(crouch,
                torso=dict(pitch=torso_pitch),
                rarm=dict(pitch=arm_pitch, roll=arm_roll),
                larm=dict(pitch=arm_pitch, roll=-arm_roll),
                rleg=dict(pitch=leg_pitch, roll=leg_roll),
                lleg=dict(pitch=leg_pitch, roll=-leg_roll))


def build():
    clips = []
    # ---- idle and walk ----
    clips.append(breathing("idle_board", board_stance(0.5, 4, (-5, 10), (-5, 10), legs=10),
                           board_stance(0.8, 5, (-6, 12), (-6, 12), legs=10), description="Strapped in, standing on a board"))
    clips.append(breathing("idle_skis", ski_stance(0.5, 5, -10, 6, -3), ski_stance(0.8, 6, -12, 7, -3),
                           description="Strapped in, standing on skis"))
    shuffle_a = pose(0.5, torso=dict(pitch=6), rarm=dict(pitch=10, roll=6), larm=dict(pitch=-10, roll=-6),
                     rleg=dict(pitch=-12), lleg=dict(pitch=12))
    shuffle_b = pose(0.5, torso=dict(pitch=6), rarm=dict(pitch=-10, roll=6), larm=dict(pitch=10, roll=-6),
                     rleg=dict(pitch=12), lleg=dict(pitch=-12))
    clips.append(clip("walk_strapped", [(0, shuffle_a, "INOUTSINE"), (10, shuffle_b, "INOUTSINE"), (20, shuffle_a, "INOUTSINE")],
                      20, True, "Slow shuffle while strapped off snow"))

    # ---- glide ----
    glide_a = board_stance(1.5, 12, (-15, 38), (-10, 24), legs=12)
    glide_b = board_stance(1.8, 13, (-17, 41), (-12, 22), legs=12)
    clips.append(breathing("glide_board_regular", glide_a, glide_b, description="Gliding, regular stance"))
    clips.append(breathing("glide_board_goofy", mirror(glide_a), mirror(glide_b), description="Gliding, goofy stance"))
    clips.append(breathing("glide_skis", ski_stance(1.5, 14, -25, 10, -6), ski_stance(1.8, 15, -27, 11, -6),
                           description="Gliding on skis"))

    # ---- speed ----
    clips.append(breathing("tuck_board", board_stance(3.0, 28, (-30, 15), (-30, 15), legs=14),
                           board_stance(3.2, 29, (-32, 15), (-32, 15), legs=14), 30, "Tucked at speed"))
    clips.append(breathing("tuck_skis", ski_stance(3.5, 40, -70, 10, -12), ski_stance(3.7, 41, -72, 10, -12), 30,
                           "Tucked at speed on skis"))
    brake_a = board_stance(2.0, -10, (-45, 20), (-45, 20), legs=14)
    brake_b = board_stance(2.2, -12, (-48, 22), (-48, 22), legs=14)
    clips.append(breathing("brake_board", brake_a, brake_b, 8, "Heel-side skid"))
    clips.append(breathing("snowplow_skis", ski_stance(2.0, 10, -20, 18, -4, leg_roll=10),
                           ski_stance(2.2, 11, -22, 19, -4, leg_roll=10), 16, "Snowplow"))

    # ---- pop ----
    charge = board_stance(3.5, 25, (25, 12), (25, 12), legs=14)
    clips.append(clip("pop_charge", [(0, charge, "OUTQUAD"), (4, charge, "LINEAR")], 4, True, "Crouched, charging a pop"))
    extend = board_stance(0, 0, (-60, 20), (-60, 20), legs=8)
    air = board_stance(2.0, 18, (-20, 55), (-20, 55), legs=16, leg_pitch=-10)
    clips.append(clip("pop_launch", [(0, charge, "OUTQUAD"), (2, extend, "INOUTSINE"), (5, air, "INOUTSINE")], 5, False,
                      "Extension off the pop, into the air pose"))

    # ---- air ----
    clips.append(breathing("air_board", air, board_stance(2.2, 19, (-22, 58), (-22, 58), legs=16, leg_pitch=-10), 20,
                           "Neutral air pose"))
    air_skis = ski_stance(2.0, 15, -30, 45, -15)
    clips.append(breathing("air_skis", air_skis, ski_stance(2.2, 16, -32, 47, -15), 20, "Neutral air pose on skis"))

    # ---- landings (quality picks the clip in phase 5) ----
    def landing(name, depth, arms, length=10):
        down = board_stance(depth, 22, arms, arms, legs=14)
        clips.append(clip(name, [(0, air, "OUTQUAD"), (3, down, "INOUTSINE"), (length, glide_a, "INOUTSINE")], length, False,
                          name.replace("_", " ").capitalize()))
    landing("land_perfect", 2.5, (-15, 30))
    landing("land_clean", 3.5, (-20, 45))
    landing("land_sketchy", 4.0, (-70, 80), 12)

    # ---- wipeouts: 30 ticks, the length of the input lock ----
    def wipeout(name, direction):
        start = board_stance(1.5, 12, (-15, 38), (-10, 24), legs=12)
        start["body"] = dict(ROOT)
        fall = pose(2.0, torso=dict(pitch=50 * direction), rarm=dict(pitch=-150 * direction, roll=20),
                    larm=dict(pitch=-150 * direction, roll=-20), rleg=dict(pitch=20 * direction, roll=8),
                    lleg=dict(pitch=10 * direction, roll=-8), body=dict(pitch=-70 * direction, y=-0.35))
        down = copy.deepcopy(fall)
        down["body"]["pitch"] = -80 * direction
        up = board_stance(2.0, 15, (-30, 30), (-30, 30), legs=12)
        up["body"] = dict(ROOT)
        clips.append(clip(name, [(0, start, "OUTQUAD"), (6, fall, "OUTBOUNCE"), (18, down, "LINEAR"),
                                 (26, up, "INOUTSINE"), (30, up, "LINEAR")], 30, False, "Wipeout"))
    wipeout("wipeout_front", 1)
    wipeout("wipeout_back", -1)

    # ---- poles (the hands layer: arms only) ----
    carry = pose(1.5, rarm=dict(pitch=-35, roll=8), larm=dict(pitch=-35, roll=-8), arms_only=True)
    clips.append(breathing("pole_carry", carry, pose(1.5, rarm=dict(pitch=-37, roll=8), larm=dict(pitch=-37, roll=-8), arms_only=True),
                           40, "Carrying poles"))
    for side, arm in (("right", "rightArm"), ("left", "leftArm")):
        reach = copy.deepcopy(carry)
        reach[arm].update(pitch=-75)
        push = copy.deepcopy(carry)
        push[arm].update(pitch=30)
        clips.append(clip(f"pole_push_{side}", [(0, carry, "OUTQUAD"), (3, reach, "INOUTSINE"), (6, push, "OUTQUAD"),
                                                (10, carry, "INOUTSINE")], 10, False, f"Pole push, {side} arm"))
    return clips


def main():
    os.makedirs(OUT, exist_ok=True)
    clips = build()
    for c in clips:
        with open(os.path.join(OUT, c["name"] + ".json"), "w") as f:
            json.dump(c, f, indent=1)
            f.write("\n")
    print(f"wrote {len(clips)} clips to {OUT}")


if __name__ == "__main__":
    main()
