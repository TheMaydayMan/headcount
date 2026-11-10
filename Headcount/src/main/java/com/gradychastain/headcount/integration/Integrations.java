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
import com.gradychastain.headcount.integration.permission.LuckPermsHook;
import com.gradychastain.headcount.integration.permission.PermissionHook;
import com.gradychastain.headcount.integration.permission.VaultPermissionHook;
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
 * Chooses which installed plugin handles each job, as configured in integrations.yml. Every category falls back to
 * a built-in behavior (or to "unavailable") when its plugin is missing, so Headcount runs with none of them.
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
    private EssentialsHook essentials;
    private final Map<String, String> summary = new LinkedHashMap<>();

    public Integrations(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled(String pluginName) {
        Plugin p = Bukkit.getPluginManager().getPlugin(pluginName);
        return p != null && p.isEnabled();
    }

    private static String provider(Cfg cfg, String key) {
        return cfg.getString(key, "auto").trim().toLowerCase(Locale.ROOT);
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

        // PvP
        String pvpChoice = provider(cfg, "pvp.provider");
        pvp = null;
        if (!pvpChoice.equals("none") && enabled("PvPManager") && (pvpChoice.equals("auto") || pvpChoice.equals("pvpmanager"))) {
            if (pvpManagerHook == null) pvpManagerHook = attempt("PvPManager", () -> new PvPManagerHook(plugin));
            pvp = pvpManagerHook;
        } else if (pvpChoice.equals("pvpmanager")) {
            plugin.getLogger().warning("integrations.yml selects PvPManager but it is not installed; PvP statuses use their fallbacks.");
        }
        summary.put("PvP", pvp == null ? "none (PlaceholderAPI statuses and built-in checks still work)" : pvp.name());
        applyPvPToggleDefault(cfg);

        // PlaceholderAPI
        String papiChoice = provider(cfg, "placeholderapi.enabled");
        boolean usePapi = enabled("PlaceholderAPI") && !papiChoice.equals("false") && !papiChoice.equals("none");
        papi = usePapi ? attempt("PlaceholderAPI", PlaceholderApiHook::new) : null;
        plugin.text().setExternal(papi == null ? null : papi::apply);
        if (papi != null && cfg.getBool("placeholderapi.register-expansion", true) && expansion == null) {
            expansion = attempt("PlaceholderAPI expansion", () -> {
                HeadcountExpansion e = new HeadcountExpansion(plugin);
                e.register();
                return e;
            });
        }
        summary.put("Placeholders", papi == null ? "built-in only" : "PlaceholderAPI" + (expansion != null ? " (+ %headcount_*% expansion)" : ""));

        // Economy
        String ecoChoice = provider(cfg, "economy.provider");
        economy = null;
        if (!ecoChoice.equals("none") && enabled("Vault")) economy = attempt("Vault economy", VaultEconomyHook::create);
        summary.put("Money", economy == null ? "none (money actions are skipped)" : economy.name());

        // Permissions
        String permChoice = provider(cfg, "permissions.provider");
        permissions = null;
        if ((permChoice.equals("auto") || permChoice.equals("luckperms")) && enabled("LuckPerms")) {
            permissions = attempt("LuckPerms", () -> new LuckPermsHook(plugin.getLogger(),
                    cfg.getBool("permissions.luckperms.use-expiry", true), cfg.getString("permissions.luckperms.server-context", "")));
        }
        if (permissions == null && (permChoice.equals("auto") || permChoice.equals("vault")) && enabled("Vault")) {
            permissions = attempt("Vault permissions", () -> VaultPermissionHook.create(plugin.getLogger()));
        }
        summary.put("Permissions", permissions == null ? "none (permission and rank actions are skipped)" : permissions.name());

        // Moderation: each kind goes to the first provider in order that supports it, falling back to vanilla.
        loadModeration(cfg);

        // Discord
        String discordChoice = provider(cfg, "discord.provider");
        discord = !discordChoice.equals("none") && enabled("DiscordSRV") ? attempt("DiscordSRV", () -> new DiscordSrvHook(plugin.getLogger())) : null;
        summary.put("Discord", discord == null ? "none (discord actions are skipped)" : discord.name());

        // Custom items
        items.clear();
        List<String> wanted = cfg.getStringList("items.providers").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        boolean auto = wanted.isEmpty() || wanted.contains("auto");
        if ((auto || wanted.contains("itemsadder")) && enabled("ItemsAdder")) addItemHook("ItemsAdder", ItemsAdderHook::new);
        if ((auto || wanted.contains("oraxen")) && enabled("Oraxen")) addItemHook("Oraxen", OraxenHook::new);
        if ((auto || wanted.contains("nexo")) && enabled("Nexo")) addItemHook("Nexo", NexoHook::new);
        summary.put("Custom items", items.isEmpty() ? "none" : String.join(", ", items.stream().map(ItemHook::prefix).toList()));

        // Bedrock
        String bedrockChoice = provider(cfg, "bedrock.provider");
        bedrock = null;
        if ((bedrockChoice.equals("auto") || bedrockChoice.equals("floodgate")) && enabled("floodgate")) bedrock = attempt("Floodgate", FloodgateHook::new);
        if (bedrock == null && (bedrockChoice.equals("auto") || bedrockChoice.equals("geyser")) && enabled("Geyser-Spigot")) bedrock = attempt("Geyser", GeyserHook::new);
        summary.put("Bedrock", bedrock == null ? "none" : bedrock.name());

        // Essentials (statuses)
        String essChoice = provider(cfg, "essentials.enabled");
        essentials = null;
        if (!essChoice.equals("false") && !essChoice.equals("none") && enabled("Essentials")) {
            essentials = attempt("EssentialsX", () -> new EssentialsHook((com.earth2me.essentials.Essentials) Bukkit.getPluginManager().getPlugin("Essentials")));
        }
        summary.put("Essentials statuses", essentials == null ? "none" : "EssentialsX");
    }

    private void addItemHook(String name, Supplier<ItemHook> factory) {
        ItemHook hook = attempt(name, factory);
        if (hook != null) items.add(hook);
    }

    private void loadModeration(Cfg cfg) {
        moderation.clear();
        String choice = provider(cfg, "moderation.provider");
        String operator = cfg.getString("moderation.operator-name", "Headcount");
        boolean silent = cfg.getBool("moderation.silent", false);
        List<ModerationHook> order = new ArrayList<>();
        List<String> names = switch (choice) {
            case "auto" -> List.of("litebans", "libertybans", "advancedban", "essentials");
            case "vanilla", "none" -> List.of();
            default -> List.of(choice);
        };
        for (String n : names) {
            ModerationHook hook = switch (n) {
                case "litebans" -> enabled("LiteBans") ? attempt("LiteBans", () -> new LiteBansHook(cfg.getSection("moderation.litebans-commands"), plugin.getLogger())) : null;
                case "libertybans" -> enabled("LibertyBans") ? attempt("LibertyBans", () -> LibertyBansHook.create(plugin.getLogger())) : null;
                case "advancedban" -> enabled("AdvancedBan") ? attempt("AdvancedBan", () -> new AdvancedBanHook(plugin, operator, silent)) : null;
                case "essentials" -> enabled("Essentials") ? attempt("EssentialsX", () -> new EssentialsModerationHook((com.earth2me.essentials.Essentials) Bukkit.getPluginManager().getPlugin("Essentials"))) : null;
                case "commands" -> new CommandModerationHook("commands", cfg.getSection("moderation.commands"), plugin.getLogger());
                default -> {
                    plugin.getLogger().warning("Unknown moderation provider '" + n + "' in integrations.yml.");
                    yield null;
                }
            };
            if (hook != null) order.add(hook);
        }
        // Essentials still provides jails when another plugin handles bans.
        if (choice.equals("auto") || choice.equals("litebans") || choice.equals("libertybans") || choice.equals("advancedban")) {
            if (enabled("Essentials") && order.stream().noneMatch(h -> h instanceof EssentialsModerationHook)) {
                ModerationHook ess = attempt("EssentialsX", () -> new EssentialsModerationHook((com.earth2me.essentials.Essentials) Bukkit.getPluginManager().getPlugin("Essentials")));
                if (ess != null) order.add(ess);
            }
        }
        VanillaModerationHook vanilla = new VanillaModerationHook(operator,
                (key, reason) -> plugin.lang().get(key, Vars.of("reason", reason == null ? "" : reason)));
        order.add(vanilla);
        StringBuilder sb = new StringBuilder();
        for (ModerationHook.Kind kind : ModerationHook.Kind.values()) {
            for (ModerationHook h : order) {
                if (h.supports(kind)) {
                    moderation.put(kind, h);
                    break;
                }
            }
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(kind.name().toLowerCase(Locale.ROOT)).append('=').append(moderation.get(kind).name());
        }
        summary.put("Moderation", sb.toString());
    }

    /**
     * PvPManager only lets players use /pvp with pvpmanager.command.pvp, which defaults to false, and PvP starts on.
     * Headcount's default rules block head moves and trades while PvP is on, so by default players get that
     * permission (only its default changes: an explicit deny in a permissions plugin still wins).
     */
    private void applyPvPToggleDefault(Cfg cfg) {
        restorePvPToggleDefault();
        if (pvp == null || pvp != pvpManagerHook || !cfg.getBool("pvp.let-players-toggle", true)) return;
        Permission perm = plugin.getServer().getPluginManager().getPermission("pvpmanager.command.pvp");
        if (perm == null || perm.getDefault() == PermissionDefault.TRUE) return;
        raisedPvPToggleFrom = perm.getDefault();
        raisedPvPToggle = perm;
        perm.setDefault(PermissionDefault.TRUE);
        Bukkit.getOnlinePlayers().forEach(org.bukkit.entity.Player::updateCommands);
        summary.put("PvP", pvp.name() + " (every player may use /pvp; pvp.let-players-toggle)");
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

    public Map<String, String> summary() {
        return summary;
    }

    public PvPHook pvp() { return pvp; }
    public PlaceholderApiHook papi() { return papi; }
    public EconomyHook economy() { return economy; }
    public PermissionHook permissions() { return permissions; }
    public ModerationHook moderation(ModerationHook.Kind kind) { return moderation.get(kind); }
    public DiscordHook discord() { return discord; }
    public EssentialsHook essentials() { return essentials; }

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
