# Hooks

Hooks run action lists (see [actions.md](actions.md)) at moments in the cycle and in a player's time on the server. They are configured under `hooks` in `config.yml`.

## Cycle hooks

`cycle-start`, `cycle-end` and each entry under `warnings` have a `global` part and a `per-player` part. `cycle-start` also runs after `/hca reset cycle`, and `first-join-cycle` runs again then for everyone online.

```yaml
hooks:
  cycle-end:
    global:                  # runs once for the server
      - message: "<gold>Cycle <cycle> is over!"
    per-player:              # runs once for each player
      players: all           # all | active | inactive | online
      offline: deliver       # deliver (run it on the player's next join) | skip
      expire-after-cycles: 1 # a stored hook older than this many cycles is dropped
      actions:
        - message: "Your score: <score>"
```

Global actions run without a player, so messages, titles, sounds and boss bars go to everyone online. Per-player actions run once for each player, with that player's variables. A player who is offline gets them on their next join, before any other join hooks.

Actions that `cycle-end` gives with `duration: cycle` or `cycles:N` count from the cycle that is starting, so they last the whole next cycle.

Each warning needs `before: <duration>`, how long before the end it fires. A warning that came due while the server was offline still fires if it is no more than `missed-warning-grace` late, and is skipped if it is later than that. Moving the end time with `/hca cycle setend` or `extend` marks warnings that are already past. `/hca reset cycle` makes every warning fire again.

### Variables

All cycle hooks: `<cycle> <cycle_start> <cycle_end> <time_left> <cycle_length> <vault_size> <starting_heads> <active> <players>`. Warnings add `<warning>`.

`cycle-end` adds, globally: `<average_score> <rewarded> <punished> <top_list>` (a formatted list of the top `top-count` players), plus `<top_N_name> <top_N_unique> <top_N_score>` for N from 1 to 10.

`cycle-end` per player: `<player> <unique> <total> <own> <score> <rank> <percentile> <streak> <streak_text> <points_earned> <points> <rolled_over> <tier> <outcomes> <playtime> <kills> <deaths> <trades>`, and `is_active` (1 or 0) for `conditions: {active: true}`.

## Player hooks

```yaml
hooks:
  player:
    first-join: [...]        # first join ever
    first-join-cycle: [...]  # first join of each cycle (also players online when a cycle starts)
    became-active: [...]     # reached activity.min-playtime this cycle; checked every activity.check-interval, or right after first-join-cycle when min-playtime is 0
    join: [...]              # every join
```

Variables: `<player> <cycle> <time_left> <points> <streak> <streak_text>`.

When several hooks run on one join, they go in this order: delivered per-player cycle hooks, `first-join`, `first-join-cycle`, `became-active`, `join`. They start `hooks.join-delay` after the join, so they come after the join message.

### Hooks for things players do to each other

These run on the player who did something, with the variables above plus `<world> <x> <y> <z>` (where it happened):

```yaml
hooks:
  cooldowns:                 # shortest time between two runs for the same player; 0 runs it every time
    hurt-pet: 10s
    kill-pet: 0s
    open-trapped-chest: 10s
  player:
    hurt-pet: [...]          # hurt another player's tamed animal
    kill-pet: [...]          # killed one
    open-trapped-chest: [...]  # opened a trapped chest
```

- `hurt-pet` runs when a player hurts another player's tamed animal: a wolf, cat, parrot, horse, donkey, mule, llama or any other animal that can be tamed. Hits, projectiles, TNT, potion clouds and the player's own pets all count. Extra variables: `<owner>` and `<owner_uuid>` (the pet's owner), `<pet>` (its custom name, or its kind in each player's own language), `<pet_type>` (like `wolf`) and `<damage>`.
- `kill-pet` runs when a player kills one, with the same variables except `<damage>`. The killing hit also runs `hurt-pet`.
- `open-trapped-chest` runs when a player opens a trapped chest. Minecraft doesn't record who placed a chest, so it runs for every trapped chest, the player's own included.

None of them run for players in creative or spectator, for a pet's owner hurting their own pet, or for damage or opening another plugin blocked (a protected claim, or [pet protection](integrations.md#pet-protection)). They can fire many times a minute, so each has a per-player cooldown under `hooks.cooldowns`. A run skipped by the cooldown does nothing at all, so a timed action in the hook should last longer than the cooldown. With the default `stacking: refresh`, each run after the cooldown restarts a timed action.

Pet vengeance turns on the attacker's PvP for 10 minutes and locks it, so the owner can fight back:

```yaml
    hurt-pet:
      - type: pvp
        state: "on"
        lock: true
        duration: 10m
        start-message: "<red>You hurt <owner>'s <pet>, so your PvP is on for <duration>."
```

A trapped chest can do the same with `open-trapped-chest`. Quote `"on"`, because YAML reads a bare `on` as true. Both examples are in the bundled `config.yml` as comments.

Other moments were left out on purpose: breaking or emptying a chest someone else placed (Minecraft doesn't track who placed it), and pressure plates and tripwires (they fire constantly, and a trap built from them can already run a command block).

## Defaults

| Hook | Default |
|---|---|
| Warnings `3d`, `1d`, `1h`, `10m` | A chat message. |
| Warning `1m` | A title and a boss bar counting down to the end. Boss bar text can use any hook variable, such as `<cycle>`. |
| `cycle-end` global | A results broadcast with the top 3, the active count, the average score, and how many were rewarded and punished. |
| `cycle-end` per player | A report: unique heads, rank, score, points earned and held, streak, tier unlocked, and outcomes (inactive players get a short note). |
| `cycle-start` global | "Cycle N has begun!" with a sound. |
| `first-join` | A welcome that explains Headcount and points to `/hc help`, and a shield that blocks the player's first punishment. |
| `first-join-cycle` | The cycle number, time left, and points to spend. |
| `became-active` | A line saying the player now counts this cycle, and that `/hc score` shows their score. |
| `join` | Nothing. |
| `hurt-pet`, `kill-pet`, `open-trapped-chest` | Nothing. Commented examples in `config.yml` show pet vengeance and a trapped chest that turns PvP on. |

Test any hook with `/hca hook <cycle-start|first-join|first-join-cycle|became-active|join|hurt-pet|kill-pet|open-trapped-chest|warning:<id>> [player]`. A test of a pet hook pretends the player hurt or killed a wolf of their own where they stand, and skips the cooldown.
