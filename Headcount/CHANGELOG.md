# Changelog

## 1.1.0 (not released yet)

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

### Tools
- Headcount Studio (`tools/studio`) is new. It's a local app for setting up, running and learning Headcount, opened from a launcher in the repository's `Headcount Studio` folder. It has visual editors for every file, a live server view with an RCON console, guides, recipes and a reference. Changes are shown for review before they're saved, with backups.
- The test server restarts in the same terminal after `restart`, and has RCON turned on for Studio.

### API
- `ActionHandler.describe()` may return MiniMessage.

## 1.0.0 (2026-11-10)

First release.
