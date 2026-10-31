package me.x1no4ekk.core.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Unit of JDBC work that produces a value.
 */
@FunctionalInterface
public interface SqlFunction<T> {

    T apply(Connection connection) throws SQLException;
}
