# Test server

This folder sets up the local Paper server Headcount is developed and tested on.

The repository holds only `setup.sh`, `start.sh`, `restart.sh`, `server.properties.template` and this README. Everything else here, like the server jar, plugins, world, logs and configs, comes from setup or from the server itself, and git ignores it. Mojang's server files can't be redistributed and the plugins have their own licenses, so none of them are committed.

```bash
./setup.sh                 # once, and after pulling a change to it
(cd ../Headcount && ./gradlew build)   # builds Headcount and copies it into plugins/
./start.sh
```

`setup.sh [--accept-eula]` downloads Paper and the free plugins Headcount works with, at the versions it was tested with, and checks each file against its sha256. It writes `server.properties` from `server.properties.template` if there isn't one yet, and asks you to accept the [Minecraft EULA](https://aka.ms/MinecraftEULA). You can run it again any time. Files that are already there and match are kept.

| Plugin | Version | Source |
|---|---|---|
| Paper | 26.3 build 246 | papermc.io |
| PvPManager | 4.0.30 | Modrinth (optional since 1.1, see below) |
| PlaceholderAPI | 2.12.3 | Modrinth |
| LuckPerms | 5.5.71 | Modrinth |
| EssentialsX | 2.22.0 | Modrinth |
| VaultUnlocked | 2.20.3 | Modrinth |
| LibertyBans | 1.2.0-M1 | Modrinth |
| AdvancedBan | 2.3.0 | GitHub releases |
| DiscordSRV | 1.30.5 | Modrinth |
| Geyser-Spigot | 2.12.1 build 1298 | geysermc.org |
| Floodgate | 2.2.5 build 141 | geysermc.org |

Some of these are older than Paper 26.3 but still work. EssentialsX 2.22.0 is built for 26.1.2 and logs "unsupported server version", but runs. Geyser's Paper build also stops at 26.1.2 and loads fine. AdvancedBan's last release is from 2020, but it loads and its API works. VaultUnlocked stands in for the unmaintained Vault and registers itself as "Vault". The paid plugins Headcount supports (LiteBans, ItemsAdder, Oraxen, Nexo) aren't installed.

## Running it

`start.sh` uses the first Java 25 or newer it finds: `~/.jdks/jdk-25.0.5+8`, then `JAVA_HOME`, then the `PATH`. Make yourself an operator with `op <name>` in the console. Type `stop` to stop the server. `restart`, or a crash, starts it again in the same terminal through `restart.sh`, which `start.sh` points `spigot.yml` at.

The server listens on port 25565, and Bedrock clients connect through Geyser on 19132. A few settings in the template are there for testing:
- `online-mode=false`, so any name can join and you can test with several accounts.
- `enforce-secure-profile=false`, which chat needs in offline mode.
- `spawn-protection=0`, so players who aren't op can place heads at spawn.
- `white-list=false`, because newer Paper builds turn the whitelist on by default.

Geyser's config is left at its defaults.

RCON is on (port 25575) with a random password that `setup.sh` writes into `server.properties`, so Headcount Studio can run commands on the server. A server set up before 1.1 gets it from the next `setup.sh`. RCON listens on every network interface, so keep 25575 closed in your firewall.

PvPManager is installed, but Headcount 1.1 uses its included PvP by default (`integrations.yml pvp.provider`). PvPManager still enforces its own rules while it is installed, like PvP on for everyone and its newbie protection. Delete its jar from `plugins/` to test Headcount's PvP cleanly, or set `pvp.provider: pvpmanager` and restart.

DiscordSRV needs a Discord bot token in `plugins/DiscordSRV/config.yml` before it connects.
