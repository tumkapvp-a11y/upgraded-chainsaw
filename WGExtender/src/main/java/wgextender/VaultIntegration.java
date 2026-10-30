package wgextender;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault is optional: this class is touched only after {@link #isAvailable()} returned true,
 * so a missing Vault never causes NoClassDefFoundError.
 */
public final class VaultIntegration {

    private VaultIntegration() {
    }

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("Vault");
    }

    /**
     * @return player groups, or {@code null} when no Vault permission provider with group support is present
     */
    public static String[] getPlayerGroups(Player player) {
        if (!isAvailable()) {
            return null;
        }
        return Hook.getPlayerGroups(player);
    }

    private static final class Hook {
        static String[] getPlayerGroups(Player player) {
            RegisteredServiceProvider<Permission> registration = Bukkit.getServicesManager().getRegistration(Permission.class);
            if (registration == null) {
                return null;
            }
            Permission permission = registration.getProvider();
            return permission.hasGroupSupport() ? permission.getPlayerGroups(player) : null;
        }
    }
}
