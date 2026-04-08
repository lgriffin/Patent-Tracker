package com.patenttracker.model;

import java.time.LocalDateTime;

public class Tag {
    private int id;
    private String name;
    private LocalDateTime createdAt;

    // Transient
    private int patentCount;
    private String source; // HUMAN or AI — set when loaded in patent context

    public Tag() {}

    public Tag(String name) {
        this.name = name;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getPatentCount() { return patentCount; }
    public void setPatentCount(int patentCount) { this.patentCount = patentCount; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public boolean isAiGenerated() { return "AI".equals(source); }

    @Override
    public String toString() { return name; }
}
