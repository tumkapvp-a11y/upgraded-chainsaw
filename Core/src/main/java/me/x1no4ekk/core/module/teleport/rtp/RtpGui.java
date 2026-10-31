package me.x1no4ekk.core.module.teleport.rtp;

import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.gui.Gui;
import me.x1no4ekk.core.module.teleport.TeleportConfig;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.util.Durations;
import me.x1no4ekk.core.util.ItemBuilder;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * /rtp menu. Each option shows its radius, price, cooldown and the viewer's status at the moment of opening.
 * Holds only the option ids; a click re-reads the current configuration.
 */
public final class RtpGui extends Gui {

    private final CorePlugin plugin;
    private final RtpService service;
    private final Supplier<TeleportConfig> config;

    public RtpGui(CorePlugin plugin, RtpService service, Supplier<TeleportConfig> config, Player viewer) {
        super(config.get().rtp().gui().rows(), Text.parse(config.get().rtp().gui().title()));
        this.plugin = plugin;
        this.service = service;
        this.config = config;
        render(config.get().rtp(), viewer);
    }

    private void render(TeleportConfig.Rtp rtp, Player viewer) {
        TeleportConfig.RtpGui gui = rtp.gui();
        User user = plugin.userManager().getUser(viewer);
        Set<Integer> used = new HashSet<>();
        for (TeleportConfig.RtpOption option : rtp.options().values()) {
            if (option.slot() >= inventory.getSize()) {
                continue;
            }
            used.add(option.slot());
            String id = option.id();
            setItem(option.slot(), icon(gui, option, viewer, user), (player, event) -> onClick(player.getUniqueId(), id));
        }
        if (gui.fillerEnabled()) {
            ItemStack filler = ItemBuilder.of(gui.fillerMaterial()).hideTooltip().build();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                if (!used.contains(slot)) {
                    setItem(slot, filler);
                }
            }
        }
    }

    private ItemStack icon(TeleportConfig.RtpGui gui, TeleportConfig.RtpOption option, Player viewer, User user) {
        String priceText = RtpService.formatPrice(option.price());
        Component price = option.price() > 0
                ? Text.parse(gui.priceFormat(), Placeholder.unparsed("amount", priceText))
                : Text.parse(gui.priceFree());
        TagResolver placeholders = TagResolver.resolver(
                Placeholder.unparsed("option", option.id()),
                Placeholder.unparsed("world", option.world()),
                Placeholder.unparsed("min-radius", String.valueOf(option.minRadius())),
                Placeholder.unparsed("max-radius", String.valueOf(option.maxRadius())),
                Placeholder.unparsed("cooldown", Durations.formatMillis(option.cooldownMillis())),
                Placeholder.component("price", price),
                Placeholder.component("status", status(gui, option, viewer, user, price)));
        return ItemBuilder.of(option.material())
                .name(option.name(), placeholders)
                .lore(option.lore(), placeholders)
                .build();
    }

    private Component status(TeleportConfig.RtpGui gui, TeleportConfig.RtpOption option, Player viewer, User user,
                             Component price) {
        if (!service.hasAccess(viewer, option)) {
            return Text.parse(gui.statusNoPermission());
        }
        long remaining = user == null ? 0 : service.cooldownRemaining(viewer, user, option);
        if (remaining > 0) {
            return Text.parse(gui.statusCooldown(), Placeholder.unparsed("time", Durations.formatMillis(remaining)));
        }
        if (option.price() > 0 && !plugin.vault().has(viewer, option.price())) {
            return Text.parse(gui.statusNoMoney(), Placeholder.component("price", price),
                    Placeholder.unparsed("balance", RtpService.formatPrice(plugin.vault().balance(viewer))));
        }
        return Text.parse(gui.statusReady());
    }

    private void onClick(UUID viewerId, String optionId) {
        // Closing and teleporting must not happen inside InventoryClickEvent.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null) {
                return;
            }
            viewer.closeInventory();
            TeleportConfig.RtpOption option = config.get().rtp().options().get(optionId);
            if (option != null) {
                service.start(viewer, option);
            }
        });
    }
}
