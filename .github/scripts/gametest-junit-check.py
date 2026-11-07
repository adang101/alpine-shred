#!/usr/bin/env python3
"""Judges a Fabric game-test run from its junit.xml: only Alpine Shred's own tests gate the step.

Usage: gametest-junit-check.py <junit.xml> <testcase-name-prefix> <expected-minimum>

Create's own Fabric game tests share the run in the "with Create" column and sometimes fail on their own
(README 77), so the process exit code cannot be trusted. This passes only when the file exists, at least
<expected-minimum> testcases whose name starts with <testcase-name-prefix> ran, and none of those failed or
errored. Other testcases' failures are printed for information only.
"""
import sys
import xml.etree.ElementTree as ET


def main():
    if len(sys.argv) != 4:
        print("usage: gametest-junit-check.py <junit.xml> <name-prefix> <expected-minimum>")
        return 2
    path, prefix, minimum = sys.argv[1], sys.argv[2], int(sys.argv[3])
    try:
        root = ET.parse(path).getroot()
    except FileNotFoundError:
        print(f"FAIL: {path} does not exist (the server crashed before writing it?)")
        return 1
    except ET.ParseError as e:
        print(f"FAIL: {path} is not valid XML: {e}")
        return 1

    ours, ours_failed, foreign_failed = 0, [], []
    for case in root.iter("testcase"):
        name = case.get("name", "")
        bad = case.find("failure") is not None or case.find("error") is not None
        if name.startswith(prefix):
            ours += 1
            if bad:
                ours_failed.append(name)
        elif bad:
            message = (case.find("failure") if case.find("failure") is not None else case.find("error")).get("message", "")
            foreign_failed.append(f"{case.get('classname', '')} {name}: {message}")

    for line in foreign_failed:
        print(f"info: not ours, not gating: {line}")
    for name in ours_failed:
        print(f"FAIL: {name}")
    ok = ours >= minimum and not ours_failed
    print(f"{'PASS' if ok else 'FAIL'}: {ours} of ours ran (need at least {minimum}), {len(ours_failed)} failed; "
          f"{len(foreign_failed)} foreign failed")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
