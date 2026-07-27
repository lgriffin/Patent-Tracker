package com.patenttracker.integration;

import com.patenttracker.dao.TestDbHelper;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MigrationIntegrationTest {

    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestDbHelper.createTestConnection();
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    @Test
    void allMigrationsApply() throws Exception {
        List<String> tables = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
            );
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }
        }

        assertTrue(tables.contains("patent"), "patent table should exist");
        assertTrue(tables.contains("inventor"), "inventor table should exist");
        assertTrue(tables.contains("patent_inventor"), "patent_inventor table should exist");
        assertTrue(tables.contains("tag"), "tag table should exist");
        assertTrue(tables.contains("patent_tag"), "patent_tag table should exist");
        assertTrue(tables.contains("status_update"), "status_update table should exist");
        assertTrue(tables.contains("schema_version"), "schema_version table should exist");
        assertTrue(tables.contains("patent_text"), "patent_text table should exist");
        assertTrue(tables.contains("patent_analysis"), "patent_analysis table should exist");
        assertTrue(tables.contains("mined_patent"), "mined_patent table should exist");
    }

    @Test
    void schemaVersion() throws Exception {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version");
            assertTrue(rs.next());
            assertEquals(5, rs.getInt(1));
        }
    }
}
