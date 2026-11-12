package com.gradychastain.headcount;

import com.gradychastain.headcount.action.ActionManager;
import com.gradychastain.headcount.action.DenyListener;
import com.gradychastain.headcount.action.types.BossBars;
import com.gradychastain.headcount.action.types.DenyAction;
import com.gradychastain.headcount.action.types.Types;
import com.gradychastain.headcount.api.HeadcountAPI;
import com.gradychastain.headcount.api.HeadcountAPIImpl;
import com.gradychastain.headcount.api.HeadcountProvider;
import com.gradychastain.headcount.audit.AuditLog;
import com.gradychastain.headcount.command.AdminCommand;
import com.gradychastain.headcount.command.HeadcountCommand;
import com.gradychastain.headcount.command.TradeCommand;
import com.gradychastain.headcount.config.ConfigFiles;
import com.gradychastain.headcount.cycle.CycleManager;
import com.gradychastain.headcount.gui.GuiItemGuard;
import com.gradychastain.headcount.gui.GuiManager;
import com.gradychastain.headcount.head.DuplicateGuard;
import com.gradychastain.headcount.head.HeadRefresher;
import com.gradychastain.headcount.head.HeadService;
import com.gradychastain.headcount.head.PlacedHeadListener;
import com.gradychastain.headcount.hook.HookManager;
import com.gradychastain.headcount.integration.Integrations;
import com.gradychastain.headcount.kill.KillManager;
import com.gradychastain.headcount.outcome.OutcomeManager;
import com.gradychastain.headcount.placeholder.Placeholders;
import com.gradychastain.headcount.player.Deliveries;
import com.gradychastain.headcount.player.PlayerManager;
import com.gradychastain.headcount.player.PlayerStore;
import com.gradychastain.headcount.points.PointsService;
import com.gradychastain.headcount.reset.ResetManager;
import com.gradychastain.headcount.scoring.LiveStats;
import com.gradychastain.headcount.scoring.ScoreService;
import com.gradychastain.headcount.shop.ShopManager;
import com.gradychastain.headcount.sidebar.SidebarManager;
import com.gradychastain.headcount.status.CombatTracker;
import com.gradychastain.headcount.status.StatusManager;
import com.gradychastain.headcount.storage.Database;
import com.gradychastain.headcount.text.Lang;
import com.gradychastain.headcount.text.Text;
import com.gradychastain.headcount.title.TitleManager;
import com.gradychastain.headcount.trade.TradeManager;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Formats;
import com.gradychastain.headcount.vault.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** Headcount: collect player heads each cycle, score them, spend points or take punishments, then reset. */
public final class HeadcountPlugin extends JavaPlugin implements Listener {
    private ConfigFiles configs;
    private Text text;
    private Lang lang;
    private Database db;
    private AuditLog audit;
    private Integrations integrations;
    private StatusManager statuses;
    private CombatTracker combat;
    private HeadService heads;
    private PlacedHeadListener placed;
    private DuplicateGuard dupes;
    private HeadRefresher refresher;
    private PlayerStore playerStore;
    private PlayerManager players;
    private Deliveries deliveries;
    private CycleManager cycles;
    private VaultManager vaults;
    private TradeManager trades;
    private ActionManager actions;
    private DenyListener denyListener;
    private BossBars bossBars;
    private OutcomeManager outcomes;
    private ScoreService scores;
    private LiveStats stats;
    private PointsService points;
    private ShopManager shop;
    private TitleManager titles;
    private SidebarManager sidebar;
    private HookManager hooks;
    private GuiManager guis;
    private KillManager kills;
    private Placeholders placeholders;
    private ResetManager resets;
    private HeadcountAPI api;
    private com.gradychastain.headcount.studio.StudioServer studio;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<Permission> registeredPermissions = new ArrayList<>();
    private volatile boolean loaded;

    @Override
    public void onEnable() {
        long started = System.currentTimeMillis();
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) getLogger().warning("Could not create the plugin folder.");
        configs = new ConfigFiles(this);
        configs.loadAll();
        text = new Text(getLogger());
        lang = new Lang(this, text);
        applyGeneralSettings();

