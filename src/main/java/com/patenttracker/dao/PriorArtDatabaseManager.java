package com.patenttracker.dao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public class PriorArtDatabaseManager {

    private static final PriorArtDatabaseManager INSTANCE = new PriorArtDatabaseManager();
    private static final String DEFAULT_DB_DIR = System.getProperty("user.home") + "/.patenttracker";
    private static final String DEFAULT_DB_NAME = "prior_art.db";

    private String dbPath;

    private PriorArtDatabaseManager() {}

    public static PriorArtDatabaseManager getInstance() {
        return INSTANCE;
    }

    public void initialize() throws SQLException, IOException {
        initialize(null);
    }

    public void initialize(String customDbPath) throws SQLException, IOException {
        if (customDbPath != null && !customDbPath.isBlank()) {
            dbPath = customDbPath;
        } else {
            Path dir = Path.of(DEFAULT_DB_DIR);
            Files.createDirectories(dir);
            dbPath = dir.resolve(DEFAULT_DB_NAME).toString();
        }

        try (Connection conn = createConnection()) {
            applyMigrations(conn);
        }
    }

    public Connection getConnection() throws SQLException {
        return createConnection();
    }

    private Connection createConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
        return conn;
    }

    static void applyMigrations(Connection conn) throws SQLException, IOException {
        int currentVersion = getCurrentSchemaVersion(conn);

        if (currentVersion < 1) {
            executeSqlResource(conn, "/db/prior-art/PA001__initial_schema.sql");
        }
        if (currentVersion < 2) {
            executeSqlResource(conn, "/db/prior-art/PA002__corpus_tables.sql");
        }
    }

    private static int getCurrentSchemaVersion(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'"
            );
            if (!rs.next()) {
                return 0;
            }
            rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version");
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            return 0;
        }
        return 0;
    }

    private static void executeSqlResource(Connection conn, String resourcePath) throws SQLException, IOException {
        InputStream is = PriorArtDatabaseManager.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("SQL resource not found: " + resourcePath);
        }

        String sql;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            sql = reader.lines().collect(Collectors.joining("\n"));
        }

        try (Statement stmt = conn.createStatement()) {
            StringBuilder current = new StringBuilder();
            boolean inTrigger = false;
            for (String s : sql.split(";")) {
                String cleaned = s.lines()
                        .filter(line -> !line.trim().startsWith("--"))
                        .collect(Collectors.joining("\n")).trim();
                if (cleaned.isEmpty()) continue;

                if (inTrigger) {
                    current.append(";").append(s);
                    if (cleaned.toUpperCase().contains("END")) {
                        stmt.execute(current.toString().trim());
                        current.setLength(0);
                        inTrigger = false;
                    }
                } else if (cleaned.toUpperCase().startsWith("CREATE TRIGGER")) {
                    current.append(s);
                    if (cleaned.toUpperCase().contains("END")) {
                        stmt.execute(current.toString().trim());
                        current.setLength(0);
                    } else {
                        inTrigger = true;
                    }
                } else {
                    stmt.execute(cleaned);
                }
            }
        }
    }
}
