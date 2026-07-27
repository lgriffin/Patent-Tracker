package com.patenttracker.model;

public record PatentInventor(int id, int patentId, int inventorId, String role, int rolePosition) {
    public PatentInventor(int patentId, int inventorId, String role, int rolePosition) {
        this(0, patentId, inventorId, role, rolePosition);
    }
}
