package com.patenttracker.dao;

import com.patenttracker.model.StatusUpdate;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class StatusUpdateDao {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Connection conn;

    public StatusUpdateDao() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    public StatusUpdateDao(Connection conn) {
        this.conn = conn;
    }

    public void insert(StatusUpdate su) throws SQLException {
        String sql = "INSERT INTO status_update (patent_id, field_name, previous_value, new_value, source) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, su.getPatentId());
            ps.setString(2, su.getFieldName());
            ps.setString(3, su.getPreviousValue());
            ps.setString(4, su.getNewValue());
            ps.setString(5, su.getSource());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                su.setId(keys.getInt(1));
            }
        }
    }

    public List<StatusUpdate> findByPatentId(int patentId) throws SQLException {
        String sql = "SELECT * FROM status_update WHERE patent_id = ? ORDER BY timestamp DESC";
        List<StatusUpdate> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private StatusUpdate mapRow(ResultSet rs) throws SQLException {
        StatusUpdate su = new StatusUpdate();
        su.setId(rs.getInt("id"));
        su.setPatentId(rs.getInt("patent_id"));
        su.setFieldName(rs.getString("field_name"));
        su.setPreviousValue(rs.getString("previous_value"));
        su.setNewValue(rs.getString("new_value"));
        su.setSource(rs.getString("source"));
        su.setTimestamp(parseDateTime(rs.getString("timestamp")));
        return su;
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
