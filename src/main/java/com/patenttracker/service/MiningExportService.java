package com.patenttracker.service;

import com.patenttracker.dao.PatentAnalysisDao;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentAnalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles markdown export for patent mining results (area-based and invention-prompt-based).
 */
public class MiningExportService {

    static final String ANALYSIS_TYPE_PREFIX = "PATENT_MINING:";
    static final String IP_ANALYSIS_TYPE_PREFIX = "PATENT_MINING_IP:";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final PatentDao patentDao;
    private final PatentAnalysisDao patentAnalysisDao;

    public MiningExportService() {
        this.patentDao = new PatentDao();
        this.patentAnalysisDao = new PatentAnalysisDao();
    }

    public MiningExportService(PatentDao patentDao, PatentAnalysisDao patentAnalysisDao) {
        this.patentDao = patentDao;
        this.patentAnalysisDao = patentAnalysisDao;
    }

    public String exportMiningMarkdown(String areaName) throws SQLException {
        PatentAnalysis analysis = getCachedMiningResult(areaName);
        if (analysis == null) return "";

        StringBuilder md = new StringBuilder();
        md.append("# Patent Mining: ").append(areaName).append("\n\n");
        md.append("*Generated: ").append(
                analysis.getAnalyzedAt() != null
                        ? analysis.getAnalyzedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : java.time.LocalDate.now().toString()
        ).append("*\n\n");

        try {
            JsonNode root = mapper.readTree(analysis.getResultJson());

            JsonNode summary = root.get("landscape_summary");
            if (summary != null) {
                md.append("## Landscape Summary\n\n");
                appendField(md, summary, "search_area", "Search Area");
                appendField(md, summary, "external_patents_analyzed", "External Patents Analyzed");
                appendField(md, summary, "portfolio_overlap", "Portfolio Overlap");
                appendField(md, summary, "competitive_density", "Competitive Density");
                appendArray(md, summary, "key_trends", "Key Trends");
                md.append("\n");
            }

            JsonNode ideas = root.get("patent_ideas");
            if (ideas != null && ideas.isArray()) {
                md.append("## Patent Ideas\n\n");
                int num = 1;
                for (JsonNode idea : ideas) {
                    md.append("### ").append(num++).append(". ")
                            .append(textOrNull(idea, "title")).append("\n\n");
                    md.append("**Problem Statement:** ").append(textOrNull(idea, "problem_statement")).append("\n\n");
                    md.append("**Description:** ").append(textOrNull(idea, "description")).append("\n\n");
                    appendField(md, idea, "novelty_angle", "Novelty Angle");
                    appendField(md, idea, "technical_domain", "Technical Domain");
                    appendField(md, idea, "feasibility", "Feasibility");
                    appendField(md, idea, "strategic_value", "Strategic Value");
                    appendArray(md, idea, "inspired_by_external", "Inspired by External Patents");
                    appendArray(md, idea, "builds_on_portfolio", "Builds on Portfolio Patents");
                    md.append("\n---\n\n");
                }
            }

            JsonNode defensive = root.get("defensive_opportunities");
            if (defensive != null && defensive.isArray()) {
                md.append("## Defensive Opportunities\n\n");
                int num = 1;
                for (JsonNode def : defensive) {
                    md.append("### ").append(num++).append(". ")
                            .append(textOrNull(def, "title")).append("\n\n");
                    md.append("**Problem Statement:** ").append(textOrNull(def, "problem_statement")).append("\n\n");
                    md.append("**Description:** ").append(textOrNull(def, "description")).append("\n\n");
                    appendField(md, def, "urgency", "Urgency");
                    appendArray(md, def, "threat_patents", "Threat Patents");
                    md.append("\n---\n\n");
                }
            }

            JsonNode blindSpots = root.get("portfolio_blind_spots");
            if (blindSpots != null && blindSpots.isArray()) {
                md.append("## Portfolio Blind Spots\n\n");
                for (JsonNode spot : blindSpots) {
                    md.append("- ").append(spot.asText()).append("\n");
                }
                md.append("\n");
            }

        } catch (Exception e) {
            md.append("```json\n").append(analysis.getResultJson()).append("\n```\n");
        }

        return md.toString();
    }

