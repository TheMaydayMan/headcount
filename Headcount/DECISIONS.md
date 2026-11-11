# Decisions

Why Headcount behaves the way it does in places where the choice wasn't obvious. Most of these can be changed in the config.

## Dependencies

- The SQLite driver is loaded with Paper's `libraries:` loader instead of being shaded into the jar. It ships native code, which breaks when it is relocated.
- Headcount compiles against the classic Vault API. The original Vault is unmaintained, but VaultUnlocked registers itself as "Vault" and implements the same API, so either works.
- Paid plugins (LiteBans, ItemsAdder, Oraxen, Nexo) are supported through their public APIs. LiteBans' API can't create punishments, so Headcount drives LiteBans through its own commands (the templates in integrations.yml) and uses the API only to check mutes.

## Cycles

- The defaults are `start-date: auto`, `end-time: 00:00` and `time-zone: UTC`. The first cycle starts on first boot, lined up with today's end time, and UTC behaves the same on any host.
- Before a future start date no cycle runs, and the vault, shop and trading say "no cycle is running yet".
- After downtime, the stored cycle ends first. The next cycle then starts at the scheduled boundary of the period that contains the current time, with the next ID. Periods that passed entirely while the server was off get no ID, so cycle IDs always go up by one.
- `/hca cycle end` starts the next cycle at once and ends it `length-days` later at the configured time. The schedule lines up from there.
- Being online during a rollover counts as joining the new cycle. `first-join-cycle` and `became-active` run for those players right after the `cycle-start` hooks.
- A warning missed while the server was offline still fires if it is at most `hooks.missed-warning-grace` (5 minutes) late. Any later and it no longer means anything, so it is skipped. Changing the end time marks warnings that are already past as done.
- Inactive players get no score, points or outcomes, but their unspent points still roll over by the configured percent. Rollover belongs to points, not to activity.
- Next-cycle points (`when: next-cycle`, `points_next`) are added to the balance at the next cycle end, so they can be spent in the cycle after it. Outcome actions run after the new balance is saved, so outcome point changes apply to the balance players start the next cycle with.
- A score of exactly 0 breaks a streak (`zero-score: break`), and an inactive cycle pauses it (`inactive: pause`).
- Ranks use competition ranking: tied players share a rank, and the next rank skips ahead. Percentile comes from rank, as (active - rank) / (active - 1). Everyone tied for first gets 100, and so does a player who is alone.

## Heads and the vault

- Economy heads have a max stack size of 1. Their unique IDs already keep them apart, and this also stops cloned copies from stacking.
- Expired heads are redrawn only when Headcount next sees them: on join, on pickup, when a container is opened, and for everyone online at cycle start.
- A placed head broken in creative drops nothing, as in vanilla. If any other removal drops nothing, as can happen with explosion yield, the head drops anyway (`always-drop: true`). Losing an economy head is the worse outcome, and it can't drop twice. A head block with economy data that Headcount never saw placed is treated as a plain head and logged as a duplicate (`untracked-blocks: strip`). For removals Headcount doesn't recognize, it only converts a dropped head with the same skin owner, so an unrelated vanilla head dropped on the spot can't become an economy head.
- A vault of up to 54 slots fits on one screen, with unused slots in the last row locked. Larger vaults are paged, 45 slots to a page, and the slots past the size on the last page are locked.
- When a `next-cycle` action (a shop purchase, the streak bonus) adds starting heads beyond the vault's size, the extra heads arrive as items so they aren't lost. A configured `starting-heads` above `vault.size` is still capped at the size.
- The drop key does nothing on a vault slot by default (`allow-drop-key: false`). Players take the head out first.
- Duplicate heads are flagged, not deleted (`remove-copies: false`), so an admin can look into it before anything is lost. A head ID that is already in a vault can never go into another (`duplicate-insert: reject`).
- Admins can edit a vault whatever their own statuses are. Their edits are audited as `VAULT_ADMIN`.
- Vault changes are written to the database as they happen. Whenever items move between a player's inventory and somewhere Headcount keeps track of (the vault, a trade offer, an action such as give-item or take-item, a delivery), the inventory is saved to disk within about a second, and right away after a trade. Otherwise a crash could roll back one side of the move and duplicate or lose the item. The timer starts at the first unsaved change, so constant clicking can't put the save off. That second is the only window left.
- The default `in-pvp` status follows the PvP provider's toggle. PvPManager turns PvP on for everyone by default, so with PvPManager as the provider, players can look in their vault but can't move heads until they run `/pvp` to turn PvP off. Remove `in-pvp` from `vault.block-movement.statuses` if that isn't wanted.
- Players can use PvPManager's `/pvp` by default, although PvPManager's own default doesn't allow it (`pvpmanager.command.pvp` is `default: false`). Left alone, a fresh install would leave players with PvP on and no way to turn it off, which locks their vault and trades. With PvPManager as the PvP provider, Headcount raises that permission's default to true (`pvp.let-players-toggle: true` in integrations.yml) and puts it back on reload and shutdown. Only the default changes, so an explicit deny in LuckPerms still wins. Set it to false to hand the permission out yourself.

