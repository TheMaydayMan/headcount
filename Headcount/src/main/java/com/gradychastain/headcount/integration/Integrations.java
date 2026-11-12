package com.gradychastain.headcount.integration;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.integration.bedrock.BedrockHook;
import com.gradychastain.headcount.integration.bedrock.FloodgateHook;
import com.gradychastain.headcount.integration.bedrock.GeyserHook;
import com.gradychastain.headcount.integration.discord.DiscordHook;
import com.gradychastain.headcount.integration.discord.DiscordSrvHook;
import com.gradychastain.headcount.integration.economy.EconomyHook;
import com.gradychastain.headcount.integration.economy.VaultEconomyHook;
import com.gradychastain.headcount.integration.essentials.EssentialsHook;
import com.gradychastain.headcount.integration.essentials.IncludedPlayerState;
import com.gradychastain.headcount.integration.essentials.PlayerStateHook;
import com.gradychastain.headcount.integration.item.ItemHook;
import com.gradychastain.headcount.integration.item.ItemsAdderHook;
import com.gradychastain.headcount.integration.item.NexoHook;
import com.gradychastain.headcount.integration.item.OraxenHook;
import com.gradychastain.headcount.integration.moderation.AdvancedBanHook;
import com.gradychastain.headcount.integration.moderation.CommandModerationHook;
import com.gradychastain.headcount.integration.moderation.EssentialsModerationHook;
import com.gradychastain.headcount.integration.moderation.LiteBansHook;
import com.gradychastain.headcount.integration.moderation.LibertyBansHook;
import com.gradychastain.headcount.integration.moderation.ModerationHook;
import com.gradychastain.headcount.integration.moderation.VanillaModerationHook;
import com.gradychastain.headcount.integration.papi.HeadcountExpansion;
import com.gradychastain.headcount.integration.papi.PlaceholderApiHook;
import com.gradychastain.headcount.integration.permission.IncludedPermissions;
import com.gradychastain.headcount.integration.permission.LuckPermsHook;
import com.gradychastain.headcount.integration.permission.PermissionHook;
import com.gradychastain.headcount.integration.permission.VaultPermissionHook;
import com.gradychastain.headcount.integration.pvp.IncludedPvP;
import com.gradychastain.headcount.integration.pvp.PvPHook;
import com.gradychastain.headcount.integration.pvp.PvPManagerHook;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Chooses who handles each job, as configured in integrations.yml: Headcount's own "included" version where there
 * is one, a named plugin, or "none" (the feature is off). A chosen plugin that is missing falls back to "included"
 * (or "none" where there is no included version), with a warning, so Headcount runs with none of them installed.
 */
public final class Integrations {
    private final HeadcountPlugin plugin;

    private PvPHook pvp;
    private PvPManagerHook pvpManagerHook;
    /** PvPManager's /pvp permission while its default is raised by pvp.let-players-toggle, and the original default. */
    private Permission raisedPvPToggle;
    private PermissionDefault raisedPvPToggleFrom;
    private PlaceholderApiHook papi;
    private HeadcountExpansion expansion;
    private EconomyHook economy;
    private PermissionHook permissions;
    private final Map<ModerationHook.Kind, ModerationHook> moderation = new EnumMap<>(ModerationHook.Kind.class);
    private DiscordHook discord;
    private final List<ItemHook> items = new ArrayList<>();
    private BedrockHook bedrock;
    private PlayerStateHook playerState;
    private IncludedPvP includedPvP;
    private IncludedPermissions includedPermissions;
    private IncludedPlayerState includedState;
    /** The other plugin's /pvp while the included PvP holds the label. */
    private org.bukkit.command.Command displacedPvP;
    private final Map<String, String> summary = new LinkedHashMap<>();

