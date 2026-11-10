# Heads, the vault, kills and trading

## Economy heads

An **economy head** is a player head whose PersistentDataContainer carries Headcount's owner UUID, cycle ID and a unique head ID. Only those count. Vanilla heads, `/give` heads and other plugins' heads never count, even with the same skin or owner name. The unique ID keeps every economy head in its own stack.

An economy head works like any player head: you can wear it, place it, put it in a chest, ender chest, shulker box, bundle or item frame, and trade it. A head from an older cycle keeps all its data and still works as a normal head, but it is worthless: it can't go in a vault and doesn't count. Its name and lore change to show that it expired (when a player joins, opens a container, or picks it up). The format is in `config.yml heads.item`.

### Placed heads

When an economy head is placed, the full item is stored in the block and in Headcount's index. Whenever the block becomes an item again, the plain drop is replaced with the original economy head. That covers breaking it, explosions, pistons, physics updates, entities such as withers, fluids, fire, and any other removal that drops it, through a catch-all on item spawns. If a removal drops nothing at all (an explosion's yield, a plugin removing drops), Headcount drops the head anyway (`heads.placed.always-drop`). Breaking in creative drops nothing, as in vanilla (`creative-break`).

Head blocks carrying economy data that Headcount never saw placed, such as world-editor copies or copied block data from creative pick-block, are treated as plain heads and logged as duplicates (`untracked-blocks`).

### Duplicates

Creative mode can clone any item. Headcount protects the economy in layers:

- The default `creative-or-spectator` status blocks the vault and trading.
- A head ID already in some vault can't be put in another (`vault.duplicate-insert`), and stacks of identical heads are refused.
- Inventories are scanned after creative inventory actions, on join, and on inventory close. Any head ID seen twice, or seen in an inventory while also in a vault, is written to the audit log as `DUPLICATE` and reported to admins with `headcount.admin.notify`. `duplicates.remove-copies` can also delete the extra copies.

## The vault

`/hc` opens a virtual inventory (`vault.size`, default 27; more than 54 becomes pages of 45, and slots past the size on the last page are locked) that holds only current-cycle economy heads. Heads move freely between it and the inventory, unless the player has a status in `vault.block-movement.statuses`. Then they can look but not move heads, or can't open it at all with `block-opening: true`. Every status has its own message (`gates.vault-move.<status>` in the language file) and bypass permission.

At each cycle start, every player who has ever joined (online or not) gets `vault.starting-heads` of their own head, capped at the vault size, plus any next-cycle heads from actions. A player who joins mid-cycle gets their vault set up as it would have been at that cycle's start. Size and starting heads changes only apply from the next cycle.

Every click and drag type is handled. Inside the vault, pick-up, place, swap, number-key and offhand swap, shift-click either way, and single-slot drags are allowed. Drop keys, middle-click cloning, double-click collecting and bundle interactions are blocked. Changes are saved as they happen, and the player's inventory is saved to disk within about a second, so a crash can't roll back only one side. Admins can open anyone's vault (`/hca vault`), online or offline, and it updates live for everyone viewing it.

At the end of a cycle, the unique heads in each vault are tallied (duplicates don't count extra; your own head counts), then every head in every vault is deleted. Heads outside vaults just expire.

## Kills

Killing a player drops a newly made head of the victim (current cycle) at the victim's location, once per killer per victim per cycle. It is never taken from the victim's vault. The killer is whoever vanilla credits, so projectiles and knocking someone into lava count; killing yourself gives nothing. The drop is separate from death drops, so it happens with or without keepInventory. The victim's own inventory follows keepInventory as usual, and the vault never drops. `head-protection`, `extra-heads` and `bounty` actions change what a kill does.

## Trading

`/trade <player>` sends a request: Java players get a clickable link, Bedrock players are told to type `/trade accept`. The target accepts with `/trade accept` or denies with `/trade deny`, and the sender can withdraw it with `/trade cancel`. A request expires after `request.timeout`.

Each player can have at most one trade or pending request, incoming or outgoing. A request fails if:
- either player is busy or on cooldown;
- the target is the sender, offline, or vanished;
- the players are out of range or in different worlds (configurable);
- either player has a blocking status.

Accepting opens a double chest. Your offer is on the left and theirs on the right (display copies only); the middle column has confirm, their status, a countdown and cancel. Both must confirm, and any change clears both confirmations. Then a countdown runs with both sides locked, and the trade executes. `trade.yml` sets:
- which kinds are allowed (heads for heads, items for heads, items for items, gifts);
- the maximum items and heads per trade;
- whether players may only trade their own head;
- the range;
- the cooldown.

A trade is cancelled, with everything returned to its owner, when:
- either player closes the screen, disconnects, is kicked or banned, dies, changes world, teleports, changes gamemode, or leaves range;
- either player gets a cancel status or a deny-trading action;
- the cycle ends, or the server stops or reloads.

Offers are saved as they change, and the owner's inventory is saved to disk within about a second of each change, so after a crash an offer comes back exactly once, on the owner's next join. Items that don't fit in an inventory are dropped at the player's feet with a warning, and items owed to a dead player are given back after respawning.

## Audit log

`plugins/Headcount/audit/` holds one line per economy event, with head IDs so any head can be traced:
- heads made (cycle start, kill, admin, action), deleted at cycle end, placed, broken or destroyed;
- vault moves and admin vault edits;
- trades with their full contents;
- cycle results, scores and outcomes;
- points changes and shop purchases;
- actions applied and reverted;
- admin actions, and duplicates.

Files rotate daily and by size, old files are gzipped, and files older than `keep-days` are deleted. Each type can be switched off under `audit.types`. Search with `/hca audit <player|head-id|text> [type:<type>]`.
