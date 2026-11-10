package com.gradychastain.headcount.integration.moderation;

import com.gradychastain.headcount.util.Cfg;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Runs console commands from templates. Used for LiteBans (whose API cannot create punishments) and for the
 * {@code commands} provider so any moderation plugin can be driven from config.
 * Template variables: {@code <player> <uuid> <reason> <duration> <jail>}.
 */
public class CommandModerationHook implements ModerationHook {
    private final String name;
    private final Cfg templates;
    private final Logger logger;

    public CommandModerationHook(String name, Cfg templates, Logger logger) {
        this.name = name;
        this.templates = templates;
        this.logger = logger;
    }

    @Override public String name() { return name; }

    private String key(Kind kind, boolean temp) {
        String base = kind.name().toLowerCase(Locale.ROOT);
        return temp ? "temp" + base : base;
    }

    @Override
    public boolean supports(Kind kind) {
        String t = templates.getString(key(kind, false), "");
        return !t.isBlank();
    }

    @Override
    public boolean apply(Kind kind, UUID uuid, String player, String reason, Duration duration, String jail) {
        String template = null;
        if (duration != null) template = templates.getString(key(kind, true), null);
        if (template == null || template.isBlank()) template = templates.getString(key(kind, false), "");
        if (template.isBlank()) return false;
        run(template, uuid, player, reason, duration, jail);
        return true;
    }

    @Override
    public boolean revoke(Kind kind, UUID uuid, String player) {
        if (kind == Kind.KICK || kind == Kind.WARN) return true;
        String template = templates.getString("un" + kind.name().toLowerCase(Locale.ROOT), "");
        if (template.isBlank()) return false;
        run(template, uuid, player, "", null, "");
        return true;
    }

    private void run(String template, UUID uuid, String player, String reason, Duration duration, String jail) {
        String cmd = template.replace("<player>", player == null ? uuid.toString() : player)
                .replace("<uuid>", uuid.toString())
                .replace("<reason>", reason == null ? "" : reason)
                .replace("<duration>", duration == null ? "" : ModerationHook.compact(duration))
                .replace("<jail>", jail == null ? "" : jail)
                .trim().replaceAll("\\s+", " ");
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        final String command = cmd;
        logger.fine("Moderation command: " + command);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
