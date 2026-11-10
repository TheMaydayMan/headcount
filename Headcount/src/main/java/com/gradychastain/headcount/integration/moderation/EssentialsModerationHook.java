package com.gradychastain.headcount.integration.moderation;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.util.UUID;

/** EssentialsX mutes (API) and jails (its jail commands, which handle teleporting in and out). */
public final class EssentialsModerationHook implements ModerationHook {
    private final Essentials ess;

    public EssentialsModerationHook(Essentials ess) {
        this.ess = ess;
    }

    @Override public String name() { return "EssentialsX"; }

    @Override
    public boolean supports(Kind kind) {
        return kind == Kind.MUTE || kind == Kind.JAIL;
    }

    @Override
    public boolean apply(Kind kind, UUID uuid, String name, String reason, Duration duration, String jail) {
        User user = ess.getUser(uuid);
        if (user == null) return false;
        if (kind == Kind.MUTE) {
            user.setMuted(true);
            user.setMuteTimeout(duration == null ? 0 : System.currentTimeMillis() + duration.toMillis());
            if (reason != null && !reason.isBlank()) user.setMuteReason(reason);
            return true;
        }
        if (kind == Kind.JAIL) {
            String time = duration == null ? "" : " " + ModerationHook.compact(duration);
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "essentials:togglejail " + user.getName() + " " + (jail == null ? "jail" : jail) + time);
        }
        return false;
    }

    @Override
    public boolean revoke(Kind kind, UUID uuid, String name) {
        User user = ess.getUser(uuid);
        if (user == null) return false;
        if (kind == Kind.MUTE) {
            user.setMuted(false);
            user.setMuteTimeout(0);
            return true;
        }
        if (kind == Kind.JAIL) {
            if (!user.isJailed()) return true;
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "essentials:togglejail " + user.getName());
        }
        return true;
    }

    @Override
    public Boolean isMuted(UUID uuid) {
        User user = ess.getUser(uuid);
        return user == null ? null : user.isMuted();
    }
}
