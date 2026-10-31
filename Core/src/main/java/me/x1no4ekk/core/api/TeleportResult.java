package me.x1no4ekk.core.api;

/**
 * Outcome of a teleport started through Core.
 */
public enum TeleportResult {
    SUCCESS,
    /** Cancelled by moving, taking damage or a newer teleport. */
    CANCELLED,
    /** Denied because the player is in combat. */
    IN_COMBAT,
    /** World not loaded or the teleport event was cancelled. */
    FAILED,
    /** The player left the server during the warmup. */
    QUIT
}
