#!/bin/bash
# The whole bot test run on a wiped bot server (Headcount data and player data deleted), in dependency order.
# Each suite's full output goes to runs/<suite>.out; the summary to runs/summary.txt.
HERE=$(cd "$(dirname "$0")" && pwd)
S=${HC_BOT_DIR:-$HERE/work}
R=$S/runs; rm -rf $R; mkdir -p $R
cp "$HERE/../../build/libs/Headcount-1.0.0.jar" $S/botserver/plugins/
sha256sum $S/botserver/plugins/Headcount-1.0.0.jar | cut -c1-16 > $R/jar.sha
"$HERE/reset-bot.sh"
: > $S/botserver-prev.log   # scan only this run's logs for warnings
cd "$HERE"
run() {
  local name=$1; shift
  echo "== $name $(date +%H:%M:%S)" >> $R/summary.txt
  timeout 600 node $name.js "$@" > $R/$name.out 2>&1
  grep -vE "PartialReadError|^\s+at " $R/$name.out | grep -E "^FAIL|passed, |HARNESS" >> $R/summary.txt
}
run tests-vault
run tests-play
sed -i 's/confirm-at-price: 50/confirm-at-price: 2/' $S/botserver/plugins/Headcount/shop.yml; echo "hca reload" >> $S/bot-console.in; sleep 3
run tests-more
run tests-final
run tests-dupe
run tests-rest
run tests-actions
run tests-outcomes
run tests-shop
run tests-misc
run tests-status
run tests-status2
run tests-score
run tests-moderation
run tests-api
# crash tests last: they kill and restart the server
echo "== crash-offer $(date +%H:%M:%S)" >> $R/summary.txt
rm -f $S/crash-ready
(timeout 90 node crash-offer.js > $R/crash-offer.out 2>&1) &
for i in $(seq 1 60); do [ -f $S/crash-ready ] && break; sleep 1; done
sleep 0.5; "$HERE/kill-bot.sh" >> $R/summary.txt; wait; "$HERE/restart-bot.sh"
grep "SETUP FAILED" $R/crash-offer.out >> $R/summary.txt
timeout 60 node crash-rejoin.js 2>&1 | grep "after restart" >> $R/summary.txt
echo "== crash-take $(date +%H:%M:%S)" >> $R/summary.txt
python3 - <<PY
d="$S/botserver/plugins/Headcount/"
a=open(d+"actions.yml").read()
if "t-take:" not in a:
    a=a.replace("lists:\n","lists:\n  t-take:\n    - type: take-item\n      material: COBBLESTONE\n      amount: 5\n      duration: 20s\n",1)
open(d+"actions.yml","w").write(a)
PY
echo "hca reload" >> $S/bot-console.in; sleep 3
rm -f $S/crash-ready
(timeout 90 node crash-take.js > $R/crash-take.out 2>&1) &
for i in $(seq 1 60); do [ -f $S/crash-ready ] && break; sleep 1; done
sleep 0.5; "$HERE/kill-bot.sh" >> $R/summary.txt; wait; "$HERE/restart-bot.sh"
timeout 60 node crash-take2.js 2>&1 | grep "cobble" >> $R/summary.txt
grep -iE "WARN.*Headcount|ERROR.*Headcount|\[Headcount\].*(WARN|ERROR)|Headcount.*Exception" $S/botserver-prev.log $S/botserver.log 2>/dev/null | grep -vE "t-loop|test" | head -20 > $R/headcount-warnings.txt
echo "DONE $(date +%H:%M:%S)" >> $R/summary.txt
