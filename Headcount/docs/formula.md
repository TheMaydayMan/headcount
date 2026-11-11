# Score formula

At the end of a cycle each **active** player's score is `formula` from `scoring.yml`, turned into a whole number with `rounding` (`floor`, `round` or `ceil`). Bonuses from `score-bonus` actions are then added, unless `add-bonus-after-formula: false` (in which case use the `bonus` variable yourself).

A positive score gives that many points (times `points.earn-multiplier` in config.yml). Negative scores trigger punishments in outcomes.yml. `/hc score` shows a player's score if the cycle ended now, and `/hca cycle simulate` shows everyone's.

Default: `unique - ceil(active / 4)`. With 12 active players, 3 unique heads scores 0 and 10 unique heads scores 7.

## Syntax

- Numbers, variables and `%placeholders%`
- `+ - * /`, `%` (remainder), `^` (power, right-associative), parentheses, unary minus
- Comparisons `< <= > >= == !=` (true is 1, false is 0), logic `&& || !`, and `condition ? a : b`
- Functions: `min(a, b, ...)`, `max(...)`, `avg(...)`, `floor`, `ceil`, `round(x[, digits])`, `trunc`, `abs`, `sign`, `pow(x, y)`, `sqrt`, `cbrt`, `exp`, `log(x[, base])` (natural log by default), `ln`, `log10`, `log2`, `clamp(x, low, high)`, `if(condition, a, b)`, `sin`, `cos`, `tan`
- Constants: `pi`, `e`, `true`, `false`

An unknown variable counts as 0 and is warned about when the plugin loads. A formula that doesn't parse is reported and replaced with the default until fixed. Write `%` as remainder with spaces around it (`a % 3`), so it is never read as the start of a placeholder.

## Variables

| Variable | Meaning |
|---|---|
| `unique` | Unique heads in the vault: heads of different owners. Duplicates don't count extra; your own head counts once. |
| `total` | Total heads in the vault, duplicates included. |
| `own` | Copies of your own head in the vault. |
| `duplicates` | `total - unique` |
| `vault_size`, `vault_free` | Your vault slots this cycle, and the empty ones. |
| `starting_heads` | Heads every vault started this cycle with. |
| `active` | Active players this cycle. |
| `players` | Players who have ever joined. |
| `online` | Players online right now. |
| `rank` | Your rank among active players by unique heads (1 = most). Ties share a rank; `scoring.yml rank-mode` chooses `competition` (1, 1, 3) or `dense` (1, 1, 2). |
| `percentile` | Your percentile among active players: 100 = top (including everyone tied for first), 0 = bottom. |
| `unique_avg`, `unique_median`, `unique_max`, `unique_min`, `unique_sum` | Over active players. |
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
| `%any_placeholder%` | Any numeric PlaceholderAPI placeholder, e.g. `%statistic_player_kills%`. Offline players resolve whatever the placeholder supports; anything that isn't a number counts as 0. |

## Examples

```yaml
formula: "floor(pow(unique, 1.5) / 2) - ceil(active / 4)"                      # exponential: rewards big collections
formula: "round((percentile - 50) / 10)"                                        # compared to everyone: -5 .. +5
formula: "rank <= 3 ? 10 - rank * 2 : (percentile < 25 ? -3 : 0)"              # top three rewarded, bottom quarter punished
formula: "round(unique / max(active, 1) * 10) - 5"                              # share of all active players collected
formula: "unique - ceil(active / 4) + min(streak_positive, 3)"                  # streak bonus on top of the default
formula: "unique - ceil(active / 4) - floor(own / 10)"                          # punish hoarding your own head
formula: "unique + kills - deaths - ceil(active / 4)"                           # kills matter
formula: "playtime_hours < 2 ? min(0, unique - ceil(active / 4)) : unique - ceil(active / 4)"  # no points without playtime
```

## Streaks

`scoring.yml streaks.inactive` decides whether a cycle where the player was inactive `pause`s or `break`s their streak. `streaks.zero-score` decides the same for a score of exactly 0.
