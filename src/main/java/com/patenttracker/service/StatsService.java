package com.patenttracker.service;

import com.patenttracker.dao.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StatsService {

    private final Connection conn;

    public StatsService() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    public int getTotalCount() throws SQLException {
        return queryInt("SELECT COUNT(*) FROM patent");
    }

    public int getContinuanceCount() throws SQLException {
        return queryInt("SELECT COUNT(*) FROM patent WHERE suffix LIKE '%CON%'");
    }

    public Map<String, Integer> getCountByStatus() throws SQLException {
        return queryMap("SELECT pto_status, COUNT(*) as cnt FROM patent GROUP BY pto_status ORDER BY cnt DESC");
    }

    public Map<String, Integer> getCountByClassification() throws SQLException {
        return queryMap(
            "SELECT t.name, COUNT(DISTINCT pt.patent_id) as cnt FROM tag t " +
            "JOIN patent_tag pt ON t.id = pt.tag_id " +
            "GROUP BY t.id ORDER BY cnt DESC"
        );
    }

    public Map<String, Integer> getCountByFilingYear() throws SQLException {
        return queryMap(
            "SELECT substr(filing_date,1,4) as year, COUNT(*) as cnt FROM patent " +
            "WHERE filing_date IS NOT NULL GROUP BY year ORDER BY year"
        );
    }

    public Map<String, Map<String, Integer>> getYearlyBreakdown() throws SQLException {
        // Returns year -> { "Filed" -> count, "Issued" -> count, "Published" -> count }
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();

        // Collect all years from all date columns
        String yearsSql = """
            SELECT DISTINCT year FROM (
                SELECT substr(filing_date,1,4) as year FROM patent WHERE filing_date IS NOT NULL
                UNION
                SELECT substr(issue_grant_date,1,4) as year FROM patent WHERE issue_grant_date IS NOT NULL
                UNION
                SELECT substr(publication_date,1,4) as year FROM patent WHERE publication_date IS NOT NULL
            ) ORDER BY year
            """;
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(yearsSql);
            while (rs.next()) {
                String year = rs.getString(1);
                if (year != null && !year.isBlank()) {
                    result.put(year, new LinkedHashMap<>());
                }
            }
        }

        // Filed per year (by filing_date)
        addBreakdownCounts(result, "Filed",
            "SELECT substr(filing_date,1,4) as year, COUNT(*) as cnt FROM patent " +
            "WHERE filing_date IS NOT NULL GROUP BY year ORDER BY year");

        // Issued per year (by issue_grant_date)
        addBreakdownCounts(result, "Issued",
            "SELECT substr(issue_grant_date,1,4) as year, COUNT(*) as cnt FROM patent " +
            "WHERE issue_grant_date IS NOT NULL GROUP BY year ORDER BY year");

        // Published per year (by publication_date)
        addBreakdownCounts(result, "Published",
            "SELECT substr(publication_date,1,4) as year, COUNT(*) as cnt FROM patent " +
            "WHERE publication_date IS NOT NULL GROUP BY year ORDER BY year");

        return result;
    }

    private void addBreakdownCounts(Map<String, Map<String, Integer>> result, String category, String sql)
            throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String year = rs.getString(1);
                int count = rs.getInt(2);
                if (year != null && result.containsKey(year)) {
                    result.get(year).put(category, count);
                }
            }
        }
    }

    public Map<String, Integer> getTopInventors(int limit) throws SQLException {
        return queryMap(
            "SELECT i.full_name, COUNT(pi.id) as cnt FROM inventor i " +
            "JOIN patent_inventor pi ON i.id = pi.inventor_id " +
            "GROUP BY i.id ORDER BY cnt DESC LIMIT " + limit
        );
    }

    public record CollaboratorInfo(String name, int patents, int published, int allowed, int pending,
                                    String topClassifications) {}

    /**
     * Returns top collaborators excluding the owner, with patent count, status breakdown,
     * and top classifications.
     */
    public List<CollaboratorInfo> getTopCollaboratorsWithClassifications(String ownerName, int limit)
            throws SQLException {
        List<CollaboratorInfo> results = new ArrayList<>();

        String sql = "SELECT i.id, i.full_name, COUNT(DISTINCT pi.patent_id) as cnt FROM inventor i " +
                     "JOIN patent_inventor pi ON i.id = pi.inventor_id " +
                     "WHERE i.full_name != ? AND i.username != ? " +
                     "GROUP BY i.id ORDER BY cnt DESC LIMIT ?";

        String statusSql = "SELECT p.pto_status, COUNT(DISTINCT p.id) as cnt FROM patent p " +
                           "JOIN patent_inventor pi ON p.id = pi.patent_id " +
                           "WHERE pi.inventor_id = ? GROUP BY p.pto_status";

        String tagSql = "SELECT t.name, COUNT(DISTINCT pt.patent_id) as cnt FROM tag t " +
                        "JOIN patent_tag pt ON t.id = pt.tag_id " +
                        "JOIN patent_inventor pi ON pi.patent_id = pt.patent_id " +
                        "WHERE pi.inventor_id = ? " +
                        "GROUP BY t.id ORDER BY cnt DESC LIMIT 3";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             PreparedStatement statusPs = conn.prepareStatement(statusSql);
             PreparedStatement tagPs = conn.prepareStatement(tagSql)) {
            ps.setString(1, ownerName);
            ps.setString(2, ownerName);
            ps.setInt(3, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int inventorId = rs.getInt("id");
                String name = rs.getString("full_name");
                int patents = rs.getInt("cnt");

                statusPs.setInt(1, inventorId);
                ResultSet statusRs = statusPs.executeQuery();
                int published = 0, allowed = 0, pending = 0;
                while (statusRs.next()) {
                    String status = statusRs.getString("pto_status");
                    int count = statusRs.getInt("cnt");
                    if (status == null) continue;
                    String lower = status.toLowerCase();
                    if (lower.contains("patented") || lower.contains("issued")
                            || lower.contains("issue fee")
                            || lower.contains("allowance") || lower.contains("allowed")) {
                        allowed += count;
                    } else if (lower.contains("published") || lower.contains("publication")) {
                        published += count;
                    } else if (!lower.contains("abandoned") && !lower.contains("expired")
                            && !lower.contains("withdrawn")) {
                        pending += count;
                    }
                }

                tagPs.setInt(1, inventorId);
                ResultSet tagRs = tagPs.executeQuery();
                List<String> tags = new ArrayList<>();
                while (tagRs.next()) {
                    tags.add(tagRs.getString("name"));
                }
                results.add(new CollaboratorInfo(name, patents, published, allowed, pending,
                        String.join(", ", tags)));
            }
        }
        return results;
    }

    /**
     * Groups raw USPTO statuses into lifecycle phases and returns counts.
     * Phases: Patented, Allowed, Published, In Examination, Filed/Pending, Abandoned/Expired
     */
    public Map<String, Integer> getCountByStatusGrouped() throws SQLException {
        Map<String, Integer> raw = getCountByStatus();
        Map<String, Integer> grouped = new LinkedHashMap<>();
        grouped.put("Patented", 0);
        grouped.put("Allowed", 0);
        grouped.put("In Examination", 0);
        grouped.put("Filed/Pending", 0);
        grouped.put("Abandoned/Expired", 0);

        for (var entry : raw.entrySet()) {
            String status = entry.getKey();
            int count = entry.getValue();
            if (status == null) continue;
            String lower = status.toLowerCase();
            if (lower.contains("patented") || lower.contains("issued")
                    || lower.contains("issue fee")) {
                grouped.merge("Patented", count, Integer::sum);
            } else if (lower.contains("allowance") || lower.contains("allowed")) {
                grouped.merge("Allowed", count, Integer::sum);
            } else if (lower.contains("abandoned") || lower.contains("expired")
                    || lower.contains("withdrawn")) {
                grouped.merge("Abandoned/Expired", count, Integer::sum);
            } else if (lower.contains("action") || lower.contains("examination")
                    || lower.contains("rejection") || lower.contains("response")
                    || lower.contains("advisory") || lower.contains("examined")) {
                grouped.merge("In Examination", count, Integer::sum);
            } else {
                grouped.merge("Filed/Pending", count, Integer::sum);
            }
        }
        return grouped;
    }

    /**
     * Returns role breakdown for a given owner name.
     * Map keys: "PRIMARY", "SECONDARY", "ADDITIONAL"
     */
    public Map<String, Integer> getOwnerRoleBreakdown(String ownerName) throws SQLException {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("PRIMARY", 0);
        result.put("SECONDARY", 0);
        result.put("ADDITIONAL", 0);

        String sql = "SELECT pi.role, COUNT(DISTINCT pi.patent_id) as cnt FROM patent_inventor pi " +
                     "JOIN inventor i ON pi.inventor_id = i.id " +
                     "WHERE i.full_name = ? OR i.username = ? " +
                     "GROUP BY pi.role";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerName);
            ps.setString(2, ownerName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.put(rs.getString("role"), rs.getInt("cnt"));
            }
        }
        return result;
    }

    private int queryInt(String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private Map<String, Integer> queryMap(String sql) throws SQLException {
        Map<String, Integer> map = new LinkedHashMap<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String key = rs.getString(1);
                if (key != null) {
                    map.put(key, rs.getInt(2));
                }
            }
        }
        return map;
    }
}
