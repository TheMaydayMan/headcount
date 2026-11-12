# Headcount test night

Tonight we play a normal evening of Minecraft with Headcount running underneath. By the end, we'll have used every command, menu and config section, Bedrock players included, plus Headcount Studio, and hit the edge cases too. You (the **host**) run the server and the admin side. Everyone else plays and follows the "**Everyone:**" prompts.

Tick boxes as you go, and write anything odd under **Notes** at the end of each part. Afterwards they're your bug list. When something looks wrong, save the console lines from `TestServer/logs/latest.log`, the output of `/hca audit <player>` and `/hca status <player>`, and a screenshot of the Studio page if Studio was involved.

---

## How to read this plan

**N** is the number of players, you included (2 to 10). Anything that depends on N is a small formula or a pick of **[2-3] / [4-6] / [7-10]**.

**Host** is you, as op. **P1, P2, ...** are the other players in join order. **Bedrock** is anyone on a phone, console or Windows through Geyser (port 19132). Try to have at least one. Steps marked **Bedrock:** need one, and you skip them if nobody's on Bedrock.

Commands that start with `/` are typed in game. Lines marked *console* go in the server terminal or in Studio's **Console** page.

**Studio** marks a step done in Headcount Studio, and bold names like **This cycle** or **Raw files** are pages in its sidebar. Keep it open on a second screen or a laptop all night. It handles config edits (with a diff before every save), ending the cycle with its **End soon** buttons, and looking up who has what in the audit trail, so you can keep playing. When a step gives both a command and a Studio way, use whichever is faster. The plan says when the two have to agree.

Each part has a rough time. The whole night is about **3.5 to 4 hours**: three short cycles of 40 to 50 minutes, plus setup and wrap-up.

