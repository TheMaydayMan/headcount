package com.gradychastain.headcount.integration.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import net.luckperms.api.track.Track;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** LuckPerms: nodes use LuckPerms' own expiry for temporary changes. */
public final class LuckPermsHook implements PermissionHook {
    private final LuckPerms lp;
    private final Logger logger;
    private final boolean useExpiry;
    private final ImmutableContextSet context;

    public LuckPermsHook(Logger logger, boolean useExpiry, String server) {
        this.lp = LuckPermsProvider.get();
        this.logger = logger;
        this.useExpiry = useExpiry;
        this.context = server == null || server.isBlank() ? ImmutableContextSet.empty() : ImmutableContextSet.of("server", server);
    }

    @Override public String name() { return "LuckPerms"; }
    @Override public boolean nativeExpiry() { return useExpiry; }
    @Override public boolean supportsTracks() { return true; }

    private void modify(UUID uuid, Consumer<User> change) {
        lp.getUserManager().modifyUser(uuid, change).exceptionally(t -> {
            logger.warning("LuckPerms change for " + uuid + " failed: " + t.getMessage());
            return null;
        });
    }

    private boolean expires(Duration expiry) {
        return useExpiry && expiry != null && !expiry.isZero() && !expiry.isNegative();
    }

    @Override
    public void setPermission(UUID player, String node, boolean value, Duration expiry) {
        var b = Node.builder(node).value(value).context(context);
        if (expires(expiry)) b.expiry(expiry.toMillis(), TimeUnit.MILLISECONDS);
        Node n = b.build();
        modify(player, u -> u.data().add(n));
    }

    @Override
    public void unsetPermission(UUID player, String node) {
        modify(player, u -> u.data().clear(n -> n.getKey().equalsIgnoreCase(node)));
    }

    @Override
    public void addGroup(UUID player, String group, Duration expiry) {
        var b = InheritanceNode.builder(group).context(context);
        if (expires(expiry)) b.expiry(expiry.toMillis(), TimeUnit.MILLISECONDS);
        InheritanceNode n = b.build();
        modify(player, u -> u.data().add(n));
    }

    @Override
    public void removeGroup(UUID player, String group) {
        modify(player, u -> u.data().clear(NodeType.INHERITANCE.predicate(n -> n.getGroupName().equalsIgnoreCase(group))));
    }

    @Override
    public String setPrimaryGroup(UUID player, String group) {
        String previous = primaryGroup(player);
        modify(player, u -> {
            u.data().add(InheritanceNode.builder(group).context(context).build());
            u.setPrimaryGroup(group);
        });
        return previous;
    }

    @Override
    public String primaryGroup(UUID player) {
        User u = lp.getUserManager().getUser(player);
        if (u == null) u = lp.getUserManager().loadUser(player).join();
        return u == null ? null : u.getPrimaryGroup();
    }

    @Override
    public boolean promote(UUID player, String track) {
        Track t = lp.getTrackManager().getTrack(track);
        if (t == null) {
            logger.warning("LuckPerms track '" + track + "' does not exist.");
            return false;
        }
        modify(player, u -> t.promote(u, context));
        return true;
    }

    @Override
    public boolean demote(UUID player, String track) {
        Track t = lp.getTrackManager().getTrack(track);
        if (t == null) {
            logger.warning("LuckPerms track '" + track + "' does not exist.");
            return false;
        }
        modify(player, u -> t.demote(u, context));
        return true;
    }

    @Override
    public void addPrefix(UUID player, String prefix, int priority, Duration expiry) {
        var b = PrefixNode.builder(prefix, priority).context(context);
        if (expires(expiry)) b.expiry(expiry.toMillis(), TimeUnit.MILLISECONDS);
        PrefixNode n = b.build();
        modify(player, u -> u.data().add(n));
    }

    @Override
    public void removePrefix(UUID player, String prefix, int priority) {
        modify(player, u -> u.data().clear(NodeType.PREFIX.predicate(n -> n.getMetaValue().equals(prefix) && n.getPriority() == priority)));
    }

    @Override
    public void addSuffix(UUID player, String suffix, int priority, Duration expiry) {
        var b = SuffixNode.builder(suffix, priority).context(context);
        if (expires(expiry)) b.expiry(expiry.toMillis(), TimeUnit.MILLISECONDS);
        SuffixNode n = b.build();
        modify(player, u -> u.data().add(n));
    }

    @Override
    public void clearMeta(UUID player, boolean suffix, int priority) {
        if (suffix) modify(player, u -> u.data().clear(NodeType.SUFFIX.predicate(n -> n.getPriority() == priority)));
        else modify(player, u -> u.data().clear(NodeType.PREFIX.predicate(n -> n.getPriority() == priority)));
    }

    @Override
    public void removeSuffix(UUID player, String suffix, int priority) {
        modify(player, u -> u.data().clear(NodeType.SUFFIX.predicate(n -> n.getMetaValue().equals(suffix) && n.getPriority() == priority)));
    }
}
