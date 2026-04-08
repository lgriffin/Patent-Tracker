package com.patenttracker.model;

import java.time.LocalDateTime;

public class StatusUpdate {
    private int id;
    private int patentId;
    private String fieldName;
    private String previousValue;
    private String newValue;
    private String source; // CSV_IMPORT, USPTO_SYNC, MANUAL_EDIT
    private LocalDateTime timestamp;

    public StatusUpdate() {}

    public StatusUpdate(int patentId, String fieldName, String previousValue, String newValue, String source) {
        this.patentId = patentId;
        this.fieldName = fieldName;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.source = source;
        this.timestamp = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatentId() { return patentId; }
    public void setPatentId(int patentId) { this.patentId = patentId; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getPreviousValue() { return previousValue; }
    public void setPreviousValue(String previousValue) { this.previousValue = previousValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