    public Integrations(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled(String pluginName) {
        Plugin p = Bukkit.getPluginManager().getPlugin(pluginName);
        return p != null && p.isEnabled();
    }

    /**
     * The provider chosen for a category. Configs from before 1.1 may still say "auto": that becomes the first of
     * {@code autoOrder} that is installed, else {@code def}, with a warning to pick one.
     */
    private String choice(Cfg cfg, String key, String def, String... autoOrder) {
        String v = cfg.getString(key, def).trim().toLowerCase(Locale.ROOT);
        if (!v.equals("auto")) return v;
        String picked = def;
        for (String name : autoOrder) {
            if (enabled(pluginName(name))) {
                picked = name;
                break;
            }
        }
        plugin.getLogger().warning("integrations.yml " + key + ": 'auto' is no longer an option, so '" + picked
                + "' is used. Set it to one of the listed providers.");
        return picked;
    }

    static String pluginName(String provider) {
        return switch (provider) {
            case "pvpmanager" -> "PvPManager";
            case "placeholderapi" -> "PlaceholderAPI";
            case "vault" -> "Vault";
            case "luckperms" -> "LuckPerms";
            case "litebans" -> "LiteBans";
            case "libertybans" -> "LibertyBans";
            case "advancedban" -> "AdvancedBan";
            case "essentials" -> "Essentials";
            case "discordsrv" -> "DiscordSRV";
            case "itemsadder" -> "ItemsAdder";
            case "oraxen" -> "Oraxen";
            case "nexo" -> "Nexo";
            case "floodgate" -> "floodgate";
            case "geyser" -> "Geyser-Spigot";
            default -> provider;
        };
    }

    /** A chosen plugin that isn't installed: say so once and use the fallback ("included" or "none"). */
    private String missing(String key, String chosen, String fallback) {
        plugin.getLogger().warning("integrations.yml " + key + " is '" + chosen + "', but " + pluginName(chosen)
                + " isn't installed or failed to load. Using '" + fallback + "' instead.");
        return fallback;
    }

    private String included() {
        return plugin.lang().plain("admin.integrations.included");
    }

    /** Safely constructs a hook; linkage problems (wrong plugin version) are logged instead of crashing startup. */
    private <T> T attempt(String what, Supplier<T> factory) {
        try {
            return factory.get();
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not hook into " + what + " (" + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + "). Headcount will use its fallback for this feature.");
            return null;
        }
    }

    public void load() {
        Cfg cfg = plugin.configs().integrations();
        summary.clear();

        // PvP: included | pvpmanager | none
        if (includedPvP == null) {
            includedPvP = new IncludedPvP(plugin);
            plugin.getServer().getPluginManager().registerEvents(includedPvP, plugin);
            plugin.getServer().getPluginManager().registerEvents(new com.gradychastain.headcount.integration.pvp.PetProtection(plugin), plugin);
        }
        String pvpChoice = choice(cfg, "pvp.provider", "included", "pvpmanager");
        pvp = null;
        switch (pvpChoice) {
            case "pvpmanager" -> {
                if (enabled("PvPManager") && pvpManagerHook == null) pvpManagerHook = attempt("PvPManager", () -> new PvPManagerHook(plugin));
                if (enabled("PvPManager") && pvpManagerHook != null) pvp = pvpManagerHook;
                else pvpChoice = missing("pvp.provider", "pvpmanager", "included");
            }
            case "included", "none" -> { }
            default -> pvpChoice = unknown("pvp.provider", pvpChoice, "included");
        }
        if (pvpChoice.equals("included")) pvp = includedPvP;
        includedPvP.setActive(pvp == includedPvP);
        takeOverPvPCommand(pvp == includedPvP);
        sum("pvp", pvp == null ? none("pvp") : pvp == includedPvP ? included() : pvp.name());
        applyPvPToggleDefault(cfg);

        // Placeholders: included | placeholderapi | none
        String papiChoice;
        if (cfg.has("placeholderapi.enabled")) {
            // Before 1.1: placeholderapi.enabled: auto | true | false
            String old = cfg.getString("placeholderapi.enabled", "auto").toLowerCase(Locale.ROOT);
            papiChoice = old.equals("false") || old.equals("none") ? "included" : "placeholderapi";
        } else {
            papiChoice = choice(cfg, "placeholders.provider", "included", "placeholderapi");
        }
        Cfg papiCfg = cfg.isSection("placeholders.placeholderapi") ? cfg.getSection("placeholders.placeholderapi") : cfg.getSection("placeholderapi");
        if (papiChoice.equals("placeholderapi") && !enabled("PlaceholderAPI")) papiChoice = missing("placeholders.provider", "placeholderapi", "included");
        if (!List.of("included", "placeholderapi", "none").contains(papiChoice)) papiChoice = unknown("placeholders.provider", papiChoice, "included");
        papi = papiChoice.equals("placeholderapi") ? attempt("PlaceholderAPI", PlaceholderApiHook::new) : null;
        plugin.text().setExternal(papi == null ? null : papi::apply);
        plugin.text().setPlaceholdersEnabled(!papiChoice.equals("none"));
        if (papi != null && papiCfg.getBool("register-expansion", true) && expansion == null) {
            expansion = attempt("PlaceholderAPI expansion", () -> {
                HeadcountExpansion e = new HeadcountExpansion(plugin);
                e.register();
                return e;
            });
        }
        sum("placeholders", papiChoice.equals("none") ? none("placeholders-off") : papi == null ? none("placeholders")
                : "PlaceholderAPI" + (expansion != null ? plugin.lang().plain("admin.integrations.papi-expansion") : ""));

        // Money: vault | none
        String ecoChoice = choice(cfg, "economy.provider", "none", "vault");
        economy = null;
        if (ecoChoice.equals("vault")) {
            if (enabled("Vault")) economy = attempt("Vault economy", VaultEconomyHook::create);
            if (economy == null) missing("economy.provider", "vault", "none");
        } else if (!ecoChoice.equals("none")) {
            unknown("economy.provider", ecoChoice, "none");
        }
        sum("money", economy == null ? none("money") : economy.name());

        // Permissions: included | luckperms | vault | none
        if (includedPermissions == null) {
            includedPermissions = new IncludedPermissions(plugin);
            plugin.getServer().getPluginManager().registerEvents(includedPermissions, plugin);
        }
        String permChoice = choice(cfg, "permissions.provider", "included", "luckperms", "vault");
        permissions = null;
        switch (permChoice) {
            case "luckperms" -> {
                if (enabled("LuckPerms")) permissions = attempt("LuckPerms", () -> new LuckPermsHook(plugin.getLogger(),
                        cfg.getBool("permissions.luckperms.use-expiry", true), cfg.getString("permissions.luckperms.server-context", "")));
                if (permissions == null) permChoice = missing("permissions.provider", "luckperms", "included");
            }
            case "vault" -> {
                if (enabled("Vault")) permissions = attempt("Vault permissions", () -> VaultPermissionHook.create(plugin.getLogger()));
                if (permissions == null) permChoice = missing("permissions.provider", "vault", "included");
            }
            case "included", "none" -> { }
            default -> permChoice = unknown("permissions.provider", permChoice, "included");
        }
        if (permChoice.equals("included")) permissions = includedPermissions;
        includedPermissions.setActive(permissions == includedPermissions);
        sum("permissions", permissions == null ? none("permissions") : permissions == includedPermissions ? included() : permissions.name());

        // Moderation: each kind goes to its own provider (moderation.kinds), else the main one, else included.
        loadModeration(cfg);

        // Discord: discordsrv | none
        String discordChoice = choice(cfg, "discord.provider", "none", "discordsrv");
        discord = null;
        if (discordChoice.equals("discordsrv")) {
            if (enabled("DiscordSRV")) discord = attempt("DiscordSRV", () -> new DiscordSrvHook(plugin.getLogger()));
            if (discord == null) missing("discord.provider", "discordsrv", "none");
        } else if (!discordChoice.equals("none")) {
            unknown("discord.provider", discordChoice, "none");
        }
        sum("discord", discord == null ? none("discord") : discord.name());

        // Custom items: any of itemsadder, oraxen, nexo (an empty list or [none] turns them off)
        items.clear();
        List<String> wanted = cfg.getStringList("items.providers").stream().map(x -> x.trim().toLowerCase(Locale.ROOT)).toList();
        if (wanted.contains("auto")) {
            plugin.getLogger().warning("integrations.yml items.providers: 'auto' is no longer an option, so every installed provider is used. List them instead.");
            wanted = List.of("itemsadder", "oraxen", "nexo");
        }
        for (String w : wanted) {
            switch (w) {
                case "itemsadder" -> { if (enabled("ItemsAdder")) addItemHook("ItemsAdder", ItemsAdderHook::new); }
                case "oraxen" -> { if (enabled("Oraxen")) addItemHook("Oraxen", OraxenHook::new); }
                case "nexo" -> { if (enabled("Nexo")) addItemHook("Nexo", NexoHook::new); }
                case "none" -> { }
                default -> unknown("items.providers", w, "none");
            }
        }
        sum("custom-items", items.isEmpty() ? none("custom-items") : String.join(", ", items.stream().map(ItemHook::prefix).toList()));

        // Bedrock: floodgate | geyser | none
        String bedrockChoice = choice(cfg, "bedrock.provider", "none", "floodgate", "geyser");
        bedrock = null;
        switch (bedrockChoice) {
            case "floodgate" -> {
                if (enabled("floodgate")) bedrock = attempt("Floodgate", FloodgateHook::new);
                if (bedrock == null) missing("bedrock.provider", "floodgate", "none");
            }
            case "geyser" -> {
                if (enabled("Geyser-Spigot")) bedrock = attempt("Geyser", GeyserHook::new);
                if (bedrock == null) missing("bedrock.provider", "geyser", "none");
            }
            case "none" -> { }
            default -> unknown("bedrock.provider", bedrockChoice, "none");
        }
        sum("bedrock", bedrock == null ? none("bedrock") : bedrock.name());

        // Player states (afk, vanished, god, jailed, muted): included | essentials | none
        if (includedState == null) {
            includedState = new IncludedPlayerState(plugin);
            plugin.getServer().getPluginManager().registerEvents(includedState, plugin);
        }
        String stateChoice;
        if (cfg.has("essentials.enabled")) {
            // Before 1.1: essentials.enabled: auto | false
            String old = cfg.getString("essentials.enabled", "auto").toLowerCase(Locale.ROOT);
            stateChoice = old.equals("false") || old.equals("none") || !enabled("Essentials") ? "included" : "essentials";
        } else {
            stateChoice = choice(cfg, "player-state.provider", "included", "essentials");
        }
        playerState = null;
        switch (stateChoice) {
            case "essentials" -> {
                if (enabled("Essentials")) playerState = attempt("EssentialsX", () -> new EssentialsHook((com.earth2me.essentials.Essentials) Bukkit.getPluginManager().getPlugin("Essentials")));
                if (playerState == null) stateChoice = missing("player-state.provider", "essentials", "included");
            }
            case "included", "none" -> { }
            default -> stateChoice = unknown("player-state.provider", stateChoice, "included");
        }
        if (stateChoice.equals("included")) playerState = includedState;
        includedState.setActive(playerState == includedState);
        sum("player-state", playerState == null ? none("player-state") : playerState == includedState ? included() : playerState.name());
    }

    private String unknown(String key, String value, String fallback) {
        plugin.getLogger().warning("integrations.yml " + key + ": '" + value + "' isn't an option. Using '" + fallback + "' instead.");
        return fallback;
    }

    /**
     * When the included PvP is chosen, /pvp is Headcount's even if another plugin (PvPManager) also has a /pvp.
     * The change is made in the command map; a server restart is needed for it to take effect after a reload.
     */
    private void takeOverPvPCommand(boolean ours) {
        org.bukkit.command.PluginCommand cmd = plugin.getCommand("pvp");
        if (cmd == null) return;
        Map<String, org.bukkit.command.Command> known = Bukkit.getCommandMap().getKnownCommands();
        if (ours) {
            org.bukkit.command.Command current = known.get("pvp");
            if (current != null && current != cmd) {
                displacedPvP = current;
                known.put("pvp", cmd);
            }
        } else if (displacedPvP != null) {
            known.put("pvp", displacedPvP);
            displacedPvP = null;
        }
    }

    /** Re-applies the included permissions to online players (after a hard reset emptied them). */
    public void refreshIncludedPermissions() {
        if (includedPermissions != null) includedPermissions.setActive(permissions == includedPermissions);
    }

    /** Settings for Headcount's own jail; configs from before 1.1 named the section vanilla-jail. */
    public Cfg includedJail() {
        Cfg cfg = plugin.configs().integrations();
        return cfg.getSection("moderation.included-jail").merge(cfg.getSection("moderation.vanilla-jail"));
    }

    /** Settings for Headcount's own mute; configs from before 1.1 named the section vanilla-mute. */
    public Cfg includedMute() {
        Cfg cfg = plugin.configs().integrations();
        return cfg.getSection("moderation.included-mute").merge(cfg.getSection("moderation.vanilla-mute"));
    }

    private void addItemHook(String name, Supplier<ItemHook> factory) {
        ItemHook hook = attempt(name, factory);
        if (hook != null) items.add(hook);
    }

    private void loadModeration(Cfg cfg) {
        moderation.clear();
        String main = choice(cfg, "moderation.provider", "included", "litebans", "libertybans", "advancedban", "essentials");
        if (main.equals("vanilla")) main = "included";
        String operator = cfg.getString("moderation.operator-name", "Headcount");
        boolean silent = cfg.getBool("moderation.silent", false);
        VanillaModerationHook included = new VanillaModerationHook(operator,
                (key, reason) -> plugin.lang().get(key, Vars.of("reason", reason == null ? "" : reason)));
        Map<String, ModerationHook> built = new java.util.HashMap<>();
        StringBuilder sb = new StringBuilder();
        Cfg kinds = cfg.getSection("moderation.kinds");
        for (ModerationHook.Kind kind : ModerationHook.Kind.values()) {
            String id = kind.name().toLowerCase(Locale.ROOT);
            String chosen = kinds.getString(id, "").trim().toLowerCase(Locale.ROOT);
            if (chosen.isEmpty() || chosen.equals("default")) chosen = main;
            ModerationHook h = null;
            if (!chosen.equals("none")) {
                String name = chosen;
                h = chosen.equals("included") ? included : built.computeIfAbsent(name, n -> moderationHook(cfg, n, operator, silent));
                if (h == null || !h.supports(kind)) {
                    // A plugin that can't do this kind (LibertyBans has no jail) leaves it to the included one.
                    if (h == null) missing(chosen.equals(main) ? "moderation.provider" : "moderation.kinds." + id, chosen, "included");
                    h = included;
                }
            }
            if (h != null) moderation.put(kind, h);
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(id).append('=').append(h == null ? "none" : h == included ? "included" : h.name());
        }
        sum("moderation", sb.toString());
    }

    private ModerationHook moderationHook(Cfg cfg, String n, String operator, boolean silent) {
        return switch (n) {
            case "litebans" -> enabled("LiteBans") ? attempt("LiteBans", () -> new LiteBansHook(cfg.getSection("moderation.litebans-commands"), plugin.getLogger())) : null;
            case "libertybans" -> enabled("LibertyBans") ? attempt("LibertyBans", () -> LibertyBansHook.create(plugin.getLogger())) : null;
            case "advancedban" -> enabled("AdvancedBan") ? attempt("AdvancedBan", () -> new AdvancedBanHook(plugin, operator, silent)) : null;
            case "essentials" -> enabled("Essentials") ? attempt("EssentialsX", () -> new EssentialsModerationHook((com.earth2me.essentials.Essentials) Bukkit.getPluginManager().getPlugin("Essentials"))) : null;
            case "commands" -> new CommandModerationHook("commands", cfg.getSection("moderation.commands"), plugin.getLogger());
            default -> {
                plugin.getLogger().warning("integrations.yml: unknown moderation provider '" + n + "'.");
                yield null;
            }
        };
    }

    /**
     * PvPManager only lets players use /pvp with pvpmanager.command.pvp, which defaults to false, and PvP starts on.
     * Headcount's default rules block head moves and trades while PvP is on, so by default players get that
     * permission (only its default changes: an explicit deny in a permissions plugin still wins).
     */
    private void applyPvPToggleDefault(Cfg cfg) {
        restorePvPToggleDefault();
        boolean let = cfg.has("pvp.let-players-toggle") ? cfg.getBool("pvp.let-players-toggle", true) // before 1.1
                : cfg.getBool("pvp.pvpmanager.let-players-toggle", true);
        if (pvp == null || pvp != pvpManagerHook || !let) return;
        Permission perm = plugin.getServer().getPluginManager().getPermission("pvpmanager.command.pvp");
        if (perm == null || perm.getDefault() == PermissionDefault.TRUE) return;
        raisedPvPToggleFrom = perm.getDefault();
        raisedPvPToggle = perm;
        perm.setDefault(PermissionDefault.TRUE);
        Bukkit.getOnlinePlayers().forEach(org.bukkit.entity.Player::updateCommands);
        sum("pvp", pvp.name() + plugin.lang().plain("admin.integrations.pvp-toggle"));
    }

    private void restorePvPToggleDefault() {
        if (raisedPvPToggle == null) return;
        raisedPvPToggle.setDefault(raisedPvPToggleFrom);
        raisedPvPToggle = null;
    }

    public void unload() {
        restorePvPToggleDefault();
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (Throwable ignored) {
                // PlaceholderAPI may already be disabled
            }
            expansion = null;
        }
    }

