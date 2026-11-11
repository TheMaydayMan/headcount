# Headcount test night

This plan is a normal evening of Minecraft with Headcount running underneath it. By the end, every command, menu, config section and integration, and Headcount Studio, has been used at least once, along with the edge cases. You (the **host**) run the server and the admin side. Everyone else just plays and follows the short "**Everyone:**" prompts.

Tick boxes as you go, and write down anything odd under **Notes** at the end of each part. That text goes straight into `1.1 Notes.md`. For anything strange, grab:
- the console lines from `TestServer/logs/latest.log`;
- `/hca audit <player>` and `/hca status <player>`;
- a screenshot of the Studio page, if Studio was involved.

---

## How to read this plan

- **N** = the number of players, you included (2–10). Values that depend on N are given as a small formula, or as a pick of **[2–3] / [4–6] / [7–10]**.
- **Host** = you, as op. **P1, P2, …** = the other players, in join order. **Bedrock** = anyone on a phone or console through Geyser (port 19132). Optional, but if you have one, use them wherever it says "(Bedrock if you have one)".
- Commands starting with `/` are typed in game. Lines marked *console* are typed in the server terminal, or in Studio's **Console** page.
- **Studio** marks a step done in Headcount Studio. Keep it open on a second screen or a laptop all night. It does the host's bookkeeping, so you can keep playing:
  - config edits, with a diff before every save;
  - the cycle end, using one-click "End in 10m/30m/1h" buttons;
  - who has what, and the audit trail.

  Each Studio step also tests Studio. Where a step gives both a command and a Studio way, use whichever is faster for you; the plan says when they must agree.
