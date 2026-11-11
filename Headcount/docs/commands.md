# Commands and permissions

## Players

| Command | Permission (default: everyone) | Does |
|---|---|---|
| `/hc` (`/headcount`) | `headcount.vault` | Opens your head vault. `/hc vault <page>` opens a page. |
| `/hc shop [tier]` | `headcount.shop` | Opens the shop on the highest tier the player has unlocked, or on the given tier. |
| `/hc titles` | `headcount.titles` | Equip a title. |
| `/hc points` | `headcount.points` | Your points, points waiting for next cycle, and the rollover percent. |
| `/hc score` | `headcount.score` | Your score if the cycle ended now. |
| `/hc top [last]` | `headcount.top` | The live leaderboard, or last cycle's. |
| `/hc history` | `headcount.history` | Your past cycles. |
| `/hc sidebar` | `headcount.sidebar` | Toggle the sidebar. |
| `/hc effects` | `headcount.effects` | Your active effects and how long each lasts. |
| `/hc help` | `headcount.help` | Help, filtered to what you can use. |
| `/trade <player>`, `/trade accept`, `/trade deny`, `/trade cancel` | `headcount.trade` | Trading. |
| `/pvp [on\|off\|status]` | `headcount.pvp` | Turns your own PvP on or off, or shows it. Only with the included PvP (`integrations.yml pvp.provider: included`); with PvPManager, `/pvp` is PvPManager's. |

`headcount.player` grants all of these.

`/pvp <player> [on|off]` shows or sets someone else's PvP (`headcount.pvp.others`, op; part of `headcount.admin`). `headcount.pvp.override` (nobody by default) can attack anyone whatever their PvP and protection.

## Admins

`/hcadmin` (`/hca`, or `/hc admin`). `headcount.admin` (default: op) grants every admin permission.

