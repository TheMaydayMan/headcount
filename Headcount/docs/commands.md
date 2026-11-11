# Commands and permissions

## Players

| Command | Permission (default: everyone) | Does |
|---|---|---|
| `/hc` (`/headcount`) | `headcount.vault` | Opens your head vault. `/hc vault <page>` opens a page. |
| `/hc shop [tier]` | `headcount.shop` | Opens the shop on the highest tier the player has unlocked, or on the given tier. |
| `/hc titles` | `headcount.titles` | Opens the titles menu, where you equip a title. |
| `/hc points` | `headcount.points` | Shows your points, the points waiting for next cycle, and the rollover percent. |
| `/hc score` | `headcount.score` | Shows what your score would be if the cycle ended now. |
| `/hc top [last]` | `headcount.top` | Shows the live leaderboard, or last cycle's. |
| `/hc history` | `headcount.history` | Shows your past cycles. |
| `/hc sidebar` | `headcount.sidebar` | Turns the sidebar on or off. |
| `/hc effects` | `headcount.effects` | Lists your active effects and how long each one lasts. |
| `/hc help` | `headcount.help` | Lists the commands you can use. |
| `/trade <player>`, `/trade accept`, `/trade deny`, `/trade cancel` | `headcount.trade` | Sends a trade request, answers one, or cancels your own. |
| `/pvp [on\|off\|status]` | `headcount.pvp` | Turns your own PvP on or off, or shows it. Only with the included PvP (`pvp.provider: included` in `integrations.yml`). With PvPManager, `/pvp` belongs to PvPManager. |

`headcount.player` grants all of these.

`/pvp <player> [on|off]` shows or sets someone else's PvP. It needs `headcount.pvp.others`, which ops have and which is part of `headcount.admin`. Players with `headcount.pvp.override` can attack anyone, whatever their PvP setting or protection. Nobody has it by default.

## Admins

The admin command is `/hcadmin`, also available as `/hca` or `/hc admin`. `headcount.admin`, which ops have by default, grants every admin permission except the hard reset.

