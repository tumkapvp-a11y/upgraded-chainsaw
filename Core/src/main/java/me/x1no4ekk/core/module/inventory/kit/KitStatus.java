package me.x1no4ekk.core.module.inventory.kit;

/**
 * Availability of a kit for a particular player; keys match {@code gui.status} in kits.yml.
 */
public enum KitStatus {

    AVAILABLE("available"),
    COOLDOWN("cooldown"),
    CLAIMED("claimed"),
    NO_PERMISSION("no-permission");

    private final String configKey;

    KitStatus(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
