package com.patenttracker.service;

import com.patenttracker.dao.PatentAnalysisDao;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.dao.PatentTextDao;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentAnalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class InsightExportService {

    private final PatentDao patentDao;
    private final PatentAnalysisDao patentAnalysisDao;
    private final PatentTextDao patentTextDao;

    public InsightExportService() {
        this.patentDao = new PatentDao();
        this.patentAnalysisDao = new PatentAnalysisDao();
        this.patentTextDao = new PatentTextDao();
    }

    public InsightExportService(PatentDao patentDao, PatentAnalysisDao patentAnalysisDao,
                                PatentTextDao patentTextDao) {
        this.patentDao = patentDao;
        this.patentAnalysisDao = patentAnalysisDao;
        this.patentTextDao = patentTextDao;
    }

    public String exportSingleAnalysisMarkdown(String analysisType, String title) throws SQLException {
        ObjectMapper om = new ObjectMapper();
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return "";

        int firstId = patents.getFirst().getId();
        PatentAnalysis analysis = patentAnalysisDao.findByPatentIdAndType(firstId, analysisType);
        if (analysis == null) return "";

        StringBuilder md = new StringBuilder();
        md.append("# ").append(title).append("\n\n");
        md.append("*Generated: ").append(
                analysis.getAnalyzedAt() != null
                        ? analysis.getAnalyzedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        ).append("*\n\n");

        try {
            JsonNode root = om.readTree(analysis.getResultJson());
            renderJsonAsMarkdown(md, root, 0);
        } catch (Exception e) {
            md.append("```json\n").append(analysis.getResultJson()).append("\n```\n");
        }
        return md.toString();
    }

    public String exportCrossPatentMarkdown() throws SQLException {
        ObjectMapper om = new ObjectMapper();
        List<Patent> patents = patentDao.findAll();
        StringBuilder md = new StringBuilder();

        md.append("# Cross-Patent Portfolio Analysis\n\n");
        md.append("*Generated: ").append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("*\n\n");
        md.append("- **Total Patents:** ").append(patents.size()).append("\n\n");

        if (!patents.isEmpty()) {
            int firstId = patents.getFirst().getId();
            appendCrossPatentSection(md, om, firstId, "CLUSTERING", "Cluster Analysis");
            appendCrossPatentSection(md, om, firstId, "WHITESPACE", "Whitespace Opportunities");
            appendCrossPatentSection(md, om, firstId, "ADJACENCY", "Adjacency Map");
            appendCrossPatentSection(md, om, firstId, "TEMPORAL_TRENDS", "Temporal Trends");
            appendCrossPatentSection(md, om, firstId, "CLAIM_COLLISION", "Claim Collision Report");
            appendCrossPatentSection(md, om, firstId, "COMPETITOR_GAPS", "Competitive Gap Analysis");
            appendCrossPatentSection(md, om, firstId, "CROSS_DOMAIN", "Cross-Domain Opportunities");
            appendCrossPatentSection(md, om, firstId, "INVENTION_PROMPTS", "Invention Prompts");
        }

        return md.toString();
    }

    public String exportMarkdown() throws SQLException {
        ObjectMapper om = new ObjectMapper();
        List<Patent> patents = patentDao.findAll();
        StringBuilder md = new StringBuilder();

        md.append("# Patent Portfolio Insights Report\n\n");
        md.append("*Generated: ").append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("*\n\n");

        md.append("## Portfolio Overview\n\n");
        md.append("- **Total Patents:** ").append(patents.size()).append("\n");
        int withText = patentTextDao.countAll();
        int withAnalysis = patentAnalysisDao.countDistinctPatents();
        md.append("- **Patents with Extracted Text:** ").append(withText).append("\n");
        md.append("- **Patents Analyzed:** ").append(withAnalysis).append("\n\n");

        md.append("## Technology Extraction Summary\n\n");
        for (Patent patent : patents) {
            PatentAnalysis tech = patentAnalysisDao.findByPatentIdAndType(patent.getId(), "TECHNOLOGY");
            if (tech == null) continue;

            md.append("### ").append(patent.getTitle());
            String num = patent.getPatentNumber() != null ? patent.getPatentNumber() : patent.getApplicationNumber();
            if (num != null) md.append(" (").append(num).append(")");
            md.append("\n\n");

            try {
                JsonNode node = om.readTree(tech.getResultJson());
                appendJsonField(md, node, "technical_field", "Field");
                appendJsonField(md, node, "problem_solved", "Problem");
                appendJsonArray(md, node, "innovations", "Innovations");
                appendJsonArray(md, node, "key_advantages", "Advantages");
                appendJsonArray(md, node, "keywords", "Keywords");
            } catch (Exception e) {
                md.append(tech.getResultJson()).append("\n");
            }
            md.append("\n");
        }

        if (!patents.isEmpty()) {
            int firstId = patents.getFirst().getId();
            appendCrossPatentSection(md, om, firstId, "CLUSTERING", "Cluster Analysis");
            appendCrossPatentSection(md, om, firstId, "WHITESPACE", "Whitespace Opportunities");
            appendCrossPatentSection(md, om, firstId, "ADJACENCY", "Adjacency Map");
            appendCrossPatentSection(md, om, firstId, "TEMPORAL_TRENDS", "Temporal Trends");
            appendCrossPatentSection(md, om, firstId, "CLAIM_COLLISION", "Claim Collision Report");
            appendCrossPatentSection(md, om, firstId, "COMPETITOR_GAPS", "Competitive Gap Analysis");
            appendCrossPatentSection(md, om, firstId, "CROSS_DOMAIN", "Cross-Domain Opportunities");
            appendCrossPatentSection(md, om, firstId, "INVENTION_PROMPTS", "Invention Prompts");
        }

        return md.toString();
    }

    private void appendCrossPatentSection(StringBuilder md, ObjectMapper om,
                                           int patentId, String type, String title) throws SQLException {
        PatentAnalysis analysis = patentAnalysisDao.findByPatentIdAndType(patentId, type);
        if (analysis == null) return;

        md.append("## ").append(title).append("\n\n");
        try {
            JsonNode root = om.readTree(analysis.getResultJson());
            renderJsonAsMarkdown(md, root, 0);
        } catch (Exception e) {
            md.append("```json\n").append(analysis.getResultJson()).append("\n```\n");
        }
        md.append("\n");
    }

    private void appendJsonField(StringBuilder md, JsonNode node, String field, String label) {
        JsonNode val = node.get(field);
        if (val != null && !val.isNull()) {
            md.append("- **").append(label).append(":** ").append(val.asText()).append("\n");
        }
    }

    private void appendJsonArray(StringBuilder md, JsonNode node, String field, String label) {
        JsonNode arr = node.get(field);
        if (arr != null && arr.isArray() && !arr.isEmpty()) {
            md.append("- **").append(label).append(":** ");
            List<String> items = new ArrayList<>();
            for (JsonNode item : arr) {
                items.add(item.asText());
            }
            md.append(String.join(", ", items)).append("\n");
        }
    }

    private void renderJsonAsMarkdown(StringBuilder md, JsonNode node, int depth) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = formatKey(entry.getKey());
                JsonNode value = entry.getValue();

                if (value.isValueNode()) {
                    md.append("- **").append(key).append(":** ").append(value.asText()).append("\n");
                } else if (value.isArray()) {
                    md.append("- **").append(key).append(":**\n");
                    renderJsonArray(md, value, depth + 1);
                } else if (value.isObject()) {
                    md.append("\n### ").append(key).append("\n\n");
                    renderJsonAsMarkdown(md, value, depth + 1);
                }
            }
        } else if (node.isArray()) {
            renderJsonArray(md, node, depth);
        }
    }

    private void renderJsonArray(StringBuilder md, JsonNode arr, int depth) {
        for (JsonNode item : arr) {
            if (item.isValueNode()) {
                md.append("  - ").append(item.asText()).append("\n");
            } else if (item.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
                boolean first = true;
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String key = formatKey(entry.getKey());
                    JsonNode value = entry.getValue();
                    if (first) {
                        if (value.isValueNode()) {
                            md.append("  - **").append(key).append(":** ").append(value.asText()).append("\n");
                        } else {
                            md.append("  - **").append(key).append(":**\n");
                        }
                        first = false;
                    } else if (value.isValueNode()) {
                        md.append("    - *").append(key).append(":* ").append(value.asText()).append("\n");
                    } else if (value.isArray()) {
                        md.append("    - *").append(key).append(":* ");
                        List<String> items = new ArrayList<>();
                        for (JsonNode child : value) {
                            items.add(child.asText());
                        }
                        md.append(String.join(", ", items)).append("\n");
                    }
                }
                md.append("\n");
            }
        }
    }

    private String formatKey(String key) {
        return key.replace("_", " ").substring(0, 1).toUpperCase() + key.replace("_", " ").substring(1);
    }
}
