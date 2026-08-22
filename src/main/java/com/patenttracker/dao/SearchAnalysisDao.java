package com.patenttracker.dao;

import com.patenttracker.model.SearchAnalysis;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class SearchAnalysisDao {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConnectionProvider connectionProvider;

    public SearchAnalysisDao() {
        this.connectionProvider = () -> PriorArtDatabaseManager.getInstance().getConnection();
    }

    public SearchAnalysisDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void insertOrUpdate(SearchAnalysis analysis) throws SQLException {
        String sql = """
            INSERT INTO search_analysis (session_id, phase, result_json, model_used, duration_ms, cost_usd)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(session_id, phase) DO UPDATE SET
                result_json = excluded.result_json,
                model_used = excluded.model_used,
                duration_ms = excluded.duration_ms,
                cost_usd = excluded.cost_usd,
                analyzed_at = datetime('now')
            """;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, analysis.getSessionId());
            ps.setString(2, analysis.getPhase());
            ps.setString(3, analysis.getResultJson());
            ps.setString(4, analysis.getModelUsed());
            ps.setLong(5, analysis.getDurationMs());
            ps.setDouble(6, analysis.getCostUsd());
            ps.executeUpdate();
        }
    }

    public SearchAnalysis findBySessionAndPhase(int sessionId, String phase) throws SQLException {
        String sql = "SELECT * FROM search_analysis WHERE session_id = ? AND phase = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.setString(2, phase);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        }
        return null;
    }

    public List<SearchAnalysis> findBySessionId(int sessionId) throws SQLException {
        String sql = "SELECT * FROM search_analysis WHERE session_id = ? ORDER BY analyzed_at";
        List<SearchAnalysis> results = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private SearchAnalysis mapRow(ResultSet rs) throws SQLException {
        return SearchAnalysis.builder()
                .id(rs.getInt("id"))
                .sessionId(rs.getInt("session_id"))
                .phase(rs.getString("phase"))
                .resultJson(rs.getString("result_json"))
                .modelUsed(rs.getString("model_used"))
                .durationMs(rs.getLong("duration_ms"))
                .costUsd(rs.getDouble("cost_usd"))
                .analyzedAt(parseDateTime(rs.getString("analyzed_at")))
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
