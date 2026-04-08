package com.patenttracker.service;

import com.patenttracker.controller.SettingsController;
import com.patenttracker.dao.DatabaseManager;
import com.patenttracker.dao.InventorDao;
import com.patenttracker.dao.InventorDao.CoInventorEdge;
import com.patenttracker.model.Inventor;
import com.patenttracker.model.Patent;

import java.sql.*;
import java.util.*;

public class GraphDataService {

    private final InventorDao inventorDao;

    public GraphDataService() {
        this.inventorDao = new InventorDao();
    }

    public String buildGraphJson(List<Patent> filteredPatents) throws SQLException {
        List<Integer> patentIds = null;
        if (filteredPatents != null) {
            patentIds = filteredPatents.stream().map(Patent::getId).toList();
        }

        List<CoInventorEdge> edges = inventorDao.getCoInventorEdges(patentIds);

        // Collect unique inventors from edges
        Set<Integer> inventorIds = new LinkedHashSet<>();
        Map<Integer, String> inventorNames = new HashMap<>();
        Map<Integer, Integer> inventorPatentCounts = new HashMap<>();

        for (CoInventorEdge edge : edges) {
            inventorIds.add(edge.inventor1Id());
            inventorIds.add(edge.inventor2Id());
            inventorNames.put(edge.inventor1Id(), edge.inventor1Name());
            inventorNames.put(edge.inventor2Id(), edge.inventor2Name());
            inventorPatentCounts.merge(edge.inventor1Id(), edge.sharedCount(), Integer::sum);
            inventorPatentCounts.merge(edge.inventor2Id(), edge.sharedCount(), Integer::sum);
        }

        // Also get standalone inventors with patents but no co-inventors
        List<Inventor> allInventors = inventorDao.findAll();
        for (Inventor inv : allInventors) {
            if (!inventorIds.contains(inv.getId()) && inv.getPatentCount() > 0) {
                inventorIds.add(inv.getId());
                inventorNames.put(inv.getId(), inv.getFullName());
                inventorPatentCounts.put(inv.getId(), inv.getPatentCount());
            }
        }

        // Get primary classification (most-used tag) per inventor
        Map<Integer, String> inventorPrimaryTag = getInventorPrimaryTags(inventorIds);

        // Get owner name for highlighting
        String ownerName = SettingsController.getOwnerName();

        // Build JSON
        StringBuilder json = new StringBuilder();
        json.append("{\"ownerName\":\"").append(escapeJson(ownerName)).append("\",");
        json.append("\"nodes\":[");

        boolean first = true;
        for (int id : inventorIds) {
            if (!first) json.append(",");
            first = false;
            String name = escapeJson(inventorNames.getOrDefault(id, "Unknown"));
            int count = inventorPatentCounts.getOrDefault(id, 1);
            int size = Math.max(10, Math.min(50, 10 + count));
            String tag = escapeJson(inventorPrimaryTag.getOrDefault(id, ""));
            boolean isOwner = name.equalsIgnoreCase(ownerName);
            json.append(String.format(
                "{\"id\":%d,\"label\":\"%s\",\"value\":%d,\"size\":%d,\"primaryTag\":\"%s\",\"isOwner\":%s}",
                id, name, count, size, tag, isOwner));
        }

        json.append("],\"edges\":[");

        first = true;
        int edgeId = 0;
        for (CoInventorEdge edge : edges) {
            if (!first) json.append(",");
            first = false;
            json.append(String.format(
                "{\"id\":%d,\"from\":%d,\"to\":%d,\"value\":%d,\"title\":\"%d shared patents\"}",
                edgeId++, edge.inventor1Id(), edge.inventor2Id(), edge.sharedCount(), edge.sharedCount()
            ));
        }

        json.append("]}");
        return json.toString();
    }

    /**
     * For each inventor, find their most frequently associated tag (primary classification).
     */
    private Map<Integer, String> getInventorPrimaryTags(Set<Integer> inventorIds) {
        if (inventorIds.isEmpty()) return Map.of();

        Map<Integer, String> result = new HashMap<>();
        String sql = """
            SELECT pi.inventor_id, t.name, COUNT(DISTINCT pt.patent_id) as cnt
            FROM patent_inventor pi
            JOIN patent_tag pt ON pi.patent_id = pt.patent_id
            JOIN tag t ON pt.tag_id = t.id
            WHERE pi.inventor_id IN (%s)
            GROUP BY pi.inventor_id, t.id
            ORDER BY pi.inventor_id, cnt DESC
            """;

        String placeholders = String.join(",", inventorIds.stream().map(id -> String.valueOf(id)).toList());
        Connection conn = DatabaseManager.getInstance().getConnection();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(String.format(sql, placeholders));
            int lastInventorId = -1;
            while (rs.next()) {
                int invId = rs.getInt("inventor_id");
                // Only take the first (highest count) tag per inventor
                if (invId != lastInventorId) {
                    result.put(invId, rs.getString("name"));
                    lastInventorId = invId;
                }
            }
        } catch (SQLException e) {
            // Non-critical
        }
        return result;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
