package com.patenttracker.model;

import java.time.LocalDateTime;

public final class SearchSession {
    private final int id;
    private final String ideaText;
    private final String decomposedJson;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private SearchSession(Builder b) {
        this.id = b.id;
        this.ideaText = b.ideaText;
        this.decomposedJson = b.decomposedJson;
        this.status = b.status;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(SearchSession s) { return new Builder(s); }

    public int getId() { return id; }
    public String getIdeaText() { return ideaText; }
    public String getDecomposedJson() { return decomposedJson; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public enum Status {
        PENDING, DECOMPOSING, SEARCHING, ANALYZING, DIFFERENTIATING, COMPLETE, FAILED
    }

    public static final class Builder {
        private int id;
        private String ideaText;
        private String decomposedJson;
        private String status = Status.PENDING.name();
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private Builder() {}

        private Builder(SearchSession s) {
            this.id = s.id;
            this.ideaText = s.ideaText;
            this.decomposedJson = s.decomposedJson;
            this.status = s.status;
            this.createdAt = s.createdAt;
            this.updatedAt = s.updatedAt;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder ideaText(String ideaText) { this.ideaText = ideaText; return this; }
        public Builder decomposedJson(String decomposedJson) { this.decomposedJson = decomposedJson; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public SearchSession build() { return new SearchSession(this); }
    }
}
