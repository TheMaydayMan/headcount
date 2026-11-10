package com.gradychastain.headcount.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** A new cycle has started (after vaults were filled). Fired on the main thread. */
public final class CycleStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final int cycleId;
    private final long startMs;
    private final long endMs;

    public CycleStartEvent(int cycleId, long startMs, long endMs) {
        this.cycleId = cycleId;
        this.startMs = startMs;
        this.endMs = endMs;
    }

    public int getCycleId() {
        return cycleId;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
