package com.patenttracker.model;

import java.time.LocalDateTime;

public final class PatentAnalysis {
    private final int id;
    private final int patentId;
    private final String analysisType;
    private final String resultJson;
    private final String modelUsed;
    private final LocalDateTime analyzedAt;

    private PatentAnalysis(Builder b) {
        this.id = b.id;
        this.patentId = b.patentId;
        this.analysisType = b.analysisType;
        this.resultJson = b.resultJson;
        this.modelUsed = b.modelUsed;
        this.analyzedAt = b.analyzedAt;
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(PatentAnalysis m) { return new Builder(m); }

    public int getId() { return id; }
    public int getPatentId() { return patentId; }
    public String getAnalysisType() { return analysisType; }
    public String getResultJson() { return resultJson; }
    public String getModelUsed() { return modelUsed; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }

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

    public static final class Builder {
        private int id;
        private int patentId;
        private String analysisType;
        private String resultJson;
        private String modelUsed;
        private LocalDateTime analyzedAt;

        private Builder() {}

        private Builder(PatentAnalysis m) {
            this.id = m.id;
            this.patentId = m.patentId;
            this.analysisType = m.analysisType;
            this.resultJson = m.resultJson;
            this.modelUsed = m.modelUsed;
            this.analyzedAt = m.analyzedAt;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder patentId(int patentId) { this.patentId = patentId; return this; }
        public Builder analysisType(String analysisType) { this.analysisType = analysisType; return this; }
        public Builder resultJson(String resultJson) { this.resultJson = resultJson; return this; }
        public Builder modelUsed(String modelUsed) { this.modelUsed = modelUsed; return this; }
        public Builder analyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; return this; }

        public PatentAnalysis build() { return new PatentAnalysis(this); }
    }
}
