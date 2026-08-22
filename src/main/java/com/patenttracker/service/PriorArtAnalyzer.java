package com.patenttracker.service;

import com.patenttracker.model.DiscoveredPatent;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PriorArtAnalyzer {

    private final ClaudeCliService claudeCliService;

    public PriorArtAnalyzer() {
        this.claudeCliService = new ClaudeCliService();
    }

    public PriorArtAnalyzer(ClaudeCliService claudeCliService) {
        this.claudeCliService = claudeCliService;
    }

    public AnalyzeResult analyzeOverlap(String ideaText, String decomposedJson,
                                         List<DiscoveredPatent> priorArt,
                                         ClaudeCliService.StreamingCallback callback) {
        long startTime = System.currentTimeMillis();

        try {
            String template = ClaudeCliService.loadPromptTemplate("prior-art-analyze");
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("idea_text", ideaText);
            variables.put("decomposed_concepts", decomposedJson);
            variables.put("prior_art_patents", buildPriorArtText(priorArt));

            int idleTimeout = ConfigService.getInstance().getIdleTimeout();
            ClaudeCliService.AnalysisResult result = claudeCliService.analyzeStreaming(
                    template, variables, idleTimeout, callback);

            long duration = System.currentTimeMillis() - startTime;

            if (result.success() && result.resultJson() != null) {
                return new AnalyzeResult(true, result.resultJson(), null,
                        result.modelUsed(), duration, result.inputTokens(),
                        result.outputTokens(), result.costUsd());
            } else {
                return new AnalyzeResult(false, null,
                        result.error() != null ? result.error() : "Analysis failed.",
                        result.modelUsed(), duration, result.inputTokens(),
                        result.outputTokens(), result.costUsd());
            }
        } catch (IOException e) {
            return new AnalyzeResult(false, null,
                    "Failed to load prompt template: " + e.getMessage(),
                    null, System.currentTimeMillis() - startTime, 0, 0, 0.0);
        }
    }

    public AnalyzeResult differentiate(String ideaText, String overlapJson,
                                        String uncoveredAreas,
                                        ClaudeCliService.StreamingCallback callback) {
        long startTime = System.currentTimeMillis();

        try {
            String template = ClaudeCliService.loadPromptTemplate("prior-art-differentiate");
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("idea_text", ideaText);
            variables.put("overlap_analysis", overlapJson);
            variables.put("uncovered_areas", uncoveredAreas != null ? uncoveredAreas : "None identified");

            int idleTimeout = ConfigService.getInstance().getIdleTimeout();
            ClaudeCliService.AnalysisResult result = claudeCliService.analyzeStreaming(
                    template, variables, idleTimeout, callback);

            long duration = System.currentTimeMillis() - startTime;

            if (result.success() && result.resultJson() != null) {
                return new AnalyzeResult(true, result.resultJson(), null,
                        result.modelUsed(), duration, result.inputTokens(),
                        result.outputTokens(), result.costUsd());
            } else {
                return new AnalyzeResult(false, null,
                        result.error() != null ? result.error() : "Differentiation failed.",
                        result.modelUsed(), duration, result.inputTokens(),
                        result.outputTokens(), result.costUsd());
            }
        } catch (IOException e) {
            return new AnalyzeResult(false, null,
                    "Failed to load prompt template: " + e.getMessage(),
                    null, System.currentTimeMillis() - startTime, 0, 0, 0.0);
        }
    }

    private String buildPriorArtText(List<DiscoveredPatent> patents) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(patents.size(), 30);
        for (int i = 0; i < limit; i++) {
            DiscoveredPatent dp = patents.get(i);
            sb.append(i + 1).append(". Patent: ").append(dp.getPatentNumber());
            sb.append(" -- ").append(dp.getTitle());
            if (dp.getAssignee() != null) {
                sb.append(" (").append(dp.getAssignee()).append(")");
            }
            if (dp.getGrantDate() != null) {
                sb.append(" [").append(dp.getGrantDate()).append("]");
            }
            sb.append("\n");
            if (dp.getAbstractText() != null && !dp.getAbstractText().isBlank()) {
                String abstractText = dp.getAbstractText();
                if (abstractText.length() > 400) {
                    abstractText = abstractText.substring(0, 400) + "...";
                }
                sb.append("   Abstract: ").append(abstractText);
            }
            if (dp.getCpcCodes() != null) {
                sb.append("\n   CPC: ").append(dp.getCpcCodes());
            }
            sb.append("\n---\n");
        }
        if (patents.size() > limit) {
            sb.append("(").append(patents.size() - limit).append(" additional patents omitted)\n");
        }
        return sb.toString();
    }

    public record AnalyzeResult(
            boolean success, String resultJson, String error,
            String modelUsed, long durationMs,
            long inputTokens, long outputTokens, double costUsd
    ) {}
}
