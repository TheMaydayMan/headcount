#!/usr/bin/env bash
# Starts the local Headcount test server with Java 25 (Paper 26.3 requires it).
# Build the plugin first: (cd ../Headcount && ./gradlew build) copies the jar into plugins/.
cd "$(dirname "$0")"
if [ ! -f paper-26.3-246.jar ]; then
  echo "The server isn't set up yet: run ./setup.sh first (it downloads Paper and the plugins)." >&2
  exit 1
fi
# The first Java that is 25 or newer: the JDK installed for this project, then JAVA_HOME, then the PATH.
JAVA=""
for j in "$HOME/.jdks/jdk-25.0.5+8/bin/java" "${JAVA_HOME:+$JAVA_HOME/bin/java}" "$(command -v java)"; do
  [ -n "$j" ] && [ -x "$j" ] || continue
  v=$("$j" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
  if [ "${v:-0}" -ge 25 ]; then JAVA="$j"; break; fi
done
if [ -z "$JAVA" ]; then
  echo "Paper 26.3 needs Java 25 or newer (looked in ~/.jdks/jdk-25.0.5+8, JAVA_HOME and the PATH)." >&2
  exit 1
fi
# `restart` (or a crash) makes restart.sh leave a .restart marker; then the server starts again here, in this
# terminal. `stop` exits for good.
rm -f .restart
while true; do
# Paper writes spigot.yml on the first start; point its restart script at restart.sh (not this script, which would
# start a second server detached from this terminal).
if [ -f spigot.yml ] && grep -q "restart-script: ./start.sh" spigot.yml; then
  sed 's#restart-script: ./start.sh#restart-script: ./restart.sh#' spigot.yml > spigot.yml.tmp && mv spigot.yml.tmp spigot.yml
fi
"$JAVA" -Xms1G -Xmx2G \
  -XX:+UseCompactObjectHeaders -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem \
  -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1MaxNewSizePercent=40 \
  -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 \
  -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32 \
  -jar paper-26.3-246.jar --nogui "$@"
  [ -f .restart ] || break
  rm -f .restart
  echo "Restarting in 3 seconds (Ctrl+C to quit)..."
  sleep 3
done
