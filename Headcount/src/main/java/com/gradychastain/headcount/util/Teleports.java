package com.gradychastain.headcount.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Teleports Headcount makes itself (the jail, a release, the teleport action). The included PvP's combat tag blocks
 * teleports by commands and plugins, which would leave a tagged player out of a jail they were sent to, or stuck in
 * it at release; it lets these through. Teleport events fire synchronously, so marking the player around the call is
 * enough.
 */
public final class Teleports {
    private static final Set<UUID> OWN = new HashSet<>();

    private Teleports() {}

    public static boolean teleport(Player p, Location to) {
        OWN.add(p.getUniqueId());
        try {
            return p.teleport(to);
        } finally {
            OWN.remove(p.getUniqueId());
        }
    }

    /** True while Headcount is teleporting this player. */
    public static boolean isOwn(Player p) {
        return OWN.contains(p.getUniqueId());
    }
}
