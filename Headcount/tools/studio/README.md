# Headcount Studio

Studio is an app for setting up and running Headcount without editing YAML by hand. It runs on your own computer and opens in your browser. It works on a server's `plugins/Headcount` folder: the config files, and the database once the server has run.

## Opening it

Double-click the launcher for your system in the repository's `Headcount Studio` folder.

| System | Launcher | The first time |
|---|---|---|
| Linux | `Headcount Studio.desktop` | Your file manager may ask first. Right-click it and choose Allow Launching (GNOME), or confirm Execute (KDE). |
| macOS | `Headcount Studio.command` | macOS may block it. Right-click it and choose Open. |
| Windows | `Headcount Studio.bat` | SmartScreen may stop it. Click More info, then Run anyway. |

Studio needs Python 3. With PyYAML installed (`pip install pyyaml`) it also checks every file before saving it. It stops on its own a couple of minutes after you close the tab, or right away with the power button at the bottom of the sidebar.

From a terminal:

```bash
python3 Headcount/tools/studio/server.py                                    # the test server in TestServer/
python3 Headcount/tools/studio/server.py --dir /path/to/server/plugins/Headcount
```

The first launch downloads Minecraft's textures and font from Mojang for the in-game previews. Without them, items show as colored squares.

## Finding your way

The sidebar has five groups of pages.

- **Overview** sums up your setup in a paragraph, and lists problems and unsaved changes.
- **Set up** has a page for each part of the game: the cycle, vaults and heads, scoring, outcomes, the shop, titles, trading, statuses, integrations, hooks, the sidebar, menus, messages and general settings. Each shows what players will see as you change it.
- **Live server** appears when the folder has a database. **This cycle** shows the countdown and lets you move the end, simulate it, end it or start it over. **Players** ranks everyone, and each player's page shows their vault as a chest, with their effects, titles, purchases and past cycles. **Past cycles**, **Effects**, **Audit log** (which can follow one head from start to finish) and **Console** are there too.
- **Help** starts with **How it works**, which explains a cycle using your own settings, with a glossary. **Recipes** are ready-made setups from the docs that you can apply after seeing exactly what they change. **Why is it blocked?** works out which status stops a player. **Reference** covers commands, permissions, placeholders, actions, status sources, the formula, text formatting and every setting, and **Docs** has the plugin's documentation.
- **Files** has **Problems**, which lists what Studio finds as you edit, **Raw files** for each file as text, and **Backups**, which also imports and exports your whole setup as a .zip.

Ctrl+K searches settings, pages, commands, placeholders, players and docs. Ctrl+Z and Ctrl+Shift+Z undo and redo across files.

## Saving

Changes stay in Studio until you save them. Ctrl+S, or the button at the top right, shows every changed line first. Studio only rewrites the lines you changed, so comments and layout stay put.

Before a save, Studio keeps the old file in `plugins/Headcount/backups/studio/`. If something else changed the file since Studio opened it, Studio asks before overwriting. After saving, reload Headcount on the server (`/hca reload`) so it uses the new files. With RCON on, Studio offers to do that for you.

## The live pages and RCON

Studio only reads the database. Anything that changes the server, like giving points or ending a cycle, is sent as a `/hca` command over RCON, and Studio asks first. A hard reset still needs its typed codes, so it runs from the Console.

The test server's `TestServer/setup.sh` turns RCON on. On another server, set `enable-rcon=true` and an `rcon.password` in `server.properties`, restart the server, and open Studio again. RCON listens on every network interface, so keep its port (25575) closed in your firewall.

Studio listens on `127.0.0.1:8765` and refuses requests from other websites. A second launch opens the Studio that's already running, and if something else has the port it takes the next one.

## What you still do elsewhere

Studio doesn't build or install the plugin, start the server, or edit `server.properties`, other plugins' configs, or translations other than `lang/en_US.yml`. Restoring the database from a hard-reset backup means copying `headcount.db` back while the server is stopped.

## Files

| File | |
|---|---|
| `server.py` | The local web server: config files, backups, import and export, the read-only database, the audit log and RCON. |
| `mcassets.py` | Downloads and unpacks Minecraft's textures, font, sounds and player skins into `cache/`. |
| `build-catalog.py` | Builds `catalog.json` (commands, permissions, placeholders, actions, status sources, the formula, every setting, recipes, docs) from the plugin's own files. It runs with every `./gradlew build`. |
| `web/` | The app. `core.js` reads and edits YAML and renders text, `store.js` holds the files and undo history, `ui.js` and `app.css` are the shared parts, and each `pages-*.js` file is one part of the sidebar. |
