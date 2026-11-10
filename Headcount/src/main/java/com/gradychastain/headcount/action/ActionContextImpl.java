package com.gradychastain.headcount.action;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.api.action.ActionContext;
import com.gradychastain.headcount.text.Vars;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The concrete {@link ActionContext} handed to handlers. */
public final class ActionContextImpl implements ActionContext {
    private final HeadcountPlugin plugin;
    private final UUID player;
    private final Vars vars;
    private final Map<String, Object> data;
    private final String source;
    private final String instanceId;
    private final DurationSpec duration;

    public ActionContextImpl(HeadcountPlugin plugin, UUID player, Vars vars, Map<String, Object> data, String source,
                             String instanceId, DurationSpec duration) {
        this.plugin = plugin;
        this.player = player;
        this.vars = vars == null ? Vars.empty() : vars;
        this.data = data == null ? new LinkedHashMap<>() : data;
        this.source = source;
        this.instanceId = instanceId;
        this.duration = duration == null ? DurationSpec.INSTANT : duration;
    }

    public HeadcountPlugin plugin() {
        return plugin;
    }

    public Vars vars() {
        return vars;
    }

    @Override public UUID playerId() { return player; }

    @Override
    public Player player() {
        return player == null ? null : Bukkit.getPlayer(player);
    }

    @Override
    public OfflinePlayer offlinePlayer() {
        return player == null ? null : Bukkit.getOfflinePlayer(player);
    }

    @Override
    public Map<String, Object> variables() {
        return vars.asMap();
    }

    @Override
    public Component render(String template) {
        return plugin.text().render(template, offlinePlayer(), vars);
    }

    @Override
    public String format(String template) {
        return plugin.text().plain(template, offlinePlayer(), vars);
    }

    @Override public Map<String, Object> data() { return data; }
    @Override public String source() { return source; }
    @Override public String instanceId() { return instanceId; }

    @Override
    public Duration duration() {
        return duration.kind() == DurationSpec.Kind.TIME ? Duration.ofMillis(duration.millis()) : null;
    }

    @Override
    public boolean timed() {
        return !duration.instant();
    }

    public DurationSpec durationSpec() {
        return duration;
    }

    /** The player's name for commands, falling back to stored names for offline players. */
    public String playerName() {
        return player == null ? "" : plugin.players().name(player);
    }
}
