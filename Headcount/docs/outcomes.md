# Outcomes

`outcomes.yml` decides what happens to each active player at the end of a cycle, based on their score. Inactive players get no score, points or outcomes.

```yaml
mode: most-extreme      # or: all

rules:
  penalty:
    display: "<gold>Penalty"   # name in reports and history
    score: "..-1"              # -1 or lower
    conditions:                # optional, written like action conditions (see actions.md)
      streak: "<=-2"
    actions: punish-medium     # an action list or the name of one in actions.yml
    punishment: true           # shields block it; defaults to true when the whole range is below 0
    group: punishments         # rules only compete within their group; defaults to punishments or rewards
    priority: 3                # optional, higher wins in most-extreme mode
    enabled: true
```

A score range is an exact number (`5`), a span (`1..3`), an open end (`..-1` for -1 or lower, `10..` for 10 or more), a comparison (`>=3`, `<0`, `!=0`) or `any`.

With `mode: all`, a player gets every rule their score matches. With `most-extreme`, they get only the most extreme matching rule in each group. The further a range is from 0, the more extreme it is: `..-10` beats `..-5`, and `10..` beats `5..`. `priority` overrides that, and ties go to the rule listed first. Rules in different groups, such as `group: top` and `group: streak`, don't compete.

A `shield` action blocks the next rule marked `punishment`. The player is told, and the history records it as `shielded:<rule>`.

Outcome actions can use `<player> <score> <unique> <total> <own> <rank> <percentile> <streak> <points_earned> <points> <rolled_over> <cycle> <tier> <playtime> <kills> <deaths> <trades> <outcome>`.

Outcome actions run as the old cycle ends, after the new point balance and streak are saved. So an action with `duration: cycle` or `cycles:N` counts from the cycle that is starting and lasts the whole next cycle. Giving or taking points changes the balance the player starts that cycle with.

The bundled rules, all of which you can change:

Punishments climb with repeat offences rather than with the size of the score. With the default formula, a player who never trades scores -3 at worst, so a single bad cycle only brings `minor`. Timed parts count only time online.

| Rule | Score | Does |
|---|---|---|
| `minor` | -1 or lower | 2 hours of slowness. |
| `penalty` | negative two cycles in a row, or -4 or lower | 3 hours of weakness and 2 fewer hearts, and the Dunce title until the next cycle ends. |
| `wanted` | negative three cycles in a row, or -6 or lower | For 2 hours of play, PvP locked on and the player's location broadcast every 10 minutes. Until the next cycle ends, a locked dunce cap (a lit jack o'lantern), glowing, the Dunce title, a 5-point bounty, and an extra head for whoever kills them. |
| `ban-repeat-offenders` | negative, with a streak of -4 or worse | A 1-day ban. Off by default. |
| `ban-extreme` | -25 or lower | A permanent ban. Off by default. |
| `top-three` | positive, rank 1-3 and in the top half | Celebration and +3 points. |
| `champion` | positive, rank 1 and in the top half | The Champion title. |
| `streak-bonus` | positive and a streak of 3 or more | +2 points. |

New players also get a shield from the `first-join` hook in config.yml, so their first punishment is blocked.

`/hca cycle simulate` shows which rules each player would get right now, without applying anything.
