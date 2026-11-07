#!/usr/bin/env python3
"""Checks the metadata inside a built mod jar (WP8, README 89).

usage: verify-jar.py <fabric|forge> <jar> <mod version>   (version as built, e.g. 1.0.0+mc1.20.1)

Reads fabric.mod.json or META-INF/mods.toml straight out of the jar, so it checks what a player's loader will
read. Exits 1 on any mismatch. Run with `python3 -I`.
"""
import json
import sys
import tomllib
import zipfile

MOD_ID = "alpineshred"
errors = []


def check(ok, what):
    if not ok:
        errors.append(what)


def fabric(z, version):
    m = json.loads(z.read("fabric.mod.json"))
    check(m.get("id") == MOD_ID, f"id {m.get('id')!r}")
    check(m.get("version") == version, f"version {m.get('version')!r}")
    deps = m.get("depends", {})
    check(deps.get("minecraft") == "~1.20.1", f"minecraft range {deps.get('minecraft')!r}")
    check(str(deps.get("fabricloader", "")).startswith(">="), "fabricloader range")
    check("create" not in deps, "create must not be a hard dependency")
    soft = m.get("suggests", {}).get("create") or m.get("recommends", {}).get("create")
    check(soft not in (None, "*"), f"create suggests/recommends needs a version range, got {soft!r}")
    return f"fabric.mod.json id={m.get('id')} version={m.get('version')} minecraft={deps.get('minecraft')} create(suggests)={soft}"


def forge(z, version):
    m = tomllib.loads(z.read("META-INF/mods.toml").decode())
    mods = m.get("mods", [])
    check(len(mods) == 1 and mods[0].get("modId") == MOD_ID, f"mods {mods!r}")
    mod = mods[0] if mods else {}
    check(mod.get("version") == version, f"version {mod.get('version')!r}")
    deps = {d["modId"]: d for d in m.get("dependencies", {}).get(MOD_ID, [])}
    mc = deps.get("minecraft", {})
    check(mc.get("versionRange") == "[1.20.1,1.20.2)", f"minecraft range {mc.get('versionRange')!r}")
    check(str(m.get("loaderVersion", "")).startswith("[47"), f"loaderVersion {m.get('loaderVersion')!r}")
    c = deps.get("create")
    check(c is not None, "no create dependency")
    if c is not None:
        check(c.get("mandatory") is False, "create must be mandatory=false")
        check(c.get("side") == "BOTH", f"create side {c.get('side')!r}")
        check(c.get("versionRange") not in (None, "*", ""), "create needs a version range")
    return (f"mods.toml modId={mod.get('modId')} version={mod.get('version')} minecraft={mc.get('versionRange')} "
            f"loaderVersion={m.get('loaderVersion')} create(mandatory={c and c.get('mandatory')}, "
            f"side={c and c.get('side')}, range={c and c.get('versionRange')})")


def main():
    loader, jar, version = sys.argv[1:4]
    with zipfile.ZipFile(jar) as z:
        summary = {"fabric": fabric, "forge": forge}[loader](z, version)
        check(any(n.startswith("io/github/adang101/alpineshred/") for n in z.namelist()), "no mod classes in jar")
    print(f"{jar}: {summary}")
    if errors:
        for e in errors:
            print(f"::error::{jar}: {e}")
        sys.exit(1)
    print("OK")


main()
