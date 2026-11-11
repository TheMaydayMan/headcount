# Example setups

Each example shows only the lines it changes. Everything else stays at its default. The first five are complete ways to run the game, and the rest are smaller changes you can add to any of them.

## Weekly cycles for an active group

For a group that plays most days. A cycle lasts a week and ends Sunday evening, when most people are around to see the results. A week leaves less time to trade, so the par stops at 3 instead of 4 and a single trade still gets most players out of the red.

To line the cycles up with Sunday, end the running cycle on a Sunday once, for example with `/hca cycle setend 2026-11-15T20:00`. Every cycle after it is a week long.

```yaml
# config.yml
cycle:
  length-days: 7
  end-time: "20:00"
# scoring.yml
formula: "unique - clamp(round(sqrt(active)), 1, 3) + ceil(clamp(6 * (unique - 1) / max(min(active, vault_size) - 1, 1), 0, 6))"
```

## Monthly seasons for a slower server

For a server where most people log on once or twice a week. A month gives everyone time to meet, so vaults start full with 27 heads and a player needs 3 hours of play to count. Scores run higher over a month, so each shop tier asks for a little more, and a reminder goes out a week before the end.

```yaml
# config.yml
cycle:
  length-days: 30
activity:
  min-playtime: 3h
vault:
  starting-heads: 27
hooks:
  warnings:
    7d:
      before: 7d
      global:
        - message: "<yellow>Cycle <cycle> ends in <white>a week</white>. Fill your vault with unique heads! <gray>/hc"
# shop.yml
tiers:
  uncommon:
    threshold: 4
  rare:
    threshold: 8
  legendary:
    threshold: 12
```

## Hardcore PvP

Everyone fights, all the time. PvP is always on and can't be turned off, so only being in combat stops trades and vault moves. Each kill is worth a point on top of the head it drops, each death costs one, and logging out of a fight costs 5 points. A kill head is only the killer's to pick up for 30 seconds. Four bad cycles in a row bring a day's ban, one step past Wanted.

Fights push scores up, so the shop tiers ask for more and head protection costs more. If you already run PvPManager, leave out the integrations.yml part, set `pvp.provider: pvpmanager` and restart.

```yaml
# integrations.yml
pvp:
  included:
    toggle:
      default: "on"
      allow-toggle: false
    newbie-protection:
      duration: 30m
    combat-tag:
      duration: 30s
      block-elytra: true
      block-ender-pearls: true
    combat-log:
      actions:
        - type: points
          amount: -5
        - message: "<red>You logged out during a fight and lost 5 points."
    messages:
      countdown: bossbar
# config.yml
vault:
  block-movement:
    statuses: [combat-tagged, creative-or-spectator, sleeping-or-dead]
kills:
  killer-pickup-only: 30s
# trade.yml
statuses:
  block-request: [combat-tagged, creative-or-spectator, sleeping-or-dead]
  block-accept: [combat-tagged, creative-or-spectator, sleeping-or-dead]
  cancel-open: [combat-tagged, creative-or-spectator, sleeping-or-dead]
# scoring.yml
formula: "unique + kills - deaths - clamp(round(sqrt(active)), 1, 4) + ceil(clamp(6 * (unique - 1) / max(min(active, vault_size) - 1, 1), 0, 6))"
# outcomes.yml
rules:
  ban-repeat-offenders:
    enabled: true
# shop.yml
tiers:
  uncommon:
    threshold: 4
    entries:
      head-protection:
        price: 6
  rare:
    threshold: 8
  legendary:
    threshold: 12
```

## Cozy, with no PvP

For a building server where nobody wants to fight. PvP is off for everyone and kills drop no heads, so trading is the only way to collect. Punishments are a nudge: the minor penalty is half an hour of slowness, the Penalty is only the Dunce title, and nobody becomes Wanted. Shields are cheaper, three quarters of unspent points carry over, and the shop entries that only matter in fights are hidden.

