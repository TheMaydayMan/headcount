package com.gradychastain.headcount.api;

import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.api.action.ActionType;
import com.gradychastain.headcount.api.status.StatusSourceType;
import com.gradychastain.headcount.head.HeadData;
import com.gradychastain.headcount.text.Vars;
import com.gradychastain.headcount.vault.VaultTally;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Implementation of {@link HeadcountAPI}. */
public final class HeadcountAPIImpl implements HeadcountAPI {
    private final HeadcountPlugin plugin;

    public HeadcountAPIImpl(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int currentCycle() {
        return plugin.cycles().currentId();
    }

    @Override
    public long cycleEndsAt() {
        var c = plugin.cycles().current();
        return c == null ? -1 : c.endMs();
    }

    @Override
    public Optional<HeadInfo> headInfo(ItemStack item) {
        HeadData d = plugin.heads().read(item);
        if (d == null) return Optional.empty();
        return Optional.of(new HeadInfo(d.id(), d.owner(), d.ownerName(), d.cycle(), plugin.heads().isCurrent(d), d.source()));
    }

    @Override
    public ItemStack createHead(UUID owner, String source) {
        var c = plugin.cycles().current();
        if (c == null) throw new IllegalStateException("No cycle is running");
        HeadData d = plugin.heads().newHead(owner, plugin.players().name(owner), c.id(), source == null ? "api" : source, null, "api");
        return plugin.heads().createItem(d);
    }

    @Override
    public int[] vaultCounts(UUID player) {
        VaultTally t = plugin.vaults().tally(player);
        return new int[]{t.unique(), t.total(), t.own()};
    }

    @Override
    public int previewScore(Player player) {
        return plugin.scores().preview(player);
    }

    @Override
    public long points(UUID player) {
        return plugin.points().balance(player);
    }

    @Override
    public long addPoints(UUID player, long amount, String reason) {
        return plugin.points().change(player, amount, false, reason == null ? "api" : reason, "api");
    }

    @Override
    public boolean hasStatus(Player player, String status) {
        return plugin.statuses().has(player, status);
    }

    @Override
    public boolean runActionList(UUID player, String listName, Map<String, Object> variables) {
        var list = plugin.actions().named(listName);
        if (list == null) return false;
        Vars v = Vars.of("player", plugin.players().name(player), "uuid", player.toString());   // as /hca run does
        if (variables != null) variables.forEach(v::put);
        plugin.actions().run(list, player, v, "api:" + listName);
        return true;
    }

    @Override
    public String equippedTitle(UUID player) {
        return plugin.titles().equipped(player);
    }

    @Override
    public void registerStatusSource(StatusSourceType type) {
        plugin.statuses().registerType(type);
        if (plugin.isLoaded()) plugin.statuses().load(plugin.configs().statuses());
    }

    @Override
    public void registerActionType(ActionType type) {
        plugin.actions().registerType(type);
        if (plugin.isLoaded()) plugin.reparseActions();
    }
}
