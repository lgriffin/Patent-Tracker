package com.patenttracker.dao;

import com.patenttracker.model.PatentText;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class PatentTextDao {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Connection conn;

    public PatentTextDao() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    public PatentTextDao(Connection conn) {
        this.conn = conn;
    }

    public int insert(PatentText pt) throws SQLException {
        String sql = "INSERT OR IGNORE INTO patent_text (patent_id, full_text, page_count) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, pt.getPatentId());
            ps.setString(2, pt.getFullText());
            if (pt.getPageCount() != null) {
                ps.setInt(3, pt.getPageCount());
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                pt.setId(id);
                return id;
            }
        }
        return -1;
    }

    public PatentText findByPatentId(int patentId) throws SQLException {
        String sql = "SELECT * FROM patent_text WHERE patent_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        }
        return null;
    }

    public boolean existsForPatent(int patentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM patent_text WHERE patent_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    public void deleteByPatentId(int patentId) throws SQLException {
        String sql = "DELETE FROM patent_text WHERE patent_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ps.executeUpdate();
        }
    }

    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM patent_text";
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public List<PatentText> findAll() throws SQLException {
        String sql = "SELECT * FROM patent_text";
        List<PatentText> results = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private PatentText mapRow(ResultSet rs) throws SQLException {
        PatentText pt = new PatentText();
        pt.setId(rs.getInt("id"));
        pt.setPatentId(rs.getInt("patent_id"));
        pt.setFullText(rs.getString("full_text"));
        int pageCount = rs.getInt("page_count");
        pt.setPageCount(rs.wasNull() ? null : pageCount);
        pt.setExtractedAt(parseDateTime(rs.getString("extracted_at")));
        return pt;
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
