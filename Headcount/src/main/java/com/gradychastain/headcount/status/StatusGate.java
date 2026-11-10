package com.gradychastain.headcount.status;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.text.Vars;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A list of statuses that block something (moving heads in the vault, sending a trade, ...).
 * Each status in a gate has its own message ({@code gates.<gate>.<status>} in the language file, falling back to
 * {@code gates.<gate>.default}) and its own bypass permission ({@code headcount.bypass.<gate>.<status>}).
 */
public final class StatusGate {
    private final HeadcountPlugin plugin;
    private final String id;
    private final List<String> statuses;
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    public StatusGate(HeadcountPlugin plugin, String id, List<String> statuses) {
        this.plugin = plugin;
        this.id = id;
        this.statuses = List.copyOf(statuses);
    }

    public String id() {
        return id;
    }

    public List<String> statuses() {
        return statuses;
    }

    /** The first blocking status the player has and does not bypass, or null when nothing blocks. */
    public String blocking(Player player) {
        for (String s : statuses) {
            String clean = s.trim().toLowerCase(Locale.ROOT);
            String permName = clean.startsWith("!") ? "not-" + clean.substring(1) : clean;
            if (player.hasPermission("headcount.bypass." + id + "." + permName) || player.hasPermission("headcount.bypass." + id + ".*")) continue;
            if (plugin.statuses().has(player, clean)) return clean;
        }
        return null;
    }

    public boolean isBlocked(Player player) {
        return blocking(player) != null;
    }

    /** Sends the message for a blocking status, at most once per {@code gates.message-cooldown} per player. */
    public void notify(Player player, String status, Vars vars) {
        long cooldown = plugin.configs().main().getMillis("gates.message-cooldown", 1000);
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        if (last != null && now - last < cooldown) return;
        lastMessage.put(player.getUniqueId(), now);
        String key = "gates." + id + "." + status.replace("!", "not-");
        if (!plugin.lang().has(key)) key = "gates." + id + ".default";
        Vars v = (vars == null ? Vars.empty() : vars.copy()).put("status", status);
        plugin.lang().send(player, key, v);
    }

    /** Checks and notifies. @return true when blocked */
    public boolean check(Player player, Vars vars) {
        String s = blocking(player);
        if (s == null) return false;
        notify(player, s, vars);
        return true;
    }
}
