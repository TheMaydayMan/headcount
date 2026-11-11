package com.gradychastain.headcount.head;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** PersistentDataContainer keys. Item keys mark economy heads; block keys preserve placed heads. */
public final class HeadKeys {
    public final NamespacedKey owner;
    public final NamespacedKey ownerName;
    public final NamespacedKey cycle;
    public final NamespacedKey id;
    public final NamespacedKey created;
    public final NamespacedKey source;
    /** The cycle run's token (1.1+). */
    public final NamespacedKey token;
    /** Fingerprint of how the name/lore was last rendered, so expired heads are re-rendered once. */
    public final NamespacedKey render;
    /** On a placed skull block: the full serialized economy head item. */
    public final NamespacedKey placedItem;
    /** On a placed skull block: the economy head's ID. */
    public final NamespacedKey placedId;
    /** Marks GUI display items so any that escape a menu can be deleted. */
    public final NamespacedKey guiItem;
    /** Marks items equipped and locked by an action, with the action instance ID. */
    public final NamespacedKey lockedItem;
    /** Marks items given by a timed action so they can be taken back when it ends. */
    public final NamespacedKey actionItem;

    public HeadKeys(Plugin plugin) {
        owner = new NamespacedKey(plugin, "head_owner");
        ownerName = new NamespacedKey(plugin, "head_owner_name");
        cycle = new NamespacedKey(plugin, "head_cycle");
        id = new NamespacedKey(plugin, "head_id");
        created = new NamespacedKey(plugin, "head_created");
        source = new NamespacedKey(plugin, "head_source");
        token = new NamespacedKey(plugin, "head_token");
        render = new NamespacedKey(plugin, "head_render");
        placedItem = new NamespacedKey(plugin, "placed_item");
        placedId = new NamespacedKey(plugin, "placed_id");
        guiItem = new NamespacedKey(plugin, "gui_item");
        lockedItem = new NamespacedKey(plugin, "locked_item");
        actionItem = new NamespacedKey(plugin, "action_item");
    }
}