- Each part says roughly how long it takes. The whole night is about **3½–4 hours**: three short cycles of about 40–50 minutes, plus setup and a wrap-up.
- Restarting: type `restart` in the console (or Studio's Console). The server comes back in the same terminal, and everyone just rejoins.

### How many players is best

**5–6 is the sweet spot. 4–8 works well with Studio running. 9–10 is the first size that is really worse.**

- **Scores mean something from 5 up.** With N ≤ 4 the formula subtracts only 1, so a player who never trades scores 0, which brings no reward and no penalty. At 5–8 that player scores −1, so penalties happen naturally and the hoarder role matters.
- **Top 3 is a real cut from 4 up.** With 2–3 players, everyone with a positive score is "top three" and gets the +5 points and the Champion title, so the reward never looks different from the default.
- **Trading and PvP shapes:** 5 gives two trade pairs plus one ring of three, and teams of 2 v 3. 6 gives two clean teams of 3.
- **Shared limits get tested:** the diamonds' stock of 10, bounties with more than one hunter, and the leaderboard ranking all need several people.

**Is more than 6 worse?** Not much at 7–8. It gets clearly worse at 9–10.
- **7–8** score exactly like 5–6 (`ceil(N/4)` = 2). More players also spread the trade-cancel and moderation cases thinner, which is good. What it costs is host attention. Without Studio, the host spends the night typing `/hca` commands and reading chat. With the Studio dashboard, leaderboard and cycle buttons, that load mostly goes away. Let a second op watch chat and the console anyway.
- **9–10** is a real step down:
  - The threshold jumps: a positive score needs **4** unique heads, and the non-trader scores −2. In 40-minute cycles, some players won't get there.
  - "Everyone does X" steps take twice as long, and the clicks and messages to check pile up faster than one host can confirm them.
  - Chat floods, so raw tags and wrong messages get missed.

  If you have 9–10, make the cycles 50–60 minutes, and split into two groups of trade partners that each have a checker (you, and a second op on Studio).
- **With 2–3 players** the plan still covers everything. Use `/hca run <player> test-minus-10` (and `/hca points ... give`) for the score cases that don't happen on their own.

### Numbers that depend on N

The default score is `unique - ceil(active / 4)`. Your own head counts as 1 unique head.

| N | ceil(N/4) | Unique heads for a **positive** score | Score of someone who never trades or kills |
|---|---|---|---|
| 2–4 | 1 | 2 | 0 (no reward, no penalty) |
| 5–8 | 2 | 3 | -1 (minor penalty) |
| 9–10 | 3 | 4 | -2 (minor penalty) |

- **Teams** (for the PvP parts): `T = ceil(N / 3)` teams, players shared out as evenly as you can. With N = 2 it's 1 v 1.
- **"Hoarder"**: one player per cycle who never trades or kills, so there is always a low score to test. With N = 2, the hoarder is the host in cycle 2.
- **Trade partners**: pair people up. With an odd N, one group of three trades in a ring (A→B→C→A).

---

## Part 0: Host prep, alone (about 45 min, before anyone joins)

### 0.1 Server, RCON and Studio

- [ ] **Remove PvPManager for tonight.** Move `TestServer/plugins/PvPManager.jar` (or the `PvPManager-*.jar`) into a folder outside `plugins/`. Headcount's own PvP is the 1.1 default, and PvPManager would enforce its own rules next to it. (Part 4 puts it back for one test, if you want.)
- [ ] Rebuild if needed (`cd Headcount && ./gradlew build`), then `./start.sh`. The console shows `Ready. Cycle N ends ...` and no Headcount warnings. This start also turns RCON on.
- [ ] *console* `hca selftest`: every check passes.
- [ ] *console* `hca integrations`:
  - PvP = included
  - placeholders = included
  - money = Vault
  - permissions = included
  - moderation = included
  - player states = included
  - Discord = DiscordSRV
  - Bedrock = Floodgate
  - custom items: none installed
- [ ] **Studio:** double-click `Headcount Studio/Headcount Studio.desktop`. If it asks, right-click it and choose **Allow Launching**.
  - [ ] It opens in the browser on **Overview**, which describes the setup in plain words and shows no problems.
  - [ ] The **live** and **RCON** lights in the top bar are on.
- [ ] **Studio → Console:** `list` shows the server's answer. `hca selftest` gives the same result as in the terminal.
- [ ] **Studio → Dashboard:** the cycle countdown matches `/hca cycle info`.
- [ ] Join as yourself. *console* `op <you>` if needed.
- [ ] **Studio quick checks**, 2 minutes:
  - [ ] **Ctrl+K**, type `score-colors`: it jumps to that option.
  - [ ] **Ctrl+K**, type your name: your player page opens, with your vault as a chest.
  - [ ] The theme button switches light and dark.

### 0.2 The resets

- [ ] **Hard reset permission:** `/lp user <you> permission set headcount.admin.reset.hard false`, then `/hca reset hard`: "no permission". Unset it again (`/lp user <you> permission unset headcount.admin.reset.hard`). As op you now have it.
- [ ] **Wrong code:** `/hca reset hard`, then type a different 4-digit number: "Wrong code ... cancelled".
- [ ] **Expired code:**
  1. **Studio → Resets & safety:** set the confirm timeout to `5s`.
  2. **Review & save:** the diff shows exactly one changed line. Save, and let Studio run `/hca reload` when it asks.
  3. Run `/hca reset hard`, wait 6 seconds, and type the code: "expired".
  4. **Studio:** Ctrl+Z undoes the timeout change. Save and reload again.
- [ ] **Confirmation count:** in Studio, set confirmations to `2`, then save and reload. The flow now asks twice, and the second time says "Last confirmation". Set it back to 3.
- [ ] **Studio → Backups:** the saves above each left a backup. Compare one with the current file: the diff is that one line.
- [ ] **Real hard reset, keeping heads, from Studio's Console.** First take 2 heads out of your vault into your inventory. Then type `hca reset hard keep-heads` in **Studio → Console**: Studio asks first. The codes appear in the console, so type `hca reset hard <code>` three times. Check:
  - [ ] The warning listed counts (players, cycles, heads, points...) and said "kept as defunct heads".
  - [ ] You get the first-join welcome again, a new vault with 27 heads, and `/hca cycle info` says cycle 1 with a new run ID.
  - [ ] The 2 heads in your inventory now say **(defunct)**, the cycle number on them is gray, and the vault refuses them ("defunct").
  - [ ] `plugins/Headcount/backups/hard-reset-<time>/` holds `headcount.db` and an `audit/` folder. The live `audit/` folder only has new lines, and **Studio → Audit log** shows only new events.
  - [ ] You are a "new player" again, so newbie protection runs for 10 minutes: `/hca status <you>` shows `newbie-protected`.
- [ ] **Hard reset, deleting heads:** take 2 heads out again and put 1 in a chest. Then `/hca reset hard delete-heads` in game, with the 3 codes.
  - [ ] The heads in your inventory vanish.
  - [ ] Open the chest: that head vanishes too.
  - [ ] `/hca audit type:head_delete` shows `reason=reset`.
  - [ ] **Studio → Audit log:** click one of those head IDs and trace it. The **head tracer** shows it was made at the earlier cycle start and deleted after the reset.
- [ ] If you still have a defunct head from the keep-heads reset, throw it on the ground. It shouldn't be deleted, since that reset kept them.

### 0.3 Config for a short night (Studio)

Do these in Studio's **Set up** pages, then save everything at once with **Review & save** (check the diff), and let Studio run `/hca reload` and `/hca selftest`.

- [ ] **Cycle & schedule → time zone:** try each of these. After each one, save and reload, then compare `/hca cycle info` (the zone and end time) with Studio's timeline.
  - [ ] `MST` (shows UTC-07:00)
  - [ ] `gmt-7`
  - [ ] `UTC+5:30`
  - [ ] `america/denver`
  - [ ] `nonsense`: Studio's Problems page flags it, and the console warns and uses UTC

  Finish on `America/Denver`. Studio's "End in 10m" buttons assume the server's zone matches your computer's, so don't use them while the zone is something else.
- [ ] **Cycle & schedule:** `activity.min-playtime: 5m`, so "active" means something tonight.
- [ ] **Vault & heads:**
  - [ ] **Title:** make sure it contains `<score>`, e.g. `"<dark_gray><owner>'s Head Vault <gray>(<unique> unique, score <score>)"`. Studio's preview shows a colored sample score. Try `<score:aqua:dark_red:yellow>` and keep whichever you like.
  - [ ] **Head lore:** make sure the cycle number is gold on current heads and gray on expired and defunct ones (the 1.1 defaults). The tooltip preview shows all three.
- [ ] **Integrations:** these choices are for tonight's tests:
  - [ ] `placeholders.provider: placeholderapi`, so `/papi parse` and other plugins see Headcount's placeholders.
  - [ ] `player-state.included.afk.after: 1m`, so AFK is quick to test.
  - [ ] `moderation.included-jail.location`: pick a spot for a little "jail" (read its coordinates from F3) and enter `world,<x>,<y>,<z>`.
  - [ ] Look at the **PvP message previews**. They show how each PvP message will look in chat, the action bar or a title.
- [ ] **Hooks & actions → action lists:** add these two lists with the action builder. Add them as cards, not YAML, to test the builder.
  - [ ] `test-minus-10`:
    - a `score-bonus` of `-10`, duration `cycle`
    - a `message`: `<red>Test: -10 score this cycle`
  - [ ] `test-mod`:
    - a `mute`, reason `Test mute`, duration `1m`
  - [ ] Open **Raw files → actions.yml**: the lists are there, and the comments around them are untouched.
- [ ] **Review & save** shows every change above across the files. Save, reload, and `/hca selftest`: all pass.
- [ ] Start the night's first cycle fresh: `/hca reset cycle delete-heads confirm`. Then **Studio → Cycle tools → End in 1h** (or `/hca cycle setend <today>T<now + 50 min>`), and check it with `/hca cycle info`.

**Notes:**

---

## Part 1: Arrivals (about 15 min)

**Everyone:** join one at a time. Bedrock players join last. **Studio → Dashboard:** each one appears in the online list.

- [ ] Each new player gets two lines, in this order, about 1 second after joining:
  1. the **first-join** welcome
  2. the **first-join-cycle** line (cycle number, time left, points)
- [ ] **Everyone:** `/hc help`: only player commands are listed, including `/pvp`. Ops also see `/hca help`, which lists both resets.
- [ ] **Everyone:** `/hc`: the vault opens with 27 of your own heads. The title shows unique 1 and a score, colored by sign (see the table above). Bedrock: the same chest menu works.
- [ ] **Everyone:** `/pvp status`: PvP is **off**, and new players are protected for 10 minutes.
- [ ] **Everyone:** `/hc sidebar`: the sidebar shows cycle, unique, score, points, streak and time left, and ticks down. `/hc sidebar` again hides it. Leave it on.
- [ ] **Everyone:** check that each of these looks right:
  - [ ] `/hc points`
  - [ ] `/hc score`: the "You are active / not active yet" part is colored, with no raw tags
  - [ ] `/hc top`
  - [ ] `/hc history`: empty
  - [ ] `/hc effects`: none
  - [ ] `/hc titles`: the menu, with locked titles
- [ ] **Host:** `/hca placeholders P1`: all the `%headcount_*%` values look right. `/papi parse P1 %headcount_unique% %headcount_time_left% %headcount_title%` works (placeholderapi is the provider tonight).
- [ ] **Host:** `/hca status P1`: shows every status. `in-pvp` is false, and `newbie-protected` is true.
- [ ] **Studio → Players → P1:** their vault chest matches what P1 sees in game.
- [ ] Look for raw tags (`<cycle>`, `<green>`, ...) in any message so far. Note each one with where it appeared.

**Notes:**

---

## Part 2: Cycle 1, "Settling in" (about 45 min)

Normal survival: build a little base, gather, chat. Tasks get dropped into the session as it goes.

### 2.1 The vault, hands-on (everyone, 5 min)

**Everyone**, in your vault:

- [ ] Click a head out, then click it back. The title's counts update live, without closing the menu.
- [ ] Shift-click a head out, then back.
- [ ] Number keys 1–9: move a head to the hotbar and back.
- [ ] F (offhand) on a vault slot.
- [ ] Q (drop) on a vault slot: blocked, because `allow-drop-key` is false.
- [ ] Double-click to collect.
- [ ] Drag dirt over vault slots, and try to put dirt, a vanilla head (`/give @s player_head`, ops) or a sword in: all refused, each with its own message.
- [ ] One player turns PvP on (`/pvp on`): their new-player protection ends with a message. They try to move a head: blocked ("in-pvp"), with a message. `/pvp off` right away: "wait Ns" (10s cooldown). Wait, then turn it off.
- [ ] **Studio → Why is it blocked?**, for that player while their PvP was on: it names `in-pvp` and the vault rule.
- [ ] **Host:** `/hca vault P1` while P1 has their vault open. You both see each other's moves live, and the title updates for both. `/hca vault P1 view` is view-only.

### 2.2 Trades (pairs, 10 min)

Pair up using the trade partners rule.

- [ ] `/trade <partner>`: the partner gets a clickable [Accept]/[Deny] (Bedrock players get the typed `/trade accept` text). Accept.
- [ ] Both put heads and items on the left; they show mirrored on the right for the other player. Try to take from the other side by click, shift-click, number key and double-click: impossible.
- [ ] Both confirm, then one changes their offer: both confirmations clear.
- [ ] Both confirm: a 3-second countdown. Clicking confirm again stops it. Let it finish: items are swapped.
- [ ] Put received heads in your vault: the title's unique count goes up live, and the score changes color once it turns positive.
- [ ] Trading again within 10 seconds: refused (cooldown).
- [ ] **Cancel cases.** Spread these across pairs; each must return everything to its owner:
  - [ ] Esc
  - [ ] the cancel button
  - [ ] disconnecting
  - [ ] *console* `kick P2`
  - [ ] dying (`/kill` inside a trade)
  - [ ] `/tp` either player
  - [ ] a nether portal
  - [ ] `/gamemode creative`
  - [ ] getting hit by someone else (combat tag; both need PvP on)
  - [ ] `/pvp on`
  - [ ] `/hca trade cancel P1`
  - [ ] `/hca reload` (host)
- [ ] Request rules:
  - [ ] trading yourself
  - [ ] an offline name
  - [ ] a `/vanish`ed player (host vanishes)
  - [ ] someone already trading
  - [ ] a request left alone for 30 seconds expires, and both are told
- [ ] **Host:** `/hca audit P1 type:trade_complete` lists the trade with head IDs. **Studio → Audit log**, filtered to P1, shows the same lines. Tracing one traded head shows: made, moved out of the vault, traded, put in the new owner's vault.

### 2.3 PvP and kills (teams, 15 min)

Split into T teams and go somewhere flat. **Everyone:** `/pvp on`, except one player who keeps it off for the first two checks.

**Blocked hits and protection:**
- [ ] Hitting the player with PvP off: no damage, and an action bar line "<name> has PvP off". They can't hit back either ("Your PvP is off").
- [ ] Shooting them with an arrow, or splashing a harming potion on them: no damage either. That player now turns PvP on.
- [ ] Kill someone. Right after they respawn, hit them again: blocked for 5 seconds ("just respawned").

**Combat tag** (it lasts 15 seconds after each hit):
- [ ] After a hit, both players get "In combat with <name>!" in the action bar with a sound, then a live countdown. When it ends: "no longer in combat".
- [ ] While tagged, each of these is refused with a message and the seconds left:
  - [ ] `/spawn`, `/home`, `/tpa <someone>`
  - [ ] `/essentials:home` (the namespaced form)
  - [ ] `/pvp off`
- [ ] **Host:** `/fly <player>` on someone, then hit them: their flight stops, and they can't start flying again until the tag ends.
- [ ] **Host:** `/pvp P1` shows P1's PvP and whether they're in combat. `/pvp P1 off` switches it for them.

**Combat log:**
- [ ] A tagged player quits the game mid-fight. They die where they stood (items drop), the last attacker gets a fresh head of theirs, and everyone else reads "<name> logged out during a fight". When they rejoin, they respawn as after any death.
- [ ] *console* `kick <tagged player>`: a kick doesn't count, so nothing happens to them.

**Kill heads:**
- [ ] A kill drops a fresh head of the victim at their body. The killer picks it up; it counts as a current head.
- [ ] The same killer kills the same victim again: no head ("already took").
- [ ] A bow kill and a knock-into-lava kill both count (use different victims).
- [ ] `/kill` and falling to your death on your own: no head.
- [ ] *console* `gamerule keepInventory true`: a kill still drops the head and the victim keeps their items. Set it back to false.
- [ ] **Everyone:** `/pvp off` afterwards (after their tag ends). `/hca status <player>` shows `combat-tagged` turning off after the tag ends.

### 2.4 Placed heads and duplicates (5 min)

- [ ] Someone places one of their heads as a block. Then:
  - [ ] break it by hand
  - [ ] blow it up with TNT
  - [ ] push it with a piston
  - [ ] wash it out with water

  Each time the same economy head drops (same ID in `/hca inspect`). **Studio's head tracer** shows each place and break.
- [ ] **Host**, in creative: middle-click clone a head from your inventory, then close the inventory. Ops with `headcount.admin.notify` get a "duplicate head" alert. The copy is refused by the vault ("copy").
- [ ] `/hca inspect` with a head in hand: owner, cycle, **run ID**, current: yes.

### 2.5 Statuses on the way (spread through the session)

Check `/hca status <player>` while players are:

- [ ] in a bed
- [ ] on fire
- [ ] in lava
- [ ] at low health
- [ ] **AFK**: stand completely still for 1 minute. "You are now AFK" arrives, and `afk` reads true. Moving or chatting gives "no longer AFK". (`/afk` is EssentialsX's command and doesn't count while `player-state` is `included`.)
- [ ] `/vanish` (EssentialsX sets the metadata Headcount reads)
- [ ] in creative
- [ ] falling far
- [ ] on Bedrock

**Host:** `/papi parse P1 %headcount_status_in-pvp%` and `%headcount_status_afk%`.

### 2.6 End of cycle 1 (with warnings)

About 15 minutes before the end, give the hoarder a reason to stay poor (they don't trade or kill).

- [ ] Check the activity rule: anyone who has played under 5 minutes is inactive (`/hc score` says not active yet). Make sure everyone has played longer.
- [ ] **Host:** `/hca cycle simulate`: lists every score and the outcome each would get. Compare it with the N table. **Studio → Leaderboard** must show the same scores and outcomes.
- [ ] **Studio → Cycle tools:** set the end 12 minutes from now (or `/hca cycle setend`). The dashboard countdown follows it.
- [ ] The **10-minute** warning arrives as chat.
- [ ] At **1 minute**: a title, plus a **boss bar** reading "Cycle 1 ends in 0:59" with the real number (no raw `<cycle>`).
- [ ] A player rejoining in the last minute also gets the boss bar.
- [ ] At the end:
  - [ ] everyone sees the results broadcast with the top 3
  - [ ] each player gets a personal report: unique, score, points, streak text in color, tier, outcomes
  - [ ] vaults are refilled with 27 new heads
  - [ ] cycle 2 starts
- [ ] Heads left in inventories now say **expired**: the cycle number is gray and the vault refuses them.
- [ ] Points: positive score = that many points, **+5 for the top 3** (the `reward-top` list). The hoarder (if negative) gets "Minor penalty" slowness; `/hc effects` shows it with time left (counts online time only).
- [ ] `/hc history`, `/hc top last`, `/hca history <player>` and **Studio → History** all match.
- [ ] Have one player log out just before the end. When they rejoin: their report and outcome actions arrive, before the first-join-cycle line.

**Notes:**

---

## Part 3: Cycle 2, "Shopping and politics" (about 50 min)

**Host:** set the end 50 minutes from now (Studio's Cycle tools, or `/hca cycle setend`).

### 3.1 Shop (10 min)

Players with points from cycle 1 shop. The others get some from the host: `/hca points P3 give 20`, or the **Give points** button on their Studio player page. Give a few players enough for higher tiers.

- [ ] `/hc shop` opens on the highest tier you unlocked (from last cycle's score):
  - Common: always
  - Uncommon: 5+
  - Rare: 10+
  - Legendary: 20+

  Browse to a locked tier: entries show **Locked** and the score needed. `/hc shop rare` opens it directly.
- [ ] With no points, an entry shows "too expensive". After `/hca points <p> give 100`, entries become buyable.
- [ ] Buy each of these at least once across the group:
  - [ ] Steak (2)
  - [ ] Speed (3; buy 5 times: "limit reached")
  - [ ] Extra heads (5)
  - [ ] Shield (8)
  - [ ] Haste (6)
  - [ ] Head protection (10)
  - [ ] Bigger vault (12)
  - [ ] Mystery box (7)
  - [ ] Title: Collector (20)
  - [ ] Rollover (15)
  - [ ] Diamonds (25; stock 10 shared by everyone)
  - [ ] Title: Legend (60; needs Collector)
  - [ ] Flight (40; 1-day cooldown)
  - [ ] Next-cycle heads (50)
- [ ] Anything priced 50 or more asks for confirmation. Cancel works. Spam-clicking confirm buys it exactly once.
- [ ] No menu item can be taken out of the shop, titles, history or confirmation menus by any click, drag, number key or double-click.
- [ ] Bedrock: the shop and confirmations work.
- [ ] **Studio → Shop:** the chest grid matches the in-game shop, page by page. Each buyer's Studio player page lists their purchases.

### 3.2 Titles, permissions and prefixes (5 min)

- [ ] **Host:** `/hca title P1 grant champion`: it auto-equips. `/hc titles` shows it equipped; unequip and re-equip work.
- [ ] **Prefixes need LuckPerms.** With `permissions.provider: included` and `permission-prefix.enabled: true` in titles.yml, no prefix appears, and the console warns that prefixes need LuckPerms or Vault. Then in **Studio → Integrations** set `permissions.provider: luckperms`, save, and reload: the title shows as a prefix in chat and in `/lp user P1 meta info`. Turn `permission-prefix` off and reload: the prefix goes away.
- [ ] `%headcount_title%` in `/papi parse`.
- [ ] **Included permissions:** set `permissions.provider` back to `included` and reload. Add a list with the action builder:
  - a `permission` action for `headcount.bypass.trade.cooldown`, duration `10m`

  Then:
  - [ ] Run it on P2: P2 can now trade twice in a row with no cooldown.
  - [ ] P2 rejoins: they still can, because the node is re-applied on join.
  - [ ] Remove it from P2's Studio effects list: the cooldown is back.

### 3.3 Actions showcase (host, 10 min)

**Host:** `/hca run <player> <list>` for each named list on different players, while everyone watches:

- [ ] `celebrate`
- [ ] `dunce-cap`: a pumpkin locked on the head. It can't be removed and comes back after death.
- [ ] `punish-mild`, `punish-medium`, `punish-severe`
- [ ] `reward-top`
- [ ] `mystery-box`

Then:

- [ ] `/hc effects` on those players lists each timed effect with time left, in readable words. `/hca effects P2` shows the same with IDs. `/hca effects P2 remove <id>` ends one at once; `remove all` clears them.
- [ ] **Studio → Effects:** lists every running effect for everyone. Its ✕ button removes one (it asks first).
- [ ] `/hca hook first-join P1`, `/hca hook first-join-cycle P1` and `/hca hook warning:1m` replay those hooks.
- [ ] **Moderation, included** (Headcount's own), on a willing player (pick the one who'll be the most fun about it):
  - [ ] `/hca run P test-mod`: a 1-minute mute. Chat and `/msg` are blocked, and `/hca status` shows `muted`. It lifts itself.
  - [ ] a `warn` (a message), and a `kick`
  - [ ] a `jail` with a 2-minute duration:
    - they are moved to the jail location;
    - walking more than 8 blocks away pulls them back;
    - `/spawn` is blocked but `/msg` works;
    - on release they go back to where they were.
  - [ ] a short timed `ban`: they're on the vanilla ban list (`banlist`), and it lifts itself.
- [ ] **One kind by a plugin:** in Studio set `moderation.kinds: { ban: libertybans }` and reload. A timed ban now goes through LibertyBans (it's in `/libertybans banlist`, not the vanilla `banlist`), and lifts on time. Remove the kind afterwards.
- [ ] Money (EssentialsX economy): a `money` action changes `/balance`.

### 3.4 Restart survival (5 min)

- [ ] With timed effects running (slowness, dunce cap...), type `restart` in the console. The server comes back in the same terminal. Studio's lights go off, then come back on once the server is up (reload the Studio tab if they don't).
- [ ] Everyone rejoins: `/hc effects` still lists everything and the effects are still applied. Milk removes a potion, which comes back within a second.
- [ ] Have someone hold a trade offer open during `restart`: after rejoining, their items come back exactly once.
- [ ] Everyone's PvP choice survived the restart (`/pvp status`).
- [ ] **Host:** give an offline player something (`/hca giveheads P4 2` while P4 is offline). On their next join, the items are delivered.

### 3.5 Reset cycle, mid-cycle (5 min)

Warn everyone first: their vaults get refilled.

- [ ] Have two players each take 2 heads out of their vault: one keeps them in their inventory, one puts them in a chest.
- [ ] **Host:** `/hca reset cycle keep-heads`: a confirm message mentions "kept as defunct heads". Then run `/hca reset cycle keep-heads confirm` (or Studio's **Reset cycle (keep heads)** button).
  - [ ] Everyone gets the "Cycle 2 was reset" message, a refilled vault and the first-join-cycle line again.
  - [ ] The cycle number is still 2. `/hca cycle info` and the Studio dashboard show a new run ID and an end time 30 days out (shorten it again with `setend`).
  - [ ] The taken-out heads are **(defunct)**, gray, and refused by the vault, while points, titles and effects are unchanged.
  - [ ] Shop "this cycle" limits reset (Speed can be bought 5 more times); kill claims reset (the same killer and victim drop a head again).
- [ ] Take heads out again, then `/hca reset cycle delete-heads confirm`. The heads in inventories vanish at once. Opening the chest removes the ones there, including any inside a shulker box or bundle. A player who was offline loses theirs when they join.
- [ ] `/hca audit type:head_delete` lists each deletion with `reason=reset` and where it happened.
- [ ] Shorten the cycle again: end in 25 minutes.

### 3.6 Bounties, protection, shields (10 min)

Back to PvP for a round. Everyone `/pvp on`.

- [ ] A player who bought **head protection** is killed: no head drops ("protected").
- [ ] A player who bought **extra heads** is killed: more heads drop than usual.
- [ ] **Host:** `/hca run <player> punish-severe`, which includes a bounty. It is announced; the killer gets the points and the claim is broadcast.
- [ ] The location broadcasts from the severe punishment repeat while it lasts.
- [ ] A **pvp lock**: run a list with `pvp: on`, `lock: true`, duration `5m` on one player. `/pvp off` says it's locked; `/hc effects` describes it as "PvP forced on (locked)".

### 3.7 End of cycle 2, the punishment test

- [ ] **Host:** `/hca run <hoarder> test-minus-10`, so their score is −10 or lower. `/hca cycle simulate` and Studio's Leaderboard both confirm it.
- [ ] One player who bought a **shield** should also be negative. Check the simulate output.
- [ ] `/hca cycle end confirm` (or Studio's **End now**, which asks first):
  - [ ] The −10 player gets "Severe penalty": the dunce cap, glowing and location broadcasts.
  - [ ] The shielded player's penalty is blocked ("shield used").
  - [ ] Punishments with "cycle" length last through cycle 3.
- [ ] Streaks: someone positive twice shows a 2-cycle streak in green on the sidebar.
- [ ] Rollover: whoever bought Rollover kept that percentage of their unspent points.
- [ ] Bigger vault / next-cycle heads buyers start cycle 3 with the bigger vault or the extra heads. Extras that don't fit arrive as items. Their Studio vault chest shows the new size.

**Notes:**

---

## Part 4: Cycle 3, "Config chaos" (about 45 min)

**Host:** set the end 40 minutes from now. In this round the host changes the rules **in Studio** while everyone plays. After each change:
1. **Review & save** (check the diff is only what you meant).
2. Let Studio run `/hca reload`.
3. See whether the change shows up in game.

- [ ] **Scoring:** in the scoring lab, change the formula to `unique * 2 - ceil(active / 4)`. The lab's chart and sample scores change as you type. After saving, `/hc score` and the vault title score match the lab. Change it back.
- [ ] **Vault & heads → score colors:** set positive to `gold`. The vault title changes color.
- [ ] `vault.block-movement.block-opening: true`: a player with PvP on can't even open the vault. Set it back to false.
- [ ] **Trading** changes, each tried with one trade:
  - [ ] `range.blocks: 10`: players 20 blocks apart can't trade, and walking apart mid-trade cancels it
  - [ ] `limits.max-heads: 2`
  - [ ] `only-own-heads: true`
  - [ ] `items-for-items: false`

  Put them back afterwards with undo, then save: the diff is empty for trade.yml.
- [ ] `duplicates.remove-copies: true`: a creative clone is removed when the inventory closes.
- [ ] `heads.placed.creative-break: drop`: breaking a placed economy head in creative drops it.
- [ ] **Sidebar:** add a line with `%headcount_rank%`. The mockup shows it first, and in game it shows within a second.
- [ ] **Outcomes:** switch `mode` from `most-extreme` to `all`. The score line shows the overlaps; `/hca cycle simulate` lists several outcomes per player. Switch it back.
- [ ] **Shop:** drag an entry to another slot, change a price, and give an entry `stock: 1`. It shows up in the shop at once.
- [ ] **Messages:** change `vault.reset-head` or any message you've seen tonight. The preview matches the game.
- [ ] **Included PvP, live:**
  - [ ] `force-on-worlds: [world_nether]`: in the Nether, `/pvp off` says PvP is always on there.
  - [ ] `messages.countdown: bossbar`: the combat countdown becomes a red boss bar.
  - [ ] `messages.tag-start.channel: title`: getting tagged shows a title instead.
  - [ ] `block-ender-pearls: true`: pearls are refused while in combat.
  - [ ] `combat-log.actions`: add a `points` action of `-5`. The next combat logger loses 5 points (check their Studio player page).
- [ ] **AFK:** set `afk.announce: everyone`. Everyone sees "<name> is now AFK".
- [ ] **Placeholders off:** set `placeholders.provider: none`. The sidebar's `%headcount_rank%` line now shows as written. Set it back to `placeholderapi`.
- [ ] **A recipe:** in **Learn → Recipes**, apply one (for example "A punishment that locks PvP on"). Studio shows the diff first. Save and reload, then undo it.
- [ ] **A conflict:** with an unsaved Studio change to config.yml, edit `config.yml` in a text editor and save it. Studio's save warns that the file changed on disk, instead of overwriting it.
- [ ] **A mistake:** in **Raw files → shop.yml**, give one entry the same `slot` as another in its tier. The **Problems** page flags it before you save. Undo it. (Studio doesn't check materials; `/hca selftest` does. Type `DIAMOND_BLOK` in an entry, save and reload, and the selftest names it. Fix it.)
- [ ] Turn on `ban-repeat-offenders` in outcomes, with a 1-minute ban for the test.
- [ ] Bedrock check (if you have one): vault, trade with a Java player, shop, sidebar.
- [ ] **Optional, PvPManager:**
  1. Put `PvPManager.jar` back and set `pvp.provider: pvpmanager`.
  2. `restart`. `/pvp` is now PvPManager's, `/hca integrations` says so, and `in-pvp` follows PvPManager.
  3. Afterwards, remove it again, set `included`, and `restart`.

### End of cycle 3

- [ ] Let it end on schedule (no `cycle end`). Warnings arrive at their times and the boss bar counts down to the second.
- [ ] Everything from 2.6 holds again. History now shows 3 cycles, with streaks carried over.

**Notes:**

---

## Part 5: Wrap-up (about 15 min)

- [ ] **Host:** `/hca audit <player>`, `/hca audit <head id>`, `/hca audit <player> type:vault_in page:2`: all find the right lines. **Studio → Audit log** finds the same, including in the older `.gz` files.
- [ ] `/hca reset <player> vault|points|titles|streak|effects|history|all confirm` on one willing player each. Each does exactly what it says.
- [ ] `/hca flag P1 set test`, then `/hca status P1`: a flag status reads true (if you defined one). Clear it.
- [ ] Optional crash test: someone puts items in a trade offer, then *console* `kill -9` the server process (`pgrep -f paper`). Restart with `./start.sh`: the items come back exactly once.
- [ ] **Studio → Import & export:** export the configs as a .zip. That's tonight's setup, saved.
- [ ] Final `/hca reset hard keep-heads` (3 codes): back to a clean server for next time. The backup folder holds tonight's database and audit log.
- [ ] Put PvPManager back in `plugins/` only if you want it for normal play (and set `pvp.provider: pvpmanager`).
- [ ] Copy anything odd from the **Notes** into `1.1 Notes.md`.

### Things this plan can't cover

- DiscordSRV messages (no bot token).
- ItemsAdder, Oraxen, Nexo and LiteBans (paid plugins).
- Floodgate with a real Xbox sign-in, only if nobody joins from Bedrock.
- EssentialsX as the player-state provider (`/afk`, `/god`, Essentials jails): set `player-state.provider: essentials` some other time if you plan to use it.