To restart, type `restart` in the console (or Studio's Console). The server comes back in the same terminal and everyone rejoins.

### How many players is best

5 or 6 is ideal. 4 to 8 works well with Studio running. 9 or 10 is noticeably harder.

Scores mean something from 3 players up. With 2, a player who never trades scores 0 and gets no penalty. From 3 to 6 that player scores -1, so penalties happen on their own and the hoarder role matters.

The top-3 reward also needs a place in the top half, so it's a real cut from 5 players up. With 2 to 4 players, only the top one or two get the 3 points. First place gets the Champion title at any size.

5 players gives two trade pairs plus a ring of three, and PvP teams of 2 against 3. 6 gives two even teams of 3. Several people are also needed for the diamonds' shared stock of 10, bounties with more than one hunter, and a leaderboard worth ranking.

At 7 or 8 the par goes up to 3, so the non-trader scores -2 and a player needs two other heads to get above 0. The trade-cancel and moderation cases get spread over more people, which helps. The cost is the host's attention. Without Studio you'd spend the night typing `/hca` commands and reading chat. Studio's **This cycle** and **Players** pages take most of that away, but have a second op watch chat and the console anyway.

At 9 or 10 the scoring works like 7 or 8, but a full set takes 9 trades each, so in 40-minute cycles some players won't get there. "Everyone does X" steps take twice as long, there's more to confirm than one host can keep up with, and chat moves fast enough that raw tags and wrong messages get missed. If you have 9 or 10, make the cycles 50 to 60 minutes and split into two groups of trade partners, each with its own checker (you, and a second op on Studio).

With 2 or 3 players the plan still covers everything. Use `/hca run <player> test-minus-10` (and `/hca points ... give`) for the score cases that won't happen naturally.

### Numbers that depend on N

Each unique head is worth a point. A small par, bigger on bigger servers, comes off the top, and a big share of everyone's heads adds up to 6 more. Your own head counts as 1 unique head. A full set is a head from every player.

| N | Par | Never trades or kills | 1 other head | 2 other heads | Full set |
|---|---|---|---|---|---|
| 2 | 1 | 0 (no penalty) | 7 (full set) | | 7 |
| 3 | 2 | -1 (minor penalty) | 3 | 7 (full set) | 7 |
| 4 | 2 | -1 | 2 | 5 | 8 |
| 5 | 2 | -1 | 2 | 4 | 9 |
| 6 | 2 | -1 | 2 | 4 | 10 |
| 7-8 | 3 | -2 | 0 | 2 | 10-11 |
| 9-10 | 3 | -2 | 0 | 2 | 12-13 |

A score is also the points it earns. So after a cycle of trading with everyone, each player has 7 to 13 points, enough for a few Common and Uncommon buys and maybe one Rare one. The shop tiers open at a score of 3 (Uncommon), 6 (Rare) and 9 (Legendary), so with 5 or more players a full set opens Legendary.

- **Teams** (for PvP): `T = ceil(N / 3)` teams, split as evenly as you can. With N = 2 it's 1 v 1.
- **Hoarder**: one player per cycle who never trades or kills, so there's always a low score to test. With N = 2, the host is the hoarder in cycle 2.
- **Trade partners**: pair people up. With an odd N, one group of three trades in a ring: A gives to B, B to C, and C back to A.

---

## Part 0: Host prep, alone (about 45 min, before anyone joins)

### 0.1 Server and Studio

- [ ] **Paper, Headcount, Geyser and Floodgate, nothing else.** Run `TestServer/setup.sh --with-bedrock`, which adds Geyser and Floodgate to Paper. Everything else Headcount can hand to a plugin (PvP, permissions, moderation, AFK, placeholders) runs on its included version tonight. `plugins/` should hold only the Headcount, Geyser-Spigot and Floodgate-Spigot jars and their folders. If an earlier setup left other plugins there, `setup.sh` lists them: move them out of `plugins/` for tonight.
- [ ] Rebuild if needed (`cd Headcount && ./gradlew build`), then `./start.sh`. The console shows `Ready. Cycle N ends ...` with no Headcount warnings, and a line saying Studio shares the Minecraft port.
- [ ] *console* `hca selftest`: every check passes.
- [ ] **Auto-switch:** if `plugins/Headcount/integrations.yml` didn't exist before this start, the console says it handed Bedrock to Floodgate, which it just found. If the file was already there from an earlier version, Headcount only notes which plugins are installed and changes nothing, and Bedrock stays none until 0.3.
- [ ] *console* `hca integrations` shows the defaults, which need no other plugin:
  - PvP = included
  - placeholders = included
  - money = none
  - permissions = included
  - moderation = included
  - player states = included
  - Discord = none
  - Bedrock = Floodgate or none (see the step above)
  - custom items: none
- [ ] **Studio:** *console* `hca studio` shows a link and a password. Open `http://localhost:25565` in a browser.
  - [ ] It asks for the password. A wrong one says so, and the right one opens **Overview**, which describes the setup in plain words and shows no problems.
  - [ ] The server light at the bottom of the sidebar is on.
  - [ ] From a phone or another computer on the same network, `http://<this computer's address>:25565` asks for the password too. After signing in there, `hca studio` counts two browsers.
  - [ ] Join as yourself. `/hca studio` in game shows the same password, and the link is clickable.
- [ ] In **Console**, `list` shows the server's answer, and `hca selftest` matches the terminal.
- [ ] The countdown on **This cycle** matches `/hca cycle info`.
- [ ] Join as yourself. *console* `op <you>` if needed.
- [ ] A couple of quick Studio checks:
  - [ ] **Ctrl+K**, type `score-colors`: it jumps to that option.
  - [ ] **Ctrl+K**, type your name: your player page opens, with your vault shown as a chest.
  - [ ] The theme button switches between light and dark.

### 0.2 The resets

- [ ] **Hard reset permission:** *console* `deop <you>`, then `/hca reset hard`: "no permission". *console* `op <you>` to get it back. (3.2 takes away just the hard reset permission.)
- [ ] **Wrong code:** `/hca reset hard`, then type a different 4-digit number: "Wrong code ... cancelled".
- [ ] **Expired code:**
  1. On **General**, set the confirm timeout to `5s`.
  2. **Review and save:** the diff is exactly one changed line. Save, then press **Reload Headcount** in the message that appears.
  3. Run `/hca reset hard`, wait 6 seconds, then type the code: "expired".
  4. In Studio, Ctrl+Z undoes the timeout change. Save and reload again.
- [ ] **Confirmation count:** set confirmations to `2` in Studio, save and reload. The flow now asks twice, and the second time says "Last confirmation". Set it back to 3.
- [ ] **Backups** has a backup from each save above. Comparing one with the current file shows only that line.
- [ ] **Real hard reset, keeping heads, from Studio's Console.** Take 2 heads out of your vault first. Then type `hca reset hard keep-heads` in Studio's **Console**, and Studio asks before sending it. The codes show up in the console, so type `hca reset hard <code>` three times. Then check:
  - [ ] The warning listed the counts (players, cycles, heads, points...) and said "kept as defunct heads".
  - [ ] You get the first-join welcome and the starter shield again, and a new vault with 18 heads and 9 empty slots. `/hca cycle info` says cycle 1 with a new run ID.
  - [ ] The 2 heads in your inventory now say **(defunct)** with a gray cycle number, and the vault refuses them ("defunct").
  - [ ] `plugins/Headcount/backups/hard-reset-<time>/` holds `headcount.db` and an `audit/` folder. The live `audit/` folder only has new lines, and Studio's **Audit log** shows only new events.
  - [ ] You count as a new player again, so newbie protection runs for 10 minutes: `/hca status <you>` shows `newbie-protected`.
- [ ] **Hard reset, deleting heads:** take 2 heads out again and put 1 in a chest. Then run `/hca reset hard delete-heads` in game, with the 3 codes.
  - [ ] The heads in your inventory vanish.
  - [ ] Open the chest, and that head vanishes too.
  - [ ] `/hca audit type:head_delete` shows `reason=reset`.
  - [ ] In **Audit log**, click one of those head IDs. Its **Follow a head** page shows it was made at the earlier cycle start and deleted after the reset.
- [ ] If you still have a defunct head from the keep-heads reset, throw it on the ground. It should stay, since that reset kept them.

### 0.3 Config for a short night (Studio)

Make these changes in Studio's **Set up** pages. Save them all at once with **Review and save** (check the diff), then press **Reload Headcount** in the message that appears and run `/hca selftest`.

- [ ] On **Cycle**, set the time zone to each of these in turn. After each one, save and reload, then compare the zone and end time in `/hca cycle info` with Studio's timeline.
  - [ ] `MST` (shows UTC-07:00)
  - [ ] `gmt-7`
  - [ ] `UTC+5:30`
  - [ ] `america/denver`
  - [ ] `nonsense`: Studio's Problems page flags it, and the console warns and falls back to UTC

  Finish on `America/Denver`. Studio's **End soon** buttons assume the server's zone matches your computer's, so don't use them while it's set to anything else.
- [ ] **Cycle:** `activity.min-playtime: 5m`, so "active" means something tonight.
- [ ] On **Vault and heads**:
  - [ ] **Title:** make sure it contains `<score>`, for example `"<dark_gray><owner>'s Head Vault <gray>(<unique> unique, score <score>)"`. Studio's preview shows a sample score in color. Try `<score:aqua:dark_red:yellow>` and keep whichever you like.
  - [ ] **Head lore:** the cycle number should be gold on current heads and gray on expired and defunct ones (the 1.1 defaults). The tooltip preview shows all three.
- [ ] **Integrations**, set for tonight's tests:
  - [ ] `bedrock.provider: floodgate`, if auto-switch didn't already set it. After the reload, `hca integrations` shows Bedrock = Floodgate, and everything else as in 0.1.
  - [ ] `player-state.included.afk.after: 1m`, so AFK is quick to test.
  - [ ] `moderation.included-jail.location`: pick a spot for a small jail, read its coordinates from F3, and enter `world,<x>,<y>,<z>`.
  - [ ] Look over the **PvP message previews**, which show how each PvP message will look in chat, the action bar or a title.
- [ ] On **Hooks**, add these two action lists as cards in the action builder, not as YAML, so the builder gets tested.
  - [ ] `test-minus-10`:
    - a `score-bonus` of `-10`, duration `cycle`
    - a `message`: `<red>Test: -10 score this cycle`
  - [ ] `test-mod`:
    - a `mute`, reason `Test mute`, duration `1m`
  - [ ] `test-heads`:
    - a `give-heads` of `12` of the player's own heads, cycle `next`, to `vault`
  - [ ] Open actions.yml in **Raw files**. The lists are there, and the comments around them are untouched.
- [ ] **Review and save** shows every change above, across all the files. Save, reload, and run `/hca selftest`: all pass.
- [ ] Start the first cycle fresh with `/hca reset cycle delete-heads confirm`. Then press **In an hour** on **This cycle** (or `/hca cycle setend <today>T<now + 50 min>`), and check it with `/hca cycle info`.

**Notes:**

---

## Part 1: Arrivals (about 15 min)

**Everyone:** join one at a time, Bedrock players last. Each one shows up in the online list on Studio's **This cycle** page.

- [ ] About a second after joining, each new player gets these, in this order:
  1. the **first-join** welcome
  2. "You start with a shield", from the starter shield
  3. the **first-join-cycle** line (cycle number, time left, points)
- [ ] Each new player's sidebar is already on, showing cycle, unique, score, points, streak and time left.
- [ ] **Bedrock:** the same messages arrive in the same order, and the sidebar shows the same lines. `/hca status <them>` shows `bedrock-player` true, and it's false for Java players.
- [ ] **Everyone:** `/hc help` lists only player commands, including `/pvp`. Ops also see `/hca help`, which lists both resets.
- [ ] **Everyone:** `/hc` opens your vault with 18 of your own heads and 9 empty slots. The title shows 1 unique and a score colored by sign (see the table above). On Bedrock, the same chest menu works.
- [ ] **Everyone:** `/pvp status` says PvP is **off** and that new players are protected for 10 minutes.
- [ ] **Everyone:** `/hc sidebar` hides the sidebar. Run it again to bring it back, and leave it on. It ticks down.
- [ ] **Everyone:** check that each of these looks right:
  - [ ] `/hc points`
  - [ ] `/hc score`: the "You are active / not active yet" part is colored, with no raw tags
  - [ ] `/hc top`
  - [ ] `/hc history`: empty
  - [ ] `/hc effects`: only the starter shield
  - [ ] `/hc titles`: the menu, with locked titles
- [ ] **Host:** `/hca placeholders P1` shows sensible values for every `%headcount_*%` placeholder. For a Bedrock player, `bedrock` reads true.
- [ ] **Host:** `/hca status P1` shows every status, with `in-pvp` false and `newbie-protected` true.
- [ ] After 5 minutes of play, each player gets the **became-active** line ("You've played enough to count this cycle"), and `/hc score` now says they're active.
- [ ] P1's page in Studio (from **Players**) shows the same vault P1 sees in game.
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
- [ ] **Bedrock:** take heads out and put them back, move one to the hotbar, and try to put in dirt. Bedrock's inventory screen sends its own kinds of clicks, and the same rules hold.
- [ ] One player turns PvP on with `/pvp on`, and gets a message that their new-player protection has ended. They try to move a head and it's blocked ("in-pvp"), with a message. They run `/pvp off` right away and get "wait Ns" (10s cooldown). Wait it out, then turn it off.
- [ ] While their PvP was on, **Why is it blocked?** for that player names `in-pvp` and the vault rule.
- [ ] **Host:** `/hca vault P1` while P1 has their vault open. You both see each other's moves as they happen, and the title updates for both of you. `/hca vault P1 view` is view-only.

### 2.2 Trades (pairs, 10 min)

Pair up as trade partners.

- [ ] `/trade <partner>`. The partner gets a clickable [Accept]/[Deny] (Bedrock players get typed `/trade accept` text instead). Accept.
- [ ] Both put heads and items on the left, and the other player sees them mirrored on the right. Try to take from the other side by click, shift-click, number key and double-click. None of them work.
- [ ] Both confirm, then one changes their offer. Both confirmations clear.
- [ ] Both confirm again for a 3-second countdown. Clicking confirm again stops it. Let it finish, and the items swap.
- [ ] Put the heads you received in your vault. The unique count in the title goes up, and the score changes color once it turns positive.
- [ ] **Bedrock:** make one pair a Bedrock player and a Java player, and trade both ways. The Bedrock player's offer mirrors correctly, and nothing can be taken from the Java side. The Bedrock player's head counts as a unique head in the Java player's vault, even if its skin shows as Steve or Alex.
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
  - [ ] someone already trading
  - [ ] a request left alone for 30 seconds expires, and both players are told
- [ ] **Host:** `/hca audit P1 type:trade_complete` lists the trade with head IDs, and Studio's **Audit log** filtered to P1 shows the same lines. Trace one traded head: it was made, moved out of the vault, traded, and put in the new owner's vault.

### 2.3 PvP and kills (teams, 15 min)

Split into T teams and find somewhere flat. **Everyone:** `/pvp on`, except one player who keeps it off for the first two checks.

**Blocked hits and protection**
- [ ] Hit the player with PvP off. No damage, and the action bar says "<name> has PvP off". They can't hit back either ("Your PvP is off").
- [ ] Shoot them with an arrow or splash a harming potion on them. Still no damage. That player now turns PvP on.
- [ ] Kill someone, and hit them again right after they respawn. It's blocked for 5 seconds ("just respawned").

**Combat tag** (lasts 15 seconds after each hit)
- [ ] After a hit, both players get "In combat with <name>!" in the action bar with a sound, then a live countdown. When it runs out: "no longer in combat".
- [ ] While tagged, each of these is refused with a message and the seconds left:
  - [ ] `/spawn`, `/home`, `/tpa <someone>`. No plugin here adds them, but they're in `blocked-commands`, so Headcount answers before the server can call them unknown.
  - [ ] `/essentials:home` (a namespaced form)
  - [ ] `/pvp off`
  - [ ] **Host:** `/tp <tagged player> <you>`. They stay where they are and are told why.
- [ ] **Host:** `/pvp P1` shows P1's PvP setting and whether they're in combat. `/pvp P1 off` switches it for them.

**Combat log**
- [ ] A tagged player quits mid-fight. They die where they stood and drop their items, the last attacker gets a fresh head of theirs, and everyone else reads "<name> logged out during a fight". When they rejoin, they respawn like after any death.
- [ ] *console* `kick <tagged player>`. Kicks don't count, so nothing happens to them.

**Kill heads**
- [ ] A kill drops a fresh head of the victim at their body. For 15 seconds only the killer can pick it up, so let someone else try first. Then the killer picks it up, and it counts as a current head.
- [ ] The same killer kills the same victim again. No head this time ("already took").
- [ ] A bow kill and a knock-into-lava kill both count (use different victims).
- [ ] **Bedrock:** a Bedrock player kills a Java player and is killed by one. The heads drop and count the same way.
- [ ] `/kill` and falling to your death on your own drop no head.
- [ ] *console* `gamerule keepInventory true`. A kill still drops the head, and the victim keeps their items. Set it back to false.
- [ ] **Everyone:** `/pvp off` once your tag ends. `/hca status <player>` shows `combat-tagged` turning off when it does.

### 2.4 Placed heads and duplicates (5 min)

- [ ] Someone places one of their heads as a block, then tries each of these:
  - [ ] break it by hand
  - [ ] blow it up with TNT
  - [ ] push it with a piston
  - [ ] wash it out with water

  Every time, the same economy head drops (same ID in `/hca inspect`), and Studio's **Follow a head** page for it shows each place and break.
- [ ] **Host**, in creative: middle-click clone a head in your inventory, then close the inventory. Ops with `headcount.admin.notify` get a "duplicate head" alert, and the vault refuses the copy ("copy").
- [ ] `/hca inspect` with a head in hand shows owner, cycle, **run ID** and current: yes.

### 2.5 Statuses along the way (spread through the session)

Check `/hca status <player>` while players are:

- [ ] in a bed
- [ ] on fire
- [ ] in lava
- [ ] at low health
- [ ] **AFK**: stand completely still for 1 minute until "You are now AFK" shows up, and `afk` reads true. Moving or chatting gives "no longer AFK".
- [ ] in creative
- [ ] falling far
- [ ] on Bedrock

**Host:** `/hca placeholders P1` shows `status_in-pvp` and `status_afk` matching `/hca status P1`.

### 2.6 End of cycle 1 (with warnings)

About 15 minutes before the end, remind the hoarder not to trade or kill.

- [ ] Check the activity rule: anyone with under 5 minutes of playtime is inactive (`/hc score` says not active yet). Make sure everyone has played longer than that.
- [ ] Every player still has the starter shield from their first join. **Host:** `/hca effects <hoarder>` and remove the hoarder's shield with `/hca effects <hoarder> remove <id>`, so their penalty lands. Leave everyone else's.
- [ ] **Host:** `/hca cycle simulate` lists every score and the outcome each would get. Compare it with the N table. Studio's **Players** page should show the same scores and outcomes.
- [ ] Set the end 12 minutes from now on **This cycle** (or with `/hca cycle setend`). The countdown follows.
- [ ] The **10-minute** warning arrives in chat.
- [ ] At **1 minute**, a title shows, plus a **boss bar** reading "Cycle 1 ends in 0:59" with the real number (no raw `<cycle>`).
- [ ] A player who rejoins in the last minute also gets the boss bar.
- [ ] At the end:
  - [ ] everyone sees the results broadcast with the top 3
  - [ ] each player gets a personal report: unique, score, points, streak text in color, tier, outcomes
  - [ ] vaults are refilled with 18 new heads
  - [ ] cycle 2 starts
- [ ] Heads left in inventories now say **expired**, with a gray cycle number, and the vault refuses them.
- [ ] Points: a positive score gives that many points, with **+3 for the top 3** in the top half (the `reward-top` list). First place gets the Champion title.
- [ ] If the hoarder is negative, they get "Minor penalty": 2 hours of slowness, which `/hc effects` shows with time left (only online time counts).
- [ ] Any other negative player still had the starter shield, so they read "Your shield blocked Minor penalty" instead, and the shield is gone from `/hc effects`.
- [ ] `/hc history`, `/hc top last`, `/hca history <player>` and Studio's **Past cycles** page all match.
- [ ] Have one player log out just before the end. When they rejoin, their report and outcome actions arrive before the first-join-cycle line.

**Notes:**

---

## Part 3: Cycle 2, "Shopping and politics" (about 50 min)

**Host:** set the end 50 minutes from now.

### 3.1 Shop (10 min)

Players with points from cycle 1 go shopping first, with what they earned. The host then gives everyone some with `/hca points P3 give 20` or the **Points** button on their Studio player page, and a few players enough for the dearest entries.

- [ ] `/hc shop` opens on the highest tier you unlocked with last cycle's score:
  - Common: always
  - Uncommon: 3+
  - Rare: 6+
  - Legendary: 9+

  Browse to a locked tier, and its entries show **Locked** and the score needed. `/hc shop rare` opens that tier directly.
- [ ] With no points, an entry shows "too expensive". After `/hca points <p> give 100`, the entries become buyable.
- [ ] Between all of you, buy each of these at least once:
  - [ ] Steak (1)
  - [ ] Speed (2; buy it 3 times to see "limit reached")
  - [ ] Extra heads (3)
  - [ ] Shield (4)
  - [ ] Haste (3)
  - [ ] Head protection (4)
  - [ ] Keep 25% more points (3)
  - [ ] Mystery box (4)
  - [ ] Title: Headhunter (6). It says the requirements aren't met until you've taken 3 heads in fights this cycle, so buy it after 3.6.
  - [ ] Title: Collector (8)
  - [ ] Bigger vault (6)
  - [ ] Diamonds (10; one stock of 5 shared by everyone)
  - [ ] Totem of Undying (10)
  - [ ] Champion's blessing (8; 1-day cooldown, so a second try says when it's available again)
  - [ ] Elytra (20; one for the whole server, so a second buyer sees it sold out)
  - [ ] Title: Legend (25; needs Collector)
