package com.gradychastain.headcount.api.action;

import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** What an action runs against. */
public interface ActionContext {
    /** The target, or null in a global hook. */
    UUID playerId();

    /** The target if online, else null. */
    Player player();

    OfflinePlayer offlinePlayer();

    /** Variables available to text, e.g. {@code score}, {@code cycle}. */
    Map<String, Object> variables();

    /** Renders MiniMessage text with this context's variables and placeholders. */
    Component render(String template);

    /** Resolves variables and placeholders to plain text (for commands). */
    String format(String template);

    /** Persistent per-instance state for reverting. Values must be strings, numbers, booleans, lists or maps. */
    Map<String, Object> data();

    /** Where the action came from, e.g. {@code outcome:tier-1} or {@code shop:speed}. */
    String source();

    /** The tracked instance ID, or null for instant actions. */
    String instanceId();

    /** The configured duration, or null for instant, cycle-bound, death-bound, permanent and manual durations. */
    Duration duration();

    /** True when the duration is anything other than instant. */
    boolean timed();
}
