# Headcount

> **This project is entirely AI-coded.** Every line of code, every config file, all of the documentation and every test in this repository was written by Claude, Anthropic's AI model, working in Claude Code from a specification and directions by Grady Chastain. None of it was written by hand. It has been built and tested extensively (see [What is verified](#what-is-verified)), but read it and test it before you run it on a server you care about.

This repository holds **Headcount**, a Paper plugin where player heads are the currency, and the files to set up the local test server it was developed and tested on. This README is the map: what is here, where each thing is, how to build and test, what has been verified, and what still needs a person.

**Status (2026-11-10):** version **1.1.0** is in progress on `main` (see [CHANGELOG.md](Headcount/CHANGELOG.md)) and is being tested by hand with [test-plan.md](test-plan.md); it is not released yet. The rest of this section is about 1.0.0: built from scratch with no compiler warnings; the test server boots with every free integration and no Headcount errors or warnings; `/hca selftest` passes all 781 checks on the default configs. About 355 checks played by bot clients pass, including two crash tests, plus a 7-check Bedrock test. 93 of the 94 items in the build checklist are verified. The one left needs things this machine doesn't have: a Discord bot token, and the paid item plugins (see [What isn't verified](#what-isnt-verified)).

## Headcount in one paragraph

Every player starts each cycle (30 days by default) with a vault full of copies of their own head. They collect other players' heads by trading and killing. When the cycle ends, the unique heads in each vault become a score: good scores earn points to spend in a shop, bad ones bring punishments, and everything resets. Nearly everything is configurable, including statuses, actions, outcomes, the score formula, the shop, titles, hooks and every message. The full description is in [Headcount/README.md](Headcount/README.md).

## Layout

```
.
├── README.md                    this map
├── LICENSE                      MIT, © 2026 Grady Chastain
├── Headcount Studio/             double-click a launcher here to open Headcount Studio:
│                                .desktop (Linux), .command (macOS), .bat (Windows)
├── test-plan.md                 the itinerary for a 2-10 player test night of 1.1
├── 1.1 Notes.md                 the 1.1 work list, with what was done under each item
├── app-plan.md                  the design plan Headcount Studio was built from
├── .gitignore
├── Headcount/                   the plugin (Gradle project, Java 25, Paper 26.3)
│   ├── README.md                features, quick start, documentation index
│   ├── CHANGELOG.md             what changed in each version
│   ├── LICENSE                  MIT, © 2026 Grady Chastain
│   ├── docs/                    16 pages for server owners and developers
│   ├── src/main/java/           the source, in com.gradychastain.headcount
│   ├── src/main/resources/      plugin.yml, the 9 commented config files, lang/en_US.yml
│   ├── src/test/java/           17 unit tests, and the API examples from the docs (compiled with every build)
│   ├── tools/
│   │   ├── gen-config-docs.py   regenerates docs/config-reference.md from the bundled configs
│   │   ├── studio/              Headcount Studio: the app to set up, run and learn Headcount
│   │   └── bot-tests/           player tests played by bots, crash tests, the Bedrock test
│   ├── DECISIONS.md             every choice the specification left open, and why
│   ├── TESTING.md               what is tested automatically, and the manual checklist
│   ├── TASKS.md                 the build checklist: every item, and how it was verified
│   └── PROGRESS_BAR.md          the build timeline: when each section was written and verified, pauses
└── TestServer/                  a local Paper 26.3 server with the free integrations
    ├── README.md                what it runs, and how
    ├── setup.sh                 downloads Paper and the plugins (pinned, sha256-checked), writes server.properties
    ├── start.sh                 starts it with Java 25; restarts in the same terminal after `restart`
    └── server.properties.template
                                 everything else in TestServer/ is downloaded or made by the server, and git-ignored
```

## Getting started

**You need:** JDK 25 for the build (Gradle downloads one if it can't find it) and Java 25 to run the server; git, bash and curl. The scripts run on Linux and macOS; on Windows, use WSL (the build alone also works with `gradlew.bat`). The bot tests need Linux and Node.js 20 ([their README](Headcount/tools/bot-tests/README.md)).

```bash
git clone https://github.com/TheMaydayMan/headcount.git
cd headcount
TestServer/setup.sh                    # downloads the server and plugins, asks you to accept the Minecraft EULA
(cd Headcount && ./gradlew build)      # builds Headcount and copies it into TestServer/plugins/
(cd TestServer && ./start.sh)          # port 25565, offline mode
```

Join with any name, type `op <name>` in the server console, then `/hc` in game. `/hc help` and `/hca help` list the commands. Headcount's settings are in `TestServer/plugins/Headcount/*.yml`: edit them, then run `/hca reload` and `/hca selftest`.

**Headcount Studio:** open the `Headcount Studio` folder and double-click the launcher (`.desktop` on Linux, `.command` on macOS, `.bat` on Windows; it needs Python 3). It opens in your browser and does everything without touching YAML:
- set up every config with visual editors
- watch the running server and run commands over RCON
- learn how Headcount works, and look up any command, option or placeholder

See [tools/studio/README.md](Headcount/tools/studio/README.md).

**On your own server:** download the jar from the [latest release](https://github.com/TheMaydayMan/headcount/releases/latest) (1.0.0; for 1.1, build it as above: the jar lands in `Headcount/build/libs/`), put it in the server's `plugins/` folder, and start it. [docs/installation.md](Headcount/docs/installation.md) has the requirements and the optional plugins.

**After pulling changes:** rebuild (`./gradlew build`), and run `TestServer/setup.sh` again if it changed. Your server's configs and data are never touched by either.

## Where to find what

| To... | Read |
|---|---|
| Understand what the plugin does | [Headcount/README.md](Headcount/README.md) |
| Install it on a server, update it | [docs/installation.md](Headcount/docs/installation.md) |
| Look up any setting | [docs/config-reference.md](Headcount/docs/config-reference.md) (all 472 options), Headcount Studio's editors and its Reference pages, or the comments in the config files themselves |
| Learn how heads, the vault, kills and trades work | [docs/heads.md](Headcount/docs/heads.md) |
| Write statuses, actions, the score formula | [docs/statuses.md](Headcount/docs/statuses.md), [docs/actions.md](Headcount/docs/actions.md), [docs/formula.md](Headcount/docs/formula.md) |
| Set up rewards and punishments, the shop, titles, hooks | [docs/outcomes.md](Headcount/docs/outcomes.md), [docs/shop.md](Headcount/docs/shop.md), [docs/titles.md](Headcount/docs/titles.md), [docs/hooks.md](Headcount/docs/hooks.md) |
| Use placeholders, format messages | [docs/placeholders.md](Headcount/docs/placeholders.md), [docs/text.md](Headcount/docs/text.md) |
| See which plugin handles what (PvP, money, ranks, bans, Bedrock...) | [docs/integrations.md](Headcount/docs/integrations.md) |
| Find a command or permission | [docs/commands.md](Headcount/docs/commands.md) |
| Copy a ready-made variation | [docs/examples.md](Headcount/docs/examples.md), or Studio's one-click recipes |
| See what changed in 1.1 | [CHANGELOG.md](Headcount/CHANGELOG.md), [1.1 Notes.md](1.1%20Notes.md) |
| Run a test night with friends | [test-plan.md](test-plan.md) |
| Write an addon (API, events, custom status sources and action types) | [docs/developers.md](Headcount/docs/developers.md) |
| Know why something works the way it does | [DECISIONS.md](Headcount/DECISIONS.md) |
| Know what was verified, and how | [TASKS.md](Headcount/TASKS.md) (each line says how), [PROGRESS_BAR.md](Headcount/PROGRESS_BAR.md) (when) |
| Test by hand | [TESTING.md](Headcount/TESTING.md) |
| Run the automated player tests | [tools/bot-tests/README.md](Headcount/tools/bot-tests/README.md) |

## Build

```bash
cd Headcount
./gradlew build
```

The build needs JDK 25. Gradle uses one that is installed (it looks in the usual places, including `~/.jdks`) or downloads one; Gradle itself runs on any JDK 17 or newer. The build compiles against every integration's API (paid plugins included, as compile-only dependencies), runs the unit tests, and puts `Headcount-<version>.jar` in `build/libs/`. It also copies the jar into `TestServer/plugins/` when that folder exists, and regenerates Headcount Studio's reference catalog (`tools/studio/catalog.json`, with Python 3).

After changing a comment in a bundled config file, run `python3 tools/gen-config-docs.py` to regenerate the config reference.

## Test server

```bash
cd TestServer
./setup.sh      # once; again after a change to it
./start.sh
```

Only the files that build the server are in the repository ([TestServer/README.md](TestServer/README.md)). `setup.sh` downloads Paper and the plugins at the exact versions Headcount was tested with, checks each against its sha256, and writes `server.properties` from the template. The server makes the rest: world, logs, plugin data and configs. `start.sh` uses the first Java 25 or newer it finds: `~/.jdks/jdk-25.0.5+8`, then `JAVA_HOME`, then the `PATH`. Type console commands into that terminal: `stop` stops it, and `restart` starts it again in the same terminal.

- **Paper 26.3** build 246, offline mode (any name can join), port **25565**, whitelist off, a normal world on easy.
- **Bedrock** clients connect through Geyser on port **19132**. Geyser's config is at its defaults.
- **Make yourself an operator** with `op <name>` in the console. Admin commands are `/hca help`.
- **Headcount's data** (`plugins/Headcount/`: configs, database, audit log) is made on the first start. Delete the folder to start over with default configs and a new cycle 1.
- **RCON** is on (port 25575) with a random password that `setup.sh` writes to `server.properties`, so Headcount Studio can run commands. RCON listens on every network interface: keep port 25575 closed in your firewall.
- **DiscordSRV** is installed but not connected: it needs a Discord bot token in `plugins/DiscordSRV/config.yml`.

| Plugin | Version | Used for |
|---|---|---|
| Headcount | 1.1.0 | this project |
| PvPManager | 4.0.30 | only with `integrations.yml pvp.provider: pvpmanager`. Headcount's included PvP is the 1.1 default; while PvPManager is installed it still enforces its own rules, so remove its jar to test the included PvP cleanly. |
| PlaceholderAPI | 2.12.3 | other plugins' placeholders in Headcount, and `%headcount_*%` for other plugins |
| VaultUnlocked | 2.20.3 | the Vault API, for money (with EssentialsX's economy) |
| LuckPerms | 5.5.71 | groups, tracks, title prefixes (with `permissions.provider: luckperms`; the default `included` gives permission nodes itself) |
| EssentialsX | 2.22.0 | economy; jails, AFK, vanish and god mode when chosen in `integrations.yml` (the defaults are Headcount's own) |
| LibertyBans | 1.2.0-M1 | bans, mutes, kicks, warnings, when chosen in `integrations.yml moderation` (the default is `included`) |
| AdvancedBan | 2.3.0 | moderation, if chosen in `integrations.yml` |
| DiscordSRV | 1.30.5 | the `discord` action (once it has a token) |
| Geyser-Spigot | 2.12.1 (build 1298) | Bedrock clients |
| Floodgate | 2.2.5 (build 141) | Bedrock players without a Java account |

The paid plugins Headcount supports (LiteBans, ItemsAdder, Oraxen, Nexo) are compiled against but not installed.

## What is verified

Every item in [TASKS.md](Headcount/TASKS.md) says how it was checked. In short:

- **Build:** a from-scratch build with no compiler warnings; 17 unit tests (expressions, durations, ranges, ranks, cycle boundaries across daylight saving, audit rotation and retention).
- **Self-test:** `/hca selftest`, which also runs quietly on every start and reload. It builds every configured item, menu, shop entry and title, parses every message and action list, and checks every status reference: 781 checks on the defaults.
- **Server runs:** clean boots and shutdowns; a seeded end of cycle checked against a hand calculation; catch-up after 2 hours and after 65 days offline; warnings and a scheduled rollover.
- **Bot tests** ([tools/bot-tests](Headcount/tools/bot-tests/README.md)): about 355 checks played by mineflayer clients. They cover every click type in the vault, trades and every way to cancel one, kills, every action type, outcomes, the shop, titles, the sidebar, every status source a bot can trigger, the moderation plugins, and the API through a test addon. Two crash tests kill the server mid-transfer. The last full run used a bot server built by `setup.sh` from an empty folder. It passed 354 of 355; the miss was a real timing bug in repeating actions, which was fixed and checked again.
- **Bedrock:** bedrock-protocol clients through Geyser and Floodgate. Headcount recognizes them, the vault opens as a chest menu, the sidebar arrives, and trade requests use the typed `/trade accept` text.

## What isn't verified

These need a person, or something this machine doesn't have. [TESTING.md](Headcount/TESTING.md) lists them as manual checks.

- **DiscordSRV:** a `discord` action reaching a channel needs DiscordSRV connected to a bot token.
- **Paid plugins:** ItemsAdder, Oraxen and Nexo items, and LiteBans. Headcount is compiled against their APIs, as the specification asked.
- **A real Bedrock client:** how the menus and sidebar look on Bedrock, and Floodgate with a real Xbox sign-in.
- **Movement:** the `flying`, `gliding`, `swimming`, `sleeping` and `falling` statuses come from client movement, which the bots can't send.
- **Look and feel:** menus, messages and titles on a real client.

## Bugs the testing found

All of these were fixed and checked again. [DECISIONS.md](Headcount/DECISIONS.md) explains the behavior that came out of each.

- **A trade offer could be duplicated by a crash.** The inventory wasn't saved after an item moved into an offer, so a hard kill returned it twice. Inventories are now saved within a second of any vault, trade or action change. The crash test duplicated the item before the fix and returns it exactly once after.
- **Players couldn't turn PvP off on a fresh install.** PvPManager makes `/pvp` operator-only by default, which left vaults and trades locked. `pvp.let-players-toggle` (on by default) lets everyone use `/pvp`.
- **"For the whole cycle" punishments ended at once.** They are given while a cycle ends, so they were removed right after being applied. They now last through the next cycle.
- **"+3 starting heads" did nothing in a full vault**, so players paid shop points for nothing. The extra heads now arrive as items.
- **Silent configuration mistakes:**
  - actions that do nothing without a duration;
  - list names that don't exist;
  - status loops that were only caught when first evaluated;
  - a false formula warning at start-up.

  The self-test reports the first three now, and the false warning is gone.
- **Smaller ones:**
  - audit log cleanup only ran on restart;
  - deny messages hid each other;
  - title prefixes didn't follow a reload;
  - empty-looking slots on a large vault's last page;
  - dying mobs counted as nearby;
  - a world change during a trade was reported as "screen closed";
  - repeating actions drifted later each time and could lose their last run;
  - two API gaps: `runActionList` without `<player>`, and score changes not shown in previews.

## Notes

- Every path in the project is relative, so the repository folder can have any name.
- The build ran from 2026-11-09 17:38 to 2026-11-10 05:23 MST, with two usage-limit pauses. This README and a last coherency pass followed from 08:53 to 09:36, the repository was set up from 09:54 and first published at 10:12, and release 1.0.0 was published at 10:18. A machine restart at 02:15 wiped a temporary copy of the bot harness, which was rebuilt and now lives in the project. [PROGRESS_BAR.md](Headcount/PROGRESS_BAR.md) has the timeline.
- License: MIT, © 2026 Grady Chastain ([LICENSE](LICENSE)). The test server's downloads keep their own licenses, and the Minecraft server they fetch is under the [Minecraft EULA](https://aka.ms/MinecraftEULA).
