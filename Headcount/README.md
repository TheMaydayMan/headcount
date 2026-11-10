# Headcount

A Paper plugin where player heads are the currency. Every player starts each cycle (30 days by default) with a vault full of their own head. Collect other players' heads by trading and by killing; at the end of the cycle the unique heads in your vault become your score. A good score earns points for the shop; a bad one brings punishments. Then everything resets.

Built for **Paper 26.3** and **Java 25**. Open source under the [MIT license](LICENSE).

> **Entirely AI-coded.** Headcount was written by Claude (Anthropic's AI model) in Claude Code, from a specification by Grady Chastain. See the [repository README](../README.md) for what was tested and how.

Nearly everything is configurable: values, limits, messages, menus, statuses, the score formula, rewards, punishments and the shop.

## How a cycle works

1. **Start.** Every player who has ever joined gets their vault filled with `starting-heads` copies of their own head (default 27), tagged with this cycle's ID. Players who join later get the same setup.
2. **Collect.** `/hc` opens your vault. Heads of other players come from **trades** (`/trade <player>`) and **kills** (killing a player drops a fresh head of theirs, once per killer and victim per cycle). Heads can be stored anywhere, but only heads in your vault count, and only economy heads of the current cycle can go in.
3. **End.** The unique heads in each active player's vault are tallied. Each player's score comes from a formula (default `unique - ceil(active / 4)`). Positive scores become points to spend in `/hc shop` during the next cycle; negative scores trigger outcomes such as debuffs, dunce caps or bounties (bans are included but disabled). Results are stored in `/hc history`.
4. **Reset.** Every vault is emptied and the next cycle begins. Heads left outside vaults expire: they keep working as normal heads but are worthless.

## Features

- **Economy heads.** Tracked by owner, cycle and a unique ID in the item's data, so vanilla, `/give` and other plugins' heads never count. Placed heads keep their data through every way a block can drop. Creative copies are blocked from vaults and flagged as duplicates.
- **A safe vault and trading.** Every click and drag type is handled. The trade menu is a double chest with confirmations, a locked countdown and crash-safe offers. Statuses can block vault movement or trading, each with its own message and bypass permission.
- **Statuses.** Named player states from PvPManager, any PlaceholderAPI placeholder, scoreboard tags, permissions, metadata, flags, or 30 built-in checks (recent PvP damage, nearby hostile mobs, low health, falling, gamemode, world...), combinable with all-of, any-of and not.
- **A full actions system.** Shared by rewards, punishments, the shop and hooks: 60 action types (messages, boss bars, commands, items, points, money, LuckPerms ranks, titles, moderation, PvP locks, potions, attributes, teleports, deny lists, bounties, shields...). Every action supports durations, stacking, chance, delay, repeat and conditions. Timed actions revert themselves, even across restarts and for offline players.
- **Hooks** for cycle start and end, warnings before the end (with a countdown boss bar), first join, first join of a cycle, becoming active, and every join.
- **A shop** with tiers unlocked by last cycle's score, pages, per-state displays, stock, limits, cooldowns, prerequisites and confirmation. Plus **titles**, a Bedrock-safe **sidebar**, **history**, a **leaderboard**, and an **audit log** that traces every head by ID.
- **Integrations**, all optional: PvPManager, PlaceholderAPI (both directions), Vault, LuckPerms, LiteBans, LibertyBans, AdvancedBan, EssentialsX, DiscordSRV, ItemsAdder, Oraxen, Nexo, Geyser and Floodgate.
- **An API** with 13 events, and hooks for other plugins to add status sources and action types.

## Quick start

1. Download the jar from the [latest release](https://github.com/TheMaydayMan/headcount/releases/latest), drop it in `plugins/` on a Paper 26.3 server running Java 25, and start it.
2. The first cycle starts immediately. Players type `/hc` to see their vault and `/hc help` for everything else.
3. Tune `plugins/Headcount/*.yml` (every option is commented), then run `/hca reload` and `/hca selftest`.

## Documentation

| Server owners | |
|---|---|
| [Installation](docs/installation.md) | Requirements, install, update, build |
| [Config reference](docs/config-reference.md) | Every option in every file, with defaults |
| [Heads, vault, kills and trading](docs/heads.md) | How the economy works and how it's protected |
| [Statuses](docs/statuses.md) | Every status source |
| [Actions](docs/actions.md) | Every action type and the common options |
| [Score formula](docs/formula.md) | Every variable and function, with examples |
| [Outcomes](docs/outcomes.md), [Shop](docs/shop.md), [Titles](docs/titles.md), [Hooks](docs/hooks.md) | End-of-cycle rules, the shop, titles, and timed action lists |
| [Placeholders](docs/placeholders.md), [Text](docs/text.md) | Placeholders and MiniMessage |
| [Integrations](docs/integrations.md) | Which plugin handles what, Bedrock |
| [Commands and permissions](docs/commands.md) | Every command and node |
| [Example setups](docs/examples.md) | Ready-made variations |

| Plugin developers | |
|---|---|
| [Developer guide](docs/developers.md) | The API, events, and adding status sources and action types |

Also: [DECISIONS.md](DECISIONS.md) (design choices the spec left open), [TESTING.md](TESTING.md) (what is tested automatically, and the manual checklist), [tools/bot-tests](tools/bot-tests/README.md) (player tests played by bots), and [TASKS.md](TASKS.md) with [PROGRESS_BAR.md](PROGRESS_BAR.md) (the build checklist, how each item was verified, and when).

## Building

```bash
./gradlew build
```

The build needs JDK 25 (Gradle finds an installed one, or downloads it). It runs the unit tests and puts the jar in `build/libs/`; it also copies the jar into `../TestServer/plugins` when that folder exists. [TestServer/setup.sh](../TestServer/README.md) sets up that test server.

## License

MIT © 2026 Grady Chastain
