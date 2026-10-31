package me.x1no4ekk.core.util;

import me.x1no4ekk.core.config.Settings;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;

/**
 * Plays configured sounds; invalid keys are ignored.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(Player player, Settings.SoundSettings sound) {
        if (sound == null || !sound.enabled()) {
            return;
        }
        play(player, sound.key(), sound.volume(), sound.pitch());
    }

    public static void play(Player player, String key, float volume, float pitch) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch), Sound.Emitter.self());
        } catch (InvalidKeyException ignored) {
            // Misconfigured sound key: silently skip.
        }
    }
}
