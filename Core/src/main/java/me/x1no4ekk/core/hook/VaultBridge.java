package me.x1no4ekk.core.hook;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Touches Vault classes; only loaded when Vault is installed (see {@link VaultHook}).
 * Providers are re-resolved when services (un)register, e.g. when the Economy plugin enables.
 */
final class VaultBridge implements Listener {

    private volatile Economy economy;
    private volatile Chat chat;

    VaultBridge() {
        refresh();
    }

    void refresh() {
        RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServicesManager().getRegistration(Economy.class);
        RegisteredServiceProvider<Chat> chatProvider = Bukkit.getServicesManager().getRegistration(Chat.class);
        economy = economyProvider == null ? null : economyProvider.getProvider();
        chat = chatProvider == null ? null : chatProvider.getProvider();
    }

    @EventHandler
    public void onRegister(ServiceRegisterEvent event) {
        Class<?> service = event.getProvider().getService();
        if (service == Economy.class || service == Chat.class) {
            refresh();
        }
    }

    @EventHandler
    public void onUnregister(ServiceUnregisterEvent event) {
        Class<?> service = event.getProvider().getService();
        if (service == Economy.class || service == Chat.class) {
            refresh();
        }
    }

    boolean hasEconomy() {
        return economy != null;
    }

    boolean hasChat() {
        return chat != null;
    }

    double balance(OfflinePlayer player) {
        Economy current = economy;
        return current == null ? 0 : current.getBalance(player);
    }

    boolean has(OfflinePlayer player, double amount) {
        Economy current = economy;
        return current != null && current.has(player, amount);
    }

    boolean withdraw(OfflinePlayer player, double amount) {
        Economy current = economy;
        if (current == null) {
            return false;
        }
        EconomyResponse response = current.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    boolean deposit(OfflinePlayer player, double amount) {
        Economy current = economy;
        if (current == null) {
            return false;
        }
        EconomyResponse response = current.depositPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    String format(double amount) {
        Economy current = economy;
        return current == null ? String.valueOf(amount) : current.format(amount);
    }

    String prefix(Player player) {
        Chat current = chat;
        return current == null ? null : current.getPlayerPrefix(player);
    }

    String suffix(Player player) {
        Chat current = chat;
        return current == null ? null : current.getPlayerSuffix(player);
    }

    String primaryGroup(Player player) {
        Chat current = chat;
        return current == null ? null : current.getPrimaryGroup(player);
    }
}
