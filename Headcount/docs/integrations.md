# Integrations

Headcount works fully with no other plugins installed. `integrations.yml` chooses who handles each job that another plugin could do. Every job has a `provider`:

- `included` is Headcount's own version. It is the default wherever one exists.
- A plugin name hands the job to that plugin, which must be installed.
- `none` turns the job off completely, Headcount's own version included.

If the chosen plugin isn't installed, Headcount logs a warning and falls back to `included`, or to `none` when there is no included version. `/hca integrations` shows what is in use and why, and the console prints the same summary at start-up. Changes apply after `/hca reload`, except switching `/pvp` between Headcount and PvPManager, which needs a restart.

Old configs still load. `auto` picks the installed plugin and logs a warning asking you to choose one. Old key names such as `placeholderapi.enabled`, `essentials.enabled` and `vanilla-jail` are still read.

| Job | `provider` choices | Included version | With `none` |
|---|---|---|---|
| PvP: the toggle, combat tag, new-player protection, the `pvp` action, and the `in-pvp`, `combat-tagged` and `newbie-protected` statuses | `included`, `pvpmanager`, `none` | Headcount's own PvP ([below](#included-pvp)) | Vanilla PvP. Anyone can fight, the PvP statuses read false, and `combat-tagged` falls back to "hit or was hit by a player in the last 15s". |
| Placeholders: `%...%` in Headcount's text, the formula and statuses | `included`, `placeholderapi`, `none` | Headcount's own `%headcount_...%` only | Placeholders aren't replaced and show as written. |
| Money (`money` action) | `vault`, `none` | none | Money actions are skipped with a warning. |
| Permissions: the `permission`, `group`, `primary-group`, `track`, `prefix` and `suffix` actions, and title prefixes | `included`, `luckperms`, `vault`, `none` | Permission nodes, saved and applied again on every join. Groups, tracks, prefixes and suffixes need LuckPerms or Vault and are skipped with a warning. | Every permission action is skipped. |
| Moderation: `ban`, `mute`, `kick`, `warn`, `jail` | `included`, `litebans`, `libertybans`, `advancedban`, `essentials`, `commands`, `none` | Vanilla bans, kicks and warnings, plus Headcount's own mute and jail | Every moderation action is skipped. |
| Player states: AFK, vanished, god, jailed and muted, for the `player-state` status source | `included`, `essentials`, `none` | AFK after `afk.after` of idling, vanished from vanish plugins' metadata, god from the invulnerable flag, and jailed and muted from Headcount's own jail and mute | All five read false. |
| Discord (`discord` action) | `discordsrv`, `none` | none | Skipped with a warning. |
| Custom items (`custom: itemsadder:ns:id`, `oraxen:id`, `nexo:id`) | `providers: [itemsadder, oraxen, nexo]`, or `[]` for none | none | The item falls back to its `material`. |
| Bedrock players | `floodgate`, `geyser`, `none` | none | Everything still works. Bedrock players just don't get the Bedrock version of the trade request text. |

A plugin that isn't on this list can still feed Headcount through a `placeholder` status. See [statuses.md](statuses.md).

## Included PvP

With `pvp.provider: included`, Headcount owns `/pvp` and runs everything under `pvp.included`.

Players switch their own PvP with `/pvp [on|off|status]` (permission `headcount.pvp`, which everyone has). A player who has never used it starts at `toggle.default`, which is off, so players have to opt in before they can fight or be hit. The toggle also has `allow-toggle`, a `cooldown` (10s, bypassed with `headcount.bypass.pvp.cooldown`), `reset-on-join`, and `force-on-worlds` and `force-off-worlds`. Admins can set anyone's PvP with `/pvp <player> [on|off]` (`headcount.pvp.others`).

New players are protected for `newbie-protection.duration` (10m) after their first join. They can end it early with `/pvp on` if `can-end-early` allows it. After respawning, a player can't be hurt by anyone for `respawn-protection.duration` (5s).

A hit is blocked when either player has PvP off or is protected. Indirect damage counts as a hit too: projectiles, harmful splash potions and lingering clouds, setting someone on fire, a player's TNT, and their tamed pets. Players with `headcount.pvp.override` can attack anyone.

A hit between two players puts them in combat for `combat-tag.duration` (15s), and every new hit restarts the timer. The tag can apply to the attacker, the victim or both. Each of these can be blocked separately while in combat:

- turning PvP off
- flying, and starting an elytra glide
- ender pearls
- teleports by commands and plugins
- the commands in `blocked-commands`, namespaced forms included (bypass with `headcount.bypass.combat-commands`)

A player who logs out while in combat is killed, so their items drop where they stood. Being kicked or banned doesn't count. The last attacker gets the kill and the kill head. `combat-log.actions` can run extra actions on the player, such as points or a message, and `broadcast` tells everyone else. `headcount.bypass.combat-log` skips all of this.

Each message type (`toggled`, `tag-start`, `tag-end`, `blocked-hit`, `blocked-action`) has its own `channel` (`chat`, `actionbar`, `title` or `none`) and `sound`. While in combat, a countdown shows in the action bar or a boss bar. The texts are under `pvp` in `lang/en_US.yml`.

The `in-pvp`, `combat-tagged` and `newbie-protected` statuses and the `pvp` action work the same with either provider.

If PvPManager is installed, it enforces its own rules even when Headcount's PvP is chosen. It starts everyone with PvP on, and its newbie protection can block hits. Remove it, or choose `pvpmanager`. Either change needs a restart.

### PvPManager

With `pvp.provider: pvpmanager`, PvPManager does everything and the `included` settings are ignored. Players start with PvP on, and PvPManager only lets them run `/pvp` with `pvpmanager.command.pvp`, which is off by default. Headcount's default rules block moving heads and trading while PvP is on, so players need a way to turn it off. That's why `pvp.pvpmanager.let-players-toggle: true`, the default, makes that permission default to true. A player or group your permissions plugin explicitly denies stays denied. Set it to `false` to hand out the permission yourself, for example with `lp group default permission set pvpmanager.command.pvp true`.

## Moderation kinds

`moderation.kinds` gives single kinds their own provider. For example, `provider: libertybans` with `kinds: { jail: essentials }` sends jails to EssentialsX and everything else to LibertyBans. When a plugin can't do a kind, that kind goes to `included`. LibertyBans and LiteBans have no jail, for example. LiteBans is driven through its commands (`litebans-commands`), because its API can't create punishments. The `commands` provider can drive any plugin through the templates under `moderation.commands`.

Headcount's own jail needs `moderation.included-jail.location`. Jailed players are held within its `radius` and can only use `allowed-commands`. Headcount's own mute blocks chat and the commands in `included-mute.blocked-commands`.

## Bedrock (Geyser and Floodgate)

Every Headcount menu is a normal chest menu, which Geyser shows on Bedrock, and clicks from Bedrock go through the same rules as clicks from Java. The sidebar uses team prefixes on unique entries, which Bedrock renders correctly.

Bedrock players get trade requests that tell them to type `/trade accept` or `/trade deny`, since they can't click a chat link. Their heads are economy heads like anyone's. The economy goes by UUID, so it doesn't matter if a Bedrock skin doesn't load.

## CoreProtect

CoreProtect logs the world side: placed heads, chests and drops. It can't see virtual inventories, so vaults and trades only show up in Headcount's own audit log.
