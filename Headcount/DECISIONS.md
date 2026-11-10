# Decisions

Choices made where the specification didn't say. Each picks the safest reasonable option and is configurable unless noted.

## Build and test environment

- **Toolchain.** The machine had only JDK 21. Temurin JDK 25.0.5 was installed to `~/.jdks/`, where Gradle finds it. The build asks for a Java 25 toolchain, and the foojay resolver lets Gradle download one on machines that don't have it. Gradle 9.9.0 (latest) through the wrapper.
- **SQLite driver.** Loaded with Paper's `libraries:` loader instead of shading it, because the driver ships native code that breaks when relocated.
- **Vault.** The original Vault is unmaintained, so the test server runs **VaultUnlocked 2.20.3**. It registers itself as "Vault" and implements the classic Vault API, which is what Headcount compiles against.
- **The test server in the repository** is only its setup: `TestServer/setup.sh` downloads Paper and each plugin at the tested version and checks its sha256. The jars aren't committed (Mojang's server files can't be redistributed, and the plugins have their own licenses), and neither is anything the server makes: the world, logs, plugin data, and a `server.properties` holding a generated secret. The script asks before accepting the Minecraft EULA.
- **Free plugins on the test server.** All of them are installed together: PvPManager, PlaceholderAPI, VaultUnlocked, LuckPerms, EssentialsX, LibertyBans, AdvancedBan, DiscordSRV, Geyser and Floodgate. With `moderation.provider: auto`, bans, mutes, kicks and warnings go to LibertyBans and jails to EssentialsX. EssentialsX 2.22.0 is tagged for 26.1.2 and logs "unsupported server version", but runs. Geyser's Paper build is tagged up to 26.1.2 and loads fine on 26.3. AdvancedBan's last release is from 2020; it loads and its API works.
- **Paid plugins** (LiteBans, ItemsAdder, Oraxen, Nexo) are compiled against their public APIs and not installed. LiteBans' API can't create punishments, so Headcount drives LiteBans through its own commands (templates in integrations.yml) and uses the API only to check mutes.
- **Test server settings.** `online-mode=false` as requested. `enforce-secure-profile=false` is required for chat in offline mode, `spawn-protection=0` lets non-op test players place heads at spawn, and `white-list=false` because newer Paper builds turn the whitelist on by default. Geyser's config is left at its defaults.
- **Bot tests.** Player behavior is tested by bots (`tools/bot-tests`): mineflayer clients, which speak Minecraft 26.1, join a copy of the test server through ViaVersion and ViaBackwards, and bedrock-protocol clients join through Geyser. ViaBackwards rejects that mineflayer version's movement packets, so the bots don't move; everything that needs a moving client is in TESTING.md as a manual check.

## Cycles

