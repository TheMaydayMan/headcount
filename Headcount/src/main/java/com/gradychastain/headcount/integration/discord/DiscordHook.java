package com.gradychastain.headcount.integration.discord;

/** Sends plain messages to Discord. */
public interface DiscordHook {
    String name();

    /** @param channel a DiscordSRV game channel name such as {@code global}; blank uses the main channel */
    boolean send(String channel, String message);
}
