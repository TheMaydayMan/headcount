package com.gradychastain.headcount.integration.permission;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.Duration;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Permissions through Vault. Vault has no expiry, tracks or prefix priorities, so Headcount reverts temporary
 * changes itself and tracks are unsupported.
 */
public final class VaultPermissionHook implements PermissionHook {
    private final Permission perms;
    private final Chat chat;
    private final Logger logger;

    private VaultPermissionHook(Permission perms, Chat chat, Logger logger) {
        this.perms = perms;
        this.chat = chat;
        this.logger = logger;
    }

    public static VaultPermissionHook create(Logger logger) {
        RegisteredServiceProvider<Permission> p = Bukkit.getServicesManager().getRegistration(Permission.class);
        if (p == null) return null;
        RegisteredServiceProvider<Chat> c = Bukkit.getServicesManager().getRegistration(Chat.class);
        return new VaultPermissionHook(p.getProvider(), c == null ? null : c.getProvider(), logger);
    }

    private static OfflinePlayer op(UUID uuid) {
        return Bukkit.getOfflinePlayer(uuid);
    }

    @Override public String name() { return "Vault (" + perms.getName() + ")"; }
    @Override public boolean nativeExpiry() { return false; }
    @Override public boolean supportsTracks() { return false; }

    @Override
    public void setPermission(UUID player, String node, boolean value, Duration expiry) {
        if (value) {
            perms.playerAdd(null, op(player), node);
        } else {
            // Vault cannot store a negated node; the closest safe behavior is removing the grant.
            logger.warning("Setting " + node + " to false needs LuckPerms; removing the node through Vault instead.");
            perms.playerRemove(null, op(player), node);
        }
    }

    @Override
    public void unsetPermission(UUID player, String node) {
        perms.playerRemove(null, op(player), node);
    }

    @Override
    public void addGroup(UUID player, String group, Duration expiry) {
        perms.playerAddGroup(null, op(player), group);
    }

    @Override
    public void removeGroup(UUID player, String group) {
        perms.playerRemoveGroup(null, op(player), group);
    }

    @Override
    public String setPrimaryGroup(UUID player, String group) {
        String previous = primaryGroup(player);
        if (previous != null) perms.playerRemoveGroup(null, op(player), previous);
        perms.playerAddGroup(null, op(player), group);
        return previous;
    }

    @Override
    public String primaryGroup(UUID player) {
        return perms.getPrimaryGroup(null, op(player));
    }

    @Override
    public boolean promote(UUID player, String track) {
        logger.warning("Track promotion needs LuckPerms; Vault has no tracks.");
        return false;
    }

    @Override
    public boolean demote(UUID player, String track) {
        logger.warning("Track demotion needs LuckPerms; Vault has no tracks.");
        return false;
    }

    @Override
    public void addPrefix(UUID player, String prefix, int priority, Duration expiry) {
        if (chat == null) {
            logger.warning("Setting a prefix through Vault needs a Vault chat provider.");
            return;
        }
        chat.setPlayerPrefix(null, op(player), prefix);
    }

    @Override
    public void removePrefix(UUID player, String prefix, int priority) {
        if (chat != null && prefix.equals(chat.getPlayerPrefix(null, op(player)))) chat.setPlayerPrefix(null, op(player), "");
    }

    @Override
    public void addSuffix(UUID player, String suffix, int priority, Duration expiry) {
        if (chat == null) {
            logger.warning("Setting a suffix through Vault needs a Vault chat provider.");
            return;
        }
        chat.setPlayerSuffix(null, op(player), suffix);
    }

    @Override
    public void clearMeta(UUID player, boolean suffix, int priority) {
        if (chat == null) return;
        if (suffix) chat.setPlayerSuffix(null, op(player), "");
        else chat.setPlayerPrefix(null, op(player), "");
    }

    @Override
    public void removeSuffix(UUID player, String suffix, int priority) {
        if (chat != null && suffix.equals(chat.getPlayerSuffix(null, op(player)))) chat.setPlayerSuffix(null, op(player), "");
    }
}
