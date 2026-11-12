# Test server

This folder sets up the local Paper server Headcount is developed and tested on.

The repository holds only `setup.sh`, `start.sh`, `restart.sh`, `server.properties.template` and this README. Everything else here, like the server jar, plugins, world, logs and configs, comes from setup or from the server itself, and git ignores it. Mojang's server files can't be redistributed and the plugins have their own licenses, so none of them are committed.

```bash
./setup.sh                 # once, and after pulling a change to it
(cd ../Headcount && ./gradlew build)   # builds Headcount and copies it into plugins/
./start.sh
```

`setup.sh` downloads Paper and checks it against its sha256. Headcount needs no other plugin, so that's all it downloads unless you ask for more. It writes `server.properties` from `server.properties.template` if there isn't one yet, and asks you to accept the [Minecraft EULA](https://aka.ms/MinecraftEULA) (`--accept-eula` skips the question). You can run it again any time. Files that are already there and match are kept.

Two flags add the free plugins Headcount integrates with, at the versions it was tested with, each checked against its sha256 too:

- `--with-bedrock` adds Geyser and Floodgate, so Bedrock players can join. That's the setup [TEST-NIGHT.md](../Headcount/TEST-NIGHT.md) uses.
- `--with-plugins` adds all of them. The integration checks in [TESTING.md](../Headcount/TESTING.md) need this.

`--plugins-dir <dir>` puts the plugins in another folder and skips the rest of the setup. The [bot tests](../Headcount/tools/bot-tests/README.md) use it to fill their own server. Plugins from an earlier setup stay in `plugins/` until you delete them, and `setup.sh` lists any you didn't ask for this time.

| Plugin | Version | Source | Flag |
|---|---|---|---|
| Paper | 26.3 build 246 | papermc.io | always |
| PvPManager | 4.0.30 | Modrinth | `--with-plugins` |
| PlaceholderAPI | 2.12.3 | Modrinth | `--with-plugins` |
| LuckPerms | 5.5.71 | Modrinth | `--with-plugins` |
| EssentialsX | 2.22.0 | Modrinth | `--with-plugins` |
| VaultUnlocked | 2.20.3 | Modrinth | `--with-plugins` |
| LibertyBans | 1.2.0-M1 | Modrinth | `--with-plugins` |
| AdvancedBan | 2.3.0 | GitHub releases | `--with-plugins` |
| DiscordSRV | 1.30.5 | Modrinth | `--with-plugins` |
| Geyser-Spigot | 2.12.1 build 1298 | geysermc.org | `--with-bedrock` or `--with-plugins` |
| Floodgate | 2.2.5 build 141 | geysermc.org | `--with-bedrock` or `--with-plugins` |

Some of these are older than Paper 26.3 but still work. EssentialsX 2.22.0 is built for 26.1.2 and logs "unsupported server version", but runs. Geyser's Paper build also stops at 26.1.2 and loads fine. AdvancedBan's last release is from 2020, but it loads and its API works. VaultUnlocked stands in for the unmaintained Vault and registers itself as "Vault". The paid plugins Headcount supports (LiteBans, ItemsAdder, Oraxen, Nexo) aren't installed.

## Running it

`start.sh` uses the first Java 25 or newer it finds: `~/.jdks/jdk-25.0.5+8`, then `JAVA_HOME`, then the `PATH`. Make yourself an operator with `op <name>` in the console. Type `stop` to stop the server. `restart`, or a crash, starts it again in the same terminal through `restart.sh`, which `start.sh` points `spigot.yml` at.

The server listens on port 25565, and with Geyser installed, Bedrock clients connect on 19132. A few settings in the template are there for testing:
- `online-mode=false`, so any name can join and you can test with several accounts.
- `enforce-secure-profile=false`, which chat needs in offline mode.
- `spawn-protection=0`, so players who aren't op can place heads at spawn.
- `white-list=false`, because newer Paper builds turn the whitelist on by default.

Geyser's config is left at its defaults.

Headcount Studio shares the server's port: once it's running, `/hca studio` in the console gives you the link (`http://localhost:25565`) and the password. A test server set up by 1.1.0's `setup.sh` has RCON turned on in `server.properties`. Studio doesn't need it any more, so you can set `enable-rcon=false`.

With `--with-plugins`, PvPManager is installed, but Headcount uses its included PvP by default (`integrations.yml pvp.provider`). PvPManager still enforces its own rules while it is installed, like PvP on for everyone and its newbie protection. Delete its jar from `plugins/` to test Headcount's PvP cleanly, or set `pvp.provider: pvpmanager` and restart. DiscordSRV needs a Discord bot token in `plugins/DiscordSRV/config.yml` before it connects.
