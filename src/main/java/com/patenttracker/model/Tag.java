package com.patenttracker.model;

import java.time.LocalDateTime;

public final class Tag {
    private final int id;
    private final String name;
    private final LocalDateTime createdAt;
    private final int patentCount;
    private final String source;

    private Tag(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.createdAt = b.createdAt;
        this.patentCount = b.patentCount;
        this.source = b.source;
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(Tag m) { return new Builder(m); }

    public int getId() { return id; }
    public String getName() { return name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public int getPatentCount() { return patentCount; }
    public String getSource() { return source; }

    public boolean isAiGenerated() { return "AI".equals(source); }

    @Override
    public String toString() { return name; }

    public static final class Builder {
        private int id;
        private String name;
        private LocalDateTime createdAt;
        private int patentCount;
        private String source;

        private Builder() {}

        private Builder(Tag m) {
            this.id = m.id;
            this.name = m.name;
            this.createdAt = m.createdAt;
            this.patentCount = m.patentCount;
            this.source = m.source;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder patentCount(int patentCount) { this.patentCount = patentCount; return this; }
        public Builder source(String source) { this.source = source; return this; }

        public Tag build() { return new Tag(this); }
    }
}