## Kills

- When head protection stops a drop, the killer hasn't used up their one drop of that victim for the cycle. Bounties pay on any credited kill, whether or not a head drops.
- Kill heads scatter where the victim died, like any drop. `killer-pickup-only` (default 0) can hold them for the killer for a while.

## Trading

- `max-items` counts item stacks and `max-heads` counts economy heads. Expired heads count as items.
- Gifts, where one side offers nothing, are allowed by default as their own trade type.
- Clicking confirm during the countdown stops it and clears both confirmations. The cancel button cancels the whole trade.
- Any teleport cancels an open trade by default, chorus fruit and ender pearls included (`teleport-min-distance: 0`). So does a gamemode change.
- When a player dies mid-trade, their offer goes into their death drops, or stays with them under keepInventory. The other player gets their own offer back. Anything owed to the dead player is delivered after they respawn.
- The trade cooldown starts after a completed trade, not after a cancel (`after-cancel: false`).
- A vanished player is reported as offline when someone tries to trade with them, so the request doesn't give them away.

## Actions

- Repeats are timed from the action's start, so they don't drift later with each run. A 5-second action that repeats every 2 seconds runs 3 times. Repeats missed while the server was down are skipped, not run in a burst.
- `delay:` is not kept across restarts. Delays are meant to be short, and durations are what persist.
- `player-command` never makes the player op for the command, since that would be a security hole. Use `console` for commands that need permissions.
- A kick for an offline player is skipped, not saved for their next join.
- For moderation actions, `duration: cycle` passes the time left in the cycle to the plugin. Punishments with `death`, `manual` or `cycles:N` are lifted by Headcount when the action ends.
- Headcount's own mute blocks chat and the commands in `moderation.vanilla-mute.blocked-commands`. Its own jail needs `moderation.vanilla-jail.location` and keeps players within its radius.
- Vault can't set negated permissions, so through Vault Headcount removes the node instead and logs a warning. LuckPerms supports them fully.
- Setting a primary group through LuckPerms also adds the group, since LuckPerms ignores a primary group the player doesn't inherit.
- Points can't go below zero by default (`allow-negative: false`). Taking more than a player has takes what they have.
- A random teleport looks for a safe surface spot inside the world border, up to 25 times. Water, lava, magma and cactus don't count as safe.
- Outcomes and cycle-end hooks run while one cycle is ending, so an action they give with `duration: cycle` (or `cycles:N`) counts from the cycle that is starting. Otherwise a punishment meant to last the whole cycle, like the default dunce cap, would end the moment it was given.
- A bare name in an action list (`- celebrate`) runs the named list with that name. If there is none and it is an action type that needs no settings (`- clear-effects`), it runs that type. `/hca selftest` reports names that are neither.
- In global hooks, messages and other actions that show something go to everyone online. Actions that need a player are skipped with a warning.
- Timed potions are infinite effects that Headcount re-applies every second while they last. On revert it removes the effect only if it is still Headcount's.

## Menus and text

