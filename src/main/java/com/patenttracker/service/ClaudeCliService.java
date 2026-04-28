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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class ClaudeCliService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 180;
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
            long[] usage = extractUsage(stdout);
            double cost = extractCost(stdout);

            return new AnalysisResult(true, resultJson, null, modelUsed, duration,
                    usage[0], usage[1], cost);

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

    private long[] extractUsage(String stdout) {
        try {
            JsonNode root = mapper.readTree(stdout);
            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode()) {
                long input = usage.path("input_tokens").asLong(0);
                long output = usage.path("output_tokens").asLong(0);
                return new long[]{input, output};
            }
        } catch (Exception ignored) {}
        return new long[]{0, 0};
    }

    private double extractCost(String stdout) {
        try {
            JsonNode root = mapper.readTree(stdout);
            if (root.has("cost_usd")) return root.get("cost_usd").asDouble(0.0);
            if (root.has("total_cost_usd")) return root.get("total_cost_usd").asDouble(0.0);
        } catch (Exception ignored) {}
        return 0.0;
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

    public AnalysisResult analyzeStreaming(String promptTemplate, Map<String, String> variables,
                                           int idleTimeoutSeconds, StreamingCallback callback) {
        long startTime = System.currentTimeMillis();

        String prompt = promptTemplate;
        for (var entry : variables.entrySet()) {
            prompt = prompt.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        String cliPath = getCliPath();

        try {
            ProcessBuilder pb = new ProcessBuilder(cliPath, "--print", "--verbose", "--output-format", "stream-json");
            pb.redirectErrorStream(false);

            Process process = pb.start();

            String finalPrompt = prompt;
            CompletableFuture.runAsync(() -> {
                try (OutputStreamWriter writer = new OutputStreamWriter(
                        process.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(finalPrompt);
                    writer.flush();
                } catch (IOException ignored) {}
            });

            StringBuilder accumulated = new StringBuilder();
            AtomicLong lastActivity = new AtomicLong(System.currentTimeMillis());
            AtomicBoolean timedOut = new AtomicBoolean(false);
            AtomicBoolean cancelled = new AtomicBoolean(false);
            String[] modelUsed = {null};
            long[] usageTokens = {0, 0};
            double[] costAccum = {0.0};

            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                } catch (IOException e) { return ""; }
            });

            Thread watchdog = new Thread(() -> {
                while (process.isAlive()) {
                    if (callback != null && callback.isCancelled()) {
                        cancelled.set(true);
                        process.destroyForcibly();
                        return;
                    }
                    long idle = System.currentTimeMillis() - lastActivity.get();
                    if (idle > idleTimeoutSeconds * 1000L) {
                        timedOut.set(true);
                        process.destroyForcibly();
                        return;
                    }
                    try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
                }
            });
            watchdog.setDaemon(true);
            watchdog.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lastActivity.set(System.currentTimeMillis());
                    if (line.isBlank()) continue;

                    try {
                        JsonNode event = mapper.readTree(line);
                        String type = event.has("type") ? event.get("type").asText() : "";

                        switch (type) {
                            case "system" -> {
                                String subtype = event.has("subtype") ? event.get("subtype").asText() : "";
                                if ("init".equals(subtype) && callback != null) {
                                    callback.onStreamStart();
                                } else if ("api_retry".equals(subtype) && callback != null) {
                                    int attempt = event.has("attempt") ? event.get("attempt").asInt() : 0;
                                    int maxRetries = event.has("max_retries") ? event.get("max_retries").asInt() : 0;
                                    String error = event.has("error") ? event.get("error").asText() : "retry";
                                    long delayMs = event.has("retry_delay_ms") ? event.get("retry_delay_ms").asLong() : 0;
                                    String msg = "API retry " + attempt + "/" + maxRetries
                                            + " (" + error + ")"
                                            + (delayMs > 0 ? " — waiting " + (delayMs / 1000) + "s" : "");
                                    callback.onRetry(msg);
                                }
                            }
                            case "assistant" -> {
                                JsonNode content = event.path("message").path("content");
                                if (content.isArray()) {
                                    for (JsonNode item : content) {
                                        if ("text".equals(item.path("type").asText())) {
                                            String text = item.path("text").asText();
                                            accumulated.append(text);
                                            if (callback != null) callback.onTextDelta(text);
                                        }
                                    }
                                }
                                String msgModel = event.path("message").path("model").asText(null);
                                if (msgModel != null) modelUsed[0] = msgModel;
                            }
                            case "result" -> {
                                if (modelUsed[0] == null) {
                                    String resultModel = event.path("model").asText(null);
                                    if (resultModel != null) modelUsed[0] = resultModel;
                                }
                                if (accumulated.isEmpty()) {
                                    String resultText = event.path("result").asText(null);
                                    if (resultText != null) accumulated.append(resultText);
                                }
                                JsonNode usage = event.path("usage");
                                if (!usage.isMissingNode()) {
                                    usageTokens[0] = usage.path("input_tokens").asLong(0);
                                    usageTokens[1] = usage.path("output_tokens").asLong(0);
                                }
                                if (event.has("cost_usd")) {
                                    costAccum[0] = event.get("cost_usd").asDouble(0.0);
                                } else if (event.has("total_cost_usd")) {
                                    costAccum[0] = event.get("total_cost_usd").asDouble(0.0);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            process.waitFor();
            watchdog.interrupt();

            long duration = System.currentTimeMillis() - startTime;

            if (cancelled.get()) {
                return new AnalysisResult(false, null, "Analysis cancelled.", null, duration);
            }

            if (timedOut.get()) {
                return new AnalysisResult(false, null,
                        "Claude CLI idle timeout: no output received for " + idleTimeoutSeconds
                                + " seconds. The model may be stuck or the prompt may be too large.",
                        null, duration);
            }

            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);

            if (process.exitValue() != 0) {
                String errorMsg = stderr.isBlank() ? "Claude CLI exited with code " + process.exitValue() : stderr;
                return new AnalysisResult(false, null, errorMsg, null, duration);
            }

            String fullText = accumulated.toString();
            if (callback != null) callback.onComplete(fullText);

            String resultJson = extractJsonFromText(fullText);
            return new AnalysisResult(true, resultJson, null, modelUsed[0], duration,
                    usageTokens[0], usageTokens[1], costAccum[0]);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new AnalysisResult(false, null, "Failed to run Claude CLI: " + msg, null, duration);
        }
    }

    public interface StreamingCallback {
        default void onStreamStart() {}
        default void onTextDelta(String text) {}
        default void onRetry(String message) {}
        default void onComplete(String fullText) {}
        default void onError(String error) {}
        default boolean isCancelled() { return false; }
    }

    public record AnalysisResult(
            boolean success, String resultJson, String error,
            String modelUsed, long durationMs,
            long inputTokens, long outputTokens, double costUsd
    ) {
        public AnalysisResult(boolean success, String resultJson, String error,
                              String modelUsed, long durationMs) {
            this(success, resultJson, error, modelUsed, durationMs, 0, 0, 0.0);
        }
    }
}
