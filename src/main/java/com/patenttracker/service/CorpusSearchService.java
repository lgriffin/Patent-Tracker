package com.patenttracker.service;

import com.patenttracker.dao.DocumentChunkDao;
import com.patenttracker.model.DocumentChunk;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CorpusSearchService {

    private static final int DEFAULT_FTS_LIMIT = 50;
    private static final int DEFAULT_SEMANTIC_LIMIT = 20;

    private final DocumentChunkDao chunkDao;
    private final ClaudeCliService claudeService;

    public CorpusSearchService() {
        this.chunkDao = new DocumentChunkDao();
        this.claudeService = new ClaudeCliService();
    }

    public CorpusSearchService(DocumentChunkDao chunkDao, ClaudeCliService claudeService) {
        this.chunkDao = chunkDao;
        this.claudeService = claudeService;
    }

    public SearchResult searchKeyword(String query, String sectionFilter, int limit) throws SQLException {
        if (query == null || query.isBlank()) {
            return new SearchResult(List.of(), 0, "KEYWORD", null);
        }

        String ftsQuery = buildFtsQuery(query);
        List<DocumentChunk> results;

        if (sectionFilter != null && !sectionFilter.isBlank()) {
            results = chunkDao.searchFtsBySection(ftsQuery, sectionFilter, limit > 0 ? limit : DEFAULT_FTS_LIMIT);
        } else {
            results = chunkDao.searchFts(ftsQuery, limit > 0 ? limit : DEFAULT_FTS_LIMIT);
        }

        return new SearchResult(results, results.size(), "KEYWORD", null);
    }

    public SearchResult searchSemantic(String query, String sectionFilter) throws SQLException, IOException {
        List<DocumentChunk> ftsResults;
        String ftsQuery = buildFtsQuery(query);

        if (sectionFilter != null && !sectionFilter.isBlank()) {
            ftsResults = chunkDao.searchFtsBySection(ftsQuery, sectionFilter, DEFAULT_FTS_LIMIT);
        } else {
            ftsResults = chunkDao.searchFts(ftsQuery, DEFAULT_FTS_LIMIT);
        }

        if (ftsResults.isEmpty()) {
            return new SearchResult(List.of(), 0, "SEMANTIC", null);
        }

        String template = ClaudeCliService.loadPromptTemplate("corpus-semantic-rank");
        StringBuilder candidateText = new StringBuilder();
        for (int i = 0; i < ftsResults.size(); i++) {
            DocumentChunk chunk = ftsResults.get(i);
            String preview = chunk.getChunkText();
            if (preview.length() > 300) preview = preview.substring(0, 300) + "...";
            candidateText.append("[").append(i).append("] ")
                    .append(chunk.getPatentNumber())
                    .append(" (").append(chunk.getSectionType()).append("): ")
                    .append(preview).append("\n\n");
        }

        Map<String, String> vars = Map.of(
                "query", query,
                "candidates", candidateText.toString()
        );

        int timeout = ConfigService.getInstance().getAnalysisTimeout();
        ClaudeCliService.AnalysisResult analysis = claudeService.analyze(template, vars, timeout);

        if (!analysis.success()) {
            return new SearchResult(ftsResults.subList(0, Math.min(DEFAULT_SEMANTIC_LIMIT, ftsResults.size())),
                    ftsResults.size(), "SEMANTIC", analysis.error());
        }

        List<DocumentChunk> ranked = reorderByRanking(ftsResults, analysis.resultJson());
        return new SearchResult(ranked, ranked.size(), "SEMANTIC", null);
    }

    public SearchResult searchHybrid(String query, String sectionFilter) throws SQLException, IOException {
        SearchResult keywordResult = searchKeyword(query, sectionFilter, DEFAULT_FTS_LIMIT);

        if (keywordResult.results().isEmpty()) {
            return keywordResult;
        }

        if (!claudeService.isAvailable()) {
            return new SearchResult(keywordResult.results(), keywordResult.totalMatches(),
                    "HYBRID", "Claude CLI not available, returning keyword results only");
        }

        return searchSemantic(query, sectionFilter);
    }

    private List<DocumentChunk> reorderByRanking(List<DocumentChunk> original, String rankingJson) {
        if (rankingJson == null || rankingJson.isBlank()) return original;

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(rankingJson);
            com.fasterxml.jackson.databind.JsonNode rankings = root.has("rankings") ? root.get("rankings") : root;

            if (!rankings.isArray()) return original;

            Map<Integer, DocumentChunk> byIndex = new LinkedHashMap<>();
            for (int i = 0; i < original.size(); i++) {
                byIndex.put(i, original.get(i));
            }

            List<DocumentChunk> reordered = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode entry : rankings) {
                int idx = entry.has("index") ? entry.get("index").asInt(-1) : -1;
                if (idx >= 0 && byIndex.containsKey(idx)) {
                    reordered.add(byIndex.remove(idx));
                }
            }

            reordered.addAll(byIndex.values());
            return reordered.subList(0, Math.min(DEFAULT_SEMANTIC_LIMIT, reordered.size()));
        } catch (Exception e) {
            return original.subList(0, Math.min(DEFAULT_SEMANTIC_LIMIT, original.size()));
        }
    }

    private String buildFtsQuery(String query) {
        String cleaned = query.replaceAll("[^a-zA-Z0-9\\s]", " ").trim();
        String[] terms = cleaned.split("\\s+");
        if (terms.length <= 1) return cleaned;

        StringBuilder fts = new StringBuilder();
        for (int i = 0; i < terms.length; i++) {
            if (i > 0) fts.append(" OR ");
            fts.append(terms[i]);
        }
        return fts.toString();
    }

    public record SearchResult(List<DocumentChunk> results, int totalMatches,
                                String searchType, String error) {
        public boolean success() { return error == null; }
    }
}
