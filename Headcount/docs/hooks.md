# Hooks

Hooks run action lists (see [actions.md](actions.md)) at moments in the cycle and in a player's time on the server. They are configured under `hooks` in `config.yml`.

## Cycle hooks

`cycle-start`, `cycle-end`, and each entry under `warnings` have two parts:

```yaml
hooks:
  cycle-end:
    global:                  # runs once for the server
      - message: "<gold>Cycle <cycle> is over!"
    per-player:              # runs once for each player
      players: all           # all | active | inactive | online
      offline: deliver       # deliver: store it and run it on the player's next join | skip
      expire-after-cycles: 1 # a stored hook older than this many cycles is dropped
      actions:
        - message: "Your score: <score>"
```

- **Global** actions run without a player. Messages, titles, sounds and boss bars go to everyone online.
- **Per-player** actions run once for each player with that player's variables. For offline players they are stored and run on their next join, first in the join order.
- Actions given by `cycle-end` with `duration: cycle` (or `cycles:N`) count from the cycle that is starting, so they last the whole next cycle.
- **Warnings** each need `before: <duration>`, how long before the end they fire. A warning that should have fired while the server was offline still fires if it is at most `missed-warning-grace` late; older ones are skipped. Changing the end time with `/hca cycle setend` or `extend` marks warnings that are already past.

### Variables

All cycle hooks: `<cycle> <cycle_start> <cycle_end> <time_left> <cycle_length> <vault_size> <starting_heads> <active> <players>`. Warnings add `<warning>`.

`cycle-end` adds, globally: `<average_score> <rewarded> <punished> <top_list>` (a formatted list of the top `top-count` players), plus `<top_N_name> <top_N_unique> <top_N_score>` for N = 1 to 10.

`cycle-end` per player: `<player> <unique> <total> <own> <score> <rank> <percentile> <streak> <streak_text> <points_earned> <points> <rolled_over> <tier> <outcomes> <playtime> <kills> <deaths> <trades>`, and `is_active` (1 or 0) for `conditions: {active: true}`.

## Player hooks

```yaml
hooks:
  player:
    first-join: [...]        # first join ever
    first-join-cycle: [...]  # first join of each cycle (also players online when a cycle starts)
    became-active: [...]     # reached activity.min-playtime this cycle (checked every activity.check-interval; right after first-join-cycle when it is 0)
    join: [...]              # every join
```

Variables: `<player> <cycle> <time_left> <points> <streak> <streak_text>`.

When several hooks run on one join, the order is: delivered per-player cycle hooks, then `first-join`, then `first-join-cycle`, then `became-active`, then `join`. They start `hooks.join-delay` after the join, so they come after the join message.

## Defaults

| Hook | Default |
|---|---|
| Warnings `3d`, `1d`, `1h`, `10m` | A chat message. |
| Warning `1m` | A title, and a boss bar counting down to the end. |
| `cycle-end` global | Results broadcast with the top 3, active count, average score, rewarded and punished. |
| `cycle-end` per player | A report: unique heads, rank, score, points earned and held, streak, tier unlocked, and outcomes (inactive players get a short note). |
| `cycle-start` global | "Cycle N has begun!" with a sound. |
| `first-join` | A welcome that explains Headcount and points to `/hc help`. |
| `first-join-cycle` | The cycle number, time left, and points to spend. |
| `became-active`, `join` | Nothing. |

Test any hook with `/hca hook <cycle-start|first-join|first-join-cycle|became-active|join|warning:<id>> [player]`.
