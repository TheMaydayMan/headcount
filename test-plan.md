# Headcount test night

Tonight we play a normal evening of Minecraft with Headcount running underneath. By the end, we'll have used every command, menu, config section and integration, plus Headcount Studio, and hit the edge cases too. You (the **host**) run the server and the admin side. Everyone else plays and follows the "**Everyone:**" prompts.

Tick boxes as you go, and write anything odd under **Notes** at the end of each part. Those notes go into `1.1 Notes.md`. When something looks wrong, save the console lines from `TestServer/logs/latest.log`, the output of `/hca audit <player>` and `/hca status <player>`, and a screenshot of the Studio page if Studio was involved.

---

## How to read this plan

**N** is the number of players, you included (2 to 10). Anything that depends on N is a small formula or a pick of **[2-3] / [4-6] / [7-10]**.

**Host** is you, as op. **P1, P2, ...** are the other players in join order. **Bedrock** is anyone on a phone or console through Geyser (port 19132). You don't need one, but if you have one, use them wherever it says "(Bedrock if you have one)".

Commands that start with `/` are typed in game. Lines marked *console* go in the server terminal or in Studio's **Console** page.

**Studio** marks a step done in Headcount Studio. Keep it open on a second screen or a laptop all night. It handles config edits (with a diff before every save), ending the cycle with its "End in 10m/30m/1h" buttons, and looking up who has what in the audit trail, so you can keep playing. When a step gives both a command and a Studio way, use whichever is faster. The plan says when the two have to agree.

Each part has a rough time. The whole night is about **3.5 to 4 hours**: three short cycles of 40 to 50 minutes, plus setup and wrap-up.

