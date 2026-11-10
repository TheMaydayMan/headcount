package com.gradychastain.headcount.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** A player joined the server for the first time ever. Fired on the main thread. */
public final class PlayerFirstJoinEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final org.bukkit.entity.Player player;

    public PlayerFirstJoinEvent(org.bukkit.entity.Player player) {
        this.player = player;
    }

    public org.bukkit.entity.Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
