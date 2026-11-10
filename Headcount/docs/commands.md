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

`headcount.player` grants all of these.

## Admins

`/hcadmin` (`/hca`, or `/hc admin`). `headcount.admin` (default: op) grants every admin permission.

| Command | Permission | Does |
|---|---|---|
| `/hca cycle info` | `headcount.admin.cycle` | Cycle ID, start, end, time left, vault settings, active players. |
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
| `/hca trade cancel <player>` | `headcount.admin.trade` | Cancels a trade or request and returns everything. |
| `/hca audit <player\|head-id\|text...> [type:<type>] [page:<n>]` | `headcount.admin.audit` | Searches the audit log, newest first. Player names also match their UUID. |
| `/hca reload` | `headcount.admin.reload` | Reloads every config and language file. Open trades are cancelled and menus closed first. |
| `/hca status <player> [status]` | `headcount.admin.debug` | A player's statuses, live. |
| `/hca flag <player> set\|clear <flag>` | `headcount.admin.debug` | Sets a flag (usable as a status). |
| `/hca inspect` | `headcount.admin.debug` | Shows the economy data of the head in your hand. |
| `/hca placeholders [player]` | `headcount.admin.debug` | Every placeholder's value for a player. |
| `/hca integrations` | `headcount.admin.debug` | Which plugin handles what. |
| `/hca selftest` | `headcount.admin.debug` | Checks every configured item, menu, message, list and status reference. It also runs quietly on start and reload, warning only about failures. |
| `/hca run <player> <list>` | `headcount.admin.run` | Runs a named action list. |
| `/hca hook <hook> [player]` | `headcount.admin.run` | Runs a hook (`cycle-start`, `first-join`, `first-join-cycle`, `became-active`, `join`, `warning:<id>`). |

`headcount.admin.notify` receives duplicate-head alerts.

## Bypass permissions (default: nobody)

| Permission | Bypasses |
|---|---|
| `headcount.bypass.vault-move.<status>` | That status blocking vault movement. |
| `headcount.bypass.vault-open.<status>` | That status blocking opening the vault (when `block-opening` is on). |
| `headcount.bypass.trade-send.<status>`, `trade-send-target.<status>`, `trade-accept.<status>`, `trade-accept-target.<status>`, `trade-cancel.<status>` | That status in each trade check. |
| `headcount.bypass.<gate>.*` | Every status for that gate. |
| `headcount.bypass.deny.<category>` / `deny.*` | Deny actions. |
| `headcount.bypass.trade.range`, `headcount.bypass.trade.cooldown` | Trade range and cooldown. |
| `headcount.bypass.*` | Everything above. |

A negated gate status (`!name`) has the permission `headcount.bypass.<gate>.not-<name>`. The permissions are registered from the current config, so they work with plain Bukkit permissions as well as LuckPerms.
