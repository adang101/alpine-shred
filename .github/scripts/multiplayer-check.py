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
CHAIR = re.compile(r"^chair gt(\d+) (\d+) (\S+) (\S+) (\S+)")
LCHAIR = re.compile(r"^lchair gt(\d+) (\d+) (\S+) (\S+) (\S+)")
CHAIR_TOLERANCE = 0.25


def chairs(path, pattern):
    """(game time, chair index) to its position."""
    out = {}
    try:
        with open(path) as f:
            for line in f:
                m = pattern.match(line)
                if m:
                    out[(int(m.group(1)), int(m.group(2)))] = (float(m.group(3)), float(m.group(4)), float(m.group(5)))
    except FileNotFoundError:
        pass
    return out


def lift_lines(path):
    try:
        with open(path) as f:
            return [line.rstrip() for line in f if line.startswith("lift: ")]
    except FileNotFoundError:
        return []


def check_chairs(server_path, client_paths, after_stall):
    """Compares what each client saw of the chairs with the server's own, by game time. True if they agree."""
    server = chairs(server_path, CHAIR)
    for line in lift_lines(server_path):
        print("multiplayer check: " + line)
    if not server:
        print("multiplayer check: no lift chairs in the server transcript")
        return False
    ok = True
    for path in client_paths:
        devs = []
        shifted = 0
        for (t, i), (x, y, z) in chairs(path, LCHAIR).items():
            if after_stall(t) or (t, i) not in server:
                continue
            d = math.dist((x, y, z), server[(t, i)])
            devs.append(d)
            # Diagnostic only: would the sample agree with the server's chair a tick earlier or later?
            if d > CHAIR_TOLERANCE and any(math.dist((x, y, z), server[(t + dt, i)]) <= CHAIR_TOLERANCE for dt in (-2, -1, 1, 2) if (t + dt, i) in server):
                shifted += 1
        if not devs:
            print(f"multiplayer check: {path}: no chair samples that match the server's")
            ok = False
            continue
        devs.sort()
        bad = sum(1 for d in devs if d > CHAIR_TOLERANCE)
        print(f"multiplayer check: {path}: {len(devs)} chair samples, max deviation {devs[-1]:.3f}, median {devs[len(devs) // 2]:.3f}, "
              f"{bad} over {CHAIR_TOLERANCE} ({shifted} of them within it a tick or two off in time)")
        for line in lift_lines(path):
            print("multiplayer check:   " + line)
        if len(devs) < 50 or bad > 0.05 * len(devs):
            ok = False
    return ok


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
    if server:
        if not check_chairs(server[0], args, after_stall):
            ok = False
    print("multiplayer check:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
