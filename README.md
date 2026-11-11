# Headcount

Headcount is a Paper plugin where player heads are the currency. This repository holds the plugin, Headcount Studio (an app for setting it up), and the scripts for the local test server it was developed on.

The whole project was written by Claude, Anthropic's AI model, from a specification by Grady Chastain, so read it and test it before you run it on a server you care about.

Each player starts a cycle with a vault of their own heads and collects other players' heads by trading and killing. When the cycle ends, the unique heads in each vault become a score, which earns shop points or punishments, and everything resets. [Headcount/README.md](Headcount/README.md) describes the features.

Version 1.1.0 is the latest release. The [changelog](Headcount/CHANGELOG.md) lists what changed.

## Layout

```
.
├── Headcount/              the plugin (Gradle project, Java 25, Paper 26.3)
│   ├── docs/               guides for server owners and developers
│   ├── src/                the source, the bundled configs and the unit tests
│   ├── tools/studio/       Headcount Studio
│   ├── tools/bot-tests/    automated player tests run by bot clients
│   ├── DECISIONS.md        design choices the specification left open
│   ├── TESTING.md          what is tested automatically, and the manual checklist
│   └── TEST-NIGHT.md       a test night for 2 to 10 players
├── Headcount Studio/       launchers for Headcount Studio
└── TestServer/             scripts that set up a local Paper server with the free integrations
```

## Getting started

You need JDK 25 to build (Gradle downloads one if it can't find it), Java 25 to run the server, and git, bash and curl. The scripts run on Linux and macOS. On Windows, use WSL, or `gradlew.bat` for the build alone.

```bash
git clone https://github.com/TheMaydayMan/headcount.git
cd headcount
TestServer/setup.sh                    # downloads the server and plugins, asks you to accept the Minecraft EULA
(cd Headcount && ./gradlew build)      # builds Headcount and copies it into TestServer/plugins/
(cd TestServer && ./start.sh)          # port 25565, offline mode
```

Join with any name, type `op <name>` in the server console, then `/hc` in game. `/hc help` and `/hca help` list the commands. The settings are in `TestServer/plugins/Headcount/*.yml`. After editing them, run `/hca reload` and then `/hca selftest`.

The build runs the unit tests and puts the jar in `Headcount/build/libs/`. After pulling changes, rebuild, and run `TestServer/setup.sh` again if it changed. Neither one touches your server's configs or data. [TestServer/README.md](TestServer/README.md) has the plugin versions, ports, RCON and the other server details.

To open Headcount Studio, double-click the launcher for your system in the `Headcount Studio` folder (`.desktop` on Linux, `.command` on macOS, `.bat` on Windows). It needs Python 3 and opens in your browser. You can edit every config there without touching YAML, watch the running server, and look up any command or option. See [its README](Headcount/tools/studio/README.md).

To run Headcount on your own server, download the jar from the [latest release](https://github.com/TheMaydayMan/headcount/releases/latest) and put it in the server's `plugins/` folder. [docs/installation.md](Headcount/docs/installation.md) has the requirements and the optional plugins.

## Documentation

The plugin's [README](Headcount/README.md) has the full list of docs. A few places to start:

- [docs/config-reference.md](Headcount/docs/config-reference.md) for any setting. The config files are commented too.
- [docs/commands.md](Headcount/docs/commands.md) for commands and permissions.
- [docs/examples.md](Headcount/docs/examples.md) for ready-made setups.
- [DECISIONS.md](Headcount/DECISIONS.md) for why something works the way it does.
- [TEST-NIGHT.md](Headcount/TEST-NIGHT.md) for a test night with friends.
- [tools/bot-tests/README.md](Headcount/tools/bot-tests/README.md) for the automated player tests, which need Linux and Node.js 20.

## What still needs testing

The bot tests and `/hca selftest` cover most of the plugin, but some things need a person or something the test server doesn't have. DiscordSRV needs a bot token to deliver the `discord` action. The paid plugins (LiteBans, ItemsAdder, Oraxen and Nexo) are compiled against but not installed. The movement statuses (`flying`, `gliding`, `swimming`, `sleeping`, `falling`) need a real client, and so do the look of menus and messages, especially on Bedrock with a real Xbox sign-in. The 1.1 features are tested by hand with [TEST-NIGHT.md](Headcount/TEST-NIGHT.md). [TESTING.md](Headcount/TESTING.md) has the full manual checklist.

## License

MIT, copyright 2026 Grady Chastain ([LICENSE](LICENSE)). The test server's downloads keep their own licenses, and the Minecraft server they fetch is under the [Minecraft EULA](https://aka.ms/MinecraftEULA).
