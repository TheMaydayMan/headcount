#!/bin/bash
# kill -9 the bot server's java process (found by its working directory), like a crash.
S=${HC_BOT_DIR:-$(cd "$(dirname "$0")" && pwd)/work}
for p in $(pgrep -f "paper-.*\.jar"); do
  if [ "$(readlink /proc/$p/cwd)" = "$S/botserver" ]; then kill -9 $p; echo "killed $p"; fi
done
sleep 3; pkill -f "tail -n0 -f $S/bot-console.in"; true
