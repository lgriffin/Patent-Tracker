package com.patenttracker.model;

import java.time.LocalDateTime;

public class PatentText {
    private int id;
    private int patentId;
    private String fullText;
    private Integer pageCount;
    private LocalDateTime extractedAt;

    public PatentText() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatentId() { return patentId; }
    public void setPatentId(int patentId) { this.patentId = patentId; }

    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }

    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }

    public LocalDateTime getExtractedAt() { return extractedAt; }
    public void setExtractedAt(LocalDateTime extractedAt) { this.extractedAt = extractedAt; }
}
