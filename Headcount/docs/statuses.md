# Statuses

`statuses.yml` defines named player states and how to detect each one. A status does nothing by itself. Other settings refer to statuses by name:

- `config.yml` `vault.block-movement.statuses`: who can't move heads in or out of the vault
- `trade.yml` `statuses.block-request`, `block-accept` and `cancel-open`
- `conditions:` on any action, outcome rule or shop entry (`statuses:` and `any-statuses:`)
- `kills.killer-blocked-statuses` and `trade.yml request.vanished-status`

In any list of statuses, `!name` means "not name".

Every status is also a placeholder, `%headcount_status_<name>%`, so other plugins such as scoreboards, menus and region plugins can use it. It reads `true` or `false`, or the text in `settings.placeholder-true` and `settings.placeholder-false`.

`/hca status <player>` shows a player's statuses as they are right now. `/hca selftest` reports any reference to a status that doesn't exist.

## Defining a status

```yaml
statuses:
  combat-tagged:
    source: pvp            # how to detect it (below)
    check: combat-tagged   # settings for that source
    fallback: recent-pvp-damage   # optional: use this status when the source can't answer
    unavailable: false     # optional: the value when there is no fallback (default false)
    description: "..."     # optional note
```

A source can't answer when its provider is `none`, when the plugin it needs is missing, or when a placeholder doesn't resolve. Headcount then uses the `fallback` status if there is one, and the `unavailable` value if not.

Results are cached for one server tick, so checking the same status many times in a tick is cheap. A status that refers back to itself through combinations or fallbacks is caught when the file loads. It is disabled and named in a warning and in `/hca selftest`. A loop that only shows up at runtime, through `nearby-player`, is still stopped and treated as false.

## Default statuses

| Status | Meaning |
|---|---|
| `in-pvp` | The player's PvP is on, according to the PvP provider in `integrations.yml` (Headcount's own `/pvp`, or PvPManager). Never active with provider `none`. |
| `combat-tagged` | In combat, by the PvP provider's combat tag. Falls back to `recent-pvp-damage` with provider `none`. |
| `recent-pvp-damage` | Dealt or took damage from a player in the last 15 seconds. |
| `newbie-protected` | The PvP provider's new-player protection. |
| `creative-or-spectator` | In creative or spectator mode. |
| `sleeping-or-dead` | Sleeping or dead. |
| `vanished` | Vanished through any plugin that sets the `vanished` metadata, or through EssentialsX when the player-state provider is `essentials`. |
| `afk` | AFK, according to the player-state provider. That is either Headcount's own idle timer (`player-state.included.afk` in `integrations.yml`, 5 minutes by default) or EssentialsX's `/afk`. |
| `bedrock-player` | Joined through Geyser. |
| `low-health`, `on-fire`, `in-lava`, `falling-far`, `in-danger` | Examples made from the built-in checks. |

## Sources

### Plugin hooks

| Source | Settings | True when |
|---|---|---|
| `pvp` | `check`: `pvp-enabled`, `pvp-disabled`, `combat-tagged`, `newbie`, `respawn-protected`, `override` | The PvP provider chosen in `integrations.yml` reports that state. With provider `none` it can't answer. |
| `player-state` | `check`: `afk`, `vanished`, `god`, `jailed`, `muted` | The player-state provider reports that state. With `included`, AFK comes from Headcount's idle timer, vanished from the `vanished` metadata, god from the invulnerable flag, and jailed and muted from Headcount's own jail and mute or the moderation plugin. With `essentials`, EssentialsX answers. The source's old name, `essentials`, still works. |
| `muted` | none | The moderation plugin reports a mute, or Headcount's own mute is active. |
| `bedrock` | none | The player joined through Geyser, as reported by Floodgate or Geyser. |

### Any plugin, through placeholders

| Source | Settings |
|---|---|
| `placeholder` | `placeholder: "%plugin_value%"`, `operator`, `value`, `ignore-case: true` |