    /** One line of the summary; the category name comes from lang admin.integrations.category.&lt;id&gt;. */
    private void sum(String id, String value) {
        summary.put(plugin.lang().plain("admin.integrations.category." + id), value);
    }

    private String none(String id) {
        return plugin.lang().plain("admin.integrations.none." + id);
    }

    public Map<String, String> summary() {
        return summary;
    }

    public PvPHook pvp() { return pvp; }
    public PlaceholderApiHook papi() { return papi; }
    public EconomyHook economy() { return economy; }
    public PermissionHook permissions() { return permissions; }
    public ModerationHook moderation(ModerationHook.Kind kind) { return moderation.get(kind); }
    public DiscordHook discord() { return discord; }
    public PlayerStateHook playerState() { return playerState; }
    public IncludedPvP includedPvP() { return includedPvP; }

    public boolean isBedrock(UUID uuid) {
        return bedrock != null && bedrock.isBedrock(uuid);
    }

    /** Creates a custom item from an ID like {@code itemsadder:ns:id}, {@code oraxen:id} or {@code nexo:id}. */
    public ItemStack customItem(String fullId) {
        int colon = fullId.indexOf(':');
        if (colon < 0) return null;
        String prefix = fullId.substring(0, colon).toLowerCase(Locale.ROOT);
        String id = fullId.substring(colon + 1);
        for (ItemHook hook : items) {
            if (hook.prefix().equals(prefix)) {
                try {
                    return hook.create(id);
                } catch (RuntimeException | LinkageError e) {
                    plugin.getLogger().warning("Custom item " + fullId + " failed: " + e.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    /** The full custom ID (with prefix) of an item, or null. */
    public String customIdOf(ItemStack item) {
        for (ItemHook hook : items) {
            try {
                String id = hook.idOf(item);
                if (id != null) return hook.prefix() + ":" + id;
            } catch (RuntimeException | LinkageError ignored) {
                // not from this plugin
            }
        }
        return null;
    }

    public boolean hasCustomItems() {
        return !items.isEmpty();
    }
}
