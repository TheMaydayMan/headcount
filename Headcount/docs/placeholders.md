# Placeholders

Headcount's placeholders are written `%headcount_<name>%`. They work:

- in all of Headcount's own text (messages, menus, items, sidebar, hooks, actions), with or without PlaceholderAPI;
- in the score formula and in `placeholder` statuses;
- in any other plugin, through PlaceholderAPI when `integrations.yml placeholders.provider` is `placeholderapi` (the expansion registers itself).

With `placeholders.provider: none`, no `%...%` placeholder is filled in anywhere, Headcount's own included.

Formatted values (like the title) are given to other plugins as legacy color codes by default. Set `placeholders.placeholderapi.expansion-format` to `minimessage` or `plain` to change that. True/false values read `yes`/`no` (`config.yml placeholders`); status placeholders use `statuses.yml settings`. `/hca placeholders <player>` lists every value for a player.

## Player

| Placeholder | Value |
|---|---|
| `unique`, `total`, `own`, `duplicates` | Heads in the player's vault right now. |
| `vault_size`, `vault_used`, `vault_free` | Vault slots. |
| `score` | Score if the cycle ended now. |
| `rank`, `percentile` | Live rank and percentile by unique heads. |
| `active` | Whether they are active this cycle. |
| `points`, `points_next` | Points to spend, and points waiting for next cycle. |
| `streak` | Signed streak (`+3`, `-2`, `0`). |
| `streak_text` | Streak as text from the language file. |
| `last_score`, `last_unique` | Last cycle's results. |
| `playtime`, `kills`, `deaths`, `trades` | This cycle. |
| `title` | Equipped (or forced) title, formatted. |
| `title_raw`, `title_id` | As plain text, and its ID. |
| `tier`, `tier_id` | Highest shop tier unlocked. |
| `shield` | Shield charges available. |
| `bounty` | Points on their head. |
| `effects` | Number of active effects. |
| `sidebar`, `in_trade`, `bedrock` | true/false. |
| `trade_cooldown` | Time until they can trade again. |
| `status_<name>` | Whether they have that status (any status in statuses.yml). |
| `flag_<name>` | Whether a Headcount flag is set. |

## Server

| Placeholder | Value |
|---|---|
| `cycle` | Current cycle number (0 before the first). A `/hca reset cycle` keeps it. |
| `cycle_start`, `cycle_end` | Dates. |
| `time_left`, `time_left_clock`, `seconds_left` | Time until the cycle ends, as `3d 4h`, `HH:MM:SS` or seconds. |
| `cycle_length`, `starting_heads` | Days per cycle; starting heads this cycle. |
| `active_players`, `total_players` | Active this cycle; ever joined. |
| `top_<n>_name`, `top_<n>_unique`, `top_<n>_total`, `top_<n>_uuid` | The live leaderboard (refreshed every `top.refresh-interval`). |

## Variables inside Headcount text

Each text in the configs also has its own `<variables>` (for example `<owner>` on head items or `<price>` in the shop), listed next to each option. These are MiniMessage tags filled in by Headcount; see [text.md](text.md).
