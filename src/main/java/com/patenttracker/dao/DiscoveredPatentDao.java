package com.patenttracker.dao;

import com.patenttracker.model.DiscoveredPatent;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class DiscoveredPatentDao {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConnectionProvider connectionProvider;

    public DiscoveredPatentDao() {
        this.connectionProvider = () -> PriorArtDatabaseManager.getInstance().getConnection();
    }

    public DiscoveredPatentDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void insert(DiscoveredPatent dp) throws SQLException {
        String sql = """
            INSERT INTO discovered_patent
                (session_id, patent_number, title, abstract_text, assignee,
                 filing_date, grant_date, cpc_codes, source, relevance_score, matched_concepts_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dp.getSessionId());
            ps.setString(2, dp.getPatentNumber());
            ps.setString(3, dp.getTitle());
            ps.setString(4, dp.getAbstractText());
            ps.setString(5, dp.getAssignee());
            ps.setString(6, dp.getFilingDate() != null ? dp.getFilingDate().toString() : null);
            ps.setString(7, dp.getGrantDate() != null ? dp.getGrantDate().toString() : null);
            ps.setString(8, dp.getCpcCodes());
            ps.setString(9, dp.getSource());
            ps.setDouble(10, dp.getRelevanceScore());
            ps.setString(11, dp.getMatchedConceptsJson());
            ps.executeUpdate();
        }
    }

    public void insertBatch(List<DiscoveredPatent> patents) throws SQLException {
        String sql = """
            INSERT INTO discovered_patent
                (session_id, patent_number, title, abstract_text, assignee,
                 filing_date, grant_date, cpc_codes, source, relevance_score, matched_concepts_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (DiscoveredPatent dp : patents) {
                ps.setInt(1, dp.getSessionId());
                ps.setString(2, dp.getPatentNumber());
                ps.setString(3, dp.getTitle());
                ps.setString(4, dp.getAbstractText());
                ps.setString(5, dp.getAssignee());
                ps.setString(6, dp.getFilingDate() != null ? dp.getFilingDate().toString() : null);
                ps.setString(7, dp.getGrantDate() != null ? dp.getGrantDate().toString() : null);
                ps.setString(8, dp.getCpcCodes());
                ps.setString(9, dp.getSource());
                ps.setDouble(10, dp.getRelevanceScore());
                ps.setString(11, dp.getMatchedConceptsJson());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    public List<DiscoveredPatent> findBySessionId(int sessionId) throws SQLException {
        String sql = "SELECT * FROM discovered_patent WHERE session_id = ? ORDER BY relevance_score DESC";
        List<DiscoveredPatent> results = new ArrayList<>();
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

    public int countBySessionId(int sessionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM discovered_patent WHERE session_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public void deleteBySessionId(int sessionId) throws SQLException {
        String sql = "DELETE FROM discovered_patent WHERE session_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.executeUpdate();
        }
    }

    private DiscoveredPatent mapRow(ResultSet rs) throws SQLException {
        return DiscoveredPatent.builder()
                .id(rs.getInt("id"))
                .sessionId(rs.getInt("session_id"))
                .patentNumber(rs.getString("patent_number"))
                .title(rs.getString("title"))
                .abstractText(rs.getString("abstract_text"))
                .assignee(rs.getString("assignee"))
                .filingDate(parseDate(rs.getString("filing_date")))
                .grantDate(parseDate(rs.getString("grant_date")))
                .cpcCodes(rs.getString("cpc_codes"))
                .source(rs.getString("source"))
                .relevanceScore(rs.getDouble("relevance_score"))
                .matchedConceptsJson(rs.getString("matched_concepts_json"))
                .fetchedAt(parseDateTime(rs.getString("fetched_at")))
                .build();
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
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
