package com.patenttracker.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DiscoveredPatent {
    private final int id;
    private final int sessionId;
    private final String patentNumber;
    private final String title;
    private final String abstractText;
    private final String assignee;
    private final LocalDate filingDate;
    private final LocalDate grantDate;
    private final String cpcCodes;
    private final String source;
    private final double relevanceScore;
    private final String matchedConceptsJson;
    private final LocalDateTime fetchedAt;

    private DiscoveredPatent(Builder b) {
        this.id = b.id;
        this.sessionId = b.sessionId;
        this.patentNumber = b.patentNumber;
        this.title = b.title;
        this.abstractText = b.abstractText;
        this.assignee = b.assignee;
        this.filingDate = b.filingDate;
        this.grantDate = b.grantDate;
        this.cpcCodes = b.cpcCodes;
        this.source = b.source;
        this.relevanceScore = b.relevanceScore;
        this.matchedConceptsJson = b.matchedConceptsJson;
        this.fetchedAt = b.fetchedAt;
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(DiscoveredPatent d) { return new Builder(d); }

    public int getId() { return id; }
    public int getSessionId() { return sessionId; }
    public String getPatentNumber() { return patentNumber; }
    public String getTitle() { return title; }
    public String getAbstractText() { return abstractText; }
    public String getAssignee() { return assignee; }
    public LocalDate getFilingDate() { return filingDate; }
    public LocalDate getGrantDate() { return grantDate; }
    public String getCpcCodes() { return cpcCodes; }
    public String getSource() { return source; }
    public double getRelevanceScore() { return relevanceScore; }
    public String getMatchedConceptsJson() { return matchedConceptsJson; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }

    public enum Source {
        GOOGLE_PATENTS, PATENTSVIEW
    }

    public static final class Builder {
        private int id;
        private int sessionId;
        private String patentNumber;
        private String title;
        private String abstractText;
        private String assignee;
        private LocalDate filingDate;
        private LocalDate grantDate;
        private String cpcCodes;
        private String source;
        private double relevanceScore;
        private String matchedConceptsJson;
        private LocalDateTime fetchedAt;

        private Builder() {}

        private Builder(DiscoveredPatent d) {
            this.id = d.id;
            this.sessionId = d.sessionId;
            this.patentNumber = d.patentNumber;
            this.title = d.title;
            this.abstractText = d.abstractText;
            this.assignee = d.assignee;
            this.filingDate = d.filingDate;
            this.grantDate = d.grantDate;
            this.cpcCodes = d.cpcCodes;
            this.source = d.source;
            this.relevanceScore = d.relevanceScore;
            this.matchedConceptsJson = d.matchedConceptsJson;
            this.fetchedAt = d.fetchedAt;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder sessionId(int sessionId) { this.sessionId = sessionId; return this; }
        public Builder patentNumber(String patentNumber) { this.patentNumber = patentNumber; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder abstractText(String abstractText) { this.abstractText = abstractText; return this; }
        public Builder assignee(String assignee) { this.assignee = assignee; return this; }
        public Builder filingDate(LocalDate filingDate) { this.filingDate = filingDate; return this; }
        public Builder grantDate(LocalDate grantDate) { this.grantDate = grantDate; return this; }
        public Builder cpcCodes(String cpcCodes) { this.cpcCodes = cpcCodes; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder relevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; return this; }
        public Builder matchedConceptsJson(String matchedConceptsJson) { this.matchedConceptsJson = matchedConceptsJson; return this; }
        public Builder fetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; return this; }

        public DiscoveredPatent build() { return new DiscoveredPatent(this); }
    }
}
