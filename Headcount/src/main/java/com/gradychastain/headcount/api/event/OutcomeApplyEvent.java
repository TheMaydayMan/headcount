package com.gradychastain.headcount.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** An outcome rule is about to be applied at a cycle's end. Cancel to skip it. Fired on the main thread. */
public final class OutcomeApplyEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final java.util.UUID playerId;
    private final int cycleId;
    private final String ruleId;
    private final boolean punishment;
    private final Integer score;
    private boolean cancelled;

    public OutcomeApplyEvent(java.util.UUID playerId, int cycleId, String ruleId, boolean punishment, Integer score) {
        this.playerId = playerId;
        this.cycleId = cycleId;
        this.ruleId = ruleId;
        this.punishment = punishment;
        this.score = score;
    }

    public java.util.UUID getPlayerId() {
        return playerId;
    }

    public int getCycleId() {
        return cycleId;
    }

    public String getRuleId() {
        return ruleId;
    }

    public boolean isPunishment() {
        return punishment;
    }

    public Integer getScore() {
        return score;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
