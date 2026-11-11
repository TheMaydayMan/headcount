# Installation

## Requirements

- Paper 26.3 (or newer 26.x)
- Java 25
- Nothing else. PvP, permission nodes, moderation and AFK have included versions. Optional plugins can take over a job, or add one: PvPManager, PlaceholderAPI, Vault/VaultUnlocked with an economy (money), LuckPerms (groups, tracks, prefixes), a moderation plugin (LiteBans, LibertyBans, AdvancedBan or EssentialsX), EssentialsX (AFK and vanish), DiscordSRV, ItemsAdder/Oraxen/Nexo, Geyser and Floodgate. See [integrations.md](integrations.md).

## Install

1. Download `Headcount-<version>.jar` from the [releases page](https://github.com/TheMaydayMan/headcount/releases/latest) (or [build it](#building-from-source)) and put it in `plugins/`.
2. Start the server. Headcount downloads its SQLite driver through Paper's library loader, writes its config files to `plugins/Headcount/`, and starts the first cycle (`cycle.start-date: auto` starts immediately; set a date to start later).
3. The console lists who handles each integration (`included`, a plugin, or `none`), and any config problem the start-up check finds.
4. Adjust the files (every option is commented, and listed in [config-reference.md](config-reference.md)), then run `/hca reload` and `/hca selftest`. Or use **Headcount Studio** (below).

## Headcount Studio

An app that runs on your own computer and opens in the browser: visual editors for every config file, a live view of the server (players, vaults, the leaderboard, the audit log), a console, guides and a full reference. From the repository, double-click a launcher in the `Headcount Studio` folder (`.desktop`, `.command` or `.bat`), or run:

```bash
python3 Headcount/tools/studio/server.py --dir /path/to/server/plugins/Headcount
```

It needs Python 3 (and PyYAML to check saves). The live pages read the database; commands need RCON (`enable-rcon=true` and an `rcon.password` in `server.properties`, then a restart). See [tools/studio/README.md](../tools/studio/README.md).

## Updating

Replace the jar and restart (not `/reload`). Your files are kept, and the database upgrades itself on the first start. New options fall back to their defaults until you add them; a warning names any file whose `config-version` is older than the build expects. Compare your files with the bundled ones (in the jar) to see what is new; Headcount Studio shows every option that differs from the default.

**From 1.0 to 1.1:**
- `integrations.yml` was rewritten around `included` / plugin / `none`. Old files still load (`auto` picks the installed plugin, with a warning), but the new PvP, permissions and AFK options only exist in the new file. Note that `pvp.provider: included` is the new default: if you used PvPManager, set `pvp.provider: pvpmanager`, or remove PvPManager.
- `config.yml` has new `reset:`, `heads.item.defunct-name`/`defunct-lore`, `vault.gui.score-colors` and `sidebar.shown-for-new-players`. To get `<score>` in the vault title, or the gray cycle number on old heads, copy those lines from the bundled file.
- Every file is `config-version: 2`. See [CHANGELOG.md](../CHANGELOG.md).

## Building from source

```bash
./gradlew build
```

This needs JDK 25 (Gradle finds an installed one, or downloads it; Gradle itself runs on JDK 17 or newer), compiles against every integration's public API, runs the tests, and puts the jar in `build/libs/`. It also copies the jar into `../TestServer/plugins` when that folder exists, and regenerates Headcount Studio's reference catalog (with Python 3).

## Files

| Path | Contents |
|---|---|
| `plugins/Headcount/*.yml` | The config files. |
| `plugins/Headcount/lang/` | Language files. |
| `plugins/Headcount/headcount.db` | The SQLite database: players, vaults, cycles, results, tracked actions, pending deliveries. Back it up with the server stopped. |
| `plugins/Headcount/audit/` | The audit log. |
| `plugins/Headcount/backups/` | Backups made by hard resets (`hard-reset-<time>/`: the database and the audit log) and by Headcount Studio saves (`studio/`). |
