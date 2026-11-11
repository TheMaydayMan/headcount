# Titles

`titles.yml` defines the titles players can own. They come from the shop, outcomes, admin commands or any `grant-title` action.

```yaml
titles:
  legend:
    display: "<gradient:gold:yellow>[Legend]</gradient>"
    description: ["<gray>The rarest title in the shop."]
    icon: { material: NETHER_STAR, glint: true }
```

- `/hc titles` lists the player's titles, plus locked ones with `gui.show-unowned`. Clicking a title equips or unequips it.
- A `force-title` action equips a title and locks it for its duration; the player can't change it until it ends.
- `%headcount_title%` gives chat and tab plugins the shown title, wrapped in `settings.format`, or `settings.none-text` when there is none. `%headcount_title_raw%` and `%headcount_title_id%` give it as plain text and as its ID.
- `settings.permission-prefix` copies the shown title into LuckPerms or Vault as a prefix or suffix, at a priority Headcount owns. It needs `integrations.yml permissions.provider` set to `luckperms` or `vault`, since the default `included` can't set prefixes. It updates when titles change, when a player joins, and for online players on `/hca reload`. Turning it off and reloading removes the prefixes Headcount set for players who are online.
- `settings.auto-equip-first` equips a player's first title automatically.

Admins can give and take titles with `/hca title <player> grant|revoke <title>`.
