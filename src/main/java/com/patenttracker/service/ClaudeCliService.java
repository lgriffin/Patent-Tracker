package com.patenttracker.service;

import com.patenttracker.controller.SettingsController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ClaudeCliService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, String> templateCache = new HashMap<>();

    public AnalysisResult analyze(String promptTemplate, Map<String, String> variables) {
        return analyze(promptTemplate, variables, DEFAULT_TIMEOUT_SECONDS);
    }

    public AnalysisResult analyze(String promptTemplate, Map<String, String> variables, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();

        String prompt = promptTemplate;
        for (var entry : variables.entrySet()) {
            prompt = prompt.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        String cliPath = getCliPath();

        try {
            ProcessBuilder pb = new ProcessBuilder(cliPath, "--print", "--output-format", "json");
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Write prompt to stdin
            String finalPrompt = prompt;
            CompletableFuture.runAsync(() -> {
                try (OutputStreamWriter writer = new OutputStreamWriter(
                        process.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(finalPrompt);
                    writer.flush();
                } catch (IOException ignored) {}
            });

            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                } catch (IOException e) { return ""; }
            });

            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                } catch (IOException e) { return ""; }
            });

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                long duration = System.currentTimeMillis() - startTime;
                return new AnalysisResult(false, null,
                        "Claude CLI timed out after " + timeoutSeconds + " seconds.",
                        null, duration);
            }

            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);

            if (process.exitValue() != 0) {
                long duration = System.currentTimeMillis() - startTime;
                String errorMsg = stderr.isBlank() ? "Claude CLI exited with code " + process.exitValue() : stderr;
                return new AnalysisResult(false, null, errorMsg, null, duration);
            }

            long duration = System.currentTimeMillis() - startTime;
            String resultJson = extractResultJson(stdout);
            String modelUsed = extractModelUsed(stdout);

            return new AnalysisResult(true, resultJson, null, modelUsed, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new AnalysisResult(false, null, "Failed to run Claude CLI: " + msg, null, duration);
        }
    }

    private String extractResultJson(String stdout) {
        try {
            JsonNode root = mapper.readTree(stdout);
            // --output-format json wraps the response in a JSON envelope
            if (root.has("result")) {
                String resultText = root.get("result").asText();
                return extractJsonFromText(resultText);
            }
            // If stdout is already the direct response
            return extractJsonFromText(stdout);
        } catch (Exception e) {
            return extractJsonFromText(stdout);
        }
    }

    private String extractJsonFromText(String text) {
        if (text == null || text.isBlank()) return text;

        String trimmed = text.trim();

        // Strip markdown code fences if present
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
            int end = trimmed.lastIndexOf("```");
            if (end > 0) trimmed = trimmed.substring(0, end);
            trimmed = trimmed.trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
            int end = trimmed.lastIndexOf("```");
            if (end > 0) trimmed = trimmed.substring(0, end);
            trimmed = trimmed.trim();
        }

        // Try to find a JSON object or array in the text
        int braceStart = trimmed.indexOf('{');
        int bracketStart = trimmed.indexOf('[');
        int start = -1;

        if (braceStart >= 0 && (bracketStart < 0 || braceStart < bracketStart)) {
            start = braceStart;
        } else if (bracketStart >= 0) {
            start = bracketStart;
        }

        if (start >= 0) {
            String candidate = trimmed.substring(start);
            try {
                mapper.readTree(candidate);
                return candidate;
            } catch (Exception ignored) {}
        }

        return trimmed;
    }

    private String extractModelUsed(String stdout) {
        try {
            JsonNode root = mapper.readTree(stdout);
            if (root.has("model")) {
                return root.get("model").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(getCliPath(), "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static String getCliPath() {
        try {
            String path = SettingsController.getClaudeCliPath();
            return (path == null || path.isBlank()) ? "claude" : path;
        } catch (Exception e) {
            return "claude";
        }
    }

    public static String loadPromptTemplate(String templateName) throws IOException {
        if (templateCache.containsKey(templateName)) {
            return templateCache.get(templateName);
        }

        String resourcePath = "/prompts/" + templateName + ".txt";
        try (var is = ClaudeCliService.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Prompt template not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String template = reader.lines().collect(Collectors.joining("\n"));
                templateCache.put(templateName, template);
                return template;
            }
        }
    }

    public record AnalysisResult(
            boolean success, String resultJson, String error,
            String modelUsed, long durationMs
    ) {}
}
