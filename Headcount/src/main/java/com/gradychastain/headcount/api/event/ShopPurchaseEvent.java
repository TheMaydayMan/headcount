package com.gradychastain.headcount.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/** A player is about to buy a shop entry. Cancel to stop the purchase. Fired on the main thread. */
public final class ShopPurchaseEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final org.bukkit.entity.Player player;
    private final String entryId;
    private final long price;
    private boolean cancelled;

    public ShopPurchaseEvent(org.bukkit.entity.Player player, String entryId, long price) {
        this.player = player;
        this.entryId = entryId;
        this.price = price;
    }

    public org.bukkit.entity.Player getPlayer() {
        return player;
    }

    public String getEntryId() {
        return entryId;
    }

    public long getPrice() {
        return price;
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
