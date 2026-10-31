package me.x1no4ekk.core.user;

import me.x1no4ekk.core.home.Home;
import me.x1no4ekk.core.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached data of an online player. Lives only in {@link UserManager} while the player is online and
 * is dropped on quit; it never references a {@link Player} or any other entity — only the UUID.
 * <p>
 * Simple fields are volatile because async chat and the database threads read them;
 * mutations happen on the main thread.
 */
public final class User {

    private final UUID uuid;
    private final long firstJoin;
    private final boolean newPlayer;
    private final long loadedAt = System.currentTimeMillis();

    private volatile String name;
    private volatile String nickname;
    private volatile boolean fly;
    private volatile boolean god;
    private volatile boolean vanish;
    private volatile boolean tpToggle;
    private volatile boolean msgToggle;
    private volatile boolean chatToggle;
    private volatile boolean socialSpy;
    private volatile boolean bossbarToggle;
    /** Serialized (see {@link Locations}) so no World reference is held. */
    private volatile String lastLocation;
    private volatile long lastJoin;

    private volatile int kills;
    private volatile int deaths;
    private volatile int mobKills;

    /** Keyed by lower-case name: MySQL compares home names case-insensitively. */
    private final Map<String, Home> homes = new ConcurrentHashMap<>();
    private final Set<UUID> ignored = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> kitCooldowns = new ConcurrentHashMap<>();
    /** Persistent cooldowns (core_cooldowns): key -> expiry epoch millis. Managed by CooldownService. */
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    private volatile boolean dirty;

    User(UUID uuid, String name, long firstJoin, long lastJoin, boolean newPlayer) {
        this.uuid = uuid;
        this.name = name;
        this.firstJoin = firstJoin;
        this.lastJoin = lastJoin;
        this.newPlayer = newPlayer;
        this.tpToggle = true;
        this.msgToggle = true;
        this.chatToggle = true;
        this.bossbarToggle = true;
    }

