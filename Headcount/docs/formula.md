# Score formula

At the end of a cycle, each active player's score is the result of `formula` in `scoring.yml`, rounded to a whole number by `rounding` (`floor`, `round` or `ceil`). Bonuses from `score-bonus` actions are added after that. To place them in the formula yourself, set `add-bonus-after-formula: false` and use the `bonus` variable.

A positive score gives that many points, multiplied by `points.earn-multiplier` in config.yml. A negative score triggers the punishments in outcomes.yml. `/hc score` shows a player's score if the cycle ended now, and `/hca cycle simulate` shows everyone's.

The default is:

```yaml
formula: "unique - clamp(round(sqrt(active)), 1, 4) + ceil(clamp(6 * (unique - 1) / max(min(active, vault_size) - 1, 1), 0, 6))"
```

It has three parts. `unique` is a point per different head, your own included. The par, `clamp(round(sqrt(active)), 1, 4)`, grows slowly with the server: 1 for up to 2 active players, 2 for 3 to 6, 3 for 7 to 12 and 4 from 13 up. The last part adds up to 6 for the share of the server's heads you collected, so a full set is worth the same bonus on a server of any size. On a server bigger than your vault, a full vault counts as a full set.

A player who never trades scores between 0 and -3, and one or two trades bring them back to 0. With 10 active players, 1 unique head scores -2, 3 score 2, 6 score 7 and all 10 score 13. A full set scores 9 with 5 active players, and a full vault of 27 different heads scores 29 with 30.

Before 1.1 the default was `unique - ceil(active / 4)`. It left small servers with scores of 1 to 3, and on big servers the par outgrew what a 27-slot vault can hold.

## Syntax

- Numbers, variables and `%placeholders%`
- `+ - * /`, `%` (remainder), `^` (power, right-associative), parentheses, unary minus
- Comparisons `< <= > >= == !=` (true is 1, false is 0), logic `&& || !`, and `condition ? a : b`
- Functions: `min(a, b, ...)`, `max(...)`, `avg(...)`, `floor`, `ceil`, `round(x[, digits])`, `trunc`, `abs`, `sign`, `pow(x, y)`, `sqrt`, `cbrt`, `exp`, `log(x[, base])` (natural log by default), `ln`, `log10`, `log2`, `clamp(x, low, high)`, `if(condition, a, b)`, `sin`, `cos`, `tan`
- Constants: `pi`, `e`, `true`, `false`

An unknown variable counts as 0, and the plugin warns about it when it loads. If the formula doesn't parse, Headcount reports it and uses the default until you fix it. Put spaces around `%` when you mean remainder (`a % 3`) so it isn't read as the start of a placeholder.

## Variables

| Variable | Meaning |
|---|---|
| `unique` | Heads of different owners in the vault. Duplicates don't count extra, and your own head counts once. |
| `total` | Total heads in the vault, duplicates included. |
| `own` | Copies of your own head in the vault. |
| `duplicates` | `total - unique` |
| `vault_size`, `vault_free` | Your vault slots this cycle, and the empty ones. |
| `starting_heads` | Heads every vault started this cycle with. |
| `active` | Active players this cycle. |
| `players` | Players who have ever joined. |
| `online` | Players online right now. |
| `rank` | Your rank among active players by unique heads, where 1 has the most. Ties share a rank. `scoring.yml rank-mode` picks `competition` (1, 1, 3) or `dense` (1, 1, 2). |
| `percentile` | Your percentile among active players. 100 is the top, including everyone tied for first, and 0 is the bottom. |
| `unique_avg`, `unique_median`, `unique_max`, `unique_min`, `unique_sum` | Unique heads across all active players. |
| `kills` | Kill heads you got this cycle. |
| `deaths` | Kill heads others got from you this cycle. |
| `trades` | Completed trades this cycle. |
| `heads_in`, `heads_out` | Heads received and given through trades this cycle. |
| `purchases`, `points_spent` | Shop purchases and points spent this cycle. |
| `points`, `points_next` | Points you hold now, and points already waiting for next cycle. |
| `playtime`, `playtime_hours` | Minutes and hours played this cycle. |
| `streak` | `+N` after N positive cycles in a row, `-N` after N negative ones, 0 otherwise. |
| `streak_positive`, `streak_negative` | The length of a positive or negative streak, else 0. |
| `last_score`, `last_unique`, `has_last` | Last cycle's score and unique heads (0 if none), and 1 if there was a last cycle. |
| `bonus` | Total of `score-bonus` actions waiting for this cycle end. |
| `titles` | Titles you own. |
| `cycle`, `cycle_length` | The cycle number and its length in days. |
| `days_elapsed`, `days_left` | Days since the cycle started, and days until it ends. |
| `is_active` | 1 if you are active this cycle. |
| `%any_placeholder%` | Any numeric PlaceholderAPI placeholder, such as `%statistic_player_kills%`. For offline players it depends on what the placeholder supports. A value that isn't a number counts as 0. |

## Examples

```yaml
formula: "floor(pow(unique, 1.5) / 2) - ceil(active / 4)"                      # exponential: rewards big collections
formula: "round((percentile - 50) / 10)"                                        # compared to everyone: -5 .. +5
formula: "rank <= 3 ? 10 - rank * 2 : (percentile < 25 ? -3 : 0)"              # top three rewarded, bottom quarter punished
formula: "round(unique / max(active, 1) * 10) - 5"                              # share of all active players collected
formula: "unique - ceil(active / 4) + min(streak_positive, 3)"                  # the 1.0 default with a streak bonus
formula: "unique - ceil(active / 4) - floor(own / 10)"                          # punish hoarding your own head
formula: "unique + kills - deaths - ceil(active / 4)"                           # kills matter
formula: "playtime_hours < 2 ? min(0, unique - ceil(active / 4)) : unique - ceil(active / 4)"  # no points without playtime
```

## Streaks

`scoring.yml streaks.inactive` decides whether a cycle where the player was inactive `pause`s or `break`s their streak. `streaks.zero-score` decides the same for a score of exactly 0.
