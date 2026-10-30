package wgextender.features.flags;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.BooleanFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.flags.registry.UnknownFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import wgextender.utils.WGRegionUtils;

public final class WGExtenderFlags {

    public static StateFlag CHORUS_FRUIT_USE_FLAG = new StateFlag("chorus-fruit-use", true);
    public static BooleanFlag OLDPVP_ATTACKSPEED = new BooleanFlag("oldpvp-attackspeed");
    public static BooleanFlag OLDPVP_NOBOW = new BooleanFlag("oldpvp-nobow");
    public static BooleanFlag OLDPVP_NOSHIELDBLOCK = new BooleanFlag("oldpvp-noshieldblock");

    private static boolean lateRegistrationNeeded;

    private WGExtenderFlags() {
    }

    /**
     * Regular registration, works while WorldGuard has not enabled yet (server start).
     */
    public static void registerOnLoad(Logger logger) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            CHORUS_FRUIT_USE_FLAG = register(registry, CHORUS_FRUIT_USE_FLAG, StateFlag.class, logger);
            OLDPVP_ATTACKSPEED = register(registry, OLDPVP_ATTACKSPEED, BooleanFlag.class, logger);
            OLDPVP_NOBOW = register(registry, OLDPVP_NOBOW, BooleanFlag.class, logger);
            OLDPVP_NOSHIELDBLOCK = register(registry, OLDPVP_NOSHIELDBLOCK, BooleanFlag.class, logger);
        } catch (IllegalStateException e) {
            // Registry is locked: plugin was loaded after WorldGuard enabled (e.g. by a plugin manager).
            lateRegistrationNeeded = true;
        }
    }

    private static <F extends Flag<?>> F register(FlagRegistry registry, F flag, Class<F> type, Logger logger) {
        try {
            registry.register(flag);
            return flag;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get(flag.getName());
            if (type.isInstance(existing)) {
                return type.cast(existing);
            }
            logger.severe("Flag " + flag.getName() + " is already registered by another plugin with a different type, it will not work");
            return flag;
        }
    }

    /**
     * Fallback for late loading: puts the flags straight into the registry map and converts values
     * that WorldGuard already loaded as unknown flags.
     */
    public static void registerLateIfNeeded(Logger logger) {
        if (!lateRegistrationNeeded) {
            return;
        }
        lateRegistrationNeeded = false;
        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            Field field = findField(registry.getClass(), "flags");
            @SuppressWarnings("unchecked")
            Map<String, Flag<?>> flagMap = (Map<String, Flag<?>>) field.get(registry);
            for (Flag<?> flag : new Flag<?>[] {CHORUS_FRUIT_USE_FLAG, OLDPVP_ATTACKSPEED, OLDPVP_NOBOW, OLDPVP_NOSHIELDBLOCK}) {
                injectFlag(flag, flagMap);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.log(Level.SEVERE, "Unable to register WGExtender flags, restart the server instead of hot-loading the plugin", e);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // check superclass
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void injectFlag(Flag<?> flag, Map<String, Flag<?>> flagMap) {
        Flag<?> previous = flagMap.put(flag.getName().toLowerCase(Locale.ROOT), flag);
        if (previous == null || previous == flag) {
            return;
        }
        for (RegionManager manager : WGRegionUtils.getRegionContainer().getLoaded()) {
            for (ProtectedRegion region : manager.getRegions().values()) {
                Map<Flag<?>, Object> regionFlags = region.getFlags();
                Object previousValue = regionFlags.remove(previous);
                if (previousValue == null) {
                    continue;
                }
                try {
                    Object value = previous instanceof UnknownFlag ? flag.unmarshal(previousValue) : previousValue;
                    if (value != null) {
                        regionFlags.put(flag, value);
                    }
                } catch (RuntimeException ignored) {
                    // incompatible value, drop it
                }
                region.setDirty(true);
            }
        }
    }
}
