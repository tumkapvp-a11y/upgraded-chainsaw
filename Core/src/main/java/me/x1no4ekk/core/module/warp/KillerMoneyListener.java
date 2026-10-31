package me.x1no4ekk.core.module.warp;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.hook.VaultHook;
import me.x1no4ekk.core.util.Sounds;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * KillerMoney: a player kill moves a share of the victim's balance to the killer, mob kills pay a random
 * reward. Needs a Vault economy, checked on every kill so a late-registered economy is picked up.
 */
final class KillerMoneyListener implements Listener {

    static final String PERMISSION = "core.killermoney";

    private static final long SWEEP_PERIOD_TICKS = 20L * 60;

    private record KillPair(UUID killer, UUID victim) {
    }

    private final CorePlugin plugin;
    private final Supplier<KillerMoneyConfig> config;
    /** Anti-farm: killer → victim pairs that already paid, with expiry time. Cleaned on quit of either side. */
    private final Map<KillPair, Long> paidPairs = new ConcurrentHashMap<>();
    private BukkitTask sweepTask;

    KillerMoneyListener(CorePlugin plugin, Supplier<KillerMoneyConfig> config) {
        this.plugin = plugin;
        this.config = config;
    }

    void start() {
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
    }

    void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        paidPairs.clear();
    }

    /**
     * Quit hook: drops pairs where the player is the killer or the victim.
     */
    void clear(UUID uuid) {
        paidPairs.keySet().removeIf(pair -> pair.killer().equals(uuid) || pair.victim().equals(uuid));
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        paidPairs.values().removeIf(expiresAt -> expiresAt <= now);
    }

    private boolean canEarn(KillerMoneyConfig settings, Player killer) {
        if (!settings.enabled() || !plugin.vault().hasEconomy()) {
            return false;
        }
        if (settings.requirePermission() && !killer.hasPermission(PERMISSION)) {
            return false;
        }
        return !settings.ignoreCreative() || killer.getGameMode() != GameMode.CREATIVE;
    }

    // ---------------------------------------------------------------- player kills

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        KillerMoneyConfig settings = config.get();
        KillerMoneyConfig.PlayerReward reward = settings.player();
        if (!reward.enabled() || reward.percent() <= 0 || !canEarn(settings, killer)) {
            return;
        }
        KillPair pair = new KillPair(killer.getUniqueId(), victim.getUniqueId());
        Long expiresAt = paidPairs.get(pair);
        long now = System.currentTimeMillis();
        if (expiresAt != null && expiresAt > now) {
            return;
        }

        VaultHook vault = plugin.vault();
        BigDecimal amount = BigDecimal.valueOf(vault.balance(victim))
                .multiply(BigDecimal.valueOf(reward.percent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        if (amount.signum() <= 0) {
            return;
        }
        double value = amount.doubleValue();
        if (!vault.withdraw(victim, value)) {
            return;
        }
        if (!vault.deposit(killer, value)) {
            if (!vault.deposit(victim, value)) {
                plugin.getLogger().severe("KillerMoney: could not refund " + amount.toPlainString() + " to " + victim.getName());
            }
            return;
        }
        if (reward.cooldownMillis() > 0) {
            paidPairs.put(pair, now + reward.cooldownMillis());
        }

        TagResolver formatted = Placeholder.unparsed("amount", vault.format(value));
        plugin.messages().send(killer, "killer-money.killer", formatted, Placeholder.unparsed("player", victim.getName()));
        actionBar(killer, plugin.messages().get("killer-money.killer-actionbar", formatted, Placeholder.unparsed("player", victim.getName())));
        Sounds.play(killer, reward.killerSound());
        plugin.messages().send(victim, "killer-money.victim", formatted, Placeholder.unparsed("player", killer.getName()));
        actionBar(victim, plugin.messages().get("killer-money.victim-actionbar", formatted, Placeholder.unparsed("player", killer.getName())));
        Sounds.play(victim, reward.victimSound());

        runCommands(reward.commands(), Map.of(
                "<killer>", killer.getName(),
                "<target>", victim.getName(),
                "<amount>", amount.toPlainString()));
    }

    // ---------------------------------------------------------------- mob kills

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return; // handled by onPlayerDeath (shared handler list)
        }
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        KillerMoneyConfig settings = config.get();
        EntityType type = entity.getType();
        KillerMoneyConfig.MobReward reward = settings.mobs().get(type);
        if (reward == null || !canEarn(settings, killer)) {
            return;
        }
        if (settings.ignoreSpawnerMobs() && entity.fromMobSpawner()) {
            return;
        }

        double raw = reward.max() > reward.min()
                ? ThreadLocalRandom.current().nextDouble(reward.min(), reward.max())
                : reward.min();
        BigDecimal amount = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            return;
        }
        double value = amount.doubleValue();
        if (!plugin.vault().deposit(killer, value)) {
            plugin.getLogger().warning("KillerMoney: could not pay " + amount.toPlainString() + " to " + killer.getName());
            return;
        }

        Component entityName = reward.name() != null ? Text.parse(reward.name()) : Component.translatable(type);
        TagResolver placeholders = TagResolver.resolver(
                Placeholder.unparsed("amount", plugin.vault().format(value)),
                Placeholder.unparsed("player", killer.getName()),
                Placeholder.component("entity", entityName));
        if (reward.message() == null) {
            plugin.messages().send(killer, "killer-money.mob", placeholders);
        } else if (!reward.message().isEmpty()) {
            killer.sendMessage(Text.parse(reward.message(), prefix(), placeholders));
        }
        if (reward.actionbar() == null) {
            actionBar(killer, plugin.messages().get("killer-money.mob-actionbar", placeholders));
        } else if (!reward.actionbar().isEmpty()) {
            actionBar(killer, Text.parse(reward.actionbar(), prefix(), placeholders));
        }
        Sounds.play(killer, reward.sound());

        String plainEntity = reward.name() != null ? Text.plain(entityName) : type.getKey().getKey();
        runCommands(reward.commands(), Map.of(
                "<player>", killer.getName(),
                "<entity>", plainEntity,
                "<amount>", amount.toPlainString()));
    }

    // ---------------------------------------------------------------- helpers

    private TagResolver prefix() {
        return Placeholder.component("prefix", plugin.messages().get("prefix"));
    }

    private static void actionBar(Player player, Component message) {
        if (!Component.empty().equals(message)) {
            player.sendActionBar(message);
        }
    }

    private void runCommands(List<String> commands, Map<String, String> placeholders) {
        for (String template : commands) {
            String command = template.startsWith("/") ? template.substring(1) : template;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                command = command.replace(entry.getKey(), entry.getValue());
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "KillerMoney: command '" + command + "' failed", e);
            }
        }
    }
}
