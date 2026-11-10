package com.gradychastain.headcount.history;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.gui.Gui;
import com.gradychastain.headcount.gui.MenuLayout;
import com.gradychastain.headcount.item.ItemSpec;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Formats;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /hc history: a player's past cycles. Admins can open anyone's. */
public final class HistoryGui extends Gui {
    public record Row(int cycle, boolean active, int unique, int total, int own, Integer score, long earned, long spent, long rolled,
                      long pointsAfter, int streak, Integer rank, String outcomes, long playtime, int kills, int deaths, int trades, long endedMs) {}

    private final UUID target;
    private final String targetName;
    private final List<Row> rows;
    private final int page;
    private final MenuLayout layout;

    private HistoryGui(HeadcountPlugin plugin, Player viewer, UUID target, String targetName, List<Row> rows, int page) {
        super(plugin, viewer);
        this.target = target;
        this.targetName = targetName;
        this.rows = rows;
        this.page = Math.max(0, page);
        this.layout = new MenuLayout(plugin.configs().main().getSection("history.gui"));
        create(layout.rows(6), layout.title(plugin, viewer, Vars.of("player", targetName, "page", this.page + 1), "<dark_gray><player>'s History"));
    }

    /** Loads history off the main thread, then opens the menu. */
    public static void open(HeadcountPlugin plugin, Player viewer, UUID target, String name) {
        plugin.db().query(c -> {
            List<Row> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT r.*, cy.ended_ms FROM results r LEFT JOIN cycles cy ON cy.id = r.cycle WHERE r.uuid=? ORDER BY r.cycle DESC""")) {
                ps.setString(1, target.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int sc = rs.getInt("score");
                        Integer score = rs.wasNull() ? null : sc;
                        int rk = rs.getInt("rank");
                        Integer rank = rs.wasNull() ? null : rk;
                        out.add(new Row(rs.getInt("cycle"), rs.getInt("active") != 0, rs.getInt("unique_heads"), rs.getInt("total_heads"),
                                rs.getInt("own_heads"), score, rs.getLong("points_earned"), rs.getLong("points_spent"), rs.getLong("rolled_over"),
                                rs.getLong("points_after"), rs.getInt("streak"), rank, rs.getString("outcomes"), rs.getLong("playtime_ms"),
                                rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("trades"), rs.getLong("ended_ms")));
                    }
                }
            }
            return out;
        }).thenAccept(rows -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline()) return;
            if (rows.isEmpty()) {
                plugin.lang().send(viewer, "history.empty", Vars.of("player", name));
                return;
            }
            new HistoryGui(plugin, viewer, target, name, rows, 0).open();
        }));
    }

    @Override
    public void render() {
        clearButtons();
        List<Integer> slots = layout.cfg().getIntList("entry-slots");
        if (slots.isEmpty()) for (int i = 0; i < 45; i++) slots.add(i);
        int per = slots.size();
        int pages = Math.max(1, (int) Math.ceil(rows.size() / (double) per));
        int pg = Math.min(page, pages - 1);
        List<Integer> taken = new ArrayList<>(slots);
        Cfg entryCfg = layout.cfg().getSection("entry");
        for (int i = 0; i < per && pg * per + i < rows.size(); i++) {
            Row r = rows.get(pg * per + i);
            String state = !r.active() ? "inactive" : r.score() != null && r.score() > 0 ? "positive" : r.score() != null && r.score() < 0 ? "negative" : "zero";
            Cfg merged = entryCfg.merge(entryCfg.getSection(state));
            List<String> outcomeNames = new ArrayList<>();
            if (r.outcomes() != null && !r.outcomes().isBlank()) {
                for (String o : r.outcomes().split(",")) outcomeNames.add(plugin.outcomes().display(o));
            }
            Vars v = Vars.of("cycle", r.cycle(), "unique", r.unique(), "total", r.total(), "own", r.own(),
                    "score", r.score() == null ? "-" : r.score(), "earned", r.earned(), "spent", r.spent(), "rolled_over", r.rolled(),
                    "points_after", r.pointsAfter(), "streak", r.streak(), "rank", r.rank() == null ? "-" : r.rank(),
                    "playtime", Formats.duration(r.playtime()), "kills", r.kills(), "deaths", r.deaths(), "trades", r.trades(),
                    "ended", r.endedMs() > 0 ? Formats.date(r.endedMs()) : "-", "player", targetName)
                    .mini("outcomes", outcomeNames.isEmpty() ? plugin.lang().raw("history.no-outcomes") : String.join("<gray>, </gray>", outcomeNames));
            set(slots.get(i), ItemSpec.parse(merged.raw(), Material.PAPER).build(plugin, viewer, v), null);
        }
        Vars v = Vars.of("page", pg + 1, "pages", pages, "player", targetName);
        if (pg > 0 && layout.hasButton("previous-page")) {
            int s = layout.slot("previous-page", 45);
            set(s, layout.buttonItem(plugin, "previous-page", viewer, v), c -> turn(pg - 1));
            taken.add(s);
        }
        if (pg < pages - 1 && layout.hasButton("next-page")) {
            int s = layout.slot("next-page", 53);
            set(s, layout.buttonItem(plugin, "next-page", viewer, v), c -> turn(pg + 1));
            taken.add(s);
        }
        if (layout.hasButton("close")) {
            int s = layout.slot("close", 49);
            set(s, layout.buttonItem(plugin, "close", viewer, v), c -> viewer.closeInventory());
            taken.add(s);
        }
        layout.fill(this, plugin, viewer, v, taken);
    }

    private void turn(int pg) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) new HistoryGui(plugin, viewer, target, targetName, rows, pg).open();
        });
    }
}
