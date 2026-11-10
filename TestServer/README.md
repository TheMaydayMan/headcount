# Test server

Sets up the local Paper server Headcount was developed and tested on.

Only the files to build it are in the repository: `setup.sh`, `start.sh`, `server.properties.template` and this README. Everything else here (the server jar, plugins, world, logs, configs) is made by setup or by the server, and is git-ignored.

```bash
./setup.sh                 # once, and after pulling a change to it
(cd ../Headcount && ./gradlew build)   # builds Headcount and copies it into plugins/
./start.sh
```

`setup.sh [--accept-eula]` downloads Paper 26.3 build 246 and the free plugins Headcount integrates with, at the exact versions it was tested with. It checks each file against its sha256, writes `server.properties` from `server.properties.template` when it's missing, and asks you to accept the [Minecraft EULA](https://aka.ms/MinecraftEULA). Run it again any time: files that are already there and match are kept.

| Plugin | Version | Source |
|---|---|---|
| Paper | 26.3 build 246 | papermc.io |
| PvPManager | 4.0.30 | Modrinth |
| PlaceholderAPI | 2.12.3 | Modrinth |
| LuckPerms | 5.5.71 | Modrinth |
| EssentialsX | 2.22.0 | Modrinth |
| VaultUnlocked | 2.20.3 | Modrinth |
| LibertyBans | 1.2.0-M1 | Modrinth |
| AdvancedBan | 2.3.0 | GitHub releases |
| DiscordSRV | 1.30.5 | Modrinth |
| Geyser-Spigot | 2.12.1 build 1298 | geysermc.org |
| Floodgate | 2.2.5 build 141 | geysermc.org |

The server runs in offline mode (any name can join) on port 25565 with the whitelist off; Bedrock clients connect through Geyser on 19132. `start.sh` uses the first Java 25 or newer it finds: `~/.jdks/jdk-25.0.5+8`, then `JAVA_HOME`, then the `PATH`. Make yourself an operator with `op <name>` in the console. DiscordSRV needs a Discord bot token in `plugins/DiscordSRV/config.yml` before it connects.
