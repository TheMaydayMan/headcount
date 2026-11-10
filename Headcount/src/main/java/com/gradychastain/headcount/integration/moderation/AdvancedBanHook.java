package com.gradychastain.headcount.integration.moderation;

import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.UUIDManager;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.UUID;
import java.util.logging.Logger;

/** AdvancedBan through its API. Database work runs off the main thread like AdvancedBan's own commands. */
public final class AdvancedBanHook implements ModerationHook {
    private final Plugin plugin;
    private final Logger logger;
    private final String operator;
    private final boolean silent;

    public AdvancedBanHook(Plugin plugin, String operator, boolean silent) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.operator = operator;
        this.silent = silent;
    }

    @Override public String name() { return "AdvancedBan"; }

    @Override
    public boolean supports(Kind kind) {
        return kind != Kind.JAIL;
    }

    private static String abUuid(UUID uuid, String name) {
        String id = name == null ? null : UUIDManager.get().getUUID(name);
        return id == null ? uuid.toString().replace("-", "") : id;
    }

    @Override
    public boolean apply(Kind kind, UUID uuid, String name, String reason, Duration duration, String jail) {
        boolean temp = duration != null;
        PunishmentType type = switch (kind) {
            case BAN -> temp ? PunishmentType.TEMP_BAN : PunishmentType.BAN;
            case MUTE -> temp ? PunishmentType.TEMP_MUTE : PunishmentType.MUTE;
            case WARN -> temp ? PunishmentType.TEMP_WARNING : PunishmentType.WARNING;
            case KICK -> PunishmentType.KICK;
            case JAIL -> null;
        };
        if (type == null) return false;
        long end = temp ? System.currentTimeMillis() + duration.toMillis() : -1L;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Punishment.create(name, abUuid(uuid, name), reason, operator, type, end, null, silent);
            } catch (RuntimeException | LinkageError e) {
                logger.warning("AdvancedBan punishment failed: " + e);
            }
        });
        return true;
    }

    @Override
    public boolean revoke(Kind kind, UUID uuid, String name) {
        if (kind == Kind.KICK || kind == Kind.WARN) return true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String id = abUuid(uuid, name);
                Punishment p = kind == Kind.BAN ? PunishmentManager.get().getBan(id) : PunishmentManager.get().getMute(id);
                if (p != null) p.delete();
            } catch (RuntimeException | LinkageError e) {
                logger.warning("AdvancedBan revoke failed: " + e);
            }
        });
        return true;
    }

    @Override
    public Boolean isMuted(UUID uuid) {
        try {
            return PunishmentManager.get().isMuted(uuid.toString().replace("-", ""));
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
