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

public class DatabaseManager {

    private static final DatabaseManager INSTANCE = new DatabaseManager();
    private static final String DEFAULT_DB_DIR = System.getProperty("user.home") + "/.patenttracker";
    private static final String DEFAULT_DB_NAME = "patents.db";

    private Connection connection;
    private String dbPath;

    private DatabaseManager() {}

    public static DatabaseManager getInstance() {
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

        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }

        applyMigrations();
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                // Ignore on close
            }
        }
    }

    private void applyMigrations() throws SQLException, IOException {
        int currentVersion = getCurrentSchemaVersion();

        // Apply V001 if not yet applied
        if (currentVersion < 1) {
            executeSqlResource("/db/V001__initial_schema.sql");
        }
        if (currentVersion < 2) {
            executeSqlResource("/db/V002__add_pdf_path.sql");
        }
        if (currentVersion < 3) {
            executeSqlResource("/db/V003__tag_source.sql");
        }
        if (currentVersion < 4) {
            executeSqlResource("/db/V004__patent_text_and_analysis.sql");
        }
        if (currentVersion < 5) {
            executeSqlResource("/db/V005__mined_patents.sql");
        }
    }

    private int getCurrentSchemaVersion() {
        try (Statement stmt = connection.createStatement()) {
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

    private void executeSqlResource(String resourcePath) throws SQLException, IOException {
        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("SQL resource not found: " + resourcePath);
        }

        String sql;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            sql = reader.lines().collect(Collectors.joining("\n"));
        }

        // Split on semicolons, but preserve trigger bodies (BEGIN...END blocks)
        try (Statement stmt = connection.createStatement()) {
            StringBuilder current = new StringBuilder();
            boolean inTrigger = false;
            for (String s : sql.split(";")) {
                // Strip comment-only lines to get the actual SQL
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
