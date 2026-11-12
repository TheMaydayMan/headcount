# Headcount

**Your head is the currency.**

Headcount is a Paper plugin that turns player heads into a whole server economy. Every two weeks, everyone gets a vault full of their own head. You trade them away, you take other players' heads in fights, and you try to end up with a head from every single person on the server. When the cycle ends, your collection becomes your score. A good score buys you things in the shop. A bad one, three times in a row, gets you a bounty on your name and a jack o'lantern stuck on your head.

Then the vaults empty, and it all starts again.

**[Download Headcount 1.1.1](https://github.com/TheMaydayMan/headcount/releases/latest)** for Paper 26.3 and Java 25.

## How it plays

You open your vault with `/hc` and find 18 copies of your own head in it. There are 27 slots, so there's room to grow.

Every trade costs you one of your heads and gets you one of theirs. `/trade <player>` opens a double chest where you both put in your offer, both confirm, and watch a short countdown before anything changes hands. Or skip the talking: kill someone and their head drops for you, once per person each cycle.

At the end of the cycle, every *different* head in your vault counts. The score scales with the server, so collecting most of a 5-player server feels as good as most of a 100-player one. Players who trade with everyone end up on top and spend their points in the shop. Players who sit on their own heads end up below zero, and it gets worse each cycle they keep doing it:

1. One bad cycle costs you a couple of hours of slowness.
2. Two in a row bring weakness, two fewer hearts and the Dunce title.
3. Three in a row make you Wanted: PvP locked on, your location announced, glowing, and a bounty for whoever takes you down.

New players get a shield for their first cycle, so they can learn the game before it bites.

## What's in it

**Heads that can't be faked.** Every economy head carries its owner, its cycle and a unique ID. Heads from `/give`, creative copies and last cycle's heads never count, and placed heads keep their data when you break them. Trades survive a server crash without duplicating anything, and every head can be traced from the moment it's made in the audit log.

**A shop worth saving for.** Four tiers unlock with last cycle's score: steak, speed and a shield against punishment at the bottom, then haste, head protection and a mystery box, then titles, a bigger vault and diamond blocks, and finally totems, half an hour of strength, resistance and regeneration, the one elytra in stock and the Legend title. Half of what you don't spend carries over, so you can save up.

**Titles, a sidebar and history.** Earn or buy titles like Champion, Headhunter and Collector, and pick one to wear with `/hc titles`. The sidebar shows your unique heads and live score as you trade (it works on Bedrock too). `/hc top` and `/hc history` show how everyone did.

**It runs on its own.** Headcount comes with its own PvP toggle and combat tag (logging out in a fight counts as a kill), its own permissions, bans, mutes and jails, and its own AFK detection. You don't need another plugin for any of it, and if you already run one that does the job, Headcount can hand it over ([which plugins](#plugins-it-works-with)).

**Every rule is yours.** The cycle length, the vault, the score formula, the rewards and punishments, the shop, the titles, the menus and every message (in MiniMessage) live in commented config files. Rewards and punishments can give items, potions, titles, points, bounties, shields, teleports, boss bars, commands and more, and anything timed undoes itself when it runs out, even for players who are offline. Statuses like "in combat" or "near hostile mobs" can block trading or the vault with their own message.

**Five ready-made setups.** Weekly cycles for an active group, monthly seasons for a slower server, hardcore PvP, a cozy server with no PvP at all, and a big-server setup. Each one is a few lines to paste, or one click in Headcount Studio.

**Headcount Studio.** A web app the plugin serves itself, right on the Minecraft port, so it works even on hosts that give you one port. `/hca studio` gives you the link and a password. It's an editor for every setting with live in-game previews: the shop as a real chest you drag items around in, the sidebar as it looks in game, and every message rendered in Minecraft's font. It also has a live view of your server, a console, guides, and a reference for every command and placeholder. Nothing is saved until you've seen the diff.

**For developers.** An API with events for everything that happens, plus hooks to add your own status sources and action types. [docs/developers.md](Headcount/docs/developers.md) has examples.

## Plugins it works with

Headcount needs no other plugin. Every job below has a built-in version or can be left off, and the ones you already run can take it over instead. Set who handles what in `integrations.yml`. When you add or remove one of these plugins, Headcount notices on the next start, switches that job over, and says so in the console.

| Plugin | What Headcount uses it for |
|---|---|
| [LuckPerms](https://luckperms.net) | Rewards and punishments that give permissions, groups, tracks, prefixes and suffixes, with expiry for timed ones. Title prefixes. |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) or [VaultUnlocked](https://modrinth.com/plugin/vaultunlocked) | Money rewards and costs from any economy plugin, and permissions and prefixes for permission plugins other than LuckPerms. |
| [PvPManager](https://modrinth.com/plugin/pvpmanager) | The PvP toggle, combat tag and newbie protection, in place of Headcount's own. |
| [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) | Other plugins' placeholders in Headcount's text, score formula and statuses, and Headcount's `%headcount_...%` placeholders in other plugins (scoreboards, tab lists, chat). |
| [EssentialsX](https://essentialsx.net) | AFK, vanish, god mode, jail and mute states, so rules like "no trading while AFK" use Essentials' own. Jails and mutes as punishments. |
| [LiteBans](https://www.spigotmc.org/resources/litebans.3715/), [LibertyBans](https://modrinth.com/plugin/libertybans), [AdvancedBan](https://github.com/DevLeoko/AdvancedBan) | Bans, mutes, kicks and warnings as punishments, recorded in your ban plugin's history. |
| [DiscordSRV](https://modrinth.com/plugin/discordsrv) | Posting cycle results, warnings and anything else an action list says to a Discord channel. |
| [Geyser](https://geysermc.org) and [Floodgate](https://geysermc.org) | Bedrock players. Everything already works through Geyser; with this on, they get trade requests they can answer without clicking. |
| [ItemsAdder](https://www.spigotmc.org/resources/itemsadder.73355/), [Oraxen](https://oraxen.com), [Nexo](https://nexomc.com) | Custom items in the shop, rewards, menus and punishments. |

Anything else with PlaceholderAPI placeholders can still feed Headcount's rules through a placeholder status. [docs/integrations.md](Headcount/docs/integrations.md) has the details.

## Getting started

1. Put the jar in your server's `plugins/` folder and start the server. The first cycle starts right away.
2. Players type `/hc` to open their vault and `/hc help` for everything else. Admin commands are under `/hca help`.
3. Change anything in `plugins/Headcount/*.yml`, or run `/hca studio` and skip the YAML. Then run `/hca reload` and `/hca selftest`, which checks every item, menu and message you've written.

[docs/installation.md](Headcount/docs/installation.md) covers the requirements, the optional plugins and updating from 1.0.

## Documentation

- [What's new in 1.1](Headcount/CHANGELOG.md)
- [Every config option](Headcount/docs/config-reference.md)
- [Commands and permissions](Headcount/docs/commands.md)
- [Ready-made setups](Headcount/docs/examples.md)
- [Headcount Studio](Headcount/docs/studio.md)
- [All the guides](Headcount/README.md#documentation): heads and trading, statuses, actions, the score formula, outcomes, the shop, titles, hooks, placeholders and text

## Building it yourself

You need JDK 25 (Gradle downloads one if it can't find it), git, bash and curl. The scripts run on Linux and macOS, or WSL on Windows.

```bash
git clone https://github.com/TheMaydayMan/headcount.git
cd headcount
TestServer/setup.sh                    # downloads a Paper test server, asks you to accept the Minecraft EULA
(cd Headcount && ./gradlew build)      # builds the jar into Headcount/build/libs/ and copies it to the test server
(cd TestServer && ./start.sh)          # port 25565, offline mode, so any name can join
```

`TestServer/setup.sh --with-plugins` also downloads every plugin Headcount works with, and `--with-bedrock` just Geyser and Floodgate. Once the server is up, `/hca studio` in its console gives you Studio's link, `http://localhost:25565`, and the password.

The plugin, its docs and its tools are in `Headcount/`, and the test server's setup scripts are in `TestServer/`. [TESTING.md](Headcount/TESTING.md) says what's tested automatically and what still needs a person, and [TEST-NIGHT.md](Headcount/TEST-NIGHT.md) is a plan for testing everything with 2 to 10 friends in one evening. [DECISIONS.md](Headcount/DECISIONS.md) explains why things work the way they do.

## About

Headcount was written by Claude, Anthropic's AI model, from a design by Grady Chastain. It's tested by its own self-test, unit tests and a few hundred automated player checks, but try it on a test server before you put it on one you care about.

MIT license, copyright 2026 Grady Chastain ([LICENSE](LICENSE)). The test server's downloads keep their own licenses, and the Minecraft server they fetch is under the [Minecraft EULA](https://aka.ms/MinecraftEULA).
