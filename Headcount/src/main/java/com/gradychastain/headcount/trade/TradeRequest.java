package com.gradychastain.headcount.trade;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/** A pending trade request. */
final class TradeRequest {
    final UUID sender;
    final UUID target;
    final long created;
    final long expiresAt;
    BukkitTask expiry;

    TradeRequest(UUID sender, UUID target, long created, long expiresAt) {
        this.sender = sender;
        this.target = target;
        this.created = created;
        this.expiresAt = expiresAt;
    }

    UUID other(UUID who) {
        return who.equals(sender) ? target : sender;
    }
}
