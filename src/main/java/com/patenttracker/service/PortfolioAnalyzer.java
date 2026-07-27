package com.patenttracker.service;

import com.patenttracker.dao.PatentAnalysisDao;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentAnalysis;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class PortfolioAnalyzer {

    private static final Path LOG_DIR = Path.of(System.getProperty("user.home"), ".patenttracker", "logs");
    private static final DateTimeFormatter LOG_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PatentDao patentDao;
    private final PatentAnalysisDao patentAnalysisDao;
    private final ClaudeCliService claudeCliService;
    private final SinglePatentAnalyzer singlePatentAnalyzer;

    public PortfolioAnalyzer() {
        this.patentDao = new PatentDao();
        this.patentAnalysisDao = new PatentAnalysisDao();
        this.claudeCliService = new ClaudeCliService();
        this.singlePatentAnalyzer = new SinglePatentAnalyzer();
    }

    public PortfolioAnalyzer(PatentDao patentDao, PatentAnalysisDao patentAnalysisDao,
                             ClaudeCliService claudeCliService, SinglePatentAnalyzer singlePatentAnalyzer) {
        this.patentDao = patentDao;
        this.patentAnalysisDao = patentAnalysisDao;
        this.claudeCliService = claudeCliService;
        this.singlePatentAnalyzer = singlePatentAnalyzer;
    }

    record PatentTechPair(Patent patent, String techJson) {}

    public InsightService.InsightResult analyzeWhitespace(List<Patent> patents) {
        return analyzeWhitespace(patents, null);
    }

    public InsightService.InsightResult analyzeWhitespace(List<Patent> patents,
                                                          InsightService.CrossPatentProgressCallback callback) {
        return runCrossPatentAnalysis(patents, "WHITESPACE", "whitespace", callback);
    }

    public InsightService.InsightResult analyzeClustering(List<Patent> patents) {
        return analyzeClustering(patents, null);
    }

    public InsightService.InsightResult analyzeClustering(List<Patent> patents,
                                                          InsightService.CrossPatentProgressCallback callback) {
        return runCrossPatentAnalysis(patents, "CLUSTERING", "clustering", callback);
    }

    public InsightService.InsightResult analyzeAdjacency(List<Patent> patents) {
        return analyzeAdjacency(patents, null);
    }

    public InsightService.InsightResult analyzeAdjacency(List<Patent> patents,
                                                         InsightService.CrossPatentProgressCallback callback) {
        return runCrossPatentAnalysis(patents, "ADJACENCY", "adjacency", callback);
    }

    public InsightService.InsightResult analyzeClaimCollision(List<Patent> patents) {
        return analyzeClaimCollision(patents, null);
    }

    public InsightService.InsightResult analyzeClaimCollision(List<Patent> patents,
                                                              InsightService.CrossPatentProgressCallback callback) {
        return runCrossPatentAnalysis(patents, "CLAIM_COLLISION", "claim-collision", callback);
    }

    public InsightService.InsightResult analyzeCompetitorGaps(List<Patent> patents) {
        return analyzeCompetitorGaps(patents, null);
    }

    public InsightService.InsightResult analyzeCompetitorGaps(List<Patent> patents,
                                                              InsightService.CrossPatentProgressCallback callback) {
        return runCrossPatentAnalysis(patents, "COMPETITOR_GAPS", "competitor-gaps", callback);
    }

    public InsightService.InsightResult analyzeCrossDomain(List<Patent> patents) {
        return analyzeCrossDomain(patents, null);
    }

    public InsightService.InsightResult analyzeCrossDomain(List<Patent> patents,
                                                           InsightService.CrossPatentProgressCallback callback) {
        return runCrossPatentAnalysis(patents, "CROSS_DOMAIN", "cross-domain", callback);
    }

    public InsightService.InsightResult analyzeTemporalTrends(List<Patent> patents) {
        return analyzeTemporalTrends(patents, null);
    }

    public InsightService.InsightResult analyzeTemporalTrends(List<Patent> patents,
                                                              InsightService.CrossPatentProgressCallback callback) {
        Function<List<PatentTechPair>, String> summaryBuilder = pairs -> {
            StringBuilder sb = new StringBuilder();
            for (PatentTechPair pair : pairs) {
                Patent p = pair.patent();
                LocalDate filed = p.getFilingDate();
                sb.append("Patent: ").append(p.getTitle())
                        .append(" (").append(p.getPatentNumber() != null ?
                                p.getPatentNumber() : p.getApplicationNumber())
                        .append(")")
                        .append(" | Filed: ").append(filed != null ? filed.format(DateTimeFormatter.ISO_LOCAL_DATE) : "unknown")
                        .append("\n")
                        .append(pair.techJson())
                        .append("\n---\n");
            }
            return sb.toString();
        };

        List<PatentTechPair> pairs = buildPatentTechPairs(patents, false);
        pairs.sort(Comparator.comparing(p -> p.patent().getFilingDate() != null
                ? p.patent().getFilingDate() : LocalDate.MAX));

        return runChunkedOrDirect(pairs, patents, "TEMPORAL_TRENDS", "temporal-trends",
                summaryBuilder, Map.of(), callback);
    }

    public InsightService.InsightResult analyzeSeedSynthesis(List<Patent> patents) {
        return analyzeSeedSynthesis(patents, null);
    }

    public InsightService.InsightResult analyzeSeedSynthesis(List<Patent> patents,
                                                              InsightService.CrossPatentProgressCallback callback) {
        List<PatentTechPair> pairs = buildPatentSeedPairs(patents);
        if (pairs.size() < 2) {
            return new InsightService.InsightResult(false, "SEED_SYNTHESIS", null,
                    "Need at least 2 patents with Idea Seeds analysis. Run 'Run Idea Seeds' first. Found: " + pairs.size(), 0);
        }
        return runChunkedOrDirect(pairs, patents, "SEED_SYNTHESIS", "seed-synthesis",
                buildPlainSummary(), Map.of(), callback);
    }

    public InsightService.InsightResult analyzeInventionPrompts(List<Patent> patents) {
        return analyzeInventionPrompts(patents, null);
    }

    public InsightService.InsightResult analyzeInventionPrompts(List<Patent> patents,
                                                                InsightService.CrossPatentProgressCallback callback) {
        List<PatentTechPair> pairs = buildPatentTechPairs(patents, false);

        StringBuilder additionalContext = new StringBuilder();
        try {
            if (!patents.isEmpty()) {
                PatentAnalysis whitespace = patentAnalysisDao.findByPatentIdAndType(
                        patents.getFirst().getId(), "WHITESPACE");
                if (whitespace != null) {
                    additionalContext.append("\nWhitespace Analysis Results:\n")
                            .append(whitespace.getResultJson()).append("\n");
                }
                PatentAnalysis clustering = patentAnalysisDao.findByPatentIdAndType(
                        patents.getFirst().getId(), "CLUSTERING");
                if (clustering != null) {
                    additionalContext.append("\nClustering Analysis Results:\n")
                            .append(clustering.getResultJson()).append("\n");
                }
            }
        } catch (SQLException ignored) {}

        Function<List<PatentTechPair>, String> summaryBuilder = buildPlainSummary();
        Map<String, String> extraVars = Map.of("additional_context", additionalContext.toString());

        return runChunkedOrDirect(pairs, patents, "INVENTION_PROMPTS", "invention-prompts",
                summaryBuilder, extraVars, callback);
    }

    List<PatentTechPair> buildPatentSeedPairs(List<Patent> patents) {
        List<PatentTechPair> pairs = new ArrayList<>();
        for (Patent patent : patents) {
            try {
                PatentAnalysis seedAnalysis = patentAnalysisDao.findByPatentIdAndType(
                        patent.getId(), "IDEA_SEEDS");
                if (seedAnalysis != null) {
                    pairs.add(new PatentTechPair(patent, seedAnalysis.getResultJson()));
                }
            } catch (SQLException e) {
                // Skip
            }
        }
        return pairs;
    }

    private Function<List<PatentTechPair>, String> buildPlainSummary() {
        return pairs -> {
            StringBuilder sb = new StringBuilder();
            for (PatentTechPair pair : pairs) {
                Patent p = pair.patent();
                sb.append("Patent: ").append(p.getTitle())
                        .append(" (").append(p.getPatentNumber() != null ?
                                p.getPatentNumber() : p.getApplicationNumber())
                        .append(")\n")
                        .append(pair.techJson())
                        .append("\n---\n");
            }
            return sb.toString();
        };
    }

    List<PatentTechPair> buildPatentTechPairs(List<Patent> patents, boolean autoAnalyze) {
        List<PatentTechPair> pairs = new ArrayList<>();
        for (Patent patent : patents) {
            try {
                PatentAnalysis techAnalysis = patentAnalysisDao.findByPatentIdAndType(
                        patent.getId(), "TECHNOLOGY");
                if (techAnalysis == null && autoAnalyze) {
                    InsightService.InsightResult techResult = singlePatentAnalyzer.analyzeTechnology(patent);
                    if (techResult.success()) {
                        techAnalysis = patentAnalysisDao.findByPatentIdAndType(patent.getId(), "TECHNOLOGY");
                    }
                }
                if (techAnalysis != null) {
                    pairs.add(new PatentTechPair(patent, techAnalysis.getResultJson()));
                }
            } catch (SQLException e) {
                // Skip
            }
        }
        return pairs;
    }

    private InsightService.InsightResult runCrossPatentAnalysis(List<Patent> patents, String analysisType,
                                                                String templateName,
                                                                InsightService.CrossPatentProgressCallback callback) {
        List<PatentTechPair> pairs = buildPatentTechPairs(patents, true);
        return runChunkedOrDirect(pairs, patents, analysisType, templateName,
                buildPlainSummary(), Map.of(), callback);
    }

    private InsightService.InsightResult runChunkedOrDirect(List<PatentTechPair> pairs, List<Patent> allPatents,
                                                            String analysisType, String templateName,
                                                            Function<List<PatentTechPair>, String> summaryBuilder,
                                                            Map<String, String> extraVariables,
                                                            InsightService.CrossPatentProgressCallback callback) {
        if (pairs.size() < 2) {
            return new InsightService.InsightResult(false, analysisType, null,
                    "Need at least 2 patents with technology extraction. Found: " + pairs.size(), 0);
        }

        int batchSize = ConfigService.getInstance().getBatchSize();
        int idleTimeout = ConfigService.getInstance().getIdleTimeout();

        ClaudeCliService.StreamingCallback streamCallback = callback == null ? null
                : new ClaudeCliService.StreamingCallback() {
            @Override public void onStreamStart() { callback.onStreamingStatus("Claude is thinking..."); }
            @Override public void onTextDelta(String text) { callback.onStreamingStatus("Receiving response..."); }
            @Override public void onRetry(String message) { callback.onStreamingStatus(message); }
            @Override public boolean isCancelled() { return callback.isCancelled(); }
        };

        try {
            String template = ClaudeCliService.loadPromptTemplate(templateName);

            if (pairs.size() <= batchSize) {
                String summaries = summaryBuilder.apply(pairs);
                Map<String, String> variables = new LinkedHashMap<>();
                variables.put("portfolio_summaries", summaries);
                variables.putAll(extraVariables);

                ClaudeCliService.AnalysisResult cliResult = claudeCliService.analyzeStreaming(
                        template, variables, idleTimeout, streamCallback);

                return storeAndReturn(cliResult, allPatents, analysisType);
            }

            List<List<PatentTechPair>> chunks = partition(pairs, batchSize);
            List<String> chunkResults = new ArrayList<>();
            long totalDuration = 0;
            long totalInput = 0, totalOutput = 0;
            double totalCost = 0.0;

            log(analysisType, "Starting chunked analysis: " + pairs.size() + " patents in "
                    + chunks.size() + " chunks (batch size " + batchSize + ")");

            for (int i = 0; i < chunks.size(); i++) {
                if (callback != null && callback.isCancelled()) {
                    log(analysisType, "Cancelled by user during chunk " + (i + 1));
                    return new InsightService.InsightResult(false, analysisType, null, "Analysis cancelled.", totalDuration);
                }

                List<PatentTechPair> chunk = chunks.get(i);
                if (chunk.size() < 2) continue;

                if (callback != null) callback.onChunkProgress(i + 1, chunks.size());

                String summaries = summaryBuilder.apply(chunk);
                Map<String, String> variables = new LinkedHashMap<>();
                variables.put("portfolio_summaries", summaries);
                variables.putAll(extraVariables);

                log(analysisType, "Chunk " + (i + 1) + "/" + chunks.size()
                        + ": " + chunk.size() + " patents, prompt ~" + summaries.length() + " chars");

                ClaudeCliService.AnalysisResult chunkResult = claudeCliService.analyzeStreaming(
                        template, variables, idleTimeout, streamCallback);
                totalDuration += chunkResult.durationMs();
                totalInput += chunkResult.inputTokens();
                totalOutput += chunkResult.outputTokens();
                totalCost += chunkResult.costUsd();

                if (chunkResult.success() && chunkResult.resultJson() != null) {
                    chunkResults.add(chunkResult.resultJson());
                    log(analysisType, "Chunk " + (i + 1) + " succeeded: "
                            + chunkResult.resultJson().length() + " chars, "
                            + chunkResult.durationMs() + "ms");
                } else {
                    log(analysisType, "Chunk " + (i + 1) + " FAILED: " + chunkResult.error());
                }
            }

            log(analysisType, "Chunk phase complete: " + chunkResults.size() + "/"
                    + chunks.size() + " chunks succeeded");

            if (chunkResults.isEmpty()) {
                log(analysisType, "All chunks failed, no results to merge");
                return new InsightService.InsightResult(false, analysisType, null,
                        "All chunks failed to produce results.", totalDuration);
            }

            if (chunkResults.size() == 1) {
                log(analysisType, "Single chunk result, skipping merge");
                ClaudeCliService.AnalysisResult singleResult = new ClaudeCliService.AnalysisResult(
                        true, chunkResults.getFirst(), null, null, totalDuration,
                        totalInput, totalOutput, totalCost);
                return storeAndReturn(singleResult, allPatents, analysisType);
            }

            String mergeTemplateName = templateName + "-merge";
            String mergeTemplate = ClaudeCliService.loadPromptTemplate(mergeTemplateName);
            int mergeIdleTimeout = idleTimeout * 3;

            List<String> toMerge = new ArrayList<>(chunkResults);
            String bestResult = chunkResults.stream()
                    .max(Comparator.comparingInt(String::length)).orElse(chunkResults.getFirst());
            int mergeRound = 0;

            log(analysisType, "Starting hierarchical merge of " + toMerge.size()
                    + " chunks (idle timeout " + mergeIdleTimeout + "s)");

            while (toMerge.size() > 1) {
                mergeRound++;
                if (callback != null) callback.onMergeProgress();

                List<List<String>> mergeGroups = partition(toMerge, 3);
                List<String> mergedResults = new ArrayList<>();
                boolean mergeFailedThisRound = false;

                log(analysisType, "Merge round " + mergeRound + ": " + toMerge.size()
                        + " items -> " + mergeGroups.size() + " groups");

                for (int g = 0; g < mergeGroups.size(); g++) {
                    if (callback != null && callback.isCancelled()) {
                        log(analysisType, "Cancelled by user during merge round " + mergeRound);
                        return new InsightService.InsightResult(false, analysisType, null, "Analysis cancelled.", totalDuration);
                    }

                    List<String> group = mergeGroups.get(g);

                    if (group.size() == 1) {
                        mergedResults.add(group.getFirst());
                        continue;
                    }

                    if (callback != null) {
                        callback.onStreamingStatus("Merging round " + mergeRound
                                + " (" + (g + 1) + "/" + mergeGroups.size() + ")...");
                    }

                    String combinedChunks = String.join("\n---CHUNK---\n", group);
                    int promptLen = mergeTemplate.length() + combinedChunks.length();
                    log(analysisType, "Merge round " + mergeRound + " group " + (g + 1)
                            + ": " + group.size() + " items, prompt ~" + promptLen + " chars");

                    Map<String, String> mergeVariables = new LinkedHashMap<>();
                    mergeVariables.put("chunk_results", combinedChunks);

                    ClaudeCliService.AnalysisResult mergeResult = claudeCliService.analyzeStreaming(
                            mergeTemplate, mergeVariables, mergeIdleTimeout, streamCallback);
                    totalDuration += mergeResult.durationMs();
                    totalInput += mergeResult.inputTokens();
                    totalOutput += mergeResult.outputTokens();
                    totalCost += mergeResult.costUsd();

                    if (mergeResult.success() && mergeResult.resultJson() != null) {
                        mergedResults.add(mergeResult.resultJson());
                        bestResult = mergeResult.resultJson();
                        log(analysisType, "Merge round " + mergeRound + " group " + (g + 1)
                                + " succeeded: " + mergeResult.resultJson().length() + " chars, "
                                + mergeResult.durationMs() + "ms");
                    } else {
                        log(analysisType, "Merge round " + mergeRound + " group " + (g + 1)
                                + " FAILED: " + mergeResult.error());
                        mergeFailedThisRound = true;
                        for (String item : group) {
                            mergedResults.add(item);
                        }
                    }
                }

                toMerge = mergedResults;

                if (mergeFailedThisRound && toMerge.size() > 1) {
                    log(analysisType, "Merge had failures in round " + mergeRound
                            + ", falling back to best available result ("
                            + bestResult.length() + " chars)");
                    ClaudeCliService.AnalysisResult fallbackResult = new ClaudeCliService.AnalysisResult(
                            true, bestResult, null, null, totalDuration,
                            totalInput, totalOutput, totalCost);
                    return storeAndReturn(fallbackResult, allPatents, analysisType);
                }
            }

            log(analysisType, "Merge complete after " + mergeRound + " rounds, final result: "
                    + toMerge.getFirst().length() + " chars");

            ClaudeCliService.AnalysisResult finalResult = new ClaudeCliService.AnalysisResult(
                    true, toMerge.getFirst(), null, null, totalDuration,
                    totalInput, totalOutput, totalCost);
            return storeAndReturn(finalResult, allPatents, analysisType);

        } catch (IOException e) {
            return new InsightService.InsightResult(false, analysisType, null,
                    "Failed to load prompt template: " + e.getMessage(), 0);
        } catch (SQLException e) {
            return new InsightService.InsightResult(false, analysisType, null,
                    "Database error: " + e.getMessage(), 0);
        }
    }

    private InsightService.InsightResult storeAndReturn(ClaudeCliService.AnalysisResult cliResult,
                                                        List<Patent> patents, String analysisType) throws SQLException {
        if (cliResult.success() && cliResult.resultJson() != null) {
            PatentAnalysis pa = PatentAnalysis.builder()
                    .patentId(patents.getFirst().getId())
                    .analysisType(analysisType)
                    .resultJson(cliResult.resultJson())
                    .modelUsed(cliResult.modelUsed())
                    .build();
            patentAnalysisDao.insertOrUpdate(pa);
            return new InsightService.InsightResult(true, analysisType, cliResult.resultJson(), null,
                    cliResult.durationMs(), cliResult.inputTokens(), cliResult.outputTokens(),
                    cliResult.costUsd());
        } else {
            return new InsightService.InsightResult(false, analysisType, null, cliResult.error(), cliResult.durationMs());
        }
    }

    static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    private static void log(String analysisType, String message) {
        try {
            Files.createDirectories(LOG_DIR);
            Path logFile = LOG_DIR.resolve("insight-analysis.log");
            try (PrintWriter pw = new PrintWriter(new FileWriter(logFile.toFile(), true))) {
                pw.println(LocalDateTime.now().format(LOG_TS) + " [" + analysisType + "] " + message);
            }
        } catch (IOException ignored) {}
    }
}
