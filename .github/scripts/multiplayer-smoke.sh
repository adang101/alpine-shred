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

CLIENTS=()
for role in rider watcher1 watcher2; do
  run="multiRider"; [ "$role" = watcher1 ] && run="multiWatcher1"; [ "$role" = watcher2 ] && run="multiWatcher2"
  dir="$B/smoke-$role"
  mkdir -p "$dir/config"
  printf '%s\n' onboardAccessibility:false skipMultiplayerWarning:true tutorialStep:none \
    pauseOnLostFocus:false renderDistance:6 simulationDistance:6 narrator:0 maxFps:20 \
    joinedFirstServer:true soundCategory_master:0.0 > "$dir/options.txt"
  printf 'earlyWindowControl = false\n' > "$dir/config/fml.toml"
  xvfb-run -a -s "-screen 0 960x540x24" ./gradlew ":forge:run${run^}" "$@" > "$LOGS/9-multi-$role.log" 2>&1 &
  CLIENTS+=($!)
  sleep 25
done

for pid in "${CLIENTS[@]}"; do wait $pid; done
echo "clients done"
for i in $(seq 1 60); do kill -0 $SERVER 2>/dev/null || break; sleep 5; done
kill $SERVER 2>/dev/null
wait $SERVER 2>/dev/null

status=0
for f in "$B/smoke-server/alpineshred-server-smoke.txt" "$B/smoke-rider/alpineshred-smoke.txt" \
         "$B/smoke-watcher1/alpineshred-smoke.txt" "$B/smoke-watcher2/alpineshred-smoke.txt"; do
  echo "=== $f"
  if [ -f "$f" ]; then grep -vE "^self gt|^watch gt" "$f" | tail -25; grep -q "^RESULT PASS" "$f" || status=1; else echo "missing"; status=1; fi
done
python3 .github/scripts/multiplayer-check.py "$B/smoke-rider/alpineshred-smoke.txt" \
  "$B/smoke-watcher1/alpineshred-smoke.txt" "$B/smoke-watcher2/alpineshred-smoke.txt" || status=1
exit $status
