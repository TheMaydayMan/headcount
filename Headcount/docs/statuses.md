# Statuses

`statuses.yml` defines **named player states** and how to detect each one. A status does nothing by itself; other files use statuses by name:

- `config.yml` `vault.block-movement.statuses`: who can't move heads in or out of the vault
- `trade.yml` `statuses.block-request`, `block-accept` and `cancel-open`
- `conditions:` on any action, outcome rule or shop entry (`statuses:` and `any-statuses:`)
- `kills.killer-blocked-statuses` and `trade.yml request.vanished-status`

In any list of statuses, `!name` means "not name".

Every status is also a placeholder, `%headcount_status_<name>%`. It reads `true` or `false`, or whatever `settings.placeholder-true` and `settings.placeholder-false` are set to, so other plugins (scoreboards, menus, region plugins) can use it.

Check a player's statuses live with `/hca status <player>`. `/hca selftest` reports any status that refers to a status that doesn't exist.

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

A source "can't answer" when its provider is `none`, when the plugin it needs is missing, or when a placeholder doesn't resolve. In that case Headcount uses `fallback` if set, otherwise `unavailable`.

Results are cached for one server tick, so checking the same status many times in a tick is cheap. A status that refers back to itself through combinations or fallbacks is found when the file loads: it is disabled and named in a warning and in `/hca selftest`. A loop that only appears while running (through `nearby-player`) is still stopped and treated as false.

## Default statuses

| Status | Meaning |
|---|---|
| `in-pvp` | The player's PvP is on, from the PvP provider in `integrations.yml` (Headcount's own `/pvp`, or PvPManager). With provider `none` it is never active. |
| `combat-tagged` | In combat (the PvP provider's combat tag). With provider `none` it falls back to `recent-pvp-damage`. |
| `recent-pvp-damage` | Dealt or took damage from a player in the last 15 seconds. |
| `newbie-protected` | The PvP provider's new-player protection. |
| `creative-or-spectator` | In creative or spectator mode. |
| `sleeping-or-dead` | Sleeping or dead. |
| `vanished` | Vanished through any plugin that sets the `vanished` metadata, or through EssentialsX when the player-state provider is `essentials`. |
| `afk` | AFK, from the player-state provider: Headcount's own idle timer (`integrations.yml player-state.included.afk`, 5 minutes by default), or EssentialsX's `/afk`. |
| `bedrock-player` | Joined through Geyser. |
| `low-health`, `on-fire`, `in-lava`, `falling-far`, `in-danger` | Examples built from the built-in checks. |

## Sources

### Plugin hooks

| Source | Settings | True when |
|---|---|---|
| `pvp` | `check`: `pvp-enabled`, `pvp-disabled`, `combat-tagged`, `newbie`, `respawn-protected`, `override` | The PvP provider chosen in `integrations.yml` (Headcount's included PvP, or PvPManager) reports that state. With provider `none` it can't answer. |
| `player-state` | `check`: `afk`, `vanished`, `god`, `jailed`, `muted` | The player-state provider reports that state. `included`: AFK from Headcount's idle timer, vanished from the `vanished` metadata, god from the invulnerable flag, jailed and muted from Headcount's own jail and mute (or the moderation plugin). `essentials`: EssentialsX. `essentials` is still accepted as the source's old name. |
| `muted` | none | The moderation plugin reports a mute, or Headcount's own mute is active. |
| `bedrock` | none | The player joined through Geyser (Floodgate or Geyser is asked). |

### Any plugin, through placeholders

| Source | Settings |
|---|---|
| `placeholder` | `placeholder: "%plugin_value%"`, `operator`, `value`, `ignore-case: true` |

The operators are `equals`, `not-equals`, `greater-than`, `less-than`, `greater-or-equal`, `less-or-equal`, `contains`, `not-contains`, `starts-with`, `ends-with` and `regex`. The short forms `=`, `!=`, `>`, `<`, `>=` and `<=` also work. When both sides are numbers they are compared as numbers.

This is how any PvP, combat-log, region or minigame plugin with PlaceholderAPI placeholders becomes a Headcount status. `%headcount_...%` placeholders work here even without PlaceholderAPI.

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
| `headcount` | `value` (any formula variable, or `score`), `range` | That live value is in range, e.g. `value: points`, `range: ">=10"`. |
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
| `gliding`, `flying`, `swimming`, `sneaking`, `sprinting`, `sleeping`, `dead`, `invisible`, `op` | none | Self-explanatory. |
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
| `always`, `never` | none | Always true or always false (handy for switching a gate off without editing every list). |

## Adding your own sources

Other plugins can add sources through the API. See [developers.md](developers.md#adding-a-status-source).
