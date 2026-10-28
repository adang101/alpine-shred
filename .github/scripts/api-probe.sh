#!/usr/bin/env bash
# Dumps `javap -p` signatures for the classes listed in .github/api-probe.txt into <out dir>, from the
# remapped (Mojang-named) jars Loom produced. Lets environments without the toolchain see real APIs.
# For net.minecraft classes both the vanilla (Fabric) and Forge-patched versions are written.
set -u
OUT="$1"; LIST="$2"
mkdir -p "$OUT"
INDEX="$OUT/.index"
: > "$INDEX"
while IFS= read -r jar; do
  unzip -Z1 "$jar" 2>/dev/null | sed "s|^|${jar}\t|" >> "$INDEX"
done < <(find ~/.gradle/caches/fabric-loom .gradle/loom-cache */.gradle/loom-cache -name "*.jar" 2>/dev/null)
while IFS= read -r cls; do
  case "$cls" in ''|\#*) continue;; esac
  path="${cls//.//}.class"
  matches=$(grep -F "	${path}" "$INDEX" | cut -f1)
  vanilla=$(echo "$matches" | grep -v -i forge | grep -E "loom.mappings|remapped|named" | head -1)
  forge=$(echo "$matches" | grep -i forge | grep -E "loom.mappings|remapped|named|patched" | head -1)
  any=$(echo "$matches" | head -1)
  first="${vanilla:-${forge:-$any}}"
  if [ -z "$first" ]; then echo "NOT FOUND" > "$OUT/$cls.txt"; continue; fi
  { echo "# $first"; javap -p -cp "$first" "$cls"; } > "$OUT/$cls.txt" 2>&1
  if [ -n "$forge" ] && [ "$forge" != "$first" ] && [[ "$cls" == net.minecraft.* ]]; then
    { echo "# $forge"; javap -p -cp "$forge" "$cls"; } > "$OUT/$cls.forge.txt" 2>&1
  fi
done < "$LIST"
rm -f "$INDEX"
