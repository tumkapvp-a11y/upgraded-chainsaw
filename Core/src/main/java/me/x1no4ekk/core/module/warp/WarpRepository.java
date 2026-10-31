package me.x1no4ekk.core.module.warp;

import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Blocking JDBC access to core_warps and core_warp_grades. Callers choose the thread (see DatabaseManager).
 */
final class WarpRepository {

    record Rating(double average, int count) {
    }

    static final String CREATE_GRADES_TABLE = """
            CREATE TABLE IF NOT EXISTS core_warp_grades (
                warp       VARCHAR(32) NOT NULL,
                uuid       CHAR(36) CHARACTER SET ascii NOT NULL,
                grade      TINYINT     NOT NULL,
                graded_at  BIGINT      NOT NULL,
                PRIMARY KEY (warp, uuid),
                INDEX idx_core_warp_grades_warp (warp)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci""";

    private static final String SELECT_ALL =
            "SELECT w.name, w.owner_uuid, w.system_warp, w.world, w.x, w.y, w.z, w.yaw, w.pitch, w.icon, w.description, "
                    + "w.visits, w.created_at, u.name AS owner_name "
                    + "FROM core_warps w LEFT JOIN core_users u ON u.uuid = w.owner_uuid";
    private static final String SELECT_RATINGS =
            "SELECT warp, AVG(grade) AS rating, COUNT(*) AS ratings FROM core_warp_grades GROUP BY warp";
    private static final String SELECT_RATING =
            "SELECT AVG(grade) AS rating, COUNT(*) AS ratings FROM core_warp_grades WHERE warp = ?";
    private static final String INSERT =
            "INSERT INTO core_warps (name, owner_uuid, system_warp, world, x, y, z, yaw, pitch, icon, description, visits, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_WARP =
            "DELETE FROM core_warps WHERE name = ?";
    private static final String DELETE_GRADES =
            "DELETE FROM core_warp_grades WHERE warp = ?";
    private static final String INCREMENT_VISITS =
            "UPDATE core_warps SET visits = visits + 1 WHERE name = ?";
    private static final String UPSERT_GRADE =
            "INSERT INTO core_warp_grades (warp, uuid, grade, graded_at) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE grade = VALUES(grade), graded_at = VALUES(graded_at)";
    private static final String SELECT_USER_NAME =
            "SELECT name FROM core_users WHERE uuid = ?";

    private final Logger logger;

    WarpRepository(Logger logger) {
        this.logger = logger;
    }

    /**
     * Every warp with its grade aggregate; owner names come from core_users.
     */
    List<Warp> loadAll(Connection connection) throws SQLException {
        Map<String, Rating> ratings = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_RATINGS);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                ratings.put(Warp.key(rs.getString("warp")), new Rating(rs.getDouble("rating"), rs.getInt("ratings")));
            }
        }

        List<Warp> warps = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                UUID owner = null;
                String rawOwner = rs.getString("owner_uuid");
                if (rawOwner != null) {
                    try {
                        owner = UUID.fromString(rawOwner);
                    } catch (IllegalArgumentException e) {
                        logger.warning("Warp '" + name + "' has an invalid owner UUID '" + rawOwner + "' and was skipped");
                        continue;
                    }
                }
                String rawIcon = rs.getString("icon");
                Material icon = rawIcon == null ? null : Material.matchMaterial(rawIcon);
                if (icon != null && (icon.isAir() || !icon.isItem())) {
                    icon = null;
                }
                Rating rating = ratings.getOrDefault(Warp.key(name), new Rating(0, 0));
                warps.add(new Warp(
                        name,
                        owner,
                        rs.getString("owner_name"),
                        rs.getBoolean("system_warp") || owner == null,
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch"),
                        icon,
                        rs.getString("description"),
                        rs.getInt("visits"),
                        rs.getLong("created_at"),
                        rating.average(),
                        rating.count()
                ));
            }
        }
        return warps;
    }

    void insert(Connection connection, Warp warp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, warp.name());
            if (warp.owner() == null) {
                statement.setNull(2, Types.CHAR);
            } else {
                statement.setString(2, warp.owner().toString());
            }
            statement.setBoolean(3, warp.system());
            statement.setString(4, warp.world());
            statement.setDouble(5, warp.x());
            statement.setDouble(6, warp.y());
            statement.setDouble(7, warp.z());
            statement.setFloat(8, warp.yaw());
            statement.setFloat(9, warp.pitch());
            if (warp.icon() == null) {
                statement.setNull(10, Types.VARCHAR);
            } else {
                statement.setString(10, warp.icon().getKey().asString());
            }
            if (warp.description() == null) {
                statement.setNull(11, Types.VARCHAR);
            } else {
                statement.setString(11, warp.description());
            }
            statement.setInt(12, warp.visits());
            statement.setLong(13, warp.createdAt());
            statement.executeUpdate();
        }
    }

    /**
     * Removes the warp together with its grades; run inside a transaction.
     */
    void delete(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_GRADES)) {
            statement.setString(1, name);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(DELETE_WARP)) {
            statement.setString(1, name);
            statement.executeUpdate();
        }
    }

    void incrementVisits(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INCREMENT_VISITS)) {
            statement.setString(1, name);
            statement.executeUpdate();
        }
    }

    void upsertGrade(Connection connection, String warp, UUID uuid, int grade, long gradedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_GRADE)) {
            statement.setString(1, warp);
            statement.setString(2, uuid.toString());
            statement.setInt(3, grade);
            statement.setLong(4, gradedAt);
            statement.executeUpdate();
        }
    }

    Rating loadRating(Connection connection, String warp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_RATING)) {
            statement.setString(1, warp);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new Rating(rs.getDouble("rating"), rs.getInt("ratings"));
                }
            }
        }
        return new Rating(0, 0);
    }

    Optional<String> findUserName(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_USER_NAME)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString("name")) : Optional.empty();
            }
        }
    }
}
