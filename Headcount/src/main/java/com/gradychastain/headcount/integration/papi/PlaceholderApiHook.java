package com.gradychastain.headcount.integration.papi;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

/** Resolves other plugins' placeholders through PlaceholderAPI. */
public final class PlaceholderApiHook {
    public String apply(OfflinePlayer player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
