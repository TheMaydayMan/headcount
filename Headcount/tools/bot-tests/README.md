# Bot tests

Player tests for Headcount, played by [mineflayer](https://github.com/PrismarineJS/mineflayer) bots on a copy of the test server. They cover what console checks and unit tests can't: clicking in menus, trading, killing, buying, joining and leaving, and the timing of hooks and timed actions. Each suite prints `PASS` or `FAIL` for each check, then a summary.

mineflayer speaks Minecraft 26.1, so the bot server runs ViaVersion and ViaBackwards to let 26.1 clients join Paper 26.3. ViaBackwards rejects this mineflayer version's routine movement packets, so `lib.js` drops them. The bots stand where the server puts them, and the tests teleport them instead.

## Setup (once)

```bash
./setup.sh
```

It needs Node.js 20 or newer, curl, python3, and JDK 25 (in `~/.jdks/jdk-25.0.5+8` or `JAVA_HOME`). It builds everything in `work/` next to these scripts (or in `$HC_BOT_DIR`):

1. Downloads the integration plugins into `work/plugin-jars` with `TestServer/setup.sh --with-plugins --plugins-dir`, which checks each one's sha256. The test server itself has none of them by default.
2. Copies the test server's Paper jar and EULA (from the repository's [`TestServer/`](../../../TestServer/README.md), after its `setup.sh` has run, or `$HC_TEST_SERVER`) to `work/botserver`, and adds the plugins the suites use: PvPManager, PlaceholderAPI, LuckPerms, EssentialsX, VaultUnlocked, LibertyBans and AdvancedBan. DiscordSRV, Geyser and Floodgate are left out.
3. Downloads ViaVersion and ViaBackwards 5.12.3, which let mineflayer's 26.1 clients join Paper 26.3.
4. Writes the bot server's `server.properties`: offline, port 25566, no whitelist, a flat peaceful world without monsters or spawn protection.
5. Builds Headcount and the test addon (`addon/`, a small plugin built against the API that `tests-api.js` uses) into the bot server.
6. Starts the server once so every plugin writes its config, then turns PvPManager's newbie protection off (it would block every PvP test).
7. Runs `npm install` here (mineflayer 4.39.0).

Run it again whenever the plugin versions in `TestServer/setup.sh` change.

## Running

```bash
./run-all.sh
```

It copies the freshly built jar into the bot server, wipes Headcount's data and the players' saved data, starts the server (console input is `work/bot-console.in`, output `work/botserver.log`), and runs every suite in order. Results go to `work/runs/summary.txt`, with each suite's full output beside it. A full run takes about 25 minutes.

To run one suite: `./reset-bot.sh`, then `node tests-<name>.js`. Several take a player name as an argument so they can be repeated with a fresh player. `restart-bot.sh` restarts the server without wiping anything, and `kill-bot.sh` kills it like a crash. Stop it with `echo stop >> work/bot-console.in`.

`reset-bot.sh` starts from Headcount's default configs, so each run is a fresh install and auto-switch hands every job to the bot server's plugins (PvPManager, PlaceholderAPI, Vault, LuckPerms, LibertyBans, EssentialsX), the way the suites expect. It adds only test settings: a peaceful world with no weather, the action lists in `test-lists.yml`, messages in the `became-active` and `join` hooks, a minimum playtime of 0 so every bot is scored, and `economy.provider: vault` for the money action. Suites change other settings for their own checks (test statuses, lower limits, a provider) and put them back when they finish. They set PvP from the console (`pvp <player> on|off`, then `untag`), because a player's own `/pvp` is held to PvPManager's toggle cooldown and a combat tag blocks trades.

| Suite | What it covers |
|---|---|
| tests-vault | join hooks, vault set-up, the PvP gate, click types in the vault, refused items, audit |
| tests-play | trading, mirror protection, kills, statuses, placeholders, shop, sidebar, titles, a live cycle end, expiry, history |
| tests-more | PlaceholderAPI in Headcount text, player commands, tab completion, trade request rules, confirmation menu, admin vault view, placed heads (break, TNT), offline delivery and the hook order on rejoin |
| tests-final | all join hooks for a non-op player, permissions, drag, offhand and double click, piston and water on placed heads, old-cycle heads, audit rotation |
| tests-dupe | forged head copies found and refused, admin alerts, expired heads refreshed in chests, shulkers and bundles |
| tests-rest | head protection, extra heads, bounties, keepInventory; trade expiry, cooldown, limits, types, range, worlds, every cancel case, overflow |
| tests-actions | every action type on a live player, timed reverts, common options, moderation through LibertyBans and EssentialsX |
| tests-outcomes | most-extreme outcomes, punishments that last the next cycle, shields, rollover |
| tests-shop | tier locks, tier and page buttons, prices, limits, stock, cooldowns, requirements, confirmation |
| tests-misc | title menu and force lock, LuckPerms prefix, sidebar toggles, admin history, a 60-slot paged vault, bypass permissions, the opening gate |
| tests-status, tests-status2 | every status source a bot can drive, combinations, loops, the status placeholder |
| tests-score | formula variables, PlaceholderAPI numbers, rounding, bonuses, activity and playtime |
| tests-moderation | AdvancedBan, command templates and the included version |
| tests-hooks2 | the `hurt-pet`, `kill-pet` and `open-trapped-chest` hooks and their cooldowns, pet protection, and pet vengeance |
| tests-api | the API methods, an addon status source and action type, and the events, including changing and cancelling them |
| crash-offer, crash-take | kill -9 with a trade offer open or a take-item active. Their second halves, crash-offer-rejoin and crash-take-rejoin, run after the restart and check the items come back exactly once. |

## The bare server (1.1 suites)

