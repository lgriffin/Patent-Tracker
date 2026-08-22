package com.patenttracker.model;

import java.util.Arrays;
import java.util.List;

public final class CorpusDomain {
    private final int id;
    private final String name;
    private final String displayName;
    private final String cpcCodes;
    private final String keywords;
    private final String description;

    private CorpusDomain(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.displayName = b.displayName;
        this.cpcCodes = b.cpcCodes;
        this.keywords = b.keywords;
        this.description = b.description;
    }

    public static Builder builder() { return new Builder(); }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getCpcCodes() { return cpcCodes; }
    public String getKeywords() { return keywords; }
    public String getDescription() { return description; }

    public List<String> getCpcCodeList() {
        if (cpcCodes == null || cpcCodes.isBlank()) return List.of();
        return Arrays.asList(cpcCodes.split(","));
    }

    public List<String> getKeywordList() {
        if (keywords == null || keywords.isBlank()) return List.of();
        return Arrays.asList(keywords.split(","));
    }

    public static final class Builder {
        private int id;
        private String name;
        private String displayName;
        private String cpcCodes;
        private String keywords;
        private String description;

        private Builder() {}

        public Builder id(int id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder cpcCodes(String cpcCodes) { this.cpcCodes = cpcCodes; return this; }
        public Builder keywords(String keywords) { this.keywords = keywords; return this; }
        public Builder description(String description) { this.description = description; return this; }

        public CorpusDomain build() { return new CorpusDomain(this); }
    }
}