To restart, type `restart` in the console (or Studio's Console). The server comes back in the same terminal and everyone rejoins.

### How many players is best

5 or 6 is ideal. 4 to 8 works well with Studio running. 9 or 10 is where it really gets worse.

Scores only mean something from 5 players up. With 4 or fewer, the formula subtracts 1, so a player who never trades scores 0 and gets no reward or penalty. At 5 to 8 that player scores -1, so penalties happen on their own and the hoarder role matters.

The top 3 is only a real cut from 4 players up. With 2 or 3, everyone with a positive score is in the top three and gets the +5 points and the Champion title, so the reward never looks different from the default.

5 players gives two trade pairs plus a ring of three, and PvP teams of 2 against 3. 6 gives two even teams of 3. Several people are also needed for the diamonds' shared stock of 10, bounties with more than one hunter, and a leaderboard worth ranking.

7 or 8 scores exactly like 5 or 6 (`ceil(N/4)` is still 2), and the trade-cancel and moderation cases get spread over more people, which helps. The cost is the host's attention. Without Studio you'd spend the night typing `/hca` commands and reading chat. The Studio dashboard, leaderboard and cycle buttons take most of that away, but have a second op watch chat and the console anyway.

9 or 10 is a real step down. A positive score needs **4** unique heads and the non-trader scores -2, so in 40-minute cycles some players won't get there. "Everyone does X" steps take twice as long, there's more to confirm than one host can keep up with, and chat moves fast enough that raw tags and wrong messages get missed. If you have 9 or 10, make the cycles 50 to 60 minutes and split into two groups of trade partners, each with its own checker (you, and a second op on Studio).

With 2 or 3 players the plan still covers everything. Use `/hca run <player> test-minus-10` (and `/hca points ... give`) for the score cases that won't happen naturally.

### Numbers that depend on N

The default score is `unique - ceil(active / 4)`. Your own head counts as 1 unique head.

| N | ceil(N/4) | Unique heads for a **positive** score | Score of someone who never trades or kills |
|---|---|---|---|
| 2-4 | 1 | 2 | 0 (no reward, no penalty) |
| 5-8 | 2 | 3 | -1 (minor penalty) |
| 9-10 | 3 | 4 | -2 (minor penalty) |

- **Teams** (for PvP): `T = ceil(N / 3)` teams, split as evenly as you can. With N = 2 it's 1 v 1.
- **Hoarder**: one player per cycle who never trades or kills, so there's always a low score to test. With N = 2, the host is the hoarder in cycle 2.
- **Trade partners**: pair people up. With an odd N, one group of three trades in a ring: A gives to B, B to C, and C back to A.

---

## Part 0: Host prep, alone (about 45 min, before anyone joins)

### 0.1 Server, RCON and Studio

- [ ] **Take PvPManager out for tonight.** Move `TestServer/plugins/PvPManager.jar` (or `PvPManager-*.jar`) to a folder outside `plugins/`. Headcount's own PvP is the 1.1 default, and PvPManager would run its own rules on top of it. Part 4 puts it back for one optional test.
- [ ] Rebuild if needed (`cd Headcount && ./gradlew build`), then `./start.sh`. The console shows `Ready. Cycle N ends ...` with no Headcount warnings. This start also turns RCON on.
- [ ] *console* `hca selftest`: every check passes.
- [ ] *console* `hca integrations` shows:
  - PvP = included
  - placeholders = included
  - money = Vault
  - permissions = included
  - moderation = included
  - player states = included
  - Discord = DiscordSRV
  - Bedrock = Floodgate
  - custom items: none installed
- [ ] **Studio:** double-click `Headcount Studio/Headcount Studio.desktop`. If it won't launch, right-click it and choose **Allow Launching**.
  - [ ] It opens in the browser on **Overview**, which describes the setup in plain words and shows no problems.
  - [ ] The **live** and **RCON** lights in the top bar are on.
- [ ] **Studio → Console:** `list` shows the server's answer, and `hca selftest` matches the terminal.
- [ ] **Studio → This cycle:** the cycle countdown matches `/hca cycle info`.
- [ ] Join as yourself. *console* `op <you>` if needed.
- [ ] A couple of quick Studio checks:
  - [ ] **Ctrl+K**, type `score-colors`: it jumps to that option.
  - [ ] **Ctrl+K**, type your name: your player page opens, with your vault shown as a chest.
  - [ ] The theme button switches between light and dark.

### 0.2 The resets

- [ ] **Hard reset permission:** `/lp user <you> permission set headcount.admin.reset.hard false`, then `/hca reset hard`: "no permission". Unset it with `/lp user <you> permission unset headcount.admin.reset.hard`, and as op you have it again.
- [ ] **Wrong code:** `/hca reset hard`, then type a different 4-digit number: "Wrong code ... cancelled".
- [ ] **Expired code:**
  1. **Studio → General:** set the confirm timeout to `5s`.
  2. **Review and save:** the diff is exactly one changed line. Save, then press **Reload the server** in the message that appears.
  3. Run `/hca reset hard`, wait 6 seconds, then type the code: "expired".
  4. In Studio, Ctrl+Z undoes the timeout change. Save and reload again.
- [ ] **Confirmation count:** set confirmations to `2` in Studio, save and reload. The flow now asks twice, and the second time says "Last confirmation". Set it back to 3.
- [ ] **Studio → Backups:** each save above left a backup. Compare one with the current file, and the diff is that one line.
- [ ] **Real hard reset, keeping heads, from Studio's Console.** Take 2 heads out of your vault first. Then type `hca reset hard keep-heads` in **Studio → Console**, and Studio asks before sending it. The codes show up in the console, so type `hca reset hard <code>` three times. Then check:
  - [ ] The warning listed the counts (players, cycles, heads, points...) and said "kept as defunct heads".
  - [ ] You get the first-join welcome again and a new vault with 27 heads, and `/hca cycle info` says cycle 1 with a new run ID.
  - [ ] The 2 heads in your inventory now say **(defunct)** with a gray cycle number, and the vault refuses them ("defunct").
  - [ ] `plugins/Headcount/backups/hard-reset-<time>/` holds `headcount.db` and an `audit/` folder. The live `audit/` folder only has new lines, and **Studio → Audit log** shows only new events.
  - [ ] You count as a new player again, so newbie protection runs for 10 minutes: `/hca status <you>` shows `newbie-protected`.
- [ ] **Hard reset, deleting heads:** take 2 heads out again and put 1 in a chest. Then run `/hca reset hard delete-heads` in game, with the 3 codes.
  - [ ] The heads in your inventory vanish.
  - [ ] Open the chest, and that head vanishes too.
  - [ ] `/hca audit type:head_delete` shows `reason=reset`.
  - [ ] In **Studio → Audit log**, click one of those head IDs. The **head tracer** shows it was made at the earlier cycle start and deleted after the reset.
- [ ] If you still have a defunct head from the keep-heads reset, throw it on the ground. It should stay, since that reset kept them.

### 0.3 Config for a short night (Studio)

Make these changes in Studio's **Set up** pages. Save them all at once with **Review and save** (check the diff), then press **Reload the server** in the message that appears and run `/hca selftest`.

- [ ] **Cycle → time zone:** try each of these. After each one, save and reload, then compare the zone and end time in `/hca cycle info` with Studio's timeline.
  - [ ] `MST` (shows UTC-07:00)
  - [ ] `gmt-7`
  - [ ] `UTC+5:30`
  - [ ] `america/denver`
  - [ ] `nonsense`: Studio's Problems page flags it, and the console warns and falls back to UTC

  Finish on `America/Denver`. Studio's "End in 10m" buttons assume the server's zone matches your computer's, so don't use them while it's set to anything else.
- [ ] **Cycle:** `activity.min-playtime: 5m`, so "active" means something tonight.
- [ ] **Vault & heads:**
  - [ ] **Title:** make sure it contains `<score>`, for example `"<dark_gray><owner>'s Head Vault <gray>(<unique> unique, score <score>)"`. Studio's preview shows a sample score in color. Try `<score:aqua:dark_red:yellow>` and keep whichever you like.
  - [ ] **Head lore:** the cycle number should be gold on current heads and gray on expired and defunct ones (the 1.1 defaults). The tooltip preview shows all three.
- [ ] **Integrations**, set for tonight's tests:
  - [ ] `placeholders.provider: placeholderapi`, so `/papi parse` and other plugins can see Headcount's placeholders.
  - [ ] `player-state.included.afk.after: 1m`, so AFK is quick to test.
  - [ ] `moderation.included-jail.location`: pick a spot for a small jail, read its coordinates from F3, and enter `world,<x>,<y>,<z>`.
  - [ ] Look over the **PvP message previews**, which show how each PvP message will look in chat, the action bar or a title.
- [ ] **Hooks → action lists:** add these two lists as cards in the action builder, not as YAML, so the builder gets tested.
  - [ ] `test-minus-10`:
    - a `score-bonus` of `-10`, duration `cycle`
    - a `message`: `<red>Test: -10 score this cycle`
  - [ ] `test-mod`:
    - a `mute`, reason `Test mute`, duration `1m`
  - [ ] Open **Raw files → actions.yml**: the lists are there, and the comments around them are untouched.
- [ ] **Review and save** shows every change above, across all the files. Save, reload, and run `/hca selftest`: all pass.
- [ ] Start the first cycle fresh with `/hca reset cycle delete-heads confirm`. Then **Studio → This cycle → In an hour** (or `/hca cycle setend <today>T<now + 50 min>`), and check it with `/hca cycle info`.

**Notes:**

---

## Part 1: Arrivals (about 15 min)

**Everyone:** join one at a time, Bedrock players last. Each one shows up in the online list on **Studio → This cycle**.

- [ ] About a second after joining, each new player gets two lines, in this order:
  1. the **first-join** welcome
  2. the **first-join-cycle** line (cycle number, time left, points)
- [ ] **Everyone:** `/hc help` lists only player commands, including `/pvp`. Ops also see `/hca help`, which lists both resets.
- [ ] **Everyone:** `/hc` opens your vault with 27 of your own heads. The title shows 1 unique and a score colored by sign (see the table above). On Bedrock, the same chest menu works.
- [ ] **Everyone:** `/pvp status` says PvP is **off** and that new players are protected for 10 minutes.
- [ ] **Everyone:** `/hc sidebar` shows cycle, unique, score, points, streak and time left, and it ticks down. Run it again to hide it, then turn it back on and leave it.
- [ ] **Everyone:** check that each of these looks right:
  - [ ] `/hc points`
  - [ ] `/hc score`: the "You are active / not active yet" part is colored, with no raw tags
  - [ ] `/hc top`
  - [ ] `/hc history`: empty
  - [ ] `/hc effects`: none
  - [ ] `/hc titles`: the menu, with locked titles
- [ ] **Host:** `/hca placeholders P1` shows sensible values for every `%headcount_*%` placeholder. `/papi parse P1 %headcount_unique% %headcount_time_left% %headcount_title%` works too.
- [ ] **Host:** `/hca status P1` shows every status, with `in-pvp` false and `newbie-protected` true.
- [ ] **Studio → Players → P1:** their vault chest matches what P1 sees in game.
- [ ] Watch for raw tags (`<cycle>`, `<green>`, ...) in any message so far, and note where each one showed up.

**Notes:**

---

## Part 2: Cycle 1, "Settling in" (about 45 min)

Normal survival: build a small base, gather, chat. The tasks below get dropped in as the session goes.

### 2.1 The vault, hands-on (everyone, 5 min)

**Everyone**, in your vault:

- [ ] Click a head out, then click it back in. The counts in the title update without closing the menu.
- [ ] Shift-click a head out, then back.
- [ ] Use number keys 1-9 to move a head to the hotbar and back.
- [ ] Press F (offhand) on a vault slot.
- [ ] Press Q (drop) on a vault slot. It's blocked, because `allow-drop-key` is false.
- [ ] Double-click to collect.
- [ ] Drag dirt over vault slots, and try to put in dirt, a vanilla head (`/give @s player_head`, ops only) or a sword. Each is refused with its own message.
- [ ] One player turns PvP on with `/pvp on`, and gets a message that their new-player protection has ended. They try to move a head and it's blocked ("in-pvp"), with a message. They run `/pvp off` right away and get "wait Ns" (10s cooldown). Wait it out, then turn it off.
- [ ] **Studio → Why is it blocked?**, for that player while their PvP was on, names `in-pvp` and the vault rule.
- [ ] **Host:** `/hca vault P1` while P1 has their vault open. You both see each other's moves as they happen, and the title updates for both of you. `/hca vault P1 view` is view-only.

### 2.2 Trades (pairs, 10 min)

Pair up as trade partners.

- [ ] `/trade <partner>`. The partner gets a clickable [Accept]/[Deny] (Bedrock players get typed `/trade accept` text instead). Accept.
- [ ] Both put heads and items on the left, and the other player sees them mirrored on the right. Try to take from the other side by click, shift-click, number key and double-click. None of them work.
- [ ] Both confirm, then one changes their offer. Both confirmations clear.
- [ ] Both confirm again for a 3-second countdown. Clicking confirm again stops it. Let it finish, and the items swap.
- [ ] Put the heads you received in your vault. The unique count in the title goes up, and the score changes color once it turns positive.
- [ ] Trading again within 10 seconds is refused (cooldown).
- [ ] **Cancel cases.** Spread these across the pairs. Every one should return everything to its owner.
  - [ ] Esc
  - [ ] the cancel button
  - [ ] disconnecting
  - [ ] *console* `kick P2`
  - [ ] dying (`/kill` inside a trade)
  - [ ] `/tp` either player
  - [ ] a nether portal
  - [ ] `/gamemode creative`
  - [ ] getting hit by someone else (combat tag, both need PvP on)
  - [ ] `/pvp on`
  - [ ] `/hca trade cancel P1`
  - [ ] `/hca reload` (host)
- [ ] Requests that should be refused or expire:
  - [ ] trading yourself
  - [ ] an offline name
  - [ ] a `/vanish`ed player (the host vanishes)
  - [ ] someone already trading
  - [ ] a request left alone for 30 seconds expires, and both players are told
- [ ] **Host:** `/hca audit P1 type:trade_complete` lists the trade with head IDs, and **Studio → Audit log** filtered to P1 shows the same lines. Trace one traded head: it was made, moved out of the vault, traded, and put in the new owner's vault.

### 2.3 PvP and kills (teams, 15 min)

Split into T teams and find somewhere flat. **Everyone:** `/pvp on`, except one player who keeps it off for the first two checks.

**Blocked hits and protection**
- [ ] Hit the player with PvP off. No damage, and the action bar says "<name> has PvP off". They can't hit back either ("Your PvP is off").
- [ ] Shoot them with an arrow or splash a harming potion on them. Still no damage. That player now turns PvP on.
- [ ] Kill someone, and hit them again right after they respawn. It's blocked for 5 seconds ("just respawned").

**Combat tag** (lasts 15 seconds after each hit)
- [ ] After a hit, both players get "In combat with <name>!" in the action bar with a sound, then a live countdown. When it runs out: "no longer in combat".
- [ ] While tagged, each of these is refused with a message and the seconds left:
  - [ ] `/spawn`, `/home`, `/tpa <someone>`
  - [ ] `/essentials:home` (the namespaced form)
  - [ ] `/pvp off`
- [ ] **Host:** `/fly <player>` on someone, then hit them. Their flight stops, and they can't fly again until the tag ends.
- [ ] **Host:** `/pvp P1` shows P1's PvP setting and whether they're in combat. `/pvp P1 off` switches it for them.

**Combat log**
- [ ] A tagged player quits mid-fight. They die where they stood and drop their items, the last attacker gets a fresh head of theirs, and everyone else reads "<name> logged out during a fight". When they rejoin, they respawn like after any death.
- [ ] *console* `kick <tagged player>`. Kicks don't count, so nothing happens to them.

**Kill heads**
- [ ] A kill drops a fresh head of the victim at their body. The killer picks it up, and it counts as a current head.
- [ ] The same killer kills the same victim again. No head this time ("already took").
- [ ] A bow kill and a knock-into-lava kill both count (use different victims).
- [ ] `/kill` and falling to your death on your own drop no head.
- [ ] *console* `gamerule keepInventory true`. A kill still drops the head, and the victim keeps their items. Set it back to false.
- [ ] **Everyone:** `/pvp off` once your tag ends. `/hca status <player>` shows `combat-tagged` turning off when it does.

### 2.4 Placed heads and duplicates (5 min)

- [ ] Someone places one of their heads as a block, then tries each of these:
  - [ ] break it by hand
  - [ ] blow it up with TNT
  - [ ] push it with a piston
  - [ ] wash it out with water

  Every time, the same economy head drops (same ID in `/hca inspect`), and **Studio's head tracer** shows each place and break.
- [ ] **Host**, in creative: middle-click clone a head in your inventory, then close the inventory. Ops with `headcount.admin.notify` get a "duplicate head" alert, and the vault refuses the copy ("copy").
- [ ] `/hca inspect` with a head in hand shows owner, cycle, **run ID** and current: yes.

### 2.5 Statuses along the way (spread through the session)

Check `/hca status <player>` while players are:

- [ ] in a bed
- [ ] on fire
- [ ] in lava
- [ ] at low health
- [ ] **AFK**: stand completely still for 1 minute until "You are now AFK" shows up, and `afk` reads true. Moving or chatting gives "no longer AFK". (`/afk` is EssentialsX's command and doesn't count while `player-state` is `included`.)
- [ ] `/vanish` (EssentialsX sets the metadata Headcount reads)
- [ ] in creative
- [ ] falling far
- [ ] on Bedrock

**Host:** `/papi parse P1 %headcount_status_in-pvp%` and `%headcount_status_afk%`.

### 2.6 End of cycle 1 (with warnings)

About 15 minutes before the end, remind the hoarder not to trade or kill.

- [ ] Check the activity rule: anyone with under 5 minutes of playtime is inactive (`/hc score` says not active yet). Make sure everyone has played longer than that.
- [ ] **Host:** `/hca cycle simulate` lists every score and the outcome each would get. Compare it with the N table. **Studio → Players** should show the same scores and outcomes.
- [ ] **Studio → This cycle:** set the end 12 minutes from now (or use `/hca cycle setend`). The countdown follows.
- [ ] The **10-minute** warning arrives in chat.
- [ ] At **1 minute**, a title shows, plus a **boss bar** reading "Cycle 1 ends in 0:59" with the real number (no raw `<cycle>`).
- [ ] A player who rejoins in the last minute also gets the boss bar.
- [ ] At the end:
  - [ ] everyone sees the results broadcast with the top 3
  - [ ] each player gets a personal report: unique, score, points, streak text in color, tier, outcomes
  - [ ] vaults are refilled with 27 new heads
  - [ ] cycle 2 starts
- [ ] Heads left in inventories now say **expired**, with a gray cycle number, and the vault refuses them.
- [ ] Points: a positive score gives that many points, with **+5 for the top 3** (the `reward-top` list). If the hoarder is negative, they get "Minor penalty" slowness, and `/hc effects` shows it with time left (only online time counts).
- [ ] `/hc history`, `/hc top last`, `/hca history <player>` and **Studio → History** all match.
- [ ] Have one player log out just before the end. When they rejoin, their report and outcome actions arrive before the first-join-cycle line.

**Notes:**

---

## Part 3: Cycle 2, "Shopping and politics" (about 50 min)

**Host:** set the end 50 minutes from now.

### 3.1 Shop (10 min)

Players with points from cycle 1 go shopping. The host gives the others some with `/hca points P3 give 20` or the **Give points** button on their Studio player page. Give a few players enough for the higher tiers.

- [ ] `/hc shop` opens on the highest tier you unlocked with last cycle's score:
  - Common: always
  - Uncommon: 5+
  - Rare: 10+
  - Legendary: 20+

  Browse to a locked tier, and its entries show **Locked** and the score needed. `/hc shop rare` opens that tier directly.
- [ ] With no points, an entry shows "too expensive". After `/hca points <p> give 100`, the entries become buyable.
- [ ] Between all of you, buy each of these at least once:
  - [ ] Steak (2)
  - [ ] Speed (3; buy it 5 times to see "limit reached")
  - [ ] Extra heads (5)
  - [ ] Shield (8)
  - [ ] Haste (6)
  - [ ] Head protection (10)
  - [ ] Bigger vault (12)
  - [ ] Mystery box (7)
  - [ ] Title: Collector (20)
  - [ ] Rollover (15)
  - [ ] Diamonds (25; one stock of 10 shared by everyone)
  - [ ] Title: Legend (60; needs Collector)
  - [ ] Flight (40; 1-day cooldown)
  - [ ] Next-cycle heads (50)
- [ ] Anything priced 50 or more asks for confirmation. Cancel works, and spam-clicking confirm buys it only once.
- [ ] No item can be taken out of the shop, titles, history or confirmation menus, whatever you click, drag or press.
- [ ] On Bedrock, the shop and confirmations work.
- [ ] **Studio → Shop:** the chest grid matches the in-game shop page by page, and each buyer's Studio player page lists what they bought.

### 3.2 Titles, permissions and prefixes (5 min)

- [ ] **Host:** `/hca title P1 grant champion`. It equips itself, and `/hc titles` shows it equipped. Unequipping and re-equipping work.
- [ ] **Prefixes need LuckPerms.** With `permissions.provider: included` and `permission-prefix.enabled: true` in titles.yml, no prefix appears, and the console warns that prefixes need LuckPerms or Vault. Now set `permissions.provider: luckperms` in **Studio → Integrations**, save and reload. The title shows as a prefix in chat and in `/lp user P1 meta info`. Turn `permission-prefix` off and reload, and the prefix goes away.
- [ ] `%headcount_title%` works in `/papi parse`.
- [ ] **Included permissions:** set `permissions.provider` back to `included` and reload. Add a list in the action builder with one `permission` action for `headcount.bypass.trade.cooldown`, duration `10m`. Then:
  - [ ] Run it on P2. P2 can now trade twice in a row with no cooldown.
  - [ ] P2 rejoins and still can, because the node is applied again on join.
  - [ ] Remove it from P2's effects list in Studio, and the cooldown is back.

### 3.3 Actions showcase (host, 10 min)

**Host:** run `/hca run <player> <list>` for each of these lists, on different players, while everyone watches:

- [ ] `celebrate`
- [ ] `dunce-cap`: a pumpkin locked on the head. It can't be taken off and comes back after death.
- [ ] `punish-mild`, `punish-medium`, `punish-severe`
- [ ] `reward-top`
- [ ] `mystery-box`

Then:

- [ ] `/hc effects` on those players lists each timed effect with time left, in readable words. `/hca effects P2` shows the same with IDs. `/hca effects P2 remove <id>` ends one right away, and `remove all` clears them.
- [ ] **Studio → Effects** lists every running effect for everyone. Its ✕ button removes one, after asking.
- [ ] `/hca hook first-join P1`, `/hca hook first-join-cycle P1` and `/hca hook warning:1m` replay those hooks.
- [ ] **Moderation, included** (Headcount's own), on a willing player:
  - [ ] `/hca run P test-mod` mutes them for 1 minute. Chat and `/msg` are blocked, and `/hca status` shows `muted`. It lifts on its own.
  - [ ] a `warn` (a message), and a `kick`
  - [ ] a `jail` with a 2-minute duration. They get moved to the jail location, and walking more than 8 blocks away pulls them back. `/spawn` is blocked but `/msg` works. When it ends, they go back to where they were.
  - [ ] a short timed `ban`. They show up on the vanilla ban list (`banlist`), and it lifts on its own.
- [ ] **One kind through a plugin:** in Studio, set `moderation.kinds: { ban: libertybans }` and reload. A timed ban now goes through LibertyBans, so it's in `/libertybans banlist` instead of the vanilla `banlist`, and it still lifts on time. Remove the kind afterwards.
- [ ] Money (EssentialsX economy): a `money` action changes `/balance`.

### 3.4 Restart survival (5 min)

- [ ] With timed effects running (slowness, dunce cap...), `restart`. Studio's lights go off, then come back once the server is up (reload the Studio tab if they don't).
- [ ] Everyone rejoins. `/hc effects` still lists everything, and the effects are still applied. Milk removes a potion effect, and it comes back within a second.
- [ ] Have someone hold a trade offer open during the restart. After they rejoin, their items come back exactly once.
- [ ] Everyone's PvP setting survived the restart (`/pvp status`).
- [ ] **Host:** give an offline player something (`/hca giveheads P4 2` while P4 is offline). The items are delivered on their next join.

### 3.5 Reset cycle, mid-cycle (5 min)

Warn everyone first, since their vaults get refilled.

- [ ] Two players each take 2 heads out of their vault. One keeps them in their inventory, and the other puts them in a chest.
- [ ] **Host:** `/hca reset cycle keep-heads` gives a confirm message that mentions "kept as defunct heads". Then run `/hca reset cycle keep-heads confirm` (or Studio's **Reset cycle (keep heads)** button).
  - [ ] Everyone gets the "Cycle 2 was reset" message, a refilled vault, and the first-join-cycle line again.
  - [ ] It's still cycle 2. `/hca cycle info` and the Studio dashboard show a new run ID and an end time 30 days out (shorten it again with `setend`).
  - [ ] The heads that were taken out are **(defunct)**, gray, and refused by the vault. Points, titles and effects haven't changed.
  - [ ] Shop limits for "this cycle" reset, so Speed can be bought 5 more times. Kill claims reset too, so the same killer and victim drop a head again.
- [ ] Take heads out again, then `/hca reset cycle delete-heads confirm`. Heads in inventories vanish at once. Opening the chest removes the ones in it, including any inside a shulker box or bundle. A player who was offline loses theirs when they join.
- [ ] `/hca audit type:head_delete` lists each deletion with `reason=reset` and where it happened.
- [ ] Shorten the cycle again so it ends in 25 minutes.

### 3.6 Bounties, protection, shields (10 min)

Back to PvP for a round. Everyone `/pvp on`.

- [ ] Kill a player who bought **head protection**. No head drops ("protected").
- [ ] Kill a player who bought **extra heads**. More heads drop than usual.
- [ ] **Host:** `/hca run <player> punish-severe`, which includes a bounty. The bounty is announced, and when someone claims it, the killer gets the points and the claim is broadcast.
- [ ] The severe punishment's location broadcasts keep repeating while it lasts.
- [ ] **PvP lock:** run a list with `pvp: on`, `lock: true` and duration `5m` on one player. `/pvp off` says it's locked, and `/hc effects` describes it as "PvP forced on (locked)".

### 3.7 End of cycle 2, the punishment test

- [ ] **Host:** `/hca run <hoarder> test-minus-10`, so their score is -10 or lower. `/hca cycle simulate` and Studio's Players table both show it.
- [ ] One player who bought a **shield** should also be negative. Check the simulate output.
- [ ] `/hca cycle end confirm` (or Studio's **End now**, which asks first):
  - [ ] The -10 player gets "Severe penalty": the dunce cap, glowing, and location broadcasts.
  - [ ] The shielded player's penalty is blocked ("shield used").
  - [ ] Punishments with "cycle" length last through cycle 3.
- [ ] Streaks: anyone who was positive twice shows a 2-cycle streak in green on the sidebar.
- [ ] Rollover: whoever bought it kept that percentage of their unspent points.
- [ ] Whoever bought Bigger vault or Next-cycle heads starts cycle 3 with the bigger vault or the extra heads. Extras that don't fit arrive as items. Their vault chest in Studio shows the new size.

**Notes:**

---

## Part 4: Cycle 3, "Config chaos" (about 45 min)

**Host:** set the end 40 minutes from now. This round, you change the rules **in Studio** while everyone plays. For each change, use **Review and save** and check the diff is only what you meant, press **Reload the server**, then see whether it shows up in game.

- [ ] **Scoring:** in the scoring lab, change the formula to `unique * 2 - ceil(active / 4)`. The lab's chart and sample scores update as you type. After saving, `/hc score` and the vault title's score match the lab. Change it back.
- [ ] **Vault & heads → score colors:** set positive to `gold`, and the vault title changes color.
- [ ] `vault.block-movement.block-opening: true`: a player with PvP on can't even open their vault. Set it back to false.
- [ ] **Trading:** try each of these with one trade:
  - [ ] `range.blocks: 10`: players 20 blocks apart can't trade, and walking apart mid-trade cancels it
  - [ ] `limits.max-heads: 2`
  - [ ] `only-own-heads: true`
  - [ ] `items-for-items: false`

  Undo them all and save. The diff for trade.yml should be empty.
- [ ] `duplicates.remove-copies: true`: a creative clone is removed when the inventory closes.
- [ ] `heads.placed.creative-break: drop`: breaking a placed economy head in creative drops it.
- [ ] **Sidebar:** add a line with `%headcount_rank%`. It shows in Studio's mockup first, then in game within a second.
- [ ] **Outcomes:** switch `mode` from `most-extreme` to `all`. The score line shows the overlaps, and `/hca cycle simulate` lists several outcomes per player. Switch it back.
- [ ] **Shop:** drag an entry to another slot, change a price, and give an entry `stock: 1`. The shop in game changes right away.
- [ ] **Messages:** change `vault.reset-head` or any other message you've seen tonight. The preview matches the game.
- [ ] **Included PvP, live:**
  - [ ] `force-on-worlds: [world_nether]`: in the Nether, `/pvp off` says PvP is always on there.
  - [ ] `messages.countdown: bossbar`: the combat countdown becomes a red boss bar.
  - [ ] `messages.tag-start.channel: title`: getting tagged shows a title instead.
  - [ ] `block-ender-pearls: true`: pearls are refused in combat.
  - [ ] `combat-log.actions`: add a `points` action of `-5`. The next combat logger loses 5 points (check their Studio player page).
- [ ] **AFK:** set `afk.announce: everyone`, and everyone sees "<name> is now AFK".
- [ ] **Placeholders off:** set `placeholders.provider: none`. The sidebar's `%headcount_rank%` line now shows the placeholder as plain text. Set it back to `placeholderapi`.
- [ ] **A recipe:** in **Learn → Recipes**, apply one (for example "A punishment that locks PvP on"). Studio shows the diff first. Save and reload, then undo it.
- [ ] **A conflict:** with an unsaved Studio change to config.yml, edit `config.yml` in a text editor and save it there. When you save in Studio, it warns that the file changed on disk instead of overwriting it.
- [ ] **A mistake:** in **Raw files → shop.yml**, give one entry the same `slot` as another in its tier. The **Problems** page flags it before you save. Undo it. Studio doesn't check materials, but `/hca selftest` does: type `DIAMOND_BLOK` in an entry, save and reload, and the selftest names it. Fix it.
- [ ] Turn on `ban-repeat-offenders` in outcomes, with a 1-minute ban for the test.
- [ ] Bedrock check (if you have one): vault, a trade with a Java player, shop, sidebar.
- [ ] **Optional, PvPManager:**
  1. Put `PvPManager.jar` back and set `pvp.provider: pvpmanager`.
  2. `restart`. `/pvp` is now PvPManager's, `/hca integrations` says so, and `in-pvp` follows PvPManager.
  3. Take it out again, set `included`, and `restart`.

### End of cycle 3

- [ ] Let it end on schedule, without `cycle end`. The warnings arrive on time and the boss bar counts down to the second.
- [ ] Everything from 2.6 holds again. History now shows 3 cycles, with streaks carried over.

**Notes:**

---

## Part 5: Wrap-up (about 15 min)

- [ ] **Host:** `/hca audit <player>`, `/hca audit <head id>` and `/hca audit <player> type:vault_in page:2` all find the right lines. **Studio → Audit log** finds the same ones, including in the older `.gz` files.
- [ ] Run `/hca reset <player> vault|points|titles|streak|effects|history|all confirm`, each option on one willing player. Each one does exactly what it says.
- [ ] `/hca flag P1 set test`, then `/hca status P1`. If you defined a flag status, it reads true. Clear the flag.
- [ ] Optional crash test: someone puts items in a trade offer, then *console* `kill -9` the server process (find it with `pgrep -f paper`). Start it again with `./start.sh`, and the items come back exactly once.
- [ ] **Studio → Backups:** export the configs as a .zip to keep tonight's setup.
- [ ] Final `/hca reset hard keep-heads` (3 codes) to leave a clean server for next time. The backup folder holds tonight's database and audit log.
- [ ] Put PvPManager back in `plugins/` only if you want it for normal play (and set `pvp.provider: pvpmanager`).
- [ ] Copy anything odd from the **Notes** into `1.1 Notes.md`.

### Things this plan can't cover

- DiscordSRV messages, since there's no bot token.
- ItemsAdder, Oraxen, Nexo and LiteBans, which are paid plugins.
- Floodgate with a real Xbox sign-in, if nobody joins from Bedrock.
- EssentialsX as the player-state provider (`/afk`, `/god`, Essentials jails). Set `player-state.provider: essentials` another time if you plan to use it.
