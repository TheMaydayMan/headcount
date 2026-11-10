package com.gradychastain.headcount.head;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Formats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates, reads and re-renders economy heads. A head counts for the economy only if its PersistentDataContainer
 * carries Headcount's owner, cycle and ID keys; skins and names never matter.
 */
public final class HeadService {
    private final HeadcountPlugin plugin;
    private final HeadKeys keys;
    private final Map<UUID, ProfileProperty> textures = new ConcurrentHashMap<>();
    private String renderHash = "";

    public HeadService(HeadcountPlugin plugin) {
        this.plugin = plugin;
        this.keys = new HeadKeys(plugin);
    }

    public HeadKeys keys() {
        return keys;
    }

    private Cfg itemCfg() {
        return plugin.configs().main().getSection("heads.item");
    }

    public void reload() {
        Cfg c = itemCfg();
        renderHash = Integer.toHexString(Objects.hash(c.getString("name"), c.getStringList("lore"), c.getString("expired-name"),
                c.getStringList("expired-lore"), c.getInt("max-stack-size", 1), c.getBool("glint", false), c.getString("item-model", "")));
    }

    // ---------------------------------------------------------------- creation

    /** Makes a new head with a fresh ID, records it and logs its creation. */
    public HeadData newHead(UUID owner, String ownerName, int cycle, String source, UUID recipient, String actor) {
        HeadData data = new HeadData(UUID.randomUUID(), owner, ownerName, cycle, System.currentTimeMillis(), source);
        register(data, recipient);
        plugin.audit().log(AuditType.HEAD_CREATE, "head", data.id(), "owner", ownerName + "(" + owner + ")", "cycle", cycle,
                "source", source, "recipient", recipient, "actor", actor);
        return data;
    }

