package com.gradychastain.headcount.gui;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.item.ItemSpec;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared menu settings: {@code title}, {@code rows}, {@code filler} (an item), {@code filler-slots}
 * ({@code empty} for every unused slot, {@code none}, or a list such as {@code [0-8, 45-53]}), and named
 * {@code buttons}, each an item definition plus {@code slot} (or {@code slots}).
 */
public final class MenuLayout {
    private final Cfg cfg;

    public MenuLayout(Cfg cfg) {
        this.cfg = cfg;
    }

    public Cfg cfg() {
        return cfg;
    }

    public int rows(int def) {
        return Math.max(1, Math.min(6, cfg.getInt("rows", def)));
    }

    public Component title(HeadcountPlugin plugin, OfflinePlayer subject, Vars vars, String def) {
        return plugin.text().render(cfg.getString("title", def), subject, vars);
    }

    public boolean hasButton(String id) {
        return cfg.isSection("buttons." + id) && cfg.getSection("buttons." + id).getBool("enabled", true);
    }

    public Cfg button(String id) {
        return cfg.getSection("buttons." + id);
    }

    public List<Integer> slots(String id) {
        Cfg b = button(id);
        if (b.has("slots")) return b.getIntList("slots");
        if (b.has("slot")) return List.of(b.getInt("slot", 0));
        return List.of();
    }

    public int slot(String id, int def) {
        List<Integer> s = slots(id);
        return s.isEmpty() ? def : s.getFirst();
    }

    public ItemStack buttonItem(HeadcountPlugin plugin, String id, OfflinePlayer subject, Vars vars) {
        return ItemSpec.parse(button(id).raw(), Material.STONE).build(plugin, subject, vars);
    }

    /** Builds the item for a state override such as {@code buttons.confirm.confirmed}. */
    public ItemStack buttonItem(HeadcountPlugin plugin, String id, String state, OfflinePlayer subject, Vars vars) {
        Cfg base = button(id);
        Cfg over = base.getSection(state);
        return ItemSpec.parse(over.isEmpty() ? base.raw() : base.merge(over).raw(), Material.STONE).build(plugin, subject, vars);
    }

    /** Places the filler item in the configured slots, skipping {@code taken} slots. */
    public void fill(Gui gui, HeadcountPlugin plugin, OfflinePlayer subject, Vars vars, List<Integer> taken) {
        if (!cfg.isSection("filler")) return;
        ItemStack filler = ItemSpec.parse(cfg.getSection("filler").raw(), Material.GRAY_STAINED_GLASS_PANE).build(plugin, subject, vars);
        int size = gui.getInventory().getSize();
        List<Integer> slots = new ArrayList<>();
        Object spec = cfg.get("filler-slots");
        String mode = spec == null ? "empty" : spec instanceof List<?> ? "list" : String.valueOf(spec).toLowerCase(Locale.ROOT);
        switch (mode) {
            case "none" -> { }
            case "list" -> slots.addAll(cfg.getIntList("filler-slots"));
            case "border" -> {
                int rows = size / 9;
                for (int i = 0; i < size; i++) {
                    int r = i / 9, c = i % 9;
                    if (r == 0 || r == rows - 1 || c == 0 || c == 8) slots.add(i);
                }
            }
            default -> {
                for (int i = 0; i < size; i++) slots.add(i);
            }
        }
        for (int s : slots) {
            if (s < 0 || s >= size || taken.contains(s)) continue;
            if (gui.getInventory().getItem(s) == null) gui.set(s, filler.clone(), null);
        }
    }
}
