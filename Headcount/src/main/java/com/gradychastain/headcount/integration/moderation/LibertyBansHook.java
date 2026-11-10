package com.gradychastain.headcount.integration.moderation;

import space.arim.libertybans.api.LibertyBans;
import space.arim.libertybans.api.PlayerVictim;
import space.arim.libertybans.api.PunishmentType;
import space.arim.omnibus.OmnibusProvider;

import java.time.Duration;
import java.util.UUID;
import java.util.logging.Logger;

/** LibertyBans through its API (obtained from the Omnibus registry). */
public final class LibertyBansHook implements ModerationHook {
    private final LibertyBans api;
    private final Logger logger;

    private LibertyBansHook(LibertyBans api, Logger logger) {
        this.api = api;
        this.logger = logger;
    }

    public static LibertyBansHook create(Logger logger) {
        return OmnibusProvider.getOmnibus().getRegistry().getProvider(LibertyBans.class)
                .map(api -> new LibertyBansHook(api, logger)).orElse(null);
    }

    @Override public String name() { return "LibertyBans"; }

    @Override
    public boolean supports(Kind kind) {
        return kind != Kind.JAIL;
    }

    private static PunishmentType type(Kind kind) {
        return switch (kind) {
            case BAN -> PunishmentType.BAN;
            case MUTE -> PunishmentType.MUTE;
            case WARN -> PunishmentType.WARN;
            case KICK -> PunishmentType.KICK;
            case JAIL -> null;
        };
    }

    @Override
    public boolean apply(Kind kind, UUID uuid, String name, String reason, Duration duration, String jail) {
        PunishmentType type = type(kind);
        if (type == null) return false;
        Duration d = duration == null || kind == Kind.KICK ? Duration.ZERO : duration;
        api.getDrafter().draftBuilder().type(type).victim(PlayerVictim.of(uuid)).reason(reason == null ? "" : reason)
                .duration(d).build().enactPunishment()
                .exceptionally(t -> {
                    logger.warning("LibertyBans punishment failed: " + t.getMessage());
                    return null;
                });
        return true;
    }

    @Override
    public boolean revoke(Kind kind, UUID uuid, String name) {
        if (kind == Kind.KICK || kind == Kind.WARN) return true;
        PunishmentType type = type(kind);
        if (type == null) return false;
        api.getRevoker().revokeByTypeAndVictim(type, PlayerVictim.of(uuid)).undoPunishment()
                .exceptionally(t -> {
                    logger.warning("LibertyBans revoke failed: " + t.getMessage());
                    return null;
                });
        return true;
    }
}
