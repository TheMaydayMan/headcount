package com.gradychastain.headcount.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A player's score was calculated. The score can be changed. Fired on the main thread at the end of a cycle, and
 * also for previews ({@code /hc score}, {@code %headcount_score%}, the sidebar, {@code /hca cycle simulate}) with
 * {@link #isPreview()} true, so a changed score shows in previews too. Previews can fire every second per player:
 * keep listeners cheap, and do nothing with side effects when it is a preview.
 */
public final class ScoreCalculatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final java.util.UUID playerId;
    private final int cycleId;
    private final java.util.Map<String, Double> variables;
    private final boolean preview;
    private int score;

    public ScoreCalculatedEvent(java.util.UUID playerId, int cycleId, int score, java.util.Map<String, Double> variables) {
        this(playerId, cycleId, score, variables, false);
    }

    public ScoreCalculatedEvent(java.util.UUID playerId, int cycleId, int score, java.util.Map<String, Double> variables, boolean preview) {
        this.playerId = playerId;
        this.cycleId = cycleId;
        this.score = score;
        this.variables = variables;
        this.preview = preview;
    }

    /** True for a preview; false for the real end-of-cycle score. */
    public boolean isPreview() {
        return preview;
    }

    public java.util.UUID getPlayerId() {
        return playerId;
    }

    public int getCycleId() {
        return cycleId;
    }

    public int getScore() {
        return score;
    }

    public java.util.Map<String, Double> getVariables() {
        return variables;
    }

    public void setScore(int score) {
        this.score = score;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
