package com.patenttracker.model;

public record PatentTag(int id, int patentId, int tagId, String source) {
    public PatentTag(int patentId, int tagId) {
        this(0, patentId, tagId, "HUMAN");
    }

    public PatentTag(int patentId, int tagId, String source) {
        this(0, patentId, tagId, source);
    }
}
