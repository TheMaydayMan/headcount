# Integrations

Every integration is optional; Headcount works fully with none of them. `integrations.yml` chooses which plugin handles what (`auto` picks the first installed one), and `/hca integrations` shows the result. On startup the console lists the chosen provider for each.

| Job | Plugins | Without them |
|---|---|---|
| PvP state, combat tag, newbie protection; the `pvp` action | **PvPManager** (direct hook). Any other PvP or combat plugin through `placeholder` statuses. | `in-pvp` is never active; `combat-tagged` falls back to Headcount's own damage check. |
| Other plugins' placeholders in Headcount's text, formula and statuses; `%headcount_*%` for other plugins | **PlaceholderAPI** | Headcount's own placeholders still work in its own text. |
| Money (`money` action) | **Vault** (or VaultUnlocked) with an economy plugin | Money actions are skipped with a warning. |
| Permissions, groups, tracks, prefixes, suffixes | **LuckPerms** (own expiry for timed changes), else **Vault** | These actions are skipped with a warning. |
| Bans, mutes, kicks, warnings, jails | **LiteBans** (driven by its commands, since its API can't create punishments), **LibertyBans**, **AdvancedBan**, **EssentialsX** (mutes and jails), or **commands** (templates for any plugin) | Vanilla bans, kicks and warnings, plus Headcount's own mutes (chat and listed commands blocked) and jails (teleport to `vanilla-jail.location`, held within its radius, commands limited). |
| Discord messages | **DiscordSRV** | Skipped with a warning. |
| Custom items in item definitions (`custom: itemsadder:ns:id`, `oraxen:id`, `nexo:id`) | **ItemsAdder**, **Oraxen**, **Nexo** | The item falls back to its `material`. |
| Bedrock players | **Floodgate**, else **Geyser** | Everything still works; only the Bedrock-specific request text is lost. |
| AFK/vanish/god/jail/mute statuses | **EssentialsX** | Those statuses use their fallback or `unavailable`. |

**PvPManager and `/pvp`.** PvPManager only lets players run `/pvp` with `pvpmanager.command.pvp`, which is off by default, and players start with PvP on. Headcount's default rules block moving heads and trading while PvP is on, so by default (`pvp.let-players-toggle: true`) Headcount makes that permission default to true, and the start-up summary says so. A player or group explicitly denied it in your permissions plugin stays denied. Set the option to `false` to hand the permission out yourself, for example `lp group default permission set pvpmanager.command.pvp true`.

Each moderation kind uses the first provider that supports it. With LibertyBans and EssentialsX both installed, for example, bans and mutes go to LibertyBans and jails to EssentialsX.

## Bedrock (Geyser and Floodgate)

- Every menu (vault, trade, shop, titles, history, confirmations) is a normal chest menu, which Geyser shows on Bedrock. Every click Geyser sends is handled by the same rules as on Java.
- Bedrock players get trade requests with `/trade accept` and `/trade deny` instead of a clickable link.
- The sidebar uses team prefixes on unique entries, which Bedrock renders correctly.
- A Bedrock player's heads are economy heads like anyone's. The economy uses the player's UUID, so it doesn't matter if the skin doesn't load.

## CoreProtect

CoreProtect logs the world side: placed heads, chests and drops. It can't see virtual inventories, so Headcount's own audit log covers vaults and trades. Use both together.
