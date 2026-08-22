package com.patenttracker.model;

import java.time.LocalDateTime;

public final class CorpusDocument {
    private final int id;
    private final String patentNumber;
    private final String title;
    private final String abstractText;
    private final String assignee;
    private final String filingDate;
    private final String grantDate;
    private final String cpcCodes;
    private final String source;
    private final String pdfPath;
    private final String fullText;
    private final int pageCount;
    private final int wordCount;
    private final int chunkCount;
    private final String downloadStatus;
    private final String extractionStatus;
    private final String chunkingStatus;
    private final String domain;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private CorpusDocument(Builder b) {
        this.id = b.id;
        this.patentNumber = b.patentNumber;
        this.title = b.title;
        this.abstractText = b.abstractText;
        this.assignee = b.assignee;
        this.filingDate = b.filingDate;
        this.grantDate = b.grantDate;
        this.cpcCodes = b.cpcCodes;
        this.source = b.source;
        this.pdfPath = b.pdfPath;
        this.fullText = b.fullText;
        this.pageCount = b.pageCount;
        this.wordCount = b.wordCount;
        this.chunkCount = b.chunkCount;
        this.downloadStatus = b.downloadStatus;
        this.extractionStatus = b.extractionStatus;
        this.chunkingStatus = b.chunkingStatus;
        this.domain = b.domain;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(CorpusDocument d) { return new Builder(d); }

    public int getId() { return id; }
    public String getPatentNumber() { return patentNumber; }
    public String getTitle() { return title; }
    public String getAbstractText() { return abstractText; }
    public String getAssignee() { return assignee; }
    public String getFilingDate() { return filingDate; }
    public String getGrantDate() { return grantDate; }
    public String getCpcCodes() { return cpcCodes; }
    public String getSource() { return source; }
    public String getPdfPath() { return pdfPath; }
    public String getFullText() { return fullText; }
    public int getPageCount() { return pageCount; }
    public int getWordCount() { return wordCount; }
    public int getChunkCount() { return chunkCount; }
    public String getDownloadStatus() { return downloadStatus; }
    public String getExtractionStatus() { return extractionStatus; }
    public String getChunkingStatus() { return chunkingStatus; }
    public String getDomain() { return domain; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public enum Status {
        PENDING, IN_PROGRESS, COMPLETE, FAILED
    }

    public static final class Builder {
        private int id;
        private String patentNumber;
        private String title;
        private String abstractText;
        private String assignee;
        private String filingDate;
        private String grantDate;
        private String cpcCodes;
        private String source = "PATENTSVIEW";
        private String pdfPath;
        private String fullText;
        private int pageCount;
        private int wordCount;
        private int chunkCount;
        private String downloadStatus = "PENDING";
        private String extractionStatus = "PENDING";
        private String chunkingStatus = "PENDING";
        private String domain;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private Builder() {}

        private Builder(CorpusDocument d) {
            this.id = d.id;
            this.patentNumber = d.patentNumber;
            this.title = d.title;
            this.abstractText = d.abstractText;
            this.assignee = d.assignee;
            this.filingDate = d.filingDate;
            this.grantDate = d.grantDate;
            this.cpcCodes = d.cpcCodes;
            this.source = d.source;
            this.pdfPath = d.pdfPath;
            this.fullText = d.fullText;
            this.pageCount = d.pageCount;
            this.wordCount = d.wordCount;
            this.chunkCount = d.chunkCount;
            this.downloadStatus = d.downloadStatus;
            this.extractionStatus = d.extractionStatus;
            this.chunkingStatus = d.chunkingStatus;
            this.domain = d.domain;
            this.createdAt = d.createdAt;
            this.updatedAt = d.updatedAt;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder patentNumber(String patentNumber) { this.patentNumber = patentNumber; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder abstractText(String abstractText) { this.abstractText = abstractText; return this; }
        public Builder assignee(String assignee) { this.assignee = assignee; return this; }
        public Builder filingDate(String filingDate) { this.filingDate = filingDate; return this; }
        public Builder grantDate(String grantDate) { this.grantDate = grantDate; return this; }
        public Builder cpcCodes(String cpcCodes) { this.cpcCodes = cpcCodes; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder pdfPath(String pdfPath) { this.pdfPath = pdfPath; return this; }
        public Builder fullText(String fullText) { this.fullText = fullText; return this; }
        public Builder pageCount(int pageCount) { this.pageCount = pageCount; return this; }
        public Builder wordCount(int wordCount) { this.wordCount = wordCount; return this; }
        public Builder chunkCount(int chunkCount) { this.chunkCount = chunkCount; return this; }
        public Builder downloadStatus(String downloadStatus) { this.downloadStatus = downloadStatus; return this; }
        public Builder extractionStatus(String extractionStatus) { this.extractionStatus = extractionStatus; return this; }
        public Builder chunkingStatus(String chunkingStatus) { this.chunkingStatus = chunkingStatus; return this; }
        public Builder domain(String domain) { this.domain = domain; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public CorpusDocument build() { return new CorpusDocument(this); }
    }
}