- While the shop, titles, history or a confirmation menu is open, every click and drag is cancelled, in the player's own inventory too, so no display item can be taken or swapped. The vault and trade screens allow only the moves their rules allow.
- Menu display items are tagged. If one ever turns up outside a menu, it is deleted on sight and logged.
- Text that comes from players, like names, is inserted as plain text, so it can't inject formatting. Literal `<placeholders>` in help text are escaped.
- The sidebar gives each player who turns it on their own scoreboard. That hides teams from the main scoreboard (such as nametag plugins) for that player while it's on. Servers using another scoreboard plugin should set `sidebar.enabled: false` and use Headcount's placeholders there.
- Language keys can't be words YAML reads as booleans (`on`, `off`, `yes`, `no`). That is why the sidebar toggle messages are `toggled-on` and `toggled-off`.
- The shop opens on the highest tier the player has unlocked, since that is usually where they want to buy. `/hc shop <tier>` opens another one.
- Deny messages ("You can't trade right now") are sent at most once every 1.5 seconds per category. Breaking or placing blocks doesn't flood chat, but trying something else that is denied is explained right away.
- Title prefixes (`permission-prefix`) are rewritten for online players on `/hca reload`, so turning the option on or off takes effect at once. Offline players are updated on their next join while it is on.
- Bypass permissions are registered from the current config on start and reload, under `headcount.bypass.<gate>.*` and `headcount.bypass.*`, so they also work without a permissions plugin.

## Storage and audit

- Headcount uses one SQLite connection on one writer thread, and every change is queued in order. The end of a cycle waits for the queue, then reads and writes in transactions.
- Audit lines are timestamped in the cycle time zone, and files are named by that zone's date. Spaces inside values are written as `_`, so every line can be read as `key=value` pairs. A file rolls over each day and when it reaches `max-file-size-mb` (at least 64 KB), and is gzipped when it does. Files older than `keep-days` (90) are deleted on start, on reload and when a new day begins.
- A self-check runs on every start and reload and warns only about real problems, like an unknown material, overlapping slots, an action that doesn't parse or does nothing without a duration, or a list or status name that doesn't exist. `/hca selftest` shows the full report.
- Statuses that refer back to themselves are found when statuses.yml loads, not the first time one is checked. The statuses in the loop are disabled, with a warning that names them.

## API

- `ScoreCalculatedEvent` also fires for previews (`/hc score`, `%headcount_score%`, the sidebar, `/hca cycle simulate`) with `isPreview()` true, so an addon that changes scores is reflected everywhere a score is shown. Previews can fire every second per player, so listeners should be cheap and skip side effects when it is a preview.
- `runActionList` fills in `<player>` and `<uuid>` like `/hca run`, so the same named lists work from the API.

## 1.1

- Resets don't change the cycle number. Each run of a cycle has a hidden run ID stamped on its heads, and `/hca reset cycle` gives the cycle a new one. That retires every older head without inventing a "cycle 4" that never ended. Players see only the cycle number. `/hca cycle info` and `/hca inspect` show the run.
- What a reset does to old heads (`delete-heads` or `keep-heads`, with the default in the config) is chosen for each reset and stored with it, so changing the config later never changes an earlier reset. Deleted heads are removed wherever Headcount next sees them, which for offline players and closed containers can be much later. Scanning every chunk and player file for them would be slow and unsafe.
- `/hca reset cycle` keeps points, titles, streaks, history and effects, and doesn't refund shop spending: it restarts the race for heads, not the economy. Shop bonuses that were used at the cycle's real start are applied again so nobody loses a paid "+N starting heads".
- `/hca reset hard` is awkward on purpose. Each confirmation needs a new random code and times out, it has its own permission that `headcount.admin` doesn't include, and it backs up the database and audit log before deleting anything.
- Integrations have no `auto`. With auto-detection, what Headcount did depended on which jars happened to be installed. Each job is now `included`, a named plugin, or `none`, and a missing plugin falls back with a warning. `none` turns the feature off completely, Headcount's own version included.
- Headcount has its own version of a job only where it can do it well alone: PvP, permission nodes, moderation and player states. Money, Discord, custom items and Bedrock can't work without the other plugin.
- Included PvP starts everyone with PvP off. Headcount's default rules lock the vault and trading while PvP is on, so this keeps a new server working, and fighting is opt-in.
- Logging out in combat kills the player rather than banning them or taking points: their items drop, and the last attacker gets the kill and its head. That is what would have happened in the fight. More actions can be added.
- The language file holds everything Headcount itself says; the other config files hold what the owner designs (menus, items, messages inside action lists), next to the thing they describe. Console logs, self-test details and permission descriptions stay English in the code.
- Headcount Studio is a local app, not a web panel inside the plugin. It runs on the owner's computer (Python standard library and PyYAML, no build step), edits the files in place keeping comments and layout, reads the database read-only, and changes live data only through `/hca` commands over RCON. Nothing is exposed to the network beyond 127.0.0.1.
