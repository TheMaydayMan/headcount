package com.gradychastain.headcount.integration.permission;

import java.time.Duration;
import java.util.UUID;

/**
 * Permissions, groups, tracks and prefixes. {@code expiry} is null for permanent changes; providers that cannot
 * expire nodes themselves ignore it and Headcount reverts the change when the action ends.
 */
public interface PermissionHook {
    String name();

    /** Whether this provider expires temporary nodes on its own. */
    boolean nativeExpiry();

    boolean supportsTracks();

    void setPermission(UUID player, String node, boolean value, Duration expiry);

    void unsetPermission(UUID player, String node);

    void addGroup(UUID player, String group, Duration expiry);

    void removeGroup(UUID player, String group);

    /** @return the previous primary group, or null if unknown */
    String setPrimaryGroup(UUID player, String group);

    String primaryGroup(UUID player);

    boolean promote(UUID player, String track);

    boolean demote(UUID player, String track);

    void addPrefix(UUID player, String prefix, int priority, Duration expiry);

    void removePrefix(UUID player, String prefix, int priority);

    void addSuffix(UUID player, String suffix, int priority, Duration expiry);

    void removeSuffix(UUID player, String suffix, int priority);

    /** Removes every prefix (or suffix) this player has at the given priority. */
    void clearMeta(UUID player, boolean suffix, int priority);
}
