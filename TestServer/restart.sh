#!/usr/bin/env bash
# Paper runs this (spigot.yml restart-script) on the way out after `restart` or a crash. It only leaves a marker;
# start.sh sees it and starts the server again in the same terminal.
touch "$(dirname "$0")/.restart"