The bot server above hands every job to a plugin, so Headcount's own versions (the 1.1 defaults) never run there. A second server has no plugin but Headcount:

```bash
./setup-bare.sh    # once, after ./setup.sh: work-bare/botserver on port 25567
./run-bare.sh      # wipes it, then runs the five suites below in order (about 10 minutes)
```

Results go to `work-bare/runs/summary.txt`, and Headcount's unexpected console warnings to `work-bare/runs/headcount-warnings.txt`. To run one suite by hand, point the scripts at it: `HC_BOT_DIR=$PWD/work-bare HC_BOT_PORT=25567 ./reset-bot.sh`, then the same variables for `node tests-bare-<name>.js`. Don't run both servers' suites at once: on a 4-core machine the timing checks (pistons, water, pickups) start to fail.

| Suite | What it covers |
|---|---|
| tests-bare-pvp | the included PvP: toggle and cooldown, new-player and respawn protection, blocked melee, arrows and potions, the combat tag and what it blocks, combat logging and kicks, kill heads and the killer-only pickup, keepInventory, the lock, forced worlds, message channels, pearls, combat-log actions. Needs players who are still new, so it runs first. |
| tests-bare-included | the included permissions (applied again on join, `value: false` beating op), jail (also while combat-tagged), mute, AFK, placeholders `included` and `none`, the vault title's live `<score>` and its color, and a raw-tag sweep |
| tests-bare-studio | Headcount Studio over HTTP on the Minecraft port: sign-in, the write checks, saves, backups and the changed-on-disk refusal, commands, export, path checks, sign-out, a new password, the lockout |
| tests-bare-reset | `/hca reset hard` and `/hca reset cycle`: permissions, wrong and expired codes, keep-heads and delete-heads, run IDs, defunct heads, deletion in containers, shulker boxes, bundles and on join, backups |
| tests-bare-config | `cycle.time-zone` forms, missing options and providers falling back to the defaults, the 1-minute title and boss bar, a live cycle end, a new player's sidebar, and auto-switch as PlaceholderAPI is added and removed. It restarts the server, so it runs last. |

## Bedrock (bedrock/)

`bedrock/bedrock-test.js` joins the main test server through Geyser with [bedrock-protocol](https://github.com/PrismarineJS/bedrock-protocol) clients (`npm install` in `bedrock/`). The test server needs Geyser and Floodgate for it, from `TestServer/setup.sh --with-bedrock`. Offline test clients can't sign in to Xbox, so for the test only, set in `TestServer/plugins/Geyser-Spigot/config.yml`: `auth-type: offline`, `validate-bedrock-login: false`, and `transport: both` with `webrtc-port: 19133` (the clients speak RakNet). Put the original file back afterwards. Run it with the server's console input file as the first argument:

- `node bedrock-test.js <console-file> single`: one client, through Floodgate. Set `bedrock.provider: floodgate` in integrations.yml first, since the default is `none`. It checks that Headcount sees a Bedrock player, the vault opens as a chest menu, and the sidebar arrives as a Bedrock sidebar.
- `node bedrock-test.js <console-file> trade`: two clients, with Floodgate moved out of `plugins/` and `bedrock.provider: geyser` (offline clients all get the same Floodgate UUID). It checks Bedrock detection through Geyser and the typed `/trade accept` request text.

`npm install` builds raknet-native, which ships prebuilt binaries named after the kernel's major version (`prebuilds/linux-5-x64`). On a newer kernel the install fails: use `npm install --ignore-scripts` and link the folder under the running kernel's number (`ln -s linux-5-x64 node_modules/raknet-native/prebuilds/linux-6-x64`). `HC_BEDROCK_RAKNET=jsp-raknet` picks the pure-JavaScript backend instead, but it timed out against Geyser 2.12.1.

Offline clients all have XUID 0. The script gives each client its own XUID; with bedrock-protocol 3.61.0 also change `XUID: '0'` to `XUID: String(client.profile.xuid || '0')` in `node_modules/bedrock-protocol/src/handshake/login.js`, or the second client is turned away as "already logged in". Delete the test players' files in `TestServer/world/players/data/` afterwards.

## What the bots can't test

The Bedrock test checks the packets, not what a real Bedrock client draws, and it can't sign in to Xbox. The bots also can't move, so the statuses that come from movement (flying, gliding, swimming, sleeping, falling) aren't covered, nor are the combat tag's flight and elytra blocks or moving to end AFK (chat ending it is), and there is no DiscordSRV bot token or paid plugin (ItemsAdder, Oraxen, Nexo) to test against. These are all in the manual checklist in [TESTING.md](../../TESTING.md).

## Files

| File | What |
|---|---|
| `setup.sh` | One-time setup (above). |
| `run-all.sh` | The full run on a wiped server. |
| `setup-bare.sh`, `run-bare.sh` | The bare server: set up once, then the five `tests-bare-*` suites on it. |
| `reset-bot.sh`, `restart-bot.sh`, `kill-bot.sh` | Start from clean data, restart, crash the bot server. |
| `lib.js` | Shared helpers: bots, console input, the server log (without its color codes), read-only database queries. Action bars are logged with the chat, marked `[actionbar]`. `HC_BOT_DIR` and `HC_BOT_PORT` pick the server. |
| `tests-*.js`, `crash-*.js` | The suites in the tables. |
| `test-lists.yml` | The test-only action lists `reset-bot.sh` adds to actions.yml. |
| `addon/` | The test addon's source, its `plugin.yml`, and `printcp.gradle` (prints the compile classpath). |
| `bedrock/` | The Bedrock test (it has its own `package.json`). |
| `package.json`, `package-lock.json` | mineflayer, pinned. |

