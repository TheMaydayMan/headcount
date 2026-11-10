package com.gradychastain.headcount.title;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.gui.Gui;
import com.gradychastain.headcount.gui.MenuLayout;
import com.gradychastain.headcount.item.ItemSpec;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /hc titles: owned titles to equip (and, optionally, unowned ones shown locked). */
public final class TitlesGui extends Gui {
    private final TitleManager titles;
    private final int page;
    private final MenuLayout layout;

    public TitlesGui(HeadcountPlugin plugin, Player viewer, int page) {
        super(plugin, viewer);
        this.titles = plugin.titles();
        this.page = Math.max(0, page);
        this.layout = new MenuLayout(plugin.configs().titles().getSection("gui"));
        create(layout.rows(6), layout.title(plugin, viewer, Vars.of("page", this.page + 1), "<dark_gray>Your Titles"));
    }

    @Override
    public void render() {
        clearButtons();
        boolean showUnowned = layout.cfg().getBool("show-unowned", true);
        List<TitleManager.Title> list = new ArrayList<>();
        for (TitleManager.Title t : titles.all().values()) {
            if (showUnowned || titles.owns(viewer.getUniqueId(), t.id())) list.add(t);
        }
        List<Integer> slots = layout.cfg().getIntList("title-slots");
        if (slots.isEmpty()) for (int i = 0; i < 45; i++) slots.add(i);
        int per = slots.size();
        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) per));
        int pg = Math.min(page, pages - 1);
        String equipped = titles.equipped(viewer.getUniqueId());
        boolean forced = titles.isForced(viewer.getUniqueId());
        List<Integer> taken = new ArrayList<>(slots);
        for (int i = 0; i < per && pg * per + i < list.size(); i++) {
            TitleManager.Title t = list.get(pg * per + i);
            boolean owned = titles.owns(viewer.getUniqueId(), t.id());
            boolean isEquipped = t.id().equals(equipped);
            String state = !owned ? "locked" : isEquipped ? (forced ? "forced" : "equipped") : "owned";
            Cfg base = layout.cfg().getSection("title-item");
            Cfg merged = base.merge(t.icon()).merge(base.getSection(state));
            Vars v = Vars.empty().mini("title", t.display()).put("id", t.id()).put("description", String.join(" ", t.description()));
            var item = ItemSpec.parse(merged.raw(), Material.NAME_TAG).build(plugin, viewer, v);
            if (!t.description().isEmpty()) {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>(item.lore() == null ? List.of() : item.lore());
                for (String line : t.description()) lore.add(plugin.text().item(line, viewer, v));
                item.lore(lore);
            }
            set(slots.get(i), item, owned ? c -> toggle(t.id(), isEquipped) : null);
        }
        Vars v = Vars.of("page", pg + 1, "pages", pages);
        if (pg > 0 && layout.hasButton("previous-page")) {
            int s = layout.slot("previous-page", 45);
            set(s, layout.buttonItem(plugin, "previous-page", viewer, v), c -> reopen(pg - 1));
            taken.add(s);
        }
        if (pg < pages - 1 && layout.hasButton("next-page")) {
            int s = layout.slot("next-page", 53);
            set(s, layout.buttonItem(plugin, "next-page", viewer, v), c -> reopen(pg + 1));
            taken.add(s);
        }
        if (layout.hasButton("unequip") && equipped != null && !forced) {
            int s = layout.slot("unequip", 49);
            set(s, layout.buttonItem(plugin, "unequip", viewer, v), c -> {
                titles.unequip(viewer.getUniqueId());
                plugin.lang().send(viewer, "titles.unequipped");
                render();
            });
            taken.add(s);
        }
        layout.fill(this, plugin, viewer, v, taken);
    }

    private void toggle(String id, boolean isEquipped) {
        if (isEquipped) {
            if (titles.isForced(viewer.getUniqueId())) {
                plugin.lang().send(viewer, "titles.forced");
                return;
            }
            titles.unequip(viewer.getUniqueId());
            plugin.lang().send(viewer, "titles.unequipped");
        } else {
            titles.equip(viewer.getUniqueId(), id, false);
        }
        render();
    }

    private void reopen(int pg) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) new TitlesGui(plugin, viewer, pg).open();
        });
    }
}
