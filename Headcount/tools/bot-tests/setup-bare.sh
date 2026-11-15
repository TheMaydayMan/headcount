#!/bin/bash
# Makes a second bot server with no plugins but Headcount (and ViaVersion and ViaBackwards, so the bots can join),
# in work-bare/ (or $HC_BOT_DIR) on port 25567. Every job runs on Headcount's included version there, as on a fresh
# install with no other plugins: the setup the 1.1 suites (tests-bare-*.js) test. Run ./setup.sh first, which this
# copies the server jar from. Run the suites with HC_BOT_DIR=work-bare HC_BOT_PORT=25567, or use run-bare.sh.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
PROJECT=$(cd "$HERE/../.." && pwd)
SRC=$HERE/work/botserver
S=${HC_BOT_DIR:-$HERE/work-bare}
B=$S/botserver
[ -f "$SRC/eula.txt" ] || { echo "Run ./setup.sh first (this copies its server)"; exit 1; }
for p in $(pgrep -f "paper-.*\.jar"); do
  [ "$(readlink /proc/$p/cwd)" = "$B" ] && { echo "The bare bot server is running; stop it first (echo stop >> $S/bot-console.in)"; exit 1; }
done
rm -rf "$B"; mkdir -p "$B/plugins"
cp "$SRC"/paper-*.jar "$SRC/eula.txt" "$SRC/start.sh" "$HERE/../../../TestServer/restart.sh" "$B/"
[ -d "$SRC/libraries" ] && cp -r "$SRC/libraries" "$B/"
cp "$SRC/plugins/ViaVersion.jar" "$SRC/plugins/ViaBackwards.jar" "$B/plugins/"
sed 's/^server-port=25566$/server-port=25567/; s/^motd=.*/motd=Headcount bare bot test/' "$SRC/server.properties" > "$B/server.properties"
(cd "$PROJECT" && ./gradlew -q build)
cp "$(ls "$PROJECT"/build/libs/Headcount-*.jar | head -1)" "$B/plugins/"
echo "Bare bot server ready in $B. Start it with HC_BOT_DIR=$S ./reset-bot.sh"
