package com.patenttracker.model;

import java.time.LocalDateTime;

public class Inventor {
    private int id;
    private String fullName;
    private String username;
    private LocalDateTime createdAt;

    // Transient fields for display
    private int patentCount;

    public Inventor() {}

    public Inventor(String fullName, String username) {
        this.fullName = fullName;
        this.username = username;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getPatentCount() { return patentCount; }
    public void setPatentCount(int patentCount) { this.patentCount = patentCount; }

    public String getDisplayName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        return username != null ? username : "Unknown";
    }
}
