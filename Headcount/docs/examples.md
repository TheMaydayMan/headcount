# Example setups

Each example shows only the lines it changes. Everything else stays at its default.

## A weekly cycle that ends Sunday evening

```yaml
# config.yml
cycle:
  length-days: 7
  start-date: 2026-11-15      # a Sunday
  end-time: "20:00"
  time-zone: America/Denver
```

## Hardcore: harsh punishments and bans for repeat offenders

```yaml
# outcomes.yml
mode: all                      # every matching rule applies
rules:
  ban-repeat-offenders:
    enabled: true
# actions.yml: make punish-mild stronger
  punish-mild:
    - message: "<red>Score <score>. You are slowed for a full day."
    - type: potion
      effect: slowness
      amplifier: 1
      duration: 1d
```

## Points carry over, and big collections are rewarded more

```yaml
# config.yml
points:
  rollover-percent: 50
# scoring.yml
formula: "floor(pow(unique, 1.5) / 2) - ceil(active / 4)"
```

## Using another combat plugin through PlaceholderAPI

```yaml
# statuses.yml
  combat-tagged:
    source: placeholder
    placeholder: "%combatlogx_in_combat%"
    operator: equals
    value: "Yes"
    fallback: recent-pvp-damage
```

## Only count players who actually play

```yaml
# config.yml
activity:
  min-playtime: 2h
```

## Another scoreboard plugin shows Headcount instead

```yaml
# config.yml
sidebar:
  enabled: false
```

Then use `%headcount_unique%`, `%headcount_score%`, `%headcount_points%`, `%headcount_time_left%` and `%headcount_streak_text%` in that plugin.

## Titles as LuckPerms prefixes

```yaml
# titles.yml
settings:
  permission-prefix:
    enabled: true
    type: prefix
    priority: 100
```

## A shop entry that rents a rank for three days

```yaml
# shop.yml, under a tier's entries
      vip-rental:
        slot: 22
        price: 30
        limit-per-cycle: 1
        display: { material: GOLD_INGOT, name: "<gold>VIP for 3 days" }
        actions:
          - type: group
            group: vip
            duration: 3d
            start-message: "<gold>You are VIP for <duration>."
            end-message: "<gray>Your VIP rental ended."
```

## A punishment that locks PvP on

```yaml
# actions.yml
  punish-pvp:
    - type: pvp
      state: on
      lock: true
      duration: 1d
      start-message: "<red>Your PvP is forced on for a day."
```

## PvP always on in the Nether, and logging out of a fight costs points

```yaml
# integrations.yml
pvp:
  included:
    toggle:
      force-on-worlds: [world_nether]
    combat-log:
      actions:
        - type: points
          amount: -5
        - message: "<red>You logged out during a fight and lost 5 points."
```

## Everyone fights by default, with a longer combat tag

```yaml
# integrations.yml
pvp:
  included:
    toggle:
      default: "on"
    combat-tag:
      duration: 30s
      block-elytra: true
      block-ender-pearls: true
    messages:
      countdown: bossbar
```

With PvP on by default, the default vault and trade blocks for `in-pvp` apply to everyone until they turn PvP off. If you don't want that, remove `in-pvp` from `vault.block-movement.statuses` and from the trade statuses.

## Keep using PvPManager

```yaml
# integrations.yml
pvp:
  provider: pvpmanager
```

Restart the server after switching.

## Bans by LibertyBans, jail by EssentialsX

```yaml
# integrations.yml
moderation:
  provider: libertybans
  kinds:
    jail: essentials
```

## Time zone as an abbreviation or offset

```yaml
# config.yml
cycle:
  time-zone: MST        # or GMT-7, UTC+5:30, America/Denver, system
```

Abbreviations and offsets are fixed: `MST` is UTC-7 all year. A region such as `America/Denver` follows daylight saving.
