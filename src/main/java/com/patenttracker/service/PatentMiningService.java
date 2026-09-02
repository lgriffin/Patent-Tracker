package com.patenttracker.service;

import com.patenttracker.dao.MinedPatentDao;
import com.patenttracker.dao.PatentAnalysisDao;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.model.MinedPatent;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentAnalysis;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Facade for patent mining operations.
 * Delegates extraction to AreaExtractor and exports to MiningExportService.
 */
public class PatentMiningService {

    static final String ANALYSIS_TYPE_PREFIX = "PATENT_MINING:";
    static final String IP_ANALYSIS_TYPE_PREFIX = "PATENT_MINING_IP:";
    private static final long BULK_THROTTLE_MS = 10000;

    private final PatentDao patentDao;
    private final PatentAnalysisDao patentAnalysisDao;
    private final MinedPatentDao minedPatentDao;
    private final GooglePatentsSearchService searchService;
    private final ClaudeCliService claudeCliService;
    private final AreaExtractor areaExtractor;
    private final MiningExportService miningExportService;

    public PatentMiningService() {
        this.patentDao = new PatentDao();
        this.patentAnalysisDao = new PatentAnalysisDao();
        this.minedPatentDao = new MinedPatentDao();
        this.searchService = new GooglePatentsSearchService();
        this.claudeCliService = new ClaudeCliService();
        this.areaExtractor = new AreaExtractor();
        this.miningExportService = new MiningExportService();
    }

    public PatentMiningService(PatentDao patentDao, PatentAnalysisDao patentAnalysisDao,
                               MinedPatentDao minedPatentDao, GooglePatentsSearchService searchService,
                               ClaudeCliService claudeCliService, AreaExtractor areaExtractor,
                               MiningExportService miningExportService) {
        this.patentDao = patentDao;
        this.patentAnalysisDao = patentAnalysisDao;
        this.minedPatentDao = minedPatentDao;
        this.searchService = searchService;
        this.claudeCliService = claudeCliService;
        this.areaExtractor = areaExtractor;
        this.miningExportService = miningExportService;
    }

    // --- Public nested types (used by controllers via qualified name) ---

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
            return "[" + category + "] " + title + (domain != null && !domain.isBlank() ? " -- " + domain : "");
        }
    }

    public record MiningResult(
            boolean success, String area, String resultJson, String error,
            long durationMs, long inputTokens, long outputTokens,
            double costUsd, int externalPatentsFound, boolean rateLimited
    ) {
        public MiningResult(boolean success, String area, String resultJson, String error,
                            long durationMs, long inputTokens, long outputTokens,
                            double costUsd, int externalPatentsFound) {
            this(success, area, resultJson, error, durationMs, inputTokens, outputTokens,
                    costUsd, externalPatentsFound, false);
        }
    }

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

    // --- Extraction (delegated to AreaExtractor) ---

    public List<AreaOfInterest> extractAreasOfInterest() throws SQLException {
        return areaExtractor.extractAreasOfInterest();
    }

    public List<InventionPromptItem> extractInventionPrompts() throws SQLException {
        return areaExtractor.extractInventionPrompts();
    }

    // --- Mining orchestration (stays in facade) ---

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
                    System.currentTimeMillis() - startTime, 0, 0, 0.0, 0, searchResult.rateLimited());
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

            int idleTimeout = ConfigService.getInstance().getIdleTimeout();
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
                    PatentAnalysis pa = PatentAnalysis.builder()
                            .patentId(patents.get(0).getId())
                            .analysisType(analysisType)
                            .resultJson(cliResult.resultJson())
                            .modelUsed(cliResult.modelUsed())
                            .build();
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
                    System.currentTimeMillis() - startTime, 0, 0, 0.0, 0, searchResult.rateLimited());
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

            int idleTimeout = ConfigService.getInstance().getIdleTimeout();
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
                    PatentAnalysis pa = PatentAnalysis.builder()
                            .patentId(patents.get(0).getId())
                            .analysisType(analysisType)
                            .resultJson(cliResult.resultJson())
                            .modelUsed(cliResult.modelUsed())
                            .build();
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

            if (result.rateLimited()) {
                if (callback != null) {
                    callback.onStatus("Stopping -- Google Patents is rate limiting requests. Try again later.");
                }
                break;
            }
        }

        return results;
    }

    // --- Cache access ---

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

    // --- Export (delegated to MiningExportService) ---

    public String exportMiningMarkdown(String areaName) throws SQLException {
        return miningExportService.exportMiningMarkdown(areaName);
    }

    public String exportIPMiningMarkdown(String promptTitle) throws SQLException {
        return miningExportService.exportIPMiningMarkdown(promptTitle);
    }

    public String exportAllMiningMarkdown() throws SQLException {
        return miningExportService.exportAllMiningMarkdown();
    }

    // --- Private helpers (mining support) ---

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
            } catch (SQLException ex) { }
        }
        return sb.toString();
    }

    private String buildExternalPatentsText(String searchArea) throws SQLException {
        List<MinedPatent> mined = minedPatentDao.findBySearchArea(searchArea);
        StringBuilder sb = new StringBuilder();
        for (MinedPatent mp : mined) {
            sb.append("Patent: ").append(mp.getPatentNumber())
                    .append(" -- ").append(mp.getTitle());
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
}
