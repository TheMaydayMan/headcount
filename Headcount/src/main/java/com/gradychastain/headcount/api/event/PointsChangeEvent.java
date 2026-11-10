package com.gradychastain.headcount.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** A player's points are about to change. The change can be modified or cancelled. Fired on the main thread. */
public final class PointsChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final java.util.UUID playerId;
    private final boolean nextCycle;
    private final String reason;
    private long delta;
    private boolean cancelled;

    public PointsChangeEvent(java.util.UUID playerId, long delta, boolean nextCycle, String reason) {
        this.playerId = playerId;
        this.delta = delta;
        this.nextCycle = nextCycle;
        this.reason = reason;
    }

    public java.util.UUID getPlayerId() {
        return playerId;
    }

    public long getDelta() {
        return delta;
    }

    public boolean isNextCycle() {
        return nextCycle;
    }

    public String getReason() {
        return reason;
    }

    public void setDelta(long delta) {
        this.delta = delta;
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
