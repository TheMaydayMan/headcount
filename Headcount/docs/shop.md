# Shop

`/hc shop` opens the shop. Players earn points from a positive score at the end of a cycle and spend them during the next one.

## Tiers

A tier unlocks when the player's score from last cycle is at or above its `threshold`. `threshold: none` is always unlocked, and players with no last cycle count as having `settings.new-player-score`. Each tier has its own pages, with buttons to switch between tiers and pages. Players can still browse a locked tier. Its entries show their locked look, and a banner shows the threshold and how far off the player is.

`/hc shop` opens on the highest tier the player has unlocked. `/hc shop <tier>` opens a tier by its ID, such as `common`.

```yaml
tiers:
  rare:
    name: "<blue>Rare"
    threshold: 6
    entries:
      diamonds:
        page: 1
        slot: 16
        price: 10
        stock: 5                   # shared by all players, -1 for unlimited; refills as set by settings.stock-reset
        limit-per-cycle: 1         # per player, 0 for unlimited
        cooldown: 1d               # per player
        confirm: true              # forces the confirmation menu on or off; by default it shows from settings.confirm-at-price up
        conditions: { statuses: ["!in-pvp"] }
        requires:
          titles: [collector]
          purchases: [common.speed]          # bought at any time; an entry in the same tier can leave out the tier
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

Each entry is shown in one of these states. Its look starts from the entry's `display`, then takes `default-states.<state>` from the top of shop.yml, then the entry's own `display-states.<state>`. Its lore is the entry's lore followed by the state's lore.

| State | When |
|---|---|
| `affordable` | The player can buy it with a click. |
| `too-expensive` | Not enough points. |
| `sold-out` | Global stock is used up. |
| `limit-reached` | Bought `limit-per-cycle` times this cycle (falls back to `sold-out`). |
| `cooldown` | On cooldown (falls back to `too-expensive`). |
| `unavailable` | A condition or requirement isn't met (falls back to `too-expensive`). |
| `locked` | The tier isn't unlocked. |

Variables in names and lore: `<name> <price> <points> <missing> <stock> <purchases_left> <limit> <bought> <cooldown> <tier> <threshold>`, plus every placeholder.

Purchases at or above `confirm-at-price` open the `confirm-gui` menu first. Each purchase is checked again at the moment of buying, so a double click or a change in between can't sell something twice. `/hca selftest` checks that every entry builds in every state and that no two entries share a slot.
