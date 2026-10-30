#!/usr/bin/env bash
# Phase 2's exit gate, "three clients see correct state": a Forge dedicated server and three Forge
# clients (a rider and two watchers) on one machine, each its own Gradle run. Usage:
#   multiplayer-smoke.sh <log dir> [gradle args...]
set -uo pipefail
LOGS="$1"; shift
exec > >(tee "$LOGS/9-multi.log") 2>&1
B=forge/build
mkdir -p "$B/smoke-server"
echo "eula=true" > "$B/smoke-server/eula.txt"
cat > "$B/smoke-server/server.properties" <<'PROPS'
online-mode=false
level-type=minecraft\:flat
gamemode=creative
difficulty=peaceful
spawn-protection=0
max-players=4
view-distance=6
simulation-distance=6
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
generate-structures=false
enforce-secure-profile=false
motd=Alpine Shred multiplayer smoke
PROPS

./gradlew :forge:runSmokeServer "$@" > "$LOGS/9-multi-server.log" 2>&1 &
SERVER=$!
for i in $(seq 1 120); do
  grep -q "Done (" "$B/smoke-server/logs/latest.log" 2>/dev/null && break
  if ! kill -0 $SERVER 2>/dev/null; then echo "server exited early"; tail -40 "$LOGS/9-multi-server.log"; exit 1; fi
  sleep 5
done
grep -q "Done (" "$B/smoke-server/logs/latest.log" || { echo "server never started"; tail -40 "$LOGS/9-multi-server.log"; kill $SERVER; exit 1; }
echo "server up"

# Each client gets its own X server (xvfb-run -a can hand two clients the same display, and a
# client whose window never opens hangs before the mod loads), and a watchdog: a client that has
# not started loading mods 4 minutes after launch is killed and launched once more.
XPIDS=()
start_x() {
  Xvfb ":$1" -screen 0 960x540x24 -nolisten tcp > /dev/null 2>&1 &
  XPIDS+=($!)
  for _ in $(seq 1 50); do [ -e "/tmp/.X11-unix/X$1" ] && return 0; sleep 0.2; done
  echo "Xvfb :$1 did not start"; return 1
}
kill_client() { pkill -f -- "--username $1" 2>/dev/null; }
declare -A PIDS DISPLAYS
launch() {
  local role=$1 run="multiRider"
  [ "$role" = watcher1 ] && run="multiWatcher1"; [ "$role" = watcher2 ] && run="multiWatcher2"
  DISPLAY=":${DISPLAYS[$role]}" ./gradlew ":forge:run${run^}" "${GRADLE_ARGS_ARR[@]}" >> "$LOGS/9-multi-$role.log" 2>&1 &
  PIDS[$role]=$!
}
GRADLE_ARGS_ARR=("$@")
n=91
for role in rider watcher1 watcher2; do
  dir="$B/smoke-$role"
  mkdir -p "$dir/config"
  rm -f "$dir/alpineshred-smoke.txt"
  printf '%s\n' onboardAccessibility:false skipMultiplayerWarning:true tutorialStep:none \
    pauseOnLostFocus:false renderDistance:6 simulationDistance:6 narrator:0 maxFps:20 \
    joinedFirstServer:true soundCategory_master:0.0 > "$dir/options.txt"
  printf 'earlyWindowControl = false\n' > "$dir/config/fml.toml"
  DISPLAYS[$role]=$n; n=$((n + 1))
  start_x "${DISPLAYS[$role]}" || exit 1
  : > "$LOGS/9-multi-$role.log"
  launch "$role"
  # Watchdog: started loading within 4 minutes, or one more try.
  for _ in $(seq 1 48); do
    grep -q "Forge mod loading" "$LOGS/9-multi-$role.log" && break
    kill -0 "${PIDS[$role]}" 2>/dev/null || break
    sleep 5
  done
  if ! grep -q "Forge mod loading" "$LOGS/9-multi-$role.log"; then
    echo "$role did not start loading; launching it again"
    kill_client "$role"; kill "${PIDS[$role]}" 2>/dev/null; wait "${PIDS[$role]}" 2>/dev/null
    echo "==== second launch" >> "$LOGS/9-multi-$role.log"
    launch "$role"
  fi
  sleep 10
done

# The server's smoke ends the session (and stops the server) when the watchers are done or it
# times out; clients get a minute after that to write their transcripts, then are stopped.
for _ in $(seq 1 180); do kill -0 $SERVER 2>/dev/null || break; sleep 5; done
kill $SERVER 2>/dev/null
wait $SERVER 2>/dev/null
for _ in $(seq 1 12); do
  alive=0
  for role in rider watcher1 watcher2; do kill -0 "${PIDS[$role]}" 2>/dev/null && alive=1; done
  [ $alive = 0 ] && break
  sleep 5
done
for role in rider watcher1 watcher2; do
  if kill -0 "${PIDS[$role]}" 2>/dev/null; then echo "$role still running; stopping it"; kill_client "$role"; fi
  kill "${PIDS[$role]}" 2>/dev/null; wait "${PIDS[$role]}" 2>/dev/null
done
echo "clients done"
for x in "${XPIDS[@]}"; do kill "$x" 2>/dev/null; done

status=0
for f in "$B/smoke-server/alpineshred-server-smoke.txt" "$B/smoke-rider/alpineshred-smoke.txt" \
         "$B/smoke-watcher1/alpineshred-smoke.txt" "$B/smoke-watcher2/alpineshred-smoke.txt"; do
  echo "=== $f"
  if [ -f "$f" ]; then grep -vE "^self gt|^watch gt" "$f" | tail -25; grep -q "^RESULT PASS" "$f" || status=1; else echo "missing"; status=1; fi
done
python3 .github/scripts/multiplayer-check.py "$B/smoke-server/alpineshred-server-smoke.txt" "$B/smoke-rider/alpineshred-smoke.txt" \
  "$B/smoke-watcher1/alpineshred-smoke.txt" "$B/smoke-watcher2/alpineshred-smoke.txt" || status=1
exit $status
