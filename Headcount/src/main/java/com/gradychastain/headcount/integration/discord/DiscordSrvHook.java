package com.gradychastain.headcount.integration.discord;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.util.DiscordUtil;

import java.util.logging.Logger;

/** Discord messages through DiscordSRV's linked channels. */
public final class DiscordSrvHook implements DiscordHook {
    private final Logger logger;

    public DiscordSrvHook(Logger logger) {
        this.logger = logger;
    }

    @Override public String name() { return "DiscordSRV"; }

    @Override
    public boolean send(String channel, String message) {
        try {
            if (!DiscordSRV.isReady) return false;
            DiscordSRV srv = DiscordSRV.getPlugin();
            TextChannel target = channel == null || channel.isBlank() ? srv.getMainTextChannel() : srv.getDestinationTextChannelForGameChannelName(channel);
            if (target == null) target = srv.getMainTextChannel();
            if (target == null) {
                logger.warning("DiscordSRV has no channel '" + channel + "' and no main channel; Discord message skipped.");
                return false;
            }
            DiscordUtil.sendMessage(target, message);
            return true;
        } catch (RuntimeException | LinkageError e) {
            logger.warning("DiscordSRV send failed: " + e);
            return false;
        }
    }
}