- [ ] Anything priced 10 or more asks for confirmation. Cancel works, and spam-clicking confirm buys it only once.
- [ ] No item can be taken out of the shop, titles, history or confirmation menus, whatever you click, drag or press.
- [ ] **Bedrock:** the shop with its confirmation, `/hc titles` and `/hc history` all work, and nothing can be taken out of them.
- [ ] The chest grid on Studio's **Shop** page matches the in-game shop page by page, and each buyer's Studio player page lists what they bought.

### 3.2 Titles, permissions and prefixes (5 min)

- [ ] **Host:** `/hca title P1 grant champion`. It equips itself, and `/hc titles` shows it equipped. Unequipping and re-equipping work.
- [ ] **Prefixes need LuckPerms or Vault.** Turn on `permission-prefix.enabled` in titles.yml and reload. No prefix appears, and the console warns that prefixes need LuckPerms or Vault. Turn it off again.
- [ ] `/hca placeholders P1` shows the title.
- [ ] **Included permissions:** add a list in the action builder with one `permission` action for `headcount.bypass.trade.cooldown`, duration `10m`. Then:
  - [ ] Run it on P2. P2 can now trade twice in a row with no cooldown.
  - [ ] P2 rejoins and still can, because the node is applied again on join.
  - [ ] Remove it from P2's effects list in Studio, and the cooldown is back.
