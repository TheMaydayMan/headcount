#!/usr/bin/env bash
# macOS: double-click to open Headcount Studio in your browser. It stops on its own a couple of minutes after you
# close the tab, or right away with the power button at the bottom of Studio's sidebar.
cd "$(dirname "$0")/.." || exit 1
if ! command -v python3 >/dev/null 2>&1; then echo "Headcount Studio needs Python 3. Get it from https://www.python.org/downloads/"; read -r -p "Press Enter to close."; exit 1; fi
exec python3 Headcount/tools/studio/server.py --exit-when-idle
