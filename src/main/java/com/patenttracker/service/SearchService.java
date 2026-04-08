package com.patenttracker.service;

import com.patenttracker.dao.PatentDao;
import com.patenttracker.model.Patent;

import java.sql.SQLException;
import java.util.List;

public class SearchService {

    private final PatentDao patentDao;

    public SearchService() {
        this.patentDao = new PatentDao();
    }

    public SearchService(PatentDao patentDao) {
        this.patentDao = patentDao;
    }

    public List<Patent> search(String titleQuery, String status, Integer inventorId,
                               String classification, Integer yearFrom, Integer yearTo,
                               Integer tagId) throws SQLException {
        return patentDao.search(titleQuery, status, inventorId, classification, yearFrom, yearTo, tagId);
    }

    public int getTotalCount() throws SQLException {
        return patentDao.count();
    }

    public List<String> getAvailableStatuses() throws SQLException {
        return patentDao.getDistinctStatuses();
    }

    public List<String> getAvailableClassifications() throws SQLException {
        return patentDao.getDistinctClassifications();
    }

    public List<Integer> getAvailableFilingYears() throws SQLException {
        return patentDao.getDistinctFilingYears();
    }
}
