package com.patenttracker.service;

import com.patenttracker.dao.PatentAnalysisDao;
import com.patenttracker.dao.PatentTextDao;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentAnalysis;
import com.patenttracker.model.PatentText;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * Handles per-patent analysis: claims, technology extraction, expansion vectors, prior art.
 */
public class SinglePatentAnalyzer {

    private final PatentTextDao patentTextDao;
    private final PatentAnalysisDao patentAnalysisDao;
    private final PdfExtractorService pdfExtractorService;
    private final ClaudeCliService claudeCliService;

    public SinglePatentAnalyzer() {
        this.patentTextDao = new PatentTextDao();
        this.patentAnalysisDao = new PatentAnalysisDao();
        this.pdfExtractorService = new PdfExtractorService();
        this.claudeCliService = new ClaudeCliService();
    }

    public SinglePatentAnalyzer(PatentTextDao patentTextDao, PatentAnalysisDao patentAnalysisDao,
                                PdfExtractorService pdfExtractorService, ClaudeCliService claudeCliService) {
        this.patentTextDao = patentTextDao;
        this.patentAnalysisDao = patentAnalysisDao;
        this.pdfExtractorService = pdfExtractorService;
        this.claudeCliService = claudeCliService;
    }

    public InsightService.InsightResult analyzeClaims(Patent patent) {
        return runSinglePatentAnalysis(patent, "CLAIMS", "claims");
    }

    public InsightService.InsightResult analyzeTechnology(Patent patent) {
        return runSinglePatentAnalysis(patent, "TECHNOLOGY", "technology");
    }

    public InsightService.InsightResult analyzeExpansion(Patent patent) {
        return runSinglePatentAnalysis(patent, "EXPANSION", "expansion");
    }

    public InsightService.InsightResult analyzePriorArt(Patent patent) {
        return runSinglePatentAnalysis(patent, "PRIOR_ART", "prior-art");
    }

    public InsightService.InsightResult analyzeIdeaSeeds(Patent patent) {
        return runSinglePatentAnalysis(patent, "IDEA_SEEDS", "idea-seeds");
    }

    InsightService.InsightResult runSinglePatentAnalysis(Patent patent, String analysisType, String templateName) {
        String text = ensureTextExtracted(patent);
        if (text == null) {
            return new InsightService.InsightResult(false, analysisType, null,
                    "No text available - ensure PDF is downloaded first.", 0);
        }

        try {
            String template = ClaudeCliService.loadPromptTemplate(templateName);
            Map<String, String> variables = Map.of(
                    "patent_title", patent.getTitle() != null ? patent.getTitle() : "",
                    "patent_number", patent.getPatentNumber() != null ? patent.getPatentNumber() :
                            (patent.getApplicationNumber() != null ? patent.getApplicationNumber() : ""),
                    "patent_text", text
            );

            int timeoutSeconds = ConfigService.getInstance().getAnalysisTimeout();
            ClaudeCliService.AnalysisResult cliResult = claudeCliService.analyze(template, variables, timeoutSeconds);

            if (cliResult.success() && cliResult.resultJson() != null) {
                PatentAnalysis pa = PatentAnalysis.builder()
                        .patentId(patent.getId())
                        .analysisType(analysisType)
                        .resultJson(cliResult.resultJson())
                        .modelUsed(cliResult.modelUsed())
                        .build();
                patentAnalysisDao.insertOrUpdate(pa);

                return new InsightService.InsightResult(true, analysisType, cliResult.resultJson(),
                        null, cliResult.durationMs(),
                        cliResult.inputTokens(), cliResult.outputTokens(), cliResult.costUsd());
            } else {
                return new InsightService.InsightResult(false, analysisType, null,
                        cliResult.error(), cliResult.durationMs());
            }
        } catch (IOException e) {
            return new InsightService.InsightResult(false, analysisType, null,
                    "Failed to load prompt template: " + e.getMessage(), 0);
        } catch (SQLException e) {
            return new InsightService.InsightResult(false, analysisType, null,
                    "Database error: " + e.getMessage(), 0);
        }
    }

    String ensureTextExtracted(Patent patent) {
        try {
            PatentText pt = patentTextDao.findByPatentId(patent.getId());
            if (pt != null) {
                return pt.getFullText();
            }

             PdfExtractorService.ExtractionResult result = pdfExtractorService.extractText(patent);
             if (result.success()) {
                 pt = patentTextDao.findByPatentId(patent.getId());
                 return pt != null ? pt.getFullText() : null;
             }
         } catch (SQLException ex) { }
         return null;
    }
}