- [ ] **A permission taken away:** add a list with a `permission` action for `headcount.admin.reset.hard`, `value: false`, duration `5m`, and run it on yourself. `/hca reset hard` now says "no permission", even though you're op. Remove it from your effects, and the reset asks for its codes again (don't finish it).

### 3.3 Actions showcase (host, 10 min)

**Host:** run `/hca run <player> <list>` for each of these lists, on different players, while everyone watches:

- [ ] `celebrate`
- [ ] `dunce-cap`: a lit jack o'lantern locked on the head. It can't be taken off and comes back after death.
- [ ] `punish-mild`, `punish-medium`, `punish-severe`
- [ ] `reward-top`
- [ ] `mystery-box`
- [ ] `test-heads`, on a player who didn't buy Bigger vault. It's checked at the end of cycle 2.

Then:

- [ ] `/hc effects` on those players lists each timed effect with time left, in readable words. `/hca effects P2` shows the same with IDs. `/hca effects P2 remove <id>` ends one right away, and `remove all` clears them.
- [ ] Studio's **Effects** page lists every running effect for everyone. Its ✕ button removes one, after asking.
- [ ] `/hca hook first-join P1`, `/hca hook first-join-cycle P1` and `/hca hook warning:1m` replay those hooks.
- [ ] **Moderation, included** (Headcount's own), on a willing player:
  - [ ] `/hca run P test-mod` mutes them for 1 minute. Chat and `/msg` are blocked, and `/hca status` shows `muted`. It lifts on its own.
  - [ ] a `warn` (a message), and a `kick`
  - [ ] a `jail` with a 2-minute duration. They get moved to the jail location, and walking more than 8 blocks away pulls them back. `/spawn` is blocked but `/msg` works. When it ends, they go back to where they were.
  - [ ] a short timed `ban`. They show up on the vanilla ban list (`banlist`), and it lifts on its own.
- [ ] **No economy:** add a `money` action to a test list and run it. With `economy.provider: none` it's skipped, with a warning in the console.

### 3.4 Restart survival (5 min)

- [ ] With timed effects running (slowness, dunce cap...), `restart`. Studio's server light goes off, then comes back once the server is up (reload the Studio tab if it doesn't). It's still signed in.
- [ ] Everyone rejoins. `/hc effects` still lists everything, and the effects are still applied. Milk removes a potion effect, and it comes back within a second.
- [ ] Have someone hold a trade offer open during the restart. After they rejoin, their items come back exactly once.
- [ ] Everyone's PvP setting survived the restart (`/pvp status`).
- [ ] **Host:** give an offline player something (`/hca giveheads P4 2` while P4 is offline). The items are delivered on their next join.

