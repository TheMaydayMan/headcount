package com.gradychastain.headcount.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** A timed action started for a player. Fired on the main thread. */
public final class ActionAppliedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final java.util.UUID playerId;
    private final String type;
    private final String source;
    private final String instanceId;

    public ActionAppliedEvent(java.util.UUID playerId, String type, String source, String instanceId) {
        this.playerId = playerId;
        this.type = type;
        this.source = source;
        this.instanceId = instanceId;
    }

    public java.util.UUID getPlayerId() {
        return playerId;
    }

    public String getType() {
        return type;
    }

    public String getSource() {
        return source;
    }

    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
