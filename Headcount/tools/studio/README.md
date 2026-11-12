# Headcount Studio: the source

Studio's user guide is [docs/studio.md](../../docs/studio.md). This folder holds the web app and the tool that builds its reference catalog. The build packs both into the jar, and the plugin serves them.

| Path | |
|---|---|
| `web/` | The app. `core.js` reads and edits YAML and renders text, `store.js` holds the files and undo history, `login.js` signs in, `ui.js` and `app.css` are the shared parts, `sprites.js` draws items and blocks from Minecraft's textures, and each `pages-*.js` file is one part of the sidebar. |
| `build-catalog.py` | Builds `catalog.json` (commands, permissions, placeholders, actions, status sources, the formula, every setting, recipes, docs) from the plugin's own files. Every `./gradlew build` runs it before packing the jar, when Python 3 is installed. |
| `catalog.json` | Its output, committed so a build without Python still has one. |

The server side is Java, in `src/main/java/com/gradychastain/headcount/studio/`:

| Class | |
|---|---|
| `StudioServer` | The HTTP server and its API: the config files (saved line by line, checked as YAML, the old file kept in `backups/studio/`), backups, import and export, commands run as the console, and the page's own files from the jar. |
| `SamePortBridge` | Shares the Minecraft port. It watches each new connection's first bytes and pipes browsers to `StudioServer` on a loopback port. |
| `StudioAuth` | The password, signed-in browsers and the limit on wrong passwords. |
| `StudioLive` | The live pages: the database through a read-only connection, and the audit log. |
| `StudioAssets` | Downloads Minecraft's textures, font, sounds and player skins from Mojang into `plugins/Headcount/studio/cache/`. |

To work on the web app, build the plugin, start a server with it, and reload the page after each build. `TestServer/` in this repository is set up for that.
