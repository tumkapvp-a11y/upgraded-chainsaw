package me.x1no4ekk.core.user;

import me.x1no4ekk.core.home.Home;
import me.x1no4ekk.core.home.HomeRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Blocking JDBC access to core_users, core_stats, core_ignores and core_kit_cooldowns.
 */
public final class UserRepository {

    private static final String SELECT_USER =
            "SELECT name, nickname, fly, god, vanish, tp_toggle, msg_toggle, chat_toggle, social_spy, bossbar_toggle, "
                    + "last_location, first_join, last_join FROM core_users WHERE uuid = ?";
    private static final String INSERT_USER =
            "INSERT INTO core_users (uuid, name, first_join, last_join) VALUES (?, ?, ?, ?)";
    private static final String UPSERT_USER =
            "INSERT INTO core_users (uuid, name, nickname, fly, god, vanish, tp_toggle, msg_toggle, chat_toggle, "
                    + "social_spy, bossbar_toggle, last_location, first_join, last_join) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE name = VALUES(name), nickname = VALUES(nickname), fly = VALUES(fly), "
                    + "god = VALUES(god), vanish = VALUES(vanish), tp_toggle = VALUES(tp_toggle), "
                    + "msg_toggle = VALUES(msg_toggle), chat_toggle = VALUES(chat_toggle), social_spy = VALUES(social_spy), "
                    + "bossbar_toggle = VALUES(bossbar_toggle), last_location = VALUES(last_location), "
                    + "last_join = VALUES(last_join)";
    private static final String SELECT_STATS =
            "SELECT kills, deaths, mob_kills FROM core_stats WHERE uuid = ?";
    private static final String UPSERT_STATS =
            "INSERT INTO core_stats (uuid, kills, deaths, mob_kills) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE kills = VALUES(kills), deaths = VALUES(deaths), mob_kills = VALUES(mob_kills)";
    private static final String SELECT_IGNORES =
            "SELECT ignored_uuid FROM core_ignores WHERE uuid = ?";
    private static final String SELECT_KITS =
            "SELECT kit, last_used FROM core_kit_cooldowns WHERE uuid = ?";
    private static final String SELECT_COOLDOWNS =
            "SELECT cooldown_key, expires_at FROM core_cooldowns WHERE uuid = ? AND expires_at > ?";
    private static final String UPSERT_COOLDOWN =
            "INSERT INTO core_cooldowns (uuid, cooldown_key, expires_at) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)";
    private static final String SELECT_UUID_BY_NAME =
            "SELECT uuid, name FROM core_users WHERE name = ? ORDER BY last_join DESC LIMIT 1";

    private final HomeRepository homeRepository;

    public UserRepository(HomeRepository homeRepository) {
        this.homeRepository = homeRepository;
    }

    /**
     * Loads a user with homes, ignores and kit cooldowns, creating the row on first join.
     */
    public User loadOrCreate(Connection connection, UUID uuid, String name) throws SQLException {
        long now = System.currentTimeMillis();
        UserSnapshot stored = selectUser(connection, uuid);
        if (stored == null) {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_USER)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setLong(3, now);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
            User user = new User(uuid, name, now, now, true);
            user.markDirty();
            return user;
        }

        int[] stats = selectStats(connection, uuid);
        UserSnapshot withStats = new UserSnapshot(uuid, stored.name(), stored.nickname(), stored.fly(), stored.god(),
                stored.vanish(), stored.tpToggle(), stored.msgToggle(), stored.chatToggle(), stored.socialSpy(),
                stored.bossbarToggle(), stored.lastLocation(), stored.firstJoin(), stored.lastJoin(),
                stats[0], stats[1], stats[2]);

        List<Home> homes = homeRepository.findByOwner(connection, uuid);
        List<UUID> ignores = selectIgnores(connection, uuid);
        Map<String, Long> kits = selectKits(connection, uuid);
        Map<String, Long> cooldowns = selectCooldowns(connection, uuid, now);

