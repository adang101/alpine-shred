#!/usr/bin/env python3
"""Checks the watchers' view of the rider against where the rider really was, by game time.

The rider's transcript has "self gt<time> <state> x y z" every tick; each watcher's has
"watch gt<time> <state> x y z clip <clip>". A remote player is drawn a few ticks behind (the
server sends player moves every other tick and the client interpolates over three), so each
watched sample must lie within 1.0 block of the rider's own track in the 10 ticks before it, and
its state must be one the rider was in during the 12 ticks before it. Fails if more than 5% of
samples miss either test.
"""
import math
import re
import sys

SELF = re.compile(r"^self gt(\d+) (\w+) (\S+) (\S+) (\S+)")
WATCH = re.compile(r"^watch gt(\d+) (\w+) (\S+) (\S+) (\S+)")


def read(path, pattern):
    out = []
    try:
        with open(path) as f:
            for line in f:
                m = pattern.match(line)
                if m:
                    out.append((int(m.group(1)), m.group(2), float(m.group(3)), float(m.group(4)), float(m.group(5))))
    except FileNotFoundError:
        pass
    return out


def main():
    rider = read(sys.argv[1], SELF)
    by_time = {}
    for t, state, x, y, z in rider:
        by_time.setdefault(t, []).append((state, x, y, z))
    if not rider:
        print("multiplayer check: no rider track")
        return 1
    ok = True
    for path in sys.argv[2:]:
        samples = [s for s in read(path, WATCH) if s[1] not in ("OFF",)]
        moving = [s for s in samples if rider[0][0] <= s[0] <= rider[-1][0]]
        far, wrong = 0, 0
        for t, state, x, y, z in moving:
            near = min((math.dist((x, y, z), p[1:]) for dt in range(0, 11) for p in by_time.get(t - dt, [])), default=99)
            if near > 1.0:
                far += 1
            states = {p[0] for dt in range(0, 13) for p in by_time.get(t - dt, [])}
            if states and state not in states:
                wrong += 1
        n = max(1, len(moving))
        print(f"multiplayer check: {path}: {len(moving)} samples while riding, {far} off the track, {wrong} in a state the rider was not in")
        if len(moving) < 20 or far > 0.05 * n or wrong > 0.05 * n:
            ok = False
    print("multiplayer check:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
