package com.patenttracker.dao;

import com.patenttracker.model.CorpusDomain;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CorpusDomainDao {

    private final ConnectionProvider connectionProvider;

    public CorpusDomainDao() {
        this.connectionProvider = () -> PriorArtDatabaseManager.getInstance().getConnection();
    }

    public CorpusDomainDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public List<CorpusDomain> findAll() throws SQLException {
        String sql = "SELECT * FROM corpus_domain ORDER BY display_name";
        List<CorpusDomain> results = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    public CorpusDomain findByName(String name) throws SQLException {
        String sql = "SELECT * FROM corpus_domain WHERE name = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    private CorpusDomain mapRow(ResultSet rs) throws SQLException {
        return CorpusDomain.builder()
                .id(rs.getInt("id"))
                .name(rs.getString("name"))
                .displayName(rs.getString("display_name"))
                .cpcCodes(rs.getString("cpc_codes"))
                .keywords(rs.getString("keywords"))
                .description(rs.getString("description"))
                .build();
    }
}
