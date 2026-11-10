package com.gradychastain.headcount.api;

import com.gradychastain.headcount.api.action.ActionType;
import com.gradychastain.headcount.api.status.StatusSourceType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Headcount's public API. Get it with {@code Bukkit.getServicesManager().load(HeadcountAPI.class)} or
 * {@link HeadcountProvider#get()}. Call it on the main server thread.
 */
public interface HeadcountAPI {
    /** Information about an economy head. */
    record HeadInfo(UUID headId, UUID owner, String ownerName, int cycle, boolean current, String source) {}

    /** The running cycle's ID, or 0 before the first cycle starts. */
    int currentCycle();

    /** Epoch milliseconds when the current cycle ends, or -1. */
    long cycleEndsAt();

    /** Economy data on an item, or empty for anything that is not a Headcount economy head. */
    Optional<HeadInfo> headInfo(ItemStack item);

    /** Makes a new economy head of {@code owner} for the current cycle (recorded and audited). */
    ItemStack createHead(UUID owner, String source);

    /** Unique heads, total heads and own-head copies in a player's vault right now. */
    int[] vaultCounts(UUID player);

    /** What the player's score would be if the cycle ended now. */
    int previewScore(Player player);

    long points(UUID player);

    /** Adds (or with a negative amount, takes) points. Returns the amount actually applied. */
    long addPoints(UUID player, long amount, String reason);

    boolean hasStatus(Player player, String status);

    /** Runs a named action list from actions.yml on a player. Returns false if no such list exists. */
    boolean runActionList(UUID player, String listName, Map<String, Object> variables);

    String equippedTitle(UUID player);

    /** Registers a status source usable as {@code source: <id>} in statuses.yml. Statuses are re-parsed. */
    void registerStatusSource(StatusSourceType type);

    /** Registers an action type usable as {@code type: <id>} in every action list. Action configs are re-parsed. */
    void registerActionType(ActionType type);
}
