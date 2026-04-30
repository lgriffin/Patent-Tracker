package com.patenttracker.dao;

import com.patenttracker.model.MinedPatent;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class MinedPatentDao {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Connection conn;

    public MinedPatentDao() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    public void insertOrIgnore(MinedPatent mp) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO mined_patent (patent_number, title, abstract_text, grant_date, search_area, search_query)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mp.getPatentNumber());
            ps.setString(2, mp.getTitle());
            ps.setString(3, mp.getAbstractText());
            ps.setString(4, mp.getGrantDate() != null ? mp.getGrantDate().toString() : null);
            ps.setString(5, mp.getSearchArea());
            ps.setString(6, mp.getSearchQuery());
            ps.executeUpdate();
        }
    }

    public List<MinedPatent> findBySearchArea(String searchArea) throws SQLException {
        String sql = "SELECT * FROM mined_patent WHERE search_area = ? ORDER BY grant_date DESC";
        List<MinedPatent> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, searchArea);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    public void deleteBySearchArea(String searchArea) throws SQLException {
        String sql = "DELETE FROM mined_patent WHERE search_area = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, searchArea);
            ps.executeUpdate();
        }
    }

    public int countBySearchArea(String searchArea) throws SQLException {
        String sql = "SELECT COUNT(*) FROM mined_patent WHERE search_area = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, searchArea);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    private MinedPatent mapRow(ResultSet rs) throws SQLException {
        MinedPatent mp = new MinedPatent();
        mp.setId(rs.getInt("id"));
        mp.setPatentNumber(rs.getString("patent_number"));
        mp.setTitle(rs.getString("title"));
        mp.setAbstractText(rs.getString("abstract_text"));
        mp.setGrantDate(parseDate(rs.getString("grant_date")));
        mp.setSearchArea(rs.getString("search_area"));
        mp.setSearchQuery(rs.getString("search_query"));
        mp.setFetchedAt(parseDateTime(rs.getString("fetched_at")));
        return mp;
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
            return LocalDateTime.now();
        }
    }
}