`operator` is one of `equals`, `not-equals`, `greater-than`, `less-than`, `greater-or-equal`, `less-or-equal`, `contains`, `not-contains`, `starts-with`, `ends-with` or `regex`. The short forms `=`, `!=`, `>`, `<`, `>=` and `<=` also work. When both sides are numbers, they are compared as numbers.

Any plugin with PlaceholderAPI placeholders can become a Headcount status this way. This example uses CombatLogX. `%headcount_...%` placeholders work here even without PlaceholderAPI.

```yaml
  combat-logged:
    source: placeholder
    placeholder: "%combatlogx_in_combat%"
    operator: equals
    value: "Yes"
```

### Server data

| Source | Settings | True when |
|---|---|---|
| `scoreboard-tag` | `tag` | The player has that scoreboard tag (`/tag`). |
| `permission` | `permission` | The player has the permission. |
| `metadata` | `key`, optional `value` | Any metadata value under `key` is truthy, or equals `value`. Vanish plugins commonly set `vanished`. |
| `flag` | `flag` | A Headcount flag is set (by the `flag` action or `/hca flag`). |
| `headcount` | `value` (any formula variable, or `score`), `range` | That live value is in range, for example `value: points` with `range: ">=10"`. |
| `in-trade` | none | The player is in an open Headcount trade. |

### Built-in checks

| Source | Settings | True when |
|---|---|---|
| `pvp-damage` | `direction`: `dealt`, `took` or `either`; `seconds: 15` | The player dealt or took damage from a player within the time. Projectiles, TNT, potion clouds and pets count for their owner. |
| `recent-damage` | `seconds: 10` | The player took any damage within the time. |
| `mob-damage` | `seconds: 10` | The player took damage from a hostile mob within the time. |
| `nearby-player` | `radius: 16`, `status` (optional), `min: 1`, `visible-only: true` | At least `min` other players are within the radius (and have `status`, if set). |
| `nearby-hostile` | `radius: 8`, `include-targeting: true`, `targeting-radius: 32` | A hostile mob is within the radius, or is targeting the player within `targeting-radius`. A mob that is dying doesn't count. |
| `targeted-by-mob` | `radius: 32` | A mob within the radius is targeting the player. |
| `health` | `below: 6`, or `range: "<6"`; `percent: false` | Health (or percent of max health) is in range. |
| `food` | `below: 6`, or `range` | Hunger is in range. |
| `on-fire` | none | Burning. |
| `in-lava`, `in-water` | none | In lava or water. |
| `falling` | `distance: 4` | Fallen more than this many blocks so far (not flying or gliding). |
| `gliding`, `flying`, `swimming`, `sneaking`, `sprinting`, `sleeping`, `dead`, `invisible`, `op` | none | The player is in that state. |
| `riding` | `vehicles: []` | In any vehicle, or one of the listed entity types (`horse`, `boat`, `minecart`, ...). |
| `gamemode` | `modes: [creative, spectator]` | In one of the gamemodes. |
| `world` | `worlds: [world_nether]` | In one of the worlds. |
| `joined-recently` | `seconds: 30` | Joined less than this long ago. |
| `potion` | `effect: speed`, `min-amplifier: 0` | Has the effect at that level or higher. |
| `y-level` | `range: "<0"`, or `below:` / `above:` | Y position in range. |
| `biome` | `biomes: [desert, minecraft:badlands]` | Standing in one of the biomes. |
| `holding` | `materials: [DIAMOND_SWORD]`, `hand`: `main`, `off` or `any` | Holding one of the materials. |
| `world-time` | `from: 13000`, `to: 23000` | World time (ticks) is in the window; wraps past midnight. |

### Combinations

| Source | Settings | True when |
|---|---|---|
| `all-of` | `statuses: [a, b, "!c"]` | All listed statuses match. |
| `any-of` | `statuses: [a, b]` | Any listed status matches. |
| `not` | `status: a` | `a` is not active. |
| `always`, `never` | none | Always true or always false. Useful for switching a gate off without editing every list that uses it. |

## Adding your own sources

Other plugins can add sources through the API. See [developers.md](developers.md#adding-a-status-source).
