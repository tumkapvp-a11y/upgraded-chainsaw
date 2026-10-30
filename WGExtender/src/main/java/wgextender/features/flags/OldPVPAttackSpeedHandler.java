package wgextender.features.flags;

import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.SessionManager;
import com.sk89q.worldguard.session.handler.FlagValueChangeHandler;
import com.sk89q.worldguard.session.handler.Handler;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import wgextender.WGExtender;
import wgextender.utils.WGRegionUtils;

/**
 * oldpvp-attackspeed flag. WorldGuard calls the handler only when the flag value at the player's
 * position changes, so there is no per-tick region lookup for every online player.
 * The bonus is a transient attribute modifier: it is never saved to player data, so a crash
 * cannot leave a player with modified attack speed.
 */
public final class OldPVPAttackSpeedHandler extends FlagValueChangeHandler<Boolean> {

    private static final double OLD_PVP_ATTACK_SPEED = 16.0;

    private static volatile NamespacedKey modifierKey;
    private static volatile boolean active;

    private OldPVPAttackSpeedHandler(Session session) {
        super(session, WGExtenderFlags.OLDPVP_ATTACKSPEED);
    }

    @Override
    protected void onInitialValue(LocalPlayer player, ApplicableRegionSet set, Boolean value) {
        apply(player, Boolean.TRUE.equals(value));
    }

    @Override
    protected boolean onSetValue(LocalPlayer player, Location from, Location to, ApplicableRegionSet toSet,
                                 Boolean currentValue, Boolean lastValue, MoveType moveType) {
        apply(player, Boolean.TRUE.equals(currentValue));
        return true;
    }

    @Override
    protected boolean onAbsentValue(LocalPlayer player, Location from, Location to, ApplicableRegionSet toSet,
                                    Boolean lastValue, MoveType moveType) {
        apply(player, false);
        return true;
    }

    // WorldGuard ticks sessions once per second with an already computed region set; this picks up
    // flag edits made while the player stays in the same region.
    @Override
    public void tick(LocalPlayer player, ApplicableRegionSet set) {
        apply(player, Boolean.TRUE.equals(set.queryValue(player, WGExtenderFlags.OLDPVP_ATTACKSPEED)));
    }

    private static void apply(LocalPlayer localPlayer, boolean enabled) {
        if (!active) {
            // Handler instances stay in existing sessions after the plugin is disabled.
            return;
        }
        Player player = localPlayer instanceof BukkitPlayer bukkitPlayer ? bukkitPlayer.getPlayer() : Bukkit.getPlayer(localPlayer.getUniqueId());
        if (player != null) {
            apply(player, enabled);
        }
    }

    private static void apply(Player player, boolean enabled) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        NamespacedKey key = modifierKey;
        if (attribute == null || key == null) {
            return;
        }
        boolean present = attribute.getModifier(key) != null;
        if (enabled && !present) {
            double bonus = OLD_PVP_ATTACK_SPEED - attribute.getBaseValue();
            if (bonus > 0) {
                attribute.addTransientModifier(new AttributeModifier(key, bonus, AttributeModifier.Operation.ADD_NUMBER));
            }
        } else if (!enabled && present) {
            attribute.removeModifier(key);
        }
    }

    public static Registration register(WGExtender plugin) {
        modifierKey = new NamespacedKey(plugin, "oldpvp_attack_speed");
        active = true;
        Factory factory = new Factory();
        SessionManager sessionManager = WGRegionUtils.getPlatform().getSessionManager();
        sessionManager.registerHandler(factory, null);

        // Sessions of players that are already online (plugin hot-load) do not pick up new factories.
        for (Player player : Bukkit.getOnlinePlayers()) {
            LocalPlayer localPlayer = WGRegionUtils.wrapPlayer(player);
            Session session = sessionManager.getIfPresent(localPlayer);
            if (session != null && session.getHandler(OldPVPAttackSpeedHandler.class) == null) {
                OldPVPAttackSpeedHandler handler = factory.create(session);
                session.register(handler);
                Location location = localPlayer.getLocation();
                handler.initialize(localPlayer, location, WGRegionUtils.getQuery().getApplicableRegions(location));
            }
        }
        return new Registration(sessionManager, factory);
    }

    public record Registration(SessionManager sessionManager, Factory factory) {
        public void unregister() {
            active = false;
            sessionManager.unregisterHandler(factory);
            for (Player player : Bukkit.getOnlinePlayers()) {
                apply(player, false);
            }
        }
    }

    public static final class Factory extends Handler.Factory<OldPVPAttackSpeedHandler> {
        @Override
        public OldPVPAttackSpeedHandler create(Session session) {
            return new OldPVPAttackSpeedHandler(session);
        }
    }
}
