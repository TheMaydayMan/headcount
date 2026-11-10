package com.gradychastain.headcount.item;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * An item definition from config. Settings: {@code material}, {@code amount}, {@code name}, {@code lore},
 * {@code enchants} (map of enchantment to level), {@code glint}, {@code item-model}, {@code custom-model-data},
 * {@code unbreakable}, {@code flags} (ItemFlag names), {@code pdc} (map of {@code namespace:key} to value),
 * {@code custom} (an ItemsAdder/Oraxen/Nexo ID like {@code nexo:ruby}), {@code head} (owner name, UUID, or
 * {@code viewer}) and {@code max-stack-size}. Names and lore use MiniMessage and placeholders.
 */
public final class ItemSpec {
    private final Cfg cfg;
    private final Material material;
    private final String custom;

    private ItemSpec(Cfg cfg, Material material, String custom) {
        this.cfg = cfg;
        this.material = material;
        this.custom = custom;
    }

    public static ItemSpec parse(Object raw, Material fallback) {
        Cfg c = raw instanceof String s ? Cfg.of(Map.of("material", s)) : Cfg.of(raw);
        String custom = c.getString("custom", null);
        String matName = c.getString("material", null);
        Material mat = matName == null ? fallback : Material.matchMaterial(matName.trim());
        if (mat == null && custom == null) {
            if (matName != null) throw new IllegalArgumentException("unknown material '" + matName + "'" + (c.path().isEmpty() ? "" : " at " + c.path()));
            mat = fallback == null ? Material.STONE : fallback;
        }
        return new ItemSpec(c, mat, custom);
    }

    public static ItemSpec parse(Object raw) {
        return parse(raw, Material.STONE);
    }

    public Cfg config() {
        return cfg;
    }

    public Material material() {
        return material;
    }

    public int amount() {
        return Math.max(1, cfg.getInt("amount", 1));
    }

    public ItemSpec withOverrides(Cfg overrides) {
        return parse(cfg.merge(overrides).raw(), material);
    }