### 3.5 Reset cycle, mid-cycle (5 min)

Warn everyone first, since their vaults get refilled.

- [ ] Two players each take 2 heads out of their vault. One keeps them in their inventory, and the other puts them in a chest.
- [ ] **Host:** `/hca reset cycle keep-heads` gives a confirm message that mentions "kept as defunct heads". Then run `/hca reset cycle keep-heads confirm` (or **Keep old heads** under "Start it over" on **This cycle**).
  - [ ] Everyone gets the "An admin restarted cycle 2" message, a refilled vault, and the first-join-cycle line again.
  - [ ] It's still cycle 2. `/hca cycle info` shows a new run ID, and it and **This cycle** show an end time 14 days out (shorten it again with `setend`).
  - [ ] The heads that were taken out are **(defunct)**, gray, and refused by the vault. Points, titles and effects haven't changed.
  - [ ] Shop limits for "this cycle" reset, so Speed can be bought 3 more times. Kill claims reset too, so the same killer and victim drop a head again.
- [ ] Take heads out again, then `/hca reset cycle delete-heads confirm`. Heads in inventories vanish at once. Opening the chest removes the ones in it, including any inside a shulker box or bundle. A player who was offline loses theirs when they join.
- [ ] `/hca audit type:head_delete` lists each deletion with `reason=reset` and where it happened.
- [ ] Shorten the cycle again so it ends in 25 minutes.