        db = new Database(getLogger(), new File(getDataFolder(), configs.main().getString("storage.file", "headcount.db")));
        try {
            db.open();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Could not open the SQLite database; Headcount is disabling itself.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        audit = new AuditLog(getLogger(), new File(getDataFolder(), configs.main().getString("audit.folder", "audit")));
        audit.configure(configs.main().getSection("audit"), Formats.zone());

        heads = new HeadService(this);
        playerStore = new PlayerStore(db);
        players = new PlayerManager(this, playerStore);
        deliveries = new Deliveries(this);
        combat = new CombatTracker();
        statuses = new StatusManager(this);
        actions = new ActionManager(this);
        Types.registerAll(actions, this);
        bossBars = new BossBars(this);
        denyListener = new DenyListener(this);
        integrations = new Integrations(this);
        cycles = new CycleManager(this);
        vaults = new VaultManager(this);
        trades = new TradeManager(this);
        outcomes = new OutcomeManager(this);
        scores = new ScoreService(this);
        stats = new LiveStats(this);
        points = new PointsService(this);
        shop = new ShopManager(this);
        titles = new TitleManager(this);
        sidebar = new SidebarManager(this);
        hooks = new HookManager(this);
        guis = new GuiManager(this);
        kills = new KillManager(this);
        placeholders = new Placeholders(this);
        resets = new ResetManager(this);
        placed = new PlacedHeadListener(this);
        dupes = new DuplicateGuard(this);
        refresher = new HeadRefresher(this);
        text.setInternal(placeholders::resolve);

        // Before integrations load, so a provider it switches (including /pvp) is in use from this start.
        new com.gradychastain.headcount.integration.IntegrationAutoSwitch(this).run();
        integrations.load();

        var pm = getServer().getPluginManager();
        for (Listener l : List.of(this, players, deliveries, combat, actions, bossBars, denyListener, trades, guis, new GuiItemGuard(this),
                kills, placed, dupes, refresher, new com.gradychastain.headcount.hook.PetAndChestHooks(this))) {
            pm.registerEvents(l, this);
        }
        AdminCommand admin = new AdminCommand(this);
        register("headcount", new HeadcountCommand(this, admin));
        register("trade", new TradeCommand(this));
        register("pvp", integrations.includedPvP());
        register("hcadmin", admin);

        api = new HeadcountAPIImpl(this);
        HeadcountProvider.set(api);
        getServer().getServicesManager().register(HeadcountAPI.class, api, this, ServicePriority.Normal);

        // Everything that parses statuses or actions waits one tick, until every plugin is enabled, so other plugins
        // can register status sources and action types from their own onEnable.
        getServer().getScheduler().runTask(this, this::finishLoading);
        studio = new com.gradychastain.headcount.studio.StudioServer(this);
        studio.start();
        getLogger().info("Enabled in " + (System.currentTimeMillis() - started) + " ms. Setup finishes on the first server tick.");
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Command /" + name + " is missing from plugin.yml");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    private void applyGeneralSettings() {
        lang.load(configs.main().getString("general.language", "en_US"));
        Cfg units = configs.main().getSection("general.time-units");
        java.time.ZoneId zone;
        try {
            String z = configs.main().getString("cycle.time-zone", "UTC");
            zone = com.gradychastain.headcount.util.Zones.parse(z);
        } catch (RuntimeException e) {
            zone = java.time.ZoneId.of("UTC");
        }
        Formats.configure(lang.raw("time.days"), lang.raw("time.hours"), lang.raw("time.minutes"), lang.raw("time.seconds"),
                units.getInt("shown", 2), configs.main().getString("general.date-format", "yyyy-MM-dd HH:mm z"), zone);
    }

