package com.patenttracker.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class MinedPatent {
    private int id;
    private String patentNumber;
    private String title;
    private String abstractText;
    private LocalDate grantDate;
    private String searchArea;
    private String searchQuery;
    private LocalDateTime fetchedAt;

    public MinedPatent() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getPatentNumber() { return patentNumber; }
    public void setPatentNumber(String patentNumber) { this.patentNumber = patentNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAbstractText() { return abstractText; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; }

    public LocalDate getGrantDate() { return grantDate; }
    public void setGrantDate(LocalDate grantDate) { this.grantDate = grantDate; }

    public String getSearchArea() { return searchArea; }
    public void setSearchArea(String searchArea) { this.searchArea = searchArea; }

    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }

    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
