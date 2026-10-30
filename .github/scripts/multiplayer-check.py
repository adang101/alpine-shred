#!/usr/bin/env python3
"""Checks the watchers' view of the rider against where the rider really was, by game time.

The rider's transcript has "self gt<time> <state> x y z" every tick; each watcher's has
"watch gt<time> <state> x y z clip <clip>". A remote player is drawn a few ticks behind (the
server sends player moves every other tick and the client interpolates over three), so each
watched sample must lie within 1.0 block of the rider's own track in the 10 ticks before it, and
its state must be one the rider was in during the 12 ticks before it. Fails if more than 5% of
samples miss either test. Samples from just after a server stall (the server's transcript notes
"stall gt<time> <ms> ms") are left out: while the server stands still the watchers rightly see the
rider stand still, and the clients' clocks drift apart until the next time sync.
"""
import math
import re
import sys

SELF = re.compile(r"^self gt(\d+) (\w+) (\S+) (\S+) (\S+)")
WATCH = re.compile(r"^watch gt(\d+) (\w+) (\S+) (\S+) (\S+)")
STALL = re.compile(r"^stall gt(\d+) (\d+) ms")


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


def stalls(path):
    out = []
    try:
        with open(path) as f:
            for line in f:
                m = STALL.match(line)
                if m:
                    out.append((int(m.group(1)), int(m.group(2))))
    except FileNotFoundError:
        pass
    return out


def main():
    args = sys.argv[1:]
    server = [a for a in args if a.endswith("server-smoke.txt")]
    args = [a for a in args if a not in server]
    stalled = stalls(server[0]) if server else []
    rider = read(args[0], SELF)
    by_time = {}
    for t, state, x, y, z in rider:
        by_time.setdefault(t, []).append((state, x, y, z))
    if not rider:
        print("multiplayer check: no rider track")
        return 1
    ok = True
    if stalled:
        print("multiplayer check: server stalls " + ", ".join(f"gt{t} ({ms} ms)" for t, ms in stalled))

    def after_stall(t):
        return any(st - 2 <= t <= st + 30 + ms // 50 for st, ms in stalled)

    for path in args[1:]:
        samples = [s for s in read(path, WATCH) if s[1] not in ("OFF",) and not after_stall(s[0])]
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
