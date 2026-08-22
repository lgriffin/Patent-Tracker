package com.patenttracker.model;

import java.time.LocalDateTime;

public final class SearchAnalysis {
    private final int id;
    private final int sessionId;
    private final String phase;
    private final String resultJson;
    private final String modelUsed;
    private final long durationMs;
    private final double costUsd;
    private final LocalDateTime analyzedAt;

    private SearchAnalysis(Builder b) {
        this.id = b.id;
        this.sessionId = b.sessionId;
        this.phase = b.phase;
        this.resultJson = b.resultJson;
        this.modelUsed = b.modelUsed;
        this.durationMs = b.durationMs;
        this.costUsd = b.costUsd;
        this.analyzedAt = b.analyzedAt;
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(SearchAnalysis a) { return new Builder(a); }

    public int getId() { return id; }
    public int getSessionId() { return sessionId; }
    public String getPhase() { return phase; }
    public String getResultJson() { return resultJson; }
    public String getModelUsed() { return modelUsed; }
    public long getDurationMs() { return durationMs; }
    public double getCostUsd() { return costUsd; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }

    public enum Phase {
        DECOMPOSE, ANALYZE, DIFFERENTIATE
    }

    public static final class Builder {
        private int id;
        private int sessionId;
        private String phase;
        private String resultJson;
        private String modelUsed;
        private long durationMs;
        private double costUsd;
        private LocalDateTime analyzedAt;

        private Builder() {}

        private Builder(SearchAnalysis a) {
            this.id = a.id;
            this.sessionId = a.sessionId;
            this.phase = a.phase;
            this.resultJson = a.resultJson;
            this.modelUsed = a.modelUsed;
            this.durationMs = a.durationMs;
            this.costUsd = a.costUsd;
            this.analyzedAt = a.analyzedAt;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder sessionId(int sessionId) { this.sessionId = sessionId; return this; }
        public Builder phase(String phase) { this.phase = phase; return this; }
        public Builder resultJson(String resultJson) { this.resultJson = resultJson; return this; }
        public Builder modelUsed(String modelUsed) { this.modelUsed = modelUsed; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder costUsd(double costUsd) { this.costUsd = costUsd; return this; }
        public Builder analyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; return this; }

        public SearchAnalysis build() { return new SearchAnalysis(this); }
    }
}
