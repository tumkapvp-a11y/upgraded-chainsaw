package me.x1no4ekk.core.module.warp;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.x1no4ekk.core.util.ItemBuilder;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Locale;

/**
 * Shared placeholders and icons for warp menus and /warpinfo. Main thread only.
 */
final class WarpFormat {

    private WarpFormat() {
    }

    /**
     * Current name of the owner if online, otherwise the name known from MySQL.
     */
    static String ownerName(WarpConfig config, Warp warp) {
        if (warp.system() || warp.owner() == null) {
            return config.serverOwner();
        }
        Player online = Bukkit.getPlayer(warp.owner());
        if (online != null) {
            return online.getName();
        }
        return warp.ownerName() != null ? warp.ownerName() : config.unknownOwner();
    }

    static Component rating(WarpConfig config, Warp warp) {
        if (warp.ratings() <= 0) {
            return Text.parse(config.noRating());
        }
        return Text.parse(config.ratingFormat(),
                Placeholder.unparsed("average", String.format(Locale.ROOT, "%.1f", warp.rating())),
                Placeholder.unparsed("count", String.valueOf(warp.ratings())));
    }

    /**
     * {@code <warp> <owner> <world> <x> <y> <z> <visits> <rating> <ratings> <created>}
     */
    static TagResolver placeholders(WarpConfig config, Warp warp, String ownerName) {
        return TagResolver.resolver(
                Placeholder.unparsed("warp", warp.name()),
                Placeholder.unparsed("owner", ownerName),
                Placeholder.unparsed("world", warp.world()),
                Placeholder.unparsed("x", String.valueOf((int) Math.floor(warp.x()))),
                Placeholder.unparsed("y", String.valueOf((int) Math.floor(warp.y()))),
                Placeholder.unparsed("z", String.valueOf((int) Math.floor(warp.z()))),
                Placeholder.unparsed("visits", String.valueOf(warp.visits())),
                Placeholder.component("rating", rating(config, warp)),
                Placeholder.unparsed("ratings", String.valueOf(warp.ratings())),
                Placeholder.unparsed("created", config.dateFormat().format(Instant.ofEpochMilli(warp.createdAt())))
        );
    }

    static TagResolver placeholders(WarpConfig config, Warp warp) {
        return placeholders(config, warp, ownerName(config, warp));
    }

    /**
     * Warp icon, or the owner's head when {@code ownerHead} is set for a player warp.
     */
    static ItemStack icon(WarpConfig config, Warp warp, WarpConfig.ItemTemplate template, boolean ownerHead) {
        TagResolver placeholders = placeholders(config, warp);
        ItemBuilder builder;
        if (ownerHead && warp.owner() != null) {
            Player online = Bukkit.getPlayer(warp.owner());
            // Online profiles carry skin textures; offline ones are resolved by the client from UUID + name.
            PlayerProfile profile = online != null ? online.getPlayerProfile() : Bukkit.createProfile(warp.owner(), warp.ownerName());
            builder = ItemBuilder.of(Material.PLAYER_HEAD).head(profile);
        } else {
            builder = ItemBuilder.of(warp.icon() != null ? warp.icon() : config.defaultIcon());
        }
        return builder.name(template.name(), placeholders).lore(template.lore(), placeholders).build();
    }
}
