package wgextender.features.regionprotect.ownormembased;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.cause.Cause;
import com.sk89q.worldguard.bukkit.event.DelegateEvent;
import com.sk89q.worldguard.bukkit.event.entity.DamageEntityEvent;
import com.sk89q.worldguard.bukkit.internal.WGMetadata;
import com.sk89q.worldguard.bukkit.listener.AbstractListener;
import com.sk89q.worldguard.bukkit.listener.RegionProtectionListener;
import com.sk89q.worldguard.bukkit.protection.events.DisallowedPVPEvent;
import com.sk89q.worldguard.bukkit.util.Entities;
import com.sk89q.worldguard.bukkit.util.Events;
import com.sk89q.worldguard.bukkit.util.InteropUtils;
import com.sk89q.worldguard.commands.CommandUtils;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import wgextender.Config;
import wgextender.utils.WGRegionUtils;

/**
 * Replaces WorldGuard's entity damage handler to implement misc.pvpmode.
 * The code follows RegionProtectionListener#onDamageEntity of WorldGuard 7.0.16, only the PvP branch differs.
 * Injected only when pvpmode is "allow" or "deny"; in "default" mode WorldGuard's handler stays untouched.
 */
public final class PvPHandlingListener extends AbstractListener {

    private static final String DENY_MESSAGE_KEY = "worldguard.region.lastMessage";
    private static final int LAST_MESSAGE_DELAY = 500;

    private final Config config;
    private final List<RegisteredListener> replaced = new ArrayList<>();
    private boolean injected;

    public PvPHandlingListener(Config config) {
        super(WorldGuardPlugin.inst());
        this.config = config;
    }

