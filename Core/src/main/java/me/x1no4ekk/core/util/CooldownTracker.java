package me.x1no4ekk.core.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cooldowns keyed by player UUID. Owners must call {@link #clear(UUID)} from a quit hook.
 * Thread-safe (used from async chat as well).
 */
public final class CooldownTracker {

    private final Map<UUID, Long> expiresAt = new ConcurrentHashMap<>();

    /**
     * @return remaining milliseconds, 0 if not on cooldown
     */
    public long remaining(UUID uuid) {
        Long until = expiresAt.get(uuid);
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            expiresAt.remove(uuid, until);
            return 0;
        }
        return left;
    }

    public boolean isActive(UUID uuid) {
        return remaining(uuid) > 0;
    }

    public void start(UUID uuid, long millis) {
        if (millis > 0) {
            expiresAt.put(uuid, System.currentTimeMillis() + millis);
        }
    }

    public void clear(UUID uuid) {
        expiresAt.remove(uuid);
    }

    public void clearAll() {
        expiresAt.clear();
    }
}
