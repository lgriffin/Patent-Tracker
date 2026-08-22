package com.patenttracker.service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class IdeaDecomposer {

    private final ClaudeCliService claudeCliService;

    public IdeaDecomposer() {
        this.claudeCliService = new ClaudeCliService();
    }

    public IdeaDecomposer(ClaudeCliService claudeCliService) {
        this.claudeCliService = claudeCliService;
    }

    public DecomposeResult decompose(String ideaText, ClaudeCliService.StreamingCallback callback) {
        long startTime = System.currentTimeMillis();

        try {
            String template = ClaudeCliService.loadPromptTemplate("prior-art-decompose");
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("idea_text", ideaText);

            int idleTimeout = ConfigService.getInstance().getIdleTimeout();
            ClaudeCliService.AnalysisResult result = claudeCliService.analyzeStreaming(
                    template, variables, idleTimeout, callback);

            long duration = System.currentTimeMillis() - startTime;

            if (result.success() && result.resultJson() != null) {
                return new DecomposeResult(true, result.resultJson(), null,
                        result.modelUsed(), duration, result.inputTokens(),
                        result.outputTokens(), result.costUsd());
            } else {
                return new DecomposeResult(false, null,
                        result.error() != null ? result.error() : "Decomposition failed.",
                        result.modelUsed(), duration, result.inputTokens(),
                        result.outputTokens(), result.costUsd());
            }
        } catch (IOException e) {
            return new DecomposeResult(false, null,
                    "Failed to load prompt template: " + e.getMessage(),
                    null, System.currentTimeMillis() - startTime, 0, 0, 0.0);
        }
    }

    public record DecomposeResult(
            boolean success, String resultJson, String error,
            String modelUsed, long durationMs,
            long inputTokens, long outputTokens, double costUsd
    ) {}
}
