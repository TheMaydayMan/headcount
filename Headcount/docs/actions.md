# Actions

Rewards, punishments, shop purchases and hooks all use one actions system. An **action list** is a YAML list where each entry is one action. Lists defined under `lists:` in `actions.yml` can be used anywhere by name.

## Writing actions

These four entries mean the same thing:

```yaml
actions:
  - type: message
    text: "<green>Hello"
  - message: "<green>Hello"        # the type as the key fills its main setting
  - "message: <green>Hello"        # one-line form
  - greet-player                   # a plain name runs that list from actions.yml
  - clear-effects                  # or a type that needs no settings (a list with the same name wins)
```

When the type is the key, its value fills the type's main setting, shown as **main** below. Extra keys on the same entry are its other settings and common options:

```yaml
  - potion: slowness              # main setting of potion is 'effect'
    amplifier: 1
    duration: 1h
```

Anywhere an action list is expected, you can also give one list name as a string: `actions: punish-mild`.

## Common options (every action)

| Option | Values | Meaning |
|---|---|---|
| `duration` | `instant` (default), `10m` / `2h` / `3d`..., `cycle`, `cycles:N`, `death`, `permanent`, `manual` | How long the action lasts. `cycle` lasts until the current cycle ends; `cycles:N` until N cycle ends have passed (actions given at a cycle end, by outcomes or cycle-end hooks, count from the cycle that is starting, so `cycle` there means the whole next cycle); `death` until the player next dies; `manual` until an admin removes it with `/hca effects <player> remove <id>`. |
| `time` | `real` (default), `online` | Whether a timed duration counts real time or only time the player is online. |
| `stacking` | `refresh` (default), `extend`, `stack`, `ignore` | What happens if the same action is already active for the player: restart its timer, add the new time to it, run another copy alongside it, or do nothing. |
| `key` | text | Marks actions as "the same" for stacking. Default: the type plus its main setting (e.g. the potion effect), or the action's position in its list. |
| `chance` | 0-100 | Chance to run (default 100). |
| `delay` | duration | Wait before running. Not kept across restarts. |
| `repeat` | duration | Run again every interval while the action is active (needs a duration). For example, lightning every 5m for 1h, or a location broadcast every 30m. |
| `conditions` | see below | Only run when these match. |
| `start-message`, `end-message` | MiniMessage | Shown to the player when it starts and ends. `<duration>` and `<remaining>` are available. |

### Conditions

```yaml
conditions:
  statuses: [in-pvp, "!vanished"]   # all must match
  any-statuses: [afk, sleeping]     # at least one must match
  score: "..-1"                     # ranges: 5, 1..3, ..-1, 10.., >=2, <3, !=0
  streak: ">=2"
  rank: "1..3"
  points: ">=10"
  unique: ">=5"
  active: true
  permission: some.node
  expression: "unique > 5 && rank <= 3"   # any formula expression; true when not 0
```

At the end of a cycle, `score`, `rank` and `streak` are that cycle's final values. Anywhere else they are the live preview. Status conditions need the player online.

## Timed actions

An action with a duration other than `instant` is **tracked**. It is saved in the database, listed in `/hc effects`, and undone automatically when it ends, even across restarts.

