package com.patenttracker.service;

import com.patenttracker.dao.PatentAnalysisDao;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.dao.PatentTextDao;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentAnalysis;
import com.patenttracker.model.PatentText;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Facade for patent insight analysis.
 * Delegates to SinglePatentAnalyzer, PortfolioAnalyzer, and InsightExportService.
 */
public class InsightService {

    private final PatentDao patentDao;
    private final PatentTextDao patentTextDao;
    private final PatentAnalysisDao patentAnalysisDao;
    private final SinglePatentAnalyzer singlePatentAnalyzer;
    private final PortfolioAnalyzer portfolioAnalyzer;
    private final InsightExportService insightExportService;

    public InsightService() {
        this.patentDao = new PatentDao();
        this.patentTextDao = new PatentTextDao();
        this.patentAnalysisDao = new PatentAnalysisDao();
        this.singlePatentAnalyzer = new SinglePatentAnalyzer();
        this.portfolioAnalyzer = new PortfolioAnalyzer();
        this.insightExportService = new InsightExportService();
    }

    public InsightService(PatentDao patentDao, PatentTextDao patentTextDao,
                          PatentAnalysisDao patentAnalysisDao,
                          SinglePatentAnalyzer singlePatentAnalyzer,
                          PortfolioAnalyzer portfolioAnalyzer,
                          InsightExportService insightExportService) {
        this.patentDao = patentDao;
        this.patentTextDao = patentTextDao;
        this.patentAnalysisDao = patentAnalysisDao;
        this.singlePatentAnalyzer = singlePatentAnalyzer;
        this.portfolioAnalyzer = portfolioAnalyzer;
        this.insightExportService = insightExportService;
    }

    // --- Callback interfaces (used by controllers via qualified name) ---

    public interface CrossPatentProgressCallback {
        void onChunkProgress(int currentChunk, int totalChunks);
        void onMergeProgress();
        void onStreamingStatus(String status);
        boolean isCancelled();
    }

    public interface AnalysisProgressCallback {
        void onProgress(int current, int total, String title);
        void onResult(InsightResult result);
        boolean isCancelled();
    }

    // --- Records (used by controllers via qualified name) ---

    public record InsightResult(
            boolean success, String analysisType, String resultJson,
            String error, long durationMs,
            long inputTokens, long outputTokens, double costUsd
    ) {
        public InsightResult(boolean success, String analysisType, String resultJson,
                             String error, long durationMs) {
            this(success, analysisType, resultJson, error, durationMs, 0, 0, 0.0);
        }
    }

    public record InsightStats(
            int totalPatents, int withText, int withAnalysis,
            Map<String, Integer> analysisByType
    ) {}

    // --- Single-patent analysis (delegated to SinglePatentAnalyzer) ---

    public InsightResult analyzeClaims(Patent patent) {
        return singlePatentAnalyzer.analyzeClaims(patent);
    }

    public InsightResult analyzeTechnology(Patent patent) {
        return singlePatentAnalyzer.analyzeTechnology(patent);
    }

    public InsightResult analyzeExpansion(Patent patent) {
        return singlePatentAnalyzer.analyzeExpansion(patent);
    }

    public InsightResult analyzePriorArt(Patent patent) {
        return singlePatentAnalyzer.analyzePriorArt(patent);
    }

    public InsightResult analyzeIdeaSeeds(Patent patent) {
        return singlePatentAnalyzer.analyzeIdeaSeeds(patent);
    }

    // --- Cross-patent analysis (delegated to PortfolioAnalyzer) ---

    public InsightResult analyzeWhitespace(List<Patent> patents) {
        return portfolioAnalyzer.analyzeWhitespace(patents);
    }

    public InsightResult analyzeWhitespace(List<Patent> patents, CrossPatentProgressCallback callback) {
        return portfolioAnalyzer.analyzeWhitespace(patents, callback);
    }

    public InsightResult analyzeClustering(List<Patent> patents) {
        return portfolioAnalyzer.analyzeClustering(patents);
    }

    public InsightResult analyzeClustering(List<Patent> patents, CrossPatentProgressCallback callback) {
        return portfolioAnalyzer.analyzeClustering(patents, callback);
    }

    public InsightResult analyzeAdjacency(List<Patent> patents) {
        return portfolioAnalyzer.analyzeAdjacency(patents);
    }

    public InsightResult analyzeAdjacency(List<Patent> patents, CrossPatentProgressCallback callback) {
        return portfolioAnalyzer.analyzeAdjacency(patents, callback);
    }

    public InsightResult analyzeClaimCollision(List<Patent> patents) {
        return portfolioAnalyzer.analyzeClaimCollision(patents);
    }

    public InsightResult analyzeClaimCollision(List<Patent> patents, CrossPatentProgressCallback callback) {
        return portfolioAnalyzer.analyzeClaimCollision(patents, callback);
    }

