# Headcount app: the plan

An all-in-one app for Headcount: set it up, run it, understand it, and find anything in it. This is the plan only; nothing past the first config editor has been built. Edit anything here, answer the questions in [§12](#12-decisions-for-you), and say "go".

---

## 1. What it is

A local web app (it runs in your browser and talks to your own machine). It has six jobs:

1. **Set up Headcount** without touching YAML: purpose-built editors for every part (cycle, vault, scoring, outcomes, shop, titles, trading, statuses, integrations, hooks, menus, sidebar, messages), with live in-game previews.
2. **Run a live server:** see the current cycle, players, scores, vaults, history and the audit log. Optionally, run admin commands without leaving the app.
3. **Learn how it works:** an interactive "how a cycle works" walkthrough, a glossary, task guides ("make a 7-day cycle"), and ready-made recipes you can apply in one click.
4. **Know where things are:** every option, command, permission, placeholder, action type and status source is searchable. Each one says what it does, where it lives, its default, and what uses it.
5. **Command guide:** every command and permission, with what it does, who can use it, examples, a copy button, and a run button when connected.
6. **Stay safe:**
   - nothing is written until you review it
   - every save is checked and backed up
   - problems are caught before the server ever sees them
   - the live data is read-only

**Principles**

- **Easy first, complete always.** Each page leads with the five settings people actually change. Every other option is one click away, never missing.
- **Show, don't describe.** Menus look like the chest they are, items show their in-game tooltip, the formula shows its numbers, and the sidebar looks like the sidebar.
- **Your files stay yours.** Edits change only the lines they touch; comments, order and formatting stay. You can review a diff before saving, undo anything, and restore any backup.
- **Works offline, installs nothing.** Python 3 plus files in the repository: no internet, no npm, no build step.
- **One source of truth.** The reference content is generated from the plugin's own docs and code, so the app can't drift from the plugin.

---

## 2. Who uses it, and for what

| Who | Wants to | The app gives them |
|---|---|---|
| A server owner setting up | "Make cycles a week, the vault 18 slots, and the shop cheaper" | The setup pages and recipes, then review and save |
| An admin during a cycle | "Who's winning? Why did Steve lose his heads? End the cycle early" | The live dashboard, the audit trail, commands |
| Someone new to Headcount | "What is a status? Why can't I trade?" | Learn, the glossary, and the "why is this blocked" explainer |
| You, testing 1.1 | "Change a setting, reload, check it" | Edit, save, then one-click `/hca reload` and selftest over RCON |

---

## 3. Architecture

```
browser (http://127.0.0.1:8765) ──HTTP──▶ server.py (Python 3, standard library + PyYAML)
                                            ├── config files: plugins/Headcount/*.yml   (read and write, with backups)
                                            ├── bundled defaults: Headcount/src/main/resources/*.yml   (read)
                                            ├── live data: plugins/Headcount/headcount.db   (read-only SQLite)
                                            ├── audit log: plugins/Headcount/audit/*.log(.gz)   (read-only, search)
                                            ├── backups: plugins/Headcount/backups/   (list, restore)
                                            ├── docs: Headcount/docs/*.md + catalog.json   (read)
                                            └── RCON to the Minecraft server   (optional; runs commands)
```

- **Front end:** plain HTML, CSS and JavaScript ES modules, served by `server.py`. No frameworks or CDNs.
  - Files: `index.html`, `app.css`, `core/` (the YAML engine, MiniMessage, the formula evaluator, ranges, the diff), `ui/` (components), `pages/` (one module per page), `catalog.json`.
- **Data model:** every config file is loaded at start-up.
  - Each file's **text is the source of truth**. An edit is a precise line change (replace one value, insert an entry, remove an entry, rewrite one block), so undo, redo and the diff fall out of comparing texts.
  - After each edit the server turns the text into data (PyYAML), and the pages read that. Without PyYAML, a built-in reader covers the common cases.
  - Edits across several files are held together until **Review & save**.
- **Live data:** read straight from `headcount.db` in SQLite read-only mode (`mode=ro`), so it can never change the database. It refreshes every few seconds while a live page is open.
  - Audit search runs on the server (gzip included) and returns pages of results.
- **RCON (optional):** the server speaks Minecraft's RCON protocol to run commands such as `/hca reload`, `/hca selftest` and `/hca cycle info`, and shows the reply. It's off until the RCON port and password are set.
  - Destructive commands (cycle end, resets) always ask first, and the hard reset still needs its typed codes.
- **Catalog generator:** `tools/config-editor/build-catalog.py` builds `catalog.json` from:
  - `plugin.yml` (commands and permissions) and `docs/commands.md` (descriptions)
  - `Placeholders.java` (the 48 placeholders)
  - `docs/actions.md` (action types and their settings)
  - `docs/statuses.md` (status sources)
  - `ScoreService` (the formula's variables and functions)
  - the bundled configs (every option, its comment and its default)
  - `docs/examples.md` (recipes)

  It runs as part of `./gradlew build`, so the app always matches the jar.

---

## 4. Navigation

A left sidebar grouped by job, a top bar with search (Ctrl+K), the server connection, and **Review & save** with a count of pending changes.

```
HOME
  Overview
LIVE SERVER          (when a server folder with a database is open)
  Dashboard · Players · Cycle · Leaderboard · History · Audit log · Effects · Console (RCON)
SET UP
  Cycle & schedule · Vault & heads · Scoring · Outcomes · Shop · Titles · Trading · Statuses ·
  Integrations · Hooks & actions · Sidebar · Menus · Messages · Resets & safety
LEARN
  How Headcount works · Guides · Recipes · Glossary · Docs
REFERENCE
  Commands & permissions · Placeholders · Action types · Status sources · Formula · MiniMessage · All options
TOOLS
  Problems · Review & save · Backups · Import / export · Raw files
```

Every page has a URL (`#/setup/shop/common`), so you can bookmark it, and Back works.

---

## 5. Home: Overview

A one-screen picture of this server's Headcount:

- **Status strip** (with live data):
  - the current cycle and its run ID
  - time left, as a ring
  - active players and the total
  - the selftest result (over RCON)
  - pending warnings
- **"Your setup in plain words"**, generated from the configs. For example: "Cycles last **30 days** and roll over at **00:00 America/Denver**. Everyone starts with **27** of their own heads in a **27**-slot vault. A score is **unique − ceil(active / 4)**: with 10 active players, 4 unique heads scores **1**. Positive scores earn points; −1 or lower gets **Minor penalty**…" Each bold value links to where it's set.
- **Cards** for each area, with the key numbers and a "change" link:
  - the cycle, the vault, scoring, outcomes
  - the shop (tiers, entries), titles, statuses
  - integrations (who handles what), messages (how many differ from the defaults)
- **Problems** (from Tools › Problems), with links.
- **Unsaved changes** per file, with Review & save.
- **First run:** a short checklist (pick a cycle length, check the time zone, look at the shop, run selftest) that ticks itself off as you go.

---

## 6. Live server

All read-only from the database and audit log. Action buttons appear only with RCON.

| Page | Shows | Actions (RCON) |
|---|---|---|
| **Dashboard** | cycle ID, run, start/end, a countdown, warnings fired; online players; the live top 10; recent audit events as a feed; integrations in use | `/hca reload`, `/hca selftest`, `/hca cycle info` |
| **Players** | everyone who has joined: search, online dot, unique heads, live score preview, points (now and next cycle), streak, titles, flags, active effects, last seen | open a player |
| **Player page** | their **vault as a chest grid** (head tiles by owner, hover for the head ID, the cycle and where it came from), stats this cycle, history (a chart of past scores), purchases, effects with time left, titles, audit trail | give points, give heads, reset parts, remove an effect, grant a title (each a real `/hca` command, shown before it runs) |
| **Cycle** | a timeline from start to end with warning markers and "now"; settings fixed at its start (vault size, starting heads); a `/hca cycle simulate` preview (scores, outcomes, points) | extend or set the end, simulate, end now (asks first), reset cycle (asks first) |
| **Leaderboard** | live and last cycle, with ranks, unique heads, scores, and the outcome each would get | |
| **History** | every past cycle: averages, rewarded and punished, the top players; click for every player's result | |
| **Audit log** | search by player, head ID or text, filter by type and date, newest first; **head tracer**: enter a head ID to see its life from creation through vaults, trades and kills to deletion, as a timeline | |
| **Effects** | every active timed action on the server: who, what, time left, source | remove one |
| **Console** | an RCON command line with history and autocomplete from the command catalog; replies shown with their colors | anything |

---

## 7. Set up (the config pages)

Every page has:

- the **main settings** at the top as plain controls
- an **"All options"** section with everything else
- a **preview panel** on the right
- **"?"** links into the docs, and **"used by"** chips showing what else depends on a setting

Every option shows its default and a reset button.

### Cycle & schedule
- Controls: length (days, with 1d, 7d and 30d presets), first start (auto or a date picker), the roll-over time, and the time zone. The time zone has a search over region IDs plus MST/GMT-7 forms, and shows "it is 14:32 there now".
- **Timeline preview:** the next three cycles with real dates in that zone, and the warning hooks as markers on the last cycle. It updates as you type.
- Activity (minimum playtime) and the hooks for warnings, start and end, linked to Hooks & actions.

### Vault & heads
- Vault size and starting heads as sliders, with a **chest preview** (pages past 54 slots), the title with a live `<score>` preview, and the score colors.
- **Head item preview:** current, expired and defunct heads as in-game tooltips, with the name and lore editors next to them.
- Movement rules (which statuses block it, as chips), placed heads, duplicates, kills (heads per kill, worlds, pickup protection), points rollover.

### Scoring: the formula lab
- A formula editor with highlighting, autocomplete for variables and functions, and errors shown where they happen.
- **Calculator:** sliders for the inputs that matter (unique, active, rank, percentile, kills, streak...), the result after rounding and bonus, and which outcome it triggers.
- **Chart:** score against unique heads for a chosen number of active players, with the reward and punishment zones shaded.
- **Presets:** the formula ideas from scoring.yml, one click each, previewed before they replace anything.
- Rounding, rank ties, streak rules and the points multiplier.

### Outcomes
- **Score line:** a −20 to +20 axis with every rule as a colored band (punishments on the left, rewards on the right, by group), and a draggable "what if my score is X" marker that lists which rules fire under the current mode (most-extreme or all).
- Rule cards: display name (preview), score range (with a range builder), conditions, shield-able, group, priority, enabled, and the **actions** (opens the action builder).
- Add, duplicate, delete and reorder rules.

### Shop
- **Tier tabs** (with thresholds), and pages within each tier.
- **Chest-grid builder:** the real menu with its filler, buttons and entries. Drag entries between slots, click an empty slot to add one, and hover for the tooltip in each state (affordable, too expensive, sold out, locked...).
- **Entry drawer:** price, limits, stock, cooldown, confirm, requirements (titles and purchases as pickers), the display item (material picker, name and lore with preview, glint), and the actions (the action builder).
- Default state looks, the confirmation menu, and shop settings.

### Titles
- Cards with the title rendered as it shows in chat and tab, a description and an icon. Add, edit, remove.
- The prefix mirroring settings, with a chat-line preview ("[Champion] Steve: hi").
- A titles-menu grid preview.

### Trading
- The main rules as switches (allowed types, gifts, ranges, limits, cooldown, countdown).
- Gate statuses as chips, with "why blocked" examples.
- A **trade menu preview:** the double chest with both offer areas and every button. Click any button to edit it.

### Statuses
- A table of every status: its source (a colored pill), a summary, its fallback, and **"used by"** (vault gate, trade gates, conditions, deny lists...).
- **Status builder:** pick a source from the 46 (grouped: PvP, player state, combat, environment, combinations...). Only that source's settings show, with a description of each.
- A **combination view** for all-of, any-of and not, as a small tree.
- A warning when a status refers to one that doesn't exist, or makes a loop.

### Integrations
- One card per job (PvP, placeholders, money, permissions, moderation, Discord, items, Bedrock, player states), each with a segmented **included / plugin / none** switch. It explains what each choice means, and says when a plugin isn't installed (with live data).
- **PvP (included):** a full settings page:
  - toggle, newbie and respawn protection
  - the combat tag and its restrictions
  - combat log
  - **message channels**: each message with its channel, sound and lang text, and a "play" preview showing it in chat, action bar or title style
- Moderation: per-kind providers as a small table, and the jail and mute settings.

### Hooks & actions
- **Hook timeline:** first join → first join of the cycle → became active → every join; cycle start → warnings (3d, 1d, 1h, 10m, 1m) → cycle end. Click a moment to edit its actions.
- **Named action lists** (actions.yml): list, edit, add, rename. "Used by" shows where each list is referenced.
- **The action builder** (used everywhere actions appear):
  - **Cards:** each action is a card. Pick its type from the 60, grouped (messages, commands, items, currency, permissions, titles, moderation, PvP, buffs, Headcount, flow). Only that type's settings show, with help and defaults.
  - **Common options:** duration (a picker: instant, a time, until the cycle ends, N cycles, death, permanent, manual), time mode, stacking, chance, delay, repeat, conditions, start and end messages.
  - **Lists:** drag to reorder, duplicate, turn into a named list, or "run list" references.
  - **Previews** of messages, titles, boss bars and sounds.
  - A YAML tab for anything the cards don't cover. The builder never loses settings it doesn't know; they show as "other settings".

### Sidebar
- A **scoreboard mockup** with the title and lines rendered from sample values: "cycle 3", "7 unique". Edit the lines with drag-to-reorder and placeholder autocomplete; the 15-line limit is shown.
- Switches: enabled, shown for new players, update interval.

### Menus
- Every menu (vault, trade, shop, confirmation, titles, history) as a chest grid: filler slots painted directly (click to toggle, or the empty / border / none modes), buttons placed, items edited in the drawer. The rows control resizes the grid.

### Messages (the lang file)
- Grouped by area (commands, vault, trade, PvP, shop...) with a preview of each message using sample variables.
- Filters: changed from the default, empty (disabled), has an error.
- The **variables available** for each message are listed (from the code that sends it), and any unknown `<tag>` is flagged.

### Resets & safety
- Reset settings, the hard-reset confirmations, the audit log (rotation, types as a switch grid), storage.

---

## 8. Learn

- **How Headcount works:** an interactive, illustrated walkthrough of one cycle, built from your settings ("on this server, a cycle lasts 30 days..."), in steps:
  1. The cycle starts and every vault fills.
  2. Collect heads by trading and killing.
  3. What counts: unique, current-cycle heads in the vault.
  4. The end: tally, score, points, outcomes.
  5. The reset: heads expire, vaults refill.
  6. The shop with last cycle's points.

  Each step has a small animation (heads moving into a vault, a score being worked out) and a "where to change this" link.
- **Guides:** short task walkthroughs that open the right page with the right fields highlighted, step by step:
  - make cycles a week long
  - add a shop item
  - make a punishment that locks PvP on
  - let players trade during PvP
  - set up titles as LuckPerms prefixes
  - use another combat plugin
  - test changes safely
  - prepare for a test night
- **Recipes:** `docs/examples.md` as cards. "Apply" shows exactly what will change (the diff), then applies it as pending edits, which you review and save like any other change.
- **Glossary:** cycle, run, economy head, expired, defunct, unique, active, status, gate, action, outcome, hook, tier, points, streak, bounty, shield, included provider... Each term has a one-paragraph explanation and links. Terms are underlined across the app, with a hover definition.
- **Docs:** every page in `Headcount/docs/` rendered inside the app, with search across all of them and links between them.
- **"Why is this blocked?" explainer:** pick a player (live) and an action (move a head, trade, open the vault). It shows which statuses are true, which gate they hit, the message they'd see, and the bypass permission. With live data it uses the statuses Headcount last computed; without, it uses hypothetical toggles.

---

## 9. Reference

All of it comes from `catalog.json`, so it always matches the plugin.

- **Commands & permissions:**
  - every `/hc`, `/hca`, `/trade` and `/pvp` command: syntax, what it does, the permission, its default (everyone or op), examples, a copy button, and Run (RCON)
  - a **permission tree** (headcount.* → admin → admin.cycle...) with defaults, and "what does this permission let them do"
  - a **"build a command" helper** for the long ones (giveheads, points, reset, audit) using pickers for players, amounts and options
- **Placeholders:** all 48 with descriptions. With a live server, their values for a chosen player. Copy as `%headcount_x%` or as a MiniMessage variable.
- **Action types:** all 60 with every setting, the default, whether it reverts on its own, and a copyable example.
- **Status sources:** all 46 with settings and examples.
- **Formula:** every variable and function, with the examples from scoring.yml.
- **MiniMessage:** the reference already built, with the try-it box.
- **All options:** every one of the 472 options in one searchable table: file, path, default, your value, its comment, and a jump to its page.

---

## 10. Tools

- **Problems:** checked continuously, and on demand.
  - Errors:
    - YAML errors
    - statuses used but not defined
    - status loops
    - action lists used but missing
    - unknown action types or status sources
    - shop entries on the same slot, or outside the menu
    - titles required or granted but not defined
    - menu buttons outside the rows
    - `auto` providers left over from 1.0
    - invalid time zones, durations or materials
  - Warnings: unknown `<tags>` in messages, a duration missing on head-protection-type actions (as selftest does), messages that will never show.

  Each problem has a "go there" link and, where it's obvious, "fix it".
- **Review & save:** pending changes per file as a side-by-side diff with comments intact; save one file or all. After saving: "Reload the server now?" (RCON), then the selftest result shown inline.
- **Backups:** every backup the editor and the hard resets have made, by date. Compare any backup with the current file, and restore it (restoring is also a pending change you review).
- **Import / export:** export the whole config as a `.zip` (to share or keep), import one (shown as a diff first). "Compare with the defaults" for the whole setup.
- **Raw files:** the plain text editor, as today, for anything else.

---

## 11. Everywhere

- **Ctrl+K command palette:** jump to any page, option, command, placeholder, status, action list, player or doc; or run an action ("Save all", "Reload server", "Undo").
- **Undo / redo** (Ctrl+Z / Ctrl+Shift+Z) across every edit, per file.
- **Pending changes** across files, marked in the sidebar, with Review & save always one click away. Leaving with unsaved changes warns first.
- **Conflict safety:** if a file changes on disk (the server, you in another editor), the app says so and offers to merge your pending edits onto it, which line-level edits make possible.
- **Previews use sample data** (Steve, cycle 3, 7 unique...). On a live server you can pick a real player's values instead.
- **Item tiles:** materials drawn as colored tiles with their name, recognizable without textures (diamond cyan, gold, glass panes in their dye color...). The tooltip mockup is the Minecraft purple-bordered box.
- Keyboard-first: `/` to search, `g` then a letter for page jumps, Esc to close drawers.
- Light and dark themes. It works on a laptop screen; on a phone it's read-mostly.
- **Help everywhere:** each field's comment as help, a "?" link to the right doc section, glossary hovers.

---

## 12. Decisions for you

1. **Where it lives.** **A, recommended:** a local app (Python), as planned here. It edits any server folder you point it at; live data is read from the database file, commands go over RCON. **B:** built into the plugin, so the plugin serves the app itself (`/hca web` gives a link). That would work on any server without Python, show truly live data and reload instantly, but it needs Java work, authentication and care on public servers. A now keeps 1.1 focused; B could come later and reuse the same front end.
2. **RCON.** OK to have `TestServer/setup.sh` turn RCON on for the test server (local only, with a random password in `server.properties`), so the app can run commands? Without it, live pages are read-only and you type commands yourself.
3. **Live data.** OK to read `headcount.db` (read-only) while the server runs? SQLite allows it safely.
4. **Scope for this round.** Build everything above, or stop after certain parts?

   | Phase | What |
   |---|---|
   | 1 | Engine, shell, palette, undo, Review & save, Problems, generic editor |
   | 2 | All Set up pages and the action builder |
   | 3 | Learn and Reference, with the catalog generator |
   | 4 | Live server pages, the audit search and head tracer, RCON |
   | 5 | Polish |

   I'd build all five in order, building and syntax-checking each, and you test.
5. **Name.** "Headcount Studio"? Or something else.

## 13. What I'll check while building (not test suites)

- Each JavaScript and Python file passes a syntax check, and the plugin still builds with no warnings.
- The edit engine is the one place a mistake could damage a config. I'd like to run it once over every bundled file: load it, save it with no changes, and check the bytes are identical. That's a few seconds and no test code to keep. Say no and I'll skip it.
