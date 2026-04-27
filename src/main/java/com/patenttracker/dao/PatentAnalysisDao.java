package com.patenttracker.dao;

import com.patenttracker.model.PatentAnalysis;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PatentAnalysisDao {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Connection conn;

    public PatentAnalysisDao() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    public PatentAnalysisDao(Connection conn) {
        this.conn = conn;
    }

    public int insertOrUpdate(PatentAnalysis pa) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO patent_analysis (patent_id, analysis_type, result_json, model_used)
            VALUES (?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, pa.getPatentId());
            ps.setString(2, pa.getAnalysisType());
            ps.setString(3, pa.getResultJson());
            ps.setString(4, pa.getModelUsed());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                pa.setId(id);
                return id;
            }
        }
        return -1;
    }

    public List<PatentAnalysis> findByPatentId(int patentId) throws SQLException {
        String sql = "SELECT * FROM patent_analysis WHERE patent_id = ? ORDER BY analysis_type";
        List<PatentAnalysis> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    public PatentAnalysis findByPatentIdAndType(int patentId, String analysisType) throws SQLException {
        String sql = "SELECT * FROM patent_analysis WHERE patent_id = ? AND analysis_type = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ps.setString(2, analysisType);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        }
        return null;
    }

    public void deleteByPatentId(int patentId) throws SQLException {
        String sql = "DELETE FROM patent_analysis WHERE patent_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ps.executeUpdate();
        }
    }

    public void deleteByPatentIdAndType(int patentId, String analysisType) throws SQLException {
        String sql = "DELETE FROM patent_analysis WHERE patent_id = ? AND analysis_type = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ps.setString(2, analysisType);
            ps.executeUpdate();
        }
    }

    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM patent_analysis";
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public int countDistinctPatents() throws SQLException {
        String sql = "SELECT COUNT(DISTINCT patent_id) FROM patent_analysis";
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public Map<String, Integer> countByType() throws SQLException {
        String sql = "SELECT analysis_type, COUNT(*) as cnt FROM patent_analysis GROUP BY analysis_type ORDER BY analysis_type";
        Map<String, Integer> results = new LinkedHashMap<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                results.put(rs.getString("analysis_type"), rs.getInt("cnt"));
            }
        }
        return results;
    }

    private PatentAnalysis mapRow(ResultSet rs) throws SQLException {
        PatentAnalysis pa = new PatentAnalysis();
        pa.setId(rs.getInt("id"));
        pa.setPatentId(rs.getInt("patent_id"));
        pa.setAnalysisType(rs.getString("analysis_type"));
        pa.setResultJson(rs.getString("result_json"));
        pa.setModelUsed(rs.getString("model_used"));
        pa.setAnalyzedAt(parseDateTime(rs.getString("analyzed_at")));
        return pa;
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s, DT_FMT);
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }
}