### 3.6 Bounties, protection, shields (10 min)

Back to PvP for a round. Everyone `/pvp on`.

- [ ] Kill a player who bought **head protection**. No head drops ("protected").
- [ ] **Host:** `/hca run <player> punish-severe`, the Wanted punishment. Its bounty is announced, their PvP is locked on, and `/hc effects` lists each part.
- [ ] Kill the wanted player. Two of their heads drop instead of one (its extra-heads part), the killer gets the bounty's 5 points, and the claim is broadcast.
- [ ] The wanted player's location broadcasts keep repeating every 10 minutes while they're online.
- [ ] Whoever now has 3 kill heads this cycle can buy **Title: Headhunter**.
- [ ] **PvP lock:** run a list with `pvp: on`, `lock: true` and duration `5m` on one player. `/pvp off` says it's locked, and `/hc effects` describes it as "PvP forced on (locked)".

### 3.7 End of cycle 2, the punishment test

- [ ] **Host:** `/hca run <hoarder> test-minus-10`, so their score is -10 or lower. `/hca cycle simulate` and Studio's Players table both show it.
- [ ] One player with a **shield** (bought, or the starter one) should also be negative. Check the simulate output.
- [ ] `/hca cycle end confirm` (or **End the cycle** on **This cycle**, which asks first):
  - [ ] The -10 player gets "Wanted": the dunce cap, glowing, the Dunce title, PvP locked on, and location broadcasts.
  - [ ] The shielded player's penalty is blocked ("shield used").
  - [ ] If anyone besides the -10 player was negative in both cycles, they get "Penalty" (weakness, 2 fewer hearts, the Dunce title) instead of "Minor penalty".
  - [ ] Punishments with "cycle" length last through cycle 3.
