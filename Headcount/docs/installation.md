# Installation

## Requirements

- Paper 26.3 (or newer 26.x)
- Java 25
- Optional: PvPManager, PlaceholderAPI, Vault/VaultUnlocked with an economy, LuckPerms, a moderation plugin (LiteBans, LibertyBans, AdvancedBan or EssentialsX), DiscordSRV, ItemsAdder/Oraxen/Nexo, Geyser and Floodgate. See [integrations.md](integrations.md).

## Install

1. Put `Headcount-<version>.jar` in `plugins/`.
2. Start the server. Headcount downloads its SQLite driver through Paper's library loader, writes its config files to `plugins/Headcount/`, and starts the first cycle (`cycle.start-date: auto` starts immediately; set a date to start later).
3. The console lists which plugin handles each integration, and any config problem the start-up check finds.
4. Adjust the files (every option is commented, and listed in [config-reference.md](config-reference.md)), then run `/hca reload` and `/hca selftest`.

## Updating

Replace the jar and restart. Your files are kept. New options fall back to their defaults until you add them; a warning names any file whose `config-version` is older than the build expects. Compare your files with the bundled ones (in the jar) to see what is new.

## Building from source

```bash
./gradlew build
```

This needs JDK 25 (Gradle finds an installed one, or downloads it; Gradle itself runs on JDK 17 or newer), compiles against every integration's public API, runs the tests, and puts the jar in `build/libs/`. It also copies the jar into `../TestServer/plugins` when that folder exists.

## Files

| Path | Contents |
|---|---|
| `plugins/Headcount/*.yml` | The config files. |
| `plugins/Headcount/lang/` | Language files. |
| `plugins/Headcount/headcount.db` | The SQLite database: players, vaults, cycles, results, tracked actions, pending deliveries. Back it up with the server stopped. |
| `plugins/Headcount/audit/` | The audit log. |
