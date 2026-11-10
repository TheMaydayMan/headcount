package com.gradychastain.headcount.integration.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Money through Vault's Economy service. */
public final class VaultEconomyHook implements EconomyHook {
    private final Economy economy;

    private VaultEconomyHook(Economy economy) {
        this.economy = economy;
    }

    /** Returns a hook when Vault has an economy provider registered, else null. */
    public static VaultEconomyHook create() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : new VaultEconomyHook(rsp.getProvider());
    }

    @Override public String name() { return "Vault (" + economy.getName() + ")"; }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public double balance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}