```yaml
# integrations.yml
pvp:
  included:
    toggle:
      allow-toggle: false
    combat-log:
      enabled: false
# config.yml
kills:
  enabled: false
points:
  rollover-percent: 75
# outcomes.yml
rules:
  wanted:
    enabled: false
# actions.yml
lists:
  punish-mild:
    - message: "<yellow>Your score was <score>, so you're a little slower for your next 30 minutes of play. <gray>A trade or two next cycle keeps you clear."
    - type: potion
      effect: slowness
      amplifier: 0
      duration: 30m
      time: online
      key: punish-slowness
      end-message: "<green>Your penalty is over."
  punish-medium:
    - message: "<gold>Your score was <score>, so you carry the Dunce title until the cycle ends."
    - type: force-title
      title: dunce
      duration: cycle
# shop.yml
tiers:
  common:
    entries:
      shield:
        price: 2
  uncommon:
    entries:
      head-protection:
        enabled: false
  rare:
    entries:
      title-headhunter:
        enabled: false
```

## A big server

For 40 or more active players. Vaults hold 36 heads and start with 24, so the best traders have room to keep collecting, and 3 hours of play are needed to count. With this many people scores run high, so a score point earns half a point and each shop tier asks for more. The diamonds and the elytra get more stock to go around.

```yaml
# config.yml
activity:
  min-playtime: 3h
vault:
  size: 36
  starting-heads: 24
points:
  earn-multiplier: 0.5
# shop.yml
tiers:
  uncommon:
    threshold: 5
  rare:
    threshold: 10
    entries:
      bigger-vault:
        display:
          lore: ["<gray>Only helps on a server big enough", "<gray>to fill 36 slots with different heads."]
      diamonds:
        stock: 15
        display:
          lore: ["<gray>15 for the whole server each cycle."]
  legendary:
    threshold: 16
    entries:
      elytra:
        stock: 3
        display:
          lore: ["<gray>3 for the whole server each cycle."]
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

With PvP on, the default rules stop players moving heads in or out of their vault and trading, so in the Nether they have to bring heads home before they count.

## A shop entry that lends a permission for a day

A purchase can give any permission for a while. This one lets a player trade without the cooldown for a day. The included permissions provider handles it, so no other plugin is needed. With a permissions plugin, the same entry can lend another plugin's permission, like `essentials.fly`.

```yaml
# shop.yml
tiers:
  uncommon:
    entries:
      no-trade-cooldown:
        slot: 22
        price: 3
        limit-per-cycle: 1
        display: { material: FEATHER, name: "<green>No trade cooldown for a day" }
        actions:
          - type: permission
            permission: headcount.bypass.trade.cooldown
            duration: 1d
            start-message: "<green>No trade cooldown for <duration>."
            end-message: "<gray>Your trade cooldown is back."
```

## A gift for everyone when a cycle starts

Each player gets 16 bread at the start of every cycle. Players who are offline get it when they next join.

```yaml
# config.yml
hooks:
  cycle-start:
    per-player:
      players: all
      offline: deliver
      expire-after-cycles: 1
      actions:
        - type: give-item
          material: BREAD
          amount: 16
        - message: "<gold>A new cycle has started. Here's some bread."
```

## Another scoreboard plugin shows Headcount instead

```yaml
# config.yml
sidebar:
  enabled: false
```

Then use `%headcount_unique%`, `%headcount_score%`, `%headcount_points%`, `%headcount_time_left%` and `%headcount_streak_text%` in that plugin. Other plugins can only read them when `placeholders.provider` in integrations.yml is `placeholderapi` and PlaceholderAPI is installed.

## Time zone as an abbreviation or offset

```yaml
# config.yml
cycle:
  time-zone: MST        # or GMT-7, UTC+5:30, America/Denver, system
```

Abbreviations and offsets are fixed: `MST` is UTC-7 all year. A region such as `America/Denver` follows daylight saving.
