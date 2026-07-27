package com.patenttracker.model;

import java.time.LocalDateTime;

public final class Inventor {
    private final int id;
    private final String fullName;
    private final String username;
    private final LocalDateTime createdAt;
    private final int patentCount;

    private Inventor(Builder b) {
        this.id = b.id;
        this.fullName = b.fullName;
        this.username = b.username;
        this.createdAt = b.createdAt;
        this.patentCount = b.patentCount;
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(Inventor m) { return new Builder(m); }

    public int getId() { return id; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public int getPatentCount() { return patentCount; }

    public String getDisplayName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        return username != null ? username : "Unknown";
    }

    public static final class Builder {
        private int id;
        private String fullName;
        private String username;
        private LocalDateTime createdAt;
        private int patentCount;

        private Builder() {}

        private Builder(Inventor m) {
            this.id = m.id;
            this.fullName = m.fullName;
            this.username = m.username;
            this.createdAt = m.createdAt;
            this.patentCount = m.patentCount;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder fullName(String fullName) { this.fullName = fullName; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder patentCount(int patentCount) { this.patentCount = patentCount; return this; }

        public Inventor build() { return new Inventor(this); }
    }
}
