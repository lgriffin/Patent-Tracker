package com.patenttracker.model;

import java.time.LocalDateTime;

public final class PatentText {
    private final int id;
    private final int patentId;
    private final String fullText;
    private final Integer pageCount;
    private final LocalDateTime extractedAt;

    private PatentText(Builder b) {
        this.id = b.id;
        this.patentId = b.patentId;
        this.fullText = b.fullText;
        this.pageCount = b.pageCount;
        this.extractedAt = b.extractedAt;
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(PatentText m) { return new Builder(m); }

    public int getId() { return id; }
    public int getPatentId() { return patentId; }
    public String getFullText() { return fullText; }
    public Integer getPageCount() { return pageCount; }
    public LocalDateTime getExtractedAt() { return extractedAt; }

    public static final class Builder {
        private int id;
        private int patentId;
        private String fullText;
        private Integer pageCount;
        private LocalDateTime extractedAt;

        private Builder() {}

        private Builder(PatentText m) {
            this.id = m.id;
            this.patentId = m.patentId;
            this.fullText = m.fullText;
            this.pageCount = m.pageCount;
            this.extractedAt = m.extractedAt;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder patentId(int patentId) { this.patentId = patentId; return this; }
        public Builder fullText(String fullText) { this.fullText = fullText; return this; }
        public Builder pageCount(Integer pageCount) { this.pageCount = pageCount; return this; }
        public Builder extractedAt(LocalDateTime extractedAt) { this.extractedAt = extractedAt; return this; }

        public PatentText build() { return new PatentText(this); }
    }
}
