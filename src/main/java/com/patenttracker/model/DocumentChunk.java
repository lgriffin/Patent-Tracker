package com.patenttracker.model;

import java.time.LocalDateTime;

public final class DocumentChunk {
    private final int id;
    private final int documentId;
    private final String patentNumber;
    private final String sectionType;
    private final int chunkIndex;
    private final String chunkText;
    private final int wordCount;
    private final int startPosition;
    private final int endPosition;
    private final String metadataJson;
    private final LocalDateTime createdAt;

    private DocumentChunk(Builder b) {
        this.id = b.id;
        this.documentId = b.documentId;
        this.patentNumber = b.patentNumber;
        this.sectionType = b.sectionType;
        this.chunkIndex = b.chunkIndex;
        this.chunkText = b.chunkText;
        this.wordCount = b.wordCount;
        this.startPosition = b.startPosition;
        this.endPosition = b.endPosition;
        this.metadataJson = b.metadataJson;
        this.createdAt = b.createdAt;
    }

    public static Builder builder() { return new Builder(); }

    public int getId() { return id; }
    public int getDocumentId() { return documentId; }
    public String getPatentNumber() { return patentNumber; }
    public String getSectionType() { return sectionType; }
    public int getChunkIndex() { return chunkIndex; }
    public String getChunkText() { return chunkText; }
    public int getWordCount() { return wordCount; }
    public int getStartPosition() { return startPosition; }
    public int getEndPosition() { return endPosition; }
    public String getMetadataJson() { return metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public enum SectionType {
        ABSTRACT, CLAIMS, CLAIM_INDIVIDUAL, DESCRIPTION,
        FIELD_OF_INVENTION, BACKGROUND, SUMMARY,
        DETAILED_DESCRIPTION, DRAWINGS_DESCRIPTION, FULL_TEXT
    }

    public static final class Builder {
        private int id;
        private int documentId;
        private String patentNumber;
        private String sectionType;
        private int chunkIndex;
        private String chunkText;
        private int wordCount;
        private int startPosition;
        private int endPosition;
        private String metadataJson;
        private LocalDateTime createdAt;

        private Builder() {}

        public Builder id(int id) { this.id = id; return this; }
        public Builder documentId(int documentId) { this.documentId = documentId; return this; }
        public Builder patentNumber(String patentNumber) { this.patentNumber = patentNumber; return this; }
        public Builder sectionType(String sectionType) { this.sectionType = sectionType; return this; }
        public Builder chunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; return this; }
        public Builder chunkText(String chunkText) { this.chunkText = chunkText; return this; }
        public Builder wordCount(int wordCount) { this.wordCount = wordCount; return this; }
        public Builder startPosition(int startPosition) { this.startPosition = startPosition; return this; }
        public Builder endPosition(int endPosition) { this.endPosition = endPosition; return this; }
        public Builder metadataJson(String metadataJson) { this.metadataJson = metadataJson; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public DocumentChunk build() { return new DocumentChunk(this); }
    }
}