- [ ] Streaks: anyone who was positive twice shows a 2-cycle streak in green on the sidebar.
- [ ] Rollover: whoever bought "Keep 25% more points" kept 75% of their unspent points, and everyone else 50%.
- [ ] Whoever bought Bigger vault or Extra heads starts cycle 3 with the bigger vault or 3 more heads. Their vault chest in Studio shows the new size.
- [ ] The `test-heads` player has a full vault, and the heads that didn't fit arrive as items.

**Notes:**

---

## Part 4: Cycle 3, "Config chaos" (about 45 min)

**Host:** set the end 40 minutes from now. This round, you change the rules **in Studio** while everyone plays. For each change, use **Review and save** and check the diff is only what you meant, press **Reload Headcount**, then see whether it shows up in game.

- [ ] **Scoring:** in the scoring lab, change the formula to `unique * 2 - ceil(active / 4)`. The lab's chart and sample scores update as you type. After saving, `/hc score` and the vault title's score match the lab. Change it back.
- [ ] On **Vault and heads**, set the positive score color to `gold`. The vault title changes color.
- [ ] `vault.block-movement.block-opening: true`: a player with PvP on can't even open their vault. Set it back to false.
- [ ] **Trading:** try each of these with one trade:
  - [ ] `range.blocks: 10`: players 20 blocks apart can't trade, and walking apart mid-trade cancels it
  - [ ] `limits.max-heads: 2`
  - [ ] `only-own-heads: true`
  - [ ] `items-for-items: false`

  Undo them all and save. The diff for trade.yml should be empty.
