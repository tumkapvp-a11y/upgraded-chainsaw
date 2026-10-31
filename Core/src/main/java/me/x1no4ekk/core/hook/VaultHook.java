package me.x1no4ekk.core.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Vault facade without Vault types in its signatures, so it is safe to use when Vault is absent.
 * Economy calls go to the Economy plugin (АРЫ) through Vault and must run on the main thread.
 */
public final class VaultHook {

    private final VaultBridge bridge;

    public VaultHook(Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            this.bridge = new VaultBridge();
            Bukkit.getPluginManager().registerEvents(bridge, plugin);
        } else {
            this.bridge = null;
        }
    }

    public boolean isVaultPresent() {
        return bridge != null;
    }

    public boolean hasEconomy() {
        return bridge != null && bridge.hasEconomy();
    }

    public boolean hasChat() {
        return bridge != null && bridge.hasChat();
    }

    public double balance(OfflinePlayer player) {
        return bridge == null ? 0 : bridge.balance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return amount <= 0 || (bridge != null && bridge.has(player, amount));
    }

    /**
     * @return {@code true} if the money was taken
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        return amount <= 0 || (bridge != null && bridge.withdraw(player, amount));
    }

    /**
     * @return {@code true} if the money was given
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        return amount <= 0 || (bridge != null && bridge.deposit(player, amount));
    }

    /**
     * Formats via the economy provider, or as a plain number with up to two decimals.
     */
    public String format(double amount) {
        if (bridge != null && bridge.hasEconomy()) {
            return bridge.format(amount);
        }
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public @Nullable String prefix(Player player) {
        return bridge == null ? null : bridge.prefix(player);
    }

    public @Nullable String suffix(Player player) {
        return bridge == null ? null : bridge.suffix(player);
    }

    public @Nullable String primaryGroup(Player player) {
        return bridge == null ? null : bridge.primaryGroup(player);
    }
}
