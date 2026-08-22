package com.patenttracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patenttracker.dao.DiscoveredPatentDao;
import com.patenttracker.dao.SearchAnalysisDao;
import com.patenttracker.dao.SearchSessionDao;
import com.patenttracker.model.DiscoveredPatent;
import com.patenttracker.model.SearchAnalysis;
import com.patenttracker.model.SearchSession;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PriorArtExportService {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SearchSessionDao sessionDao;
    private final DiscoveredPatentDao discoveredPatentDao;
    private final SearchAnalysisDao analysisDao;

    public PriorArtExportService() {
        this.sessionDao = new SearchSessionDao();
        this.discoveredPatentDao = new DiscoveredPatentDao();
        this.analysisDao = new SearchAnalysisDao();
    }

    public PriorArtExportService(SearchSessionDao sessionDao, DiscoveredPatentDao discoveredPatentDao,
                                  SearchAnalysisDao analysisDao) {
        this.sessionDao = sessionDao;
        this.discoveredPatentDao = discoveredPatentDao;
        this.analysisDao = analysisDao;
    }

    public String exportMarkdown(int sessionId) throws SQLException {
        SearchSession session = sessionDao.findById(sessionId);
        if (session == null) return "Session not found.";

        List<DiscoveredPatent> patents = discoveredPatentDao.findBySessionId(sessionId);
        List<SearchAnalysis> analyses = analysisDao.findBySessionId(sessionId);

        StringBuilder md = new StringBuilder();
        md.append("# Prior Art Search Report\n\n");
        md.append("**Date**: ").append(session.getCreatedAt() != null
                ? session.getCreatedAt().format(DT_FMT) : "Unknown").append("\n");
        md.append("**Status**: ").append(session.getStatus()).append("\n\n");

        md.append("## Invention Idea\n\n");
        md.append(session.getIdeaText()).append("\n\n");

        // Decomposition
        SearchAnalysis decompose = findByPhase(analyses, "DECOMPOSE");
        if (decompose != null) {
            md.append("## Idea Decomposition\n\n");
            appendDecomposeSection(md, decompose.getResultJson());
        }

        // Discovered Patents
        md.append("## Discovered Prior Art (").append(patents.size()).append(" patents)\n\n");
        if (!patents.isEmpty()) {
            md.append("| # | Patent | Title | Source | Date |\n");
            md.append("|---|--------|-------|--------|------|\n");
            for (int i = 0; i < patents.size(); i++) {
                DiscoveredPatent dp = patents.get(i);
                md.append("| ").append(i + 1)
                  .append(" | ").append(dp.getPatentNumber())
                  .append(" | ").append(dp.getTitle() != null ? dp.getTitle() : "")
                  .append(" | ").append(dp.getSource())
                  .append(" | ").append(dp.getGrantDate() != null ? dp.getGrantDate().toString() : "")
                  .append(" |\n");
            }
            md.append("\n");
        }

        // Overlap Analysis
        SearchAnalysis analyze = findByPhase(analyses, "ANALYZE");
        if (analyze != null) {
            md.append("## Overlap Analysis\n\n");
            appendAnalyzeSection(md, analyze.getResultJson());
        }

        // Differentiation Suggestions
        SearchAnalysis diff = findByPhase(analyses, "DIFFERENTIATE");
        if (diff != null) {
            md.append("## Differentiation Suggestions\n\n");
            appendDifferentiateSection(md, diff.getResultJson());
        }

        // Cost summary
        double totalCost = analyses.stream().mapToDouble(SearchAnalysis::getCostUsd).sum();
        long totalDuration = analyses.stream().mapToLong(SearchAnalysis::getDurationMs).sum();
        if (totalCost > 0 || totalDuration > 0) {
            md.append("---\n\n");
            md.append("**Analysis Cost**: $").append(String.format("%.4f", totalCost));
            md.append(" | **Duration**: ").append(totalDuration / 1000).append("s\n");
        }

        return md.toString();
    }

    private void appendDecomposeSection(StringBuilder md, String json) {
        try {
            JsonNode root = mapper.readTree(json);

            appendField(md, root, "idea_summary", "### Summary\n\n");

            JsonNode concepts = root.get("key_concepts");
            if (concepts != null && concepts.isArray()) {
                md.append("### Key Concepts\n\n");
                for (JsonNode c : concepts) {
                    md.append("- **").append(textOf(c, "concept")).append("**: ");
                    md.append(textOf(c, "description")).append("\n");
                }
                md.append("\n");
            }

            JsonNode claims = root.get("potential_claims");
            if (claims != null && claims.isArray()) {
                md.append("### Potential Claims\n\n");
                int num = 1;
                for (JsonNode c : claims) {
                    md.append(num++).append(". ").append(c.asText()).append("\n");
                }
                md.append("\n");
            }

            appendField(md, root, "novelty_hypothesis", "### Novelty Hypothesis\n\n");

        } catch (Exception e) {
            md.append("```json\n").append(json).append("\n```\n\n");
        }
    }

    private void appendAnalyzeSection(StringBuilder md, String json) {
        try {
            JsonNode root = mapper.readTree(json);

            JsonNode assessment = root.get("overlap_assessment");
            if (assessment != null) {
                md.append("**Overall Risk**: ").append(textOf(assessment, "overall_risk")).append("\n\n");
                md.append(textOf(assessment, "summary")).append("\n\n");
            }

            JsonNode overlaps = root.get("concept_overlaps");
            if (overlaps != null && overlaps.isArray()) {
                md.append("### Concept Overlap Details\n\n");
                for (JsonNode o : overlaps) {
                    md.append("#### ").append(textOf(o, "concept"))
                      .append(" — ").append(textOf(o, "overlap_level")).append("\n\n");
                    JsonNode patents = o.get("overlapping_patents");
                    if (patents != null && patents.isArray()) {
                        for (JsonNode p : patents) {
                            md.append("- ").append(textOf(p, "patent_number"))
                              .append(": ").append(textOf(p, "overlap_description")).append("\n");
                        }
                    }
                    String gap = textOf(o, "gap_description");
                    if (gap != null) {
                        md.append("\n*Gap*: ").append(gap).append("\n");
                    }
                    md.append("\n");
                }
            }

            JsonNode uncovered = root.get("uncovered_areas");
            if (uncovered != null && uncovered.isArray() && !uncovered.isEmpty()) {
                md.append("### Uncovered Areas\n\n");
                for (JsonNode u : uncovered) {
                    md.append("- ").append(u.asText()).append("\n");
                }
                md.append("\n");
            }
        } catch (Exception e) {
            md.append("```json\n").append(json).append("\n```\n\n");
        }
    }

    private void appendDifferentiateSection(StringBuilder md, String json) {
        try {
            JsonNode root = mapper.readTree(json);

            appendField(md, root, "differentiation_strategy", "### Strategy\n\n");

            JsonNode suggestions = root.get("suggestions");
            if (suggestions != null && suggestions.isArray()) {
                md.append("### Suggestions\n\n");
                int num = 1;
                for (JsonNode s : suggestions) {
                    String strength = textOf(s, "strength");
                    md.append(num++).append(". **[").append(strength).append("]** ");
                    md.append(textOf(s, "title")).append("\n\n");
                    md.append("   ").append(textOf(s, "description")).append("\n\n");
                    String novelty = textOf(s, "novelty_argument");
                    if (novelty != null) {
                        md.append("   *Novelty*: ").append(novelty).append("\n\n");
                    }
                    String claim = textOf(s, "claim_language_hint");
                    if (claim != null) {
                        md.append("   *Claim hint*: ").append(claim).append("\n\n");
                    }
                }
            }

            JsonNode focus = root.get("recommended_claim_focus");
            if (focus != null && focus.isArray() && !focus.isEmpty()) {
                md.append("### Recommended Claim Focus\n\n");
                for (JsonNode f : focus) {
                    md.append("- ").append(f.asText()).append("\n");
                }
                md.append("\n");
            }

            JsonNode nextSteps = root.get("next_steps");
            if (nextSteps != null && nextSteps.isArray() && !nextSteps.isEmpty()) {
                md.append("### Next Steps\n\n");
                for (JsonNode n : nextSteps) {
                    md.append("- ").append(n.asText()).append("\n");
                }
                md.append("\n");
            }
        } catch (Exception e) {
            md.append("```json\n").append(json).append("\n```\n\n");
        }
    }

    private void appendField(StringBuilder md, JsonNode root, String field, String header) {
        String value = textOf(root, field);
        if (value != null) {
            md.append(header).append(value).append("\n\n");
        }
    }

    private String textOf(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        return val.asText();
    }

    private SearchAnalysis findByPhase(List<SearchAnalysis> analyses, String phase) {
        return analyses.stream()
                .filter(a -> phase.equals(a.getPhase()))
                .findFirst()
                .orElse(null);
    }
}
