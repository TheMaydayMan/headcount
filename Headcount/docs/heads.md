# Heads, the vault, kills and trading

## Economy heads

An economy head is a player head that Headcount made. Its PersistentDataContainer holds the owner's UUID, the cycle number, the cycle's run ID and a unique head ID. Only these heads count. Vanilla heads, `/give` heads and other plugins' heads never do, even with the same skin or owner name. Because each head has its own ID, economy heads never stack.

Otherwise an economy head works like any player head. You can wear it, place it, put it in a chest, ender chest, shulker box, bundle or item frame, and trade it. A head from an older cycle keeps its data and still works as a normal head, but it can't go in a vault and doesn't count. Its name and lore are updated to show that it expired when a player joins, opens a container, or picks it up. The format is under `heads.item` in `config.yml`. By default the cycle number is gold on current heads and gray on expired and defunct ones.

### Cycle runs and resets

Each run of a cycle has a hidden run ID alongside the cycle number players see. `/hca reset cycle` keeps the number and starts a new run, and `/hca reset hard` starts again at cycle 1. Either way, every head made before the reset stops counting. You choose per reset what happens to them: `delete-heads` removes them wherever Headcount finds them, and `keep-heads` leaves them as defunct heads that the vault refuses. Without either option, `reset.delete-old-heads` decides.

`/hca inspect` tells current, expired and defunct heads apart and shows the run. [commands.md](commands.md#resets) has the details of both resets, including where deleted heads are cleaned up.

### Placed heads

When an economy head is placed, the full item is stored in the block and in Headcount's index. Whenever the block turns back into an item, the plain drop is swapped for the original economy head. Headcount does this by watching item spawns, so it works for any removal that drops the head, from breaking and explosions to pistons, fluids, fire and withers. When a removal drops nothing at all, for example because of an explosion's yield or a plugin clearing drops, `heads.placed.always-drop` drops the head anyway. Breaking it in creative drops nothing, as in vanilla (`creative-break`).

A head block with economy data that Headcount never saw placed, such as a world-editor copy or a creative pick-block copy, is treated as a plain head and logged as a duplicate (`untracked-blocks`).

### Duplicates

Creative mode can clone any item, so Headcount checks for copies in several ways:

- The default `creative-or-spectator` status blocks the vault and trading.
- A head ID already in some vault can't be put in another (`vault.duplicate-insert`), and stacks of identical heads are refused.
- Inventories are scanned after creative inventory actions, on join, and on inventory close. Any head ID seen twice, or seen in an inventory while also in a vault, is written to the audit log as `DUPLICATE` and reported to admins with `headcount.admin.notify`. `duplicates.remove-copies` can also delete the extra copies.

## The vault

`/hc` opens the vault, a virtual inventory that only holds current-cycle economy heads. Its size is `vault.size`, 27 by default. A vault bigger than 54 is split into pages of 45, and any slots on the last page past the size are locked. Heads move freely between the vault and the player's inventory unless the player has a status in `vault.block-movement.statuses`. Then they can look but not move heads, or with `block-opening: true` they can't open it at all. Every status has its own message (`gates.vault-move.<status>` in the language file) and its own bypass permission.

When a cycle starts, every player who has ever joined, online or not, gets `vault.starting-heads` of their own head, up to the vault size, plus any next-cycle heads from actions. A player who first joins mid-cycle gets the vault they would have had at the start. Changes to the size or the starting heads apply from the next cycle, or at a `/hca reset cycle`.

The vault title can show live numbers with `<unique>`, `<total>` and `<score>`. `<score>` is the owner's score if the cycle ended now. It is colored by `vault.gui.score-colors`, or you can set the colors in place with `<score:green:red:gray>`. The title updates while the vault is open, for everyone viewing it.

Inside the vault, players can pick up, place and swap heads, use number keys and the offhand swap, shift-click in either direction, and drag across single slots. Drop keys, middle-click cloning, double-click collecting and bundle interactions are blocked. Changes are saved as they happen, and the player's inventory is saved to disk within about a second, so a crash can't roll back only one side. Admins can open anyone's vault, online or offline, with `/hca vault`.

When a cycle ends, each vault's unique heads are counted. Extra copies of the same player's head don't add anything, and your own head counts. Then every head in every vault is deleted. Heads outside vaults just expire.

## Kills

Killing a player drops a new current-cycle head of the victim where they died. Each killer gets a given victim's head once per cycle. The head is made fresh and never taken from the victim's vault. The killer is whoever vanilla credits, so projectiles and knocking someone into lava count, and killing yourself gives nothing. The head drops whether or not keepInventory is on. The victim's own inventory follows keepInventory as usual, and the vault never drops. The `head-protection`, `extra-heads` and `bounty` actions change what a kill does.

## Trading

`/trade <player>` sends a request. Java players get a clickable link, and Bedrock players are told to type `/trade accept`. The target accepts with `/trade accept` or denies with `/trade deny`, and the sender can withdraw it with `/trade cancel`. A request expires after `request.timeout`.

Each player can be in one trade or pending request at a time, incoming or outgoing. A request fails if either player is busy, on cooldown or has a blocking status, or if the target is the sender, offline or vanished. Depending on `trade.yml`, it can also fail when the players are out of range or in different worlds.

Accepting opens a double chest. Your offer is on the left and theirs is on the right, shown as display copies. The middle column has the confirm button, the other player's status, a countdown and a cancel button. Both players must confirm, and any change to an offer clears both confirmations. A countdown then runs with both sides locked, and the trade goes through.

In `trade.yml` you set which kinds of trade are allowed (heads for heads, items for heads, items for items, and gifts), the most items and heads per trade, whether players can only trade their own head, the range and the cooldown.

A trade is cancelled and everything goes back to its owner if either player leaves the trade screen, the server, the world or the range, dies, teleports, changes gamemode, or gets a cancel status or a deny-trading action. Trades are also cancelled when the cycle ends and when the server stops or reloads.

Offers and the owner's inventory are saved the same way as vault changes, so after a crash an offer comes back exactly once, on the owner's next join. Items that don't fit in an inventory are dropped at the player's feet with a warning, and items owed to a dead player are given back after respawning.

## Audit log

`plugins/Headcount/audit/` holds one line per economy event. Lines include head IDs, so any head can be traced. It records:

- heads being made, deleted, placed, broken or destroyed
- vault moves and admin vault edits
- trades, with their full contents
- cycle results, scores and outcomes
- points changes and shop purchases
- actions applied and reverted
- admin actions
- duplicates

A new file starts each day or when one gets too big. Old files are gzipped, and files older than `keep-days` are deleted. Each type can be switched off under `audit.types`. Search with `/hca audit <player|head-id|text> [type:<type>]`, or in Headcount Studio's audit page, which can also trace one head from creation to deletion. A hard reset moves the log files into its backup folder (`reset.hard.archive-audit-log`).
