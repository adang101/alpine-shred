#!/usr/bin/env bash
# Copies the jars nested inside the pinned Create Fabric release into fabric/build/create-nested, which
# fabric/build.gradle reads: as compile-only mods always (the kinetic drive extends Create's block entity, and
# javac needs its whole supertype chain, which reaches into Porting Lib), and as runtime mods under -PwithCreate.
# Loom does not unpack jar-in-jar content itself. The download is pinned by sha256 (gradle.properties,
# create_fabric_version). Run from the repository root, before any Gradle task that compiles :fabric.
set -euo pipefail
CREATE_FABRIC_VERSION=6.0.8.1+build.1744-mc1.20.1
CREATE_FABRIC_SHA256=a613603fb08d967c5a4138d1adb7374c4a347b91e489fe21579a4cff4f40105a
TMP="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
curl -fsSL --retry 3 -o "$TMP/create-fabric.jar" \
  "https://api.modrinth.com/maven/maven/modrinth/create-fabric/$CREATE_FABRIC_VERSION/create-fabric-$CREATE_FABRIC_VERSION.jar"
echo "$CREATE_FABRIC_SHA256  $TMP/create-fabric.jar" | sha256sum -c -
# Porting Lib's modules nest further modules (fluids, core, ...), and Loom strips nested jars from
# every jar it remaps, so unpack to a fixed point (three levels is more than enough), then drop the
# plain libraries and keep the newest of each module.
W="$TMP/create-nested-work"; D=fabric/build/create-nested
mkdir -p "$W/all" "$D"
unzip -jo -q "$TMP/create-fabric.jar" 'META-INF/jars/*.jar' -d "$W/all"
for pass in 1 2 3; do
  for j in "$W"/all/*.jar; do unzip -jo -q "$j" 'META-INF/jars/*.jar' -d "$W/all" 2>/dev/null || true; done
done
rm -f "$W"/all/core-3.* "$W"/all/toml-3.* "$W"/all/jsr305-*
ls "$W/all" | sed -E 's/-[0-9][^/]*\.jar$//' | sort -u | while read -r base; do
  cp "$W/all/$(ls "$W/all" | grep -E "^${base}-[0-9]" | sort -V | tail -1)" "$D/"
done
ls "$D"
test "$(ls "$D" | wc -l)" -ge 15
