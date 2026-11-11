# Headcount

A Paper plugin where player heads are the currency. Every player starts each cycle (30 days by default) with a vault full of their own head. You collect other players' heads by trading and by killing, and at the end of the cycle the unique heads in your vault become your score. A good score earns points for the shop and a bad one brings punishments. Then everything resets.

Headcount is built for Paper 26.3 and Java 25, and is open source under the [MIT license](../LICENSE). Almost all of it can be configured, from the numbers and messages to the menus, statuses, score formula, rewards, punishments and shop.

## How a cycle works

1. Every player who has ever joined gets a vault filled with copies of their own head (`starting-heads`, 27 by default). Each head is tagged with the cycle number and a hidden run ID. Players who join later get the same setup.
2. `/hc` opens your vault. You get other players' heads from trades (`/trade <player>`) and kills. Killing a player drops a fresh head of theirs, once per killer and victim each cycle. Heads can be stored anywhere, but only the ones in your vault count, and only current-cycle heads can go in.
3. Each player's score comes from a formula over the unique heads in their vault (by default `unique - ceil(active / 4)`). Positive scores become points to spend in `/hc shop` during the next cycle. Negative scores trigger outcomes such as debuffs, dunce caps or bounties. Bans are included but turned off. `/hc history` keeps the results.
4. Every vault is emptied and the next cycle begins. Heads left outside vaults expire. They still work as normal heads, but they're worth nothing.

Admins can also start the current cycle over with `/hca reset cycle`, or wipe everything back to cycle 1 with `/hca reset hard`, which asks for typed confirmation codes. Each reset either deletes the old heads wherever they turn up or leaves them as defunct heads.

## Features

Economy heads carry their owner, cycle and a unique ID in the item's data, so heads from vanilla, `/give` or other plugins never count. A placed head keeps its data when the block drops. Creative copies can't go in a vault and are flagged as duplicates. The vault and the trade menu allow only the moves their rules allow, and trade offers survive a crash. Trades use a double chest with confirmations and a locked countdown.

Statuses are named player states, like "in PvP" or "near hostile mobs". They can come from the PvP plugin, PlaceholderAPI placeholders, scoreboard tags, permissions, metadata or built-in checks, and can be combined with all-of, any-of and not. A status can block vault movement or trading, with its own message and bypass permission.

Actions are what rewards, punishments, the shop and hooks actually do: messages, boss bars, commands, items, points, money, LuckPerms ranks, titles, moderation, potions, teleports, bounties and more. Any action can have a duration, stacking, a chance, a delay, repeats and conditions. Timed actions undo themselves when they run out, even across restarts and for offline players.

Hooks run action lists at cycle start and end, before the end (with a countdown boss bar), on a player's first join, first join of a cycle, on becoming active, and on every join.

The shop has tiers unlocked by last cycle's score, pages, stock, limits, cooldowns, prerequisites and confirmation. There are also titles, a sidebar that works on Bedrock, history, a leaderboard, and an audit log that traces every head by its ID.

The included PvP gives players a `/pvp` toggle (off by default), protection for new players and after respawning, and a combat tag that can block commands, teleports, flight, elytra and pearls. Logging out in combat counts as a kill for the last attacker.

Each job (PvP, permissions, moderation, player states, placeholders) is set to `included`, a supported plugin, or `none`. The supported plugins are PvPManager, PlaceholderAPI, Vault, LuckPerms, LiteBans, LibertyBans, AdvancedBan, EssentialsX, DiscordSRV, ItemsAdder, Oraxen, Nexo, Geyser and Floodgate. None of them are required.

Headcount Studio is a local app for setting all of this up without writing YAML. It can also watch a running server and send it commands over RCON. See [tools/studio](tools/studio/README.md).

Plugins can use the API and its events, and add their own status sources and action types.

## Quick start

1. Download the jar from the [latest release](https://github.com/TheMaydayMan/headcount/releases/latest), put it in `plugins/` on a Paper 26.3 server running Java 25, and start the server.
2. The first cycle starts right away. Players type `/hc` to see their vault and `/hc help` for the rest.
3. Edit `plugins/Headcount/*.yml` (every option is commented), then run `/hca reload` and `/hca selftest`. Or use Headcount Studio and skip the YAML.

## Documentation

| For server owners | |
|---|---|
| [Installation](docs/installation.md) | Requirements, installing, updating, building |
| [Config reference](docs/config-reference.md) | Every option in every file, with defaults |
| [Heads, vault, kills and trading](docs/heads.md) | How the economy works and how it's protected |
| [Statuses](docs/statuses.md) | Status sources |
| [Actions](docs/actions.md) | Action types and the options they share |
| [Score formula](docs/formula.md) | Variables and functions, with examples |
| [Outcomes](docs/outcomes.md), [Shop](docs/shop.md), [Titles](docs/titles.md), [Hooks](docs/hooks.md) | End-of-cycle rules, the shop, titles and timed action lists |
| [Placeholders](docs/placeholders.md), [Text](docs/text.md) | Placeholders and MiniMessage |
| [Integrations](docs/integrations.md) | Which plugin handles what, and Bedrock |
| [Commands and permissions](docs/commands.md) | Every command and permission node |
| [Example setups](docs/examples.md) | Ready-made variations |

| For plugin developers | |
|---|---|
| [Developer guide](docs/developers.md) | The API, events, and adding status sources and action types |

The [changelog](CHANGELOG.md) lists what changed in each version. [DECISIONS.md](DECISIONS.md) explains design choices the specification left open. [TESTING.md](TESTING.md) covers what is tested automatically and has the manual checklist, and [tools/bot-tests](tools/bot-tests/README.md) has the automated player tests.

## Building

```bash
./gradlew build
```

This puts `Headcount-<version>.jar` in `build/libs/`. It needs JDK 25, which Gradle finds (it also looks in `~/.jdks`) or downloads. The build compiles against every integration's API, the paid plugins included, and runs the unit tests. If `../TestServer/plugins` exists, the jar is copied there too ([test server README](../TestServer/README.md)). [Installation](docs/installation.md#building-from-source) has the rest.

If you change a comment in a bundled config file, run `python3 tools/gen-config-docs.py` to regenerate the config reference.

## License

MIT, copyright 2026 Grady Chastain.
