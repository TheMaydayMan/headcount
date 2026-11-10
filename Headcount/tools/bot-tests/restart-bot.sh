#!/bin/bash
# Starts the bot server again without wiping anything (after a stop or a kill -9).
S=${HC_BOT_DIR:-$(cd "$(dirname "$0")" && pwd)/work}; L=$S/botserver.log
pkill -f "tail -n0 -f $S/bot-console.in"
: > $S/bot-console.in; [ -f $L ] && cat $L >> $S/botserver-prev.log
cd $S/botserver && (tail -n0 -f $S/bot-console.in | ./start.sh > $L 2>&1; echo "EXIT $?" >> $L) > /dev/null 2>&1 &
for i in $(seq 1 150); do grep -qE "Done \(|EXIT " $L 2>/dev/null && break; sleep 1; done
sleep 2
