# Shop

`/hc shop` opens the shop. Points come from a positive score at the end of a cycle and are spent during the next one.

## Tiers

Each tier unlocks at a threshold of the player's score from **last cycle** (`threshold: none` is always unlocked). Players with no last cycle use `settings.new-player-score`. Each tier is its own set of pages; buttons switch tiers and pages. Locked tiers can be browsed: their entries show a "locked" look, and a banner shows the threshold and how far away the player is.

`/hc shop` opens on the highest tier the player has unlocked; `/hc shop <tier>` opens a given tier (by its ID, such as `common`).

```yaml
tiers:
  rare:
    name: "<blue>Rare"
    threshold: 10
    entries:
      diamonds:
        page: 1
        slot: 15
        price: 25
        stock: 10                  # global stock (-1 = unlimited); refills each cycle (settings.stock-reset)
        limit-per-cycle: 1         # per player (0 = unlimited)
        cooldown: 1d               # per player
        confirm: true              # force the confirmation menu on or off (default: price >= settings.confirm-at-price)
        conditions: { statuses: ["!in-pvp"] }
        requires:
          titles: [collector]
          purchases: [common.speed]          # ever bought (an entry in the same tier can be named without its tier)
          purchases-this-cycle: []
          permissions: []
        display:
          material: DIAMOND_BLOCK
          name: "<blue>Diamond block"
          glint: true
        display-states:
          sold-out: { material: BARRIER }
        actions:
          - give-item: DIAMOND_BLOCK
```

## Entry states

Each entry is shown in one of these states. Its look is built from the entry's `display`, then `default-states.<state>` at the top of shop.yml, then the entry's own `display-states.<state>`. Its lore is the entry's own lore followed by the state's lore.

| State | When |
|---|---|
| `affordable` | Everything is fine; clicking buys it. |
| `too-expensive` | Not enough points. |
| `sold-out` | Global stock is used up. |
| `limit-reached` | Bought `limit-per-cycle` times this cycle (falls back to `sold-out`). |
| `cooldown` | On cooldown (falls back to `too-expensive`). |
| `unavailable` | A condition or requirement isn't met (falls back to `too-expensive`). |
| `locked` | The tier isn't unlocked. |

Variables in names and lore: `<name> <price> <points> <missing> <stock> <purchases_left> <limit> <bought> <cooldown> <tier> <threshold>`, plus every placeholder.

Purchases at or above `confirm-at-price` open the menu in `confirm-gui` first. Every purchase is checked again at the moment of buying, so double clicks or a change in between never sell something twice. `/hca selftest` checks that every entry builds in every state and that no two entries share a slot.
