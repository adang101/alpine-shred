#!/usr/bin/env bash
# Runs Fabric datagen and stops the game JVM once generation has finished.
#
# Data generation completes and writes every file, but the JVM then stays alive (a non-daemon
# thread from a dependency), so a plain `./gradlew :fabric:runDatagen` never returns. This waits for
# the cache summary line datagen prints at the end, records a thread dump for diagnosis, stops the
# process, and decides success from the log rather than the (killed) process exit code.
#
# Usage: .github/scripts/run-datagen.sh <log file> [extra gradle args...]
set -u
LOG="$1"
shift
./gradlew :fabric:runDatagen "$@" > "$LOG" 2>&1 &
GRADLE_PID=$!

finished=0
for _ in $(seq 1 180); do
  if grep -q "Caching: total files" "$LOG"; then finished=1; break; fi
  if ! kill -0 "$GRADLE_PID" 2>/dev/null; then break; fi
  sleep 5
done

if kill -0 "$GRADLE_PID" 2>/dev/null; then
  sleep 5
  GAME_PID=$(pgrep -f -- "-Dfabric-api.datagen" | head -1)
  if [ -n "$GAME_PID" ]; then
    echo "=== datagen finished=${finished}; JVM ${GAME_PID} still running, thread dump follows" >> "$LOG"
    jstack "$GAME_PID" >> "$LOG" 2>&1 || true
    kill "$GAME_PID" 2>/dev/null
  fi
  sleep 5
  kill "$GRADLE_PID" 2>/dev/null
fi
wait "$GRADLE_PID" 2>/dev/null
cat "$LOG"

if [ "$finished" = 1 ] && ! grep -q "Failed to run data generation" "$LOG"; then
  echo "Data generation succeeded."
  exit 0
fi
echo "::error::Data generation failed; see the log above."
exit 1
