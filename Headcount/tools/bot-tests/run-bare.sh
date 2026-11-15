#!/bin/bash
# The 1.1 suites on the bare server (setup-bare.sh): Headcount alone, so every job runs on its included version.
# Wipes the bare server's Headcount and player data first. Order matters: tests-bare-pvp needs players who are still
# new, tests-bare-reset wipes everything, and tests-bare-config ends a cycle and restarts the server, so it's last.
# Each suite's output goes to work-bare/runs/<suite>.out, the summary to work-bare/runs/summary.txt.
HERE=$(cd "$(dirname "$0")" && pwd)
export HC_BOT_DIR=${HC_BOT_DIR:-$HERE/work-bare}
export HC_BOT_PORT=${HC_BOT_PORT:-25567}
S=$HC_BOT_DIR
[ -d "$S/botserver" ] || { echo "No bare server: run ./setup-bare.sh first"; exit 1; }
R=$S/runs; rm -rf $R; mkdir -p $R
JAR=$(ls "$HERE"/../../build/libs/Headcount-*.jar | head -1)
running() { for p in $(pgrep -f "paper-.*\.jar"); do [ "$(readlink /proc/$p/cwd)" = "$S/botserver" ] && return 0; done; return 1; }
if running; then echo stop >> $S/bot-console.in; for i in $(seq 1 60); do running || break; sleep 1; done; fi
rm -f $S/botserver/plugins/Headcount-*.jar; cp "$JAR" $S/botserver/plugins/
rm -rf $S/botserver/plugins/PlaceholderAPI* # left by an interrupted tests-bare-config
sha256sum "$JAR" | cut -c1-16 > $R/jar.sha
"$HERE/reset-bot.sh"
: > $S/botserver-prev.log
cd "$HERE"
for name in tests-bare-pvp tests-bare-included tests-bare-studio tests-bare-reset tests-bare-config; do
  echo "== $name $(date +%H:%M:%S)" >> $R/summary.txt
  timeout 600 node $name.js > $R/$name.out 2>&1
  grep -vE "PartialReadError|^\s+at " $R/$name.out | grep -E "^FAIL|^[0-9]+ passed, |HARNESS" >> $R/summary.txt
done
# Headcount's own warnings and errors, less the ones the suites cause on purpose
grep -hE "(WARN|ERROR)\]: \[Headcount\]|Headcount.*Exception" $S/botserver-prev.log $S/botserver.log 2>/dev/null \
  | grep -vE "t-loop|test|nonsense|skipped because it needs LuckPerms|Hard reset" | head -20 > $R/headcount-warnings.txt
echo "DONE $(date +%H:%M:%S)" >> $R/summary.txt
cat $R/summary.txt
