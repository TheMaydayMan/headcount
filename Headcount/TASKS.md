# Headcount build checklist

Timing, pace and the ETA now live in [PROGRESS_BAR.md](PROGRESS_BAR.md). This file is the checklist.

- `[x]` verified, with how on the same line
- `[ ]` **written**: the code or file exists but has not been verified yet
- `[ ]` with no tag: not started

Nothing is ticked until it has been checked by compiling, a test, a server boot, a runtime check or a re-read against the spec, and the line says which.

## 0. Environment and research
- [x] Toolchain: Temurin JDK 25 in ~/.jdks, Gradle 9.9.0 wrapper (verified: `java -version` 25.0.5, `./gradlew --version` 9.9.0 on JVM 25.0.5)
- [x] Paper 26.3 build 246 and API `io.papermc.paper:paper-api:26.3.build.246-stable` (verified: Fill API and Maven metadata)
- [x] PvPManager 4.0.30 API read (verified: `javap` on the jar: `CombatPlayer.get`, `isInCombat`, `hasPvPEnabled`, `isNewbie`, `setPvP`, `PlayerTogglePvPEvent`)
- [x] Paper 26.3 / Adventure 5.2 API surface checked (verified: grep of the 26.3 and Adventure 5.2.0 source jars)
- [x] Compile-only APIs resolve (verified: 20 dependencies resolve with 0 failures, jars downloaded)

## 1. Project skeleton
- [x] Gradle build, Java 25 toolchain, plugin.yml, deploy-to-test-server task (verified: `./gradlew build` succeeds with 0 compiler warnings and copies the jar; Paper 26.3 accepted `api-version: '26.3'` and loaded the plugin)
- [x] Main plugin class, lifecycle, service registration (verified: 4 boots enable with no Headcount warnings, finish setup on the first tick, and `stop` disables cleanly; the PlaceholderAPI expansion, which needs the plugin wired up, resolves)

## 2. Config and text
- [x] config.yml, fully commented (verified: parses as YAML, every key the code reads is present, loads at boot with no warnings)
- [x] statuses.yml, trade.yml, scoring.yml, actions.yml, outcomes.yml, shop.yml, titles.yml, integrations.yml (verified: all parse, all read keys present, no load warnings; outcomes and named lists ran in the seeded cycle end)
- [x] lang/en_US.yml (verified: every key the code requests exists; fixed `on:`/`off:` keys that YAML read as booleans; escaped literal `<player>` in help text)
- [x] Text rendering: MiniMessage, internal placeholders, PlaceholderAPI passthrough (verified: all 280 messages parse and internal placeholders resolve; verified with mineflayer bots on a copy of the test server: `%pvpmanager_pvp_status%` resolved inside a Headcount message, `<points>`/`<price>` filled in shop messages)
- [x] Parsers: durations, ranges, item definitions, GUI layouts (verified: unit tests for durations and ranges; `/hca selftest` builds every configured item and checks every menu layout)
- [x] Every lang key used in code exists in en_US.yml; every config key read has a default (verified: scripted scan of all `lang()` calls and `configs()` reads against the bundled files)
- [x] All bundled YAML parses and loads without warnings (verified: PyYAML scan for non-string keys, plus server boot)

## 3. Storage and audit
- [x] SQLite schema and single-writer database thread (verified: schema created on first boot; seeded data read, cycle end written in transactions, read back with sqlite3)
- [x] Audit log files with rotation, search by player or head ID (verified: console search by head ID and player; 3 JUnit tests for size rotation, gzip, search inside .gz, day change and keep-days, which found that keep-days only ran on start and reload (fixed: also on each new day); live: the file rotated at the 64 KB minimum and `/hca audit` found a line that was only inside the .gz)

