package com.patenttracker.model;

public class PatentInventor {
    private int id;
    private int patentId;
    private int inventorId;
    private String role; // PRIMARY, SECONDARY, ADDITIONAL
    private int rolePosition; // 1=Primary, 2=Secondary, 3-5=Additional

    public PatentInventor() {}

    public PatentInventor(int patentId, int inventorId, String role, int rolePosition) {
        this.patentId = patentId;
        this.inventorId = inventorId;
        this.role = role;
        this.rolePosition = rolePosition;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatentId() { return patentId; }
    public void setPatentId(int patentId) { this.patentId = patentId; }

    public int getInventorId() { return inventorId; }
    public void setInventorId(int inventorId) { this.inventorId = inventorId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public int getRolePosition() { return rolePosition; }
    public void setRolePosition(int rolePosition) { this.rolePosition = rolePosition; }
}
