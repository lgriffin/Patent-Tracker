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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PriorArtService {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final SearchSessionDao sessionDao;
    private final DiscoveredPatentDao discoveredPatentDao;
    private final SearchAnalysisDao analysisDao;
    private final IdeaDecomposer ideaDecomposer;
    private final PriorArtAnalyzer priorArtAnalyzer;
    private final GooglePatentsSearchService googleSearchService;
    private final PatentsViewSearchService patentsViewService;
    private final PriorArtExportService exportService;

    public PriorArtService() {
        this.sessionDao = new SearchSessionDao();
        this.discoveredPatentDao = new DiscoveredPatentDao();
        this.analysisDao = new SearchAnalysisDao();
        this.ideaDecomposer = new IdeaDecomposer();
        this.priorArtAnalyzer = new PriorArtAnalyzer();
        this.googleSearchService = new GooglePatentsSearchService();
        this.patentsViewService = new PatentsViewSearchService();
        this.exportService = new PriorArtExportService();
    }

    public PriorArtService(SearchSessionDao sessionDao, DiscoveredPatentDao discoveredPatentDao,
                            SearchAnalysisDao analysisDao, IdeaDecomposer ideaDecomposer,
                            PriorArtAnalyzer priorArtAnalyzer, GooglePatentsSearchService googleSearchService,
                            PatentsViewSearchService patentsViewService, PriorArtExportService exportService) {
        this.sessionDao = sessionDao;
        this.discoveredPatentDao = discoveredPatentDao;
        this.analysisDao = analysisDao;
        this.ideaDecomposer = ideaDecomposer;
        this.priorArtAnalyzer = priorArtAnalyzer;
        this.googleSearchService = googleSearchService;
        this.patentsViewService = patentsViewService;
        this.exportService = exportService;
    }

    public PipelineResult runFullPipeline(String ideaText, PipelineProgressCallback callback) {
        long startTime = System.currentTimeMillis();
        long totalInputTokens = 0;
        long totalOutputTokens = 0;
        double totalCost = 0.0;

        try {
            // Create session
            SearchSession session = SearchSession.builder()
                    .ideaText(ideaText)
                    .status(SearchSession.Status.PENDING.name())
                    .build();
            int sessionId = sessionDao.insert(session);

            // === Phase 1: DECOMPOSE ===
            if (callback != null) callback.onPhaseChange(1, "Decomposing idea into concepts...");
            sessionDao.updateStatus(sessionId, SearchSession.Status.DECOMPOSING.name());

            ClaudeCliService.StreamingCallback streamCb = createStreamCallback(callback);
            IdeaDecomposer.DecomposeResult decomposeResult = ideaDecomposer.decompose(ideaText, streamCb);

            if (!decomposeResult.success()) {
                sessionDao.updateStatus(sessionId, SearchSession.Status.FAILED.name());
                return new PipelineResult(false, sessionId, null, decomposeResult.error(),
                        System.currentTimeMillis() - startTime, 0, 0, 0, 0.0);
            }

            totalInputTokens += decomposeResult.inputTokens();
            totalOutputTokens += decomposeResult.outputTokens();
            totalCost += decomposeResult.costUsd();

            sessionDao.updateDecomposedJson(sessionId, decomposeResult.resultJson());
            analysisDao.insertOrUpdate(SearchAnalysis.builder()
                    .sessionId(sessionId)
                    .phase(SearchAnalysis.Phase.DECOMPOSE.name())
                    .resultJson(decomposeResult.resultJson())
                    .modelUsed(decomposeResult.modelUsed())
                    .durationMs(decomposeResult.durationMs())
                    .costUsd(decomposeResult.costUsd())
                    .build());

            if (callback != null) callback.onDecomposeComplete(decomposeResult.resultJson());

            // Extract search queries and CPC codes from decomposition
            List<String> searchQueries = extractSearchQueries(decomposeResult.resultJson());
            List<String> cpcCodes = extractCpcCodes(decomposeResult.resultJson());

            if (callback != null && callback.isCancelled()) {
                sessionDao.updateStatus(sessionId, SearchSession.Status.FAILED.name());
                return cancelledResult(sessionId, startTime, totalInputTokens, totalOutputTokens, totalCost);
            }

            // === Phase 2: DISCOVER ===
            if (callback != null) callback.onPhaseChange(2, "Searching patent databases...");
            sessionDao.updateStatus(sessionId, SearchSession.Status.SEARCHING.name());

            List<DiscoveredPatent> allPatents = new ArrayList<>();
            Set<String> seenNumbers = new LinkedHashSet<>();

            // 2a: Google Patents search
            GooglePatentsSearchService.SearchProgressCallback googleCb = callback == null ? null
                    : new GooglePatentsSearchService.SearchProgressCallback() {
                @Override public void onStatus(String status) { callback.onSearchStatus(status); }
                @Override public boolean isCancelled() { return callback.isCancelled(); }
            };

            GooglePatentsSearchService.PriorArtSearchResult googleResult =
                    googleSearchService.searchForPriorArt(searchQueries, googleCb);
            if (googleResult.success()) {
                for (DiscoveredPatent dp : googleResult.patents()) {
                    if (seenNumbers.add(dp.getPatentNumber())) {
                        allPatents.add(DiscoveredPatent.builder(dp).sessionId(sessionId).build());
                    }
                }
            }

            if (callback != null && callback.isCancelled()) {
                sessionDao.updateStatus(sessionId, SearchSession.Status.FAILED.name());
                return cancelledResult(sessionId, startTime, totalInputTokens, totalOutputTokens, totalCost);
            }

            // 2b: PatentsView search (by keywords from queries + CPC codes)
            PatentsViewSearchService.SearchProgressCallback pvCb = callback == null ? null
                    : new PatentsViewSearchService.SearchProgressCallback() {
                @Override public void onStatus(String status) { callback.onSearchStatus(status); }
                @Override public boolean isCancelled() { return callback.isCancelled(); }
            };

            List<String> pvKeywords = extractKeywordsFromQueries(searchQueries);
            if (!pvKeywords.isEmpty()) {
                PatentsViewSearchService.SearchResult pvKeywordResult =
                        patentsViewService.searchByKeywords(pvKeywords, pvCb);
                if (pvKeywordResult.success()) {
                    for (DiscoveredPatent dp : pvKeywordResult.patents()) {
                        if (seenNumbers.add(dp.getPatentNumber())) {
                            allPatents.add(DiscoveredPatent.builder(dp).sessionId(sessionId).build());
                        }
                    }
                }
            }

            if (!cpcCodes.isEmpty()) {
                PatentsViewSearchService.SearchResult pvCpcResult =
                        patentsViewService.searchByCpc(cpcCodes, pvCb);
                if (pvCpcResult.success()) {
                    for (DiscoveredPatent dp : pvCpcResult.patents()) {
                        if (seenNumbers.add(dp.getPatentNumber())) {
                            allPatents.add(DiscoveredPatent.builder(dp).sessionId(sessionId).build());
                        }
                    }
                }
            }

            // Store discovered patents
            if (!allPatents.isEmpty()) {
                discoveredPatentDao.insertBatch(allPatents);
            }

            if (callback != null) {
                callback.onSearchComplete(allPatents.size());
            }

            if (allPatents.isEmpty()) {
                sessionDao.updateStatus(sessionId, SearchSession.Status.COMPLETE.name());
                return new PipelineResult(true, sessionId, decomposeResult.resultJson(),
                        null, System.currentTimeMillis() - startTime,
                        allPatents.size(), totalInputTokens, totalOutputTokens, totalCost);
            }

            if (callback != null && callback.isCancelled()) {
                sessionDao.updateStatus(sessionId, SearchSession.Status.FAILED.name());
                return cancelledResult(sessionId, startTime, totalInputTokens, totalOutputTokens, totalCost);
            }

            // === Phase 3: ANALYZE ===
            if (callback != null) callback.onPhaseChange(3, "Analyzing overlap with prior art...");
            sessionDao.updateStatus(sessionId, SearchSession.Status.ANALYZING.name());

            PriorArtAnalyzer.AnalyzeResult analyzeResult = priorArtAnalyzer.analyzeOverlap(
                    ideaText, decomposeResult.resultJson(), allPatents, streamCb);

            if (!analyzeResult.success()) {
                sessionDao.updateStatus(sessionId, SearchSession.Status.FAILED.name());
                return new PipelineResult(false, sessionId, null, analyzeResult.error(),
                        System.currentTimeMillis() - startTime, allPatents.size(),
                        totalInputTokens, totalOutputTokens, totalCost);
            }

            totalInputTokens += analyzeResult.inputTokens();
            totalOutputTokens += analyzeResult.outputTokens();
            totalCost += analyzeResult.costUsd();

            analysisDao.insertOrUpdate(SearchAnalysis.builder()
                    .sessionId(sessionId)
                    .phase(SearchAnalysis.Phase.ANALYZE.name())
                    .resultJson(analyzeResult.resultJson())
                    .modelUsed(analyzeResult.modelUsed())
                    .durationMs(analyzeResult.durationMs())
                    .costUsd(analyzeResult.costUsd())
                    .build());

            if (callback != null && callback.isCancelled()) {
                sessionDao.updateStatus(sessionId, SearchSession.Status.FAILED.name());
                return cancelledResult(sessionId, startTime, totalInputTokens, totalOutputTokens, totalCost);
            }

            // Extract uncovered areas for differentiation
            String uncoveredAreas = extractUncoveredAreas(analyzeResult.resultJson());

            // === Phase 4: DIFFERENTIATE ===
            if (callback != null) callback.onPhaseChange(4, "Generating differentiation suggestions...");
            sessionDao.updateStatus(sessionId, SearchSession.Status.DIFFERENTIATING.name());

            PriorArtAnalyzer.AnalyzeResult diffResult = priorArtAnalyzer.differentiate(
                    ideaText, analyzeResult.resultJson(), uncoveredAreas, streamCb);

            if (!diffResult.success()) {
                sessionDao.updateStatus(sessionId, SearchSession.Status.FAILED.name());
                return new PipelineResult(false, sessionId, null, diffResult.error(),
                        System.currentTimeMillis() - startTime, allPatents.size(),
                        totalInputTokens, totalOutputTokens, totalCost);
            }

            totalInputTokens += diffResult.inputTokens();
            totalOutputTokens += diffResult.outputTokens();
            totalCost += diffResult.costUsd();

            analysisDao.insertOrUpdate(SearchAnalysis.builder()
                    .sessionId(sessionId)
                    .phase(SearchAnalysis.Phase.DIFFERENTIATE.name())
                    .resultJson(diffResult.resultJson())
                    .modelUsed(diffResult.modelUsed())
                    .durationMs(diffResult.durationMs())
                    .costUsd(diffResult.costUsd())
                    .build());

            sessionDao.updateStatus(sessionId, SearchSession.Status.COMPLETE.name());

            if (callback != null) callback.onPhaseChange(4, "Pipeline complete.");

            return new PipelineResult(true, sessionId, diffResult.resultJson(), null,
                    System.currentTimeMillis() - startTime, allPatents.size(),
                    totalInputTokens, totalOutputTokens, totalCost);

        } catch (SQLException e) {
            return new PipelineResult(false, -1, null,
                    "Database error: " + e.getMessage(),
                    System.currentTimeMillis() - startTime, 0,
                    totalInputTokens, totalOutputTokens, totalCost);
        }
    }

    // --- Session management ---

    public List<SearchSession> getSearchHistory() throws SQLException {
        return sessionDao.findAll();
    }

    public SearchSession getSession(int sessionId) throws SQLException {
        return sessionDao.findById(sessionId);
    }

    public List<DiscoveredPatent> getDiscoveredPatents(int sessionId) throws SQLException {
        return discoveredPatentDao.findBySessionId(sessionId);
    }

    public List<SearchAnalysis> getAnalyses(int sessionId) throws SQLException {
        return analysisDao.findBySessionId(sessionId);
    }

    public SearchAnalysis getAnalysisByPhase(int sessionId, String phase) throws SQLException {
        return analysisDao.findBySessionAndPhase(sessionId, phase);
    }

    public void deleteSession(int sessionId) throws SQLException {
        sessionDao.delete(sessionId);
    }

    // --- Export ---

    public String exportMarkdown(int sessionId) throws SQLException {
        return exportService.exportMarkdown(sessionId);
    }

    // --- Private helpers ---

    private List<String> extractSearchQueries(String decomposedJson) {
        List<String> queries = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(decomposedJson);
            JsonNode searchQueries = root.get("search_queries");
            if (searchQueries != null && searchQueries.isArray()) {
                for (JsonNode q : searchQueries) {
                    queries.add(q.asText());
                }
            }
        } catch (Exception ex) { }
        return queries;
    }

    private List<String> extractCpcCodes(String decomposedJson) {
        List<String> codes = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(decomposedJson);
            JsonNode classifications = root.get("cpc_classifications");
            if (classifications != null && classifications.isArray()) {
                for (JsonNode c : classifications) {
                    String code = c.has("code") ? c.get("code").asText() : null;
                    if (code != null && !code.isBlank()) {
                        codes.add(code);
                    }
                }
            }
        } catch (Exception ex) { }
        return codes;
    }

    private List<String> extractKeywordsFromQueries(List<String> queries) {
        Set<String> keywords = new LinkedHashSet<>();
        for (String query : queries) {
            for (String word : query.split("\\s+")) {
                String cleaned = word.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                if (cleaned.length() > 3 && !isStopWord(cleaned)) {
                    keywords.add(cleaned);
                    if (keywords.size() >= 8) return List.copyOf(keywords);
                }
            }
        }
        return List.copyOf(keywords);
    }

    private String extractUncoveredAreas(String analyzeJson) {
        try {
            JsonNode root = mapper.readTree(analyzeJson);
            JsonNode uncovered = root.get("uncovered_areas");
            if (uncovered != null && uncovered.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode area : uncovered) {
                    sb.append("- ").append(area.asText()).append("\n");
                }
                return sb.toString();
            }
        } catch (Exception ex) { }
        return "None identified";
    }

    private ClaudeCliService.StreamingCallback createStreamCallback(PipelineProgressCallback callback) {
        if (callback == null) return null;
        return new ClaudeCliService.StreamingCallback() {
            @Override public void onStreamStart() { callback.onSearchStatus("Claude is thinking..."); }
            @Override public void onTextDelta(String text) { callback.onSearchStatus("Receiving response..."); }
            @Override public void onRetry(String message) { callback.onSearchStatus(message); }
            @Override public boolean isCancelled() { return callback.isCancelled(); }
        };
    }

    private PipelineResult cancelledResult(int sessionId, long startTime,
                                            long inputTokens, long outputTokens, double cost) {
        return new PipelineResult(false, sessionId, null, "Cancelled.",
                System.currentTimeMillis() - startTime, 0, inputTokens, outputTokens, cost);
    }

    private boolean isStopWord(String word) {
        return Set.of("with", "from", "that", "this", "have", "been", "will",
                "based", "using", "into", "over", "through", "between", "across",
                "under", "about", "after", "before", "during", "within", "without",
                "their", "which", "where", "there", "these", "those", "than", "then",
                "more", "most", "some", "such", "each", "every", "both", "either",
                "other", "same", "also", "only", "very", "method", "system", "apparatus",
                "device", "comprising", "configured", "patent", "invention").contains(word);
    }

    // --- Result types ---

    public record PipelineResult(
            boolean success, int sessionId, String resultJson, String error,
            long durationMs, int patentsFound,
            long inputTokens, long outputTokens, double costUsd
    ) {}

    public interface PipelineProgressCallback {
        void onPhaseChange(int phase, String description);
        void onSearchStatus(String status);
        void onDecomposeComplete(String decomposedJson);
        void onSearchComplete(int patentsFound);
        boolean isCancelled();
    }
}
