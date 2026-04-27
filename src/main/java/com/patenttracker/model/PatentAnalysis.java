package com.patenttracker.model;

import java.time.LocalDateTime;

public class PatentAnalysis {
    private int id;
    private int patentId;
    private String analysisType;
    private String resultJson;
    private String modelUsed;
    private LocalDateTime analyzedAt;

    public PatentAnalysis() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatentId() { return patentId; }
    public void setPatentId(int patentId) { this.patentId = patentId; }

    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }

    public enum AnalysisType {
        CLAIMS("Claim Decomposition"),
        TECHNOLOGY("Technology Extraction"),
        WHITESPACE("Whitespace Finder"),
        EXPANSION("Expansion Vectors"),
        CLUSTERING("Cross-Patent Clustering"),
        PRIOR_ART("Prior Art Proximity"),
        ADJACENCY("Adjacency Mapping"),
        TEMPORAL_TRENDS("Temporal Trends"),
        CLAIM_COLLISION("Claim Collision"),
        COMPETITOR_GAPS("Competitor Gaps"),
        INVENTION_PROMPTS("Invention Prompts"),
        CROSS_DOMAIN("Cross-Domain Combinator");

        private final String displayLabel;

        AnalysisType(String displayLabel) {
            this.displayLabel = displayLabel;
        }

        public String getDisplayLabel() { return displayLabel; }

        public static AnalysisType fromString(String s) {
            try {
                return valueOf(s);
            } catch (IllegalArgumentException e) {
                return TECHNOLOGY;
            }
        }
    }
}
