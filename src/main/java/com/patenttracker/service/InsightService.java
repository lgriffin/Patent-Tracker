package com.patenttracker.service;

import com.patenttracker.controller.SettingsController;
import com.patenttracker.dao.PatentAnalysisDao;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.dao.PatentTextDao;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentAnalysis;
import com.patenttracker.model.PatentText;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class InsightService {

    private final PatentDao patentDao;
    private final PatentTextDao patentTextDao;
    private final PatentAnalysisDao patentAnalysisDao;
    private final PdfExtractorService pdfExtractorService;
    private final ClaudeCliService claudeCliService;

    public InsightService() {
        this.patentDao = new PatentDao();
        this.patentTextDao = new PatentTextDao();
        this.patentAnalysisDao = new PatentAnalysisDao();
        this.pdfExtractorService = new PdfExtractorService();
        this.claudeCliService = new ClaudeCliService();
    }

    public InsightResult analyzeClaims(Patent patent) {
        return runSinglePatentAnalysis(patent, "CLAIMS", "claims");
    }

    public InsightResult analyzeTechnology(Patent patent) {
        return runSinglePatentAnalysis(patent, "TECHNOLOGY", "technology");
    }

    public InsightResult analyzeExpansion(Patent patent) {
        return runSinglePatentAnalysis(patent, "EXPANSION", "expansion");
    }

    public InsightResult analyzePriorArt(Patent patent) {
        return runSinglePatentAnalysis(patent, "PRIOR_ART", "prior-art");
    }

    private InsightResult runSinglePatentAnalysis(Patent patent, String analysisType, String templateName) {
        String text = ensureTextExtracted(patent);
        if (text == null) {
            return new InsightResult(false, analysisType, null,
                    "No text available - ensure PDF is downloaded first.", 0);
        }

        try {
            String template = ClaudeCliService.loadPromptTemplate(templateName);
            Map<String, String> variables = Map.of(
                    "patent_title", patent.getTitle() != null ? patent.getTitle() : "",
                    "patent_number", patent.getPatentNumber() != null ? patent.getPatentNumber() :
                            (patent.getApplicationNumber() != null ? patent.getApplicationNumber() : ""),
                    "patent_text", text
            );

            ClaudeCliService.AnalysisResult cliResult = claudeCliService.analyze(template, variables);

            if (cliResult.success() && cliResult.resultJson() != null) {
                PatentAnalysis pa = new PatentAnalysis();
                pa.setPatentId(patent.getId());
                pa.setAnalysisType(analysisType);
                pa.setResultJson(cliResult.resultJson());
                pa.setModelUsed(cliResult.modelUsed());
                patentAnalysisDao.insertOrUpdate(pa);

                return new InsightResult(true, analysisType, cliResult.resultJson(),
                        null, cliResult.durationMs());
            } else {
                return new InsightResult(false, analysisType, null,
                        cliResult.error(), cliResult.durationMs());
            }
        } catch (IOException e) {
            return new InsightResult(false, analysisType, null,
                    "Failed to load prompt template: " + e.getMessage(), 0);
        } catch (SQLException e) {
            return new InsightResult(false, analysisType, null,
                    "Database error: " + e.getMessage(), 0);
        }
    }

    public InsightResult analyzeWhitespace(List<Patent> patents) {
        return runCrossPatentAnalysis(patents, "WHITESPACE", "whitespace");
    }

    public InsightResult analyzeClustering(List<Patent> patents) {
        return runCrossPatentAnalysis(patents, "CLUSTERING", "clustering");
    }

    public InsightResult analyzeAdjacency(List<Patent> patents) {
        return runCrossPatentAnalysis(patents, "ADJACENCY", "adjacency");
    }

    public InsightResult analyzeClaimCollision(List<Patent> patents) {
        return runCrossPatentAnalysis(patents, "CLAIM_COLLISION", "claim-collision");
    }

    public InsightResult analyzeCompetitorGaps(List<Patent> patents) {
        return runCrossPatentAnalysis(patents, "COMPETITOR_GAPS", "competitor-gaps");
    }

    public InsightResult analyzeCrossDomain(List<Patent> patents) {
        return runCrossPatentAnalysis(patents, "CROSS_DOMAIN", "cross-domain");
    }

    public InsightResult analyzeTemporalTrends(List<Patent> patents) {
        return runCrossPatentAnalysisWithDates(patents, "TEMPORAL_TRENDS", "temporal-trends");
    }

    public InsightResult analyzeInventionPrompts(List<Patent> patents) {
        return runCrossPatentAnalysisEnriched(patents);
    }

    private InsightResult runCrossPatentAnalysisWithDates(List<Patent> patents, String analysisType, String templateName) {
        StringBuilder summaries = new StringBuilder();
        int included = 0;

        for (Patent patent : patents) {
            try {
                PatentAnalysis techAnalysis = patentAnalysisDao.findByPatentIdAndType(
                        patent.getId(), "TECHNOLOGY");
                if (techAnalysis == null) continue;

                LocalDate filed = patent.getFilingDate();
                summaries.append("Patent: ").append(patent.getTitle())
                        .append(" (").append(patent.getPatentNumber() != null ?
                                patent.getPatentNumber() : patent.getApplicationNumber())
                        .append(")")
                        .append(" | Filed: ").append(filed != null ? filed.format(DateTimeFormatter.ISO_LOCAL_DATE) : "unknown")
                        .append("\n")
                        .append(techAnalysis.getResultJson())
                        .append("\n---\n");
                included++;
            } catch (SQLException e) {
                // Skip
            }
        }

        if (included < 2) {
            return new InsightResult(false, analysisType, null,
                    "Need at least 2 patents with technology extraction. Found: " + included, 0);
        }

        try {
            String template = ClaudeCliService.loadPromptTemplate(templateName);
            Map<String, String> variables = Map.of("portfolio_summaries", summaries.toString());

            int timeout = SettingsController.getAnalysisTimeout();
            ClaudeCliService.AnalysisResult cliResult = claudeCliService.analyze(template, variables, timeout);

            if (cliResult.success() && cliResult.resultJson() != null) {
                PatentAnalysis pa = new PatentAnalysis();
                pa.setPatentId(patents.get(0).getId());
                pa.setAnalysisType(analysisType);
                pa.setResultJson(cliResult.resultJson());
                pa.setModelUsed(cliResult.modelUsed());
                patentAnalysisDao.insertOrUpdate(pa);
                return new InsightResult(true, analysisType, cliResult.resultJson(), null, cliResult.durationMs());
            } else {
                return new InsightResult(false, analysisType, null, cliResult.error(), cliResult.durationMs());
            }
        } catch (IOException e) {
            return new InsightResult(false, analysisType, null, "Failed to load prompt template: " + e.getMessage(), 0);
        } catch (SQLException e) {
            return new InsightResult(false, analysisType, null, "Database error: " + e.getMessage(), 0);
        }
    }

    private InsightResult runCrossPatentAnalysisEnriched(List<Patent> patents) {
        StringBuilder summaries = new StringBuilder();
        int included = 0;

        for (Patent patent : patents) {
            try {
                PatentAnalysis techAnalysis = patentAnalysisDao.findByPatentIdAndType(
                        patent.getId(), "TECHNOLOGY");
                if (techAnalysis == null) continue;

                summaries.append("Patent: ").append(patent.getTitle())
                        .append(" (").append(patent.getPatentNumber() != null ?
                                patent.getPatentNumber() : patent.getApplicationNumber())
                        .append(")\n")
                        .append(techAnalysis.getResultJson())
                        .append("\n---\n");
                included++;
            } catch (SQLException e) {
                // Skip
            }
        }

        if (included < 2) {
            return new InsightResult(false, "INVENTION_PROMPTS", null,
                    "Need at least 2 patents with technology extraction. Found: " + included, 0);
        }

        StringBuilder additionalContext = new StringBuilder();
        try {
            PatentAnalysis whitespace = patentAnalysisDao.findByPatentIdAndType(
                    patents.get(0).getId(), "WHITESPACE");
            if (whitespace != null) {
                additionalContext.append("\nWhitespace Analysis Results:\n")
                        .append(whitespace.getResultJson()).append("\n");
            }

            PatentAnalysis clustering = patentAnalysisDao.findByPatentIdAndType(
                    patents.get(0).getId(), "CLUSTERING");
            if (clustering != null) {
                additionalContext.append("\nClustering Analysis Results:\n")
                        .append(clustering.getResultJson()).append("\n");
            }
        } catch (SQLException ignored) {}

        try {
            String template = ClaudeCliService.loadPromptTemplate("invention-prompts");
            Map<String, String> variables = Map.of(
                    "portfolio_summaries", summaries.toString(),
                    "additional_context", additionalContext.toString()
            );

            int timeout = SettingsController.getAnalysisTimeout();
            ClaudeCliService.AnalysisResult cliResult = claudeCliService.analyze(template, variables, timeout);

            if (cliResult.success() && cliResult.resultJson() != null) {
                PatentAnalysis pa = new PatentAnalysis();
                pa.setPatentId(patents.get(0).getId());
                pa.setAnalysisType("INVENTION_PROMPTS");
                pa.setResultJson(cliResult.resultJson());
                pa.setModelUsed(cliResult.modelUsed());
                patentAnalysisDao.insertOrUpdate(pa);
                return new InsightResult(true, "INVENTION_PROMPTS", cliResult.resultJson(), null, cliResult.durationMs());
            } else {
                return new InsightResult(false, "INVENTION_PROMPTS", null, cliResult.error(), cliResult.durationMs());
            }
        } catch (IOException e) {
            return new InsightResult(false, "INVENTION_PROMPTS", null, "Failed to load prompt template: " + e.getMessage(), 0);
        } catch (SQLException e) {
            return new InsightResult(false, "INVENTION_PROMPTS", null, "Database error: " + e.getMessage(), 0);
        }
    }

    private InsightResult runCrossPatentAnalysis(List<Patent> patents, String analysisType, String templateName) {
        StringBuilder summaries = new StringBuilder();
        int included = 0;

        for (Patent patent : patents) {
            try {
                PatentAnalysis techAnalysis = patentAnalysisDao.findByPatentIdAndType(
                        patent.getId(), "TECHNOLOGY");

                if (techAnalysis == null) {
                    InsightResult techResult = analyzeTechnology(patent);
                    if (!techResult.success()) continue;
                    techAnalysis = patentAnalysisDao.findByPatentIdAndType(patent.getId(), "TECHNOLOGY");
                    if (techAnalysis == null) continue;
                }

                summaries.append("Patent: ").append(patent.getTitle())
                        .append(" (").append(patent.getPatentNumber() != null ?
                                patent.getPatentNumber() : patent.getApplicationNumber())
                        .append(")\n")
                        .append(techAnalysis.getResultJson())
                        .append("\n---\n");
                included++;
            } catch (SQLException e) {
                // Skip this patent
            }
        }

        if (included < 2) {
            return new InsightResult(false, analysisType, null,
                    "Need at least 2 patents with technology extraction. Found: " + included, 0);
        }

        try {
            String template = ClaudeCliService.loadPromptTemplate(templateName);
            Map<String, String> variables = Map.of("portfolio_summaries", summaries.toString());

            int timeout = SettingsController.getAnalysisTimeout();
            ClaudeCliService.AnalysisResult cliResult = claudeCliService.analyze(template, variables, timeout);

            if (cliResult.success() && cliResult.resultJson() != null) {
                // Store with first patent's ID
                PatentAnalysis pa = new PatentAnalysis();
                pa.setPatentId(patents.get(0).getId());
                pa.setAnalysisType(analysisType);
                pa.setResultJson(cliResult.resultJson());
                pa.setModelUsed(cliResult.modelUsed());
                patentAnalysisDao.insertOrUpdate(pa);

                return new InsightResult(true, analysisType, cliResult.resultJson(),
                        null, cliResult.durationMs());
            } else {
                return new InsightResult(false, analysisType, null,
                        cliResult.error(), cliResult.durationMs());
            }
        } catch (IOException e) {
            return new InsightResult(false, analysisType, null,
                    "Failed to load prompt template: " + e.getMessage(), 0);
        } catch (SQLException e) {
            return new InsightResult(false, analysisType, null,
                    "Database error: " + e.getMessage(), 0);
        }
    }

    public List<InsightResult> analyzeAll(String analysisType, String templateName,
                                          AnalysisProgressCallback callback) {
        List<InsightResult> results = new ArrayList<>();

        try {
            List<PatentText> allText = patentTextDao.findAll();

            for (int i = 0; i < allText.size(); i++) {
                if (callback != null && callback.isCancelled()) break;

                PatentText pt = allText.get(i);
                Patent patent = patentDao.findById(pt.getPatentId());
                if (patent == null) continue;

                if (callback != null) {
                    callback.onProgress(i + 1, allText.size(), patent.getTitle());
                }

                // Skip if already analyzed
                try {
                    PatentAnalysis existing = patentAnalysisDao.findByPatentIdAndType(
                            patent.getId(), analysisType);
                    if (existing != null) {
                        InsightResult cached = new InsightResult(true, analysisType,
                                existing.getResultJson(), null, 0);
                        results.add(cached);
                        if (callback != null) callback.onResult(cached);
                        continue;
                    }
                } catch (SQLException ignored) {}

                InsightResult result = runSinglePatentAnalysis(patent, analysisType, templateName);
                results.add(result);

                if (callback != null) {
                    callback.onResult(result);
                }

                if (i < allText.size() - 1) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            results.add(new InsightResult(false, analysisType, null,
                    "Database error: " + e.getMessage(), 0));
        }

        return results;
    }

    public List<PatentAnalysis> getCachedAnalyses(int patentId) throws SQLException {
        return patentAnalysisDao.findByPatentId(patentId);
    }

    public PatentAnalysis getCachedAnalysis(int patentId, String type) throws SQLException {
        return patentAnalysisDao.findByPatentIdAndType(patentId, type);
    }

    public InsightStats getStats() {
        try {
            int totalPatents = patentDao.count();
            int withText = patentTextDao.countAll();
            int withAnalysis = patentAnalysisDao.countDistinctPatents();
            Map<String, Integer> byType = patentAnalysisDao.countByType();
            return new InsightStats(totalPatents, withText, withAnalysis, byType);
        } catch (SQLException e) {
            return new InsightStats(0, 0, 0, Map.of());
        }
    }

    private String ensureTextExtracted(Patent patent) {
        try {
            PatentText pt = patentTextDao.findByPatentId(patent.getId());
            if (pt != null) {
                return pt.getFullText();
            }

            PdfExtractorService.ExtractionResult result = pdfExtractorService.extractText(patent);
            if (result.success()) {
                pt = patentTextDao.findByPatentId(patent.getId());
                return pt != null ? pt.getFullText() : null;
            }
        } catch (SQLException ignored) {}
        return null;
    }

    public String exportMarkdown() throws SQLException {
        ObjectMapper om = new ObjectMapper();
        List<Patent> patents = patentDao.findAll();
        StringBuilder md = new StringBuilder();

        md.append("# Patent Portfolio Insights Report\n\n");
        md.append("*Generated: ").append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("*\n\n");

        // Portfolio Overview
        md.append("## Portfolio Overview\n\n");
        md.append("- **Total Patents:** ").append(patents.size()).append("\n");
        int withText = patentTextDao.countAll();
        int withAnalysis = patentAnalysisDao.countDistinctPatents();
        md.append("- **Patents with Extracted Text:** ").append(withText).append("\n");
        md.append("- **Patents Analyzed:** ").append(withAnalysis).append("\n\n");

        // Technology Extraction Summary
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

        // Cross-patent analysis sections — each stored with first patent's ID
        if (!patents.isEmpty()) {
            int firstId = patents.get(0).getId();
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

    public record InsightResult(
            boolean success, String analysisType, String resultJson,
            String error, long durationMs
    ) {}

    public record InsightStats(
            int totalPatents, int withText, int withAnalysis,
            Map<String, Integer> analysisByType
    ) {}

    public interface AnalysisProgressCallback {
        void onProgress(int current, int total, String title);
        void onResult(InsightResult result);
        boolean isCancelled();
    }
}
