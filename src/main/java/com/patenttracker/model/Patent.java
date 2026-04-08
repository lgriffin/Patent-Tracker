package com.patenttracker.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Patent {
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

    // Transient fields for display
    private String primaryInventorName;
    private String secondaryInventorName;
    private String additionalInventorNames;
    private String tagNames;

    public Patent() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFileNumber() { return fileNumber; }
    public void setFileNumber(String fileNumber) { this.fileNumber = fileNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDate getFilingDate() { return filingDate; }
    public void setFilingDate(LocalDate filingDate) { this.filingDate = filingDate; }

    public String getApplicationNumber() { return applicationNumber; }
    public void setApplicationNumber(String applicationNumber) { this.applicationNumber = applicationNumber; }

    public LocalDate getPublicationDate() { return publicationDate; }
    public void setPublicationDate(LocalDate publicationDate) { this.publicationDate = publicationDate; }

    public String getPublicationNumber() { return publicationNumber; }
    public void setPublicationNumber(String publicationNumber) { this.publicationNumber = publicationNumber; }

    public LocalDate getIssueGrantDate() { return issueGrantDate; }
    public void setIssueGrantDate(LocalDate issueGrantDate) { this.issueGrantDate = issueGrantDate; }

    public String getPatentNumber() { return patentNumber; }
    public void setPatentNumber(String patentNumber) { this.patentNumber = patentNumber; }

    public String getPtoStatus() { return ptoStatus; }
    public void setPtoStatus(String ptoStatus) { this.ptoStatus = ptoStatus; }

    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }

    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }

    public String getParentFileNumber() { return parentFileNumber; }
    public void setParentFileNumber(String parentFileNumber) { this.parentFileNumber = parentFileNumber; }

    public Integer getCsvRowNumber() { return csvRowNumber; }
    public void setCsvRowNumber(Integer csvRowNumber) { this.csvRowNumber = csvRowNumber; }

    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getPrimaryInventorName() { return primaryInventorName; }
    public void setPrimaryInventorName(String primaryInventorName) { this.primaryInventorName = primaryInventorName; }

    public String getSecondaryInventorName() { return secondaryInventorName; }
    public void setSecondaryInventorName(String secondaryInventorName) { this.secondaryInventorName = secondaryInventorName; }

    public String getAdditionalInventorNames() { return additionalInventorNames; }
    public void setAdditionalInventorNames(String additionalInventorNames) { this.additionalInventorNames = additionalInventorNames; }

    public String getTagNames() { return tagNames; }
    public void setTagNames(String tagNames) { this.tagNames = tagNames; }
}
