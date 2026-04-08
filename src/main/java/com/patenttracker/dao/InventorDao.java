package com.patenttracker.dao;

import com.patenttracker.model.Inventor;
import com.patenttracker.model.PatentInventor;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InventorDao {

    private final Connection conn;

    public InventorDao() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    public InventorDao(Connection conn) {
        this.conn = conn;
    }

    public Inventor findOrCreate(String fullName, String username) throws SQLException {
        // Try to find by username first (most reliable dedup)
        if (username != null && !username.isBlank()) {
            Inventor existing = findByUsername(username);
            if (existing != null) {
                // Update fullName if we now have a better one
                if (fullName != null && !fullName.isBlank()
                    && (existing.getFullName() == null || existing.getFullName().equals(existing.getUsername()))) {
                    existing.setFullName(fullName);
                    updateFullName(existing.getId(), fullName);
                }
                return existing;
            }
        }

        // Try by full name
        if (fullName != null && !fullName.isBlank()) {
            Inventor existing = findByFullName(fullName);
            if (existing != null) {
                // Update username if we now have one
                if (username != null && !username.isBlank() && existing.getUsername() == null) {
                    existing.setUsername(username);
                    updateUsername(existing.getId(), username);
                }
                return existing;
            }
        }

        // Create new
        Inventor inv = new Inventor(fullName, username);
        insert(inv);
        return inv;
    }

    public int insert(Inventor inv) throws SQLException {
        String sql = "INSERT INTO inventor (full_name, username) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, inv.getFullName());
            ps.setString(2, inv.getUsername());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                inv.setId(id);
                return id;
            }
        }
        return -1;
    }

    public void addPatentInventor(PatentInventor pi) throws SQLException {
        String sql = "INSERT OR IGNORE INTO patent_inventor (patent_id, inventor_id, role, role_position) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pi.getPatentId());
            ps.setInt(2, pi.getInventorId());
            ps.setString(3, pi.getRole());
            ps.setInt(4, pi.getRolePosition());
            ps.executeUpdate();
        }
    }

    public Inventor findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM inventor WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    public Inventor findByFullName(String fullName) throws SQLException {
        String sql = "SELECT * FROM inventor WHERE full_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    public List<Inventor> findAll() throws SQLException {
        String sql = "SELECT inv.*, COUNT(pi.id) as patent_count FROM inventor inv " +
                     "LEFT JOIN patent_inventor pi ON inv.id = pi.inventor_id " +
                     "GROUP BY inv.id ORDER BY patent_count DESC";
        List<Inventor> results = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Inventor inv = mapRow(rs);
                inv.setPatentCount(rs.getInt("patent_count"));
                results.add(inv);
            }
        }
        return results;
    }

    public List<Inventor> findByPatentId(int patentId) throws SQLException {
        String sql = "SELECT inv.*, pi.role, pi.role_position FROM inventor inv " +
                     "JOIN patent_inventor pi ON inv.id = pi.inventor_id " +
                     "WHERE pi.patent_id = ? ORDER BY pi.role_position";
        List<Inventor> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, patentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    /**
     * Returns co-inventor pairs with shared patent counts.
     * Each row: inventor1_id, inventor1_name, inventor2_id, inventor2_name, shared_count
     */
    public List<CoInventorEdge> getCoInventorEdges() throws SQLException {
        String sql = """
            SELECT i1.id as inv1_id, i1.full_name as inv1_name,
                   i2.id as inv2_id, i2.full_name as inv2_name,
                   COUNT(DISTINCT pi1.patent_id) as shared_count
            FROM patent_inventor pi1
            JOIN patent_inventor pi2 ON pi1.patent_id = pi2.patent_id AND pi1.inventor_id < pi2.inventor_id
            JOIN inventor i1 ON pi1.inventor_id = i1.id
            JOIN inventor i2 ON pi2.inventor_id = i2.id
            GROUP BY i1.id, i2.id
            ORDER BY shared_count DESC
            """;
        List<CoInventorEdge> edges = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                edges.add(new CoInventorEdge(
                    rs.getInt("inv1_id"), rs.getString("inv1_name"),
                    rs.getInt("inv2_id"), rs.getString("inv2_name"),
                    rs.getInt("shared_count")
                ));
            }
        }
        return edges;
    }

    /**
     * Returns co-inventor edges filtered by a set of patent IDs.
     */
    public List<CoInventorEdge> getCoInventorEdges(List<Integer> patentIds) throws SQLException {
        if (patentIds == null || patentIds.isEmpty()) {
            return getCoInventorEdges();
        }

        String placeholders = String.join(",", patentIds.stream().map(id -> "?").toList());
        String sql = String.format("""
            SELECT i1.id as inv1_id, i1.full_name as inv1_name,
                   i2.id as inv2_id, i2.full_name as inv2_name,
                   COUNT(DISTINCT pi1.patent_id) as shared_count
            FROM patent_inventor pi1
            JOIN patent_inventor pi2 ON pi1.patent_id = pi2.patent_id AND pi1.inventor_id < pi2.inventor_id
            JOIN inventor i1 ON pi1.inventor_id = i1.id
            JOIN inventor i2 ON pi2.inventor_id = i2.id
            WHERE pi1.patent_id IN (%s)
            GROUP BY i1.id, i2.id
            ORDER BY shared_count DESC
            """, placeholders);

        List<CoInventorEdge> edges = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < patentIds.size(); i++) {
                ps.setInt(i + 1, patentIds.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                edges.add(new CoInventorEdge(
                    rs.getInt("inv1_id"), rs.getString("inv1_name"),
                    rs.getInt("inv2_id"), rs.getString("inv2_name"),
                    rs.getInt("shared_count")
                ));
            }
        }
        return edges;
    }

    private void updateFullName(int id, String fullName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE inventor SET full_name = ? WHERE id = ?")) {
            ps.setString(1, fullName);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private void updateUsername(int id, String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE inventor SET username = ? WHERE id = ?")) {
            ps.setString(1, username);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private Inventor mapRow(ResultSet rs) throws SQLException {
        Inventor inv = new Inventor();
        inv.setId(rs.getInt("id"));
        inv.setFullName(rs.getString("full_name"));
        inv.setUsername(rs.getString("username"));
        return inv;
    }

    public record CoInventorEdge(int inventor1Id, String inventor1Name,
                                  int inventor2Id, String inventor2Name,
                                  int sharedCount) {}
}
