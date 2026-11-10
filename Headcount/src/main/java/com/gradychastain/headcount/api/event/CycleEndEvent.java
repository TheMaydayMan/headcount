package com.gradychastain.headcount.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** A cycle has ended: results are stored, outcomes applied and vaults about to be cleared for the next cycle. Fired on the main thread. */
public final class CycleEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final com.gradychastain.headcount.cycle.CycleResults results;

    public CycleEndEvent(com.gradychastain.headcount.cycle.CycleResults results) {
        this.results = results;
    }

    public com.gradychastain.headcount.cycle.CycleResults getResults() {
        return results;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
