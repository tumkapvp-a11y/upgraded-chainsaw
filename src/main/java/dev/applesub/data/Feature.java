package dev.applesub.data;

public enum Feature {
    NIGHT_VISION("night-vision"),
    ANTI_FIRE("anti-fire"),
    ANTI_TRASH("anti-trash"),
    ARMOR_NOTIFY("armor-notify");

    private final String key;

    Feature(String key) {
        this.key = key;
    }

    /** Key used in config.yml. */
    public String key() {
        return key;
    }
}
