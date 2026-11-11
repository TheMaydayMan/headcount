# Config reference

Generated from the bundled config files by `tools/gen-config-docs.py`. Each option shows its default and the
comment above it in the file. Durations accept `30s`, `10m`, `2h`, `3d`, `1w` or a number of seconds; text uses
MiniMessage and placeholders (see [text.md](text.md)).

Files: [config.yml](#configyml) · [trade.yml](#tradeyml) · [scoring.yml](#scoringyml) · [integrations.yml](#integrationsyml) · [shop.yml](#shopyml) · [titles.yml](#titlesyml) · [statuses.yml](#statusesyml) · [outcomes.yml](#outcomesyml) · [actions.yml](#actionsyml)

## config.yml

```text
General settings, the cycle, the vault, heads, kills, points, the sidebar and hooks.

Other files:
  statuses.yml      named player states (in PvP, combat tagged, ...) used by name everywhere
  trade.yml         /trade rules and the trade screen
  scoring.yml       the score formula and streaks
  actions.yml       reusable named action lists
  outcomes.yml      end-of-cycle rewards and punishments
  shop.yml          the points shop
  titles.yml        titles players can own and equip
  integrations.yml  which other plugins handle what
  lang/en_US.yml    every message players see

Text uses MiniMessage (https://docs.advntr.dev/minimessage/format) and placeholders:
  <name>               a variable for that particular text (listed next to each option)
  %headcount_<name>%   any Headcount placeholder (works without PlaceholderAPI)
  %other_placeholder%  any PlaceholderAPI placeholder, when PlaceholderAPI is installed

Durations accept 30s, 10m, 2h, 3d, 1w, combinations like 1h30m, or a plain number of seconds.
Changes apply after /hca reload, except where noted. /hca selftest then checks every item, menu, message and list.

Quick start: the settings most servers change first
  cycle.length-days, cycle.start-date, cycle.end-time, cycle.time-zone     when cycles run
  vault.size, vault.starting-heads                                          how big the game is
  activity.min-playtime                                                     who counts as active
  scoring.yml formula                                                       what a score is worth
  outcomes.yml, shop.yml                                                    rewards, punishments, the shop
  integrations.yml                                                          PvP, permissions, moderation...

Sections in this file: general, storage, cycle, activity, vault, heads, duplicates, kills, points,
gates / gui / placeholders / top (small display settings), history, sidebar, audit, reset, hooks.

Item definitions (any 'material:' item here and in the other files: menu buttons, shop displays, give-item...):
  material      a Minecraft material, e.g. DIAMOND_SWORD (or custom: below instead)
  name, lore    MiniMessage text (lore is a list)
  amount        stack size;  max-stack-size  1-99
  glint         true/false enchantment shine;  enchants  {sharpness: 3, unbreaking: 2}
  unbreakable   true;  flags  [HIDE_ENCHANTS, HIDE_ATTRIBUTES, ...]
  item-model    namespace:key (resource packs);  custom-model-data  a number
  head          viewer | <player name> | <uuid> for PLAYER_HEAD items
  custom        itemsadder:ns:id | oraxen:id | nexo:id (integrations.yml items)
  pdc           {namespace:key: value} persistent data, for other plugins to recognize the item

Menus (vault.gui, history.gui, and the menus in trade.yml, shop.yml and titles.yml):
  title         the window title;  rows  1-6
  filler        an item definition for empty slots
  filler-slots  a list like [0-8, 45-53], or: empty (every free slot) | border (the outer ring) | none
  buttons.<id>  an item definition plus slot: N (or slots: [..]); enabled: false hides that button
  Slot numbers start at 0 in the top-left and go left to right, 9 per row.

Where text lives: this file holds text you design (menu titles, item names and lore, and the messages inside
action lists). Everything Headcount itself says (command replies, errors, confirmations, help, effect
descriptions) is in lang/en_US.yml.
```

Hooks are explained in [hooks.md](hooks.md).

| Option | Default | Description |
|---|---|---|
| `general` | `(section)` |  |
| `general.language` | `en_US` | Language file in plugins/Headcount/lang/ (without .yml). |
| `general.date-format` | `"yyyy-MM-dd HH:mm z"` | How dates are written in messages (Java DateTimeFormatter pattern). Shown in the cycle time zone. |
| `general.time-units` | `(section)` |  |
| `general.time-units.shown` | `2` | How many units a duration shows, e.g. 2 -&gt; "3d 4h", 3 -&gt; "3d 4h 12m". Unit names are in the lang file. |
| `storage` | `(section)` |  |
| `storage.file` | `headcount.db` | SQLite database file inside plugins/Headcount/. Every change is saved as it happens. |
| `cycle` | `(section)` | Cycle |
| `cycle.length-days` | `30` | Length of a cycle in days. A change applies from the next cycle, or a /hca reset cycle (the running cycle keeps its end). |
| `cycle.start-date` | `auto` | The day the first cycle starts, as YYYY-MM-DD, or "auto" to start immediately (aligned to end-time today). Before a configured future date, Headcount waits and no cycle runs. |
| `cycle.end-time` | `"00:00"` | Exact time of day (24h HH:MM) cycles roll over. |
| `cycle.time-zone` | `UTC` | Time zone for start-date and end-time. Any of: a region (America/Denver, Europe/London; follows daylight saving), an abbreviation (MST, EST, CET, ...; a fixed offset all year: MST is always UTC-7), an offset (GMT-7, UTC+5:30), or "system". (If the server was offline at a rollover, the next start... (the full comment is in the file) |
| `activity` | `(section)` |  |
| `activity.min-playtime` | `0` | Playtime during a cycle needed to count as active. 0 means joining once is enough. Only active players get a score, points and outcomes. |
| `activity.check-interval` | `30s` | How often playtime is saved and the active threshold checked. |
| `vault` | `(section)` | Head vault (/hc) |
| `vault.size` | `27` | Slots in each vault. Up to 54 fits one screen; more than 54 is split into pages of 45. Size and starting heads only change at the start of the next cycle (or at a /hca reset cycle). |
| `vault.starting-heads` | `27` | Heads of their own every player starts each cycle with (capped at the vault size). Extra starting heads from next-cycle actions (a shop purchase, the streak bonus) that don't fit are given as items instead. |
| `vault.max-size` | `270` | Largest size actions (next-cycle vault-size) may grow a vault to. |
| `vault.block-movement` | `(section)` |  |
| `vault.block-movement.statuses` | `[in-pvp, combat-tagged, creative-or-spectator, sleeping-or-dead]` | Statuses (statuses.yml) that stop a player moving heads in or out of their vault. Each has its own message (lang: gates.vault-move.&lt;status&gt;) and bypass permission (headcount.bypass.vault-move.&lt;status&gt;). Prefix a status with ! to block when it is NOT active. |
| `vault.block-movement.block-opening` | `false` | true: the same statuses also stop the vault from opening at all (messages: gates.vault-open.&lt;status&gt;, bypass: headcount.bypass.vault-open.&lt;status&gt;). false: blocked players can still open and look. |
| `vault.allow-drop-key` | `false` | Allow the drop key (Q) on a vault slot to drop a head on the ground. Off by default: take it out first. |
| `vault.duplicate-insert` | `reject` | A head whose ID is already in some vault can only be a copy (creative cloning). reject \| allow |
| `vault.save-player-data-after-change` | `true` | Save the player's own inventory to disk shortly after a vault change, so a crash cannot roll back only one side. |
| `vault.gui` | `(section)` |  |
| `vault.gui.title` | `"&lt;dark_gray&gt;&lt;owner&gt;'s Head Vault &lt;gray&gt;(&lt;uniqu...` | &lt;owner&gt; &lt;unique&gt; &lt;total&gt; &lt;size&gt; &lt;used&gt; &lt;page&gt; &lt;pages&gt;, and &lt;score&gt;: the owner's score if the cycle ended now (shown as ? while the owner is offline). &lt;score&gt; uses score-colors below; &lt;score:green:red:gray&gt; picks the positive, negative and zero colours in place (any MiniMessage colour or tag,... (the full comment is in the file) |
| `vault.gui.score-colors` | `(section)` |  |
| `vault.gui.score-colors.positive` | `green` |  |
| `vault.gui.score-colors.negative` | `red` |  |
| `vault.gui.score-colors.zero` | `gray` |  |
| `vault.gui.locked-slot` | `(section)` | Slots past the vault size on the last row. |
| `vault.gui.locked-slot.material` | `BLACK_STAINED_GLASS_PANE` |  |
| `vault.gui.locked-slot.name` | `" "` |  |
| `vault.gui.filler` | `(section)` | Only used for vaults bigger than 54 slots (paged): the bottom row. |
| `vault.gui.filler.material` | `GRAY_STAINED_GLASS_PANE` |  |
| `vault.gui.filler.name` | `" "` |  |
| `vault.gui.filler-slots` | `[45-53]` |  |
| `vault.gui.buttons` | `(section)` |  |
| `vault.gui.buttons.previous-page` | `(section)` |  |
| `vault.gui.buttons.previous-page.slot` | `45` |  |
| `vault.gui.buttons.previous-page.material` | `ARROW` |  |
| `vault.gui.buttons.previous-page.name` | `"&lt;yellow&gt;Previous page"` |  |
| `vault.gui.buttons.previous-page.lore` | `["&lt;gray&gt;Page &lt;page&gt; of &lt;pages&gt;"]` |  |
| `vault.gui.buttons.next-page` | `(section)` |  |
| `vault.gui.buttons.next-page.slot` | `53` |  |
| `vault.gui.buttons.next-page.material` | `ARROW` |  |
| `vault.gui.buttons.next-page.name` | `"&lt;yellow&gt;Next page"` |  |
| `vault.gui.buttons.next-page.lore` | `["&lt;gray&gt;Page &lt;page&gt; of &lt;pages&gt;"]` |  |
| `vault.gui.buttons.info` | `(section)` |  |
| `vault.gui.buttons.info.slot` | `49` |  |
| `vault.gui.buttons.info.material` | `PLAYER_HEAD` |  |
| `vault.gui.buttons.info.head` | `viewer` |  |
| `vault.gui.buttons.info.name` | `"&lt;gold&gt;&lt;owner&gt;'s vault"` |  |
| `vault.gui.buttons.info.lore` | `["&lt;gray&gt;Unique heads: &lt;white&gt;&lt;unique&gt;", "&lt;gray...` |  |
| `heads` | `(section)` | Economy heads |
| `heads.item` | `(section)` |  |
| `heads.item.name` | `"&lt;yellow&gt;&lt;owner&gt;'s Head"` | Variables: &lt;owner&gt; &lt;owner_uuid&gt; &lt;cycle&gt; &lt;current_cycle&gt; &lt;head_id&gt; &lt;head_id_short&gt; &lt;created&gt; &lt;source&gt; |
| `heads.item.lore` | `["&lt;gray&gt;Cycle &lt;gold&gt;&lt;cycle&gt;&lt;/gold&gt; economy ...` |  |
| `heads.item.expired-name` | `"&lt;gray&gt;&lt;owner&gt;'s Head &lt;dark_gray&gt;(expired)"` | A head from an older cycle keeps its data and works as a normal head but is worthless. |
| `heads.item.expired-lore` | `["&lt;red&gt;Expired&lt;/red&gt; &lt;dark_gray&gt;- from cycle &lt;...` |  |
| `heads.item.defunct-name` | `"&lt;gray&gt;&lt;owner&gt;'s Head &lt;dark_gray&gt;(defunct)"` | A defunct head: made before a /hca reset cycle or /hca reset hard that kept the old heads (keep-heads). |
| `heads.item.defunct-lore` | `["&lt;red&gt;Defunct&lt;/red&gt; &lt;dark_gray&gt;- cycle &lt;gray&...` |  |
| `heads.item.max-stack-size` | `1` | 1 keeps every head in its own slot (each head has a unique ID anyway). 1-99. |
| `heads.item.glint` | `false` | Enchantment glint on current heads. |
| `heads.item.item-model` | `""` | Optional item model (namespace:key) for resource packs; leave empty for the normal head. |
| `heads.placed` | `(section)` |  |
| `heads.placed.preserve` | `true` | Keep economy data on heads placed as blocks and give it back whenever the block becomes an item again (breaking, explosions, pistons, physics, entities, fluids, or anything else that drops it). |
| `heads.placed.creative-break` | `destroy` | Breaking a head in creative drops nothing in vanilla. destroy keeps that; drop gives the head back anyway. |
| `heads.placed.always-drop` | `true` | If an economy head block is removed and nothing dropped (explosion yield, a plugin removing drops), drop it. |
| `heads.placed.untracked-blocks` | `strip` | Head blocks carrying economy data that Headcount never saw placed (world editor copies, copied block data) are treated as plain heads (strip) and logged as duplicates. allow trusts them. |
| `duplicates` | `(section)` |  |
| `duplicates.notify-admins` | `true` | Message admins with headcount.admin.notify when a duplicated head ID is found. |
| `duplicates.remove-copies` | `false` | Remove extra copies found in an inventory (the copy already in a vault is kept). Off: only flag them. |
| `duplicates.scan-on-inventory-close` | `true` | Scan the player's inventory for duplicates when they close any inventory. |
| `kills` | `(section)` | Kills |
| `kills.enabled` | `true` | Killing a player drops a newly made head of the victim (current cycle) at the victim's location. It is not taken from their vault. The killer is whoever vanilla credits (projectiles and knock-into-lava count). Suicide gives nothing. The drop happens whether or not keepInventory is on. |
| `kills.once-per-victim-per-cycle` | `true` | Only one head per killer per victim per cycle. |
| `kills.heads-per-kill` | `1` | Heads dropped per kill (extra-heads actions add to this). |
| `kills.disabled-worlds` | `[]` | Worlds where kills drop nothing. |
| `kills.killer-blocked-statuses` | `[]` | Statuses on the killer that stop a drop (e.g. creative-or-spectator). |
| `kills.killer-pickup-only` | `0s` | For this long only the killer can pick the head up (0 = anyone). |
| `points` | `(section)` | Points (Headcount's own currency, not Vault money) |
| `points.rollover-percent` | `0` | A positive score gives that many points at a cycle's end, to spend during the next cycle. When that cycle ends, this percent of unspent points rolls over and the rest are gone. |
| `points.earn-multiplier` | `1.0` | Points per score point (1.0 = a score of 7 gives 7 points). Rounded down. |
| `points.allow-negative` | `false` | Let balances go below zero when points are taken. |
| `gates` | `(section)` | Messages, menus, placeholders |
| `gates.message-cooldown` | `1s` | Minimum time between two "you can't do that while ..." messages to the same player. |
| `gui` | `(section)` |  |
| `gui.click-cooldown` | `150ms` | Minimum time between two button clicks in any Headcount menu (stops double purchases from fast clicks). |
| `placeholders` | `(section)` |  |
| `placeholders.true-text` | `"yes"` | How true/false placeholders (status_..., active, ...) read. |
| `placeholders.false-text` | `"no"` |  |
| `top` | `(section)` |  |
| `top.lines` | `10` | Lines shown by /hc top. |
| `top.refresh-interval` | `30s` | How often the live leaderboard (/hc top, %headcount_top_...%, ranks in previews) refreshes. |
| `history` | `(section)` |  |
| `history.gui` | `(section)` |  |
| `history.gui.title` | `"&lt;dark_gray&gt;&lt;player&gt;'s History"` | &lt;player&gt; &lt;page&gt; |
| `history.gui.rows` | `6` |  |
| `history.gui.entry-slots` | `[0-44]` |  |
| `history.gui.entry` | `(section)` | One item per past cycle. Variables: &lt;cycle&gt; &lt;unique&gt; &lt;total&gt; &lt;own&gt; &lt;score&gt; &lt;earned&gt; &lt;spent&gt; &lt;rolled_over&gt; &lt;points_after&gt; &lt;streak&gt; &lt;rank&gt; &lt;outcomes&gt; &lt;playtime&gt; &lt;kills&gt; &lt;deaths&gt; &lt;trades&gt; &lt;ended&gt; &lt;player&gt; |
| `history.gui.entry.material` | `PAPER` |  |
| `history.gui.entry.name` | `"&lt;yellow&gt;Cycle &lt;cycle&gt;"` |  |
| `history.gui.entry.lore` | `["&lt;gray&gt;Ended: &lt;white&gt;&lt;ended&gt;", "&lt;gray&gt;Uniq...` |  |
| `history.gui.entry.positive` | `{ material: LIME_DYE }` |  |
| `history.gui.entry.negative` | `{ material: RED_DYE }` |  |
| `history.gui.entry.zero` | `{ material: GRAY_DYE }` |  |
| `history.gui.entry.inactive` | `(section)` |  |
| `history.gui.entry.inactive.material` | `GRAY_STAINED_GLASS_PANE` |  |
| `history.gui.entry.inactive.lore` | `["&lt;dark_gray&gt;Not active this cycle"]` |  |
| `history.gui.filler` | `{ material: GRAY_STAINED_GLASS_PANE, name: " " }` |  |
| `history.gui.filler-slots` | `[45-53]` |  |
| `history.gui.buttons` | `(section)` |  |
| `history.gui.buttons.previous-page` | `{ slot: 45, material: ARROW, name: "&lt;yellow&gt;Newer", lore: ["&...` |  |
| `history.gui.buttons.next-page` | `{ slot: 53, material: ARROW, name: "&lt;yellow&gt;Older", lore: ["&...` |  |
| `history.gui.buttons.close` | `{ slot: 49, material: BARRIER, name: "&lt;red&gt;Close" }` |  |
| `sidebar` | `(section)` | Sidebar |
| `sidebar.enabled` | `true` | Server-wide switch. false removes Headcount's sidebar entirely so another scoreboard plugin can show Headcount's placeholders instead. Players toggle their own with /hc sidebar. |
| `sidebar.shown-for-new-players` | `false` | Whether a player's sidebar starts shown the first time they join (they can still hide it with /hc sidebar). Players who already joined keep their own choice. |
| `sidebar.update-interval` | `1s` | How often the lines are redrawn. |
| `sidebar.title` | `"&lt;gold&gt;&lt;bold&gt;HEADCOUNT"` |  |
| `sidebar.lines` | `list of 8` | Up to 15 lines. Headcount placeholders and PlaceholderAPI placeholders work here. |
| `sidebar.lines-before-start` | `["&lt;gray&gt;Headcount starts in", "&lt;white&gt;a little while"]` |  |
| `audit` | `(section)` | Audit log (plugins/Headcount/audit/) |
| `audit.enabled` | `true` |  |
| `audit.folder` | `audit` |  |
| `audit.max-file-size-mb` | `10` | Files rotate daily and when they reach this size (minimum 0.0625 = 64 KB). |
| `audit.keep-days` | `90` | Delete files older than this many days (0 = keep forever). Checked on start, reload and each new day. |
| `audit.compress-old-files` | `true` | Gzip finished files. |
| `audit.also-log-to-console` | `false` | Also print every audit line to the console. |
| `audit.log-vault-moves` | `true` | Log every head moving in or out of a vault. |
| `audit.log-each-cycle-start-head` | `true` | Log each head made at cycle start (one line per head) instead of one summary line. |
| `audit.search-page-size` | `10` | Lines per page in /hca audit. |
| `audit.types` | `(section)` | Switch individual event types off (all on by default). (see the linked page) |
| `reset` | `(section)` | Resets (/hca reset cycle, /hca reset hard) |
| `reset.delete-old-heads` | `true` | Every head made before a reset is worthless (it can't go in a vault and never counts). Each reset chooses what happens to them: add delete-heads or keep-heads to the command, or leave it out to use this default. delete (true): deleted wherever Headcount sees them: inventories and ender chests at... (the full comment is in the file) |
| `reset.hard` | `(section)` |  |
| `reset.hard.enabled` | `true` | /hca reset hard deletes every player's Headcount data and starts again from cycle 1, keeping these config files. It needs the headcount.admin.reset.hard permission (the console always has it). |
| `reset.hard.confirmations` | `3` | How many times the admin must type a new random code to confirm (at least 1). |
| `reset.hard.confirm-timeout` | `60s` | Time to type each code. |
| `reset.hard.backup-database` | `true` | Copy the database to plugins/Headcount/backups/hard-reset-&lt;time&gt;/ first. |
| `reset.hard.archive-audit-log` | `true` | Move the audit log files there too, so the audit log starts empty. |
| `hooks` | `(section)` | Hooks: action lists that run at moments in the cycle and in a player's time on the server. Every list uses the actions system (see actions.yml for every type and the common options). Cycle hooks (cycle-start, cycle-end, and each warning) have (cycle-start also runs after /hca reset cycle):... (the full comment is in the file) |
| `hooks.join-delay` | `1s` | Wait this long after a join before running join hooks, so messages come after the join message. |
| `hooks.missed-warning-grace` | `5m` | A warning whose moment passed while the server was offline still fires if it is at most this late. |
| `hooks.top-count` | `3` | How many players &lt;top_list&gt; lists. |
| `hooks.cycle-start` | `(section)` | (see the linked page) |
| `hooks.cycle-end` | `(section)` | (see the linked page) |
| `hooks.warnings` | `(section)` | Warnings before the end. Each needs 'before' (how long before the end it fires). (see the linked page) |
| `hooks.player` | `(section)` | (see the linked page) |

## trade.yml

```text
/trade <player> sends a request; the target accepts with /trade accept (or the chat link on Java),
denies with /trade deny, and the sender withdraws with /trade cancel.
Each player can have at most one active trade or pending request at a time, incoming or outgoing.

Where text lives: this file holds text you design (menu titles, item names and lore, and the messages inside
action lists). Everything Headcount itself says (command replies, errors, confirmations, help, effect
descriptions) is in lang/en_US.yml.
```

| Option | Default | Description |
|---|---|---|
| `request` | `(section)` |  |
| `request.timeout` | `30s` | How long a request waits before it expires. |
| `request.vanished-status` | `vanished` | A target with this status (statuses.yml) is treated as offline, so vanished staff are never revealed. |
| `confirm` | `(section)` |  |
| `confirm.countdown` | `3s` | Once both players confirm, both sides lock and the trade happens after this countdown. Any change to either side clears both confirmations. |
| `cooldown` | `(section)` |  |
| `cooldown.duration` | `10s` | Time between trades for both players (bypass: headcount.bypass.trade.cooldown). |
| `cooldown.after-cancel` | `false` | Also start the cooldown when a trade is cancelled. |
| `range` | `(section)` |  |
| `range.blocks` | `-1` | Maximum distance between the players in blocks, or -1 for no limit (bypass: headcount.bypass.trade.range). |
| `range.same-world` | `true` | Both players must be in the same world. |
| `allowed-types` | `(section)` |  |
| `allowed-types.heads-for-heads` | `true` | "Heads" are economy heads of the current cycle; everything else (including expired heads) is an "item". |
| `allowed-types.items-for-heads` | `true` |  |
| `allowed-types.items-for-items` | `true` |  |
| `allowed-types.gifts` | `true` | One side offers nothing (a gift). |
| `limits` | `(section)` |  |
| `limits.max-items` | `24` | Most item stacks one side may offer (heads not counted). |
| `limits.max-heads` | `24` | Most economy heads one side may offer. |
| `limits.only-own-heads` | `false` | Only allow offering economy heads of your own head. |
| `limits.blocked-items` | `[]` | Materials that can never be traded. |
| `statuses` | `(section)` |  |
| `statuses.block-request` | `[in-pvp, combat-tagged, creative-or-spectator, sleeping-or-dead]` | Statuses that stop a request being sent (checked on both players). Messages: gates.trade-send.&lt;status&gt; (you) and gates.trade-send-target.&lt;status&gt; (the other player). Bypass: headcount.bypass.trade-send.&lt;status&gt;. |
| `statuses.block-accept` | `[in-pvp, combat-tagged, creative-or-spectator, sleeping-or-dead]` | Statuses that stop a request being accepted (checked on both players). Messages: gates.trade-accept.&lt;status&gt; / gates.trade-accept-target.&lt;status&gt;. |
| `statuses.cancel-open` | `[in-pvp, combat-tagged, creative-or-spectator, sleeping-or-dead]` | Statuses that cancel an open trade (checked on both players, every cancel.check-interval). Bypass: headcount.bypass.trade-cancel.&lt;status&gt;. |
| `cancel` | `(section)` |  |
| `cancel.on-teleport` | `true` | An open trade is cancelled (everything returned to its owner) if either player closes the screen, disconnects, is kicked or banned, dies, changes world, leaves range, gets a cancel status, the cycle ends, or the server stops or reloads. These add more cases: |
| `cancel.teleport-min-distance` | `0` | Teleports shorter than this (in the same world) do not cancel. |
| `cancel.on-gamemode-change` | `true` |  |
| `cancel.check-interval` | `500ms` | How often open trades are checked for range, statuses and closed screens. |
| `save-player-data-after-trade` | `true` | Save both players' inventories to disk right after a trade completes, and about a second after any item moves into or out of an offer, so a crash can neither duplicate nor lose offered items. |
| `gui` | `(section)` |  |
| `gui.title` | `"&lt;dark_gray&gt;Trade with &lt;other&gt;"` | Variables in all trade texts: &lt;other&gt; &lt;seconds&gt; &lt;my_heads&gt; &lt;my_items&gt; &lt;their_heads&gt; &lt;their_items&gt; &lt;max_heads&gt; &lt;max_items&gt; |
| `gui.rows` | `6` |  |
| `gui.own-slots` | `[0-3, 9-12, 18-21, 27-30, 36-39, 45-48]` | Your offer (left) and the other player's offer (right). Both lists must be the same length. |
| `gui.other-slots` | `[5-8, 14-17, 23-26, 32-35, 41-44, 50-53]` |  |
| `gui.filler` | `(section)` |  |
| `gui.filler.material` | `BLACK_STAINED_GLASS_PANE` |  |
| `gui.filler.name` | `" "` |  |
| `gui.filler-slots` | `empty` |  |
| `gui.buttons` | `(section)` |  |
| `gui.buttons.info` | `(section)` |  |
| `gui.buttons.info.slot` | `4` |  |
| `gui.buttons.info.material` | `PLAYER_HEAD` |  |
| `gui.buttons.info.head` | `viewer` |  |
| `gui.buttons.info.name` | `"&lt;yellow&gt;Trading with &lt;other&gt;"` |  |
| `gui.buttons.info.lore` | `["&lt;gray&gt;Your offer is on the left.", "&lt;gray&gt;Heads: &lt;...` |  |
| `gui.buttons.confirm` | `(section)` |  |
| `gui.buttons.confirm.slot` | `13` |  |
| `gui.buttons.confirm.unconfirmed` | `(section)` |  |
| `gui.buttons.confirm.unconfirmed.material` | `LIME_DYE` |  |
| `gui.buttons.confirm.unconfirmed.name` | `"&lt;green&gt;Confirm trade"` |  |
| `gui.buttons.confirm.unconfirmed.lore` | `["&lt;gray&gt;Both players must confirm.", "&lt;gray&gt;Any change ...` |  |
| `gui.buttons.confirm.confirmed` | `(section)` |  |
| `gui.buttons.confirm.confirmed.material` | `LIME_CONCRETE` |  |
| `gui.buttons.confirm.confirmed.name` | `"&lt;green&gt;&lt;bold&gt;You confirmed"` |  |
| `gui.buttons.confirm.confirmed.lore` | `["&lt;gray&gt;Click to take it back."]` |  |
| `gui.buttons.other-status` | `(section)` |  |
| `gui.buttons.other-status.slot` | `22` |  |
| `gui.buttons.other-status.unconfirmed` | `(section)` |  |
| `gui.buttons.other-status.unconfirmed.material` | `GRAY_DYE` |  |
| `gui.buttons.other-status.unconfirmed.name` | `"&lt;gray&gt;&lt;other&gt; has not confirmed"` |  |
| `gui.buttons.other-status.confirmed` | `(section)` |  |
| `gui.buttons.other-status.confirmed.material` | `LIME_CONCRETE` |  |
| `gui.buttons.other-status.confirmed.name` | `"&lt;green&gt;&lt;other&gt; confirmed"` |  |
| `gui.buttons.countdown` | `(section)` |  |
| `gui.buttons.countdown.slot` | `31` |  |
| `gui.buttons.countdown.idle` | `(section)` |  |
| `gui.buttons.countdown.idle.material` | `GRAY_STAINED_GLASS_PANE` |  |
| `gui.buttons.countdown.idle.name` | `" "` |  |
| `gui.buttons.countdown.running` | `(section)` |  |
| `gui.buttons.countdown.running.material` | `CLOCK` |  |
| `gui.buttons.countdown.running.name` | `"&lt;gold&gt;Trading in &lt;seconds&gt;..."` |  |
| `gui.buttons.countdown.running.lore` | `["&lt;gray&gt;Both sides are locked.", "&lt;gray&gt;Click confirm t...` |  |
| `gui.buttons.cancel` | `(section)` |  |
| `gui.buttons.cancel.slot` | `40` |  |
| `gui.buttons.cancel.material` | `BARRIER` |  |
| `gui.buttons.cancel.name` | `"&lt;red&gt;Cancel trade"` |  |
| `gui.buttons.cancel.lore` | `["&lt;gray&gt;Everything goes back to its owner."]` |  |

## scoring.yml

```text
How each active player's end-of-cycle score is calculated. A positive score gives that many points
(config.yml points.earn-multiplier); a negative score triggers punishments (outcomes.yml).

Where text lives: this file holds text you design (menu titles, item names and lore, and the messages inside
action lists). Everything Headcount itself says (command replies, errors, confirmations, help, effect
descriptions) is in lang/en_US.yml.
```

Every formula variable and function is in [formula.md](formula.md).

| Option | Default | Description |
|---|---|---|
| `formula` | `"unique - ceil(active / 4)"` | The formula. Normal math: + - * / % (remainder) ^ (power), parentheses, comparisons (&lt; &lt;= &gt; &gt;= == !=, which give 1 or 0), && \|\| !, and "condition ? a : b". Functions: min(a, b, ...) max(...) avg(...) floor ceil round(x[, digits]) trunc abs sign pow(x, y) sqrt cbrt exp log(x[, base]) ln log10... (the full comment is in the file) |
| `rounding` | `floor` | Other ideas (copy one into 'formula'): Exponential, rewarding big collections:   floor(pow(unique, 1.5) / 2) - ceil(active / 4) Percent-based, compared to everyone:      round((percentile - 50) / 10) Rank-based, top 3 rewarded:               rank &lt;= 3 ? 10 - rank * 2 : (percentile &lt; 25 ? -3 : 0)... (the full comment is in the file) |
| `add-bonus-after-formula` | `true` | Add the 'score-bonus' action total after the formula and rounding. Set false if your formula uses 'bonus' itself. |
| `rank-mode` | `competition` | How ties rank: competition (10, 10, 8 -&gt; 1, 1, 3) or dense (10, 10, 8 -&gt; 1, 1, 2). |
| `streaks` | `(section)` |  |
| `streaks.inactive` | `pause` | A streak counts consecutive cycles with a positive score, or with a negative score. When a player is inactive for a cycle: pause (keep the streak) \| break (reset to 0) |
| `streaks.zero-score` | `break` | A score of exactly 0: break \| pause |

## integrations.yml

```text
Who handles each job that other plugins can do. Every job has a 'provider':

  included   Headcount's own version, built in. Nothing else to install. The default wherever it exists.
  <plugin>   hand the job to that plugin (it must be installed).
  none       the job is off completely, Headcount's own version included.

If the chosen plugin isn't installed (or fails to load), Headcount logs a warning and uses 'included', or
'none' where there is no included version. /hca integrations shows what is in use right now.
Changes apply after /hca reload, except switching /pvp between plugins, which needs a restart.

Where text lives: this file holds settings only. What Headcount says to players (the PvP messages, AFK
notices, ...) is in lang/en_US.yml; how those messages reach the player (chat, action bar, title, sound) is here.
```

What each integration does is in [integrations.md](integrations.md).

| Option | Default | Description |
|---|---|---|
| `pvp` | `(section)` | PvP: the PvP toggle, the combat tag and new-player protection. They feed the in-pvp, combat-tagged and newbie-protected statuses (statuses.yml), which the default vault and trade rules use, and the pvp action. |
| `pvp.provider` | `included` | included \| pvpmanager \| none included:   Headcount's own PvP (everything under 'included' below). It owns the /pvp command. pvpmanager: PvPManager does it all; the 'included' settings are ignored. none:       no PvP handling. Players can always fight (vanilla), the PvP statuses read false, and... (the full comment is in the file) |
| `pvp.included` | `(section)` | ---- Headcount's own PvP (provider: included) ---- |
| `pvp.included.toggle` | `(section)` |  |
| `pvp.included.toggle.default` | `"off"` | PvP for players who never used /pvp: on \| off. With off, players must opt in with /pvp before they can fight or be hit, which also keeps the default vault and trade rules (blocked while PvP is on) out of the way. |
| `pvp.included.toggle.allow-toggle` | `true` | Players may switch their own PvP with /pvp (also needs the permission headcount.pvp, default everyone). |
| `pvp.included.toggle.cooldown` | `10s` | Time between two switches. Bypass: headcount.bypass.pvp.cooldown |
| `pvp.included.toggle.reset-on-join` | `false` | Put everyone back to the default each time they join. |
| `pvp.included.toggle.force-on-worlds` | `[]` | Worlds where PvP is always on, or always off, whatever each player chose. Exact world names. |
| `pvp.included.toggle.force-off-worlds` | `[]` |  |
| `pvp.included.newbie-protection` | `(section)` |  |
| `pvp.included.newbie-protection.duration` | `10m` | New players can't attack or be attacked for this long after their first join (real time). 0 = off. |
| `pvp.included.newbie-protection.can-end-early` | `true` | A protected player may end it early with /pvp on. |
| `pvp.included.respawn-protection` | `(section)` |  |
| `pvp.included.respawn-protection.duration` | `5s` | Nobody can hurt a player for this long after they respawn (attacking someone ends it). 0 = off. |
| `pvp.included.combat-tag` | `(section)` |  |
| `pvp.included.combat-tag.enabled` | `true` | A hit between two players puts them "in combat" (the combat-tagged status) for 'duration'; every new hit restarts it. Arrows, tridents, thrown potions, a player's TNT and their tamed pets count as their hits. |
| `pvp.included.combat-tag.duration` | `15s` |  |
| `pvp.included.combat-tag.tag-attacker` | `true` | Who is tagged by a hit. |
| `pvp.included.combat-tag.tag-victim` | `true` |  |
| `pvp.included.combat-tag.untag-on-death` | `true` | Dying ends the tag. |
| `pvp.included.combat-tag.block-pvp-toggle` | `true       # can't turn PvP off` | While in combat: |
| `pvp.included.combat-tag.block-flight` | `true           # flying stops and can't start again (creative and s...` |  |
| `pvp.included.combat-tag.block-elytra` | `false          # can't start gliding` |  |
| `pvp.included.combat-tag.block-ender-pearls` | `false    # can't throw ender pearls` |  |
| `pvp.included.combat-tag.block-teleport` | `true         # teleports by commands and plugins (/spawn, /home, /t...` |  |
| `pvp.included.combat-tag.blocked-commands` | `[spawn, home, homes, warp, warps, tpa, tpahere, tpaccept, tpyes, ba...` | Commands that can't be used (no slash; namespaced forms like essentials:home are blocked too). Bypass for these and for teleports: headcount.bypass.combat-commands |
| `pvp.included.combat-log` | `(section)` |  |
| `pvp.included.combat-log.enabled` | `true` | Leaving the server while in combat. Being kicked or banned doesn't count. Bypass: headcount.bypass.combat-log |
| `pvp.included.combat-log.punishment` | `kill` | kill: the player dies as they leave, so their items and XP drop where they stood. none: nothing happens to them; only 'actions' and 'broadcast' below. |
| `pvp.included.combat-log.credit-last-attacker` | `true` | The player who last hit them gets the kill, and with it the kill head drop (config.yml kills). |
| `pvp.included.combat-log.actions` | `[]` | Extra actions run on the player who left (any action from actions.yml; the ones that need the player online wait for their next join). For example: actions: - type: points amount: -5 - message: "&lt;red&gt;You logged out during a fight and lost 5 points." |
| `pvp.included.combat-log.broadcast` | `true` | Tell everyone else (lang: pvp.combat-log-broadcast). |
| `pvp.included.messages` | `(section)` | How each kind of PvP message reaches the player. The texts are in lang/en_US.yml under pvp. channel: chat \| actionbar \| title \| none sound:   a sound key (e.g. "minecraft:block.note_block.bass"), or "" for none. volume and pitch: 0-2. The same message is sent at most once a second to a player. |
| `pvp.included.messages.toggled` | `(section)` | /pvp switched your PvP (pvp.toggled-on / toggled-off / newbie-ended). |
| `pvp.included.messages.toggled.channel` | `chat` |  |
| `pvp.included.messages.toggled.sound` | `"minecraft:ui.button.click"` |  |
| `pvp.included.messages.tag-start` | `(section)` | You hit someone or were hit, and are now in combat (pvp.tag-start). |
| `pvp.included.messages.tag-start.channel` | `actionbar` |  |
| `pvp.included.messages.tag-start.sound` | `"minecraft:item.armor.equip_iron"` |  |
| `pvp.included.messages.tag-end` | `(section)` | Your combat tag ended (pvp.tag-end). |
| `pvp.included.messages.tag-end.channel` | `actionbar` |  |
| `pvp.included.messages.tag-end.sound` | `""` |  |
| `pvp.included.messages.blocked-hit` | `(section)` | Your hit was stopped because someone has PvP off or is protected (pvp.blocked-hit.*). |
| `pvp.included.messages.blocked-hit.channel` | `actionbar` |  |
| `pvp.included.messages.blocked-hit.sound` | `""` |  |
| `pvp.included.messages.blocked-action` | `(section)` | Something was refused: a blocked command, teleport, flight, pearl, /pvp during combat or a cooldown. |
| `pvp.included.messages.blocked-action.channel` | `chat` |  |
| `pvp.included.messages.blocked-action.sound` | `"minecraft:block.note_block.bass"` |  |
| `pvp.included.messages.blocked-action.pitch` | `0.7` |  |
| `pvp.included.messages.countdown` | `actionbar` | The live countdown while in combat (pvp.countdown): actionbar \| bossbar \| none |
| `pvp.included.messages.countdown-bossbar-color` | `red` | Boss bar color when countdown is bossbar: pink \| blue \| red \| green \| yellow \| purple \| white |
| `pvp.pvpmanager` | `(section)` | ---- PvPManager (provider: pvpmanager) ---- |
| `pvp.pvpmanager.let-players-toggle` | `true` | PvPManager only lets players use /pvp with its permission pvpmanager.command.pvp, which is off by default, and players start with PvP on. Headcount's default rules block moving heads and trading while PvP is on, so with this on, every player may use /pvp (the permission's default becomes true; a... (the full comment is in the file) |
| `placeholders` | `(section)` | Placeholders: %...% in Headcount's text, the score formula and statuses. |
| `placeholders.provider` | `included` | included \| placeholderapi \| none included:       Headcount's own %headcount_...% placeholders only (docs/placeholders.md lists them). placeholderapi: those plus every PlaceholderAPI placeholder, and %headcount_...% for other plugins. none:           no %...% placeholders at all, Headcount's own... (the full comment is in the file) |
| `placeholders.placeholderapi` | `(section)` |  |
| `placeholders.placeholderapi.register-expansion` | `true` | Register the %headcount_...% expansion so other plugins (scoreboards, tab, chat) can show Headcount's data. |
| `placeholders.placeholderapi.expansion-format` | `legacy` | How formatted values (like %headcount_title%) are given to other plugins: legacy \| minimessage \| plain |
| `economy` | `(section)` | Money, for the money action. (Points, Headcount's own currency, need nothing.) |
| `economy.provider` | `vault` | vault \| none. vault uses whatever economy plugin is behind Vault (EssentialsX, CMI, ...). |
| `permissions` | `(section)` | Permissions: the permission, group, primary-group, track, prefix and suffix actions, and title prefixes (titles.yml permission-prefix). |
| `permissions.provider` | `included` | included \| luckperms \| vault \| none included:  Headcount gives permission nodes itself (saved, and re-applied on every join). It can't do groups, tracks, prefixes or suffixes: those actions are skipped with a warning. Use luckperms or vault. luckperms: everything, with LuckPerms' own expiry on... (the full comment is in the file) |
| `permissions.luckperms` | `(section)` |  |
| `permissions.luckperms.use-expiry` | `true` | Give timed changes LuckPerms' own expiry too, so they end even if Headcount is removed. |
| `permissions.luckperms.server-context` | `""` | Optional "server" context for nodes Headcount adds (empty = global). |
| `moderation` | `(section)` | Moderation: the ban, mute, kick, warn and jail actions. |
| `moderation.provider` | `included` | included \| litebans \| libertybans \| advancedban \| essentials \| commands \| none included:    vanilla bans (the server's ban list), kicks, warnings (a message), and Headcount's own mute and jail (settings below). commands:    any moderation plugin, driven by the command templates under 'commands'.... (the full comment is in the file) |
| `moderation.kinds` | `{}` | Give single kinds their own provider (same choices), e.g. bans by LibertyBans but jail by EssentialsX: kinds: jail: essentials Kinds: ban, mute, kick, warn, jail. Leave out a kind to use 'provider'. |
| `moderation.operator-name` | `Headcount` | Name shown as the punisher where the plugin supports it. |
| `moderation.silent` | `false` | Hide punishment broadcasts where the plugin supports it. |
| `moderation.included-jail` | `(section)` | Headcount's own jail (used by 'included', and for jail with any plugin that has none). |
| `moderation.included-jail.location` | `""` | world,x,y,z[,yaw,pitch]. Must be set for jail actions to work. E.g. "world,0,64,0" |
| `moderation.included-jail.radius` | `8` | Jailed players further than this many blocks are pulled back. |
| `moderation.included-jail.release-to` | `previous` | Where release sends them: previous \| spawn |
| `moderation.included-jail.allowed-commands` | `[msg, tell, r, hc, headcount]` | Commands jailed players may still use (everything else is blocked). |
| `moderation.included-mute` | `(section)` | Headcount's own mute (used by 'included'): blocks chat and these commands. |
| `moderation.included-mute.blocked-commands` | `[msg, tell, w, r, me, say]` |  |
| `moderation.litebans-commands` | `(section)` | LiteBans' API cannot create punishments, so LiteBans is driven by its commands. Variables: &lt;player&gt; &lt;uuid&gt; &lt;reason&gt; &lt;duration&gt; |
| `moderation.litebans-commands.ban` | `"litebans:ban &lt;player&gt; &lt;reason&gt;"` |  |
| `moderation.litebans-commands.tempban` | `"litebans:tempban &lt;player&gt; &lt;duration&gt; &lt;reason&gt;"` |  |
| `moderation.litebans-commands.unban` | `"litebans:unban &lt;player&gt;"` |  |
| `moderation.litebans-commands.mute` | `"litebans:mute &lt;player&gt; &lt;reason&gt;"` |  |
| `moderation.litebans-commands.tempmute` | `"litebans:tempmute &lt;player&gt; &lt;duration&gt; &lt;reason&gt;"` |  |
| `moderation.litebans-commands.unmute` | `"litebans:unmute &lt;player&gt;"` |  |
| `moderation.litebans-commands.kick` | `"litebans:kick &lt;player&gt; &lt;reason&gt;"` |  |
| `moderation.litebans-commands.warn` | `"litebans:warn &lt;player&gt; &lt;reason&gt;"` |  |
| `moderation.commands` | `(section)` | Templates for provider 'commands'. Leave a kind empty if your plugin can't do it (it then goes to 'included'). Variables: &lt;player&gt; &lt;uuid&gt; &lt;reason&gt; &lt;duration&gt; &lt;jail&gt; |
| `moderation.commands.ban` | `"ban &lt;player&gt; &lt;reason&gt;"` |  |
| `moderation.commands.tempban` | `""` |  |
| `moderation.commands.unban` | `"pardon &lt;player&gt;"` |  |
| `moderation.commands.mute` | `""` |  |
| `moderation.commands.tempmute` | `""` |  |
| `moderation.commands.unmute` | `""` |  |
| `moderation.commands.kick` | `"kick &lt;player&gt; &lt;reason&gt;"` |  |
| `moderation.commands.warn` | `""` |  |
| `moderation.commands.jail` | `""` |  |
| `moderation.commands.tempjail` | `""` |  |
| `moderation.commands.unjail` | `""` |  |
| `discord` | `(section)` | Discord, for the discord action. |
| `discord.provider` | `discordsrv` | discordsrv \| none |
| `discord.default-channel` | `global` | DiscordSRV game channel used when an action doesn't name one. |
| `items` | `(section)` | Custom items: item definitions written as "custom: itemsadder:ns:id", "custom: oraxen:id", "custom: nexo:id". |
| `items.providers` | `[itemsadder, oraxen, nexo]` | Which of itemsadder, oraxen and nexo to use (the ones not installed are skipped). [] turns custom items off. |
| `bedrock` | `(section)` | Bedrock players (through Geyser). Bedrock players get /trade accept instead of the chat link; every menu and the sidebar work for them. Also the bedrock status. |
| `bedrock.provider` | `floodgate` | floodgate \| geyser \| none. floodgate also knows players who have no Java account. |
| `player-state` | `(section)` | Player states: afk, vanished, god, jailed and muted, for the player-state status source (the afk and vanished statuses in statuses.yml). |
| `player-state.provider` | `included` | included \| essentials \| none included:   AFK after 'afk.after' without activity; vanished from the "vanished" flag most vanish plugins set; god mode from the player's invulnerable flag; jailed and muted from Headcount's own jail and mute (or the moderation plugin, when it can tell). essentials:... (the full comment is in the file) |
| `player-state.included` | `(section)` |  |
| `player-state.included.afk` | `(section)` |  |
| `player-state.included.afk.after` | `5m` | Time without moving, looking around, chatting, commands or clicking before a player is AFK. 0 = never. |
| `player-state.included.afk.looking-counts` | `true` | Turning the camera counts as activity. |
| `player-state.included.afk.announce` | `self` | Who is told when someone goes AFK or comes back: self \| everyone \| none (lang: player-state.*) |

## shop.yml

```text
/hc shop. Points are earned from a positive score at the end of a cycle and spent during the next one.
Each tier unlocks at a threshold of the player's score from LAST cycle and is its own set of pages.
Locked tiers can still be browsed: entries show their "locked" look and the banner shows the threshold.

Where text lives: this file holds text you design (menu titles, item names and lore, and the messages inside
action lists). Everything Headcount itself says (command replies, errors, confirmations, help, effect
descriptions) is in lang/en_US.yml.
```

Tiers and entries are explained in [shop.md](shop.md).

| Option | Default | Description |
|---|---|---|
| `settings` | `(section)` |  |
| `settings.confirm-at-price` | `50` | Purchases at or above this price open a confirmation menu first (an entry can override with confirm: true/false). |
| `settings.new-player-score` | `0` | The "last cycle score" used for players with no previous cycle (new or inactive last cycle). |
| `settings.stock-reset` | `cycle` | When global stock refills: cycle (every cycle) \| never |
| `gui` | `(section)` |  |
| `gui.title` | `"&lt;dark_gray&gt;Shop » &lt;tier&gt; &lt;dark_gray&gt;(&lt;points&...` | Variables: &lt;tier&gt; &lt;tier_id&gt; &lt;tier_number&gt; &lt;tiers&gt; &lt;page&gt; &lt;pages&gt; &lt;points&gt; &lt;last_score&gt; &lt;threshold&gt; &lt;needed&gt; |
| `gui.rows` | `6` |  |
| `gui.filler` | `(section)` |  |
| `gui.filler.material` | `GRAY_STAINED_GLASS_PANE` |  |
| `gui.filler.name` | `" "` |  |
| `gui.filler-slots` | `[0-9, 17, 18, 26, 27, 35, 36-53]` |  |
| `gui.buttons` | `(section)` |  |
| `gui.buttons.previous-tier` | `(section)` |  |
| `gui.buttons.previous-tier.slot` | `45` |  |
| `gui.buttons.previous-tier.material` | `SPECTRAL_ARROW` |  |
| `gui.buttons.previous-tier.name` | `"&lt;yellow&gt;« &lt;target_tier&gt;"` |  |
| `gui.buttons.next-tier` | `(section)` |  |
| `gui.buttons.next-tier.slot` | `53` |  |
| `gui.buttons.next-tier.material` | `SPECTRAL_ARROW` |  |
| `gui.buttons.next-tier.name` | `"&lt;yellow&gt;&lt;target_tier&gt; »"` |  |
| `gui.buttons.previous-page` | `(section)` |  |
| `gui.buttons.previous-page.slot` | `48` |  |
| `gui.buttons.previous-page.material` | `ARROW` |  |
| `gui.buttons.previous-page.name` | `"&lt;yellow&gt;Previous page"` |  |
| `gui.buttons.previous-page.lore` | `["&lt;gray&gt;Page &lt;page&gt; of &lt;pages&gt;"]` |  |
| `gui.buttons.next-page` | `(section)` |  |
| `gui.buttons.next-page.slot` | `50` |  |
| `gui.buttons.next-page.material` | `ARROW` |  |
| `gui.buttons.next-page.name` | `"&lt;yellow&gt;Next page"` |  |
| `gui.buttons.next-page.lore` | `["&lt;gray&gt;Page &lt;page&gt; of &lt;pages&gt;"]` |  |
| `gui.buttons.info` | `(section)` |  |
| `gui.buttons.info.slot` | `49` |  |
| `gui.buttons.info.material` | `SUNFLOWER` |  |
| `gui.buttons.info.name` | `"&lt;gold&gt;&lt;points&gt; points"` |  |
| `gui.buttons.info.lore` | `["&lt;gray&gt;Tier: &lt;tier&gt; &lt;dark_gray&gt;(&lt;tier_number&...` |  |
| `gui.buttons.close` | `(section)` |  |
| `gui.buttons.close.slot` | `46` |  |
| `gui.buttons.close.material` | `BARRIER` |  |
| `gui.buttons.close.name` | `"&lt;red&gt;Close"` |  |
| `gui.buttons.locked-banner` | `(section)` |  |
| `gui.buttons.locked-banner.slot` | `4` |  |
| `gui.buttons.locked-banner.material` | `IRON_BARS` |  |
| `gui.buttons.locked-banner.name` | `"&lt;red&gt;Locked: &lt;tier&gt;"` |  |
| `gui.buttons.locked-banner.lore` | `["&lt;gray&gt;Needs a last cycle score of &lt;white&gt;&lt;threshol...` |  |
| `confirm-gui` | `(section)` | The confirmation menu for expensive purchases. The entry being bought is shown in the 'display' slot. |
| `confirm-gui.title` | `"&lt;dark_gray&gt;Buy &lt;name&gt; for &lt;price&gt; points?"` |  |
| `confirm-gui.rows` | `3` |  |
| `confirm-gui.filler` | `{ material: GRAY_STAINED_GLASS_PANE, name: " " }` |  |
| `confirm-gui.filler-slots` | `empty` |  |
| `confirm-gui.buttons` | `(section)` |  |
| `confirm-gui.buttons.confirm` | `(section)` |  |
| `confirm-gui.buttons.confirm.slots` | `[10, 11, 12]` |  |
| `confirm-gui.buttons.confirm.material` | `LIME_CONCRETE` |  |
| `confirm-gui.buttons.confirm.name` | `"&lt;green&gt;Buy for &lt;price&gt; points"` |  |
| `confirm-gui.buttons.confirm.lore` | `["&lt;gray&gt;You have &lt;points&gt; points."]` |  |
| `confirm-gui.buttons.cancel` | `(section)` |  |
| `confirm-gui.buttons.cancel.slots` | `[14, 15, 16]` |  |
| `confirm-gui.buttons.cancel.material` | `RED_CONCRETE` |  |
| `confirm-gui.buttons.cancel.name` | `"&lt;red&gt;Cancel"` |  |
| `confirm-gui.buttons.display` | `(section)` |  |
| `confirm-gui.buttons.display.slot` | `13` |  |
| `default-states` | `(section)` | How entries look in each state, merged under every entry's own 'display' and 'display-states'. States: affordable, too-expensive, sold-out, locked, and optionally limit-reached, cooldown, unavailable (those three fall back to sold-out / too-expensive / too-expensive when not defined). Variables:... (the full comment is in the file) |
| `default-states.affordable` | `(section)` |  |
| `default-states.affordable.lore` | `["&lt;gray&gt;Price: &lt;gold&gt;&lt;price&gt; points", "&lt;gray&g...` |  |
| `default-states.too-expensive` | `(section)` |  |
| `default-states.too-expensive.lore` | `["&lt;gray&gt;Price: &lt;red&gt;&lt;price&gt; points", "&lt;red&gt;...` |  |
| `default-states.sold-out` | `(section)` |  |
| `default-states.sold-out.material` | `BARRIER` |  |
| `default-states.sold-out.lore` | `["&lt;gray&gt;Price: &lt;gold&gt;&lt;price&gt; points", "&lt;red&gt...` |  |
| `default-states.limit-reached` | `(section)` |  |
| `default-states.limit-reached.lore` | `["&lt;gray&gt;Price: &lt;gold&gt;&lt;price&gt; points", "&lt;red&gt...` |  |
| `default-states.cooldown` | `(section)` |  |
| `default-states.cooldown.lore` | `["&lt;gray&gt;Price: &lt;gold&gt;&lt;price&gt; points", "&lt;red&gt...` |  |
| `default-states.unavailable` | `(section)` |  |
| `default-states.unavailable.lore` | `["&lt;gray&gt;Price: &lt;gold&gt;&lt;price&gt; points", "&lt;red&gt...` |  |
| `default-states.locked` | `(section)` |  |
| `default-states.locked.material` | `GRAY_DYE` |  |
| `default-states.locked.glint` | `false` |  |
| `default-states.locked.lore` | `["&lt;dark_gray&gt;Locked", "&lt;gray&gt;Needs a last cycle score o...` |  |
| `tiers` | `(section)` | Tiers in order. 'threshold: none' is always unlocked. Tier settings: name, threshold (a last-cycle score, or none), pages (at least; entries can add more), enabled, entries. Entry settings: name                 how the entry is called in messages (default: its display name without formatting)... (the full comment is in the file) |

## titles.yml

```text
Titles players can own (from the shop, outcomes or admins) and equip with /hc titles.
The equipped title is %headcount_title% for chat and tab plugins, and can also be applied as a LuckPerms
(or Vault) prefix or suffix. An action can force a title on a player for a while (force-title).

Where text lives: this file holds text you design (menu titles, item names and lore, and the messages inside
action lists). Everything Headcount itself says (command replies, errors, confirmations, help, effect
descriptions) is in lang/en_US.yml.
```

Defining titles is explained in [titles.md](titles.md).

| Option | Default | Description |
|---|---|---|
| `settings` | `(section)` |  |
| `settings.none-text` | `""` | %headcount_title% when nothing is equipped. |
| `settings.format` | `"&lt;title&gt; "` | How %headcount_title% wraps the title's display. &lt;title&gt; is the display text. |
| `settings.auto-equip-first` | `true` | Equip a player's first title automatically. |
| `settings.permission-prefix` | `(section)` | Mirror the shown title into the permissions plugin as a prefix or suffix (legacy & colors are generated). |
| `settings.permission-prefix.enabled` | `false` | Turning this on or off with /hca reload updates online players at once. Others are updated when they next join while it is on; a prefix left from before it was turned off can be removed with your permissions plugin. |
| `settings.permission-prefix.type` | `prefix` | prefix \| suffix |
| `settings.permission-prefix.priority` | `100` | Headcount owns every prefix (or suffix) at this priority; pick one nothing else uses. |
| `settings.permission-prefix.format` | `"&lt;title&gt; "` |  |
| `titles` | `(section)` | Each title: display (MiniMessage, what players see), description (lore lines in the menu), icon (an item definition for the menu, see config.yml), enabled (false hides it). The key (champion, ...) is the ID used by /hca title, the grant-title action and shop requirements. (see the linked page) |
| `gui` | `(section)` |  |
| `gui.title` | `"&lt;dark_gray&gt;Your Titles"` | &lt;page&gt; |
| `gui.rows` | `6` |  |
| `gui.show-unowned` | `true` | Show titles the player does not own, as locked. |
| `gui.title-slots` | `[10-16, 19-25, 28-34]` |  |
| `gui.title-item` | `(section)` | Look of each title (merged with the title's own 'icon'). States: owned, equipped, forced, locked. Variables: &lt;title&gt; &lt;id&gt; &lt;description&gt; |
| `gui.title-item.material` | `NAME_TAG` |  |
| `gui.title-item.name` | `"&lt;title&gt;"` |  |
| `gui.title-item.owned` | `(section)` |  |
| `gui.title-item.owned.lore` | `["", "&lt;yellow&gt;Click to equip"]` |  |
| `gui.title-item.equipped` | `(section)` |  |
| `gui.title-item.equipped.glint` | `true` |  |
| `gui.title-item.equipped.lore` | `["", "&lt;green&gt;Equipped", "&lt;yellow&gt;Click to unequip"]` |  |
| `gui.title-item.forced` | `(section)` |  |
| `gui.title-item.forced.glint` | `true` |  |
| `gui.title-item.forced.lore` | `["", "&lt;red&gt;Forced on you for now"]` |  |
| `gui.title-item.locked` | `(section)` |  |
| `gui.title-item.locked.material` | `GRAY_DYE` |  |
| `gui.title-item.locked.lore` | `["", "&lt;dark_gray&gt;You do not own this title"]` |  |
| `gui.filler` | `{ material: GRAY_STAINED_GLASS_PANE, name: " " }` |  |
| `gui.filler-slots` | `[0-9, 17, 18, 26, 27, 35, 36-53]` |  |
| `gui.buttons` | `(section)` |  |
| `gui.buttons.previous-page` | `{ slot: 45, material: ARROW, name: "&lt;yellow&gt;Previous page", l...` |  |
| `gui.buttons.next-page` | `{ slot: 53, material: ARROW, name: "&lt;yellow&gt;Next page", lore:...` |  |
| `gui.buttons.unequip` | `{ slot: 49, material: BARRIER, name: "&lt;red&gt;Unequip title" }` |  |

## statuses.yml

```text
Named player states and how to detect each one. Nothing here blocks anything by itself: other files use
these names (vault, trade, conditions, deny lists...), and every status is also a placeholder,
%headcount_status_<name>%, so other plugins can read it.

Each status has a 'source' plus that source's settings, and optionally:
  fallback:     another status to use when this source cannot answer (its plugin is missing)
  unavailable:  true/false to use instead when there is no fallback (default false)
  description:  a note for yourself
In any list of statuses, "!name" means "not name".

Sources (full reference in docs/statuses.md):
  pvp              the PvP provider (integrations.yml pvp: included or PvPManager). check: pvp-enabled |
                   pvp-disabled | combat-tagged | newbie | respawn-protected | override
  placeholder      placeholder: "%..%", operator: equals | not-equals | greater-than | less-than |
                   greater-or-equal | less-or-equal | contains | not-contains | starts-with | ends-with | regex,
                   value: "...", ignore-case: true. Works with any plugin's PlaceholderAPI placeholders.
  scoreboard-tag   tag: name
  permission       permission: node
  metadata         key: name (true when any value is truthy), optional value: text to match
  flag             flag: name (set by the flag action)
  pvp-damage       dealt or took damage from a player recently. direction: dealt | took | either, seconds: 15
  recent-damage    took any damage recently. seconds: 10
  mob-damage       took damage from a hostile mob recently. seconds: 10
  nearby-player    radius: 16, status: (optional, the other player must have it), min: 1, visible-only: true
  nearby-hostile   radius: 8, include-targeting: true, targeting-radius: 32
  targeted-by-mob  radius: 32
  health           below: 6, or range: "<6" / "..5", percent: false
  food             below: 6, or range
  on-fire, in-lava, in-water, gliding, flying, swimming, sneaking, sprinting, sleeping, dead, invisible, op, bedrock
  falling          distance: 4 (blocks fallen so far)
  riding           vehicles: [] (empty = any vehicle; else entity types like horse, boat, minecart)
  gamemode         modes: [creative, spectator]
  world            worlds: [world_nether]
  joined-recently  seconds: 30
  potion           effect: speed, min-amplifier: 0
  y-level          range: "<0" (or below: / above:)
  biome            biomes: [desert, minecraft:badlands]
  holding          materials: [DIAMOND_SWORD], hand: main | off | any
  world-time       from: 13000, to: 23000 (ticks; wraps past midnight)
  player-state     the player-state provider (integrations.yml: included or EssentialsX).
                   check: afk | vanished | god | jailed | muted   ("essentials" is the same source's old name)
  muted            muted by the moderation plugin or by Headcount's own mute
  in-trade         in an open Headcount trade
  headcount        value: points | unique | total | rank | percentile | streak | score | ..., range: ">0"
  all-of / any-of  statuses: [a, b, "!c"]
  not              status: a
  always / never

Where text lives: this file holds text you design (menu titles, item names and lore, and the messages inside
action lists). Everything Headcount itself says (command replies, errors, confirmations, help, effect
descriptions) is in lang/en_US.yml.
```

Every status source is in [statuses.md](statuses.md).

| Option | Default | Description |
|---|---|---|
| `settings` | `(section)` |  |
| `settings.placeholder-true` | `"true"` | How %headcount_status_&lt;name&gt;% reads. |
| `settings.placeholder-false` | `"false"` |  |
| `statuses` | `(section)` | (see the linked page) |

## outcomes.yml

```text
What happens to each ACTIVE player at the end of a cycle, based on their score. (Inactive players get no
score and no outcomes.)

Each rule has:
  score:       a range, e.g. "..-1" (-1 or lower), "-4..-1", "10.." (10 or more), "5", ">=3", "<0"
  conditions:  optional extra requirements (streak, rank, statuses, expression ... see actions.yml)
  actions:     an action list (or the name of one from actions.yml)
  display:     how the rule is named in reports and history
  punishment:  true if a shield should block it (default: true when the whole score range is below 0)
  group:       rules compete within their group (default: punishments or rewards)
  priority:    optional; higher wins in most-extreme mode (default: how far the range is from 0)
  enabled:     false to switch a rule off

Variables in outcome actions: <player> <score> <unique> <total> <own> <rank> <percentile> <streak>
<points_earned> <points> <rolled_over> <cycle> <tier> <playtime> <kills> <deaths> <trades> <outcome>

Where text lives: this file holds text you design (menu titles, item names and lore, and the messages inside
action lists). Everything Headcount itself says (command replies, errors, confirmations, help, effect
descriptions) is in lang/en_US.yml.
```

Writing rules is explained in [outcomes.md](outcomes.md).

| Option | Default | Description |
|---|---|---|
| `mode` | `most-extreme` | all: a player gets every rule their score matches. most-extreme: only the most extreme matching rule in each group (a -12 gets "severe", not all three). |
| `rules` | `(section)` | (see the linked page) |

## actions.yml

```text
Rewards, punishments, shop purchases and hooks all use one actions system. An action list is a YAML list;
each entry is one action. Lists defined here under 'lists' can be used anywhere by name.

Writing an action (all four mean the same thing):
  - type: message
    text: "<green>Hello"
  - message: "<green>Hello"          (the type as the key, filling its main setting)
  - "message: <green>Hello"          (a one-line string)
  - my-list-name                     (a plain name runs that named list)
  - clear-effects                    (or a type that needs no settings, when no list has that name)

Common options (every action):
  duration:   instant (default) | 10m, 2h, 3d... | cycle (until the cycle ends; given at a cycle end, as outcomes
              are, it lasts through the next cycle) | cycles:N | death (until the
              player dies) | permanent | manual (until an admin removes it with /hca effects)
  time:       real (default) | online   - whether a timed duration counts real time or only time online
  stacking:   refresh (default) | extend | stack | ignore - what happens if the same action is already active
  key:        a name that marks actions as "the same" for stacking (default: the type and its main setting)
  chance:     0-100 (default 100)
  delay:      wait before running (not kept across restarts)
  repeat:     run again every X while active (needs a duration), e.g. lightning every 5m for 1h
  conditions: statuses: [..]  any-statuses: [..]  score: "..-1"  streak: ">=2"  rank: "1..3"  points: ">=10"
              unique: ">=5"  active: true  permission: node  expression: "unique > 5 && rank <= 3"
  start-message / end-message: MiniMessage shown when it starts and ends (<duration> <remaining>)

Timed actions are tracked and undone automatically when they end, even across restarts; for offline players
they are applied or undone on the next join. Built-in types know how to undo themselves; console and
player-command take 'revert-commands'.

Variables in action text: whatever the caller provides (<player>, <score>, <cycle>, <points>, ...) plus every
%headcount_...% and PlaceholderAPI placeholder.

ACTION TYPES (settings in parentheses; full reference in docs/actions.md)
Messages and effects
  message       (text [string or list], audience: player | all | world | nearby:<r> | permission:<node>)
  broadcast     (text, permission, console: true)
  title         (title, subtitle, fade-in, stay, fade-out, audience)
  actionbar     (text, audience)
  sound         (sound: entity.experience_orb.pickup, volume, pitch, source, audience)
  particle      (particle, count, offset, speed, height)
  bossbar       (text, color, overlay, progress, countdown-to: cycle-end | 60s | 2026-12-01T00:00, seconds)
                variables <countdown> <countdown_clock> <seconds>; in global hooks it shows to everyone
  discord       (text, channel) through DiscordSRV (integrations.yml discord)
  broadcast-location (text with <player> <x> <y> <z> <world>, audience: all) - add repeat for repeated reports
Commands
  console       (command or commands, revert-commands, require-online: false) - <player> <uuid> available
  player-command (command or commands, revert-commands)
Items
  give-item     (material or item: {...full item definition}, amount, to: inventory | enderchest, take-back: true)
  take-item     (item definition to match: material, name-contains, lore-contains, custom, pdc; amount: N | all;
                from: inventory | enderchest | both; return: true gives them back when a timed action ends)
  equip         (slot: head | chest | legs | feet | offhand, item, lock: true) - e.g. a dunce cap
  give-heads    (owner: self | <name> | <uuid>, amount, cycle: current | next, to: inventory | vault)
  loot          (table: minecraft:chests/simple_dungeon, luck)
  experience    (amount: +/- points, levels: false)
Currency
  points        (amount: +/-, when: now | next-cycle, revert: true)
  money         (amount: +/-, revert: true) through Vault (integrations.yml economy)
Permissions and ranks (integrations.yml permissions.provider: included does permission nodes only; LuckPerms
or Vault do all of these, and LuckPerms gets its own expiry for timed changes)
  permission    (permission, mode: add | remove, value: true)
  group         (group, mode: add | remove)
  primary-group (group)
  track         (track, direction: promote | demote) - LuckPerms only
  prefix/suffix (text, priority)
Titles
  grant-title, revoke-title (title, equip: false)
  strip-title   (which: equipped | all)
  force-title   (title) - equipped and locked for the duration
Moderation (integrations.yml moderation.provider: included uses vanilla bans and Headcount's own mutes and
jails, or hand any kind to a moderation plugin)
  ban, mute, kick, warn, jail   (reason, jail: <jail name>) - a timed duration makes it temporary
PvP (integrations.yml pvp.provider: included or PvPManager)
  pvp           (state: on | off, lock: true [can't /pvp while active], restore: true [back to before when it ends])
Buffs and debuffs (kept applied through death and milk while timed)
  potion        (effect, amplifier, seconds [for instant], ambient, particles, icon)
  attribute     (attribute: max_health | movement_speed | attack_damage | scale | jump_strength | gravity |
                block_break_speed | ..., amount, operation: add_number | add_scalar | multiply_scalar_1)
  glowing, fire (ticks), lightning (damage: false), freeze (ticks), hunger (food: +/-, exhaustion per second)
  gamemode      (mode, lock: true)
  teleport      (to: spawn | bed | random | world,x,y,z[,yaw,pitch]; random: {world, radius, min-radius, center};
                return: true sends them back when a timed teleport ends)
  heal, feed    (amount or full), damage (amount), launch (power, x, z), clear-effects
  deny          (deny: [break, place, chat, commands, items, fly, elytra, ender-pearl, worlds, trade, shop, vault,
                interact, drop, pickup, attack], commands: [..], allowed-commands: [..], items: [..], worlds: [..],
                message)
Headcount
  next-cycle    (starting-heads: "+5" | "-3" | "=10", vault-size: "+9")
  rollover      (percent: 50 to set, or add: 10)
(head-protection, extra-heads, bounty, shield, deny and force-title only work while active: give them a
duration, such as cycle, 1d or permanent. /hca selftest flags one without.)
  head-protection (charges: 0 = unlimited) - your head does not drop when you are killed
  extra-heads   (amount, charges) - extra heads drop when you are killed
  bounty        (points, actions: [...] for the killer, claims: 1, announce: true)
  shield        (charges: 1) - blocks the next punishment outcome
  flag          (flag, mode: set | clear) - usable as a status (source: flag)
  score-bonus   (amount) - added to this cycle's final score
Flow
  run           (list: <name>, or actions: [...])
  random        (choices: [{weight: 3, actions: [...]}, {weight: 1, list: other-list}])

Where text lives: this file holds text you design (menu titles, item names and lore, and the messages inside
action lists). Everything Headcount itself says (command replies, errors, confirmations, help, effect
descriptions) is in lang/en_US.yml.
```

Every action type is in [actions.md](actions.md).

| Option | Default | Description |
|---|---|---|
| `lists` | `(section)` | (see the linked page) |
