#!/bin/bash
# Stops the bot server if it is running, wipes Headcount's data and the players' saved data, starts it again,
# and applies the test-only settings: peaceful, no weather, the test lists (test-lists.yml) and hook messages.
HERE=$(cd "$(dirname "$0")" && pwd)
S=${HC_BOT_DIR:-$HERE/work}; L=$S/botserver.log
touch $S/bot-console.in
running() { for p in $(pgrep -f "paper-.*\.jar"); do [ "$(readlink /proc/$p/cwd)" = "$S/botserver" ] && return 0; done; return 1; }
if running; then
  echo stop >> $S/bot-console.in
  for i in $(seq 1 60); do running || break; sleep 1; done
fi
pkill -f "tail -n0 -f $S/bot-console.in"
rm -rf $S/botserver/plugins/Headcount $S/botserver/world/players
: > $S/bot-console.in; [ -f $L ] && mv $L $S/botserver-prev.log
cd $S/botserver && (tail -n0 -f $S/bot-console.in | ./start.sh > $L 2>&1; echo "EXIT $?" >> $L) > /dev/null 2>&1 &
for i in $(seq 1 150); do grep -qE "Done \(|EXIT " $L 2>/dev/null && break; sleep 1; done
grep -q "EXIT " $L && { echo "the bot server did not start; see $L"; exit 1; }
echo "difficulty peaceful" >> $S/bot-console.in
echo "minecraft:gamerule advance_weather false" >> $S/bot-console.in
echo "minecraft:weather clear" >> $S/bot-console.in
python3 - "$S/botserver/plugins/Headcount" "$HERE/test-lists.yml" <<'PY'
import sys
d, lists = sys.argv[1] + "/", sys.argv[2]
body = "".join(l for l in open(lists) if not l.startswith("#"))
a = open(d + "actions.yml").read()
if "  ta-message:" not in a:
    a = a.replace("lists:\n", "lists:\n" + body, 1)
    open(d + "actions.yml", "w").write(a)
c = open(d + "config.yml").read()
if "HOOK became-active" not in c:   # messages in the two empty player hooks, so the join order can be checked
    c = c.replace("    became-active: []\n    join: []", '    became-active:\n      - message: "HOOK became-active"\n    join:\n      - message: "HOOK join"', 1)
    open(d + "config.yml", "w").write(c)
PY
echo "hca reload" >> $S/bot-console.in; sleep 3