| Command | Permission | Does |
|---|---|---|
| `/hca cycle info` | `headcount.admin.cycle` | Shows the cycle number and run ID, start and end, time left, vault settings and active players. |
| `/hca cycle end confirm` | `headcount.admin.cycle` | Ends the cycle now (scores, outcomes, wipe) and starts the next. |
| `/hca cycle start` | `headcount.admin.cycle` | Starts a cycle when none is running (before the configured start date). |
| `/hca cycle simulate` | `headcount.admin.cycle` | Shows what ending now would do, without applying it. |
| `/hca cycle setend <yyyy-MM-ddTHH:mm>` / `extend <duration>` | `headcount.admin.cycle` | Moves the current cycle's end. A negative duration such as `extend -1d` shortens it. |
| `/hca vault <player> [view]` | `headcount.admin.vault` (editing: `headcount.admin.vault.edit`) | Opens anyone's vault, online or offline. Edits are audited. |
| `/hca giveheads <player> <amount> [owner\|self] [current\|next] [inventory\|vault]` | `headcount.admin.heads` | Makes economy heads. |
| `/hca points <player> give\|take\|set <amount> [next]` | `headcount.admin.points` | Changes points now, or for next cycle. |
| `/hca title <player> grant\|revoke <title>` | `headcount.admin.titles` | Grants or removes a title. |
| `/hca history <player>` | `headcount.admin.history` | Shows anyone's past cycles. |
| `/hca effects <player> [list\|remove <id\|all>]` | `headcount.admin.effects` | Lists a player's active effects, or removes one or all of them. |
| `/hca reset <player> vault\|points\|titles\|streak\|effects\|history\|all confirm` | `headcount.admin.reset` | Resets part of a player's data. |
| `/hca reset cycle [delete-heads\|keep-heads] confirm` | `headcount.admin.reset` and `headcount.admin.reset.cycle` | Starts the current cycle over from now, keeping its number (see [Resets](#resets)). |
| `/hca reset hard [delete-heads\|keep-heads]`, then `/hca reset hard <code>` for each confirmation | `headcount.admin.reset` and `headcount.admin.reset.hard` (op only, not part of `headcount.admin`. The console always has it.) | Deletes all Headcount data and starts over from cycle 1 (see [Resets](#resets)). |
| `/hca trade cancel <player>` | `headcount.admin.trade` | Cancels a trade or request and returns everything. |
| `/hca audit <player\|head-id\|text...> [type:<type>] [page:<n>]` | `headcount.admin.audit` | Searches the audit log, newest first. Player names also match their UUID. |
| `/hca reload` | `headcount.admin.reload` | Reloads every config and language file. Open trades are cancelled and menus closed first. |
| `/hca status <player> [status]` | `headcount.admin.debug` | Shows a player's statuses as they are right now. |
| `/hca flag <player> set\|clear <flag>` | `headcount.admin.debug` | Sets or clears a flag, which statuses can check. |
| `/hca inspect` | `headcount.admin.debug` | Shows the economy data of the head in your hand: owner, cycle, run, and whether it is current, expired or defunct. |
| `/hca placeholders [player]` | `headcount.admin.debug` | Lists every placeholder's value for a player. |
| `/hca integrations` | `headcount.admin.debug` | Shows who handles each job (included, a plugin, or none) and why, for example because a plugin is missing. |
| `/hca selftest` | `headcount.admin.debug` | Checks the configured items, menus, messages, lists and status references. It also runs quietly on start-up and reload, and only warns about failures. |
| `/hca run <player> <list>` | `headcount.admin.run` | Runs a named action list. |
| `/hca hook <hook> [player]` | `headcount.admin.run` | Runs a hook (`cycle-start`, `first-join`, `first-join-cycle`, `became-active`, `join`, `warning:<id>`). |

Players with `headcount.admin.notify` get duplicate-head alerts.

## Resets

Both resets make every head made so far worthless. Each run of a cycle stamps a hidden run ID on its heads, and a reset starts a new run. Add `delete-heads` or `keep-heads` to choose what happens to the old heads. Without either, `reset.delete-old-heads` in `config.yml` decides, and its default is to delete them.

With `delete-heads`, Headcount deletes old heads wherever it finds them. Online players lose them from their inventories, ender chests and cursor right away, and offline players lose them when they next join. Containers are cleaned when opened, including shulkers and bundles inside them. A head is also deleted when it is picked up or dropped, so breaking a placed old head drops nothing. Queued deliveries and recovered trade offers are cleaned too. Each deletion goes in the audit log as `type:head_delete`.

With `keep-heads`, the heads stay as defunct heads, named with `heads.item.defunct-name` and `defunct-lore`, and the vault refuses them.

The choice is saved with each reset, so changing the config later doesn't change what an earlier reset did.

`/hca reset cycle` starts the current cycle over from now. It keeps the cycle number and gets a new run ID. The cycle then runs from now to the next scheduled end, and:

- every vault is refilled with the current config's vault size and starting heads, plus any "+N starting heads" bonuses used when the cycle first started
- kill claims, activity and playtime, per-cycle shop limits and stock, and warnings start again
- the `cycle-start` and `first-join-cycle` hooks run again

Points, titles, streaks, history and active effects are kept, and points spent this cycle aren't refunded. Online players get the `admin.reset.cycle-broadcast` message.

`/hca reset hard` first shows what will be deleted. It then asks for a new random 4-digit code for each confirmation. `reset.hard.confirmations` sets how many (3 by default), and each has to come within `reset.hard.confirm-timeout`. A wrong or late code cancels the reset. Once confirmed, it:

1. cancels trades, closes menus, reverts every active effect, and removes titles and their prefixes
2. backs up the database to `backups/hard-reset-<time>/` and moves the audit log there (`reset.hard.backup-database` and `archive-audit-log`)
3. deletes all Headcount data and starts again at cycle 1, setting up everyone online as a new player

Config files are kept. Set `reset.hard.enabled: false` to turn the command off.

## Bypass permissions (default: nobody)

| Permission | Bypasses |
|---|---|
| `headcount.bypass.vault-move.<status>` | That status blocking vault movement. |
| `headcount.bypass.vault-open.<status>` | That status blocking opening the vault (when `block-opening` is on). |
| `headcount.bypass.trade-send.<status>`, `trade-send-target.<status>`, `trade-accept.<status>`, `trade-accept-target.<status>`, `trade-cancel.<status>` | That status in each trade check. |
| `headcount.bypass.<gate>.*` | Every status for that gate. |
| `headcount.bypass.deny.<category>`, `headcount.bypass.deny.*` | Deny actions of that category, or all of them. |
| `headcount.bypass.trade.range`, `headcount.bypass.trade.cooldown` | Trade range and cooldown. |
| `headcount.bypass.pvp.cooldown` | The included PvP's `/pvp` cooldown. |
| `headcount.bypass.combat-log` | The included PvP's combat-log punishment. |
| `headcount.bypass.combat-commands` | The included PvP's blocked commands and teleports while in combat. |
| `headcount.bypass.*` | Everything above. |

A negated gate status (`!name`) is bypassed with `headcount.bypass.<gate>.not-<name>`. These permissions are registered from the current config, so they work with plain Bukkit permissions as well as LuckPerms.
