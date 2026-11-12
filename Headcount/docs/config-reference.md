# Config reference

Generated from the bundled config files by `tools/gen-config-docs.py`. Each option shows its default and the
comment above it in the file. Durations accept `30s`, `10m`, `2h`, `3d`, `1w` or a number of seconds; text uses
MiniMessage and placeholders (see [text.md](text.md)).

Files: [config.yml](#configyml) · [trade.yml](#tradeyml) · [scoring.yml](#scoringyml) · [integrations.yml](#integrationsyml) · [shop.yml](#shopyml) · [titles.yml](#titlesyml) · [statuses.yml](#statusesyml) · [outcomes.yml](#outcomesyml) · [actions.yml](#actionsyml)

## config.yml

```text
General settings, the cycle, the vault, heads, kills, points, the sidebar and hooks.

Other files:
  statuses.yml      named player states (in PvP, combat tagged, ...) that the other files refer to
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
Changes apply after /hca reload unless a comment says otherwise. /hca selftest checks every item, menu,
message and list.

Quick start: the settings most servers change first
  cycle.length-days, cycle.start-date, cycle.end-time, cycle.time-zone     when cycles run
  vault.size, vault.starting-heads                                          how big the game is
  activity.min-playtime                                                     who counts as active
  scoring.yml formula                                                       what a score is worth
  outcomes.yml, shop.yml                                                    rewards, punishments, the shop
  integrations.yml                                                          PvP, permissions, moderation...

Item definitions (any 'material:' item here and in the other files: menu buttons, shop displays, give-item...):
  material           a Minecraft material like DIAMOND_SWORD, or use custom: instead
  name, lore         MiniMessage text (lore is a list)
  amount             stack size
  max-stack-size     1-99
  glint              true/false enchantment shine
  enchants           {sharpness: 3, unbreaking: 2}
  unbreakable        true/false
  flags              [HIDE_ENCHANTS, HIDE_ATTRIBUTES, ...]
  item-model         namespace:key, for resource packs
  custom-model-data  a number
  head               viewer | <player name> | <uuid>, for PLAYER_HEAD items
  custom             itemsadder:ns:id | oraxen:id | nexo:id (see integrations.yml items)
  pdc                {namespace:key: value} persistent data, so other plugins can recognize the item

Menus (vault.gui, history.gui, and the menus in trade.yml, shop.yml and titles.yml):
  title         the window title
  rows          1-6
  filler        an item definition for empty slots
  filler-slots  a list like [0-8, 45-53], or: empty (every free slot) | border (the outer ring) | none
  buttons.<id>  an item definition plus slot: N or slots: [..]. enabled: false hides the button.
  Slots are numbered from 0 in the top-left corner, left to right, 9 per row.

Menu titles, item names and lore, and the messages in action lists are written in these config files.
Everything else Headcount says is in lang/en_US.yml.
```

Hooks are explained in [hooks.md](hooks.md).

| Option | Default | Description |
|---|---|---|
| `general` | `(section)` |  |
| `general.language` | `en_US` | Language file in plugins/Headcount/lang/, without .yml. |
| `general.date-format` | `"yyyy-MM-dd HH:mm z"` | Date format for messages, as a Java DateTimeFormatter pattern. Dates show in the cycle time zone. |
| `general.time-units` | `(section)` |  |
| `general.time-units.shown` | `2` | How many units a duration shows: 2 gives "3d 4h" and 3 gives "3d 4h 12m". Unit names are in the lang file. |
| `storage` | `(section)` |  |
| `storage.file` | `headcount.db` | SQLite database file in plugins/Headcount/. Every change is saved as it happens. |
| `studio` | `(section)` | Headcount Studio: a web app for editing these files with in-game previews, watching the cycle live and running commands, served by the server itself. Open the link that /hca studio shows and sign in with the password it gives (it needs headcount.admin.studio, which ops have). /hca studio reset... (the full comment is in the file) |
| `studio.enabled` | `true` |  |
| `studio.port` | `same` | Where Studio listens: same   the Minecraft port. Browsers and Minecraft share it, so Studio works on hosts that give you only one port: open http://&lt;the address you join with&gt; in a browser, like http://play.example.com:25565. It doesn't work behind a proxy that only passes Minecraft traffic... (the full comment is in the file) |
| `studio.bind` | `0.0.0.0` | The network address to listen on when port is a number. 0.0.0.0 means all of them; 127.0.0.1 keeps Studio to this machine (for a reverse proxy on the same machine). |
| `studio.public-url` | `""` | The link /hca studio shows. Leave it empty to build it from the address you joined with. Set it when Studio sits behind a subdomain or reverse proxy, like https://studio.example.com |
| `studio.session-length` | `7d` | How long a browser stays signed in. |
| `studio.textures` | `true` | Download Minecraft's textures, font and sounds from Mojang the first time Studio is opened, so previews look like the game. They're kept in plugins/Headcount/studio/cache/. Without them, previews use flat colours. |
| `cycle` | `(section)` | Cycle |
| `cycle.length-days` | `14` | Length of a cycle in days. A change takes effect at the next cycle or the next /hca reset cycle. The running cycle keeps its end. Two weeks gives a player who logs on twice a week four or five sessions to trade in, and still ends often enough that rewards and punishments feel tied to what they did. |
| `cycle.start-date` | `auto` | The day the first cycle starts, as YYYY-MM-DD, or "auto" to start right away, lined up to end-time today. Until a future date arrives, no cycle runs. |
| `cycle.end-time` | `"00:00"` | Time of day cycles roll over, as 24-hour HH:MM. |
| `cycle.time-zone` | `UTC` | Time zone for start-date and end-time. Use a region like America/Denver or Europe/London (follows daylight saving), an abbreviation like MST or CET (a fixed offset all year, so MST is always UTC-7), an offset like GMT-7 or UTC+5:30, or "system". If the server was offline at a rollover, the next... (the full comment is in the file) |
| `activity` | `(section)` |  |
| `activity.min-playtime` | `2h` | Playtime in a cycle needed to count as active. 0 means joining once is enough. Only active players get a score, points and outcomes. Two hours keeps someone who drops in for a look from being scored, and punished, before they've had a chance to trade. |
| `activity.check-interval` | `30s` | How often playtime is saved and the active threshold checked. |
| `vault` | `(section)` | Head vault (/hc) |
| `vault.size` | `27` | Slots in each vault. Up to 54 fits on one screen. Bigger vaults are split into pages of 45. Size and starting heads change at the start of the next cycle or at a /hca reset cycle. |
| `vault.starting-heads` | `18` | How many of their own heads each player starts a cycle with, up to the vault size. Extra starting heads from next-cycle actions, like a shop purchase, are given as items if they don't fit. Each trade costs one of these, so they are what a player spends. 18 is enough to trade with everyone on a... (the full comment is in the file) |
| `vault.max-size` | `270` | Largest size the next-cycle vault-size action can grow a vault to. |
| `vault.block-movement` | `(section)` |  |
| `vault.block-movement.statuses` | `[in-pvp, combat-tagged, creative-or-spectator, sleeping-or-dead]` | Statuses (statuses.yml) that stop a player moving heads in or out of their vault. Each one has its own message, gates.vault-move.&lt;status&gt; in the lang file, and its own bypass permission, headcount.bypass.vault-move.&lt;status&gt;. Put ! before a status to block while it is NOT active. |
| `vault.block-movement.block-opening` | `false` | Whether the same statuses also stop the vault from opening. If false, blocked players can still open it and look. Messages are gates.vault-open.&lt;status&gt; and the bypass is headcount.bypass.vault-open.&lt;status&gt;. |
| `vault.allow-drop-key` | `false` | Whether the drop key (Q) on a vault slot drops the head on the ground. Normally players take it out first. |
| `vault.duplicate-insert` | `reject` | Whether a head whose ID is already in a vault can go in another one: reject \| allow A second head with the same ID can only be a copy, usually from creative cloning. |
| `vault.save-player-data-after-change` | `true` | Save the player's inventory to disk shortly after a vault change, so a crash can't roll back just one side. |
| `vault.gui` | `(section)` |  |
| `vault.gui.title` | `"&lt;dark_gray&gt;&lt;owner&gt;'s Head Vault &lt;gray&gt;(&lt;uniqu...` | Vault window title. Variables: &lt;owner&gt; &lt;unique&gt; &lt;total&gt; &lt;size&gt; &lt;used&gt; &lt;page&gt; &lt;pages&gt; &lt;score&gt;. &lt;score&gt; is the owner's score if the cycle ended now, or ? while the owner is offline. It uses score-colors below, or write &lt;score:green:red:gray&gt; to pick the positive, negative and zero colors right... (the full comment is in the file) |
| `vault.gui.score-colors` | `(section)` |  |
| `vault.gui.score-colors.positive` | `green` |  |
| `vault.gui.score-colors.negative` | `red` |  |
| `vault.gui.score-colors.zero` | `gray` |  |
| `vault.gui.locked-slot` | `(section)` | Slots past the vault size on the last row. |
| `vault.gui.locked-slot.material` | `BLACK_STAINED_GLASS_PANE` |  |
| `vault.gui.locked-slot.name` | `" "` |  |
| `vault.gui.filler` | `(section)` | The bottom row of paged vaults (more than 54 slots). |
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
| `heads.item.name` | `"&lt;yellow&gt;&lt;owner&gt;'s Head"` | Name of a current economy head. Variables for the name and lore: &lt;owner&gt; &lt;owner_uuid&gt; &lt;cycle&gt; &lt;current_cycle&gt; &lt;head_id&gt; &lt;head_id_short&gt; &lt;created&gt; &lt;source&gt; |
| `heads.item.lore` | `["&lt;gray&gt;Cycle &lt;gold&gt;&lt;cycle&gt;&lt;/gold&gt; economy ...` |  |
| `heads.item.expired-name` | `"&lt;gray&gt;&lt;owner&gt;'s Head &lt;dark_gray&gt;(expired)"` | Name of a head from an older cycle. It keeps its data and works as a normal head, but it's worthless. |
| `heads.item.expired-lore` | `["&lt;red&gt;Expired&lt;/red&gt; &lt;dark_gray&gt;- from cycle &lt;...` |  |
| `heads.item.defunct-name` | `"&lt;gray&gt;&lt;owner&gt;'s Head &lt;dark_gray&gt;(defunct)"` | Name of a defunct head, one made before a reset that kept the old heads (keep-heads). |
| `heads.item.defunct-lore` | `["&lt;red&gt;Defunct&lt;/red&gt; &lt;dark_gray&gt;- cycle &lt;gray&...` |  |
| `heads.item.max-stack-size` | `1` | Stack size for heads, 1-99. At 1, every head keeps its own slot. Each head has a unique ID either way. |
| `heads.item.glint` | `false` | Enchantment glint on current heads. |
| `heads.item.item-model` | `""` | Item model (namespace:key) for resource packs. Leave empty for the normal head. |
| `heads.placed` | `(section)` |  |
| `heads.placed.preserve` | `true` | Keep economy data on heads placed as blocks, and give it back whenever the block drops as an item again. |
| `heads.placed.creative-break` | `destroy` | What breaking a head block in creative does: destroy \| drop destroy drops nothing, like vanilla. drop gives the head back anyway. |
| `heads.placed.always-drop` | `true` | Drop an economy head block that was removed without dropping anything, like from an explosion or a plugin. |
| `heads.placed.untracked-blocks` | `strip` | How to treat head blocks with economy data that Headcount never saw placed: strip \| allow These come from world editor copies or copied block data. strip turns them into plain heads and logs them as duplicates. allow trusts them. |
| `duplicates` | `(section)` |  |
| `duplicates.notify-admins` | `true` | Message admins with headcount.admin.notify when a duplicated head ID is found. |
| `duplicates.remove-copies` | `false` | Remove extra copies found in an inventory. The copy already in a vault is kept. If false, copies are only flagged. |
| `duplicates.scan-on-inventory-close` | `true` | Check a player's inventory for duplicates whenever they close any inventory. |
| `kills` | `(section)` | Kills |
| `kills.enabled` | `true` | Killing a player drops a new head of the victim where they died. The head is made fresh, not taken from their vault, and it drops even with keepInventory on. The killer is whoever vanilla credits, so projectiles and knocking someone into lava count. Suicide gives nothing. |
| `kills.once-per-victim-per-cycle` | `true` | Only one head per killer per victim per cycle. |
| `kills.heads-per-kill` | `1` | Heads dropped per kill. extra-heads actions add to this. |
| `kills.disabled-worlds` | `[]` | Worlds where kills drop nothing. |
| `kills.killer-blocked-statuses` | `[]` | Statuses on the killer that stop a drop, like creative-or-spectator. |
| `kills.killer-pickup-only` | `15s` | How long only the killer can pick up the head. 0 lets anyone. |
| `points` | `(section)` | Points (Headcount's own currency, not Vault money) |
| `points.rollover-percent` | `50` | Percent of unspent points kept when a cycle ends. The rest are gone. A positive score gives points at the end of a cycle, to spend during the next one. At 50, savings level off at about twice what a player earns in a cycle, so saving for a Legendary item works but a lead can't grow forever. |
| `points.earn-multiplier` | `1.0` | Points per score point, rounded down. With 1.0 a score of 7 gives 7 points. |
| `points.allow-negative` | `false` | Let balances go below zero when points are taken. |
| `gates` | `(section)` | Messages, menus, placeholders |
| `gates.message-cooldown` | `1s` | Minimum time between two "you can't do that while ..." messages to the same player. |
| `gui` | `(section)` |  |
| `gui.click-cooldown` | `150ms` | Minimum time between two clicks in any Headcount menu. It stops fast clicks from buying something twice. |
| `placeholders` | `(section)` |  |
| `placeholders.true-text` | `"yes"` | Text that true/false placeholders show, such as the status placeholders and active. |
| `placeholders.false-text` | `"no"` |  |
| `top` | `(section)` |  |
| `top.lines` | `10` | Lines shown by /hc top. |
| `top.refresh-interval` | `30s` | How often the live leaderboard refreshes. It feeds /hc top, %headcount_top_...% and ranks in previews. |
| `history` | `(section)` |  |
| `history.gui` | `(section)` |  |
| `history.gui.title` | `"&lt;dark_gray&gt;&lt;player&gt;'s History"` | History window title. Variables: &lt;player&gt; &lt;page&gt; |
| `history.gui.rows` | `6` |  |
| `history.gui.entry-slots` | `[0-44]` |  |
| `history.gui.entry` | `(section)` | The item for each past cycle. Variables: &lt;cycle&gt; &lt;unique&gt; &lt;total&gt; &lt;own&gt; &lt;score&gt; &lt;earned&gt; &lt;spent&gt; &lt;rolled_over&gt; &lt;points_after&gt; &lt;streak&gt; &lt;rank&gt; &lt;outcomes&gt; &lt;playtime&gt; &lt;kills&gt; &lt;deaths&gt; &lt;trades&gt; &lt;ended&gt; &lt;player&gt; |
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
| `sidebar.enabled` | `true` | Turns Headcount's sidebar on for the server. Set false to show Headcount's placeholders in another scoreboard plugin instead. Players toggle their own with /hc sidebar. |
| `sidebar.shown-for-new-players` | `true` | Whether the sidebar starts shown for a player's first join. Players who already joined keep their choice. It shows a new player their unique heads and score changing as they trade, which teaches the game fastest. |
| `sidebar.update-interval` | `1s` | How often the lines are redrawn. |
| `sidebar.title` | `"&lt;gold&gt;&lt;bold&gt;HEADCOUNT"` |  |
| `sidebar.lines` | `list of 8` | Sidebar lines, up to 15. Headcount and PlaceholderAPI placeholders both work. |
| `sidebar.lines-before-start` | `["&lt;gray&gt;Headcount starts in", "&lt;white&gt;a little while"]` |  |
| `audit` | `(section)` | Audit log (plugins/Headcount/audit/) |
| `audit.enabled` | `true` |  |
| `audit.folder` | `audit` |  |
| `audit.max-file-size-mb` | `10` | Size in MB at which a log file rotates, at least 0.0625 (64 KB). Files also rotate daily. |
| `audit.keep-days` | `90` | Delete files older than this many days. 0 keeps them forever. Checked on start, on reload and each new day. |
| `audit.compress-old-files` | `true` | Gzip finished files. |
| `audit.also-log-to-console` | `false` | Also print every audit line to the console. |
| `audit.log-vault-moves` | `true` | Log every head moving in or out of a vault. |
| `audit.log-each-cycle-start-head` | `true` | Log each head made at cycle start on its own line, instead of one summary line. |
| `audit.search-page-size` | `10` | Lines per page in /hca audit. |
| `audit.types` | `(section)` | Turn single event types off. All are on by default. (see the linked page) |
| `reset` | `(section)` | Resets (/hca reset cycle, /hca reset hard) |
| `reset.delete-old-heads` | `true` | Whether a reset deletes the heads made before it, when the command doesn't say delete-heads or keep-heads. Old heads are worthless either way: they can't go in a vault and never count. true:   they are deleted wherever Headcount finds them - inventories and ender chests at the reset and on join,... (the full comment is in the file) |
| `reset.hard` | `(section)` |  |
| `reset.hard.enabled` | `true` | Allow /hca reset hard, which deletes every player's Headcount data and starts again from cycle 1. Config files are kept. It needs the headcount.admin.reset.hard permission, which the console always has. |
| `reset.hard.confirmations` | `3` | How many new random codes the admin types to confirm, at least 1. |
| `reset.hard.confirm-timeout` | `60s` | Time to type each code. |
| `reset.hard.backup-database` | `true` | Copy the database to plugins/Headcount/backups/hard-reset-&lt;time&gt;/ first. |
| `reset.hard.archive-audit-log` | `true` | Move the audit log files there too, so the audit log starts empty. |
| `hooks` | `(section)` | Hooks: action lists that run at set moments in the cycle and when players join. Each list uses the actions system. actions.yml has every type and the common options. Cycle hooks (cycle-start, cycle-end and each warning) have two parts. cycle-start also runs after /hca reset cycle. global:     ... (the full comment is in the file) |
| `hooks.join-delay` | `1s` | Delay before join hooks run, so their messages come after the join message. |
| `hooks.missed-warning-grace` | `5m` | How late a warning can still fire if its moment passed while the server was offline. |
| `hooks.top-count` | `3` | How many players &lt;top_list&gt; lists. |
| `hooks.cooldowns` | `(section)` | Shortest time between two runs of a player hook for the same player, for the hooks that follow something the player did (hurt-pet, kill-pet, open-trapped-chest below). Every hit on a pet and every opening of a trapped chest counts, so without a wait a player could get the same message many times... (the full comment is in the file) |
| `hooks.cooldowns.hurt-pet` | `10s` |  |
| `hooks.cooldowns.kill-pet` | `0s` |  |
| `hooks.cooldowns.open-trapped-chest` | `10s` |  |
| `hooks.cycle-start` | `(section)` | (see the linked page) |
| `hooks.cycle-end` | `(section)` | (see the linked page) |
| `hooks.warnings` | `(section)` | Warnings before the end of a cycle. Each one needs 'before', how long before the end it fires. (see the linked page) |
| `hooks.player` | `(section)` | (see the linked page) |

## trade.yml

```text
/trade <player> sends a request. The target accepts with /trade accept, or the chat link on Java, and
denies with /trade deny. The sender withdraws it with /trade cancel.
A player can only have one trade or pending request at a time, sent or received.

The trade menu's text is written here. Everything else Headcount says is in lang/en_US.yml.
```

| Option | Default | Description |
|---|---|---|
| `request` | `(section)` |  |
| `request.timeout` | `30s` | How long a request waits before it expires. |
| `request.vanished-status` | `vanished` | Status (statuses.yml) that makes a target count as offline, so vanished staff aren't revealed. |
| `confirm` | `(section)` |  |
| `confirm.countdown` | `3s` | Countdown between both players confirming and the trade going through. Both sides are locked meanwhile. Any change to either side clears both confirmations. |
| `cooldown` | `(section)` |  |
| `cooldown.duration` | `10s` | Time both players wait before their next trade. Bypass: headcount.bypass.trade.cooldown |
| `cooldown.after-cancel` | `false` | Also start the cooldown when a trade is cancelled. |
| `range` | `(section)` |  |
| `range.blocks` | `-1` | Maximum distance between the players in blocks. -1 means no limit. Bypass: headcount.bypass.trade.range |
| `range.same-world` | `true` | Both players must be in the same world. |
| `allowed-types` | `(section)` |  |
| `allowed-types.heads-for-heads` | `true` | Which kinds of trade are allowed. "Heads" are economy heads of the current cycle. Anything else, expired heads included, is an "item". |
| `allowed-types.items-for-heads` | `true` |  |
| `allowed-types.items-for-items` | `true` |  |
| `allowed-types.gifts` | `true` | Trades where one side offers nothing. |
| `limits` | `(section)` |  |
| `limits.max-items` | `24` | Most item stacks one side can offer, not counting heads. |
| `limits.max-heads` | `24` | Most economy heads one side can offer. |
| `limits.only-own-heads` | `false` | Players can only offer their own heads. |
| `limits.blocked-items` | `[]` | Materials that can never be traded. |
| `statuses` | `(section)` |  |
| `statuses.block-request` | `[in-pvp, combat-tagged, creative-or-spectator, sleeping-or-dead]` | Statuses that stop a request from being sent, checked on both players. The sender sees gates.trade-send.&lt;status&gt; or gates.trade-send-target.&lt;status&gt;, depending on who has it. Bypass: headcount.bypass.trade-send.&lt;status&gt; |
| `statuses.block-accept` | `[in-pvp, combat-tagged, creative-or-spectator, sleeping-or-dead]` | Statuses that stop a request from being accepted, checked on both players. Messages are gates.trade-accept.&lt;status&gt; and gates.trade-accept-target.&lt;status&gt;. |
| `statuses.cancel-open` | `[in-pvp, combat-tagged, creative-or-spectator, sleeping-or-dead]` | Statuses that cancel an open trade, checked on both players every cancel.check-interval. Bypass: headcount.bypass.trade-cancel.&lt;status&gt; |
| `cancel` | `(section)` | When an open trade is cancelled, everything goes back to its owner. A trade always cancels when a player closes the screen, leaves or dies, at a cycle end, a stop or a reload. range (which covers changing world), statuses.cancel-open and the options below add more reasons. |
| `cancel.on-teleport` | `true` | Also cancel when either player teleports. |
| `cancel.teleport-min-distance` | `0` | Teleports shorter than this many blocks, within the same world, don't cancel. |
| `cancel.on-gamemode-change` | `true` | Also cancel when either player's game mode changes. |
| `cancel.check-interval` | `500ms` | How often open trades are checked for range, statuses and closed screens. |
| `save-player-data-after-trade` | `true` | Save both players' inventories to disk when a trade completes, and about a second after any item moves in or out of an offer. A crash then can't duplicate or lose offered items. |
| `gui` | `(section)` |  |
| `gui.title` | `"&lt;dark_gray&gt;Trade with &lt;other&gt;"` | Trade window title. Every text in this menu can use &lt;other&gt; &lt;seconds&gt; &lt;my_heads&gt; &lt;my_items&gt; &lt;their_heads&gt; &lt;their_items&gt; &lt;max_heads&gt; &lt;max_items&gt; |
| `gui.rows` | `6` |  |
| `gui.own-slots` | `[0-3, 9-12, 18-21, 27-30, 36-39, 45-48]` | Slots for your offer, on the left. other-slots holds the other player's offer. Both lists must be the same length. |
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
How each active player's score is worked out at the end of a cycle. A positive score gives points
(config.yml points.earn-multiplier) and a negative one brings punishments (outcomes.yml).
```

Every formula variable and function is in [formula.md](formula.md).

| Option | Default | Description |
|---|---|---|
| `formula` | `"unique - clamp(round(sqrt(active)), 1, 4) + ceil(clamp(6 * (unique...` | The score formula, worked out for each active player when a cycle ends. It takes normal math: + - * / % (remainder) ^ (power), parentheses, comparisons (&lt; &lt;= &gt; &gt;= == !=, which give 1 or 0), && \|\| !, and "condition ? a : b". Functions: min(a, b, ...) max(...) avg(...) floor ceil round(x[,... (the full comment is in the file) |
| `rounding` | `floor` | How the formula's result is rounded to a whole number: floor \| round \| ceil |
| `add-bonus-after-formula` | `true` | Add the total from 'score-bonus' actions after the formula and rounding. Set false if your formula uses 'bonus'. |
| `rank-mode` | `competition` | How tied scores are ranked: competition \| dense Scores of 10, 10 and 8 rank 1, 1, 3 with competition and 1, 1, 2 with dense. |
| `streaks` | `(section)` |  |
| `streaks.inactive` | `pause` | What a cycle without activity does to a streak: pause \| break pause keeps the streak and break resets it to 0. A streak counts cycles in a row with a positive score, or in a row with a negative one. |
| `streaks.zero-score` | `break` | What a score of exactly 0 does to a streak: break \| pause |

## integrations.yml

```text
Who handles each job that other plugins can do. Every job has a 'provider':

  included   the built-in version, with nothing else to install. It is the default wherever it exists.
  <plugin>   hand the job to that plugin, which must be installed.
  none       turn the job off, including the built-in version.

If the chosen plugin is missing or fails to load, Headcount logs a warning and uses 'included', or 'none'
where there is no built-in version. /hca integrations shows what is in use right now.
Changes apply after /hca reload, except switching /pvp between plugins, which needs a restart.
With auto-switch on (below), Headcount also changes providers here itself when plugins are added or removed.

This file only holds settings. Player messages, like the PvP and AFK notices, are in lang/en_US.yml. How
those messages reach the player (chat, action bar, title, sound) is set here.
```

What each integration does is in [integrations.md](integrations.md).

| Option | Default | Description |
|---|---|---|
| `auto-switch` | `true` | Switch providers here automatically when plugins are added or removed, checked at each server start: - a plugin that wasn't installed at the last start takes the jobs it can do, unless a job already belongs to another plugin that is still installed. On the first start with a new... (the full comment is in the file) |
| `pvp` | `(section)` | PvP: the PvP toggle, the combat tag and new-player protection. These feed the in-pvp, combat-tagged and newbie-protected statuses (statuses.yml) used by the default vault and trade rules, and the pvp action. |
| `pvp.provider` | `included` | Which plugin handles PvP: included \| pvpmanager \| none included:   the built-in PvP, set up under 'included' below. It owns the /pvp command. pvpmanager: PvPManager does all of it, and the 'included' settings are ignored. none:       no PvP handling. Players can always fight, as in vanilla, and... (the full comment is in the file) |
| `pvp.included` | `(section)` | ---- Built-in PvP (provider: included) ---- |
| `pvp.included.toggle` | `(section)` |  |
| `pvp.included.toggle.default` | `"off"` | PvP for players who never used /pvp: on \| off If it is off, players opt in with /pvp before they can fight or be hit. That also keeps the default vault and trade rules, which block players with PvP on, out of their way. |
| `pvp.included.toggle.allow-toggle` | `true` | Players can switch their own PvP with /pvp. They also need headcount.pvp, which everyone has by default. |
| `pvp.included.toggle.cooldown` | `10s` | Time between two switches. Bypass: headcount.bypass.pvp.cooldown |
| `pvp.included.toggle.reset-on-join` | `false` | Put everyone back to the default each time they join. |
| `pvp.included.toggle.force-on-worlds` | `[]` | Worlds where PvP is always on for everyone, by exact world name. force-off-worlds keeps it off. |
| `pvp.included.toggle.force-off-worlds` | `[]` |  |
| `pvp.included.newbie-protection` | `(section)` |  |
| `pvp.included.newbie-protection.duration` | `10m` | How long new players are protected after their first join, in real time. 0 turns it off. Protected players can't attack or be attacked. |
| `pvp.included.newbie-protection.can-end-early` | `true` | A protected player can end it early with /pvp on. |
| `pvp.included.respawn-protection` | `(section)` |  |
| `pvp.included.respawn-protection.duration` | `5s` | How long nobody can hurt a player after they respawn. Attacking someone ends it. 0 turns it off. |
| `pvp.included.combat-tag` | `(section)` |  |
| `pvp.included.combat-tag.enabled` | `true` | Turns on the combat tag. A hit between two players puts them in combat (the combat-tagged status) for 'duration', and each new hit starts it over. A player's arrows, tridents, thrown potions, TNT and tamed pets count as their hits. |
| `pvp.included.combat-tag.duration` | `15s` |  |
| `pvp.included.combat-tag.tag-attacker` | `true` | Tag the player who hits. |
| `pvp.included.combat-tag.tag-victim` | `true` | Tag the player who is hit. |
| `pvp.included.combat-tag.untag-on-death` | `true` | End the tag when the player dies. |
| `pvp.included.combat-tag.block-pvp-toggle` | `true       # can't turn PvP off` | While in combat: |
| `pvp.included.combat-tag.block-flight` | `true           # flying stops and can't start again (never affects ...` |  |
| `pvp.included.combat-tag.block-elytra` | `false          # can't start gliding` |  |
| `pvp.included.combat-tag.block-ender-pearls` | `false    # can't throw ender pearls` |  |
| `pvp.included.combat-tag.block-teleport` | `true         # teleports by commands and plugins (/spawn, /home, /t...` |  |
| `pvp.included.combat-tag.blocked-commands` | `[spawn, home, homes, warp, warps, tpa, tpahere, tpaccept, tpyes, ba...` | Commands that can't be used in combat, written without the slash. Namespaced forms like essentials:home are blocked too. Bypass for these and for teleports: headcount.bypass.combat-commands |
| `pvp.included.combat-log` | `(section)` |  |
| `pvp.included.combat-log.enabled` | `true` | Punish players who leave the server in combat. Kicks and bans don't count. Bypass: headcount.bypass.combat-log |
| `pvp.included.combat-log.punishment` | `kill` | What happens to the player who left: kill \| none kill makes them die as they leave, so their items and XP drop where they stood. none only runs 'actions' and 'broadcast' below. |
| `pvp.included.combat-log.credit-last-attacker` | `true` | Give the kill to the player who last hit them, along with the kill head drop (config.yml kills). |
| `pvp.included.combat-log.actions` | `[]` | Extra actions to run on the player who left. Any action from actions.yml works. Actions that need the player online wait until they next join. For example: actions: - type: points amount: -5 - message: "&lt;red&gt;You logged out during a fight and lost 5 points." |
| `pvp.included.combat-log.broadcast` | `true` | Tell everyone else. The message is pvp.combat-log-broadcast in the lang file. |
| `pvp.included.messages` | `(section)` | How each kind of PvP message reaches the player. The texts are in lang/en_US.yml under pvp. channel: chat \| actionbar \| title \| none sound:   a sound key like "minecraft:block.note_block.bass", or "" for none. volume and pitch: 0-2. A player gets the same message at most once a second. |
| `pvp.included.messages.toggled` | `(section)` | /pvp switched your PvP (pvp.toggled-on, toggled-off and newbie-ended). |
| `pvp.included.messages.toggled.channel` | `chat` |  |
| `pvp.included.messages.toggled.sound` | `"minecraft:ui.button.click"` |  |
| `pvp.included.messages.tag-start` | `(section)` | You hit someone or were hit, and are now in combat (pvp.tag-start). |
| `pvp.included.messages.tag-start.channel` | `actionbar` |  |
| `pvp.included.messages.tag-start.sound` | `"minecraft:item.armor.equip_iron"` |  |
| `pvp.included.messages.tag-end` | `(section)` | Your combat tag ended (pvp.tag-end). |
| `pvp.included.messages.tag-end.channel` | `actionbar` |  |
| `pvp.included.messages.tag-end.sound` | `""` |  |
| `pvp.included.messages.blocked-hit` | `(section)` | Your hit was stopped because you or the other player has PvP off or is protected (pvp.blocked-hit.*). |
| `pvp.included.messages.blocked-hit.channel` | `actionbar` |  |
| `pvp.included.messages.blocked-hit.sound` | `""` |  |
| `pvp.included.messages.blocked-action` | `(section)` | Something you tried was refused, like a blocked command or teleport, or /pvp during combat or a cooldown. |
| `pvp.included.messages.blocked-action.channel` | `chat` |  |
| `pvp.included.messages.blocked-action.sound` | `"minecraft:block.note_block.bass"` |  |
| `pvp.included.messages.blocked-action.pitch` | `0.7` |  |
| `pvp.included.messages.countdown` | `actionbar` | The live countdown while in combat (pvp.countdown): actionbar \| bossbar \| none |
| `pvp.included.messages.countdown-bossbar-color` | `red` | Boss bar color when countdown is bossbar: pink \| blue \| red \| green \| yellow \| purple \| white |
| `pvp.pet-protection` | `(section)` | ---- Pet protection (provider: included or pvpmanager) ---- Tamed animals (wolves, cats, parrots, horses, donkeys, llamas...) share their owner's PvP: a player can only hurt someone else's pet if the two players could fight. That means the attacker's and the owner's PvP are on and neither is... (the full comment is in the file) |
| `pvp.pet-protection.enabled` | `false` |  |
| `pvp.pet-protection.offline-owners` | `protect` | Pets whose owner is offline: protect \| allow An offline player can't be fought, so protect keeps their pets safe until they're back. |
| `pvp.pvpmanager` | `(section)` | ---- PvPManager (provider: pvpmanager) ---- |
| `pvp.pvpmanager.let-players-toggle` | `true` | Let every player use PvPManager's /pvp. PvPManager keeps its pvpmanager.command.pvp permission from players by default, and they start with PvP on. Headcount's default rules block moving heads and trading while PvP is on, so players need a way to turn it off. This sets the permission's default... (the full comment is in the file) |
| `placeholders` | `(section)` | Placeholders: %...% in Headcount's text, the score formula and statuses. |
| `placeholders.provider` | `included` | Which placeholders work: included \| placeholderapi \| none included:       only Headcount's %headcount_...% placeholders (listed in docs/placeholders.md). placeholderapi: those plus every PlaceholderAPI placeholder, and %headcount_...% for other plugins. none:           no %...% placeholders at... (the full comment is in the file) |
| `placeholders.placeholderapi` | `(section)` |  |
| `placeholders.placeholderapi.register-expansion` | `true` | Register the %headcount_...% expansion, so other plugins like scoreboards, tab and chat can show Headcount's data. |
| `placeholders.placeholderapi.expansion-format` | `legacy` | How formatted values like %headcount_title% are passed to other plugins: legacy \| minimessage \| plain |
| `economy` | `(section)` | Money, for the money action. Points are Headcount's own currency and need nothing. |
| `economy.provider` | `none` | Which economy the money action uses: vault \| none Headcount has no money of its own, so the default content gives none out. With Vault and an economy plugin like EssentialsX or CMI installed, set vault to use the money action. auto-switch sets it when Vault is added and an economy plugin has... (the full comment is in the file) |
| `permissions` | `(section)` | Permissions: the permission, group, primary-group, track, prefix and suffix actions, and title prefixes (titles.yml permission-prefix). |
| `permissions.provider` | `included` | Which plugin handles permissions: included \| luckperms \| vault \| none included:  Headcount sets permission nodes itself, saves them and re-applies them on every join. It can't do groups, tracks, prefixes or suffixes, so those actions are skipped with a warning. luckperms: everything, with... (the full comment is in the file) |
| `permissions.luckperms` | `(section)` |  |
| `permissions.luckperms.use-expiry` | `true` | Give timed changes LuckPerms' own expiry too, so they still end if Headcount is removed. |
| `permissions.luckperms.server-context` | `""` | "server" context for the nodes Headcount adds. Leave empty for global. |
| `moderation` | `(section)` | Moderation: the ban, mute, kick, warn and jail actions. |
| `moderation.provider` | `included` | Which plugin handles moderation: included \| litebans \| libertybans \| advancedban \| essentials \| commands \| none included:    vanilla bans (the server's ban list), kicks, warnings sent as a message, and the built-in mute and jail set up below. commands:    any moderation plugin, driven by the... (the full comment is in the file) |
| `moderation.kinds` | `{}` | A different provider for single kinds, with the same choices. For example, bans by LibertyBans but jail by EssentialsX: kinds: jail: essentials The kinds are ban, mute, kick, warn and jail. Kinds left out, or set to default, use 'provider'. auto-switch sets a kind back to default when its plugin... (the full comment is in the file) |
| `moderation.operator-name` | `Headcount` | Name shown as the punisher, where the plugin supports it. |
| `moderation.silent` | `false` | Hide punishment broadcasts, if the plugin allows it. |
| `moderation.included-jail` | `(section)` | The built-in jail, used by 'included' and for jail with any plugin that has none. |
| `moderation.included-jail.location` | `""` | Jail location as world,x,y,z[,yaw,pitch], like "world,0,64,0". Jail actions don't work until it is set. |
| `moderation.included-jail.radius` | `8` | Jailed players who get further away than this many blocks are pulled back. |
| `moderation.included-jail.release-to` | `previous` | Where release sends them: previous \| spawn |
| `moderation.included-jail.allowed-commands` | `[msg, tell, r, hc, headcount]` | Commands jailed players can still use. Everything else is blocked. |
| `moderation.included-mute` | `(section)` | The built-in mute, used by 'included'. It blocks chat and these commands. |
| `moderation.included-mute.blocked-commands` | `[msg, tell, w, r, me, say]` |  |
| `moderation.litebans-commands` | `(section)` | Commands used for LiteBans, since its API can't create punishments. Variables: &lt;player&gt; &lt;uuid&gt; &lt;reason&gt; &lt;duration&gt; |
| `moderation.litebans-commands.ban` | `"litebans:ban &lt;player&gt; &lt;reason&gt;"` |  |
| `moderation.litebans-commands.tempban` | `"litebans:tempban &lt;player&gt; &lt;duration&gt; &lt;reason&gt;"` |  |
| `moderation.litebans-commands.unban` | `"litebans:unban &lt;player&gt;"` |  |
| `moderation.litebans-commands.mute` | `"litebans:mute &lt;player&gt; &lt;reason&gt;"` |  |
| `moderation.litebans-commands.tempmute` | `"litebans:tempmute &lt;player&gt; &lt;duration&gt; &lt;reason&gt;"` |  |
| `moderation.litebans-commands.unmute` | `"litebans:unmute &lt;player&gt;"` |  |
| `moderation.litebans-commands.kick` | `"litebans:kick &lt;player&gt; &lt;reason&gt;"` |  |
| `moderation.litebans-commands.warn` | `"litebans:warn &lt;player&gt; &lt;reason&gt;"` |  |
| `moderation.commands` | `(section)` | Command templates for provider 'commands'. Leave a kind empty if your plugin can't do it, and 'included' handles it instead. Variables: &lt;player&gt; &lt;uuid&gt; &lt;reason&gt; &lt;duration&gt; &lt;jail&gt; |
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
| `discord.provider` | `none` | Which plugin sends Discord messages: discordsrv \| none discordsrv needs DiscordSRV installed and connected to a bot. auto-switch sets it when DiscordSRV is added. |
| `discord.default-channel` | `global` | DiscordSRV game channel for actions that don't name one. |
| `items` | `(section)` | Custom items: item definitions written as "custom: itemsadder:ns:id", "custom: oraxen:id", "custom: nexo:id". |
| `items.providers` | `[]` | Custom item plugins to use, out of itemsadder, oraxen and nexo. Ones that aren't installed are skipped, and [] turns custom items off. List the ones you install, like [itemsadder]. auto-switch adds a plugin here when it's added to the server and takes it out when it's removed. |
| `bedrock` | `(section)` | Bedrock players, through Geyser. They get /trade accept instead of the chat link, and every menu and the sidebar work for them. This also feeds the bedrock status. |
| `bedrock.provider` | `none` | How Bedrock players are recognized: floodgate \| geyser \| none floodgate also knows players who have no Java account. Set it, or geyser, when Geyser is installed. auto-switch picks floodgate if both are added at once. |
| `player-state` | `(section)` | Player states: afk, vanished, god, jailed and muted, for the player-state status source (the afk and vanished statuses in statuses.yml). |
| `player-state.provider` | `included` | Which plugin reports player states: included \| essentials \| none included:   AFK after 'afk.after' without activity, and vanished from the "vanished" flag most vanish plugins set. God mode comes from the player's invulnerable flag. Jailed and muted come from the built-in jail and mute, or from... (the full comment is in the file) |
| `player-state.included` | `(section)` |  |
| `player-state.included.afk` | `(section)` |  |
| `player-state.included.afk.after` | `5m` | How long a player goes without moving, looking around, chatting, using commands or clicking before they are AFK. 0 means never. |
| `player-state.included.afk.looking-counts` | `true` | Count turning the camera as activity. |
| `player-state.included.afk.announce` | `self` | Who is told when someone goes AFK or comes back: self \| everyone \| none The messages are player-state.* in the lang file. |

## shop.yml

```text
The /hc shop menu. Players earn points from a positive score at the end of a cycle and spend them during the
next one. Each tier has its own pages and unlocks when the player's score from the previous cycle reaches its
threshold. Players can still browse a locked tier. Its entries show their locked look, and a banner shows the
score needed.
```

Tiers and entries are explained in [shop.md](shop.md).

| Option | Default | Description |
|---|---|---|
| `settings` | `(section)` |  |
| `settings.confirm-at-price` | `10` | Price at which a purchase asks for confirmation first. An entry can override this with confirm: true or false. 10 is about what a good cycle earns, so a purchase that takes that much asks first. |
| `settings.new-player-score` | `0` | Last-cycle score for players who don't have one, because they are new or were inactive. |
| `settings.stock-reset` | `cycle` | When global stock refills: cycle \| never |
| `gui` | `(section)` |  |
| `gui.title` | `"&lt;dark_gray&gt;Shop » &lt;tier&gt; &lt;dark_gray&gt;(&lt;points&...` | Menu title. Variables: &lt;tier&gt; &lt;tier_id&gt; &lt;tier_number&gt; &lt;tiers&gt; &lt;page&gt; &lt;pages&gt; &lt;points&gt; &lt;last_score&gt; &lt;threshold&gt; &lt;needed&gt; |
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
| `confirm-gui` | `(section)` | Confirmation menu for expensive purchases. The entry being bought is shown in the 'display' slot. |
| `confirm-gui.title` | `"&lt;dark_gray&gt;Buy &lt;name&gt;? &lt;price&gt; points"` |  |
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
| `default-states` | `(section)` | Default look of entries in each state. An entry's own 'display' and 'display-states' override these. The states are affordable, too-expensive, sold-out and locked, plus three optional ones. If left out, limit-reached looks like sold-out, and cooldown and unavailable look like too-expensive.... (the full comment is in the file) |
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
| `tiers` | `(section)` | The shop's tiers, in order. A tier with 'threshold: none' is always unlocked. Tier settings: name, threshold (a last-cycle score, or none), pages (the minimum; entries can add more), enabled and entries. Entry settings: name                 the entry's name in messages (default: its display name... (the full comment is in the file) |

## titles.yml

```text
Titles players can own and equip with /hc titles. They get them from the shop, from outcomes or from an admin.
Chat and tab plugins show the equipped title with %headcount_title%, and Headcount can also set it as a
LuckPerms or Vault prefix or suffix. The force-title action puts a title on a player for a set time.
```

Defining titles is explained in [titles.md](titles.md).

| Option | Default | Description |
|---|---|---|
| `settings` | `(section)` |  |
| `settings.none-text` | `""` | What %headcount_title% shows when nothing is equipped. |
| `settings.format` | `"&lt;title&gt; "` | How %headcount_title% shows the equipped title. &lt;title&gt; is the title's display text. |
| `settings.auto-equip-first` | `true` | Equip a player's first title automatically. |
| `settings.permission-prefix` | `(section)` | Copies the shown title into your permissions plugin as a prefix or suffix, with colors converted to legacy &-codes. |
| `settings.permission-prefix.enabled` | `false` | Turns the prefix or suffix on. A change applied with /hca reload updates online players right away, and others when they next join while it is on. A prefix left over from before it was turned off has to be removed with your permissions plugin. |
| `settings.permission-prefix.type` | `prefix` | Whether the title is set as a prefix or a suffix. |
| `settings.permission-prefix.priority` | `100` | Priority of the prefix or suffix. Headcount clears anything else at this priority, so pick one nothing else uses. |
| `settings.permission-prefix.format` | `"&lt;title&gt; "` |  |
| `titles` | `(section)` | The titles players can own. Each key (champion, collector...) is the title's ID for /hca title, the grant-title action and shop requirements. display       what players see, in MiniMessage description   lore lines in the menu icon          the menu item, as an item definition (see config.yml)... (the full comment is in the file) |
| `gui` | `(section)` |  |
| `gui.title` | `"&lt;dark_gray&gt;Your Titles"` | Menu title. Variables: &lt;page&gt; |
| `gui.rows` | `6` |  |
| `gui.show-unowned` | `true` | Show titles the player doesn't own, as locked. |
| `gui.title-slots` | `[10-16, 19-25, 28-34]` |  |
| `gui.title-item` | `(section)` | How each title looks in the menu, combined with the title's own 'icon'. States: owned, equipped, forced, locked. Variables: &lt;title&gt; &lt;id&gt; &lt;description&gt; |
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
| `gui.title-item.locked.lore` | `["", "&lt;dark_gray&gt;You don't own this title"]` |  |
| `gui.filler` | `{ material: GRAY_STAINED_GLASS_PANE, name: " " }` |  |
| `gui.filler-slots` | `[0-9, 17, 18, 26, 27, 35, 36-53]` |  |
| `gui.buttons` | `(section)` |  |
| `gui.buttons.previous-page` | `{ slot: 45, material: ARROW, name: "&lt;yellow&gt;Previous page", l...` |  |
| `gui.buttons.next-page` | `{ slot: 53, material: ARROW, name: "&lt;yellow&gt;Next page", lore:...` |  |
| `gui.buttons.unequip` | `{ slot: 49, material: BARRIER, name: "&lt;red&gt;Unequip title" }` |  |

## statuses.yml

```text
Named player states and how each one is detected. A status does nothing by itself. The vault and trade
rules, conditions and deny lists in other files refer to statuses by name. Each status is also a
placeholder, %headcount_status_<name>%, that other plugins can read.

Each status has a 'source' plus that source's settings. It can also have:
  fallback:     another status to use when the source can't answer, usually because its plugin is missing
  unavailable:  true or false, used when there is no fallback (default false)
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
  pvp-damage       dealt or took player damage recently. direction: dealt | took | either, seconds: 15
  recent-damage    took any damage recently. seconds: 10
  mob-damage       took damage from a hostile mob recently. seconds: 10
  nearby-player    radius: 16, status: (optional, the other player must have it), min: 1, visible-only: true
  nearby-hostile   radius: 8, include-targeting: true, targeting-radius: 32
  targeted-by-mob  radius: 32
  health           below: 6, or range: "<6" / "..5", percent: false
  food             below: 6, or range
  on-fire, in-lava, in-water, gliding, flying, swimming, sneaking, sprinting, sleeping, dead, invisible, op, bedrock
  falling          distance: 4 (blocks fallen so far)
  riding           vehicles: [] (entity types like horse, boat, minecart, or empty for any vehicle)
  gamemode         modes: [creative, spectator]
  world            worlds: [world_nether]
  joined-recently  seconds: 30
  potion           effect: speed, min-amplifier: 0
  y-level          range: "<0" (or below: / above:)
  biome            biomes: [desert, minecraft:badlands]
  holding          materials: [DIAMOND_SWORD], hand: main | off | any
  world-time       from: 13000, to: 23000 (ticks; wraps past midnight)
  player-state     the player-state provider (integrations.yml: included or EssentialsX). "essentials" is
                   this source's old name. check: afk | vanished | god | jailed | muted
  muted            muted by the moderation plugin or by Headcount's own mute
  in-trade         in an open Headcount trade
  headcount        value: points | unique | total | rank | percentile | streak | score | ..., range: ">0"
  all-of / any-of  statuses: [a, b, "!c"]
  not              status: a
  always / never
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
What happens to each active player at the end of a cycle, based on their score. Inactive players get no
score, so no outcomes either.

Each rule has:
  score:       a range, like "..-1" for -1 or lower, "-4..-1", "10.." for 10 or more, "5", ">=3", "<0"
  conditions:  optional extra requirements such as streak, rank or statuses, as in actions.yml
  actions:     an action list, or the name of one from actions.yml
  display:     the rule's name in reports and history
  punishment:  true if a shield should block it. Defaults to true when the whole score range is below 0.
  group:       rules compete within their group (default: punishments or rewards)
  priority:    higher wins in most-extreme mode (default: how far the range is from 0)
  enabled:     false switches the rule off

Variables in outcome actions: <player> <score> <unique> <total> <own> <rank> <percentile> <streak>
<points_earned> <points> <rolled_over> <cycle> <tier> <playtime> <kills> <deaths> <trades> <outcome>
```

Writing rules is explained in [outcomes.md](outcomes.md).

| Option | Default | Description |
|---|---|---|
| `mode` | `most-extreme` | Which matching rules a player gets: all \| most-extreme all gives every rule the score matches. most-extreme gives only the most extreme match in each group, so a player who qualifies for Wanted gets that and not the two lighter penalties as well. |
| `rules` | `(section)` | (see the linked page) |

## actions.yml

```text
Rewards, punishments, shop purchases and hooks all run action lists. An action list is a YAML list with one
action per entry. A list defined here under 'lists' can be used anywhere by its name.

These three entries do the same thing:
  - type: message
    text: "<green>Hello"
  - message: "<green>Hello"          the type as the key, filling its main setting
  - "message: <green>Hello"          a one-line string
A bare name runs the list with that name, or a type that needs no settings if no list is called that:
  - my-list-name
  - clear-effects

Options every action takes:
  duration:   instant (default), 10m / 2h / 3d, cycle, cycles:N, death, permanent or manual.
              cycle lasts until the cycle ends. Given at a cycle end, like an outcome, it lasts through the
              next cycle. death lasts until the player dies, manual until an admin removes it with /hca effects.
  time:       real (default) or online - whether a timed duration counts real time or only time online
  stacking:   refresh (default), extend, stack or ignore - what happens if the same action is already active
  key:        a name that marks actions as the same for stacking (default: the type and its main setting)
  chance:     0-100 (default 100)
  delay:      wait before running (lost on restart)
  repeat:     run again every X while active, like lightning every 5m for 1h. Needs a duration.
  conditions: statuses: [..]  any-statuses: [..]  score: "..-1"  streak: ">=2"  rank: "1..3"  points: ">=10"
              unique: ">=5"  active: true  permission: node  expression: "unique > 5 && rank <= 3"
  start-message / end-message: MiniMessage shown when it starts and ends, with <duration> and <remaining>

Timed actions are undone when they end, even across restarts. Offline players get them applied or undone
when they next join. Built-in types undo themselves; console and player-command take 'revert-commands'.

Action text can use whatever variables the caller provides (<player>, <score>, <cycle>, <points> and so on),
plus any %headcount_...% or PlaceholderAPI placeholder.

Action types, with their settings in parentheses. The full reference is docs/actions.md.
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
  equip         (slot: head | chest | legs | feet | offhand, item, lock: true) - a dunce cap, for example
  give-heads    (owner: self | <name> | <uuid>, amount, cycle: current | next, to: inventory | vault)
  loot          (table: minecraft:chests/simple_dungeon, luck)
  experience    (amount: +/- points, levels: false)
Currency
  points        (amount: +/-, when: now | next-cycle, revert: true)
  money         (amount: +/-, revert: true) through Vault (integrations.yml economy)
Permissions and ranks. The included permissions provider (integrations.yml) handles permission nodes only.
LuckPerms or Vault handle all of these, and LuckPerms expires timed changes itself.
  permission    (permission, mode: add | remove, value: true)
  group         (group, mode: add | remove)
  primary-group (group)
  track         (track, direction: promote | demote) - LuckPerms only
  prefix/suffix (text, priority)
Titles
  grant-title, revoke-title (title, equip: false)
  strip-title   (which: equipped | all)
  force-title   (title) - equipped and locked for the duration
Moderation. The included provider uses vanilla bans and Headcount's own mutes and jails. integrations.yml can
hand any of them to a moderation plugin instead.
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
head-protection, extra-heads, bounty, shield, deny and force-title only work while active, so give them a
duration such as cycle, 1d or permanent. /hca selftest warns about one without.
  head-protection (charges, 0 for unlimited) - your head does not drop when you are killed
  extra-heads   (amount, charges) - extra heads drop when you are killed
  bounty        (points, actions: [...] for the killer, claims: 1, announce: true)
  shield        (charges: 1) - blocks the next punishment outcome
  flag          (flag, mode: set | clear) - usable as a status (source: flag)
  score-bonus   (amount) - added to this cycle's final score
Flow
  run           (list: <name>, or actions: [...])
  random        (choices: [{weight: 3, actions: [...]}, {weight: 1, list: other-list}])
```

Every action type is in [actions.md](actions.md).

| Option | Default | Description |
|---|---|---|
| `lists` | `(section)` | (see the linked page) |
