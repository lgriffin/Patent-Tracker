package com.patenttracker.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class MinedPatent {
    private final int id;
    private final String patentNumber;
    private final String title;
    private final String abstractText;
    private final LocalDate grantDate;
    private final String searchArea;
    private final String searchQuery;
    private final LocalDateTime fetchedAt;

    private MinedPatent(Builder b) {
        this.id = b.id;
        this.patentNumber = b.patentNumber;
        this.title = b.title;
        this.abstractText = b.abstractText;
        this.grantDate = b.grantDate;
        this.searchArea = b.searchArea;
        this.searchQuery = b.searchQuery;
        this.fetchedAt = b.fetchedAt;
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(MinedPatent m) { return new Builder(m); }

    public int getId() { return id; }
    public String getPatentNumber() { return patentNumber; }
    public String getTitle() { return title; }
    public String getAbstractText() { return abstractText; }
    public LocalDate getGrantDate() { return grantDate; }
    public String getSearchArea() { return searchArea; }
    public String getSearchQuery() { return searchQuery; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }

    public static final class Builder {
        private int id;
        private String patentNumber;
        private String title;
        private String abstractText;
        private LocalDate grantDate;
        private String searchArea;
        private String searchQuery;
        private LocalDateTime fetchedAt;

        private Builder() {}

        private Builder(MinedPatent m) {
            this.id = m.id;
            this.patentNumber = m.patentNumber;
            this.title = m.title;
            this.abstractText = m.abstractText;
            this.grantDate = m.grantDate;
            this.searchArea = m.searchArea;
            this.searchQuery = m.searchQuery;
            this.fetchedAt = m.fetchedAt;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder patentNumber(String patentNumber) { this.patentNumber = patentNumber; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder abstractText(String abstractText) { this.abstractText = abstractText; return this; }
        public Builder grantDate(LocalDate grantDate) { this.grantDate = grantDate; return this; }
        public Builder searchArea(String searchArea) { this.searchArea = searchArea; return this; }
        public Builder searchQuery(String searchQuery) { this.searchQuery = searchQuery; return this; }
        public Builder fetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; return this; }

        public MinedPatent build() { return new MinedPatent(this); }
    }
}
