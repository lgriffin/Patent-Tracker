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

    private final ConnectionProvider connectionProvider;

    public StatusUpdateDao() {
        this.connectionProvider = () -> DatabaseManager.getInstance().getConnection();
    }

    public StatusUpdateDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void insert(StatusUpdate su) throws SQLException {
        String sql = "INSERT INTO status_update (patent_id, field_name, previous_value, new_value, source) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, su.getPatentId());
            ps.setString(2, su.getFieldName());
            ps.setString(3, su.getPreviousValue());
            ps.setString(4, su.getNewValue());
            ps.setString(5, su.getSource());
            ps.executeUpdate();
        }
    }

    public List<StatusUpdate> findByPatentId(int patentId) throws SQLException {
        String sql = "SELECT * FROM status_update WHERE patent_id = ? ORDER BY timestamp DESC";
        List<StatusUpdate> results = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private StatusUpdate mapRow(ResultSet rs) throws SQLException {
        return StatusUpdate.builder()
                .id(rs.getInt("id"))
                .patentId(rs.getInt("patent_id"))
                .fieldName(rs.getString("field_name"))
                .previousValue(rs.getString("previous_value"))
                .newValue(rs.getString("new_value"))
                .source(rs.getString("source"))
                .timestamp(parseDateTime(rs.getString("timestamp")))
                .build();
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
