#!/usr/bin/env python3
"""
Copies the built showcase world into the dev client's saves and makes it a single-player world with
cheats on (a dedicated server writes allowCommands = 0). Needs nbtlib (pip install nbtlib).

    python tools/showcase/install.py [server run dir] [saves dir]
"""
import os
import shutil
import sys

import nbtlib

src = os.path.join(sys.argv[1] if len(sys.argv) > 1 else os.path.join("forge", "build", "showcase-server"), "world")
saves = sys.argv[2] if len(sys.argv) > 2 else os.path.join("forge", "run", "saves")
name = "Alpine Shred Showcase"
dst = os.path.join(saves, name)

if os.path.exists(dst):
    shutil.rmtree(dst)
shutil.copytree(src, dst, ignore=shutil.ignore_patterns("session.lock"))

level = nbtlib.load(os.path.join(dst, "level.dat"))
data = level["Data"]
data["LevelName"] = nbtlib.String(name)
data["allowCommands"] = nbtlib.Byte(1)
data["GameType"] = nbtlib.Int(1)  # creative to start; /gamemode survival to feel wear and wipeouts
data["Difficulty"] = nbtlib.Byte(0)
level.save()
for stale in ("level.dat_old",):
    p = os.path.join(dst, stale)
    if os.path.exists(p):
        os.remove(p)
print("installed", os.path.abspath(dst))