    public InsightResult analyzeCompetitorGaps(List<Patent> patents) {
        return portfolioAnalyzer.analyzeCompetitorGaps(patents);
    }

    public InsightResult analyzeCompetitorGaps(List<Patent> patents, CrossPatentProgressCallback callback) {
        return portfolioAnalyzer.analyzeCompetitorGaps(patents, callback);
    }

    public InsightResult analyzeCrossDomain(List<Patent> patents) {
        return portfolioAnalyzer.analyzeCrossDomain(patents);
    }

    public InsightResult analyzeCrossDomain(List<Patent> patents, CrossPatentProgressCallback callback) {
        return portfolioAnalyzer.analyzeCrossDomain(patents, callback);
    }

    public InsightResult analyzeTemporalTrends(List<Patent> patents) {
        return portfolioAnalyzer.analyzeTemporalTrends(patents);
    }

    public InsightResult analyzeTemporalTrends(List<Patent> patents, CrossPatentProgressCallback callback) {
        return portfolioAnalyzer.analyzeTemporalTrends(patents, callback);
    }

    public InsightResult analyzeInventionPrompts(List<Patent> patents) {
        return portfolioAnalyzer.analyzeInventionPrompts(patents);
    }

    public InsightResult analyzeInventionPrompts(List<Patent> patents, CrossPatentProgressCallback callback) {
        return portfolioAnalyzer.analyzeInventionPrompts(patents, callback);
    }

    public InsightResult analyzeSeedSynthesis(List<Patent> patents) {
        return portfolioAnalyzer.analyzeSeedSynthesis(patents);
    }

    public InsightResult analyzeSeedSynthesis(List<Patent> patents, CrossPatentProgressCallback callback) {
        return portfolioAnalyzer.analyzeSeedSynthesis(patents, callback);
    }

    // --- Batch orchestration ---

    public List<InsightResult> analyzeAll(String analysisType, String templateName,
                                          AnalysisProgressCallback callback) {
        List<InsightResult> results = new ArrayList<>();

        try {
            List<PatentText> allText = patentTextDao.findAll();

            for (int i = 0; i < allText.size(); i++) {
                if (callback != null && callback.isCancelled()) break;

                PatentText pt = allText.get(i);
                Patent patent = patentDao.findById(pt.getPatentId());
                if (patent == null) continue;

                if (callback != null) {
                    callback.onProgress(i + 1, allText.size(), patent.getTitle());
                }

                // Skip if already analyzed
                try {
                    PatentAnalysis existing = patentAnalysisDao.findByPatentIdAndType(
                            patent.getId(), analysisType);
                    if (existing != null) {
                        InsightResult cached = new InsightResult(true, analysisType,
                                existing.getResultJson(), null, 0);
                        results.add(cached);
                        if (callback != null) callback.onResult(cached);
                        continue;
                    }
                } catch (SQLException ex) { }

                InsightResult result = singlePatentAnalyzer.runSinglePatentAnalysis(
                        patent, analysisType, templateName);
                results.add(result);

                if (callback != null) {
                    callback.onResult(result);
                }

                if (i < allText.size() - 1) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            results.add(new InsightResult(false, analysisType, null,
                    "Database error: " + e.getMessage(), 0));
        }

        return results;
    }

    // --- Cache / stats (direct DAO access) ---

    public List<PatentAnalysis> getCachedAnalyses(int patentId) throws SQLException {
        return patentAnalysisDao.findByPatentId(patentId);
    }

    public PatentAnalysis getCachedAnalysis(int patentId, String type) throws SQLException {
        return patentAnalysisDao.findByPatentIdAndType(patentId, type);
    }

    public void deleteCachedAnalysis(int patentId, String type) throws SQLException {
        patentAnalysisDao.deleteByPatentIdAndType(patentId, type);
    }

    public InsightStats getStats() {
        try {
            int totalPatents = patentDao.count();
            int withText = patentTextDao.countAll();
            int withAnalysis = patentAnalysisDao.countDistinctPatents();
            Map<String, Integer> byType = patentAnalysisDao.countByType();
            return new InsightStats(totalPatents, withText, withAnalysis, byType);
        } catch (SQLException e) {
            return new InsightStats(0, 0, 0, Map.of());
        }
    }

    // --- Export (delegated to InsightExportService) ---

    public String exportMarkdown() throws SQLException {
        return insightExportService.exportMarkdown();
    }

    public String exportCrossPatentMarkdown() throws SQLException {
        return insightExportService.exportCrossPatentMarkdown();
    }

    public String exportSingleAnalysisMarkdown(String analysisType, String title) throws SQLException {
        return insightExportService.exportSingleAnalysisMarkdown(analysisType, title);
    }
}
