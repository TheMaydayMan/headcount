package com.gradychastain.headcount.integration.economy;

import org.bukkit.OfflinePlayer;

/** A money economy (Vault). Headcount's own points are separate and never go through this. */
public interface EconomyHook {
    String name();

    boolean deposit(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    double balance(OfflinePlayer player);

    String format(double amount);
}
