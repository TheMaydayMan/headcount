package com.gradychastain.headcount.integration.moderation;

import io.papermc.paper.ban.BanListType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Built-in fallback: vanilla profile bans, kicks and warnings (a message), plus Headcount's own mutes and jails,
 * which are enforced while the matching action is active.
 */
public final class VanillaModerationHook implements ModerationHook {
    private final String source;
    private final BiFunction<String, String, Component> messages;

    /** @param messages renders a lang key with a {@code <reason>} variable */
    public VanillaModerationHook(String source, BiFunction<String, String, Component> messages) {
        this.source = source;
        this.messages = messages;
    }

    @Override public String name() { return "vanilla"; }

    @Override
    public boolean supports(Kind kind) {
        return true;
    }

    @Override
    public boolean enforcedByHeadcount(Kind kind) {
        return kind == Kind.MUTE || kind == Kind.JAIL;
    }

    @Override
    public boolean apply(Kind kind, UUID uuid, String name, String reason, Duration duration, String jail) {
        Player online = Bukkit.getPlayer(uuid);
        switch (kind) {
            case BAN -> {
                var profile = Bukkit.createProfile(uuid, name);
                Instant expires = duration == null ? null : Instant.now().plus(duration);
                Bukkit.getBanList(BanListType.PROFILE).addBan(profile, reason, expires, source);
                if (online != null) online.kick(messages.apply("moderation.banned-kick", reason));
            }
            case KICK -> {
                if (online != null) online.kick(messages.apply("moderation.kicked", reason));
            }
            case WARN -> {
                if (online != null) online.sendMessage(messages.apply("moderation.warned", reason));
            }
            case MUTE -> {
                if (online != null) online.sendMessage(messages.apply("moderation.muted", reason));
            }
            case JAIL -> {
                // Teleporting and restrictions are handled by the jail action itself.
            }
        }
        return true;
    }

    @Override
    public boolean revoke(Kind kind, UUID uuid, String name) {
        if (kind == Kind.BAN) Bukkit.getBanList(BanListType.PROFILE).pardon(Bukkit.createProfile(uuid, name));
        return true;
    }
}
