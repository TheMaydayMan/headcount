# Integrations

Headcount works fully with no other plugins installed. `integrations.yml` chooses who handles each job that another plugin could do. Every job has a `provider`:

- `included`: Headcount's own version, built in. It is the default wherever it exists.
- a plugin name: hand the job to that plugin. It must be installed.
- `none`: the job is off completely, Headcount's own version included.

If the chosen plugin isn't installed, Headcount logs a warning and uses `included`, or `none` where there is no included version. `/hca integrations` shows what is in use and why, and the console prints the same summary on start-up. Changes apply after `/hca reload`. The exception is switching `/pvp` between Headcount and PvPManager, which needs a restart.

Old configs still load. `auto` picks the installed plugin, with a warning asking you to choose one. Old key names such as `placeholderapi.enabled`, `essentials.enabled` and `vanilla-jail` are still read.

| Job | `provider` choices | Included version | With `none` |
|---|---|---|---|
| **PvP:** the toggle, combat tag, new-player protection, the `pvp` action, the `in-pvp`, `combat-tagged` and `newbie-protected` statuses | `included`, `pvpmanager`, `none` | Headcount's own PvP ([below](#included-pvp)) | Vanilla PvP: anyone can fight, the PvP statuses read false, and `combat-tagged` falls back to "hit or was hit by a player in the last 15s". |
| **Placeholders:** `%...%` in Headcount's text, the formula and statuses | `included`, `placeholderapi`, `none` | Headcount's own `%headcount_...%` only | No `%...%` at all; they show as written. |
| **Money** (`money` action) | `vault`, `none` | none | Money actions are skipped with a warning. |
| **Permissions:** the `permission`, `group`, `primary-group`, `track`, `prefix` and `suffix` actions, title prefixes | `included`, `luckperms`, `vault`, `none` | Permission nodes, saved and re-applied on every join. Groups, tracks, prefixes and suffixes need LuckPerms or Vault, so they are skipped with a warning. | Every permission action is skipped. |
| **Moderation:** `ban`, `mute`, `kick`, `warn`, `jail` | `included`, `litebans`, `libertybans`, `advancedban`, `essentials`, `commands`, `none` | Vanilla bans, kicks and warnings, plus Headcount's own mute and jail | Every moderation action is skipped. |
| **Player states:** AFK, vanished, god, jailed, muted (the `player-state` status source) | `included`, `essentials`, `none` | AFK after `afk.after` idle; vanished from vanish plugins' metadata; god from the invulnerable flag; jailed and muted from Headcount's own jail and mute | All five read false. |
| **Discord** (`discord` action) | `discordsrv`, `none` | none | Skipped with a warning. |
| **Custom items** (`custom: itemsadder:ns:id`, `oraxen:id`, `nexo:id`) | `providers: [itemsadder, oraxen, nexo]`, `[]` for none | none | The item falls back to its `material`. |
| **Bedrock players** | `floodgate`, `geyser`, `none` | none | Everything still works; only the Bedrock-specific trade request text is lost. |

Any other plugin's state can still become a Headcount status through a `placeholder` status (see [statuses.md](statuses.md)).

## Included PvP

With `pvp.provider: included`, Headcount owns `/pvp` and handles everything under `pvp.included`:

- **Toggle.** `/pvp [on|off|status]` (permission `headcount.pvp`, everyone). Players who never used it get `toggle.default`, which is **off**: they must opt in before they can fight or be hit. Also `allow-toggle`, a `cooldown` (10s; bypass `headcount.bypass.pvp.cooldown`), `reset-on-join`, and `force-on-worlds` / `force-off-worlds`. Admins use `/pvp <player> [on|off]` (`headcount.pvp.others`).
- **Protection.** New players are protected for `newbie-protection.duration` (10m) after their first join, and can end it early with `/pvp on` (`can-end-early`). Nobody can hurt a player for `respawn-protection.duration` (5s) after they respawn.
- **Blocked hits.** A hit involving someone with PvP off or protected is stopped. That includes arrows and other projectiles, harmful splash potions, damage from lingering clouds, setting someone on fire, a player's TNT and their tamed pets. `headcount.pvp.override` attacks anyone.
- **Combat tag.** A hit between players puts them in combat (`combat-tag.duration`, 15s; each new hit restarts it) for the attacker and/or victim. While in combat, each of these can be switched on or off:
  - turning PvP off
  - flight, and starting an elytra glide
  - ender pearls
  - teleports by commands and plugins
  - the commands in `blocked-commands`, namespaced forms included (bypass `headcount.bypass.combat-commands`)
- **Combat log.** Leaving while in combat (not being kicked or banned) kills the player, so their items drop where they stood. The last attacker gets the kill and the kill head. `combat-log.actions` runs any extra actions on them (points, a message...), and `broadcast` tells everyone else. Bypass: `headcount.bypass.combat-log`.
- **Communication.** Each message type (`toggled`, `tag-start`, `tag-end`, `blocked-hit`, `blocked-action`) has its own `channel` (`chat`, `actionbar`, `title` or `none`) and `sound`. A live countdown shows while in combat, in the action bar or a boss bar. The texts are in `lang/en_US.yml` under `pvp`.

The `in-pvp`, `combat-tagged` and `newbie-protected` statuses and the `pvp` action work the same with either provider.

**Switching to or from PvPManager needs a restart.** While PvPManager is installed it enforces its own rules even when Headcount's are chosen: it starts everyone with PvP on, and its newbie protection can block hits. Remove it, or choose `pvpmanager`.

### PvPManager

With `pvp.provider: pvpmanager`, PvPManager does everything and the `included` settings are ignored. PvPManager only lets players run `/pvp` with `pvpmanager.command.pvp`, which is off by default, and players start with PvP on. Headcount's default rules block moving heads and trading while PvP is on, so `pvp.pvpmanager.let-players-toggle: true` (the default) makes that permission default to true. A player or group explicitly denied it in your permissions plugin stays denied. Set it to `false` to hand the permission out yourself, for example `lp group default permission set pvpmanager.command.pvp true`.

## Moderation kinds

`moderation.kinds` gives single kinds their own provider: for example, `provider: libertybans` with `kinds: { jail: essentials }`. A plugin that can't do a kind (LibertyBans and LiteBans have no jail) leaves it to `included`. LiteBans is driven by its commands (`litebans-commands`), because its API can't create punishments. `commands` drives any plugin through the templates under `moderation.commands`.

Headcount's own jail needs `moderation.included-jail.location`. Jailed players are held within its `radius` and can only use `allowed-commands`. Headcount's own mute blocks chat and `included-mute.blocked-commands`.

## Bedrock (Geyser and Floodgate)

- Every menu (vault, trade, shop, titles, history, confirmations) is a normal chest menu, which Geyser shows on Bedrock. Every click Geyser sends is handled by the same rules as on Java.
- Bedrock players get trade requests with `/trade accept` and `/trade deny` instead of a clickable link.
- The sidebar uses team prefixes on unique entries, which Bedrock renders correctly.
- A Bedrock player's heads are economy heads like anyone's. The economy uses the player's UUID, so it doesn't matter if the skin doesn't load.

## CoreProtect

CoreProtect logs the world side: placed heads, chests and drops. It can't see virtual inventories, so Headcount's own audit log covers vaults and trades. Use both together.