| Command | Permission | Does |
|---|---|---|
| `/hca cycle info` | `headcount.admin.cycle` | Cycle number and run ID, start, end, time left, vault settings, active players. |
| `/hca cycle end confirm` | `headcount.admin.cycle` | Ends the cycle now (scores, outcomes, wipe) and starts the next. |
| `/hca cycle start` | `headcount.admin.cycle` | Starts a cycle when none is running (before the configured start date). |
| `/hca cycle simulate` | `headcount.admin.cycle` | Shows what ending now would do, without applying it. |
| `/hca cycle setend <yyyy-MM-ddTHH:mm>` / `extend <duration>` | `headcount.admin.cycle` | Changes the current cycle's end (`extend -1d` shortens it). |
| `/hca vault <player> [view]` | `headcount.admin.vault` (editing: `headcount.admin.vault.edit`) | Opens anyone's vault, online or offline. Edits are audited. |
| `/hca giveheads <player> <amount> [owner\|self] [current\|next] [inventory\|vault]` | `headcount.admin.heads` | Makes economy heads. |
| `/hca points <player> give\|take\|set <amount> [next]` | `headcount.admin.points` | Changes points now, or for next cycle. |
| `/hca title <player> grant\|revoke <title>` | `headcount.admin.titles` | Titles. |
| `/hca history <player>` | `headcount.admin.history` | Anyone's history. |
| `/hca effects <player> [list\|remove <id\|all>]` | `headcount.admin.effects` | A player's active actions. |
| `/hca reset <player> vault\|points\|titles\|streak\|effects\|history\|all confirm` | `headcount.admin.reset` | Resets part of a player's data. |
| `/hca reset cycle [delete-heads\|keep-heads] confirm` | `headcount.admin.reset` and `headcount.admin.reset.cycle` | Starts the current cycle over from now, keeping its number (see [Resets](#resets)). |
| `/hca reset hard [delete-heads\|keep-heads]`, then `/hca reset hard <code>` for each confirmation | `headcount.admin.reset` and `headcount.admin.reset.hard` (op, **not** part of `headcount.admin`; the console always has it) | Deletes all Headcount data and starts over from cycle 1 (see [Resets](#resets)). |
| `/hca trade cancel <player>` | `headcount.admin.trade` | Cancels a trade or request and returns everything. |
| `/hca audit <player\|head-id\|text...> [type:<type>] [page:<n>]` | `headcount.admin.audit` | Searches the audit log, newest first. Player names also match their UUID. |
| `/hca reload` | `headcount.admin.reload` | Reloads every config and language file. Open trades are cancelled and menus closed first. |
| `/hca status <player> [status]` | `headcount.admin.debug` | A player's statuses, live. |
| `/hca flag <player> set\|clear <flag>` | `headcount.admin.debug` | Sets a flag (usable as a status). |
| `/hca inspect` | `headcount.admin.debug` | Shows the economy data of the head in your hand: owner, cycle, run, and whether it is current, expired or defunct. |
| `/hca placeholders [player]` | `headcount.admin.debug` | Every placeholder's value for a player. |
| `/hca integrations` | `headcount.admin.debug` | Who handles each job: included, a plugin, or none, and why (for example a missing plugin). |
| `/hca selftest` | `headcount.admin.debug` | Checks every configured item, menu, message, list and status reference. It also runs quietly on start and reload, warning only about failures. |
| `/hca run <player> <list>` | `headcount.admin.run` | Runs a named action list. |
| `/hca hook <hook> [player]` | `headcount.admin.run` | Runs a hook (`cycle-start`, `first-join`, `first-join-cycle`, `became-active`, `join`, `warning:<id>`). |

`headcount.admin.notify` receives duplicate-head alerts.

## Resets

Both resets make every head made so far worthless, because each run of a cycle has a hidden run ID stamped on its heads. Add `delete-heads` or `keep-heads` to choose what happens to those heads; leave it out to use `config.yml reset.delete-old-heads` (default: delete).

- **delete-heads:** old heads are deleted wherever Headcount sees them: online players' inventories, ender chests and cursor at once; offline players' when they join; containers when opened (shulkers and bundles inside them too); items picked up or dropped, so a broken placed head drops nothing; queued deliveries and recovered trade offers. Each deletion is in the audit log (`type:head_delete`).
- **keep-heads:** they stay as **defunct** heads (`heads.item.defunct-name` and `defunct-lore`) that the vault refuses.

The choice is stored with each reset, so changing the config later never changes what an earlier reset did.

**`/hca reset cycle`** starts the current cycle over from now, with the same number and a new run ID:
- every vault is refilled with the current config's vault size and starting heads (plus "+N starting heads" bonuses that were used when the cycle first started);
- the cycle runs from now to the next scheduled end;
- kill claims, activity and playtime, per-cycle shop limits and stock, and warnings start again;
- cycle-start and first-join-cycle hooks run again.

Points, titles, streaks, history and active effects are kept, and points spent this cycle are not refunded. Online players are told (`admin.reset.cycle-broadcast`).

**`/hca reset hard`** shows what will be deleted and asks for a new random 4-digit code for each confirmation (`reset.hard.confirmations`, default 3; each within `reset.hard.confirm-timeout`). A wrong or late code cancels it. Then it:
1. cancels trades, closes menus, reverts every active effect and removes titles and their prefixes;
2. backs up the database to `backups/hard-reset-<time>/` and moves the audit log there (`reset.hard.backup-database`, `archive-audit-log`);
3. deletes all Headcount data and starts again at cycle 1, with everyone online set up as a new player.

Config files are kept. `reset.hard.enabled: false` switches the command off.

## Bypass permissions (default: nobody)

| Permission | Bypasses |
|---|---|
| `headcount.bypass.vault-move.<status>` | That status blocking vault movement. |
| `headcount.bypass.vault-open.<status>` | That status blocking opening the vault (when `block-opening` is on). |
| `headcount.bypass.trade-send.<status>`, `trade-send-target.<status>`, `trade-accept.<status>`, `trade-accept-target.<status>`, `trade-cancel.<status>` | That status in each trade check. |
| `headcount.bypass.<gate>.*` | Every status for that gate. |
| `headcount.bypass.deny.<category>` / `deny.*` | Deny actions. |
| `headcount.bypass.trade.range`, `headcount.bypass.trade.cooldown` | Trade range and cooldown. |
| `headcount.bypass.pvp.cooldown` | The included PvP's `/pvp` cooldown. |
| `headcount.bypass.combat-log` | The included PvP's combat-log punishment. |
| `headcount.bypass.combat-commands` | The included PvP's blocked commands and teleports while in combat. |
| `headcount.bypass.*` | Everything above. |

A negated gate status (`!name`) has the permission `headcount.bypass.<gate>.not-<name>`. The permissions are registered from the current config, so they work with plain Bukkit permissions as well as LuckPerms.