    /** Builds the item, rendering text for {@code subject} with {@code vars}. */
    public ItemStack build(HeadcountPlugin plugin, OfflinePlayer subject, Vars vars) {
        ItemStack item = null;
        if (custom != null) {
            item = plugin.integrations().customItem(custom);
            if (item == null) {
                plugin.getLogger().warning("Custom item '" + custom + "' is unavailable (plugin missing or unknown ID); using "
                        + (material == null ? "STONE" : material.name()) + " instead.");
            }
        }
        if (item == null) item = new ItemStack(material == null ? Material.STONE : material);
        // Amounts above 99 are split into several stacks by the give action; one stack holds at most 99.
        item.setAmount(Math.max(1, Math.min(amount(), 99)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String name = cfg.getString("name", null);
        if (name != null) meta.displayName(plugin.text().item(name, subject, vars));
        List<String> lore = cfg.getStringList("lore");
        if (!lore.isEmpty()) meta.lore(plugin.text().itemLines(lore, subject, vars));
        Cfg ench = cfg.getSection("enchants");
        for (String e : ench.keys()) {
            Enchantment en = io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(key(e));
            if (en == null) {
                plugin.getLogger().warning("Unknown enchantment '" + e + "' at " + cfg.path());
                continue;
            }
            meta.addEnchant(en, ench.getInt(e, 1), true);
        }
        if (cfg.has("glint")) meta.setEnchantmentGlintOverride(cfg.getBool("glint", false));
        String model = cfg.getString("item-model", "");
        if (!model.isBlank()) {
            NamespacedKey k = NamespacedKey.fromString(model.toLowerCase(Locale.ROOT));
            if (k != null) meta.setItemModel(k);
        }
        if (cfg.has("custom-model-data")) {
            var cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) cfg.getDouble("custom-model-data", 0)));
            meta.setCustomModelDataComponent(cmd);
        }
        if (cfg.getBool("unbreakable", false)) meta.setUnbreakable(true);
        for (String f : cfg.getStringList("flags")) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(f.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown item flag '" + f + "' at " + cfg.path());
            }
        }
        if (cfg.has("max-stack-size")) meta.setMaxStackSize(Math.max(1, Math.min(99, cfg.getInt("max-stack-size", 64))));
        Cfg pdc = cfg.getSection("pdc");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (String k : pdc.keys()) {
            NamespacedKey nk = NamespacedKey.fromString(k.toLowerCase(Locale.ROOT), plugin);
            if (nk == null) continue;
            Object v = pdc.get(k);
            if (v instanceof Integer i) container.set(nk, PersistentDataType.INTEGER, i);
            else if (v instanceof Long l) container.set(nk, PersistentDataType.LONG, l);
            else if (v instanceof Number n) container.set(nk, PersistentDataType.DOUBLE, n.doubleValue());
            else if (v instanceof Boolean b) container.set(nk, PersistentDataType.BOOLEAN, b);
            else container.set(nk, PersistentDataType.STRING, plugin.text().plain(String.valueOf(v), subject, vars));
        }
        String head = cfg.getString("head", null);
        if (head != null && meta instanceof SkullMeta skull) {
            String h = plugin.text().plain(head, subject, vars);
            if (h.equalsIgnoreCase("viewer") && subject != null) {
                skull.setPlayerProfile(plugin.heads().profile(subject.getUniqueId(), subject.getName()));
            } else {
                try {
                    UUID id = UUID.fromString(h);
                    skull.setPlayerProfile(plugin.heads().profile(id, null));
                } catch (IllegalArgumentException notUuid) {
                    OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(h);
                    skull.setPlayerProfile(op != null ? plugin.heads().profile(op.getUniqueId(), op.getName()) : Bukkit.createProfile(null, h));
                }
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Whether a stack matches this definition for take/match purposes. Only settings present are compared:
     * material, custom ID, {@code name-contains}, {@code lore-contains}, {@code item-model}, {@code pdc} keys.
     */
    public boolean matches(HeadcountPlugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (custom != null) {
            String id = plugin.integrations().customIdOf(item);
            if (id == null || !id.equalsIgnoreCase(custom)) return false;
        } else if (cfg.has("material") && item.getType() != material) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        String nameContains = cfg.getString("name-contains", null);
        if (nameContains != null) {
            if (meta == null || meta.displayName() == null) return false;
            String plain = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            if (!plain.toLowerCase(Locale.ROOT).contains(nameContains.toLowerCase(Locale.ROOT))) return false;
        }
        String loreContains = cfg.getString("lore-contains", null);
        if (loreContains != null) {
            if (meta == null || meta.lore() == null) return false;
            boolean found = false;
            for (var line : meta.lore()) {
                if (PlainTextComponentSerializer.plainText().serialize(line).toLowerCase(Locale.ROOT).contains(loreContains.toLowerCase(Locale.ROOT))) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        String model = cfg.getString("item-model", "");
        if (!model.isBlank()) {
            if (meta == null || !meta.hasItemModel() || !meta.getItemModel().toString().equalsIgnoreCase(model)) return false;
        }
        Cfg pdc = cfg.getSection("pdc");
        for (String k : pdc.keys()) {
            NamespacedKey nk = NamespacedKey.fromString(k.toLowerCase(Locale.ROOT), plugin);
            if (nk == null || !item.getPersistentDataContainer().has(nk)) return false;
        }
        return true;
    }

    static NamespacedKey key(String s) {
        String k = s.trim().toLowerCase(Locale.ROOT);
        return k.contains(":") ? NamespacedKey.fromString(k) : NamespacedKey.minecraft(k);
    }

    /** Serializable form for persistence. */
    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(cfg.raw());
    }

    public List<String> describe() {
        List<String> out = new ArrayList<>();
        out.add((custom != null ? custom : material.name()) + " x" + amount());
        return out;
    }
}