- [ ] `duplicates.remove-copies: true`: a creative clone is removed when the inventory closes.
- [ ] `heads.placed.creative-break: drop`: breaking a placed economy head in creative drops it.
- [ ] **Sidebar:** add a line with `%headcount_rank%`. It shows in Studio's mockup first, then in game within a second, on Bedrock too.
- [ ] **Outcomes:** switch `mode` from `most-extreme` to `all`. The score line shows the overlaps, and `/hca cycle simulate` lists several outcomes per player. Switch it back.
- [ ] **Shop:** drag an entry to another slot, change a price, and give an entry `stock: 1`. The shop in game changes right away.
- [ ] **Messages:** change `vault.reset-head` or any other message you've seen tonight. The preview matches the game.
- [ ] **Included PvP, live:**
  - [ ] `force-on-worlds: [world_nether]`: in the Nether, `/pvp off` says PvP is always on there.
  - [ ] `messages.countdown: bossbar`: the combat countdown becomes a red boss bar.
  - [ ] `messages.tag-start.channel: title`: getting tagged shows a title instead.
  - [ ] `block-ender-pearls: true`: pearls are refused in combat.
  - [ ] `combat-log.actions`: add a `points` action of `-5`. The next combat logger loses 5 points (check their Studio player page).
  - [ ] `pvp.pet-protection` on (integrations.yml): someone with PvP off tames a wolf. A player with PvP on can't hurt it, and gets told why. The owner hitting their own wolf isn't stopped.
- [ ] **Pet and chest hooks** (config.yml `hooks.player`, examples in the comments):
  - [ ] `hurt-pet`: paste the pet vengeance example (PvP on and locked for 10m). Hitting someone else's tamed pet turns your PvP on, and `/pvp off` is refused until it ends. Hitting it again within 10s doesn't run it twice.
  - [ ] `open-trapped-chest`: paste the trapped chest example. Opening a trapped chest turns your PvP on for 5m. A player in creative doesn't trigger it.
  - [ ] `kill-pet`: add a `message` with `<owner>` and `<pet>`. Killing a pet shows it with the right names.
  - [ ] A Bedrock player triggers each of these the same way.

  Empty all three again afterwards.
- [ ] **AFK:** set `afk.announce: everyone`, and everyone sees "<name> is now AFK".
- [ ] **Placeholders off:** set `placeholders.provider: none`. The sidebar's `%headcount_rank%` line now shows the placeholder as plain text. Set it back to `included`.
- [ ] **A recipe:** on **Recipes** (under Help), apply one (for example "A gift for everyone when a cycle starts"). Studio shows the diff first. Save and reload, then undo it.
- [ ] **A conflict:** with an unsaved Studio change to config.yml, edit `config.yml` in a text editor and save it there. When you save in Studio, it warns that the file changed on disk instead of overwriting it.
- [ ] **A mistake:** open shop.yml in **Raw files** and give one entry the same `slot` as another in its tier. The **Problems** page flags it before you save. Undo it. Studio doesn't check materials, but `/hca selftest` does: type `DIAMOND_BLOK` in an entry, save and reload, and the selftest names it. Fix it.
- [ ] Turn on `ban-repeat-offenders` in outcomes, with a 1-minute ban and its streak condition set to `"<=-1"` for the test, so anyone negative at the end of cycle 3 gets it.
- [ ] **Bedrock providers:**
  1. Set `bedrock.provider: geyser` and reload. `/hca integrations` says Geyser, `bedrock-player` still reads true, and a trade request to a Bedrock player is still the typed `/trade accept` text.
  2. Set it to `none` and reload. Bedrock players can still play and trade, but `bedrock-player` reads false and their trade requests show the Java [Accept]/[Deny] text.
  3. Set it back to `floodgate`.

### End of cycle 3

- [ ] Let it end on schedule, without `cycle end`. The warnings arrive on time and the boss bar counts down to the second.
- [ ] Everything from 2.6 holds again. History now shows 3 cycles, with streaks carried over.
- [ ] Anyone negative is banned for a minute on top of their penalty, and the ban lifts on its own.

**Notes:**

---

## Part 5: Wrap-up (about 15 min)

- [ ] **Host:** `/hca audit <player>`, `/hca audit <head id>` and `/hca audit <player> type:vault_in page:2` all find the right lines. Studio's **Audit log** finds the same ones, including in the older `.gz` files.
- [ ] Run `/hca reset <player> vault|points|titles|streak|effects|history|all confirm`, each option on one willing player. Each one does exactly what it says.
- [ ] `/hca flag P1 set test`, then `/hca status P1`. If you defined a flag status, it reads true. Clear the flag.
- [ ] Optional crash test: someone puts items in a trade offer, then *console* `kill -9` the server process (find it with `pgrep -f paper`). Start it again with `./start.sh`, and the items come back exactly once.
- [ ] On **Backups**, download the configs as a .zip to keep tonight's setup.
- [ ] Final `/hca reset hard keep-heads` (3 codes) to leave a clean server for next time. The backup folder holds tonight's database and audit log.
- [ ] Gather everyone's **Notes** into one bug list.

### Things this plan can't cover

- Every integration except Geyser and Floodgate: PvPManager, PlaceholderAPI, Vault, LuckPerms, EssentialsX, the moderation plugins, DiscordSRV and the custom item plugins. They're in [TESTING.md](TESTING.md#13-other-plugins).
- Floodgate with a real Xbox sign-in, if nobody joins from Bedrock.