    /** Records a head in the head registry without auditing (bulk paths audit themselves). */
    public void register(HeadData data, UUID recipient) {
        plugin.db().write(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO heads(head_id, owner, cycle, source, created_ms, recipient) VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, data.id().toString());
                ps.setString(2, data.owner().toString());
                ps.setInt(3, data.cycle());
                ps.setString(4, data.source());
                ps.setLong(5, data.created());
                ps.setString(6, recipient == null ? null : recipient.toString());
                ps.executeUpdate();
            }
        });
    }

    /** Builds the item for existing head data (no new ID, nothing recorded). */
    public ItemStack createItem(HeadData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(SkullMeta.class, meta -> {
            meta.setPlayerProfile(profile(data.owner(), data.ownerName()));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keys.owner, PersistentDataType.STRING, data.owner().toString());
            pdc.set(keys.ownerName, PersistentDataType.STRING, data.ownerName());
            pdc.set(keys.cycle, PersistentDataType.INTEGER, data.cycle());
            pdc.set(keys.id, PersistentDataType.STRING, data.id().toString());
            pdc.set(keys.created, PersistentDataType.LONG, data.created());
            pdc.set(keys.source, PersistentDataType.STRING, data.source());
        });
        render(item, data);
        return item;
    }

    // ---------------------------------------------------------------- reading

    /** The economy data on an item, or null if it is not an economy head (vanilla, /give and other plugins' heads). */
    public HeadData read(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return null;
        var pdc = item.getPersistentDataContainer();
        String owner = pdc.get(keys.owner, PersistentDataType.STRING);
        String id = pdc.get(keys.id, PersistentDataType.STRING);
        Integer cycle = pdc.get(keys.cycle, PersistentDataType.INTEGER);
        if (owner == null || id == null || cycle == null) return null;
        try {
            return new HeadData(UUID.fromString(id), UUID.fromString(owner),
                    Objects.requireNonNullElse(pdc.get(keys.ownerName, PersistentDataType.STRING), "?"), cycle,
                    Objects.requireNonNullElse(pdc.get(keys.created, PersistentDataType.LONG), 0L),
                    Objects.requireNonNullElse(pdc.get(keys.source, PersistentDataType.STRING), "unknown"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isEconomyHead(ItemStack item) {
        return read(item) != null;
    }

    /** True when the head belongs to the cycle running right now. */
    public boolean isCurrent(HeadData data) {
        return data != null && plugin.cycles().current() != null && data.cycle() == plugin.cycles().current().id();
    }

    /** A current-cycle economy head: the only thing allowed in vaults and counted as a head in trades. */
    public HeadData readCurrent(ItemStack item) {
        HeadData d = read(item);
        return isCurrent(d) ? d : null;
    }

    // ---------------------------------------------------------------- rendering

    /** Re-renders name and lore if the head's state (current/expired) or the configured format changed. */
    public boolean refresh(ItemStack item) {
        HeadData data = read(item);
        if (data == null) return false;
        String fp = fingerprint(data);
        String have = item.getPersistentDataContainer().get(keys.render, PersistentDataType.STRING);
        if (fp.equals(have)) return false;
        render(item, data);
        return true;
    }

    private String fingerprint(HeadData data) {
        return (isCurrent(data) ? "current" : "expired") + ":" + renderHash;
    }

    private void render(ItemStack item, HeadData data) {
        Cfg c = itemCfg();
        boolean current = isCurrent(data);
        Vars vars = vars(data);
        String nameTemplate = current ? c.getString("name", "<yellow><owner>'s Head") : c.getString("expired-name", "<gray><owner>'s Head <dark_gray>(expired)");
        List<String> lore = current ? c.getStringList("lore") : c.getStringList("expired-lore");
        String fp = fingerprint(data);
        item.editMeta(meta -> {
            meta.displayName(plugin.text().item(nameTemplate, null, vars));
            meta.lore(plugin.text().itemLines(lore, null, vars));
            int max = c.getInt("max-stack-size", 1);
            if (max >= 1 && max <= 99) meta.setMaxStackSize(max);
            if (c.getBool("glint", false) && current) meta.setEnchantmentGlintOverride(true);
            else meta.setEnchantmentGlintOverride(null);
            String model = c.getString("item-model", "");
            if (!model.isBlank()) {
                var key = org.bukkit.NamespacedKey.fromString(model);
                if (key != null) meta.setItemModel(key);
            }
            meta.getPersistentDataContainer().set(keys.render, PersistentDataType.STRING, fp);
        });
    }

    public Vars vars(HeadData data) {
        int cur = plugin.cycles().current() == null ? 0 : plugin.cycles().current().id();
        return Vars.of("owner", data.ownerName(), "owner_uuid", data.owner(), "cycle", data.cycle(), "head_id", data.id(),
                "head_id_short", Formats.shortId(data.id().toString()), "created", Formats.date(data.created()),
                "source", data.source(), "current_cycle", cur);
    }

    // ---------------------------------------------------------------- skins

    /** Remembers a player's skin texture so heads made while they are offline still show it. */
    public void cacheTexture(Player player) {
        for (ProfileProperty p : player.getPlayerProfile().getProperties()) {
            if (p.getName().equals("textures")) {
                textures.put(player.getUniqueId(), p);
                plugin.playerStore().saveTexture(player.getUniqueId(), p.getValue(), p.getSignature());
                return;
            }
        }
    }

    public void putTexture(UUID uuid, String value, String signature) {
        if (value != null && !value.isEmpty()) textures.put(uuid, new ProfileProperty("textures", value, signature));
    }

    public PlayerProfile profile(UUID owner, String name) {
        Player online = Bukkit.getPlayer(owner);
        if (online != null) {
            PlayerProfile p = online.getPlayerProfile();
            if (p.hasTextures()) return p;
        }
        PlayerProfile profile = Bukkit.createProfile(owner, name == null || name.isEmpty() || name.length() > 16 ? null : name);
        ProfileProperty tex = textures.get(owner);
        if (tex == null) tex = plugin.playerStore().loadTexture(owner);
        if (tex != null) {
            textures.put(owner, tex);
            profile.setProperty(tex);
        }
        return profile;
    }
}
