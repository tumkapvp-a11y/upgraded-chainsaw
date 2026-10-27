package dev.applesub.feature;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class NightVision {

    /** Finite night vision shorter than this gets replaced by the infinite one. */
    private static final int REFRESH_BELOW_TICKS = 20 * 15;

    private NightVision() {
    }

    public static void ensure(Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (current != null && (current.isInfinite() || current.getDuration() > REFRESH_BELOW_TICKS)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                PotionEffect.INFINITE_DURATION, 0, true, false, true));
    }

    /** Removes only the infinite effect so potions the player drank stay intact. */
    public static void remove(Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (current != null && current.isInfinite()) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }
}