    public void inject(Plugin plugin) {
        if (injected) {
            return;
        }
        HandlerList handlers = DamageEntityEvent.getHandlerList();
        for (RegisteredListener listener : handlers.getRegisteredListeners()) {
            if (listener.getListener().getClass() == RegionProtectionListener.class) {
                replaced.add(listener);
                handlers.unregister(listener);
            }
        }
        if (replaced.isEmpty()) {
            throw new IllegalStateException("WorldGuard RegionProtectionListener is not registered");
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        injected = true;
    }

    public void uninject() {
        if (!injected) {
            return;
        }
        HandlerList.unregisterAll(this);
        HandlerList handlers = DamageEntityEvent.getHandlerList();
        for (RegisteredListener listener : replaced) {
            handlers.register(listener);
        }
        replaced.clear();
        injected = false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageEntity(DamageEntityEvent event) {
        if (event.getResult() == Result.ALLOW) return; // pre-allowed by another plugin
        if (!isRegionSupportEnabled(event.getWorld())) return;

        com.sk89q.worldedit.util.Location target = BukkitAdapter.adapt(event.getTarget());
        RegionAssociable associable = createRegionAssociable(event.getCause());
        RegionQuery query = WGRegionUtils.getQuery();
        Player playerAttacker = event.getCause().getFirstPlayer();
        boolean canDamage;
        String what;

        boolean pvp = event.getEntity() instanceof Player && playerAttacker != null && !playerAttacker.equals(event.getEntity());
        if (isWhitelisted(event.getCause(), event.getWorld(), pvp)) {
            return;
        }

        if (Entities.isHostile(event.getEntity()) || Entities.isAmbient(event.getEntity())) {
            canDamage = event.getRelevantFlags().isEmpty() || query.queryState(target, associable, combine(event)) != State.DENY;
            what = "hit that";
        } else if (Entities.isVehicle(event.getEntity().getType())) {
            canDamage = query.testBuild(target, associable, combine(event, Flags.DESTROY_VEHICLE));
            what = "change that";
        } else if (Entities.isConsideredBuildingIfUsed(event.getEntity())) {
            canDamage = query.testBuild(target, associable, combine(event));
            what = "change that";
        } else if (pvp) {
            LocalPlayer localAttacker = getPlugin().wrapPlayer(playerAttacker);
            Player defender = (Player) event.getEntity();
            if (Entities.isNPC(defender)) {
                return;
            }

            canDamage = canPvP(query, event, target, associable, playerAttacker, localAttacker);
            if (!canDamage && Events.fireAndTestCancel(new DisallowedPVPEvent(playerAttacker, defender, event.getOriginalEvent()))) {
                canDamage = true;
            }
            what = "PvP";
        } else if (event.getEntity() instanceof Player) {
            canDamage = event.getRelevantFlags().isEmpty() || query.queryState(target, associable, combine(event)) != State.DENY;
            what = "damage that";
        } else if (Entities.isNonHostile(event.getEntity())) {
            canDamage = query.testBuild(target, associable, combine(event, Flags.DAMAGE_ANIMALS));
            what = "harm that";
        } else {
            canDamage = query.testBuild(target, associable, combine(event, Flags.INTERACT));
            what = "hit that";
        }

        if (!canDamage) {
            tellErrorMessage(event, event.getCause(), event.getTarget(), what);
            event.setCancelled(true);
        }
    }

    private boolean canPvP(RegionQuery query, DamageEntityEvent event, com.sk89q.worldedit.util.Location target,
                           RegionAssociable associable, Player attacker, LocalPlayer localAttacker) {
        com.sk89q.worldedit.util.Location attackerLocation = localAttacker.getLocation();
        StateFlag[] flags = combine(event, Flags.PVP);
        Boolean mode = config.miscDefaultPvPFlagOperationMode;
        if (mode == null) {
            return query.testBuild(target, associable, flags)
                && query.queryState(attackerLocation, localAttacker, flags) != State.DENY
                && query.queryState(target, localAttacker, flags) != State.DENY;
        }
        if (mode) {
            // allow: PvP everywhere unless the pvp flag is explicitly denied
            return query.queryState(attackerLocation, localAttacker, flags) != State.DENY
                && query.queryState(target, localAttacker, flags) != State.DENY;
        }
        // deny: PvP in the wild, inside regions only where pvp is explicitly allowed
        if (query.getApplicableRegions(attackerLocation).size() == 0 && query.getApplicableRegions(target).size() == 0) {
            return true;
        }
        return query.queryState(attackerLocation, localAttacker, flags) == State.ALLOW
            && query.queryState(target, localAttacker, flags) == State.ALLOW;
    }

    private boolean isWhitelisted(Cause cause, World world, boolean pvp) {
        if (cause.getRootCause() instanceof Player player) {
            WorldConfiguration config = getWorldConfig(world);
            if (config.fakePlayerBuildOverride && InteropUtils.isFakePlayer(player)) {
                return true;
            }
            LocalPlayer localPlayer = getPlugin().wrapPlayer(player);
            return !pvp && WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer, localPlayer.getWorld());
        }
        return false;
    }

    private void tellErrorMessage(DelegateEvent event, Cause cause, Location location, String what) {
        if (event.isSilent() || cause.isIndirect()) {
            return;
        }
        if (cause.getRootCause() instanceof Player player) {
            long now = System.currentTimeMillis();
            Long lastTime = WGMetadata.getIfPresent(player, DENY_MESSAGE_KEY, Long.class);
            if (lastTime == null || now - lastTime >= LAST_MESSAGE_DELAY) {
                LocalPlayer localPlayer = getPlugin().wrapPlayer(player);
                String message = WGRegionUtils.getQuery().queryValue(BukkitAdapter.adapt(location), localPlayer, Flags.DENY_MESSAGE);
                if (message != null && !message.isEmpty()) {
                    message = WorldGuard.getInstance().getPlatform().getMatcher().replaceMacros(localPlayer, message);
                    message = CommandUtils.replaceColorMacros(message);
                    localPlayer.printRaw(message.replace("%what%", what));
                }
                WGMetadata.put(player, DENY_MESSAGE_KEY, now);
            }
        }
    }

    private static StateFlag[] combine(DelegateEvent event, StateFlag... flag) {
        List<StateFlag> extra = event.getRelevantFlags();
        StateFlag[] flags = Arrays.copyOf(flag, flag.length + extra.size());
        for (int i = 0; i < extra.size(); i++) {
            flags[flag.length + i] = extra.get(i);
        }
        return flags;
    }
}
