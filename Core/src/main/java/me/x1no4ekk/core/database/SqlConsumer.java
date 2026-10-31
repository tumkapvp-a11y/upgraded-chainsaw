package me.x1no4ekk.core.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Unit of JDBC work without a result.
 */
@FunctionalInterface
public interface SqlConsumer {

    void accept(Connection connection) throws SQLException;
}
