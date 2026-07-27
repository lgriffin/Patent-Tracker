package com.patenttracker.service;

import com.patenttracker.dao.PatentAnalysisDao;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentAnalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracts areas of interest and invention prompts from cached analysis JSON.
 */
public class AreaExtractor {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final PatentDao patentDao;
    private final PatentAnalysisDao patentAnalysisDao;

    public AreaExtractor() {
        this.patentDao = new PatentDao();
        this.patentAnalysisDao = new PatentAnalysisDao();
    }

    public AreaExtractor(PatentDao patentDao, PatentAnalysisDao patentAnalysisDao) {
        this.patentDao = patentDao;
        this.patentAnalysisDao = patentAnalysisDao;
    }

    public List<PatentMiningService.AreaOfInterest> extractAreasOfInterest() throws SQLException {
        Map<String, AreaBuilder> areas = new LinkedHashMap<>();
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return List.of();

        int firstId = patents.getFirst().getId();

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

        List<PatentMiningService.AreaOfInterest> result = new ArrayList<>();
        for (AreaBuilder builder : areas.values()) {
            result.add(builder.build());
        }

        result.sort((a, b) -> Integer.compare(b.sourceAnalyses().size(), a.sourceAnalyses().size()));
        return result;
    }

    public List<PatentMiningService.InventionPromptItem> extractInventionPrompts() throws SQLException {
        List<Patent> patents = patentDao.findAll();
        if (patents.isEmpty()) return List.of();

        int firstId = patents.getFirst().getId();
        PatentAnalysis analysis = patentAnalysisDao.findByPatentIdAndType(firstId, "INVENTION_PROMPTS");
        if (analysis == null) return List.of();

        List<PatentMiningService.InventionPromptItem> items = new ArrayList<>();
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

    private PatentMiningService.InventionPromptItem parseInventionPromptNode(JsonNode node,
                                                                             String category, String itemType) {
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

        return new PatentMiningService.InventionPromptItem(
                title != null ? title : "Untitled",
                problem != null ? problem : "",
                description != null ? description : "",
                domain != null ? domain : "",
                category,
                sourcePatents
        );
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

    String textOrNull(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull() || !val.isTextual()) return null;
        String text = val.asText().trim();
        return text.isEmpty() ? null : text;
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

        PatentMiningService.AreaOfInterest build() {
            return new PatentMiningService.AreaOfInterest(name, List.copyOf(keywords), List.copyOf(sources));
        }
    }
}