    // ---------------------------------------------------------------- identity

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (!name.equals(this.name)) {
            this.name = name;
            markDirty();
        }
    }

    /**
     * Resolves the online player on demand; never cached.
     */
    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public boolean isNewPlayer() {
        return newPlayer;
    }

    public long getFirstJoin() {
        return firstJoin;
    }

    public long getLastJoin() {
        return lastJoin;
    }

    public void setLastJoin(long lastJoin) {
        this.lastJoin = lastJoin;
        markDirty();
    }

    long getLoadedAt() {
        return loadedAt;
    }

    // ---------------------------------------------------------------- persistent toggles

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
        markDirty();
    }

    public boolean isFly() {
        return fly;
    }

    public void setFly(boolean fly) {
        this.fly = fly;
        markDirty();
    }

    public boolean isGod() {
        return god;
    }

    public void setGod(boolean god) {
        this.god = god;
        markDirty();
    }

    public boolean isVanish() {
        return vanish;
    }

    public void setVanish(boolean vanish) {
        this.vanish = vanish;
        markDirty();
    }

    public boolean isTpToggle() {
        return tpToggle;
    }

    public void setTpToggle(boolean tpToggle) {
        this.tpToggle = tpToggle;
        markDirty();
    }

    public boolean isMsgToggle() {
        return msgToggle;
    }

    public void setMsgToggle(boolean msgToggle) {
        this.msgToggle = msgToggle;
        markDirty();
    }

    public boolean isChatToggle() {
        return chatToggle;
    }

    public void setChatToggle(boolean chatToggle) {
        this.chatToggle = chatToggle;
        markDirty();
    }

    public boolean isSocialSpy() {
        return socialSpy;
    }

    public void setSocialSpy(boolean socialSpy) {
        this.socialSpy = socialSpy;
        markDirty();
    }

    public boolean isBossbarToggle() {
        return bossbarToggle;
    }

    public void setBossbarToggle(boolean bossbarToggle) {
        this.bossbarToggle = bossbarToggle;
        markDirty();
    }

    /**
     * @return last location (for /back), or {@code null} if unknown or its world is not loaded
     */
    public Location getLastLocation() {
        return Locations.deserialize(lastLocation);
    }

    public void setLastLocation(Location location) {
        this.lastLocation = Locations.serialize(location);
        markDirty();
    }

    // ---------------------------------------------------------------- statistics

    public int getKills() {
        return kills;
    }

    public void addKill() {
        kills++;
        markDirty();
    }

    public int getDeaths() {
        return deaths;
    }

    public void addDeath() {
        deaths++;
        markDirty();
    }

    public int getMobKills() {
        return mobKills;
    }

    public void addMobKill() {
        mobKills++;
        markDirty();
    }

    // ---------------------------------------------------------------- homes

    public Home getHome(String name) {
        return homes.get(key(name));
    }

    public boolean hasHome(String name) {
        return homes.containsKey(key(name));
    }

    public int getHomeCount() {
        return homes.size();
    }

    /**
     * @return homes sorted by name; a fresh immutable copy safe to hand to GUIs
     */
    public List<Home> getHomesSorted() {
        List<Home> list = new ArrayList<>(homes.values());
        list.sort(Comparator.comparing(Home::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(list);
    }

    /**
     * Cache-only mutation; persistence is handled by HomeManager.
     */
    public void putHome(Home home) {
        homes.put(key(home.name()), home);
    }

    public Home removeHome(String name) {
        return homes.remove(key(name));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- ignores & kits (persisted immediately by their managers)

    public Set<UUID> getIgnored() {
        return Collections.unmodifiableSet(ignored);
    }

    public boolean isIgnoring(UUID other) {
        return ignored.contains(other);
    }

    public void addIgnored(UUID other) {
        ignored.add(other);
    }

    public void removeIgnored(UUID other) {
        ignored.remove(other);
    }

    public Long getKitLastUsed(String kit) {
        return kitCooldowns.get(kit.toLowerCase(Locale.ROOT));
    }

    public void setKitLastUsed(String kit, long timestamp) {
        kitCooldowns.put(kit.toLowerCase(Locale.ROOT), timestamp);
    }

    /**
     * @return expiry (epoch millis) of a persistent cooldown, or 0
     */
    public long getCooldownExpiry(String key) {
        Long expiry = cooldowns.get(key);
        if (expiry == null) {
            return 0;
        }
        if (expiry <= System.currentTimeMillis()) {
            cooldowns.remove(key, expiry);
            return 0;
        }
        return expiry;
    }

    void putCooldown(String key, long expiresAt) {
        cooldowns.put(key, expiresAt);
    }

    // ---------------------------------------------------------------- persistence helpers

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    void clearDirty() {
        this.dirty = false;
    }

    /**
     * Immutable copy of everything stored in core_users / core_stats. Taken on the main thread so the
     * writer thread never observes a half-updated user.
     */
    UserSnapshot snapshot() {
        return new UserSnapshot(uuid, name, nickname, fly, god, vanish, tpToggle, msgToggle, chatToggle,
                socialSpy, bossbarToggle, lastLocation, firstJoin, lastJoin, kills, deaths, mobKills);
    }

    /**
     * Applies values loaded from the database. Only called before the user is published to the cache.
     */
    void load(UserSnapshot data, Collection<Home> loadedHomes, Collection<UUID> loadedIgnores,
              Map<String, Long> loadedKits, Map<String, Long> loadedCooldowns) {
        this.nickname = data.nickname();
        this.fly = data.fly();
        this.god = data.god();
        this.vanish = data.vanish();
        this.tpToggle = data.tpToggle();
        this.msgToggle = data.msgToggle();
        this.chatToggle = data.chatToggle();
        this.socialSpy = data.socialSpy();
        this.bossbarToggle = data.bossbarToggle();
        this.lastLocation = data.lastLocation();
        this.kills = data.kills();
        this.deaths = data.deaths();
        this.mobKills = data.mobKills();
        for (Home home : loadedHomes) {
            putHome(home);
        }
        ignored.addAll(loadedIgnores);
        loadedKits.forEach((kit, time) -> kitCooldowns.put(kit.toLowerCase(Locale.ROOT), time));
        cooldowns.putAll(loadedCooldowns);
    }
}