    private void finishLoading() {
        try {
            parseAll();
            statuses.reportErrors();
            heads.loadVoided();
            cycles.load();
            vaults.loadIndex();
            kills.load();
            placed.load();
            actions.loadActive();
            shop.load(configs.shop()); // stock depends on the cycle ID
            cycles.catchUp();
            stats.refreshNow();
            players.loadOnline();
            sidebar.reload();
            actions.maintainOnline();
            registerBypassPermissions();
            startTasks();
            loaded = true;
            new com.gradychastain.headcount.command.SelfTest(this).runQuiet();
            var c = cycles.current();
            getLogger().info(c == null
                    ? "Ready. No cycle is running yet. The first one starts " + (cycles.pendingFirstStart() < 0 ? "when an admin starts it" : Formats.date(cycles.pendingFirstStart())) + "."
                    : "Ready. Cycle " + c.id() + " ends " + Formats.date(c.endMs()) + " (" + Formats.duration(c.remainingMs()) + " left).");
            for (Map.Entry<String, String> e : integrations.summary().entrySet()) getLogger().info("  " + e.getKey() + ": " + e.getValue());
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Headcount couldn't finish loading, so it is disabling itself to protect your data.", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /** Parses every config section that uses statuses or actions. */
    private void parseAll() {
        statuses.load(configs.statuses());
        actions.loadNamed(configs.actions());
        outcomes.load(configs.outcomes());
        shop.load(configs.shop());
        titles.load(configs.titles());
        trades.load(configs.trade());
        hooks.reload();
        heads.reload();
        vaults.reload();
        scores.load(configs.scoring());
    }

    /** Re-parses action lists after a new action type is registered through the API. */
    public void reparseActions() {
        actions.saveAll();
        db.flush();
        actions.loadNamed(configs.actions());
        outcomes.load(configs.outcomes());
        shop.load(configs.shop());
        hooks.reload();
        actions.loadActive();
    }

    private void startTasks() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        var s = getServer().getScheduler();
        tasks.add(s.runTaskTimer(this, () -> {
            safely("cycle tick", cycles::tick);
            safely("action tick", actions::tick);
            safely("boss bar tick", bossBars::tick);
            safely("chat refresh", denyListener::refreshChat);
            safely("vault titles", vaults::refreshTitles);
        }, 20L, 20L));
        long activity = Math.max(20, configs.main().getMillis("activity.check-interval", 30_000) / 50);
        tasks.add(s.runTaskTimer(this, () -> safely("activity tick", players::tick), activity, activity));
        long refresh = Math.max(20, configs.main().getMillis("top.refresh-interval", 30_000) / 50);
        tasks.add(s.runTaskTimer(this, () -> safely("stats refresh", stats::refresh), refresh, refresh));
        tasks.add(s.runTaskTimer(this, () -> safely("combat prune", () -> combat.prune(600_000)), 6000L, 6000L));
    }

    private void safely(String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Error in " + what, e);
        }
    }

    /**
     * Registers bypass permissions so they also work with plain Bukkit permissions:
     * headcount.bypass.<gate>.<status> (default false) under headcount.bypass.<gate>.* and headcount.bypass.*.
     */
    private void registerBypassPermissions() {
        var pm = getServer().getPluginManager();
        for (Permission p : registeredPermissions) pm.removePermission(p);
        registeredPermissions.clear();
        Map<String, List<String>> gates = new HashMap<>();
        List<String> vault = configs.main().getStringList("vault.block-movement.statuses");
        gates.put("vault-move", vault);
        gates.put("vault-open", vault);
        Cfg trade = configs.trade();
        gates.put("trade-send", trade.getStringList("statuses.block-request"));
        gates.put("trade-send-target", trade.getStringList("statuses.block-request"));
        gates.put("trade-accept", trade.getStringList("statuses.block-accept"));
        gates.put("trade-accept-target", trade.getStringList("statuses.block-accept"));
        gates.put("trade-cancel", trade.getStringList("statuses.cancel-open"));
        Permission root = pm.getPermission("headcount.bypass.*");
        Map<String, Boolean> rootChildren = new HashMap<>();
        for (Map.Entry<String, List<String>> g : gates.entrySet()) {
            Map<String, Boolean> children = new HashMap<>();
            for (String st : g.getValue()) {
                String s = st.trim().toLowerCase(Locale.ROOT);
                String name = "headcount.bypass." + g.getKey() + "." + (s.startsWith("!") ? "not-" + s.substring(1) : s);
                add(name, "Bypass the '" + s + "' status for " + g.getKey(), null);
                children.put(name, true);
            }
            String wildcard = "headcount.bypass." + g.getKey() + ".*";
            add(wildcard, "Bypass every status for " + g.getKey(), children);
            rootChildren.put(wildcard, true);
        }
        Map<String, Boolean> denyChildren = new HashMap<>();
        for (String c : DenyAction.CATEGORIES) {
            add("headcount.bypass.deny." + c, "Ignore deny actions for " + c, null);
            denyChildren.put("headcount.bypass.deny." + c, true);
        }
        add("headcount.bypass.deny.*", "Ignore every deny action", denyChildren);
        rootChildren.put("headcount.bypass.deny.*", true);
        add("headcount.bypass.trade.range", "Trade at any distance", null);
        add("headcount.bypass.trade.cooldown", "Trade without waiting for the cooldown", null);
        rootChildren.put("headcount.bypass.trade.range", true);
        rootChildren.put("headcount.bypass.trade.cooldown", true);
        if (root != null) {
            root.getChildren().putAll(rootChildren);
            root.recalculatePermissibles();
        }
    }

