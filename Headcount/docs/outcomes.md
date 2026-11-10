# Outcomes

`outcomes.yml` decides what happens to each **active** player at the end of a cycle, based on their score. Inactive players get no score, no points and no outcomes.

```yaml
mode: most-extreme      # or: all

rules:
  penalty-5:
    display: "<gold>Penalty"   # name in reports and history
    score: "..-5"              # -5 or lower
    conditions:                # optional (see actions.md: statuses, streak, rank, expression, ...)
      streak: "<=-2"
    actions: punish-medium     # an action list or the name of one in actions.yml
    punishment: true           # a shield blocks it (default: true when the whole score range is below 0)
    group: punishments         # rules compete within a group (default: punishments or rewards)
    priority: 5                # optional: higher wins in most-extreme mode
    enabled: true
```

**Score ranges:** `5` (exactly), `1..3`, `..-1` (-1 or lower), `10..` (10 or more), `>=3`, `<0`, `!=0`, `any`.

**Modes:**
- `all`: a player gets every rule their score matches.
- `most-extreme`: only the most extreme matching rule in each group. A rule is more extreme the further its range is from 0 (`..-10` beats `..-5` beats `..-1`, and `10..` beats `5..`), unless `priority` says otherwise. Ties go to the rule listed first. Rules in different groups (`group: top` and `group: streak`, for example) don't compete.

**Shields:** a `shield` action blocks the next rule marked `punishment`. It is recorded as `shielded:<rule>` in the history and the player is told.

**Variables in outcome actions:** `<player> <score> <unique> <total> <own> <rank> <percentile> <streak> <points_earned> <points> <rolled_over> <cycle> <tier> <playtime> <kills> <deaths> <trades> <outcome>`.

Outcome actions run while the old cycle is ending, so an action they give with `duration: cycle` (or `cycles:N`) counts from the cycle that is starting: "for the cycle" means the whole next cycle.

Outcome actions run after the new balance and streak are saved, so taking points takes them from the balance the player starts the next cycle with, and giving points adds to it.

**Defaults** (all editable):

| Rule | Score | Does |
|---|---|---|
| `penalty-1` | -1 or lower | A message and a day of slowness (online time). |
| `penalty-5` | -5 or lower | Two days of weakness and -2 hearts. |
| `penalty-10` | -10 or lower | A locked dunce cap, glowing and a 5-point bounty for the whole next cycle, and the player's location broadcast every 30 minutes for a day. |
| `ban-repeat-offenders` | negative, with a streak of -3 or worse | A 1-day ban. **Disabled.** |
| `ban-extreme` | -25 or lower | A permanent ban. **Disabled.** |
| `top-three` | positive and rank 1-3 | Celebration, the Champion title, +5 points. |
| `streak-bonus` | positive and a streak of 3 or more | +3 starting heads next cycle. |

`/hca cycle simulate` shows which rules each player would get right now, without applying anything.
