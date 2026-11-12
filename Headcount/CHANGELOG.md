# Changelog

## 1.1.1 (2026-11-12)

Replace the jar and restart the server. Nothing in the database changes. `config.yml`, `integrations.yml` and `lang/en_US.yml` are now `config-version: 3`. Their new options and messages use the defaults until you add them.

### Headcount Studio is part of the plugin
- Studio is now served by the plugin, so it's there whenever the server is running. `/hca studio` shows its link and a password. `/hca studio reset` makes a new password and signs every browser out. The permission, `headcount.admin.studio`, is op-only and not part of `headcount.admin`, because Studio can edit every config file and run any command as the console.
- By default Studio shares the Minecraft port: browsers and game clients connect to the same address, so it works on hosts that give you only one port. `studio.port` gives it a port of its own, and `studio.public-url` sets the link for a subdomain or reverse proxy. [docs/studio.md](docs/studio.md) explains it.
- Studio runs commands as the console directly, so it no longer needs RCON. Sign-ins, saves and commands are logged to the console and the audit log, and five wrong passwords lock an address out for five minutes.
- The Python app and the launchers in the `Headcount Studio` folder are gone.

### Studio
- Action lists can be edited where they're used. An action that runs a list opens that list in place, with every other place that runs it listed, since the change applies to all of them. A new **Action lists** page shows every list and where it's used. **Make a list from these** turns ticked actions into a new list, and renaming a list updates every place that runs it. The add-action picker lists your own lists first.
- Every list in Studio has the same row controls: move up, move down (action cards only had up), duplicate and remove. Outcome rules, titles and shop tiers can be reordered now, shop items and statuses duplicated, and console command lists dragged into order.
- **Set up** is split into **Game** and **Server**, so the slide-in menu fits on a phone without scrolling. Action cards no longer push drawers sideways on phones, drawer footers stay on one row, and the add-action picker keeps its search box in view.
- Every item in the game draws properly now: 1,657 of them, built from Minecraft's own models. Stairs, fences, anvils, slabs, walls, trapdoors and other non-cube blocks used to draw as plain cubes, and shields, banners, beds, shulker boxes, chests, decorated pots, the conduit and mob heads were flat colour squares. Blocks also face the right way, as they do in the game's inventory. A Studio that already downloaded the textures downloads them again once, in the new format.
- A new icon: a pixel player head on Studio's teal, sharp at tab size, with a matching home-screen icon.

### Pets and trapped chests
- Three new player hooks in `config.yml`: `hurt-pet` and `kill-pet` run on a player who hurts or kills someone else's tamed pet, and `open-trapped-chest` on a player who opens a trapped chest. They can run any action, like turning the player's PvP on and locking it. They're empty by default, with examples in the comments. `hooks.cooldowns` keeps them from running on every hit.
- `pvp.pet-protection` in `integrations.yml` stops players from hurting the pets of players they couldn't fight (PvP off, new-player or respawn protection). It's off by default.

### Integrations
- Headcount remembers which plugins it found on the last start. A plugin that's new takes over the jobs it can do, a plugin that's gone hands its job back to the included version (or `none`), and each switch is one line in the console saying how to undo it. `auto-switch: false` in `integrations.yml` turns it off. On the first start after upgrading, it only notes what's installed.
- A missing `economy`, `discord` or `bedrock` provider in an old `integrations.yml` now means `none`, like the bundled file, instead of Vault, DiscordSRV and Floodgate.