    private void add(String name, String description, Map<String, Boolean> children) {
        var pm = getServer().getPluginManager();
        if (pm.getPermission(name) != null) pm.removePermission(name);
        Permission p = new Permission(name, description, PermissionDefault.FALSE, children);
        pm.addPermission(p);
        registeredPermissions.add(p);
    }

    /** /hca reload: cancels trades and closes menus first (a reload must never leave items in a GUI). */
    public void reloadAll() {
        trades.cancelAll("reload");
        guis.closeAll();
        vaults.closeAllViews();
        configs.loadAll();
        applyGeneralSettings();
        audit.configure(configs.main().getSection("audit"), Formats.zone());
        integrations.load();
        parseAll();
        statuses.reportErrors();
        sidebar.reload();
        titles.resyncMeta();
        refresher.refreshOnline();
        registerBypassPermissions();
        startTasks();
        if (studio != null) studio.reload();
        new com.gradychastain.headcount.command.SelfTest(this).runQuiet();
    }

    @Override
    public void onDisable() {
        loaded = false;
        if (studio != null) studio.stop();
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        try {
            if (trades != null) trades.shutdown();
            if (guis != null) guis.closeAll();
            if (vaults != null) {
                vaults.flushAll();
                vaults.closeAllViews();
            }
            if (players != null && cycles != null) players.flushPlaytime();
            if (actions != null) actions.saveAll();
            if (sidebar != null) sidebar.shutdown();
            if (bossBars != null) bossBars.hideAll();
            if (integrations != null) integrations.unload();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Error while shutting down", e);
        }
        if (api != null) getServer().getServicesManager().unregister(HeadcountAPI.class, api);
        HeadcountProvider.set(null);
        if (db != null) db.close();
        if (audit != null) audit.close();
    }

    // ---------------------------------------------------------------- small listeners

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (sidebar != null) sidebar.onQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && loaded) titles.onJoin(p);
        }, 40L);
    }

    // ---------------------------------------------------------------- accessors

    public boolean isLoaded() { return loaded; }
    public ConfigFiles configs() { return configs; }
    public Text text() { return text; }
    public Lang lang() { return lang; }
    public Database db() { return db; }
    public AuditLog audit() { return audit; }
    public Integrations integrations() { return integrations; }
    public StatusManager statuses() { return statuses; }
    public CombatTracker combat() { return combat; }
    public HeadService heads() { return heads; }
    public DuplicateGuard dupes() { return dupes; }
    public HeadRefresher refresher() { return refresher; }
    public PlayerStore playerStore() { return playerStore; }
    public PlayerManager players() { return players; }
    public Deliveries deliveries() { return deliveries; }
    public CycleManager cycles() { return cycles; }
    public VaultManager vaults() { return vaults; }
    public TradeManager trades() { return trades; }
    public ActionManager actions() { return actions; }
    public BossBars bossBars() { return bossBars; }
    public OutcomeManager outcomes() { return outcomes; }
    public ScoreService scores() { return scores; }
    public LiveStats stats() { return stats; }
    public PointsService points() { return points; }
    public ShopManager shop() { return shop; }
    public TitleManager titles() { return titles; }
    public SidebarManager sidebar() { return sidebar; }
    public HookManager hooks() { return hooks; }
    public GuiManager guis() { return guis; }
    public KillManager kills() { return kills; }
    public Placeholders placeholders() { return placeholders; }
    public ResetManager resets() { return resets; }
    public HeadcountAPI api() { return api; }
    public com.gradychastain.headcount.studio.StudioServer studio() { return studio; }
}
