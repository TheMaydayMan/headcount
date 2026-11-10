package com.gradychastain.headcount.integration.essentials;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Reads EssentialsX player state for statuses: afk, vanished, god, jailed, muted. */
public final class EssentialsHook {
    private final Essentials ess;

    public EssentialsHook(Essentials ess) {
        this.ess = ess;
    }

    public Essentials essentials() {
        return ess;
    }

    public Boolean check(Player player, String what) {
        User u;
        try {
            u = ess.getUser(player);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
        if (u == null) return null;
        return switch (what.toLowerCase(Locale.ROOT)) {
            case "afk" -> u.isAfk();
            case "vanished", "vanish" -> u.isVanished();
            case "god", "godmode" -> u.isGodModeEnabled();
            case "jailed", "jail" -> u.isJailed();
            case "muted", "mute" -> u.isMuted();
            default -> null;
        };
    }
}
