#!/bin/bash
# One-time setup for the bot tests (see README.md). Run it again any time to rebuild the bot server from the
# current test server. It makes, in work/ (or $HC_BOT_DIR):
#   botserver/   a copy of ../../../TestServer (or $HC_TEST_SERVER) on port 25566, with ViaVersion and ViaBackwards,
#                without DiscordSRV, Geyser and Floodgate, and with the test addon
# and installs mineflayer here. Needs Node.js 20+, curl, python3, and JDK 25 (found like TestServer/start.sh).
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
PROJECT=$(cd "$HERE/../.." && pwd)
S=${HC_BOT_DIR:-$HERE/work}
TS=${HC_TEST_SERVER:-$(cd "$PROJECT/../TestServer" && pwd)}
B=$S/botserver

# --- tools -----------------------------------------------------------------------------------------------
command -v node >/dev/null || { echo "Node.js 20 or newer is needed"; exit 1; }
JDK=""
for j in "$HOME/.jdks/jdk-25.0.5+8" "$JAVA_HOME"; do
  [ -n "$j" ] && [ -x "$j/bin/javac" ] || continue
  v=$("$j/bin/javac" -version 2>&1 | sed -n 's/javac \([0-9]*\).*/\1/p')
  if [ "${v:-0}" -ge 25 ]; then JDK=$j; break; fi
done
[ -n "$JDK" ] || { echo "JDK 25 is needed (looked in ~/.jdks/jdk-25.0.5+8 and JAVA_HOME)"; exit 1; }
[ -f "$TS/start.sh" ] || { echo "No test server at $TS: run TestServer/setup.sh first (or set HC_TEST_SERVER)"; exit 1; }
for p in $(pgrep -f "paper-.*\.jar"); do
  [ "$(readlink /proc/$p/cwd)" = "$B" ] && { echo "The bot server is running; stop it first (echo stop >> $S/bot-console.in)"; exit 1; }
done

# --- the bot server --------------------------------------------------------------------------------------
echo "Copying $TS to $B"
rm -rf "$B"; mkdir -p "$B/plugins"
cp -r "$TS"/paper-*.jar "$TS/eula.txt" "$TS/start.sh" "$B/"
[ -d "$TS/libraries" ] && cp -r "$TS/libraries" "$B/"
for f in "$TS"/plugins/*; do
  n=$(basename "$f")
  case "$n" in Headcount|DiscordSRV*|Geyser*|[Ff]loodgate*|spark|update) continue ;; esac
  cp -r "$f" "$B/plugins/"
done
echo "Downloading ViaVersion and ViaBackwards"
curl -sSfLo "$B/plugins/ViaVersion.jar" https://cdn.modrinth.com/data/P1OZGk5p/versions/OKm4Zi14/ViaVersion-5.12.3-SNAPSHOT.jar
curl -sSfLo "$B/plugins/ViaBackwards.jar" https://cdn.modrinth.com/data/NpvuJQoq/versions/4jOs3Inr/ViaBackwards-5.12.3-SNAPSHOT.jar
cat > "$B/server.properties" <<'PROPS'
online-mode=false
enforce-secure-profile=false
server-port=25566
white-list=false
spawn-protection=0
view-distance=4
simulation-distance=4
level-type=minecraft\:flat
generate-structures=false
spawn-monsters=false
difficulty=peaceful
max-players=20
motd=Headcount bot test
PROPS

# --- builds: Headcount and the test addon ----------------------------------------------------------------
echo "Building Headcount and the test addon"
(cd "$PROJECT" && ./gradlew -q build)
CP=$(cd "$PROJECT" && ./gradlew -q -I "$HERE/addon/printcp.gradle" printTestClasspath | sed -n 's/^CP=//p')
OUT=$(mktemp -d)
"$JDK/bin/javac" -d "$OUT" -cp "$CP" "$HERE/addon/hctest/TestAddon.java"
cp "$HERE/addon/plugin.yml" "$OUT/"
(cd "$OUT" && "$JDK/bin/jar" cf "$B/plugins/HeadcountTestAddon.jar" plugin.yml hctest)
rm -rf "$OUT"
cp "$(ls "$PROJECT"/build/libs/Headcount-*.jar | head -1)" "$B/plugins/"

# --- first boot, so every plugin writes its config; then PvPManager's newbie protection off ----------------
echo "First boot of the bot server"
HC_BOT_DIR="$S" "$HERE/reset-bot.sh"
echo stop >> "$S/bot-console.in"
for i in $(seq 1 60); do grep -q "EXIT " "$S/botserver.log" && break; sleep 1; done
python3 - "$B/plugins/PvPManager/config.yml" <<'PY'
import sys
p = sys.argv[1]
s = open(p).read()
s = s.replace("Newbie Protection:\n  Enabled: true", "Newbie Protection:\n  Enabled: false", 1)
assert "Newbie Protection:\n  Enabled: false" in s, "PvPManager's config.yml changed; turn newbie protection off by hand"
open(p, "w").write(s)
PY

# --- mineflayer --------------------------------------------------------------------------------------------
echo "Installing mineflayer"
(cd "$HERE" && npm install --no-audit --no-fund --silent)
echo "Done. Run ./run-all.sh (about 25 minutes), or start the server with ./reset-bot.sh and run one suite with node."
