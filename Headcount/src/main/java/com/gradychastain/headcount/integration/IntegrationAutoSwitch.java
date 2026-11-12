package com.gradychastain.headcount.integration;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.integration.AutoSwitch.Change;
import com.gradychastain.headcount.integration.AutoSwitch.Job;
import com.gradychastain.headcount.integration.AutoSwitch.Kind;
import com.gradychastain.headcount.integration.AutoSwitch.Mode;
import com.gradychastain.headcount.integration.AutoSwitch.Reason;
import com.gradychastain.headcount.integration.moderation.ModerationHook;
import com.gradychastain.headcount.util.YamlLines;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * integrations.yml {@code auto-switch}: at start-up, hands jobs to plugins that were just installed and takes them back
 * from plugins that were removed (rules in {@link AutoSwitch}). Which plugins were installed at the last start is kept
 * in {@value #RECORD}. The change is written into integrations.yml line by line, so comments and layout stay, and runs
 * before {@link Integrations#load()} so a switched PvP provider also gets /pvp.
 */
public final class IntegrationAutoSwitch {
    static final String RECORD = "detected-plugins.txt";
    private static final String FILE = "integrations.yml";

    private static final List<String> MODERATION_PLUGINS = List.of("litebans", "libertybans", "advancedban", "essentials");
    /** Every provider that is another plugin, for the record. */
    static final List<String> PROVIDERS = List.of("pvpmanager", "placeholderapi", "vault", "luckperms", "litebans", "libertybans",
            "advancedban", "essentials", "discordsrv", "itemsadder", "oraxen", "nexo", "floodgate", "geyser");

    private final HeadcountPlugin plugin;

    public IntegrationAutoSwitch(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    /** The jobs and who may take them. Vault is never handed permissions and Essentials never moderation: see docs/integrations.md. */
    static List<Job> jobs() {
        List<Job> jobs = new ArrayList<>();
        jobs.add(new Job("pvp.provider", Kind.SINGLE, List.of("pvpmanager"), List.of("pvpmanager"), "included"));
        jobs.add(new Job("placeholders.provider", Kind.SINGLE, List.of("placeholderapi"), List.of("placeholderapi"), "included"));
        jobs.add(new Job("economy.provider", Kind.SINGLE, List.of("vault"), List.of("vault"), "none"));
        jobs.add(new Job("permissions.provider", Kind.SINGLE, List.of("luckperms"), List.of("luckperms", "vault"), "included"));
        jobs.add(new Job("moderation.provider", Kind.SINGLE, List.of("litebans", "libertybans", "advancedban"), MODERATION_PLUGINS, "included"));
        for (ModerationHook.Kind k : ModerationHook.Kind.values()) {
            jobs.add(new Job("moderation.kinds." + k.name().toLowerCase(Locale.ROOT), Kind.OVERRIDE, List.of(), MODERATION_PLUGINS, "default"));
        }
        jobs.add(new Job("discord.provider", Kind.SINGLE, List.of("discordsrv"), List.of("discordsrv"), "none"));
        jobs.add(new Job("items.providers", Kind.LIST, List.of("itemsadder", "oraxen", "nexo"), List.of("itemsadder", "oraxen", "nexo"), "none"));
        jobs.add(new Job("bedrock.provider", Kind.SINGLE, List.of("floodgate", "geyser"), List.of("floodgate", "geyser"), "none"));
        jobs.add(new Job("player-state.provider", Kind.SINGLE, List.of("essentials"), List.of("essentials"), "included"));
        return jobs;
    }

    public void run() {
        File folder = plugin.getDataFolder();
        File file = new File(folder, FILE);
        File recordFile = new File(folder, RECORD);
        boolean on = plugin.configs().integrations().getBool("auto-switch", true);
        boolean fresh = plugin.configs().created(FILE);
        Set<String> seen = readRecord(recordFile);
        Mode mode = !on ? Mode.RECORD_ONLY : fresh ? Mode.FRESH : seen != null ? Mode.NORMAL : Mode.RECORD_ONLY;

        Set<String> enabled = new LinkedHashSet<>();
        Set<String> installed = new LinkedHashSet<>();
        for (String id : PROVIDERS) {
            Plugin p = Bukkit.getPluginManager().getPlugin(Integrations.pluginName(id));
            if (p == null) continue;
            installed.add(id);
            if (p.isEnabled()) enabled.add(id);
        }

        YamlConfiguration actual = YamlConfiguration.loadConfiguration(file);
        AutoSwitch.Result result = AutoSwitch.decide(mode, jobs(), actual::get, seen == null ? Set.of() : seen, enabled, installed, this::canDo);

        if (!result.changes().isEmpty()) apply(file, result.changes(), fresh);
        if (on && seen == null && !fresh && !result.record().isEmpty()) {
            plugin.getLogger().info("integrations.yml auto-switch: noted the plugins installed now (" + names(result.record())
                    + "). From the next start, adding or removing one of them switches the jobs it can do.");
        }
        if (seen == null || !seen.equals(result.record())) writeRecord(recordFile, result.record());
    }

    /** Vault only takes money when an economy plugin has registered with it. */
    private boolean canDo(Job job, String provider) {
        if (job.key().equals("economy.provider") && provider.equals("vault")) {
            try {
                return Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class) != null;
            } catch (LinkageError e) {
                return false;
            }
        }
        return true;
    }

    private void apply(File file, List<Change> changes, boolean fresh) {
        String text;
        try {
            text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("integrations.yml auto-switch: couldn't read the file (" + e.getMessage() + "), so nothing was switched.");
            return;
        }
        List<String> lines = YamlLines.lines(text);
        List<Change> done = new ArrayList<>();
        for (Change c : changes) {
            if (YamlLines.set(lines, c.key(), AutoSwitch.yaml(c.to()))) {
                done.add(c);
            } else {
                plugin.getLogger().warning("integrations.yml auto-switch: " + why(c, fresh) + ", but " + c.key()
                        + " isn't written in a form Headcount can change (or isn't in the file). Set it to "
                        + AutoSwitch.yaml(c.to()) + " yourself if you want that.");
            }
        }
        if (done.isEmpty()) return;
        try {
            Path target = file.toPath();
            Path tmp = target.resolveSibling(FILE + ".tmp");
            Files.writeString(tmp, YamlLines.join(lines, text), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().warning("integrations.yml auto-switch: couldn't save the file (" + e.getMessage() + "), so nothing was switched.");
            return;
        }
        for (Change c : done) plugin.getLogger().info(line(c, fresh));
        plugin.getLogger().info("integrations.yml auto-switch made " + (done.size() == 1 ? "this change" : "these changes")
                + ". Set auto-switch: false in integrations.yml to choose every provider yourself.");
        plugin.configs().reload(FILE);
    }

    /** "pvp.provider is now 'pvpmanager' (was 'included'), because ... To undo ..." */
    private String line(Change c, boolean fresh) {
        String from = AutoSwitch.yaml(c.from());
        String to = AutoSwitch.yaml(c.to());
        boolean quote = !(c.to() instanceof List);
        String q = quote ? "'" : "";
        StringBuilder sb = new StringBuilder("integrations.yml: ").append(c.key()).append(" is now ").append(q).append(to).append(q)
                .append(" (was ").append(q).append(from).append(q).append("), because ").append(why(c, fresh)).append(". ");
        boolean restart = c.key().equals("pvp.provider");
        List<Reason> added = c.reasons().stream().filter(Reason::added).toList();
        List<Reason> removed = c.reasons().stream().filter(r -> !r.added()).toList();
        if (c.to() instanceof List) {
            sb.append("To undo, edit the list and run /hca reload.");
        } else if (removed.isEmpty()) {
            sb.append("To undo, set it back to '").append(from).append("' and ").append(restart ? "restart the server" : "run /hca reload")
                    .append(". It won't be switched again unless ").append(display(added.getFirst().provider())).append(" is removed.");
        } else {
            String gone = removed.getFirst().provider();
            boolean comesBack = jobs().stream().anyMatch(j -> j.key().equals(c.key()) && j.takeover().contains(gone));
            sb.append(comesBack ? "Installing " + display(gone) + " again switches it back."
                    : "To use " + display(gone) + " again, install it and set " + c.key() + " back to '" + from + "'.");
        }
        return sb.toString();
    }

    private String why(Change c, boolean fresh) {
        List<String> parts = new ArrayList<>();
        List<String> added = c.reasons().stream().filter(Reason::added).map(r -> display(r.provider())).toList();
        List<String> removed = c.reasons().stream().filter(r -> !r.added()).map(r -> display(r.provider())).toList();
        if (!removed.isEmpty()) parts.add(String.join(" and ", removed) + (removed.size() == 1 ? " was" : " were") + " installed at the last start and isn't now");
        if (!added.isEmpty()) {
            String who = String.join(" and ", added);
            if (!removed.isEmpty()) parts.add(who + " is installed");
            else parts.add(fresh ? who + (added.size() == 1 ? " is" : " are") + " installed (first start with this integrations.yml)"
                    : who + (added.size() == 1 ? " is" : " are") + " new since the last start");
        }
        return String.join(", and ", parts);
    }

    private static String display(String provider) {
        Plugin p = Bukkit.getPluginManager().getPlugin(Integrations.pluginName(provider));
        return p != null ? p.getName() : Integrations.pluginName(provider);
    }

    private static String names(Set<String> providers) {
        return String.join(", ", providers.stream().map(IntegrationAutoSwitch::display).toList());
    }

    /** The providers recorded at the last start, or null when there is no record yet. */
    static Set<String> readRecord(File f) {
        if (!f.exists()) return null;
        Set<String> out = new LinkedHashSet<>();
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String t = line.strip().toLowerCase(Locale.ROOT);
                if (!t.isEmpty() && !t.startsWith("#")) out.add(t);
            }
        } catch (IOException e) {
            return null;
        }
        return out;
    }

    private void writeRecord(File f, Set<String> providers) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Plugins Headcount could hand a job to (integrations.yml) that were installed at the last start.\n");
        sb.append("# integrations.yml auto-switch compares the next start with this list. Headcount keeps it up to date itself.\n");
        for (String p : providers) sb.append(p).append('\n');
        try {
            Files.writeString(f.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't save " + RECORD + " (" + e.getMessage() + "). integrations.yml auto-switch may repeat a switch next start.");
        }
    }
}
