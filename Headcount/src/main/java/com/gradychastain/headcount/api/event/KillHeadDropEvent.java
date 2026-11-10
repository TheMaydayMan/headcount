package com.gradychastain.headcount.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** A kill is about to drop the victim's head(s). The amount can be changed; cancel to drop nothing. Fired on the main thread. */
public final class KillHeadDropEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final org.bukkit.entity.Player killer;
    private final org.bukkit.entity.Player victim;
    private int amount;
    private boolean cancelled;

    public KillHeadDropEvent(org.bukkit.entity.Player killer, org.bukkit.entity.Player victim, int amount) {
        this.killer = killer;
        this.victim = victim;
        this.amount = amount;
    }

    public org.bukkit.entity.Player getKiller() {
        return killer;
    }

    public org.bukkit.entity.Player getVictim() {
        return victim;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
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