- **Defaults:** `start-date: auto` (start on first boot, aligned to today's `end-time`), `end-time: 00:00`, `time-zone: UTC` (predictable on any host).
- **Before a future start date** no cycle runs. The vault, shop and trading say "no cycle is running yet".
- **Catch-up after downtime.** The stored cycle ends first. The next cycle then starts at the scheduled boundary of the period containing "now", and its ID is the old ID + 1. Calendar periods that passed entirely while offline get no cycle ID, so IDs always go up by exactly one.
- **`/hca cycle end`** starts the next cycle immediately and ends it `length-days` later at the configured time. The schedule realigns from then on.
- **Online during a rollover** counts as joining the new cycle: `first-join-cycle` and `became-active` run for those players right after the `cycle-start` hooks.
- **Missed warnings.** A warning missed while the server was offline fires only if it is at most `hooks.missed-warning-grace` (5 minutes) late. Later than that is meaningless, so it is skipped. Changing the end time marks warnings that are already past.
- **Inactive players** get no score, points or outcomes, but their unspent points still roll over by the configured percent. Rollover is a property of points, not of activity.
- **Next-cycle points** (`when: next-cycle`, `points_next`) are added to the balance at the next cycle end, so they can be spent in the cycle after it. Outcome actions run after the new balance is saved, so outcome point changes apply to the balance players start the next cycle with.
- **Streaks.** A score of exactly 0 breaks a streak (`zero-score: break`), and an inactive cycle pauses it (`inactive: pause`).
- **Ranks** use competition ranking (ties share a rank, the next rank skips). **Percentile** is rank-based: (active − rank) / (active − 1), so everyone tied for first gets 100 and a player alone gets 100.

## Heads and the vault

- **Economy heads have a max stack size of 1.** Unique IDs already keep them apart, and this also stops cloned copies from stacking.
- **Expired heads** are re-rendered lazily: on join, when a container is opened, on pickup, and for everyone online at cycle start. Heads in unloaded containers update the next time they are seen.
- **Placed heads.** Breaking in creative drops nothing, as in vanilla. If a removal drops nothing at all, the head is dropped anyway (`always-drop: true`): losing economy heads to explosion yield is worse than an extra drop that isn't possible anyway. Head blocks carrying economy data that Headcount never saw placed are treated as plain heads and logged as duplicates (`untracked-blocks: strip`). The catch-all for unknown removal paths only converts a dropped head of the same skin owner, so an unrelated vanilla head dropped on the spot can't become an economy head.
- **Vaults over 54 slots** are paged, 45 per page, and slots past the size on the last page are locked. Up to 54 fits one screen, with unused slots in the last row locked.
- **Extra starting heads.** A `next-cycle` action that adds starting heads beyond the vault's size (a shop purchase, the streak bonus) delivers the extra heads as items, so they aren't lost. A configured `starting-heads` above `vault.size` is still capped at the size.
- **The drop key on a vault slot** is off by default (`allow-drop-key: false`). Take a head out first.
- **Duplicates** are flagged, not deleted (`remove-copies: false`), so an admin can investigate before anything is lost. A head ID already in a vault can never go into another (`duplicate-insert: reject`).
- **Admin vault edits** are allowed regardless of the admin's own statuses, and are audited as `VAULT_ADMIN`.
- **Crash safety.** Vault changes are written to the database as they happen. The player's own inventory is saved to disk within about a second of a vault change, an item moving into or out of a trade offer, an action applying or reverting (give-item, take-item, equip and loot move items), or a delivery, and right after every trade, so a crash can't roll back only one side of a transfer (which would duplicate or lose the item). The first unsaved change starts the timer, so constant clicking can't postpone the save. The remaining window is that second. Bot tests killed the server (kill -9) with a trade offer open and with a take-item action active: each item came back exactly once. With the offer save turned off, the same test duplicated the offer, which is the bug this fixed.
- **The default `in-pvp` status** follows the spec: PvPManager's PvP toggle. PvPManager turns PvP on for everyone by default, so out of the box players can look in their vault but can't move heads until they run `/pvp` to turn PvP off. Remove `in-pvp` from `vault.block-movement.statuses` if that isn't wanted.
- **Players can use `/pvp` by default.** PvPManager's own default doesn't allow it: `pvpmanager.command.pvp` is `default: false`, so a fresh install would leave players with PvP on and no way to turn it off, which locks their vault and trades. A bot test without operator rights found this. With PvPManager as the PvP provider, Headcount raises that permission's default to true (`pvp.let-players-toggle: true` in integrations.yml) and puts it back on reload and shutdown. Only the default changes, so an explicit deny in LuckPerms still wins. Set it to false to hand the permission out yourself.

## Kills

- **Head protection used up by a kill doesn't count as that killer's one drop for the cycle.** Bounties pay on any credited kill, independent of the once-per-victim rule.
- **Kill heads scatter naturally at the victim's location.** `killer-pickup-only` (default 0) can reserve them for the killer for a while.

## Trading

- **"Max items"** counts item stacks; **"max heads"** counts economy heads. Expired heads are items.
- **Gifts** (one side empty) are a fourth allowed type, on by default.
- **Clicking confirm during the countdown** stops it and clears confirmations. The cancel button cancels the whole trade.
- **Any teleport cancels an open trade by default**, chorus fruit and pearls included (`teleport-min-distance: 0`), as does a gamemode change.
- **When a player dies** mid-trade, their offer goes into their death drops (or stays with them under keepInventory). The other side gets theirs back. Items owed to a dead player are delivered after they respawn.
- **Cooldown** starts after a completed trade, not after a cancel (`after-cancel: false`).
- **Vanished targets** are reported as offline, so vanish is never revealed.

## Actions

- **Repeats keep their own schedule:** every `repeat` counted from the start, so they don't drift later with each run, and a 5-second action repeating every 2 seconds runs 3 times. Repeats missed while the server was down are skipped, not run in a burst.
- **`delay:` is not kept across restarts.** Delays are meant to be short; tracked durations are what persist.
- **`player-command` never elevates the player to op**, which would be a security risk. Use `console` for privileged commands.
- **Kicks for offline players are skipped**, not saved for their next join.
- **Moderation durations.** `cycle` passes the time left in the cycle to the plugin. `death`, `manual` and `cycles:N` are lifted by Headcount when the action ends.
- **Headcount's own mute** (no moderation plugin) blocks chat and the commands in `moderation.vanilla-mute.blocked-commands`. **Headcount's own jail** needs `moderation.vanilla-jail.location`, and holds players within its radius.
- **Negated permissions through Vault** are not supported by Vault itself. Headcount removes the node instead and warns; LuckPerms supports them fully.
- **Setting a primary group through LuckPerms** also adds the group, since LuckPerms ignores a primary group the player doesn't inherit.
- **Points can't go below zero by default** (`allow-negative: false`). Taking more than a player has takes what they have.
- **Random teleports** pick a safe surface spot (not water, lava, magma or cactus) inside the world border, trying up to 25 times.
- **"Until the cycle ends" at a cycle end.** Outcomes and cycle-end hooks run while one cycle is ending, so an action they give with `duration: cycle` (or `cycles:N`) counts from the cycle that is starting. Otherwise "marked for the whole cycle" would end the moment it was given. A bot test found this: the default severe punishment's glowing, bounty and dunce cap were removed right after being applied.
- **A bare name in an action list** (`- celebrate`) runs the named list with that name. If there is none and it is an action type that needs no settings (`- clear-effects`), it runs that type. `/hca selftest` reports names that are neither.
- **Global hooks.** Message-like actions go to everyone online, and actions that need a player are skipped with a warning.
- **Timed potions** use infinite-duration effects, re-applied every second while active, and removed on revert only if they are still Headcount's.

## Menus and text

- **Menus are fully locked.** While the shop, titles, history or a confirmation menu is open, every click and drag is cancelled, including in the player's own inventory, so no display item can ever be taken or swapped in. The vault and trade screens allow only their validated moves.
- **Display items are tagged.** If one ever appears outside a menu, it is deleted on sight and logged.
- **Escaping.** Values that come from players (names) are inserted as plain text, so they can't inject formatting. Literal `<placeholders>` in help text are escaped.
- **The sidebar** gives each player who turns it on their own scoreboard. That hides teams from the main scoreboard (such as nametag plugins) for that player while it's on. Servers using another scoreboard plugin should set `sidebar.enabled: false` and use Headcount's placeholders there.
- **Language keys must not be bare YAML booleans** (`on`, `off`, `yes`, `no`). The sidebar toggle messages are `toggled-on` and `toggled-off` for that reason.
- **The shop opens on the highest tier the player has unlocked**, since that is usually where they want to buy. `/hc shop <tier>` opens another one.
- **Deny messages** ("You can't trade right now") are sent at most once per category every 1.5 seconds, so breaking or placing blocks doesn't flood chat, while trying a different denied thing is explained at once.
- **Title prefixes** (`permission-prefix`) are rewritten for online players on `/hca reload`, so turning the option on or off takes effect at once. Offline players are updated on their next join while it is on.
- **Bypass permissions** are registered from the current config on start and reload, under `headcount.bypass.<gate>.*` and `headcount.bypass.*`, so they also work without a permissions plugin.

## Storage and audit

- **One SQLite connection on one writer thread.** Every change is queued in order. The end of a cycle waits for the queue, then reads and writes in transactions.
- **Audit lines** are timestamped in the cycle time zone and files are named by that zone's date. Spaces inside values are written as `_` so every line stays `key=value` parseable. Files roll over each day and at `max-file-size-mb` (at least 64 KB), gzip when they roll over, and are deleted after 90 days (`keep-days`), checked on start, reload and each new day.
- **A start-up self-check** runs on every start and reload. It warns only about real problems, such as unknown materials, overlapping slots, actions that failed to parse, actions that do nothing without a duration, list names that don't exist, unknown status names, or statuses that refer back to themselves (those are disabled). `/hca selftest` shows the full report.
- **Status loops** are found when statuses.yml loads, and the statuses in the loop are disabled with a warning naming the loop, instead of being discovered the first time one is checked.

## API

- **`ScoreCalculatedEvent` also fires for previews** (`/hc score`, `%headcount_score%`, the sidebar, `/hca cycle simulate`) with `isPreview()` true, so an addon that changes scores is reflected everywhere a score is shown. Previews can fire every second per player, so listeners should be cheap and skip side effects when it is a preview.
- **`runActionList`** fills in `<player>` and `<uuid>` like `/hca run`, so the same named lists work from the API.
