package com.gradychastain.headcount.integration.moderation;

import com.gradychastain.headcount.util.Cfg;
import litebans.api.Database;

import java.util.UUID;
import java.util.logging.Logger;

/** LiteBans: punishments through its commands (its API is read-only), mute checks through its API. */
public final class LiteBansHook extends CommandModerationHook {
    public LiteBansHook(Cfg templates, Logger logger) {
        super("LiteBans", templates, logger);
    }

    @Override
    public boolean supports(Kind kind) {
        return kind != Kind.JAIL && super.supports(kind);
    }

    @Override
    public Boolean isMuted(UUID uuid) {
        try {
            return Database.get().isPlayerMuted(uuid, null);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