## 4. Heads
- [x] Economy head factory (verified by self-test: PDC round-trip, byte round-trip, vanilla heads rejected, unique IDs never stack, expired heads intact but not current and rendered differently, configured max stack size)
- [x] Placed-head preservation (verified with mineflayer bots on a copy of the test server: break by hand, TNT, piston push and water each logged HEAD_BREAK with the stored ID (`break:`, `explosion`, `piston`, `fluid`), the dropped item was the same economy head, and the index emptied; water can also leave a head in place, and then it stays indexed. Entity changes and the catch-all were checked by the code review in section 20)
- [x] Creative clone protection and duplicate head ID detection (verified with mineflayer bots on a copy of the test server: copies forged with vanilla `/give` were found by the inventory-close scan (DUPLICATE, `in-vault-of`, `copies=2`), online admins got the alert, a copy of an ID already in a vault was refused, and of two copies only the first went in. Middle-click clone in a menu changes nothing. The creative-inventory trigger was checked by code review)
- [x] Lazy lore refresh for expired heads (verified with mineflayer bots on a copy of the test server: old-cycle heads in a chest, in a shulker box in that chest and in a bundle in that chest all showed as expired when the chest was opened; an old head in the inventory was refused by the vault with "from cycle N and has expired")

## 5. Head vault
- [x] Virtual vault storage, sizes, paging, shared live view for admins (verified with mineflayer bots on a copy of the test server: storage and the 27-slot vault; a 60-slot cycle filled 60 heads, page 1 showed 45 with a next-page button, page 2 the other 15, a move on page 2 was saved and previous-page went back; the admin live view and VAULT_ADMIN audit. Fixed: slots past the size on a paged vault's last page looked empty; they are now locked panes)
- [x] Every click/drag type handled; only current-cycle economy heads allowed (verified with mineflayer bots on a copy of the test server: click, shift-click both ways, number keys both ways, offhand key (F) both ways and with dirt, Q and Ctrl+Q, middle click, double click (collect), and dragging dirt over vault slots; dirt, vanilla heads, old-cycle heads and copies refused; head totals unchanged and no ID twice after each step)
- [x] Status gate for movement and optional opening, per-status messages and bypass permissions (verified with mineflayer bots on a copy of the test server: the in-pvp gate blocked moves with its own message; headcount.bypass.vault-move.in-pvp let the move through; with block-opening on the vault didn't open ("can't open your vault while your PvP is on") and headcount.bypass.vault-open.in-pvp opened it)
- [x] Cycle-start fill for all known players; mid-cycle setup for new players (verified: seeded offline players refilled at cycle end (section 9); verified with mineflayer bots on a copy of the test server: online players got 27 new-cycle heads at each cycle start, and players joining mid-cycle got their vault on first join)

## 6. Statuses
- [x] Status registry, combinations, fallbacks, caching, loop detection (verified: fallbacks and unknown names by the self-test; verified with mineflayer bots on a copy of the test server: all-of, any-of with "!", not; every condition change was seen by the next query, so the cache never served a stale answer; a loop between two statuses is now reported and disabled at load (it was only caught when first evaluated), and still answers false)
- [x] Sources: PvP plugin, placeholder comparison, scoreboard tag, permission, metadata, flag, every built-in check, extras (verified with mineflayer bots on a copy of the test server, 46 checks: pvp, placeholder (greater-than, regex), headcount, scoreboard-tag, permission, metadata and essentials vanish, flag, pvp-damage, recent-damage, mob-damage, nearby-player (with a status filter), nearby-hostile, targeted-by-mob, health, food, on-fire, in-water, in-lava, sneaking, sprinting, invisible, op, bedrock, dead, riding, gamemode, world, joined-recently, potion, y-level, biome, holding, world-time, essentials afk and god, muted, in-trade, always and never, each false, then true, then false again. flying, gliding, swimming, sleeping and falling come from client movement these bots can't send; their checks are single Bukkit calls, read in the code. Fixed: a mob in its death animation still counted as nearby. Status sources from other plugins: section 17)
- [x] Default statuses; statuses exposed as placeholders (verified with mineflayer bots on a copy of the test server: the default in-pvp, combat-tagged, creative-or-spectator, vanished (metadata and EssentialsX) and the gates built on them; %headcount_status_<name>% reads true and false through PlaceholderAPI)

## 7. Kills
- [x] Kill head drop (vanilla killer credit, once per killer and victim per cycle, independent of keepInventory) (verified with mineflayer bots on a copy of the test server: a real PvP kill dropped the victim's head with KILL_DROP and the new ID; a second kill in the same cycle dropped nothing; with keep_inventory on the kill still dropped the head and the victim kept their items)
- [x] Head protection, extra heads, bounty payouts (verified with mineflayer bots on a copy of the test server: head-protection stopped the drop ("head is protected", KILL_DROP result=head-protected), didn't use up the once-per-cycle claim and ended when its charge was used; extra-heads amount 2 dropped 3 heads with 3 new IDs; a bounty was announced, paid the killer 5 points even though the head was already taken, and the claim was broadcast. Also added a self-test check: these actions do nothing without a duration, and one without is now reported)

## 8. Trade
- [x] Requests (one per player, expiry, every failure check, clickable link and Bedrock fallback) (verified with mineflayer bots on a copy of the test server: request and accept, yourself refused, offline name refused, one request at a time, sender withdraw, expiry after request.timeout with both told, the cooldown after a trade, out of range and other world refused; verified with bedrock-protocol Bedrock clients through Geyser on the main test server: a request to a Bedrock player says "Type /trade accept or /trade deny" with no click link)
- [x] Double-chest GUI, mirrored offers, confirmations, countdown, lock, execution (verified with mineflayer bots on a copy of the test server: both get a double chest, offers mirrored on the other side, mirror can't be taken by click or double click, both sides locked during the countdown, items exchanged, TRADE_COMPLETE lists the head by ID)
- [x] Rules (types, max items and heads, own head only, range, world, cooldown) (verified with mineflayer bots on a copy of the test server: blocked-items, max-items 1, max-heads 1 and only-own-heads each refused the offer with their message and returned the items; items-for-items: false refused the confirmation while a gift went through; range.blocks and same-world refused requests; the cooldown blocked a request right after a trade)
- [x] Every cancel case; crash-safe offer persistence and return on join; overflow drops (verified with mineflayer bots on a copy of the test server: closing, teleport, a cancel status, gamemode change, kick, ban, death (the dying player's offer drops with their items), world change and moving out of range each cancelled and returned both offers; a kicked or banned player had theirs on rejoin. Crash: kill -9 with an offer open returned it exactly once after the restart; this found and fixed a dupe (the inventory wasn't saved after an offer change). A return that no longer fit dropped at the player's feet with a message. Fixed a world change being reported as "screen closed")

## 9. Cycle
- [x] Scheduling (length, start date, end time, time zone), catch-up after downtime (verified: `auto` start at 00:00 UTC; 2-hour and 65-day outages processed on boot with consecutive IDs and schedule-aligned starts; DST boundary unit test)
- [x] Activity and playtime tracking (verified with mineflayer bots on a copy of the test server: with min-playtime 15s a new player was inactive, became active once it was reached (checked every activity.check-interval) and the became-active hook ran; playtime was stored when the player left)
- [x] End-of-cycle pipeline (verified: seeded 7 offline players; scores, ranks, streaks, rollover, pending points, outcomes, titles, results, wipe and refill all matched a hand calculation; fixed points_after to record the balance after outcomes)

## 10. Hooks
- [x] Cycle hooks (start, end, warnings), global and per-player lists, offline delivery (verified: global hooks, warnings and offline storage in sections 9 and 20; verified with mineflayer bots on a copy of the test server: a player offline at cycle end got their stored report on rejoin, before the first-join-cycle message; online players got the results broadcast and their report)
- [x] Player hooks in the required order (verified with mineflayer bots on a copy of the test server: a brand-new player got first-join, first-join-cycle, became-active and join in that order, with test messages in the two empty default hooks; a returning player got the delivered report first)
- [x] Default hooks (verified: every hook list parses fully; a 90 s test cycle fired only the 1 m warning (title + boss bar), skipped warnings already past, ran the results broadcast, and stored per-player reports for offline players)

## 11. Scoring and points
- [x] Expression engine (verified: 6 JUnit tests on precedence, functions, ternary, placeholders, errors)
- [x] Formula variables, PlaceholderAPI numeric placeholders, rounding modes (verified with mineflayer bots on a copy of the test server: total, own, points, vault_size, starting_heads, is_active, kills, deaths and trades read the right values in /hc score; %headcount_points% works in the formula and a non-numeric placeholder counts as 0; floor, ceil and round on 2.25 and 2.5; a score-bonus is added after the formula)
- [x] Points, rollover, next-cycle points; streaks with pause or break (verified: seeded cycle end: 0% rollover, points_next paid out, inactive streak paused)

## 12. Actions
- [x] Framework: lists, named lists, shorthand, common options (verified: self-test checks every list parses to as many actions as entries; planted typo detected; seeded outcomes ran named lists, `type:` keys and shorthand)
- [x] Persistent tracking, revert across restarts, offline apply and revert (verified: PENDING_APPLY rows reloaded across restarts; verified with mineflayer bots on a copy of the test server: a timed action queued for an offline player applied on join; one that ended while the server was down reverted on the player's rejoin; give-item, equip, permission, group, prefix, force-title, pvp, potion, attribute, glowing, gamemode, teleport, deny, bossbar and ban each reverted when their duration ended)
- [x] Message/effect, command, item, currency, permission/rank, title types (verified with mineflayer bots on a copy of the test server, 61 checks on a non-op player: message, broadcast, title, actionbar, sound, particle, bossbar, broadcast-location, console, player-command, give-item with take-back, equip, give-heads, loot, experience, points now and next cycle, money through Vault, LuckPerms permission, group, track and prefix, grant/force/strip title; discord needs DiscordSRV connected to a bot token)
- [x] Moderation, PvP, buffs/debuffs, deny, Headcount-specific, flow types (verified with mineflayer bots on a copy of the test server: LibertyBans warn, mute, kick and a temporary ban that lifted on time, an EssentialsX jail; pvp forced and locked; potion, attribute, glowing, fire, lightning, freeze, hunger, gamemode lock, teleport and return, damage, heal, feed, launch, clear-effects; deny chat, a command, trade, shop and vault, then lifted; next-cycle, rollover, shield, flag, score-bonus, head-protection, extra-heads, bounty; run, random, chance, conditions, delay, repeat. Found and fixed: a bare `- clear-effects` was read as a missing list (now a type, and the self-test reports missing list names); the shop's "+3 starting heads" did nothing in a full vault (extra heads now arrive as items); deny messages hid each other for 1.5 s across categories; repeats drifted later with each run and could lose the last one (now on a fixed schedule))

## 13. Outcomes
- [x] Rules, all or most-extreme per group, shields, ban rules disabled by default (verified: groups and most-extreme in the seeded run; verified with mineflayer bots on a copy of the test server: two players at score -10 got only penalty-10; its message, dunce cap, glowing and bounty landed and lasted through the next cycle, then ended; a shield blocked it (shielded:penalty-10), told the player and used its charge; the ban rules are enabled: false. Found and fixed: outcome actions with duration: cycle were removed the moment they were given)

## 14. Shop
- [x] Tiers, pages, navigation, entry states, prices, limits, stock, cooldowns, prerequisites (verified: every entry builds in every state; verified with mineflayer bots on a copy of the test server, 22 checks: a new player sees higher tiers locked and can't buy there; with a last score of 25 /hc shop opens on the highest tier (documented); tier and page buttons; exact prices; limit-reached, sold-out (global stock, shop_stock row), cooldown, unavailable (requires a title, then bought after getting it) and too-expensive ("need 2 more points") each shown and refused)
- [x] Confirmation menu above a price threshold (verified with mineflayer bots on a copy of the test server: a price at confirm-at-price opened the confirmation; cancel bought nothing and reopened the shop; confirm bought exactly once (56 to 54 points); a click inside gui.click-cooldown (150 ms) is dropped by design)

## 15. Titles, sidebar, history, top, effects
- [x] Titles with equip GUI, force lock, placeholder, LuckPerms prefix or suffix (verified with mineflayer bots on a copy of the test server: auto-equip of the first title, equip by clicking, %headcount_title%, force-title locks the menu ("title is locked") and the equipped title returns after; permission-prefix writes the title as a LuckPerms prefix and unequipping removes it. Fixed: turning permission-prefix on or off with /hca reload didn't update online players)
- [x] Sidebar (server toggle, per-player toggle, Bedrock-safe) (verified with mineflayer bots on a copy of the test server: /hc sidebar on shows the configured lines as team prefixes, off removes the sidebar, the choice survives a rejoin, sidebar.enabled: false tells players it is disabled; verified with bedrock-protocol Bedrock clients through Geyser on the main test server: the sidebar arrives as a Bedrock sidebar objective with its score lines, and the vault opens as a chest menu with its 27 heads)
- [x] History GUI (self and admin) (verified with mineflayer bots on a copy of the test server: /hc history lists past cycles; /hca history <player> opens that player's history for an admin)
- [x] /hc top, /hc effects, /hc points, /hc score, /hc help (verified with mineflayer bots on a copy of the test server: each answers with its message; /hc effects lists the tracked timed effect)

## 16. Integrations
- [x] integrations.yml provider selection with fallbacks (verified: boot summary picked LibertyBans over AdvancedBan, EssentialsX jails, Floodgate, LuckPerms, Vault economy)
- [ ] **written** PvPManager, PlaceholderAPI (+ expansion), Vault, LuckPerms, moderation plugins, DiscordSRV, custom items, Geyser/Floodgate, Essentials (verified with mineflayer bots on a copy of the test server: PvPManager PvP and combat tag; PlaceholderAPI both ways; Vault money through EssentialsX; LuckPerms permissions, groups, tracks and prefixes; LibertyBans, AdvancedBan, command templates and the vanilla fallback each for warn, mute, kick and a timed ban; EssentialsX jail, afk, god and vanish. Found and fixed: PvPManager's /pvp is op-only by default (pvp.let-players-toggle). DiscordSRV needs a Discord bot token, ItemsAdder, Oraxen and Nexo are paid (compiled against only, as the spec says); Geyser and Floodgate verified with bedrock-protocol Bedrock clients through Geyser on the main test server: Headcount detects Bedrock players through Floodgate and through Geyser. DiscordSRV and the custom item plugins load and are picked up at boot)

## 17. API and events
- [x] Public API, 13 custom events, external status sources and action types (verified with mineflayer bots on a copy of the test server and a test addon plugin built against the API: every HeadcountAPI method; an addon status source and action type used from the configs; all 13 events fired; ScoreCalculatedEvent, KillHeadDropEvent, PointsChangeEvent, ShopPurchaseEvent and OutcomeApplyEvent changed or cancelled the result. Fixed: runActionList didn't fill in <player>; ScoreCalculatedEvent now also fires for previews (isPreview()), so /hc score and the sidebar show an addon's changes)

## 18. Commands and permissions
- [x] Player commands, /trade, admin commands, tab completion, permission nodes, dynamic bypass nodes (verified: admin commands from console, bypass node registration; verified with mineflayer bots on a copy of the test server: every /hc subcommand and /trade from a player, tab completion for /hc and /trade, a non-op player can use /hc but not /hca, and tab completion hides `admin` for them)

## 19. Test server
- [x] Paper 26.3 build 246 downloaded (verified: sha256 matches the Fill API checksum)
- [x] Free plugins downloaded (verified: each jar's plugin.yml read: PvPManager, PlaceholderAPI, LuckPerms, EssentialsX, VaultUnlocked as "Vault", LibertyBans, AdvancedBan, DiscordSRV, Geyser-Spigot, floodgate)
- [x] Build copies the Headcount jar into TestServer/plugins (verified: `./gradlew build` put Headcount-1.0.0.jar there, same size as build/libs)
- [x] EULA, server.properties (online-mode=false), start script (verified: server boots offline-mode with ./start.sh on Java 25)
- [x] Server boots with Headcount, PvPManager and every free integration; no Headcount errors or warnings (verified 11-10 09:32 with the final jar (sha256 65451389...) and Headcount's data folder wiped: every integration picked up (PvPManager, PlaceholderAPI, Vault/EssentialsX, LuckPerms, LibertyBans, EssentialsX jails, DiscordSRV, Floodgate), cycle 1 started, `/hca selftest` 781 checks passed, clean stop, and no Headcount warning or error anywhere in the log)

## 20. Automated verification
- [x] `/hca selftest`, also run quietly on start and reload (verified: 781 checks pass on defaults (718 when first written; checks for missing durations, missing list names and status loops were added during bot testing); planted faults (bad material, slot clash, typo'd action, unknown status) were all reported)
- [x] Runtime check: warnings and a scheduled rollover (verified: cycle ended on its own at the set second)
- [x] Unit tests for pure logic (verified: 17 tests pass (3 for audit rotation and retention added during bot testing); they caught `"2d"` parsing as 2 seconds and a tied-leader percentile bug, both fixed)
- [x] Runtime check: cycle end with seeded offline players (verified: see section 9)
- [x] Runtime check: catch-up after downtime (verified: 2 h and 65 d outages)
- [x] Runtime check: console commands, integration summary, PlaceholderAPI expansion (verified: cycle info/end/simulate, audit, integrations, help, reload, `papi parse`)
- [x] Code review pass of the dupe-sensitive paths (verified by re-reading every click/drag branch of the vault and trade menus and every placed-head path; fixed the placed-head catch-all so only a drop of the same owner's head can be converted)

## 20b. Bot-driven player tests (a copy of the test server with ViaBackwards, so 26.1 mineflayer bots can join)
- [x] Bot server copy and two mineflayer bots connect (verified: Paper 26.3 copy with ViaVersion and ViaBackwards on port 25566; mineflayer 26.1 bots join; 6 bots used)
- [x] Join flow: first-join and first-join-cycle hooks, vault set up (verified: see sections 5 and 10)
- [x] Vault: every click type the bot can send (click, shift-click, number key, drop key, double click, drag) against the rules (verified: see section 5)
- [x] Trade between two bots: request, accept, offer, mirror can't be taken, confirm, countdown, exchange; a cancel case (verified: see section 8)
- [x] Kill: one bot kills the other, head drop with the right owner, once per cycle (verified: see section 7)
- [x] Statuses on a live player; shop purchase; sidebar; titles menu (verified: in-pvp, combat-tagged and creative-or-spectator on live players; see sections 14 and 15)
- [x] Final run on a wiped bot server: every suite in one go (verified: tools/bot-tests/run-all.sh, third full run 11-10 04:55-05:16 on a wiped bot server: 354 of 355 checks passed and both crash tests returned the items exactly once; the miss (a zombie targeting the other bot) was a test fix, and after it tests-status2 passed 6/6. Runs 1 and 2 found only harness problems (PvPManager's /pvp cooldown, a test addon flooding the log, AdvancedBan's own warning escalation), all fixed. Two small fixes came after run 3 (no false formula warning at start-up, no "unknown status" for a disabled one); the suites they touch, score 11/11, status 40/40 and status2 6/6, were rerun on the final jar. Bedrock smoke test through Geyser and Floodgate: 7/7. A fourth full run, on a bot server made by setup.sh with the project's own scripts (11-10 09:01-09:22), passed 354 of 355 and both crash tests; the miss was a real bug (repeats drifting), fixed and rechecked: 6 of 6 runs gave 3 repeats, tests-actions 61/61, tests-shop 22/22)

## 21. Docs
- [x] README (verified: feature counts checked against the code, 60 action types and 46 status sources; all links resolve)
- [x] Server owner docs: install, every config option, statuses, actions, formula variables, placeholders, hooks, example setups (verified: config-reference.md generated from the bundled files, 398 options; 13 guide pages; every relative link and anchor checked)
- [x] Developer docs: events, API, adding status sources and action types (verified: method and event lists taken from the source; the examples compile with every build as src/test/java/com/example/headcountaddon/ExampleAddon.java)
- [x] LICENSE (MIT, Grady Chastain), DECISIONS.md, TESTING.md (verified: LICENSE is the standard MIT text, (c) 2026 Grady Chastain; DECISIONS claims spot-checked against the code; TESTING commands match the real command syntax)

## 22. Final verification
- [x] Clean build, no compiler warnings (verified: `./gradlew clean build --no-build-cache` compiles from scratch with 0 warnings; 17 unit tests pass; the jar is copied to TestServer/plugins (same sha256, 65451389...))
- [x] Final boot: no Headcount errors or warnings (verified: see section 19)

## 23. Workspace README and final coherency pass (asked 11-10, after the build)
- [x] One README at the workspace root that maps everything: what Headcount is, where every file is, how to build, run and test, what is verified and what isn't (verified: Plugins/README.md; every claim checked against the code and the test server (counts, versions, ports, paths), and every link and anchor in it and the other 22 docs resolves)
- [x] Docs brought in line with what changed during verification (verified by re-reading each against the code: statuses.md loops and dying mobs, actions.md durations for tracked-only effects, outcomes.md and hooks.md cycle-end durations, heads.md paging and save timing, titles.md prefix on reload, commands.md and the help text for `/hc shop [tier]`, DECISIONS.md, TESTING.md, TASKS.md counts, the `starting-heads` comment and the shop entry it affects)
- [x] TestServer/start.sh picks the first Java 25+ (verified: with JAVA_HOME at JDK 21 it still uses JDK 25; with no JDK 25 at all it stops with a clear message)
- [x] Bot harness ready to use from the project: setup.sh, one shared test-lists.yml, helper scripts that work on a first run (verified: setup.sh built a bot server from scratch in an empty folder, tests-vault 17/17 and tests-api 23/23 ran on it alone, then run-all.sh ran every suite on it (see section 20b))

## 24. Git repository (asked 11-10)
- [x] The whole project is one repository: Headcount/, the top-level README, a root LICENSE and .gitignore, and TestServer/ as its setup only (verified: `git status` lists 230 files, the largest 51 KB; a scan of every tracked file found no secrets, keys, email addresses, IP addresses or personal paths. Not tracked: build output, the bot tests' work and node_modules folders, and everything the server downloads or makes, including Mojang's server files and a server.properties holding a generated secret)
- [x] TestServer/setup.sh rebuilds the test server: Paper and the 10 plugins at the tested versions, each checked against its sha256, server.properties from a template, and the Minecraft EULA only once accepted (verified: from an empty folder all 11 downloads matched; declining the EULA stops before writing eula.txt)
- [x] Works from a fresh clone (verified 11-10 10:05: `git clone`, `TestServer/setup.sh`, `./gradlew build` (JDK 25 found by Gradle; the hardcoded JDK path is gone and the foojay resolver can download one), then the server booted with Headcount ready, `/hca selftest` 781 checks passed, no Headcount warnings, and `git status` stayed clean)
- [x] "Entirely AI-coded" disclaimer at the top of the repository README, and a note in the plugin README (verified: re-read; every link in the 24 docs resolves)
- [x] Published at https://github.com/TheMaydayMan/headcount (verified 11-10 10:12, pushed at 10:12:44: the remote main is the local commit, the repository is public, GitHub detects the MIT license, and it holds all 230 files)
- [x] The top-level README is enough on its own to clone, set up, build, run and update it, and to install Headcount on another server (verified: re-read as a newcomer; added the clone URL, the requirements and platforms, the own-server and after-a-pull steps; TestServer/setup.sh now also works without sha256sum (macOS `shasum`), tested with sha256sum, with only shasum, and with a corrupted jar, which was re-downloaded)

