package com.patenttracker.model;

import java.time.LocalDateTime;

public class PatentTag {
    private int id;
    private int patentId;
    private int tagId;
    private String source; // HUMAN or AI
    private LocalDateTime createdAt;

    public PatentTag() {}

    public PatentTag(int patentId, int tagId) {
        this.patentId = patentId;
        this.tagId = tagId;
        this.source = "HUMAN";
    }

    public PatentTag(int patentId, int tagId, String source) {
        this.patentId = patentId;
        this.tagId = tagId;
        this.source = source;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatentId() { return patentId; }
    public void setPatentId(int patentId) { this.patentId = patentId; }

    public int getTagId() { return tagId; }
    public void setTagId(int tagId) { this.tagId = tagId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
