package com.patenttracker.dao;

import com.patenttracker.model.Inventor;
import com.patenttracker.model.PatentInventor;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InventorDao {

    private final ConnectionProvider connectionProvider;

    public InventorDao() {
        this.connectionProvider = () -> DatabaseManager.getInstance().getConnection();
    }

    public InventorDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public Inventor findOrCreate(String fullName, String username) throws SQLException {
        if (username != null && !username.isBlank()) {
            Inventor existing = findByUsername(username);
            if (existing != null) {
                if (fullName != null && !fullName.isBlank()
                    && (existing.getFullName() == null || existing.getFullName().equals(existing.getUsername()))) {
                    updateFullName(existing.getId(), fullName);
                    return Inventor.builder(existing).fullName(fullName).build();
                }
                return existing;
            }
        }

        if (fullName != null && !fullName.isBlank()) {
            Inventor existing = findByFullName(fullName);
            if (existing != null) {
                if (username != null && !username.isBlank() && existing.getUsername() == null) {
                    updateUsername(existing.getId(), username);
                    return Inventor.builder(existing).username(username).build();
                }
                return existing;
            }
        }

        Inventor inv = Inventor.builder().fullName(fullName).username(username).build();
        int id = insert(inv);
        return Inventor.builder(inv).id(id).build();
    }

    public int insert(Inventor inv) throws SQLException {
        String sql = "INSERT INTO inventor (full_name, username) VALUES (?, ?)";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, inv.getFullName());
            ps.setString(2, inv.getUsername());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        }
        return -1;
    }

    public void addPatentInventor(PatentInventor pi) throws SQLException {
        String sql = "INSERT OR IGNORE INTO patent_inventor (patent_id, inventor_id, role, role_position) VALUES (?, ?, ?, ?)";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pi.patentId());
            ps.setInt(2, pi.inventorId());
            ps.setString(3, pi.role());
            ps.setInt(4, pi.rolePosition());
            ps.executeUpdate();
        }
    }

    public Inventor findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM inventor WHERE username = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    public Inventor findByFullName(String fullName) throws SQLException {
        String sql = "SELECT * FROM inventor WHERE full_name = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Inventor inv = mapRow(rs);
                results.add(Inventor.builder(inv).patentCount(rs.getInt("patent_count")).build());
            }
        }
        return results;
    }

    public List<Inventor> findByPatentId(int patentId) throws SQLException {
        String sql = "SELECT inv.*, pi.role, pi.role_position FROM inventor inv " +
                     "JOIN patent_inventor pi ON inv.id = pi.inventor_id " +
                     "WHERE pi.patent_id = ? ORDER BY pi.role_position";
        List<Inventor> results = new ArrayList<>();
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
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
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
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE inventor SET full_name = ? WHERE id = ?")) {
            ps.setString(1, fullName);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private void updateUsername(int id, String username) throws SQLException {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE inventor SET username = ? WHERE id = ?")) {
            ps.setString(1, username);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private Inventor mapRow(ResultSet rs) throws SQLException {
        return Inventor.builder()
                .id(rs.getInt("id"))
                .fullName(rs.getString("full_name"))
                .username(rs.getString("username"))
                .build();
    }

    public record CoInventorEdge(int inventor1Id, String inventor1Name,
                                  int inventor2Id, String inventor2Name,
                                  int sharedCount) {}
}
