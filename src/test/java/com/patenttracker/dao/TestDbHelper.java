package com.patenttracker.dao;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class TestDbHelper {

    private TestDbHelper() {}

    public static Connection createTestConnection() throws SQLException, IOException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys=ON");
        }
        DatabaseManager.applyMigrations(conn);
        return conn;
    }

    public static ConnectionProvider testProvider(Connection conn) {
        Connection wrapper = nonClosingWrapper(conn);
        return () -> wrapper;
    }

    private static Connection nonClosingWrapper(Connection real) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("close".equals(method.getName())) return null;
            if ("isClosed".equals(method.getName())) return false;
            return method.invoke(real, args);
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                handler);
    }

    public static int insertMinimalPatent(Connection conn, String fileNumber, String title) throws SQLException {
        String sql = "INSERT INTO patent (file_number, title, pto_status, suffix) VALUES (?, ?, 'Filed', 'US')";
        try (var ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fileNumber);
            ps.setString(2, title);
            ps.executeUpdate();
            var keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to insert minimal patent");
    }
}
