package com.patenttracker.service;

import com.patenttracker.controller.SettingsController;
import com.patenttracker.dao.MinedPatentDao;
import com.patenttracker.dao.PatentAnalysisDao;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.model.MinedPatent;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentAnalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PatentMiningService {

    private static final String ANALYSIS_TYPE_PREFIX = "PATENT_MINING:";
    private static final String IP_ANALYSIS_TYPE_PREFIX = "PATENT_MINING_IP:";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final PatentDao patentDao;
    private final PatentAnalysisDao patentAnalysisDao;
    private final MinedPatentDao minedPatentDao;
    private final GooglePatentsSearchService searchService;
    private final ClaudeCliService claudeCliService;

    public PatentMiningService() {
        this.patentDao = new PatentDao();
        this.patentAnalysisDao = new PatentAnalysisDao();
        this.minedPatentDao = new MinedPatentDao();
        this.searchService = new GooglePatentsSearchService();
        this.claudeCliService = new ClaudeCliService();
    }

    public List<AreaOfInterest> extractAreasOfInterest() throws SQLException {
        Map<String, AreaBuilder> areas = new LinkedHashMap<>();
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return List.of();

        int firstId = patents.get(0).getId();

        extractFromAnalysis(areas, firstId, "CLUSTERING",
                node -> {
                    extractArrayField(areas, node, "clusters", "theme", "key_technologies", "Clustering");
                });

        extractFromAnalysis(areas, firstId, "CROSS_DOMAIN",
                node -> {
                    extractArrayField(areas, node, "domain_inventory", "domain", "core_capabilities", "Cross-Domain");
                });

        extractFromAnalysis(areas, firstId, "WHITESPACE",
                node -> {
                    JsonNode opps = node.get("whitespace_opportunities");
                    if (opps != null && opps.isArray()) {
                        for (JsonNode opp : opps) {
                            String area = textOrNull(opp, "area");
                            if (area != null) {
                                getBuilder(areas, area).addSource("Whitespace");
                            }
                        }
                    }
                });

        extractFromAnalysis(areas, firstId, "TEMPORAL_TRENDS",
                node -> {
                    JsonNode trends = node.get("domain_trends");
                    if (trends != null && trends.isArray()) {
                        for (JsonNode t : trends) {
                            String domain = textOrNull(t, "domain");
                            if (domain != null) {
                                getBuilder(areas, domain).addSource("Temporal Trends");
                            }
                        }
                    }
                    JsonNode emerging = node.get("emerging_themes");
                    if (emerging != null && emerging.isArray()) {
                        for (JsonNode e : emerging) {
                            String theme = e.isTextual() ? e.asText() : textOrNull(e, "theme");
                            if (theme != null) {
                                getBuilder(areas, theme).addSource("Temporal Trends (Emerging)");
                            }
                        }
                    }
                });

        extractFromAnalysis(areas, firstId, "ADJACENCY",
                node -> {
                    JsonNode anchors = node.get("existing_anchors");
                    if (anchors != null && anchors.isArray()) {
                        for (JsonNode a : anchors) {
                            String domain = textOrNull(a, "domain");
                            if (domain != null) {
                                getBuilder(areas, domain).addSource("Adjacency (Anchor)");
                            }
                        }
                    }
                    JsonNode map = node.get("adjacency_map");
                    if (map != null && map.isArray()) {
                        for (JsonNode m : map) {
                            String target = textOrNull(m, "target_area");
                            if (target != null) {
                                getBuilder(areas, target).addSource("Adjacency (Target)");
                            }
                        }
                    }
                });

        extractFromAnalysis(areas, firstId, "COMPETITOR_GAPS",
                node -> {
                    JsonNode strengths = node.get("portfolio_strengths");
                    if (strengths != null && strengths.isArray()) {
                        for (JsonNode s : strengths) {
                            String area = textOrNull(s, "area");
                            if (area != null) {
                                getBuilder(areas, area).addSource("Competitor Gaps (Strength)");
                            }
                        }
                    }
                    JsonNode competitors = node.get("likely_competitor_areas");
                    if (competitors != null && competitors.isArray()) {
                        for (JsonNode c : competitors) {
                            String area = textOrNull(c, "area");
                            if (area != null) {
                                getBuilder(areas, area).addSource("Competitor Gaps");
                            }
                        }
                    }
                });

        for (Patent patent : patents) {
            try {
                PatentAnalysis tech = patentAnalysisDao.findByPatentIdAndType(patent.getId(), "TECHNOLOGY");
                if (tech != null) {
                    JsonNode node = mapper.readTree(tech.getResultJson());
                    String field = textOrNull(node, "technical_field");
                    if (field != null) {
                        getBuilder(areas, field).addSource("Technology");
                    }
                    JsonNode keywords = node.get("keywords");
                    if (keywords != null && keywords.isArray()) {
                        for (JsonNode kw : keywords) {
                            if (kw.isTextual()) {
                                String keyword = kw.asText().trim();
                                if (!keyword.isEmpty() && keyword.length() > 3) {
                                    AreaBuilder builder = areas.values().stream()
                                            .filter(b -> b.name.toLowerCase().contains(keyword.toLowerCase())
                                                    || keyword.toLowerCase().contains(b.name.toLowerCase()))
                                            .findFirst().orElse(null);
                                    if (builder != null) {
                                        builder.addKeyword(keyword);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        List<AreaOfInterest> result = new ArrayList<>();
        for (AreaBuilder builder : areas.values()) {
            result.add(builder.build());
        }

        result.sort((a, b) -> Integer.compare(b.sourceAnalyses().size(), a.sourceAnalyses().size()));
        return result;
    }

    public List<InventionPromptItem> extractInventionPrompts() throws SQLException {
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return List.of();

        int firstId = patents.get(0).getId();
        PatentAnalysis analysis = patentAnalysisDao.findByPatentIdAndType(firstId, "INVENTION_PROMPTS");
        if (analysis == null) return List.of();

        List<InventionPromptItem> items = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(analysis.getResultJson());

            JsonNode prompts = root.get("invention_prompts");
            if (prompts != null && prompts.isArray()) {
                for (JsonNode p : prompts) {
                    items.add(parseInventionPromptNode(p,
                            textOrNull(p, "category"),
                            "INVENTION"));
                }
            }

            JsonNode quickWins = root.get("quick_wins");
            if (quickWins != null && quickWins.isArray()) {
                for (JsonNode q : quickWins) {
                    items.add(parseInventionPromptNode(q, "QUICK_WIN", "QUICK_WIN"));
                }
            }

            JsonNode moonshots = root.get("moonshots");
            if (moonshots != null && moonshots.isArray()) {
                for (JsonNode m : moonshots) {
                    items.add(parseInventionPromptNode(m, "MOONSHOT", "MOONSHOT"));
                }
            }
        } catch (Exception ignored) {}

        return items;
    }

    private InventionPromptItem parseInventionPromptNode(JsonNode node, String category, String itemType) {
        String title = textOrNull(node, "title");
        String problem = textOrNull(node, "problem_statement");
        String description = textOrNull(node, "description");
        String domain = textOrNull(node, "technical_domain");
        if (category == null) category = "UNCATEGORIZED";

        List<String> sourcePatents = new ArrayList<>();
        JsonNode sp = node.get("source_patents");
        if (sp != null && sp.isArray()) {
            for (JsonNode s : sp) sourcePatents.add(s.asText());
        }

        return new InventionPromptItem(
                title != null ? title : "Untitled",
                problem != null ? problem : "",
                description != null ? description : "",
                domain != null ? domain : "",
                category,
                sourcePatents
        );
    }

    public MiningResult mineInventionPrompt(InventionPromptItem prompt, MiningProgressCallback callback) {
        long startTime = System.currentTimeMillis();

        List<String> searchTerms = deriveSearchKeywords(prompt);
        String searchLabel = prompt.title();

        if (callback != null) callback.onStatus("Searching Google Patents for: " + searchLabel + "...");

        GooglePatentsSearchService.SearchResult searchResult =
                searchService.search(searchLabel, searchTerms,
                        new GooglePatentsSearchService.SearchProgressCallback() {
                            @Override public void onStatus(String status) {
                                if (callback != null) callback.onStatus(status);
                            }
                            @Override public boolean isCancelled() {
                                return callback != null && callback.isCancelled();
                            }
                        });

        if (!searchResult.success()) {
            return new MiningResult(false, searchLabel, null, searchResult.error(),
                    System.currentTimeMillis() - startTime, 0, 0, 0.0, 0);
        }

        if (callback != null && callback.isCancelled()) {
            return new MiningResult(false, searchLabel, null, "Cancelled.",
                    System.currentTimeMillis() - startTime, 0, 0, 0.0, searchResult.patentsFound());
        }

        if (callback != null) callback.onStatus("Building analysis prompt...");

        try {
            String portfolioSummary = buildPortfolioSummary();
            String externalPatents = buildExternalPatentsText(searchLabel);

            String template = ClaudeCliService.loadPromptTemplate("patent-mining-ip");
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("invention_title", prompt.title());
            variables.put("invention_problem", prompt.problemStatement());
            variables.put("invention_description", prompt.description());
            variables.put("source_patents", String.join(", ", prompt.sourcePatents()));
            variables.put("portfolio_summary", portfolioSummary);
            variables.put("external_patents", externalPatents);

            if (callback != null) callback.onStatus("Running Claude analysis...");

            int idleTimeout = SettingsController.getIdleTimeout();
            ClaudeCliService.StreamingCallback streamCallback = callback == null ? null
                    : new ClaudeCliService.StreamingCallback() {
                @Override public void onStreamStart() { callback.onStatus("Claude is thinking..."); }
                @Override public void onTextDelta(String text) { callback.onStatus("Receiving response..."); }
                @Override public void onRetry(String message) { callback.onStatus(message); }
                @Override public boolean isCancelled() { return callback.isCancelled(); }
            };

            ClaudeCliService.AnalysisResult cliResult = claudeCliService.analyzeStreaming(
                    template, variables, idleTimeout, streamCallback);

            long totalDuration = System.currentTimeMillis() - startTime;

            if (cliResult.success() && cliResult.resultJson() != null) {
                List<Patent> patents = patentDao.findAll();
                if (!patents.isEmpty()) {
                    String analysisType = IP_ANALYSIS_TYPE_PREFIX + searchLabel;
                    PatentAnalysis pa = new PatentAnalysis();
                    pa.setPatentId(patents.get(0).getId());
                    pa.setAnalysisType(analysisType);
                    pa.setResultJson(cliResult.resultJson());
                    pa.setModelUsed(cliResult.modelUsed());
                    patentAnalysisDao.insertOrUpdate(pa);
                }

                return new MiningResult(true, searchLabel, cliResult.resultJson(), null,
                        totalDuration, cliResult.inputTokens(), cliResult.outputTokens(),
                        cliResult.costUsd(), searchResult.patentsFound());
            } else {
                return new MiningResult(false, searchLabel, null,
                        cliResult.error() != null ? cliResult.error() : "Analysis failed.",
                        totalDuration, cliResult.inputTokens(), cliResult.outputTokens(),
                        cliResult.costUsd(), searchResult.patentsFound());
            }
        } catch (IOException e) {
            return new MiningResult(false, searchLabel, null,
                    "Failed to load prompt template: " + e.getMessage(),
                    System.currentTimeMillis() - startTime, 0, 0, 0.0, searchResult.patentsFound());
        } catch (SQLException e) {
            return new MiningResult(false, searchLabel, null,
                    "Database error: " + e.getMessage(),
                    System.currentTimeMillis() - startTime, 0, 0, 0.0, searchResult.patentsFound());
        }
    }

    private List<String> deriveSearchKeywords(InventionPromptItem prompt) {
        Set<String> keywords = new LinkedHashSet<>();
        if (prompt.domain() != null && !prompt.domain().isBlank()) {
            for (String word : prompt.domain().split("\\s+")) {
                if (word.length() > 2) keywords.add(word);
            }
        }
        if (prompt.title() != null) {
            for (String word : prompt.title().split("\\s+")) {
                if (word.length() > 3 && !isStopWord(word)) {
                    keywords.add(word);
                }
            }
        }
        return List.copyOf(keywords);
    }

    private boolean isStopWord(String word) {
        return Set.of("with", "from", "that", "this", "have", "been", "will",
                "based", "using", "into", "over", "through", "between", "across",
                "under", "about", "after", "before", "during", "within", "without",
                "their", "which", "where", "there", "these", "those", "than", "then",
                "more", "most", "some", "such", "each", "every", "both", "either",
                "other", "same", "also", "only", "very").contains(word.toLowerCase());
    }

    public MiningResult mineArea(AreaOfInterest area, MiningProgressCallback callback) {
        long startTime = System.currentTimeMillis();

        if (callback != null) callback.onStatus("Searching Google Patents...");

        GooglePatentsSearchService.SearchResult searchResult =
                searchService.search(area.name(), area.keywords(),
                        new GooglePatentsSearchService.SearchProgressCallback() {
                            @Override public void onStatus(String status) {
                                if (callback != null) callback.onStatus(status);
                            }
                            @Override public boolean isCancelled() {
                                return callback != null && callback.isCancelled();
                            }
                        });

        if (!searchResult.success()) {
            return new MiningResult(false, area.name(), null, searchResult.error(),
                    System.currentTimeMillis() - startTime, 0, 0, 0.0, 0);
        }

        if (callback != null && callback.isCancelled()) {
            return new MiningResult(false, area.name(), null, "Cancelled.",
                    System.currentTimeMillis() - startTime, 0, 0, 0.0, searchResult.patentsFound());
        }

        if (callback != null) callback.onStatus("Building analysis prompt...");

        try {
            String portfolioSummary = buildPortfolioSummary();
            String externalPatents = buildExternalPatentsText(area.name());

            String template = ClaudeCliService.loadPromptTemplate("patent-mining");
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("search_area", area.name());
            variables.put("portfolio_summary", portfolioSummary);
            variables.put("external_patents", externalPatents);

            if (callback != null) callback.onStatus("Running Claude analysis...");

            int idleTimeout = SettingsController.getIdleTimeout();
            ClaudeCliService.StreamingCallback streamCallback = callback == null ? null
                    : new ClaudeCliService.StreamingCallback() {
                @Override public void onStreamStart() { callback.onStatus("Claude is thinking..."); }
                @Override public void onTextDelta(String text) { callback.onStatus("Receiving response..."); }
                @Override public void onRetry(String message) { callback.onStatus(message); }
                @Override public boolean isCancelled() { return callback.isCancelled(); }
            };

            ClaudeCliService.AnalysisResult cliResult = claudeCliService.analyzeStreaming(
                    template, variables, idleTimeout, streamCallback);

            long totalDuration = System.currentTimeMillis() - startTime;

            if (cliResult.success() && cliResult.resultJson() != null) {
                List<Patent> patents = patentDao.findAll();
                if (!patents.isEmpty()) {
                    String analysisType = ANALYSIS_TYPE_PREFIX + area.name();
                    PatentAnalysis pa = new PatentAnalysis();
                    pa.setPatentId(patents.get(0).getId());
                    pa.setAnalysisType(analysisType);
                    pa.setResultJson(cliResult.resultJson());
                    pa.setModelUsed(cliResult.modelUsed());
                    patentAnalysisDao.insertOrUpdate(pa);
                }

                return new MiningResult(true, area.name(), cliResult.resultJson(), null,
                        totalDuration, cliResult.inputTokens(), cliResult.outputTokens(),
                        cliResult.costUsd(), searchResult.patentsFound());
            } else {
                return new MiningResult(false, area.name(), null,
                        cliResult.error() != null ? cliResult.error() : "Analysis failed.",
                        totalDuration, cliResult.inputTokens(), cliResult.outputTokens(),
                        cliResult.costUsd(), searchResult.patentsFound());
            }
        } catch (IOException e) {
            return new MiningResult(false, area.name(), null,
                    "Failed to load prompt template: " + e.getMessage(),
                    System.currentTimeMillis() - startTime, 0, 0, 0.0, searchResult.patentsFound());
        } catch (SQLException e) {
            return new MiningResult(false, area.name(), null,
                    "Database error: " + e.getMessage(),
                    System.currentTimeMillis() - startTime, 0, 0, 0.0, searchResult.patentsFound());
        }
    }

    public PatentAnalysis getCachedMiningResult(String areaName) throws SQLException {
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return null;
        String analysisType = ANALYSIS_TYPE_PREFIX + areaName;
        return patentAnalysisDao.findByPatentIdAndType(patents.get(0).getId(), analysisType);
    }

    public List<String> getCachedMiningAreas() throws SQLException {
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return List.of();
        int firstId = patents.get(0).getId();

        List<PatentAnalysis> all = patentAnalysisDao.findByPatentId(firstId);
        List<String> areas = new ArrayList<>();
        for (PatentAnalysis pa : all) {
            if (pa.getAnalysisType().startsWith(ANALYSIS_TYPE_PREFIX)) {
                areas.add(pa.getAnalysisType().substring(ANALYSIS_TYPE_PREFIX.length()));
            }
        }
        return areas;
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

    public PatentAnalysis getCachedIPMiningResult(String promptTitle) throws SQLException {
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return null;
        String analysisType = IP_ANALYSIS_TYPE_PREFIX + promptTitle;
        return patentAnalysisDao.findByPatentIdAndType(patents.get(0).getId(), analysisType);
    }

    public List<String> getCachedIPMiningAreas() throws SQLException {
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return List.of();
        int firstId = patents.get(0).getId();

        List<PatentAnalysis> all = patentAnalysisDao.findByPatentId(firstId);
        List<String> areas = new ArrayList<>();
        for (PatentAnalysis pa : all) {
            if (pa.getAnalysisType().startsWith(IP_ANALYSIS_TYPE_PREFIX)) {
                areas.add(pa.getAnalysisType().substring(IP_ANALYSIS_TYPE_PREFIX.length()));
            }
        }
        return areas;
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

    private String buildPortfolioSummary() throws SQLException {
        List<Patent> patents = patentDao.findAll();
        StringBuilder sb = new StringBuilder();
        for (Patent patent : patents) {
            try {
                PatentAnalysis tech = patentAnalysisDao.findByPatentIdAndType(patent.getId(), "TECHNOLOGY");
                if (tech != null) {
                    sb.append("Patent: ").append(patent.getTitle())
                            .append(" (").append(patent.getPatentNumber() != null
                                    ? patent.getPatentNumber() : patent.getApplicationNumber())
                            .append(")\n")
                            .append(tech.getResultJson())
                            .append("\n---\n");
                }
            } catch (SQLException ignored) {}
        }
        return sb.toString();
    }

    private String buildExternalPatentsText(String searchArea) throws SQLException {
        List<MinedPatent> mined = minedPatentDao.findBySearchArea(searchArea);
        StringBuilder sb = new StringBuilder();
        for (MinedPatent mp : mined) {
            sb.append("Patent: ").append(mp.getPatentNumber())
                    .append(" — ").append(mp.getTitle());
            if (mp.getGrantDate() != null) {
                sb.append(" (").append(mp.getGrantDate()).append(")");
            }
            sb.append("\n");
            if (mp.getAbstractText() != null && !mp.getAbstractText().isBlank()) {
                String abstractText = mp.getAbstractText();
                if (abstractText.length() > 500) {
                    abstractText = abstractText.substring(0, 500) + "...";
                }
                sb.append("Abstract: ").append(abstractText);
            }
            sb.append("\n---\n");
        }
        return sb.toString();
    }

    private void extractFromAnalysis(Map<String, AreaBuilder> areas, int patentId,
                                      String analysisType, AnalysisExtractor extractor) {
        try {
            PatentAnalysis analysis = patentAnalysisDao.findByPatentIdAndType(patentId, analysisType);
            if (analysis != null) {
                JsonNode node = mapper.readTree(analysis.getResultJson());
                extractor.extract(node);
            }
        } catch (Exception ignored) {}
    }

    private void extractArrayField(Map<String, AreaBuilder> areas, JsonNode root,
                                    String arrayName, String nameField, String keywordsField,
                                    String sourceName) {
        JsonNode arr = root.get(arrayName);
        if (arr == null || !arr.isArray()) return;
        for (JsonNode item : arr) {
            String name = textOrNull(item, nameField);
            if (name == null) continue;
            AreaBuilder builder = getBuilder(areas, name);
            builder.addSource(sourceName);
            JsonNode kws = item.get(keywordsField);
            if (kws != null && kws.isArray()) {
                for (JsonNode kw : kws) {
                    if (kw.isTextual()) builder.addKeyword(kw.asText());
                }
            }
        }
    }

    private AreaBuilder getBuilder(Map<String, AreaBuilder> areas, String name) {
        String normalized = name.trim();
        String key = normalized.toLowerCase();
        return areas.computeIfAbsent(key, k -> new AreaBuilder(normalized));
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

    @FunctionalInterface
    private interface AnalysisExtractor {
        void extract(JsonNode node);
    }

    private static class AreaBuilder {
        final String name;
        final Set<String> keywords = new LinkedHashSet<>();
        final Set<String> sources = new LinkedHashSet<>();

        AreaBuilder(String name) { this.name = name; }

        void addKeyword(String kw) {
            if (kw != null && !kw.isBlank()) keywords.add(kw.trim());
        }
        void addSource(String source) {
            if (source != null) sources.add(source);
        }

        AreaOfInterest build() {
            return new AreaOfInterest(name, List.copyOf(keywords), List.copyOf(sources));
        }
    }

    public record AreaOfInterest(String name, List<String> keywords, List<String> sourceAnalyses) {
        @Override
        public String toString() { return name; }
    }

    public record InventionPromptItem(
            String title, String problemStatement, String description,
            String domain, String category, List<String> sourcePatents
    ) {
        @Override
        public String toString() {
            return "[" + category + "] " + title + (domain != null && !domain.isBlank() ? " — " + domain : "");
        }
    }

    public record MiningResult(
            boolean success, String area, String resultJson, String error,
            long durationMs, long inputTokens, long outputTokens,
            double costUsd, int externalPatentsFound
    ) {}

    public record MiningHistoryItem(
            String analysisType, String area, String resultJson,
            java.time.LocalDateTime analyzedAt, boolean isIPMining
    ) {}

    public interface MiningProgressCallback {
        void onStatus(String status);
        boolean isCancelled();
    }

    public interface BulkMiningProgressCallback extends MiningProgressCallback {
        void onPromptProgress(int current, int total, String promptTitle);
        default void onPromptComplete(String promptTitle, boolean success) {}
        default void onPromptComplete(String promptTitle, boolean success, String error) {
            onPromptComplete(promptTitle, success);
        }
        default void onPromptSkipped(String promptTitle) {}
    }

    public List<MiningHistoryItem> getAllMiningResults() throws SQLException {
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return List.of();
        int firstId = patents.get(0).getId();

        List<MiningHistoryItem> results = new ArrayList<>();

        List<PatentAnalysis> generalResults = patentAnalysisDao.findByPatentIdAndTypePrefix(firstId, ANALYSIS_TYPE_PREFIX);
        for (PatentAnalysis pa : generalResults) {
            String area = pa.getAnalysisType().substring(ANALYSIS_TYPE_PREFIX.length());
            results.add(new MiningHistoryItem(pa.getAnalysisType(), area,
                    pa.getResultJson(), pa.getAnalyzedAt(), false));
        }

        List<PatentAnalysis> ipResults = patentAnalysisDao.findByPatentIdAndTypePrefix(firstId, IP_ANALYSIS_TYPE_PREFIX);
        for (PatentAnalysis pa : ipResults) {
            String area = pa.getAnalysisType().substring(IP_ANALYSIS_TYPE_PREFIX.length());
            results.add(new MiningHistoryItem(pa.getAnalysisType(), area,
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

    private static final long BULK_THROTTLE_MS = 3000;

    public List<MiningResult> mineAllInventionPrompts(BulkMiningProgressCallback callback) throws SQLException {
        List<InventionPromptItem> prompts = extractInventionPrompts();
        if (prompts.isEmpty()) {
            if (callback != null) callback.onStatus("No invention prompts found. Run Invention Prompts analysis first.");
            return List.of();
        }

        List<MiningResult> results = new ArrayList<>();
        int total = prompts.size();
        int skipped = 0;
        boolean firstMine = true;

        for (int i = 0; i < total; i++) {
            if (callback != null && callback.isCancelled()) break;

            InventionPromptItem prompt = prompts.get(i);

            PatentAnalysis cached = getCachedIPMiningResult(prompt.title());
            if (cached != null) {
                skipped++;
                if (callback != null) {
                    callback.onPromptSkipped(prompt.title());
                }
                continue;
            }

            if (!firstMine) {
                try {
                    if (callback != null) callback.onStatus("Throttling to avoid rate limits...");
                    Thread.sleep(BULK_THROTTLE_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            firstMine = false;

            if (callback != null) {
                callback.onPromptProgress(i + 1 - skipped, total - skipped, prompt.title());
            }

            MiningResult result = mineInventionPrompt(prompt, callback);
            results.add(result);

            if (callback != null) {
                callback.onPromptComplete(prompt.title(), result.success(), result.error());
            }
        }

        return results;
    }

    public String exportAllMiningMarkdown() throws SQLException {
        List<MiningHistoryItem> history = getAllMiningResults();
        if (history.isEmpty()) return "";

        StringBuilder md = new StringBuilder();
        md.append("# Patent Mining — All Results\n\n");
        md.append("*Exported: ").append(java.time.LocalDate.now()).append("*\n\n");
        md.append("**Total results:** ").append(history.size()).append("\n\n");
        md.append("---\n\n");

        for (MiningHistoryItem item : history) {
            if (item.isIPMining()) {
                md.append(exportIPMiningMarkdown(item.area()));
            } else {
                md.append(exportMiningMarkdown(item.area()));
            }
            md.append("\n---\n\n");
        }

        return md.toString();
    }
}
