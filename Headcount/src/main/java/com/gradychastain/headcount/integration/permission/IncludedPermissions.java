package com.gradychastain.headcount.integration.permission;

import com.gradychastain.headcount.HeadcountPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Headcount's own permissions (integrations.yml permissions.provider: included): permission nodes given by the
 * permission action, stored in Headcount's database and applied with Bukkit permission attachments whenever the
 * player is online. Groups, tracks, prefixes and suffixes need LuckPerms or Vault; those actions are skipped with a
 * warning.
 */
public final class IncludedPermissions implements PermissionHook, Listener {
    private final HeadcountPlugin plugin;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private boolean active;

    public IncludedPermissions(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    public void setActive(boolean on) {
        active = on;
        for (UUID id : Set.copyOf(attachments.keySet())) detach(id);
        if (on) for (Player p : Bukkit.getOnlinePlayers()) attach(p);
    }

    private void unsupported(String what) {
        if (warned.add(what)) {
            plugin.getLogger().warning(what + " needs LuckPerms or Vault (integrations.yml permissions.provider); the included "
                    + "permissions only handle permission nodes. Skipped.");
        }
    }

    private Map<String, Boolean> stored(UUID uuid) {
        return plugin.db().sync(c -> {
            Map<String, Boolean> out = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT node, value FROM included_permissions WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.put(rs.getString(1), rs.getInt(2) != 0);
                }
            }
            return out;
        });
    }

    private void attach(Player p) {
        detach(p.getUniqueId());
        Map<String, Boolean> nodes = stored(p.getUniqueId());
        if (nodes.isEmpty()) return;
        PermissionAttachment a = p.addAttachment(plugin);
        nodes.forEach(a::setPermission);
        attachments.put(p.getUniqueId(), a);
        p.updateCommands();
    }

    private void detach(UUID id) {
        PermissionAttachment a = attachments.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (a != null && p != null) {
            p.removeAttachment(a);
            p.updateCommands();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (active) attach(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        attachments.remove(event.getPlayer().getUniqueId());
    }

    @Override public String name() { return "included"; }
    @Override public boolean nativeExpiry() { return false; }
    @Override public boolean supportsTracks() { return false; }

    @Override
    public void setPermission(UUID player, String node, boolean value, Duration expiry) {
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO included_permissions(uuid, node, value) VALUES (?,?,?)")) {
                ps.setString(1, player.toString());
                ps.setString(2, node);
                ps.setInt(3, value ? 1 : 0);
                ps.executeUpdate();
            }
        });
        Player p = Bukkit.getPlayer(player);
        if (p != null) Bukkit.getScheduler().runTask(plugin, () -> attach(p));
    }

    @Override
    public void unsetPermission(UUID player, String node) {
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM included_permissions WHERE uuid=? AND node=?")) {
                ps.setString(1, player.toString());
                ps.setString(2, node);
                ps.executeUpdate();
            }
        });
        Player p = Bukkit.getPlayer(player);
        if (p != null) Bukkit.getScheduler().runTask(plugin, () -> attach(p));
    }

    @Override public void addGroup(UUID player, String group, Duration expiry) { unsupported("The group action"); }
    @Override public void removeGroup(UUID player, String group) { unsupported("The group action"); }
    @Override public String setPrimaryGroup(UUID player, String group) { unsupported("The primary-group action"); return null; }
    @Override public String primaryGroup(UUID player) { return null; }
    @Override public boolean promote(UUID player, String track) { unsupported("The track action"); return false; }
    @Override public boolean demote(UUID player, String track) { unsupported("The track action"); return false; }
    @Override public void addPrefix(UUID player, String prefix, int priority, Duration expiry) { unsupported("Prefixes (the prefix action and title prefixes)"); }
    @Override public void removePrefix(UUID player, String prefix, int priority) { }
    @Override public void addSuffix(UUID player, String suffix, int priority, Duration expiry) { unsupported("Suffixes (the suffix action and title suffixes)"); }
    @Override public void removeSuffix(UUID player, String suffix, int priority) { }
    @Override public void clearMeta(UUID player, boolean suffix, int priority) { }
}