        User user = new User(uuid, name, stored.firstJoin(), now, false);
        user.load(withStats, homes, ignores, kits, cooldowns);
        if (!name.equals(stored.name())) {
            user.markDirty();
        }
        return user;
    }

    public void save(Connection connection, UserSnapshot snapshot) throws SQLException {
        saveAll(connection, List.of(snapshot));
    }

    /**
     * Batched upsert of users and their stats; call inside a transaction for atomicity.
     */
    public void saveAll(Connection connection, Collection<UserSnapshot> snapshots) throws SQLException {
        if (snapshots.isEmpty()) {
            return;
        }
        try (PreparedStatement users = connection.prepareStatement(UPSERT_USER);
             PreparedStatement stats = connection.prepareStatement(UPSERT_STATS)) {
            for (UserSnapshot s : snapshots) {
                String id = s.uuid().toString();
                users.setString(1, id);
                users.setString(2, s.name());
                if (s.nickname() == null) {
                    users.setNull(3, Types.VARCHAR);
                } else {
                    users.setString(3, s.nickname());
                }
                users.setBoolean(4, s.fly());
                users.setBoolean(5, s.god());
                users.setBoolean(6, s.vanish());
                users.setBoolean(7, s.tpToggle());
                users.setBoolean(8, s.msgToggle());
                users.setBoolean(9, s.chatToggle());
                users.setBoolean(10, s.socialSpy());
                users.setBoolean(11, s.bossbarToggle());
                if (s.lastLocation() == null) {
                    users.setNull(12, Types.VARCHAR);
                } else {
                    users.setString(12, s.lastLocation());
                }
                users.setLong(13, s.firstJoin());
                users.setLong(14, s.lastJoin());
                users.addBatch();

                stats.setString(1, id);
                stats.setInt(2, s.kills());
                stats.setInt(3, s.deaths());
                stats.setInt(4, s.mobKills());
                stats.addBatch();
            }
            users.executeBatch();
            stats.executeBatch();
        }
    }

    /**
     * Case-insensitive lookup of the most recent player with this name.
     */
    public Optional<Map.Entry<UUID, String>> findByName(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_UUID_BY_NAME)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(Map.entry(UUID.fromString(rs.getString("uuid")), rs.getString("name")));
                }
            }
        }
        return Optional.empty();
    }

    public void saveCooldown(Connection connection, UUID uuid, String key, long expiresAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_COOLDOWN)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, key);
            statement.setLong(3, expiresAt);
            statement.executeUpdate();
        }
    }

    private Map<String, Long> selectCooldowns(Connection connection, UUID uuid, long now) throws SQLException {
        Map<String, Long> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_COOLDOWNS)) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, now);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("cooldown_key"), rs.getLong("expires_at"));
                }
            }
        }
        return result;
    }

    private UserSnapshot selectUser(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_USER)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new UserSnapshot(
                        uuid,
                        rs.getString("name"),
                        rs.getString("nickname"),
                        rs.getBoolean("fly"),
                        rs.getBoolean("god"),
                        rs.getBoolean("vanish"),
                        rs.getBoolean("tp_toggle"),
                        rs.getBoolean("msg_toggle"),
                        rs.getBoolean("chat_toggle"),
                        rs.getBoolean("social_spy"),
                        rs.getBoolean("bossbar_toggle"),
                        rs.getString("last_location"),
                        rs.getLong("first_join"),
                        rs.getLong("last_join"),
                        0, 0, 0
                );
            }
        }
    }

    private int[] selectStats(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_STATS)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new int[]{rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("mob_kills")};
                }
            }
        }
        return new int[3];
    }

    private List<UUID> selectIgnores(Connection connection, UUID uuid) throws SQLException {
        List<UUID> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_IGNORES)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(UUID.fromString(rs.getString("ignored_uuid")));
                }
            }
        }
        return result;
    }

    private Map<String, Long> selectKits(Connection connection, UUID uuid) throws SQLException {
        Map<String, Long> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_KITS)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("kit"), rs.getLong("last_used"));
                }
            }
        }
        return result;
    }
}
