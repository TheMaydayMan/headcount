# Developer guide

Headcount has a small public API in `com.gradychastain.headcount.api`, 13 events, and two extension points: **status sources** and **action types**. Every example here is compiled with each build (`src/test/java/com/example/headcountaddon/ExampleAddon.java`), so it is known to compile against the current API.

## Depending on Headcount

Compile against the Headcount jar (it isn't published to a repository yet), and declare the dependency in your `plugin.yml`:

```kotlin
// build.gradle.kts
dependencies {
    compileOnly(files("libs/Headcount-1.0.0.jar"))
}
```

```yaml
# plugin.yml
depend: [Headcount]       # or softdepend if Headcount is optional for you
```

Get the API from Bukkit's services, or from `HeadcountProvider.get()`. Call it on the main thread.

```java
HeadcountAPI headcount = Bukkit.getServicesManager().load(HeadcountAPI.class);
```

## API

| Method | Does |
|---|---|
| `int currentCycle()` | The running cycle's ID, or 0 before the first. |
| `long cycleEndsAt()` | When the current cycle ends (epoch ms), or -1. |
| `Optional<HeadInfo> headInfo(ItemStack)` | Economy data on an item (head ID, owner, owner name, cycle, whether it is current, source), or empty for anything that isn't an economy head. |
| `ItemStack createHead(UUID owner, String source)` | Makes a new economy head of `owner` for the current cycle. It is recorded and audited like any other. |
| `int[] vaultCounts(UUID)` | `{unique, total, own}` for a player's vault right now. |
| `int previewScore(Player)` | Their score if the cycle ended now. |
| `long points(UUID)` / `long addPoints(UUID, long amount, String reason)` | Read or change points; negative amounts take. Returns the amount actually applied. |
| `boolean hasStatus(Player, String)` | Tests a status from statuses.yml (`!name` negates). |
| `boolean runActionList(UUID, String list, Map<String, Object> variables)` | Runs a named list from actions.yml on a player (`<player>` and `<uuid>` are filled in; your variables are added). Returns false if there is no list with that name. |
| `String equippedTitle(UUID)` | The shown title's ID, or null. |
| `void registerStatusSource(StatusSourceType)` | Adds a `source:` for statuses.yml. |
| `void registerActionType(ActionType)` | Adds a `type:` for every action list. |

## Events

All fire on the main thread.

| Event | When | Notes |
|---|---|---|
| `CycleStartEvent` | A cycle started (vaults already filled). | `getCycleId()`, `getStartMs()`, `getEndMs()` |
| `CycleEndEvent` | A cycle ended: results stored and outcomes applied. | `getResults()`: `cycle()`, `players()` (UUID to `PlayerResult`), `ranked()`, `top(n)`, `active()`, `totalPlayers()`, `averageScore()`, `rewarded()`, `punished()` |
| `PlayerFirstJoinEvent` | First join ever. | |
| `PlayerCycleJoinEvent` | First join of the current cycle (or online when it started). | `getCycleId()` |
| `PlayerBecameActiveEvent` | Reached the playtime needed to be active. | `getCycleId()` |
| `ScoreCalculatedEvent` | A player's score was calculated: at the end of a cycle, and for previews (`/hc score`, `%headcount_score%`, the sidebar, `/hca cycle simulate`) | `getVariables()` (every formula variable), `getScore()`, `isPreview()`; `setScore(int)` changes it. Previews can fire every second per player, so keep the listener cheap and skip side effects when `isPreview()` |
| `OutcomeApplyEvent` | An outcome rule is about to apply. | Cancellable. `getRuleId()`, `isPunishment()`, `getScore()` |
| `PointsChangeEvent` | Points are about to change. | Cancellable. `getDelta()` / `setDelta(long)`, `isNextCycle()`, `getReason()` |
| `ShopPurchaseEvent` | A purchase is about to happen. | Cancellable. `getEntryId()` (`tier.entry`), `getPrice()` |
| `KillHeadDropEvent` | A kill is about to drop heads. | Cancellable. `getKiller()`, `getVictim()`, `getAmount()` / `setAmount(int)` |
| `TradeCompleteEvent` | A trade completed. | `getPlayerA()`, `getPlayerB()`, `getGivenByA()`, `getGivenByB()` |
| `ActionAppliedEvent` / `ActionRevertedEvent` | A timed action started or ended. | `getType()`, `getSource()`, `getInstanceId()`; reverted adds `getReason()` |

```java
@EventHandler
public void onScore(ScoreCalculatedEvent event) {
    // Never punish anyone with under 2 hours of playtime this cycle.
    Double playtimeHours = event.getVariables().get("playtime_hours");
    if (playtimeHours != null && playtimeHours < 2 && event.getScore() < 0) event.setScore(0);
}

@EventHandler
public void onKillHead(KillHeadDropEvent event) {
    if (event.getKiller().hasPermission("myserver.doubleheads")) event.setAmount(event.getAmount() * 2);
}
```

## Adding a status source

Implement `StatusSourceType`. `create` gets the status's block from statuses.yml as a `ConfigView`, plus a resolver for testing other statuses. Return a `StatusCheck`: `true` or `false`, or `null` when you can't answer right now (Headcount then uses the status's `fallback` or `unavailable`). Throw `IllegalArgumentException` for bad settings; the message is shown to the server owner.

```java
static final class InArenaSource implements StatusSourceType {
    @Override
    public String id() {
        return "in-arena";
    }

    @Override
    public StatusCheck create(ConfigView settings, StatusResolver statuses) {
        String arena = settings.getString("arena", "");
        if (arena.isBlank()) throw new IllegalArgumentException("arena is required");
        return player -> player.getScoreboardTags().contains("arena:" + arena);
    }
}

headcount.registerStatusSource(new InArenaSource());
```

```yaml
# statuses.yml
  in-main-arena:
    source: in-arena
    arena: main
```

Checks run on the main thread, are cached for one tick, and should be fast.

## Adding an action type

Implement `ActionType`. `parse` reads the settings once, when the config loads; the returned `ActionHandler` does the work. Headcount handles everything common: chance, delay, conditions, durations, stacking, repeats, persistence across restarts, offline players, and messages. A handler only applies and, for timed use, reverts.

```java
static final class SpeedBoostType implements ActionType {
    @Override
    public String id() {
        return "speed-boost";
    }

    @Override
    public String primaryKey() {
        return "amount";            // so "speed-boost: 0.05" works
    }

    @Override
    public ActionHandler parse(ConfigView settings) {
        double amount = settings.getDouble("amount", 0.02);
        return new ActionHandler() {
            @Override
            public boolean apply(ActionContext ctx) {
                Player p = ctx.player();
                if (p == null) return false;
                var attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
                if (attr == null) return false;
                ctx.data().put("before", attr.getBaseValue());   // persisted for the revert
                attr.setBaseValue(attr.getBaseValue() + amount);
                return true;
            }

            @Override
            public void revert(ActionContext ctx) {
                Player p = ctx.player();
                var attr = p == null ? null : p.getAttribute(Attribute.MOVEMENT_SPEED);
                if (attr != null && ctx.data().get("before") instanceof Number n) attr.setBaseValue(n.doubleValue());
            }

            @Override
            public boolean revertible() {
                return true;
            }

            @Override
            public String describe(ActionContext ctx) {
                return "speed boost +" + amount;   // shown in /hc effects
            }
        };
    }
}
```

```yaml
- type: speed-boost
  amount: 0.05
  duration: 1h
```

### Handler options

Override these as needed:

| Method | Default | Meaning |
|---|---|---|
| `revertible()` | false | `revert` does something. Timed actions that aren't revertible just expire. |
| `requiresOnline()` | true | The player must be online to apply and revert. If false, the action also runs for offline players (Headcount still queues messages and the like). |
| `needsPlayer()` | true | False for actions that make sense in global hooks (broadcasts, console commands). |
| `maintain(ctx)` | none | Called about once a second while active and the player is online, and on join and respawn. Use it to keep an effect applied through death or milk. |
| `repeat(ctx)` | `apply` | Called on each `repeat:` interval. |
| `stateful()` | false | The effect exists only while tracked (like deny lists or shields). `duration: permanent` then keeps it tracked forever instead of applying it once. |
| `stackKey()` | null | Identifies "the same action" for stacking, e.g. the effect it applies. |
| `describe(ctx)` | the type | One line for `/hc effects`. |

`ActionContext` gives you `player()` (null if offline), `playerId()`, `offlinePlayer()`, `variables()`, `render(template)` (MiniMessage with variables and placeholders), `format(template)` (plain text for commands), `data()` (a persisted map for revert state; use strings, numbers, booleans, lists and maps), `source()` (e.g. `outcome:penalty-1`), `instanceId()`, `duration()` and `timed()`.

### When to register

Register in your `onEnable`. Headcount parses statuses and actions on the first server tick after every plugin has enabled. Registering later makes Headcount re-parse its configs, and tracked actions of your type that were waiting since startup are rebuilt.

## Running Headcount's own tests

```bash
./gradlew test
```

On a running server, `/hca selftest` checks every configured item, menu, message, action list and status reference.
