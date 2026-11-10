package com.gradychastain.headcount.shop;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.gui.Gui;
import com.gradychastain.headcount.gui.MenuLayout;
import com.gradychastain.headcount.player.PlayerData;
import com.gradychastain.headcount.text.Vars;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** One page of one shop tier. Locked tiers can be browsed and show what they need. */
final class ShopGui extends Gui {
    private final ShopManager shop;
    private final int tierIndex;
    private final int page;

    ShopGui(HeadcountPlugin plugin, Player viewer, ShopManager shop, int tierIndex, int page) {
        super(plugin, viewer);
        this.shop = shop;
        List<ShopManager.Tier> tiers = shop.tiers();
        this.tierIndex = Math.max(0, Math.min(tierIndex, tiers.size() - 1));
        ShopManager.Tier t = tiers.get(this.tierIndex);
        this.page = Math.max(0, Math.min(page, t.pages() - 1));
        MenuLayout layout = shop.layout();
        create(layout.rows(6), layout.title(plugin, viewer, vars(t), "<dark_gray>Shop » <tier>"));
    }

    private Vars vars(ShopManager.Tier t) {
        PlayerData d = plugin.players().get(viewer);
        int last = shop.lastScore(d);
        return Vars.empty().mini("tier", t.name()).put("tier_id", t.id()).put("page", page + 1).put("pages", t.pages()).put("points", d.points)
                .put("last_score", last).put("threshold", t.threshold() == null ? "-" : t.threshold())
                .put("needed", t.threshold() == null ? 0 : Math.max(0, t.threshold() - last))
                .put("tier_number", tierIndex + 1).put("tiers", shop.tiers().size());
    }

    @Override
    public void render() {
        clearButtons();
        List<ShopManager.Tier> tiers = shop.tiers();
        ShopManager.Tier t = tiers.get(tierIndex);
        MenuLayout layout = shop.layout();
        Vars v = vars(t);
        List<Integer> taken = new ArrayList<>();
        PlayerData d = plugin.players().get(viewer);
        boolean unlocked = shop.unlocked(t, shop.lastScore(d));
        for (ShopManager.Entry e : t.entries()) {
            if (e.page() - 1 != page) continue;
            if (e.slot() < 0 || e.slot() >= inventory.getSize()) continue;
            ShopManager.State st = shop.state(viewer, e);
            set(e.slot(), shop.display(viewer, e, st), c -> shop.click(viewer, e, this));
            taken.add(e.slot());
        }
        if (!unlocked && layout.hasButton("locked-banner")) {
            int s = layout.slot("locked-banner", 4);
            set(s, layout.buttonItem(plugin, "locked-banner", viewer, v), null);
            taken.add(s);
        }
        if (tierIndex > 0 && layout.hasButton("previous-tier")) {
            int s = layout.slot("previous-tier", 45);
            set(s, layout.buttonItem(plugin, "previous-tier", viewer, v.copy().mini("target_tier", tiers.get(tierIndex - 1).name())), c -> go(tierIndex - 1, 0));
            taken.add(s);
        }
        if (tierIndex < tiers.size() - 1 && layout.hasButton("next-tier")) {
            int s = layout.slot("next-tier", 53);
            set(s, layout.buttonItem(plugin, "next-tier", viewer, v.copy().mini("target_tier", tiers.get(tierIndex + 1).name())), c -> go(tierIndex + 1, 0));
            taken.add(s);
        }
        if (page > 0 && layout.hasButton("previous-page")) {
            int s = layout.slot("previous-page", 48);
            set(s, layout.buttonItem(plugin, "previous-page", viewer, v), c -> go(tierIndex, page - 1));
            taken.add(s);
        }
        if (page < t.pages() - 1 && layout.hasButton("next-page")) {
            int s = layout.slot("next-page", 50);
            set(s, layout.buttonItem(plugin, "next-page", viewer, v), c -> go(tierIndex, page + 1));
            taken.add(s);
        }
        if (layout.hasButton("info")) {
            int s = layout.slot("info", 49);
            set(s, layout.buttonItem(plugin, "info", viewer, v), null);
            taken.add(s);
        }
        if (layout.hasButton("close")) {
            int s = layout.slot("close", 46);
            set(s, layout.buttonItem(plugin, "close", viewer, v), c -> viewer.closeInventory());
            taken.add(s);
        }
        layout.fill(this, plugin, viewer, v, taken);
    }

    private void go(int tier, int pg) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) new ShopGui(plugin, viewer, shop, tier, pg).open();
        });
    }

    void reopen() {
        if (viewer.isOnline()) new ShopGui(plugin, viewer, shop, tierIndex, page).open();
    }
}
