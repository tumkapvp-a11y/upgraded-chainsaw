package me.x1no4ekk.core.teleport;

/**
 * How a teleport is performed.
 *
 * @param warmup      apply the configured warmup (skipped with {@code core.teleport.bypass-warmup})
 * @param checkCombat deny while the player is in combat (skipped with {@code core.combat.bypass})
 * @param recordBack  store the departure point for /back
 * @param sound       play the configured arrival sound
 */
public record TeleportOptions(boolean warmup, boolean checkCombat, boolean recordBack, boolean sound) {

    /** Player-initiated teleports: /spawn, /warp, /tpa, /back, /rtp, guild home. */
    public static final TeleportOptions PLAYER = new TeleportOptions(true, true, true, true);

    /** Staff teleports: /tp, /tphere, /tppos, /top, /playerhome. */
    public static final TeleportOptions ADMIN = new TeleportOptions(false, false, true, true);

    /** Teleport Horn: instant, but not allowed in combat; the horn plays its own sound. */
    public static final TeleportOptions HORN = new TeleportOptions(false, true, true, false);
}
