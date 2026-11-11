# Headcount Studio

An all-in-one app for Headcount: set it up without editing YAML, watch and run a live server, learn how it works, and look anything up. It runs on your own computer and opens in your browser.

## Opening it

Double-click the launcher for your system in the repository's `Headcount Studio` folder:

| System | File | First time |
|---|---|---|
| Linux | `Headcount Studio.desktop` | Your file manager may ask: right-click it and choose **Allow Launching** (GNOME), or confirm **Execute** (KDE). |
| macOS | `Headcount Studio.command` | macOS may block it the first time: right-click → **Open**. |
| Windows | `Headcount Studio.bat` | Windows may show SmartScreen: **More info** → **Run anyway**. |

It needs **Python 3** (with **PyYAML**, `pip install pyyaml`, so saves are checked first). Studio stops by itself a couple of minutes after you close its tab, or use the ⏻ button. From a terminal:

```bash
python3 Headcount/tools/studio/server.py                        # the test server (TestServer/plugins/Headcount)
python3 Headcount/tools/studio/server.py --dir /path/to/server/plugins/Headcount
```

It listens on `127.0.0.1:8765` only, and answers only its own page: requests from other websites are refused. If Studio is already open, a second launch just opens the same page; if the one running is older code (after an update), the launch replaces it; if something else has the port, it uses the next free one (8766, ...).

## What's in it

- **Overview:** your setup in plain words, problems, unsaved changes, and a getting-started checklist.
- **Live server** (when the server folder has a database):
  - a dashboard with the cycle countdown, online players, the top 10 and recent events
  - players, and each player's vault as a chest grid, with their effects, history, titles and purchases
  - cycle tools (simulate, extend, end, reset), the leaderboard with the outcome each would get, and past cycles
  - the **audit log** search, and a **head tracer** that follows one head from creation to deletion
  - every running effect, and a **console** (RCON)

  The database is only ever read; changes go through `/hca` commands over RCON, and each asks first.
- **Set up:** visual editors for everything:
  - the cycle schedule, with a timeline of the next cycles
  - the vault and head items, with in-game tooltips
  - a **scoring lab** (sliders, a live result and a chart)
  - an **outcomes score line**
  - a **chest-grid shop builder** (drag items, edit in a drawer)
  - titles, trading, statuses (with "used by")
  - integrations: included / plugin / none, with the PvP message previews
  - hooks on a timeline, and named action lists
  - a **sidebar mockup**, every **menu** as a chest, all messages with previews, resets and the audit log
  - the **action builder**, used everywhere actions appear: each action is a card with its own fields, and you can drag to reorder.
- **Learn:** an animated "how a cycle works" built from your settings, step-by-step guides, recipes you can apply in one click (with a diff first), "why is it blocked?", a glossary, and every docs page.
- **Reference:** commands (with a builder and a permission tree), placeholders, all 60 action types, status sources, the formula, MiniMessage, and all options (each one jumps to where it's edited).
- **Tools:** problems (checked as you type), **Review & save** with a diff of every change, backups (compare and restore), import and export as a .zip, and the raw files.
- Everywhere:
  - **Ctrl+K** searches pages, options, commands, placeholders, statuses, lists, players and docs
  - **Ctrl+Z / Ctrl+Shift+Z** undo and redo across files, **Ctrl+S** opens Review & save
  - light and dark themes

## How it keeps your files safe

- Edits change only the lines they touch: comments, blank lines, order and quoting stay as they were.
- Nothing is written until **Review & save**. Each save is checked as YAML, the previous file is kept in `plugins/Headcount/backups/studio/`, and a file changed on disk since it was loaded is never overwritten without asking.
- After saving, Studio offers to run `/hca reload` and `/hca selftest` on the server (RCON).

## What Studio doesn't do

Studio covers Headcount's own files and data. These are still done outside it:

- **The plugin itself:** building or downloading the jar, putting it in `plugins/`, and updating it (`./gradlew build`).
- **Starting the server:** `TestServer/start.sh` in a terminal. Once it is running, `restart` and `stop` work from Studio's Console.
- **`server.properties`:** RCON, ports and the rest. `TestServer/setup.sh` does it for the test server.
- **Other plugins' jars and configs:** for example removing PvPManager, setting a DiscordSRV token, LuckPerms groups and their weights, Essentials jails. Their in-game commands (`lp ...`, `op`, `/essentials:...`) do work from Studio's Console.
- **Language files other than `lang/en_US.yml`:** a translation in `lang/<code>.yml` is edited by hand.
- **Changing data directly:** the database is read-only in Studio, and every change goes through a `/hca` command. Restoring a hard-reset backup (`backups/hard-reset-<time>/`) means copying `headcount.db` back with the server stopped.
- **The firewall:** keep RCON's port closed to the outside.
- **Playing:** anything that needs a player in game, like opening menus or placing heads.

## RCON

The console, cycle tools and command buttons need RCON. `TestServer/setup.sh` turns it on for the test server with a random password; Studio reads it from `server.properties`. On another server set `enable-rcon=true` and a `rcon.password`, restart it, and reopen Studio. RCON listens on every network interface, so keep its port (25575) closed in your firewall.

## Files

| File | What |
|---|---|
| `server.py` | The local server: config files, the read-only database, the audit log, RCON, backups, import and export. |
| `build-catalog.py` | Builds `catalog.json` (commands, permissions, placeholders, action types, status sources, formula, every option, recipes, docs) from the plugin's own files. Runs with every `./gradlew build`. |
| `web/` | The app: `core.js` (the YAML engine, MiniMessage, the formula), `store.js`, `ui.js`, `actions.js` (the action builder), and the `pages-*.js` modules. |
