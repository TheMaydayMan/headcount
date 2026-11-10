package com.gradychastain.headcount.action.types;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.util.Formats;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime boss bars. Per-player bars belong to a tracked action (shown while it is active). Global bars come from
 * global hooks: everyone online sees them, players who join while one is running see it too, and it disappears
 * when its countdown reaches zero or its time runs out.
 */
public final class BossBars implements Listener {
    /** A bar's look and countdown. {@code countdownTo} is an epoch millisecond, or 0 for no countdown. */
    record Spec(String text, BossBar.Color color, BossBar.Overlay overlay, float progress, long countdownTo, long countdownFrom) {}

    private record Global(BossBar bar, Spec spec, long endsAt) {}

    private final HeadcountPlugin plugin;
    private final Map<String, BossBar> perPlayer = new HashMap<>();
    private final Map<String, Spec> perPlayerSpec = new HashMap<>();
    private final List<Global> globals = new ArrayList<>();

    public BossBars(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    private Component text(Spec s, Player subject) {
        long remaining = s.countdownTo() > 0 ? Math.max(0, s.countdownTo() - System.currentTimeMillis()) : 0;
        Vars v = Vars.of("countdown", Formats.duration(remaining), "countdown_clock", Formats.clock(remaining), "seconds", (remaining + 999) / 1000);
        return plugin.text().render(s.text(), subject, v);
    }

    private float progress(Spec s) {
        if (s.countdownTo() <= 0) return Math.max(0, Math.min(1, s.progress()));
        long total = Math.max(1, s.countdownTo() - s.countdownFrom());
        long left = Math.max(0, s.countdownTo() - System.currentTimeMillis());
        return Math.max(0, Math.min(1, left / (float) total));
    }

    void showFor(String instanceId, Player p, Spec spec) {
        BossBar bar = perPlayer.get(instanceId);
        if (bar == null) {
            bar = BossBar.bossBar(text(spec, p), progress(spec), spec.color(), spec.overlay());
            perPlayer.put(instanceId, bar);
            perPlayerSpec.put(instanceId, spec);
        } else {
            bar.name(text(spec, p));
            bar.progress(progress(spec));
        }
        p.showBossBar(bar);
    }

    void hide(String instanceId, Player p) {
        BossBar bar = perPlayer.remove(instanceId);
        perPlayerSpec.remove(instanceId);
        if (bar != null && p != null) p.hideBossBar(bar);
    }

    /** Shows a temporary bar (instant actions) for a few seconds. */
    void flash(Player p, Spec spec, long millis) {
        BossBar bar = BossBar.bossBar(text(spec, p), progress(spec), spec.color(), spec.overlay());
        p.showBossBar(bar);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.hideBossBar(bar), Math.max(1, millis / 50));
    }

    void showGlobal(Spec spec, long millis) {
        long ends = spec.countdownTo() > 0 ? spec.countdownTo() : System.currentTimeMillis() + Math.max(1000, millis);
        BossBar bar = BossBar.bossBar(text(spec, null), progress(spec), spec.color(), spec.overlay());
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
        globals.add(new Global(bar, spec, ends));
    }

    /** Called every second. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Global g : new ArrayList<>(globals)) {
            if (now >= g.endsAt()) {
                for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(g.bar());
                globals.remove(g);
                continue;
            }
            g.bar().name(text(g.spec(), null));
            g.bar().progress(progress(g.spec()));
        }
        for (Map.Entry<String, BossBar> e : perPlayer.entrySet()) {
            Spec s = perPlayerSpec.get(e.getKey());
            if (s == null) continue;
            e.getValue().progress(progress(s));
        }
    }

    /** Re-renders a per-player bar's text for its viewer (placeholders are per player). */
    void update(String instanceId, Player p) {
        BossBar bar = perPlayer.get(instanceId);
        Spec s = perPlayerSpec.get(instanceId);
        if (bar != null && s != null) bar.name(text(s, p));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (Global g : globals) event.getPlayer().showBossBar(g.bar());
    }

    public void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Global g : globals) p.hideBossBar(g.bar());
            for (BossBar b : perPlayer.values()) p.hideBossBar(b);
        }
        globals.clear();
        perPlayer.clear();
        perPlayerSpec.clear();
    }

    boolean hasPerPlayer(String instanceId) {
        return perPlayer.containsKey(instanceId);
    }
}
