package com.gradychastain.headcount.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** A trade completed and items were exchanged. Fired on the main thread. */
public final class TradeCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final org.bukkit.entity.Player playerA;
    private final org.bukkit.entity.Player playerB;
    private final java.util.List<org.bukkit.inventory.ItemStack> givenByA;
    private final java.util.List<org.bukkit.inventory.ItemStack> givenByB;

    public TradeCompleteEvent(org.bukkit.entity.Player playerA, org.bukkit.entity.Player playerB, java.util.List<org.bukkit.inventory.ItemStack> givenByA, java.util.List<org.bukkit.inventory.ItemStack> givenByB) {
        this.playerA = playerA;
        this.playerB = playerB;
        this.givenByA = givenByA;
        this.givenByB = givenByB;
    }

    public org.bukkit.entity.Player getPlayerA() {
        return playerA;
    }

    public org.bukkit.entity.Player getPlayerB() {
        return playerB;
    }

    public java.util.List<org.bukkit.inventory.ItemStack> getGivenByA() {
        return givenByA;
    }

    public java.util.List<org.bukkit.inventory.ItemStack> getGivenByB() {
        return givenByB;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
