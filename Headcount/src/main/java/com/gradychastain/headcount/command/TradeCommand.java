package com.gradychastain.headcount.command;

import com.gradychastain.headcount.HeadcountPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /trade <player> | accept | deny | cancel */
public final class TradeCommand implements TabExecutor {
    private final HeadcountPlugin plugin;

    public TradeCommand(HeadcountPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.lang().send(sender, "general.players-only");
            return true;
        }
        if (!plugin.isLoaded()) {
            plugin.lang().send(sender, "general.loading");
            return true;
        }
        if (args.length == 0) {
            plugin.lang().send(p, "trade.usage", "label", label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> plugin.trades().accept(p);
            case "deny", "decline" -> plugin.trades().deny(p);
            case "cancel" -> plugin.trades().cancelCommand(p);
            default -> plugin.trades().request(p, args[0]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> out = new ArrayList<>(List.of("accept", "deny", "cancel"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(sender)) continue;
            if (sender instanceof Player s && !s.canSee(p)) continue;
            out.add(p.getName());
        }
        return HeadcountCommand.filter(out, args[0]);
    }
}
