package com.gradychastain.headcount.api.action;

/**
 * Does the work of one configured action. Headcount handles chance, delay, conditions, duration, stacking, repeats,
 * persistence across restarts and offline players; a handler only applies and reverts.
 */
public interface ActionHandler {
    /**
     * Applies the action. Store anything needed to revert later in {@link ActionContext#data()}; it is persisted.
     *
     * @return false if the action could not be applied (it is then not tracked)
     */
    boolean apply(ActionContext ctx);

    /** Undoes the action when a timed duration ends. Only called when {@link #revertible()} is true. */
    default void revert(ActionContext ctx) {}

    /** True if {@link #revert} does something; timed actions that are not revertible simply expire. */
    default boolean revertible() {
        return false;
    }

    /**
     * True if the target must be online to apply and revert. When false, apply and revert also run for offline
     * players (for example permission changes through LuckPerms).
     */
    default boolean requiresOnline() {
        return true;
    }

    /** True if the action needs a target player at all (false for broadcasts and console commands in global hooks). */
    default boolean needsPlayer() {
        return true;
    }

    /** Called about once a second while a timed action is active and its target is online, and on join and respawn. */
    default void maintain(ActionContext ctx) {}

    /** Called each {@code repeat:} interval while active; defaults to applying again. */
    default void repeat(ActionContext ctx) {
        apply(ctx);
    }

    /**
     * True for effects that exist only while Headcount tracks them (deny lists, shields, bounties, locks). A
     * {@code permanent} duration keeps such actions tracked forever instead of applying them once.
     */
    default boolean stateful() {
        return false;
    }

    /** A key identifying "the same action" for stacking rules, e.g. the potion effect. Null uses the action's position. */
    default String stackKey() {
        return null;
    }

    /**
     * One line describing the action for /hc effects, as MiniMessage (escape any player-supplied text). Null uses the
     * type name.
     */
    default String describe(ActionContext ctx) {
        return null;
    }
}