    public String exportIPMiningMarkdown(String promptTitle) throws SQLException {
        PatentAnalysis analysis = getCachedIPMiningResult(promptTitle);
        if (analysis == null) return "";

        StringBuilder md = new StringBuilder();
        md.append("# Invention Prompt Mining: ").append(promptTitle).append("\n\n");
        md.append("*Generated: ").append(
                analysis.getAnalyzedAt() != null
                        ? analysis.getAnalyzedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : java.time.LocalDate.now().toString()
        ).append("*\n\n");

        try {
            JsonNode root = mapper.readTree(analysis.getResultJson());

            JsonNode validation = root.get("idea_validation");
            if (validation != null) {
                md.append("## Idea Validation\n\n");
                appendField(md, validation, "original_idea", "Original Idea");
                appendField(md, validation, "landscape_support", "Landscape Support");
                appendField(md, validation, "differentiation_assessment", "Differentiation");
                appendArray(md, validation, "closest_external", "Closest External Patents");
                appendArray(md, validation, "recommended_refinements", "Recommended Refinements");
                appendArray(md, validation, "risk_factors", "Risk Factors");
                md.append("\n");
            }

            JsonNode summary = root.get("landscape_summary");
            if (summary != null) {
                md.append("## Landscape Summary\n\n");
                appendField(md, summary, "search_area", "Search Area");
                appendField(md, summary, "external_patents_analyzed", "External Patents Analyzed");
                appendField(md, summary, "portfolio_overlap", "Portfolio Overlap");
                appendField(md, summary, "competitive_density", "Competitive Density");
                appendArray(md, summary, "key_trends", "Key Trends");
                md.append("\n");
            }

            JsonNode ideas = root.get("patent_ideas");
            if (ideas != null && ideas.isArray()) {
                md.append("## Refined Patent Ideas\n\n");
                int num = 1;
                for (JsonNode idea : ideas) {
                    md.append("### ").append(num++).append(". ")
                            .append(textOrNull(idea, "title")).append("\n\n");
                    md.append("**Problem Statement:** ").append(textOrNull(idea, "problem_statement")).append("\n\n");
                    md.append("**Description:** ").append(textOrNull(idea, "description")).append("\n\n");
                    appendField(md, idea, "novelty_angle", "Novelty Angle");
                    appendField(md, idea, "technical_domain", "Technical Domain");
                    appendField(md, idea, "feasibility", "Feasibility");
                    appendField(md, idea, "strategic_value", "Strategic Value");
                    appendArray(md, idea, "inspired_by_external", "Inspired by External Patents");
                    appendArray(md, idea, "builds_on_portfolio", "Builds on Portfolio Patents");
                    md.append("\n---\n\n");
                }
            }

            JsonNode defensive = root.get("defensive_opportunities");
            if (defensive != null && defensive.isArray()) {
                md.append("## Defensive Opportunities\n\n");
                int num = 1;
                for (JsonNode def : defensive) {
                    md.append("### ").append(num++).append(". ")
                            .append(textOrNull(def, "title")).append("\n\n");
                    md.append("**Problem Statement:** ").append(textOrNull(def, "problem_statement")).append("\n\n");
                    md.append("**Description:** ").append(textOrNull(def, "description")).append("\n\n");
                    appendField(md, def, "urgency", "Urgency");
                    appendArray(md, def, "threat_patents", "Threat Patents");
                    md.append("\n---\n\n");
                }
            }

            JsonNode blindSpots = root.get("portfolio_blind_spots");
            if (blindSpots != null && blindSpots.isArray()) {
                md.append("## Portfolio Blind Spots\n\n");
                for (JsonNode spot : blindSpots) {
                    md.append("- ").append(spot.asText()).append("\n");
                }
                md.append("\n");
            }
        } catch (Exception e) {
            md.append("```json\n").append(analysis.getResultJson()).append("\n```\n");
        }

        return md.toString();
    }

    public String exportAllMiningMarkdown() throws SQLException {
        List<PatentMiningService.MiningHistoryItem> history = getAllMiningResults();
        if (history.isEmpty()) return "";

        StringBuilder md = new StringBuilder();
        md.append("# Patent Mining -- All Results\n\n");
        md.append("*Exported: ").append(java.time.LocalDate.now()).append("*\n\n");
        md.append("**Total results:** ").append(history.size()).append("\n\n");
        md.append("---\n\n");

        for (PatentMiningService.MiningHistoryItem item : history) {
            if (item.isIPMining()) {
                md.append(exportIPMiningMarkdown(item.area()));
            } else {
                md.append(exportMiningMarkdown(item.area()));
            }
            md.append("\n---\n\n");
        }

        return md.toString();
    }

    private PatentAnalysis getCachedMiningResult(String areaName) throws SQLException {
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return null;
        String analysisType = ANALYSIS_TYPE_PREFIX + areaName;
        return patentAnalysisDao.findByPatentIdAndType(patents.getFirst().getId(), analysisType);
    }

    private PatentAnalysis getCachedIPMiningResult(String promptTitle) throws SQLException {
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return null;
        String analysisType = IP_ANALYSIS_TYPE_PREFIX + promptTitle;
        return patentAnalysisDao.findByPatentIdAndType(patents.getFirst().getId(), analysisType);
    }

    private List<PatentMiningService.MiningHistoryItem> getAllMiningResults() throws SQLException {
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return List.of();
        int firstId = patents.getFirst().getId();

        List<PatentMiningService.MiningHistoryItem> results = new ArrayList<>();

        List<PatentAnalysis> generalResults = patentAnalysisDao.findByPatentIdAndTypePrefix(firstId, ANALYSIS_TYPE_PREFIX);
        for (PatentAnalysis pa : generalResults) {
            String area = pa.getAnalysisType().substring(ANALYSIS_TYPE_PREFIX.length());
            results.add(new PatentMiningService.MiningHistoryItem(pa.getAnalysisType(), area,
                    pa.getResultJson(), pa.getAnalyzedAt(), false));
        }

        List<PatentAnalysis> ipResults = patentAnalysisDao.findByPatentIdAndTypePrefix(firstId, IP_ANALYSIS_TYPE_PREFIX);
        for (PatentAnalysis pa : ipResults) {
            String area = pa.getAnalysisType().substring(IP_ANALYSIS_TYPE_PREFIX.length());
            results.add(new PatentMiningService.MiningHistoryItem(pa.getAnalysisType(), area,
                    pa.getResultJson(), pa.getAnalyzedAt(), true));
        }

        results.sort((a, b) -> {
            if (a.analyzedAt() == null && b.analyzedAt() == null) return 0;
            if (a.analyzedAt() == null) return 1;
            if (b.analyzedAt() == null) return -1;
            return b.analyzedAt().compareTo(a.analyzedAt());
        });

        return results;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull() || !val.isTextual()) return null;
        String text = val.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private void appendField(StringBuilder md, JsonNode node, String field, String label) {
        JsonNode val = node.get(field);
        if (val != null && !val.isNull()) {
            md.append("- **").append(label).append(":** ").append(val.asText()).append("\n");
        }
    }

    private void appendArray(StringBuilder md, JsonNode node, String field, String label) {
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
}