### Test server and docs
- `TestServer/setup.sh` downloads only Paper now. `--with-plugins` gets every plugin Headcount works with, and `--with-bedrock` just Geyser and Floodgate. The bot tests download their own plugins.
- The README lists every plugin Headcount works with and what it uses each one for.
- The test-night plan covers Geyser and Floodgate and Headcount's included versions only. The other integrations moved to TESTING.md.
- The bot tests are up to date with 1.1 again (they still expected 1.0's 27 starting heads and old config text), and a new suite covers the pet and trapped-chest hooks. A full run is 369 checks.

## 1.1.0 (2026-11-11)

Restart the server after installing it instead of using `/reload`. The database upgrades itself on the first start. Every config file is now `config-version: 2`, and the start-up warning names any files that are older. [installation.md](docs/installation.md#updating) says what to copy into existing configs.

### Resets
- `/hca reset cycle [delete-heads|keep-heads] confirm` starts the current cycle over from now, keeping its number. Vaults are refilled from the current config, and kills, activity, shop limits and stock, and warnings start again. Cycle-start hooks run again. Points, titles, streaks, history and effects are kept. Permission: `headcount.admin.reset.cycle`.
- `/hca reset hard [delete-heads|keep-heads]` deletes all Headcount data and starts again at cycle 1. It asks for a number of typed 4-digit codes first (3 by default). Before deleting anything, it reverts live effects, titles and prefixes, and backs up the database and audit log to `backups/hard-reset-<time>/`. Its permission, `headcount.admin.reset.hard`, is not part of `headcount.admin`.
- Each cycle run stamps a hidden run ID on its heads, so a reset makes every older head worthless. Old heads are either deleted wherever Headcount sees them (inventories, ender chests, containers, pickups, drops, deliveries and trade recovery, each one audited) or kept as defunct heads, named by `heads.item.defunct-name` and `defunct-lore`. The default comes from `reset.delete-old-heads`, and each reset stores its own choice.
- `/hca cycle info` and `/hca inspect` show the run. Inspect tells current, expired and defunct heads apart.

### Vault and heads
- The vault title and info button accept `<score>`, the owner's live score colored by `vault.gui.score-colors`. `<score:pos:neg:zero>` sets the colors in place.
- The vault title updates while it's open, for the owner and for admins viewing it.
- The bundled head lore shows the cycle number in gold on current heads and in gray on expired and defunct ones.

### Integrations
- Every job is set to `included`, a plugin, or `none`. The `auto` setting is gone, but old configs still load, with `auto` picking the installed plugin. If a chosen plugin is missing, Headcount falls back. Both cases log a warning.
- Headcount now has its own PvP, which is the new default. It adds `/pvp [on|off|status]` and `/pvp <player> [on|off]`, and includes:
  - a toggle that's off by default, with a cooldown, a reset on join, and worlds where it's forced;
  - protection for new players and after respawning;
  - blocked hits when either player has PvP off, including projectiles, potions, fire, TNT and pets;
  - a combat tag that can block commands, teleports, flight, elytra, pearls and turning PvP off;
  - combat-log kills that credit the last attacker, with extra actions and a broadcast;
  - a channel and sound for each message, and a live countdown in the action bar or a boss bar.

  New permissions: `headcount.pvp`, `headcount.pvp.others`, `headcount.pvp.override`, `headcount.bypass.pvp.cooldown`, `headcount.bypass.combat-log`, `headcount.bypass.combat-commands`.
- The included permissions provider saves permission nodes and applies them again on join. Groups, tracks and prefixes still need LuckPerms or Vault.
- The old vanilla moderation fallback is now called `included`. `moderation.kinds` can give a single kind its own provider.
- Player states (formerly `essentials`) now have Headcount's own AFK detection with optional announcements, vanish metadata, god mode, and its own jail and mute. The `afk` status works without EssentialsX. The status source is now called `player-state`, and `essentials` is still accepted.
- Placeholders can be `included`, `placeholderapi` or `none`.

### Text
- Everything players and admins see now comes from the language file or the configs, including effect descriptions, durations and states, the words `/hca inspect` uses, and the `/hca integrations` summary.
- Each config file's header says what kind of text it holds.
- Raw MiniMessage tags no longer show up in boss bars, `/hc score`'s active line, `<streak_text>`, shop tier names, outcome names or the shield message. Boss bars now get every variable, such as `<cycle>`.

### Config
- `cycle.time-zone` accepts abbreviations (`MST`, `EST`, `CET`), offsets (`GMT-7`, `UTC+5:30`, `-07:00`), region IDs in any case, and `system`.
- New options: `reset:` (`delete-old-heads` and `hard.*`), `vault.gui.score-colors`, `heads.item.defunct-name`, `heads.item.defunct-lore` and `sidebar.shown-for-new-players`.
- Every file's comments were revised. config.yml has a quick start, there are references for item definitions and menus, and options that were missing from the comments are now documented.

### Balance
The bundled game was reworked so it plays well from 2 to 100 active players. Existing servers keep their own config files and the values in them. To try the new defaults, compare your files with the bundled ones, or apply a preset from [examples.md](docs/examples.md), which now has weekly, monthly, hardcore PvP, cozy and big-server setups. Options missing from a file now fall back to the new values instead of the old ones.

config.yml
- `cycle.length-days` 30 to 14: a player who logs on twice a week still gets several sessions to trade, and results come often enough to feel tied to what they did.
- `activity.min-playtime` 0 to 2h: someone who drops in for a look is no longer scored and punished.
- `vault.starting-heads` 27 to 18: each trade costs one of your heads, so heads are something you spend. On big servers the keenest traders run short and buy heads with items, take them in fights or buy more in the shop. The 9 empty slots show new players where other heads go.
- `kills.killer-pickup-only` 0s to 15s: the killer gets the head they earned before anyone else can grab it.
- `points.rollover-percent` 0 to 50: players can save for Legendary items, and savings level off at about twice a cycle's earnings, so a lead can't grow without limit.
- `sidebar.shown-for-new-players` false to true: watching unique heads and the score change as they trade is the quickest way for a new player to learn the game.
- The `first-join` hook also gives a shield, so a new player's first bad cycle doesn't punish them. `became-active` now tells players they count this cycle.

scoring.yml
- `formula` from `unique - ceil(active / 4)` to a point per unique head, minus a par of 1 to 4 that grows with the server, plus up to 6 for the share of everyone's heads collected. The old par left 2 to 6 players with scores of 1 to 3 and outgrew the 27-slot vault on big servers, where most players were punished every cycle. Now a full set is worth a similar score at any size, and a non-trader scores 0 to -3.

outcomes.yml
- `penalty-1`, `penalty-5` and `penalty-10` became `minor`, `penalty` and `wanted`. They climb with repeat offences (two and three negative cycles in a row) instead of score size, so one bad cycle only ever brings the minor penalty. Very low scores from bonuses still go straight to Penalty (-4) or Wanted (-6).
- `top-three` gives 3 points instead of 5 and the title, and needs a place in the top half, so on 2 to 4 players finishing third isn't a prize. The new `champion` rule gives the Champion title to first place.
- `streak-bonus` gives 2 points instead of 3 starting heads, which were rarely usable. It's flat, so a streak can't snowball.
- `ban-repeat-offenders` (still off) needs a streak of -4 instead of -3, one past Wanted.

actions.yml
- `punish-mild`: 2 hours of slowness instead of a day, counting online time. A day of playtime could last weeks for a casual player.
- `punish-medium`: 3 hours of weakness and 2 fewer hearts instead of 2 days, plus the Dunce title until the cycle ends.
- `punish-severe` is the Wanted punishment. PvP is locked on and the location broadcast every 10 minutes for 2 hours of play, so the bounty can actually be claimed. The dunce cap, glowing, the Dunce title, the 5-point bounty and an extra head for the killer last until the cycle ends.
- `dunce-cap` is a lit jack o'lantern instead of a carved pumpkin, which covered the wearer's screen.
- `reward-top` gives 3 points and no longer the title, which moved to the `champion` rule.
- `mystery-box`: the 3-own-heads prize, worth nothing, became bottles o' enchanting and an 8-point jackpot.

shop.yml
- `confirm-at-price` 50 to 10, about one good cycle's earnings, so Rare and Legendary buys ask first.
- Tier thresholds 5, 10 and 20 to 3, 6 and 9. The old Rare and Legendary tiers were out of reach on most servers. Now two trades open Uncommon on a server of up to 6 players, and a full set with 5 or more players opens Legendary.
- Prices fit a score of about 3 to 10 points a cycle. Common: steak 2 to 1, speed 3 to 2 (limit 5 to 3, online time), +3 starting heads 5 to 3, shield 8 to 4 (now lasting two cycle ends). Uncommon: haste 6 to 3, head protection 10 to 4 (2 charges for the cycle instead of a day), mystery box 7 to 4.
- Rollover moved from Rare (15 points, set to 50%) to Uncommon (3 points, 25% more), since 50% is now the default.
- Bigger vault moved from Uncommon (12) to Rare (6), since it only helps once a vault holds 27 different heads.
- Rare: the Collector title 20 to 8, the diamond block 25 to 10 with a stock of 5 instead of 10, and a new Headhunter title (6) for players who took 3 heads in fights that cycle.
- Legendary: the Legend title 60 to 25. Flight was removed because it needed EssentialsX. "5 of your heads next cycle" was removed because the heads didn't fit in a full vault. New: Totem of Undying (10), Champion's blessing (8, Strength, Resistance and Regeneration, once a day) and an elytra (20, one per cycle for the whole server).

titles.yml
- New `headhunter` title. `champion` now means first place. The `dunce` icon is a jack o'lantern to match the cap.

integrations.yml
- `economy.provider` vault to none, `discord.provider` discordsrv to none, `items.providers` to `[]` and `bedrock.provider` floodgate to none. A fresh install uses only Headcount's own providers, and nothing in the bundled content needs another plugin.

lang/en_US.yml
- `score.you-are-not-active` now tells the player to keep playing to get a score.

### Tools
- Headcount Studio (`tools/studio`) is new. It's a local app for setting up, running and learning Headcount, opened from a launcher in the repository's `Headcount Studio` folder. It has visual editors for every file, a live server view with an RCON console, guides, recipes and a reference. Changes are shown for review before they're saved, with backups.
- The test server restarts in the same terminal after `restart`, and has RCON turned on for Studio.

### API
- `ActionHandler.describe()` may return MiniMessage.

## 1.0.0 (2026-11-10)

First release.
