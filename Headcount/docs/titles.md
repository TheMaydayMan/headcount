# Titles

`titles.yml` defines titles players can own: from the shop, outcomes, admin commands, or any `grant-title` action.

```yaml
titles:
  legend:
    display: "<gradient:gold:yellow>[Legend]</gradient>"
    description: ["<gray>The rarest title in the shop."]
    icon: { material: NETHER_STAR, glint: true }
```

- `/hc titles` lists owned titles (and, with `gui.show-unowned`, locked ones). Click one to equip or unequip it.
- A `force-title` action equips a title and locks it for its duration; the player can't change it until it ends.
- `%headcount_title%` shows the shown title (wrapped by `settings.format`, or `settings.none-text` when there is none) for chat and tab plugins. `%headcount_title_raw%` and `%headcount_title_id%` are also available.
- `settings.permission-prefix` can mirror the shown title into LuckPerms (or Vault) as a prefix or suffix at a priority Headcount owns. It needs `integrations.yml permissions.provider: luckperms` (or `vault`); the default `included` can't set prefixes. It updates when titles change, on join, and for online players on `/hca reload` (turning it off then removes the prefixes Headcount set for players online).
- `settings.auto-equip-first` equips a player's first title automatically.

Admins: `/hca title <player> grant|revoke <title>`.
