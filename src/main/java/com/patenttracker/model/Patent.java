package com.patenttracker.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Patent {
    private final int id;
    private final String fileNumber;
    private final String title;
    private final LocalDate filingDate;
    private final String applicationNumber;
    private final LocalDate publicationDate;
    private final String publicationNumber;
    private final LocalDate issueGrantDate;
    private final String patentNumber;
    private final String ptoStatus;
    private final String suffix;
    private final String classification;
    private final String parentFileNumber;
    private final Integer csvRowNumber;
    private final String pdfPath;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    // Transient fields for display
    private final String primaryInventorName;
    private final String secondaryInventorName;
    private final String additionalInventorNames;
    private final String tagNames;

    private Patent(Builder b) {
        this.id = b.id;
        this.fileNumber = b.fileNumber;
        this.title = b.title;
        this.filingDate = b.filingDate;
        this.applicationNumber = b.applicationNumber;
        this.publicationDate = b.publicationDate;
        this.publicationNumber = b.publicationNumber;
        this.issueGrantDate = b.issueGrantDate;
        this.patentNumber = b.patentNumber;
        this.ptoStatus = b.ptoStatus;
        this.suffix = b.suffix;
        this.classification = b.classification;
        this.parentFileNumber = b.parentFileNumber;
        this.csvRowNumber = b.csvRowNumber;
        this.pdfPath = b.pdfPath;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
        this.primaryInventorName = b.primaryInventorName;
        this.secondaryInventorName = b.secondaryInventorName;
        this.additionalInventorNames = b.additionalInventorNames;
        this.tagNames = b.tagNames;
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(Patent m) { return new Builder(m); }

    public int getId() { return id; }
    public String getFileNumber() { return fileNumber; }
    public String getTitle() { return title; }
    public LocalDate getFilingDate() { return filingDate; }
    public String getApplicationNumber() { return applicationNumber; }
    public LocalDate getPublicationDate() { return publicationDate; }
    public String getPublicationNumber() { return publicationNumber; }
    public LocalDate getIssueGrantDate() { return issueGrantDate; }
    public String getPatentNumber() { return patentNumber; }
    public String getPtoStatus() { return ptoStatus; }
    public String getSuffix() { return suffix; }
    public String getClassification() { return classification; }
    public String getParentFileNumber() { return parentFileNumber; }
    public Integer getCsvRowNumber() { return csvRowNumber; }
    public String getPdfPath() { return pdfPath; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getPrimaryInventorName() { return primaryInventorName; }
    public String getSecondaryInventorName() { return secondaryInventorName; }
    public String getAdditionalInventorNames() { return additionalInventorNames; }
    public String getTagNames() { return tagNames; }

    public static final class Builder {
        private int id;
        private String fileNumber;
        private String title;
        private LocalDate filingDate;
        private String applicationNumber;
        private LocalDate publicationDate;
        private String publicationNumber;
        private LocalDate issueGrantDate;
        private String patentNumber;
        private String ptoStatus;
        private String suffix;
        private String classification;
        private String parentFileNumber;
        private Integer csvRowNumber;
        private String pdfPath;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String primaryInventorName;
        private String secondaryInventorName;
        private String additionalInventorNames;
        private String tagNames;

        private Builder() {}

        private Builder(Patent m) {
            this.id = m.id;
            this.fileNumber = m.fileNumber;
            this.title = m.title;
            this.filingDate = m.filingDate;
            this.applicationNumber = m.applicationNumber;
            this.publicationDate = m.publicationDate;
            this.publicationNumber = m.publicationNumber;
            this.issueGrantDate = m.issueGrantDate;
            this.patentNumber = m.patentNumber;
            this.ptoStatus = m.ptoStatus;
            this.suffix = m.suffix;
            this.classification = m.classification;
            this.parentFileNumber = m.parentFileNumber;
            this.csvRowNumber = m.csvRowNumber;
            this.pdfPath = m.pdfPath;
            this.createdAt = m.createdAt;
            this.updatedAt = m.updatedAt;
            this.primaryInventorName = m.primaryInventorName;
            this.secondaryInventorName = m.secondaryInventorName;
            this.additionalInventorNames = m.additionalInventorNames;
            this.tagNames = m.tagNames;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder fileNumber(String fileNumber) { this.fileNumber = fileNumber; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder filingDate(LocalDate filingDate) { this.filingDate = filingDate; return this; }
        public Builder applicationNumber(String applicationNumber) { this.applicationNumber = applicationNumber; return this; }
        public Builder publicationDate(LocalDate publicationDate) { this.publicationDate = publicationDate; return this; }
        public Builder publicationNumber(String publicationNumber) { this.publicationNumber = publicationNumber; return this; }
        public Builder issueGrantDate(LocalDate issueGrantDate) { this.issueGrantDate = issueGrantDate; return this; }
        public Builder patentNumber(String patentNumber) { this.patentNumber = patentNumber; return this; }
        public Builder ptoStatus(String ptoStatus) { this.ptoStatus = ptoStatus; return this; }
        public Builder suffix(String suffix) { this.suffix = suffix; return this; }
        public Builder classification(String classification) { this.classification = classification; return this; }
        public Builder parentFileNumber(String parentFileNumber) { this.parentFileNumber = parentFileNumber; return this; }
        public Builder csvRowNumber(Integer csvRowNumber) { this.csvRowNumber = csvRowNumber; return this; }
        public Builder pdfPath(String pdfPath) { this.pdfPath = pdfPath; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder primaryInventorName(String primaryInventorName) { this.primaryInventorName = primaryInventorName; return this; }
        public Builder secondaryInventorName(String secondaryInventorName) { this.secondaryInventorName = secondaryInventorName; return this; }
        public Builder additionalInventorNames(String additionalInventorNames) { this.additionalInventorNames = additionalInventorNames; return this; }
        public Builder tagNames(String tagNames) { this.tagNames = tagNames; return this; }

        public Patent build() { return new Patent(this); }
    }
}
