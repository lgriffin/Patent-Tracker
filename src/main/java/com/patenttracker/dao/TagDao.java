package com.patenttracker.dao;

import com.patenttracker.model.Tag;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TagDao {

    private final Connection conn;

    public TagDao() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    public TagDao(Connection conn) {
        this.conn = conn;
    }

    public Tag findOrCreate(String name) throws SQLException {
        Tag existing = findByName(name);
        if (existing != null) return existing;

        Tag tag = new Tag(name.trim());
        insert(tag);
        return tag;
    }

    public int insert(Tag tag) throws SQLException {
        String sql = "INSERT INTO tag (name) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tag.getName());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                tag.setId(id);
                return id;
            }
        }
        return -1;
    }

    public Tag findByName(String name) throws SQLException {
        String sql = "SELECT * FROM tag WHERE name = ? COLLATE NOCASE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    public List<Tag> findByPatentId(int patentId) throws SQLException {
        String sql = "SELECT t.*, pt.source as tag_source FROM tag t JOIN patent_tag pt ON t.id = pt.tag_id WHERE pt.patent_id = ? ORDER BY t.name";
        List<Tag> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Tag tag = mapRow(rs);
                tag.setSource(rs.getString("tag_source"));
                results.add(tag);
            }
        }
        return results;
    }

    public List<Tag> findAllWithCounts() throws SQLException {
        String sql = "SELECT t.*, COUNT(pt.id) as patent_count FROM tag t " +
                     "LEFT JOIN patent_tag pt ON t.id = pt.tag_id " +
                     "GROUP BY t.id ORDER BY t.name";
        List<Tag> results = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Tag tag = mapRow(rs);
                tag.setPatentCount(rs.getInt("patent_count"));
                results.add(tag);
            }
        }
        return results;
    }

    public void addToPatent(int patentId, int tagId) throws SQLException {
        addToPatent(patentId, tagId, "HUMAN");
    }

    public void addToPatent(int patentId, int tagId, String source) throws SQLException {
        String sql = "INSERT OR IGNORE INTO patent_tag (patent_id, tag_id, source) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ps.setInt(2, tagId);
            ps.setString(3, source);
            ps.executeUpdate();
        }
    }

    public void removeFromPatent(int patentId, int tagId) throws SQLException {
        String sql = "DELETE FROM patent_tag WHERE patent_id = ? AND tag_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ps.setInt(2, tagId);
            ps.executeUpdate();
        }
    }

    public void bulkAddToPatents(List<Integer> patentIds, int tagId, String source) throws SQLException {
        String sql = "INSERT OR IGNORE INTO patent_tag (patent_id, tag_id, source) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int patentId : patentIds) {
                ps.setInt(1, patentId);
                ps.setInt(2, tagId);
                ps.setString(3, source);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void rename(int tagId, String newName) throws SQLException {
        String sql = "UPDATE tag SET name = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName.trim());
            ps.setInt(2, tagId);
            ps.executeUpdate();
        }
    }

    public void delete(int tagId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM patent_tag WHERE tag_id = ?")) {
            ps.setInt(1, tagId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM tag WHERE id = ?")) {
            ps.setInt(1, tagId);
            ps.executeUpdate();
        }
    }

    private Tag mapRow(ResultSet rs) throws SQLException {
        Tag tag = new Tag();
        tag.setId(rs.getInt("id"));
        tag.setName(rs.getString("name"));
        return tag;
    }
}
