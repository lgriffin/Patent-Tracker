package com.patenttracker.dao;

import com.patenttracker.model.SearchSession;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class SearchSessionDao {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConnectionProvider connectionProvider;

    public SearchSessionDao() {
        this.connectionProvider = () -> PriorArtDatabaseManager.getInstance().getConnection();
    }

    public SearchSessionDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public int insert(SearchSession session) throws SQLException {
        String sql = """
            INSERT INTO search_session (idea_text, decomposed_json, status)
            VALUES (?, ?, ?)
            """;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, session.getIdeaText());
            ps.setString(2, session.getDecomposedJson());
            ps.setString(3, session.getStatus());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        }
        return -1;
    }

    public void updateStatus(int sessionId, String status) throws SQLException {
        String sql = "UPDATE search_session SET status = ?, updated_at = datetime('now') WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        }
    }

    public void updateDecomposedJson(int sessionId, String decomposedJson) throws SQLException {
        String sql = "UPDATE search_session SET decomposed_json = ?, updated_at = datetime('now') WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, decomposedJson);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        }
    }

    public SearchSession findById(int id) throws SQLException {
        String sql = "SELECT * FROM search_session WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        }
        return null;
    }

    public List<SearchSession> findAll() throws SQLException {
        String sql = "SELECT * FROM search_session ORDER BY created_at DESC";
        List<SearchSession> results = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    public void delete(int sessionId) throws SQLException {
        String sql = "DELETE FROM search_session WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.executeUpdate();
        }
    }

    private SearchSession mapRow(ResultSet rs) throws SQLException {
        return SearchSession.builder()
                .id(rs.getInt("id"))
                .ideaText(rs.getString("idea_text"))
                .decomposedJson(rs.getString("decomposed_json"))
                .status(rs.getString("status"))
                .createdAt(parseDateTime(rs.getString("created_at")))
                .updatedAt(parseDateTime(rs.getString("updated_at")))
                .build();
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s, DT_FMT);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(s);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
