package com.gradychastain.headcount.trade;

import com.gradychastain.headcount.util.Cfg;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Trade settings from trade.yml. */
final class TradeRules {
    final long requestTimeoutMs;
    final long countdownMs;
    final long cooldownMs;
    final boolean cooldownAfterCancel;
    final double range;
    final boolean sameWorld;
    final boolean headsForHeads, itemsForHeads, itemsForItems, gifts;
    final int maxItems, maxHeads;
    final boolean onlyOwnHeads;
    final Set<Material> blockedItems = new HashSet<>();
    final boolean cancelOnTeleport;
    final double teleportThreshold;
    final boolean cancelOnGamemode;
    final long checkIntervalTicks;
    final boolean savePlayerData;
    final String vanishedStatus;
    final List<String> blockSend, blockAccept, cancelOpen;

    TradeRules(Cfg c) {
        requestTimeoutMs = Math.max(1000, c.getMillis("request.timeout", 30_000));
        countdownMs = Math.max(0, c.getMillis("confirm.countdown", 3_000));
        cooldownMs = Math.max(0, c.getMillis("cooldown.duration", 10_000));
        cooldownAfterCancel = c.getBool("cooldown.after-cancel", false);
        range = c.getDouble("range.blocks", -1);
        sameWorld = c.getBool("range.same-world", true);
        headsForHeads = c.getBool("allowed-types.heads-for-heads", true);
        itemsForHeads = c.getBool("allowed-types.items-for-heads", true);
        itemsForItems = c.getBool("allowed-types.items-for-items", true);
        gifts = c.getBool("allowed-types.gifts", true);
        maxItems = c.getInt("limits.max-items", 24);
        maxHeads = c.getInt("limits.max-heads", 24);
        onlyOwnHeads = c.getBool("limits.only-own-heads", false);
        for (String m : c.getStringList("limits.blocked-items")) {
            Material mat = Material.matchMaterial(m.trim().toUpperCase(Locale.ROOT));
            if (mat != null) blockedItems.add(mat);
        }
        cancelOnTeleport = c.getBool("cancel.on-teleport", true);
        teleportThreshold = c.getDouble("cancel.teleport-min-distance", 0);
        cancelOnGamemode = c.getBool("cancel.on-gamemode-change", true);
        checkIntervalTicks = Math.max(1, c.getMillis("cancel.check-interval", 500) / 50);
        savePlayerData = c.getBool("save-player-data-after-trade", true);
        vanishedStatus = c.getString("request.vanished-status", "vanished");
        blockSend = c.getStringList("statuses.block-request");
        blockAccept = c.getStringList("statuses.block-accept");
        cancelOpen = c.getStringList("statuses.cancel-open");
    }
}
