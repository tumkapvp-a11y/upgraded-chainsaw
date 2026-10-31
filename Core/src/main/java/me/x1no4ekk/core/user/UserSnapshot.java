package me.x1no4ekk.core.user;

import java.util.UUID;

/**
 * Immutable state of a user row (core_users + core_stats) used for saving and loading.
 */
record UserSnapshot(
        UUID uuid,
        String name,
        String nickname,
        boolean fly,
        boolean god,
        boolean vanish,
        boolean tpToggle,
        boolean msgToggle,
        boolean chatToggle,
        boolean socialSpy,
        boolean bossbarToggle,
        String lastLocation,
        long firstJoin,
        long lastJoin,
        int kills,
        int deaths,
        int mobKills
) {
}
