package com.patenttracker.model;

import java.time.LocalDateTime;

public final class StatusUpdate {
    private final int id;
    private final int patentId;
    private final String fieldName;
    private final String previousValue;
    private final String newValue;
    private final String source;
    private final LocalDateTime timestamp;

    private StatusUpdate(Builder b) {
        this.id = b.id;
        this.patentId = b.patentId;
        this.fieldName = b.fieldName;
        this.previousValue = b.previousValue;
        this.newValue = b.newValue;
        this.source = b.source;
        this.timestamp = b.timestamp;
    }

    public static StatusUpdate create(int patentId, String fieldName, String previousValue,
                                       String newValue, String source) {
        return builder()
                .patentId(patentId)
                .fieldName(fieldName)
                .previousValue(previousValue)
                .newValue(newValue)
                .source(source)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(StatusUpdate m) { return new Builder(m); }

    public int getId() { return id; }
    public int getPatentId() { return patentId; }
    public String getFieldName() { return fieldName; }
    public String getPreviousValue() { return previousValue; }
    public String getNewValue() { return newValue; }
    public String getSource() { return source; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public static final class Builder {
        private int id;
        private int patentId;
        private String fieldName;
        private String previousValue;
        private String newValue;
        private String source;
        private LocalDateTime timestamp;

        private Builder() {}

        private Builder(StatusUpdate m) {
            this.id = m.id;
            this.patentId = m.patentId;
            this.fieldName = m.fieldName;
            this.previousValue = m.previousValue;
            this.newValue = m.newValue;
            this.source = m.source;
            this.timestamp = m.timestamp;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder patentId(int patentId) { this.patentId = patentId; return this; }
        public Builder fieldName(String fieldName) { this.fieldName = fieldName; return this; }
        public Builder previousValue(String previousValue) { this.previousValue = previousValue; return this; }
        public Builder newValue(String newValue) { this.newValue = newValue; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }

        public StatusUpdate build() { return new StatusUpdate(this); }
    }
}
