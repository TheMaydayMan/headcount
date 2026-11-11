# Manual test checklist

This checklist predates 1.1. The 1.1 features (resets, defunct heads, time zone codes, `<score>` and the live vault title, the included PvP, permissions and AFK, and the reworked integrations) aren't in it or in the bot tests. They are covered by the test night plan, [test-plan.md](../test-plan.md), which also runs through most of what is below in a normal play session.

Some of it is already tested automatically:
- `./gradlew test` runs the unit tests for the pure logic, such as expressions, durations, cycle boundaries across DST and audit rotation.
- `/hca selftest` checks every configured item, menu, message, action list and status reference.
- The bot tests in [tools/bot-tests](tools/bot-tests/README.md) play sections 1 to 10 and 12 with bot clients, crash tests included. Run them with `tools/bot-tests/run-all.sh`.

A person is still needed for anything that depends on a real client or on something the test server lacks: how menus and the sidebar look, Bedrock with a real Xbox sign-in, the movement statuses (the bots can't move), DiscordSRV, LiteBans and the custom item plugins. Those are in the checklist below. Go through the whole list for a release or after big changes.

## Setup

1. Set up the server once with `TestServer/setup.sh` and build Headcount (`cd Headcount && ./gradlew build`, which copies the jar into the server). Start it with `cd TestServer && ./start.sh` (Java 25, offline mode, port 25565).
2. Join with two Java clients under different names. Offline mode accepts any name, e.g. two launcher profiles or instances. Call them **A** and **B**. Make A op (`op A` in the console).
3. For Bedrock: join with a Bedrock client on port 19132 (Geyser), called **C**.
4. Headcount's included PvP starts everyone with PvP off. If PvPManager is still in `plugins/`, it turns PvP on for everyone, and the default `in-pvp` status then blocks vault movement. Delete its jar, or run `/pvp` as each player to turn PvP off (see [TestServer/README.md](../TestServer/README.md)).
5. Start from clean data: stop the server, delete `TestServer/plugins/Headcount/`, and start again.
6. Useful commands: `/hca status <player>`, `/hca effects <player>`, `/hca audit <player>`, `/hca inspect` (with a head in your hand), `/hca cycle simulate`, `/hca cycle end confirm`.

## 1. First join and the vault

- [ ] A's first join shows the welcome message (first-join hook), then the cycle message with time left and points (first-join-cycle).
- [ ] `/hc` opens "A's Head Vault" with 27 of A's own heads (27 slots, 3 rows). Each head shows A's skin, the cycle number and a short ID.
- [ ] `/hca inspect` on one of those heads (after taking it out) shows owner A, the current cycle, current: yes, in a vault: no.
- [ ] Take a head out, put it back, and swap two heads by moving them between slots. Shift-click in and out works. Number keys 1-9 and F (offhand) on a vault slot swap with the hotbar or offhand.
- [ ] A dirt block can't be put in the vault by click, shift-click, number key, drag or offhand ("Only economy heads...").
- [ ] Double-clicking a head on the cursor while the vault is open doesn't pull heads out of the vault.
- [ ] Q and Ctrl+Q on a vault slot do nothing. Middle click on a vault slot in creative does nothing.
- [ ] A vanilla head (`/give A player_head`) is refused by the vault.
- [ ] Turn PvP on with `/pvp`: the vault opens, but moving a head says you can't move heads while your PvP is on. Turn it off again.
- [ ] `/gamemode creative`: moving heads is refused (creative message). Back to survival.
- [ ] With `vault.block-movement.block-opening: true` and `/hca reload`, a blocked player can't open the vault at all. Set it back.
- [ ] Grant `headcount.bypass.vault-move.in-pvp` (`lp user A permission set headcount.bypass.vault-move.in-pvp`): with PvP on, A can move heads. Unset it.
- [ ] B's first join also gets a full vault of B's heads. A player who joins mid-cycle gets the same.

## 2. Heads in the world

- [ ] Wear an economy head; place it in an item frame, a chest, a shulker box and a bundle; take each back. `/hca inspect` still shows the same ID.
- [ ] Place an economy head as a block and break it in survival: the drop is the economy head (same ID per `/hca inspect`).
- [ ] Place one, blow it up with TNT: the drop is the economy head.
- [ ] Place one in front of a piston and push it: the drop is the economy head.
- [ ] Place one and let a wither break it (or `/summon wither` nearby): the drop is the economy head.
- [ ] Place one, then remove it with `/setblock ~ ~ ~ air destroy`: the drop is the economy head.
- [ ] Break a placed economy head in creative: nothing drops, and `HEAD_DESTROYED` with `cause=creative-break` appears in `/hca audit`.
- [ ] Creative middle-click (pick block, with and without Ctrl) on a placed economy head, then place the picked head and break it in survival: the drop is a plain head, not an economy head. `/hca audit` shows `DUPLICATE`.
- [ ] In creative, middle-click an economy head in your inventory to clone it: a `DUPLICATE` alert reaches the op, and the copy can't be put in a vault (in survival, after taking the original's slot into account).

