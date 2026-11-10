package com.gradychastain.headcount.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** A timed action ended for a player. Fired on the main thread. */
public final class ActionRevertedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final java.util.UUID playerId;
    private final String type;
    private final String source;
    private final String instanceId;
    private final String reason;

    public ActionRevertedEvent(java.util.UUID playerId, String type, String source, String instanceId, String reason) {
        this.playerId = playerId;
        this.type = type;
        this.source = source;
        this.instanceId = instanceId;
        this.reason = reason;
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

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
