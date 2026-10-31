package me.x1no4ekk.core.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Blocking JDBC access to core_homes. Callers decide the thread (see DatabaseManager).
 */
public final class HomeRepository {

    private static final String SELECT_BY_OWNER =
            "SELECT name, world, x, y, z, yaw, pitch, created_at FROM core_homes WHERE uuid = ?";
    private static final String UPSERT =
            "INSERT INTO core_homes (uuid, name, world, x, y, z, yaw, pitch, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), "
                    + "yaw = VALUES(yaw), pitch = VALUES(pitch)";
    private static final String DELETE =
            "DELETE FROM core_homes WHERE uuid = ? AND name = ?";

    public List<Home> findByOwner(Connection connection, UUID owner) throws SQLException {
        List<Home> homes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_OWNER)) {
            statement.setString(1, owner.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    homes.add(new Home(
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            rs.getLong("created_at")
                    ));
                }
            }
        }
        return homes;
    }

    public void upsert(Connection connection, UUID owner, Home home) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, owner.toString());
            statement.setString(2, home.name());
            statement.setString(3, home.world());
            statement.setDouble(4, home.x());
            statement.setDouble(5, home.y());
            statement.setDouble(6, home.z());
            statement.setFloat(7, home.yaw());
            statement.setFloat(8, home.pitch());
            statement.setLong(9, home.createdAt());
            statement.executeUpdate();
        }
    }

    public boolean delete(Connection connection, UUID owner, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, owner.toString());
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        }
    }
}
