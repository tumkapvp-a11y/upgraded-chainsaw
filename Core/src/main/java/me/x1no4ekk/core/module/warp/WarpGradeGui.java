package me.x1no4ekk.core.module.warp;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.x1no4ekk.core.CorePlugin;
import me.x1no4ekk.core.user.User;
import me.x1no4ekk.core.user.UserManager;
import me.x1no4ekk.core.util.Durations;
import me.x1no4ekk.core.util.ItemBuilder;
import me.x1no4ekk.core.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grade a player warp from 1 to 5. Owners cannot grade their own warps; a persistent cooldown limits spam.
 */
final class WarpGradeGui extends WarpMenu {

    static final String COOLDOWN_KEY = "warp-grade";

    private final String warpName;

    WarpGradeGui(WarpModule module, UUID viewerId, Warp warp) {
        super(module, viewerId, module.config().gradeMenu().rows(),
                Text.parse(module.config().gradeMenu().title(), Placeholder.unparsed("warp", warp.name())));
        this.warpName = warp.name();
        render(warp);
    }

    private void render(Warp warp) {
        WarpConfig config = module.config();
        WarpConfig.GradeMenu layout = config.gradeMenu();
        clear();
        fill(layout.filler(), List.of());
        setItem(layout.warpSlot(), WarpFormat.icon(config, warp, layout.warpItem(), false));
        for (Map.Entry<Integer, WarpConfig.GradeButton> entry : layout.grades().entrySet()) {
            int grade = entry.getKey();
            setItem(entry.getValue().slot(), gradeHead(layout.gradeItem(), entry.getValue().texture(), grade),
                    (player, event) -> onGrade(player, grade));
        }
        setItem(layout.back().slot(), button(layout.back()),
                (player, event) -> nextTick(viewer -> new PlayersWarpGui(module, viewer.getUniqueId()).open(viewer)));
    }

    private static ItemStack gradeHead(WarpConfig.ItemTemplate template, String texture, int grade) {
        // Stable UUID per texture so identical heads stack and are cached by the client.
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new ProfileProperty("textures", texture));
        TagResolver placeholder = Placeholder.unparsed("grade", String.valueOf(grade));
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .head(profile)
                .name(template.name(), placeholder)
                .lore(template.lore(), placeholder)
                .build();
    }

    private void onGrade(Player player, int grade) {
        CorePlugin plugin = module.plugin();
        Warp warp = module.manager().get(warpName);
        if (warp == null || warp.system()) {
            closeLater();
            plugin.messages().send(player, "warp.not-found", Placeholder.unparsed("warp", warpName));
            return;
        }
        if (warp.isOwnedBy(player.getUniqueId())) {
            plugin.messages().send(player, "warp.grade-own");
            return;
        }
        UserManager users = plugin.userManager();
        User user = users.getUser(player);
        if (user == null) {
            plugin.messages().send(player, "general.data-not-loaded");
            return;
        }
        long remaining = users.getCooldownRemaining(user, COOLDOWN_KEY);
        if (remaining > 0) {
            plugin.messages().send(player, "warp.grade-cooldown", Placeholder.unparsed("time", Durations.formatMillis(remaining)));
            return;
        }
        users.startCooldown(user, COOLDOWN_KEY, module.config().gradeCooldownMillis());
        closeLater();

        UUID graderId = player.getUniqueId();
        String graderName = player.getName();
        UUID ownerId = warp.owner();
        TagResolver placeholders = TagResolver.resolver(
                Placeholder.unparsed("warp", warp.name()),
                Placeholder.unparsed("grade", String.valueOf(grade)),
                Placeholder.unparsed("player", graderName));
        module.manager().grade(warp, graderId, grade).whenCompleteAsync((ignored, error) -> {
            Player grader = Bukkit.getPlayer(graderId);
            if (error != null) {
                if (grader != null) {
                    plugin.messages().send(grader, "general.database-error");
                }
                return;
            }
            if (grader != null) {
                plugin.messages().send(grader, "warp.graded", placeholders);
            }
            Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
            if (owner != null) {
                plugin.messages().send(owner, "warp.grade-received", placeholders);
            }
        }, plugin.mainThread());
    }
}
