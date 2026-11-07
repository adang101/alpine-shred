#!/usr/bin/env bash
# Prints the CHANGELOG.md section for one version (the lines under "## [<version>]" up to the next "## "), failing
# when the section is missing or empty. usage: changelog-section.sh <version without v> [file]
set -euo pipefail
v="$1"; f="${2:-CHANGELOG.md}"
out=$(awk -v h="## [$v]" 'index($0,h)==1{on=1;next} /^## /{on=0} on' "$f")
if [ -z "$(printf '%s' "$out" | tr -d '[:space:]')" ]; then
  echo "::error::no CHANGELOG.md section for $v" >&2; exit 1
fi
printf '%s\n' "$out"
