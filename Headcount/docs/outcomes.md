# Outcomes

`outcomes.yml` decides what happens to each active player at the end of a cycle, based on their score. Inactive players get no score, points or outcomes.

```yaml
mode: most-extreme      # or: all

rules:
  penalty-5:
    display: "<gold>Penalty"   # name in reports and history
    score: "..-5"              # -5 or lower
    conditions:                # optional, written like action conditions (see actions.md)
      streak: "<=-2"
    actions: punish-medium     # an action list or the name of one in actions.yml
    punishment: true           # shields block it; defaults to true when the whole range is below 0
    group: punishments         # rules only compete within their group; defaults to punishments or rewards
    priority: 5                # optional, higher wins in most-extreme mode
    enabled: true
```

A score range is an exact number (`5`), a span (`1..3`), an open end (`..-1` for -1 or lower, `10..` for 10 or more), a comparison (`>=3`, `<0`, `!=0`) or `any`.

With `mode: all`, a player gets every rule their score matches. With `most-extreme`, they get only the most extreme matching rule in each group. The further a range is from 0, the more extreme it is: `..-10` beats `..-5`, and `10..` beats `5..`. `priority` overrides that, and ties go to the rule listed first. Rules in different groups, such as `group: top` and `group: streak`, don't compete.

A `shield` action blocks the next rule marked `punishment`. The player is told, and the history records it as `shielded:<rule>`.

Outcome actions can use `<player> <score> <unique> <total> <own> <rank> <percentile> <streak> <points_earned> <points> <rolled_over> <cycle> <tier> <playtime> <kills> <deaths> <trades> <outcome>`.

Outcome actions run as the old cycle ends, after the new point balance and streak are saved. So an action with `duration: cycle` or `cycles:N` counts from the cycle that is starting and lasts the whole next cycle. Giving or taking points changes the balance the player starts that cycle with.

The bundled rules, all of which you can change:

| Rule | Score | Does |
|---|---|---|
| `penalty-1` | -1 or lower | A message and a day of slowness (online time). |
| `penalty-5` | -5 or lower | Two days of weakness and -2 hearts. |
| `penalty-10` | -10 or lower | A locked dunce cap, glowing and a 5-point bounty for the whole next cycle, and the player's location broadcast every 30 minutes for a day. |
| `ban-repeat-offenders` | negative, with a streak of -3 or worse | A 1-day ban. Off by default. |
| `ban-extreme` | -25 or lower | A permanent ban. Off by default. |
| `top-three` | positive and rank 1-3 | Celebration, the Champion title, +5 points. |
| `streak-bonus` | positive and a streak of 3 or more | +3 starting heads next cycle. |

`/hca cycle simulate` shows which rules each player would get right now, without applying anything.