## 3. Kills

- [ ] A kills B with a sword: a fresh head of B drops where B died. It doesn't come from B's vault (B's vault still has 27).
- [ ] A kills B again in the same cycle: no head, and "already took" is shown.
- [ ] A kills B with a bow, and by knocking B into lava: credited kills drop heads (use a new cycle or another victim each time).
- [ ] B kills themselves (lava alone, `/kill`): no head.
- [ ] With `/gamerule keepInventory true`, a kill still drops the victim's head, while B keeps their own items.
- [ ] Give B head protection (`/hca run B` with a list containing `head-protection` and a duration, or buy it in the uncommon shop tier): A's kill drops nothing ("protected").

## 4. Trading

- [ ] `/trade B` from A: B gets a clickable [Accept]/[Deny] (Java). B clicks Accept: both see a double chest with their own offer on the left.
- [ ] `/trade A` from C (Bedrock): A gets it; `/trade C` from A: C gets the text version with `/trade accept`.
- [ ] Put items and heads on your side by click, shift-click, number keys and drag: the other side sees them mirrored on the right.
- [ ] Click, shift-click, double-click and number-key the other player's items on the right side: nothing can be taken.
- [ ] Both confirm, then one changes their offer: both confirmations clear.
- [ ] Both confirm: a 3-second countdown runs and neither side can change anything; then items are exchanged. Full inventories get items dropped at the feet with a warning.
- [ ] Clicking confirm during the countdown stops it.
- [ ] A second trade within 10 s is refused (cooldown).
- [ ] Each of these cancels the trade and returns everything to its owner:
  - [ ] closing the screen (Esc)
  - [ ] disconnecting
  - [ ] `/kick B`
  - [ ] banning B (`/ban B` through LibertyBans)
  - [ ] dying (the dying player's offer goes to their death drops)
  - [ ] `/tp` either player
  - [ ] changing world (nether portal)
  - [ ] changing gamemode
  - [ ] getting combat tagged (A hits B)
  - [ ] turning PvP on with `/pvp`
  - [ ] the cancel button
  - [ ] `/hca trade cancel A`
  - [ ] `/hca reload`
  - [ ] `/stop`, with items back after restart
- [ ] Request rules: trading yourself, an offline name, a vanished player (`/vanish` with EssentialsX), and a player already in a trade are all refused with the right message. Requests expire after 30 s.
- [ ] With `range.blocks: 10`, players 20 blocks apart can't trade, and walking apart mid-trade cancels it.
- [ ] Crash recovery: put items in an offer, wait two seconds, kill the server process (`kill -9`), restart, rejoin: the items are given back exactly once (not duplicated).
- [ ] `limits.max-heads: 2`: a third head can't be offered. `only-own-heads: true`: B's head can't be offered by A.

## 5. Statuses

- [ ] `/hca status A` lists every status with its current value. Check: `in-pvp` follows `/pvp`; `combat-tagged` turns on when hitting B and off when the combat tag ends; `creative-or-spectator`; `sleeping-or-dead` (in a bed); `vanished` (`/vanish`); `afk` (`/afk`); `low-health`, `on-fire`, `in-lava`, `falling-far`.
- [ ] `%headcount_status_combat-tagged%` reads true/false (`/papi parse A %headcount_status_combat-tagged%`).
- [ ] The sources that need a moving player, which the bots can't test. `sleeping` and `falling-far` are default statuses; add test statuses with `source: flying`, `gliding` and `swimming`. Each one turns true while you fly, glide with an elytra, swim, lie in a bed or fall 4 blocks, and false after.
- [ ] With `pvp.provider: none` in integrations.yml and a restart, `combat-tagged` falls back to recent player damage: true for 15 seconds after a hit.

## 6. Shop

- [ ] `/hc shop` opens on the highest tier the player has unlocked (Common for a new player). Other tiers can be browsed with the arrows and show "Locked" with the needed score; `/hc shop rare` opens one directly.
- [ ] With 0 points, an entry shows "too expensive". `/hca points A give 100`: entries become buyable.
- [ ] Buy Speed: speed for 30 minutes, points go down, "left this cycle" drops. Buy it 5 times: it shows the limit reached.
- [ ] An entry at or above 50 points asks for confirmation first. Cancel works; confirm buys it once even with rapid clicks.
- [ ] The Rare diamond block has stock 10: after 10 purchases (across players) it shows sold out.
- [ ] Legendary "Title: Legend" needs the Collector title: unavailable until Collector is bought.
- [ ] No menu item can be taken out of the shop, titles, history or confirmation menus by any click, drag, number key or double click.

## 7. End of cycle and outcomes

Set up A with a positive score and B with a negative one: put B's heads in A's vault through trades or kills, and keep B's vault poor. `/hca cycle simulate` shows the expected scores and outcomes first.

- [ ] `/hca cycle end confirm`: everyone online sees the results broadcast with the top 3. A sees a report (unique, score, points, streak, tier, outcomes), and B sees the penalty message.
- [ ] Vaults are refilled with 27 new-cycle heads. Heads from last cycle in inventories now say "expired", and the vault refuses them.
- [ ] A's points equal the positive score (+5 if top 3). B has the penalty's slowness. `/hc effects` lists it with time left; it counts only online time.
- [ ] A player offline at the end gets their report and any instant outcome actions on their next join.
- [ ] `/hc history` shows the cycle with the right numbers; `/hca history B` works for admins.
- [ ] `/hc top last` lists last cycle's top players.
- [ ] A score of -10 or lower: dunce cap locked on the head (it can't be removed, and it comes back after death), glowing, a bounty announced, and location broadcasts every 30 minutes.

## 8. Timed actions across restarts

- [ ] Give A a timed potion (`/hca run A punish-mild`). Restart the server: after rejoining, the effect is still listed and still applied. Drinking milk removes it, then it comes back within a second.
- [ ] Give an offline player a timed action: it applies on their join. Let a real-time action expire while they're offline: it is undone on their join.
- [ ] `/hca effects A remove <id>` ends an effect immediately and undoes it.

## 9. Hooks

- [ ] `/hca cycle extend` or `setend` to 2 minutes from now: the 1-minute warning shows a title and a countdown boss bar, which also appears for a player joining during that minute. The cycle then ends by itself.
- [ ] `/hca hook first-join A` and `/hca hook first-join-cycle A` show those messages again.
- [ ] Join order: with a pending end-of-cycle report, rejoin and check it comes before the first-join-cycle message.
- [ ] With the end set a little over an hour away, the 1-hour and 10-minute warnings show in chat. Only the 1-minute warning has been seen live so far.

## 10. Sidebar, titles, placeholders

- [ ] `/hc sidebar` shows the sidebar (cycle, unique, score, points, streak, time left) and updates every second. `/hc sidebar` again hides it.
- [ ] The sidebar on Bedrock (C) shows the same lines.
- [ ] `/hca title A grant champion`: it auto-equips. `/hc titles` shows it equipped; unequip and re-equip work.
- [ ] `/papi parse A %headcount_title% %headcount_points% %headcount_unique% %headcount_time_left%` shows the right values.
- [ ] With `titles.yml permission-prefix.enabled: true` and `/hca reload`, LuckPerms shows the title as A's prefix (`lp user A meta info`).

## 11. Bedrock (C)

- [ ] C signs in with a real Xbox account through Floodgate, and Headcount treats C as a Bedrock player (`/hca status C` shows `bedrock-player` true).
- [ ] The vault, shop, titles, history and confirmation menus and the sidebar look right on C's screen.
- [ ] C's vault opens and heads can be moved in and out.
- [ ] C can trade with A using `/trade accept`. Items and heads move correctly and nothing can be taken from A's side.
- [ ] C's shop, titles, history and confirmation menus work and can't be looted.
- [ ] C's own heads (in A's vault after a trade or kill) count as unique heads, even if their skin shows as Steve or Alex.

## 12. Admin

- [ ] `/hca vault B` opens B's vault while B is offline; moving a head is saved (B sees it on join). With B online, both see changes live.
- [ ] `/hca giveheads A 3 B current vault` puts 3 of B's heads in A's vault. `... next` queues them for next cycle's vault.
- [ ] `/hca reset A vault confirm` empties A's vault; other reset options do what they say.
- [ ] `/hca audit A`, `/hca audit <head id>`, and `/hca audit A type:trade_complete` find the right lines.

## 13. Other plugins

The test server has no Discord bot token and none of the paid plugins, so these are checked on a server that has them.

- [ ] DiscordSRV, connected to a Discord bot: a `discord` action (for example in a test list run with `/hca run A <list>`) posts its text in the configured channel.
- [ ] ItemsAdder, Oraxen or Nexo: a shop entry or `give-item` action with `custom: itemsadder:ns:id` (or `oraxen:id`, `nexo:id`) gives the custom item.
- [ ] LiteBans with `moderation.provider: litebans`: warn, mute, kick and a timed ban reach LiteBans, and a status with `source: muted` reads true while LiteBans has the player muted.
