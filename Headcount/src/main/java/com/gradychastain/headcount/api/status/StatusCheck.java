package com.gradychastain.headcount.api.status;

import org.bukkit.entity.Player;

/** A compiled status test. */
@FunctionalInterface
public interface StatusCheck {
    /**
     * @return true or false, or null when the source cannot answer right now (for example its plugin is missing);
     *         null makes Headcount use the status's {@code fallback} or {@code unavailable} setting
     */
    Boolean test(Player player);
}
