# Installation

## Requirements

- Paper 26.3 or a newer 26.x
- Java 25

Headcount needs no other plugins. It has its own PvP, permission nodes, moderation and AFK. It can also hand those jobs to a plugin you already run, or get extra features from one: PvPManager, PlaceholderAPI, Vault or VaultUnlocked with an economy (for money), LuckPerms (for groups, tracks and prefixes), a moderation plugin (LiteBans, LibertyBans, AdvancedBan or EssentialsX), EssentialsX for AFK and vanish, DiscordSRV, ItemsAdder, Oraxen or Nexo, and Geyser with Floodgate. On the first start, Headcount hands each job to an installed plugin that can do it, and later starts follow plugins you add or remove ([auto-switch](integrations.md#auto-switch)). See [integrations.md](integrations.md).

## Install

1. Download `Headcount-<version>.jar` from the [releases page](https://github.com/TheMaydayMan/headcount/releases/latest), or [build it yourself](#building-from-source), and put it in `plugins/`.
2. Start the server. Headcount downloads its SQLite driver through Paper's library loader and writes its config files to `plugins/Headcount/`. The first cycle starts right away with the default `cycle.start-date: auto`. Set a date there to start later.
3. Check the console. It lists who handles each integration (`included`, a plugin, or `none`) and any config problems found at start-up.
4. Edit the files, then run `/hca reload` and `/hca selftest`. Every option is commented in the files and listed in [config-reference.md](config-reference.md). You can also edit them in Headcount Studio: `/hca studio` shows its link and password.

## Headcount Studio

Headcount Studio is a web app for editing the configs and watching the server, served by the plugin itself. It has an editor for each config file, a live view of the server (players, vaults, the leaderboard and the audit log), a console, and guides with a full reference. Run `/hca studio` for the link and password. By default it shares the Minecraft port, so it works on hosts that give you one port. [studio.md](studio.md) covers the rest.

## Updating

Replace the jar and restart the server. Don't use `/reload`. Your files are kept, and the database upgrades itself on the first start. New options use their defaults until you add them, and a warning names any file whose `config-version` is older than the build expects. To see what's new, compare your files with the bundled ones in the jar, or open Headcount Studio, which shows every option that differs from its default.

### From 1.0 to 1.1

- `integrations.yml` was rewritten around `included`, a plugin, or `none`. Old files still load, with `auto` picking the installed plugin and logging a warning, but the new PvP, permissions and AFK options only exist in the new file. The default is now `pvp.provider: included`, so if you used PvPManager, set `pvp.provider: pvpmanager` or remove PvPManager.
- `config.yml` gained `reset:`, `heads.item.defunct-name`, `heads.item.defunct-lore`, `vault.gui.score-colors` and `sidebar.shown-for-new-players`. If you want `<score>` in the vault title or the gray cycle number on old heads, copy those lines from the bundled file.
- The default economy was rebalanced (the Balance section of the changelog lists every value). Your files keep the values you have. To play with the new balance, move your config files out of `plugins/Headcount/` and restart so Headcount writes fresh ones, then copy back what you had customized. Options that are missing from your files now fall back to the new defaults, so for example the sidebar starts on for new players.
- Every file is now `config-version: 2`. The full list of changes is in [CHANGELOG.md](../CHANGELOG.md).

## Building from source

```bash
./gradlew build
```

The build needs JDK 25. Gradle finds an installed one or downloads it, and Gradle itself runs on JDK 17 or newer. It compiles against every integration's public API, runs the tests and puts the jar in `build/libs/`. If a `../TestServer/plugins` folder exists, the jar is copied there too. With Python 3 installed, the build also regenerates Headcount Studio's reference catalog.

## Files

| Path | Contents |
|---|---|
| `plugins/Headcount/*.yml` | The config files. |
| `plugins/Headcount/lang/` | Language files. |
| `plugins/Headcount/headcount.db` | The SQLite database: players, vaults, cycles, results, tracked actions and pending deliveries. Back it up while the server is stopped. |
| `plugins/Headcount/audit/` | The audit log. |
| `plugins/Headcount/backups/` | Backups from hard resets (`hard-reset-<time>/`, holding the database and the audit log) and from Headcount Studio saves (`studio/`). |