- If the player is offline when it starts, it is applied on their next join (for types that need them online).
- If they are offline when it ends, it is undone on their next join.
- Built-in types know how to undo themselves. `console` and `player-command` use `revert-commands`.
- An instant action for an offline player that needs them online (a title, a sound) waits and runs on their next join.
- `permanent` applies once and is never undone. For effects that exist only while tracked (deny lists, shields, locks, Headcount's own mutes and jails), `permanent` keeps them tracked forever.
- Those tracked-only effects (`head-protection`, `extra-heads`, `bounty`, `shield`, `deny`, `force-title`, a locked `equip`) do nothing with the default `instant`: give them a duration. `/hca selftest` and the start-up check report one without.

In **global hooks**, actions run without a player. Message-like actions then go to everyone online, server-side actions (console, broadcast, discord, run, random) work normally, and actions that need a player are skipped with a warning.

## Action types

"Online" says whether the player must be online. Where it says no, the action also works on offline players, for example at the end of a cycle.

### Messages and effects

| Type | Main | Settings | Online | Undo |
|---|---|---|---|---|
| `message` | `text` | `text` (string or list), `audience`: `player` (default with a player), `all` (default in global hooks), `world`, `nearby:<radius>`, `permission:<node>` | yes | - |
| `broadcast` | `text` | `text`, `permission` (only players with it), `console: true` | no | - |
| `title` | `title` | `title`, `subtitle`, `fade-in: 500ms`, `stay: 3s`, `fade-out: 1s`, `audience` | yes | - |
| `actionbar` | `text` | `text`, `audience` | yes | - |
| `sound` | `sound` | `sound` (a key like `entity.experience_orb.pickup`), `volume: 1`, `pitch: 1`, `source: master`, `audience` | yes | - |
| `particle` | `particle` | `particle: happy_villager`, `count: 20`, `offset: 0.5` (or `offset-x/y/z`), `speed: 0.05`, `height: 1` | yes | - |
| `bossbar` | `text` | `text`, `color: red`, `overlay: progress` (or `notched_6/10/12/20`), `progress: 1.0`, `countdown-to`: `cycle-end`, a duration like `60s`, or a date-time `2026-12-01T00:00`; `seconds: 10` (how long an instant bar shows). Text variables: `<countdown>`, `<countdown_clock>`, `<seconds>`. Timed: shown while active. In a global hook: everyone online sees it, players who join while it runs see it too, and it disappears at the end of the countdown. | yes | hides it |
| `discord` | `text` | `text`, `channel` (a DiscordSRV channel; default from integrations.yml) | no | - |
| `broadcast-location` | `text` | `text` (`<player> <x> <y> <z> <world>`), `audience: all`. Add `repeat` to report it regularly. | yes | - |

### Commands

| Type | Main | Settings | Online | Undo |
|---|---|---|---|---|
| `console` | `command` | `command` or `commands` (list), `revert-commands`, `require-online: false`. `<player>`, `<uuid>` and every variable work. | no | runs `revert-commands` |
| `player-command` | `command` | `command` or `commands`, `revert-commands` (also run as the player) | yes | runs `revert-commands` |

### Items

Item definitions accept `material`, `amount`, `name`, `lore`, `enchants` (map of enchantment to level), `glint`, `item-model`, `custom-model-data`, `unbreakable`, `flags`, `pdc` (map of `namespace:key` to value), `head` (owner name, UUID or `viewer`), `max-stack-size`, and `custom: itemsadder:ns:id` / `oraxen:id` / `nexo:id` for custom item plugins. Write them flat on the action or under `item:`.

| Type | Main | Settings | Online | Undo |
|---|---|---|---|---|
| `give-item` | `material` | item definition, `amount`, `to`: `inventory` or `enderchest`, `take-back: true` | no (queued for offline players) | timed: the given items are tagged and taken back |
| `take-item` | `material` | what to match: `material`, `custom`, `name-contains`, `lore-contains`, `item-model`, `pdc` keys; `amount` (a number or `all`), `from`: `inventory`, `enderchest` or `both`, `return: true`. Never takes economy heads. | yes | timed: gives the items back |
| `equip` | `material` | `slot`: `head`, `chest`, `legs`, `feet` or `offhand`; item definition; `lock: true` (can't be removed, survives death) | yes | removes it and gives back what was in the slot |
| `give-heads` | `amount` | `owner`: `self`, a name or a UUID; `amount`; `cycle`: `current` or `next`; `to`: `inventory` or `vault`. Next-cycle heads go into the vault when the next cycle starts; heads that don't fit are delivered as items. | no | - |
| `loot` | `table` | `table: minecraft:chests/simple_dungeon`, `luck` | yes | - |
| `experience` | `amount` | `amount` (negative takes), `levels: false` | yes | - |

### Currency

| Type | Main | Settings | Online | Undo |
|---|---|---|---|---|
| `points` | `amount` | `amount` (negative takes; variables allowed), `when`: `now` or `next-cycle`, `revert: true` | no | timed: reverses the change |
| `money` | `amount` | `amount` (Vault economy), `revert: true` | no | timed: reverses the change |

### Permissions and ranks

These work through LuckPerms, or Vault as a fallback. With LuckPerms, timed changes also get LuckPerms' own expiry, so they end even if Headcount is removed.

| Type | Main | Settings | Online | Undo |
|---|---|---|---|---|
| `permission` | `permission` | `permission`, `mode`: `add` or `remove`, `value: true` | no | yes |
| `group` | `group` | `group`, `mode`: `add` or `remove` | no | yes |
| `primary-group` | `group` | `group` | no | restores the previous primary group |
| `track` | `track` | `track`, `direction`: `promote` or `demote` (LuckPerms only) | no | the opposite move |
| `prefix`, `suffix` | `text` | `text`, `priority: 100` | no | yes |

### Titles

| Type | Main | Settings | Online | Undo |
|---|---|---|---|---|
| `grant-title` | `title` | `title`, `equip: false` | no | revokes it, if the player didn't already own it |
| `revoke-title` | `title` | `title` | no | gives it back |
| `strip-title` | `which` | `which`: `equipped` or `all` | no | gives back and re-equips |
| `force-title` | `title` | `title`. It is equipped and locked for the duration. | no | unlocks and restores the previous title |

### Moderation

These go through the moderation plugin chosen in `integrations.yml`, falling back to vanilla bans, kicks and warnings, and Headcount's own mutes and jails.

| Type | Main | Settings | Online | Undo |
|---|---|---|---|---|
| `ban`, `mute`, `warn` | `reason` | `reason` | no | timed: lifts the ban or mute |
| `kick` | `reason` | `reason` (skipped for offline players) | - | - |
| `jail` | `reason` | `reason`, `jail` (jail name for EssentialsX). Headcount's own jail uses `moderation.vanilla-jail` in integrations.yml. | yes | releases |

A timed `duration` makes the punishment temporary and is passed to the plugin. `instant` or `permanent` makes it permanent. For `cycle`, the time left in the cycle is passed. For `death`, `manual` and `cycles:N`, Headcount lifts the punishment itself when the action ends.

### PvP

| Type | Main | Settings | Online | Undo |
|---|---|---|---|---|
| `pvp` | `state` | `state`: `on` or `off`, `lock: true` (the player can't toggle it while active), `restore: true` | yes | unlocks and restores the previous state |

### Buffs and debuffs

Timed effects are re-applied every second while active, so they survive death and milk.

| Type | Main | Settings | Undo |
|---|---|---|---|
| `potion` | `effect` | `effect`, `amplifier: 0`, `seconds: 30` (length when instant), `ambient: false`, `particles: true`, `icon: true` | removes it |
| `attribute` | `attribute` | `attribute` (`max_health`, `movement_speed`, `attack_damage`, `scale`, `jump_strength`, `gravity`, `block_break_speed`, `step_height`, ...), `amount`, `operation`: `add_number`, `add_scalar` or `multiply_scalar_1` | removes the modifier |
| `glowing` | - | none | stops glowing |
| `fire` | `ticks` | `ticks: 100`. Rain and water put it out, as in vanilla; a timed fire keeps relighting while active. | puts it out |
| `lightning` | `damage` | `damage: false` (effect only) | - |
| `freeze` | `ticks` | `ticks: 140` | thaws |
| `hunger` | `food` | `food` (change in hunger), `exhaustion` (added every second while timed) | - |
| `gamemode` | `mode` | `mode: adventure`, `lock: true` | restores the previous mode |
| `teleport` | `to` | `to`: `spawn`, `bed`, `random` or `world,x,y,z[,yaw,pitch]`; `world`; `random: {world, center: "x,y,z", radius: 2000, min-radius: 100}`; `return: true` | sends them back |
| `heal`, `feed` | `amount` | `amount` or `full` | - |
| `damage` | `amount` | `amount: 2` | - |
| `launch` | `power` | `power: 1.5`, `x`, `z` | - |
| `clear-effects` | - | none | - |
| `deny` | `deny` | `deny`: any of `break`, `place`, `chat`, `commands`, `items`, `fly`, `elytra`, `ender-pearl`, `worlds`, `trade`, `shop`, `vault`, `interact`, `drop`, `pickup`, `attack`; plus `commands: [..]` (empty means all commands), `allowed-commands: [..]`, `items: [MATERIAL]` (empty means all items), `worlds: [..]`, `message`. Bypass with `headcount.bypass.deny.<category>`. | lifts it |

### Headcount

`head-protection`, `extra-heads`, `bounty` and `shield` last while they are tracked, so give them a duration (`cycle`, `1d`, `permanent`...). Their charges end them early when used up.

| Type | Main | Settings | Online |
|---|---|---|---|
| `next-cycle` | `starting-heads` | `starting-heads` and/or `vault-size`: `"+5"`, `"-3"` or `"=10"`. These add up across several actions and apply at the next cycle start. Extra starting heads that don't fit in the vault are delivered as items. | no |
| `rollover` | `percent` | `percent: 50` sets this player's rollover for the next cycle end; `add: 10` adds to the current one | no |
| `head-protection` | `charges` | `charges: 0` (0 = unlimited). The player's head doesn't drop when they're killed. | no |
| `extra-heads` | `amount` | `amount: 1`, `charges: 0`. Extra heads drop when the player is killed. | no |
| `bounty` | `points` | `points`, `actions: [...]` (run for the killer), `claims: 1`, `announce: true` | no |
| `shield` | `charges` | `charges: 1`. Blocks the next punishment outcome. | no |
| `flag` | `flag` | `flag`, `mode`: `set` or `clear`. Usable as a status (`source: flag`); timed: restores the previous state. | no |
| `score-bonus` | `amount` | `amount`, added to this cycle's final score. Timed: removed again. | no |

### Flow

| Type | Main | Settings |
|---|---|---|
| `run` | `list` | `list: <name>`, or `actions: [...]` inline |
| `random` | `choices` | `choices: [{weight: 3, actions: [...]}, {weight: 1, list: other-list}]`. Picks one choice by weight. |

## Examples

```yaml
# A dunce cap for the rest of the cycle
- type: equip
  slot: head
  lock: true
  duration: cycle
  item: { material: CARVED_PUMPKIN, name: "<red>Dunce Cap" }

# Lightning every 10 minutes for an hour of playtime
- type: lightning
  duration: 1h
  time: online
  repeat: 10m

# A temporary VIP rank through LuckPerms
- type: group
  group: vip
  duration: 3d
  start-message: "<gold>VIP for <duration>!"

# Ban only for repeat offenders
- type: ban
  reason: "Three bad cycles in a row"
  duration: 1d
  conditions: { streak: "<=-3" }
```

## Adding your own action types

Other plugins can add action types through the API. See [developers.md](developers.md#adding-an-action-type).
