package com.patenttracker.dao;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class PriorArtTestDbHelper {

    private PriorArtTestDbHelper() {}

    public static Connection createTestConnection() throws SQLException, IOException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys=ON");
        }
        PriorArtDatabaseManager.applyMigrations(conn);
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
}
