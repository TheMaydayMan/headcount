# Changelog

## 1.1.0 (in progress)

Restart the server after installing it, rather than using `/reload`. The database upgrades itself on the first start. Every config file is now `config-version: 2`, and the start-up warning names the files that are older. See [installation.md](docs/installation.md#updating) for what to copy into existing configs.

### Resets
- `/hca reset cycle [delete-heads|keep-heads] confirm` starts the current cycle over from now with the same number:
  - vaults are refilled from the current config;
  - kills, activity, shop limits and stock, and warnings start again;
  - cycle-start hooks run again.

  Points, titles, streaks, history and effects are kept. Permission: `headcount.admin.reset.cycle`.
- `/hca reset hard [delete-heads|keep-heads]` deletes all Headcount data and starts again at cycle 1, after a configurable number of typed 4-digit codes (default 3). Before it deletes anything it:
  - reverts live effects, titles and prefixes;
  - backs up the database and audit log to `backups/hard-reset-<time>/`.

  It has its own permission, `headcount.admin.reset.hard`, which `headcount.admin` doesn't include.
- Each cycle run has a hidden run ID stamped on its heads, so a reset makes every older head worthless:
  - **deleted** wherever Headcount sees it: inventories, ender chests, containers, pickups, drops, deliveries and trade recovery, each one audited;
  - or kept as a **defunct** head (`heads.item.defunct-name` / `defunct-lore`).

  The default is `reset.delete-old-heads`, and the choice is stored with each reset.
- `/hca cycle info` and `/hca inspect` show the run; inspect tells current, expired and defunct heads apart.

### Vault and heads
- `<score>` in the vault title and info button: the owner's live score, colored by `vault.gui.score-colors`, or `<score:pos:neg:zero>` in place.
- The vault title updates live while open (unique heads, score), for the owner and admins viewing it.
- The bundled head lore shows the cycle number in gold on current heads and gray on expired and defunct ones.

### Integrations
- Every job is `included`, a plugin, or `none`. There is no `auto`. Old configs still load: `auto` picks the installed plugin, with a warning. A chosen plugin that's missing falls back with a warning.
- **Included PvP** (the new default), with `/pvp [on|off|status]` and `/pvp <player> [on|off]`:
  - toggle: off by default, with a cooldown, reset on join, and forced worlds;
  - new-player and respawn protection;
  - blocked hits, including projectiles, potions, fire, TNT and pets;
  - a combat tag that can block commands, teleports, flight, elytra, pearls and turning PvP off;
  - combat-log kills crediting the last attacker, with extra actions and a broadcast;
  - per-message channel and sound, and a live countdown in the action bar or a boss bar.

  New permissions: `headcount.pvp`, `headcount.pvp.others`, `headcount.pvp.override`, `headcount.bypass.pvp.cooldown`, `headcount.bypass.combat-log`, `headcount.bypass.combat-commands`.
- **Included permissions:** permission nodes saved and re-applied on join. Groups, tracks and prefixes still need LuckPerms or Vault.
- **Moderation:** the old vanilla fallback is now `included`. `moderation.kinds` gives single kinds their own provider.
- **Player states** (was `essentials`): Headcount's own AFK detection (with optional announcements), vanish metadata, god mode, and its own jail and mute. The `afk` status works without EssentialsX. The status source is now `player-state`; `essentials` is still accepted.
- **Placeholders:** `included` / `placeholderapi` / `none`.

### Text
- Everything players and admins see is in the language file or the configs; nothing is hardcoded:
  - effect descriptions, durations and states;
  - inspect words;
  - the `/hca integrations` summary.
- Every config file's header says which kind of text it holds.
- Fixed raw MiniMessage tags in:
  - boss bars (they now get every variable, such as `<cycle>`);
  - `/hc score`'s active line, `<streak_text>`, shop tier names, outcome names and the shield message.

### Config
- `cycle.time-zone` accepts abbreviations (`MST`, `EST`, `CET`, ...), offsets (`GMT-7`, `UTC+5:30`, `-07:00`), region IDs in any case, and `system`.
- New options:
  - `reset:` (`delete-old-heads`, `hard.*`);
  - `vault.gui.score-colors`;
  - `heads.item.defunct-name` / `defunct-lore`;
  - `sidebar.shown-for-new-players`.
- A full pass over every file's comments:
  - a quick start in config.yml, and references for item definitions and menus;
  - options that existed but weren't documented.

### Tools
- **Headcount Studio** (`tools/studio`; double-click a launcher in the repository's `Headcount Studio` folder): a local app for setting up, running and learning Headcount. It has:
  - visual editors for every file;
  - a live server view and an RCON console;
  - guides, recipes and a full reference;
  - review-before-save with backups.
- The test server restarts in the same terminal after `restart`, and has RCON on for Studio.

### API
- `ActionHandler.describe()` may return MiniMessage.

## 1.0.0 (2026-11-10)

First release.
