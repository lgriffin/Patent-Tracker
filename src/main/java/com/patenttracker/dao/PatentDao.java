package com.patenttracker.dao;

import com.patenttracker.model.Patent;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class PatentDao {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Connection conn;

    public PatentDao() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    public PatentDao(Connection conn) {
        this.conn = conn;
    }

    public int insert(Patent p) throws SQLException {
        String sql = """
            INSERT INTO patent (file_number, title, filing_date, application_number,
                publication_date, publication_number, issue_grant_date, patent_number,
                pto_status, suffix, classification, parent_file_number, csv_row_number)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getFileNumber());
            ps.setString(2, p.getTitle());
            ps.setString(3, dateToStr(p.getFilingDate()));
            ps.setString(4, p.getApplicationNumber());
            ps.setString(5, dateToStr(p.getPublicationDate()));
            ps.setString(6, p.getPublicationNumber());
            ps.setString(7, dateToStr(p.getIssueGrantDate()));
            ps.setString(8, p.getPatentNumber());
            ps.setString(9, p.getPtoStatus());
            ps.setString(10, p.getSuffix());
            ps.setString(11, p.getClassification());
            ps.setString(12, p.getParentFileNumber());
            if (p.getCsvRowNumber() != null) {
                ps.setInt(13, p.getCsvRowNumber());
            } else {
                ps.setNull(13, Types.INTEGER);
            }
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                p.setId(id);
                return id;
            }
        }
        return -1;
    }

    public void update(Patent p) throws SQLException {
        String sql = """
            UPDATE patent SET title=?, filing_date=?, application_number=?,
                publication_date=?, publication_number=?, issue_grant_date=?, patent_number=?,
                pto_status=?, suffix=?, classification=?, parent_file_number=?, pdf_path=?,
                updated_at=datetime('now')
            WHERE id=?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getTitle());
            ps.setString(2, dateToStr(p.getFilingDate()));
            ps.setString(3, p.getApplicationNumber());
            ps.setString(4, dateToStr(p.getPublicationDate()));
            ps.setString(5, p.getPublicationNumber());
            ps.setString(6, dateToStr(p.getIssueGrantDate()));
            ps.setString(7, p.getPatentNumber());
            ps.setString(8, p.getPtoStatus());
            ps.setString(9, p.getSuffix());
            ps.setString(10, p.getClassification());
            ps.setString(11, p.getParentFileNumber());
            ps.setString(12, p.getPdfPath());
            ps.setInt(13, p.getId());
            ps.executeUpdate();
        }
    }

    public Patent findById(int id) throws SQLException {
        String sql = "SELECT p.*, i.full_name as primary_inventor_name " +
                     "FROM patent p LEFT JOIN patent_inventor pi ON p.id = pi.patent_id AND pi.role = 'PRIMARY' " +
                     "LEFT JOIN inventor i ON pi.inventor_id = i.id WHERE p.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        }
        return null;
    }

    public Patent findByFileNumber(String fileNumber) throws SQLException {
        String sql = "SELECT p.*, i.full_name as primary_inventor_name " +
                     "FROM patent p LEFT JOIN patent_inventor pi ON p.id = pi.patent_id AND pi.role = 'PRIMARY' " +
                     "LEFT JOIN inventor i ON pi.inventor_id = i.id WHERE p.file_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileNumber);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        }
        return null;
    }

    public List<Patent> findAll() throws SQLException {
        String sql = EXTENDED_SELECT + " ORDER BY p.filing_date DESC";
        return executeQuery(sql);
    }

    private static final String EXTENDED_SELECT =
        "SELECT p.*, " +
        "(SELECT i1.full_name FROM patent_inventor pi1 JOIN inventor i1 ON pi1.inventor_id = i1.id " +
        " WHERE pi1.patent_id = p.id AND pi1.role = 'PRIMARY' LIMIT 1) as primary_inventor_name, " +
        "(SELECT i2.full_name FROM patent_inventor pi2 JOIN inventor i2 ON pi2.inventor_id = i2.id " +
        " WHERE pi2.patent_id = p.id AND pi2.role = 'SECONDARY' LIMIT 1) as secondary_inventor_name, " +
        "(SELECT GROUP_CONCAT(i3.full_name, ', ') FROM patent_inventor pi3 JOIN inventor i3 ON pi3.inventor_id = i3.id " +
        " WHERE pi3.patent_id = p.id AND pi3.role = 'ADDITIONAL') as additional_inventor_names, " +
        "(SELECT GROUP_CONCAT(t.name, ', ') FROM patent_tag pt JOIN tag t ON pt.tag_id = t.id " +
        " WHERE pt.patent_id = p.id) as tag_names " +
        "FROM patent p";

    public List<Patent> search(String titleQuery, String status, Integer inventorId,
                               String classification, Integer yearFrom, Integer yearTo,
                               Integer tagId) throws SQLException {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // Base query with subqueries for display fields
        sql.append("SELECT DISTINCT p.*, ");
        sql.append("(SELECT i1.full_name FROM patent_inventor pi1 JOIN inventor i1 ON pi1.inventor_id = i1.id ");
        sql.append(" WHERE pi1.patent_id = p.id AND pi1.role = 'PRIMARY' LIMIT 1) as primary_inventor_name, ");
        sql.append("(SELECT i2.full_name FROM patent_inventor pi2 JOIN inventor i2 ON pi2.inventor_id = i2.id ");
        sql.append(" WHERE pi2.patent_id = p.id AND pi2.role = 'SECONDARY' LIMIT 1) as secondary_inventor_name, ");
        sql.append("(SELECT GROUP_CONCAT(i3.full_name, ', ') FROM patent_inventor pi3 JOIN inventor i3 ON pi3.inventor_id = i3.id ");
        sql.append(" WHERE pi3.patent_id = p.id AND pi3.role = 'ADDITIONAL') as additional_inventor_names, ");
        sql.append("(SELECT GROUP_CONCAT(t.name, ', ') FROM patent_tag pt JOIN tag t ON pt.tag_id = t.id ");
        sql.append(" WHERE pt.patent_id = p.id) as tag_names ");
        sql.append("FROM patent p ");

        // Join for inventor filter (any role)
        if (inventorId != null) {
            sql.append("JOIN patent_inventor pi_filter ON p.id = pi_filter.patent_id AND pi_filter.inventor_id = ? ");
            params.add(inventorId);
        }

        // Join for tag filter
        if (tagId != null) {
            sql.append("JOIN patent_tag pt ON p.id = pt.patent_id AND pt.tag_id = ? ");
            params.add(tagId);
        }

        // FTS title search
        if (titleQuery != null && !titleQuery.isBlank()) {
            sql.append("JOIN patent_fts fts ON p.id = fts.rowid ");
        }

        sql.append("WHERE 1=1 ");

        if (titleQuery != null && !titleQuery.isBlank()) {
            sql.append("AND patent_fts MATCH ? ");
            // Add wildcards for prefix matching
            params.add(titleQuery.trim() + "*");
        }

        if (status != null && !status.isBlank()) {
            sql.append("AND p.pto_status = ? ");
            params.add(status);
        }

        if (classification != null && !classification.isBlank()) {
            sql.append("AND p.classification = ? ");
            params.add(classification);
        }

        if (yearFrom != null) {
            sql.append("AND p.filing_date >= ? ");
            params.add(yearFrom + "-01-01");
        }

        if (yearTo != null) {
            sql.append("AND p.filing_date <= ? ");
            params.add(yearTo + "-12-31");
        }

        sql.append("ORDER BY p.filing_date DESC");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int idx = 0; idx < params.size(); idx++) {
                Object param = params.get(idx);
                if (param instanceof Integer) {
                    ps.setInt(idx + 1, (Integer) param);
                } else {
                    ps.setString(idx + 1, param.toString());
                }
            }
            return mapResults(ps.executeQuery());
        }
    }

    public List<Patent> findByParentFileNumber(String parentFileNumber) throws SQLException {
        String sql = "SELECT p.*, i.full_name as primary_inventor_name " +
                     "FROM patent p LEFT JOIN patent_inventor pi ON p.id = pi.patent_id AND pi.role = 'PRIMARY' " +
                     "LEFT JOIN inventor i ON pi.inventor_id = i.id WHERE p.parent_file_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, parentFileNumber);
            return mapResults(ps.executeQuery());
        }
    }

    public int count() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM patent");
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public List<String> getDistinctStatuses() throws SQLException {
        List<String> statuses = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT DISTINCT pto_status FROM patent ORDER BY pto_status");
            while (rs.next()) {
                String s = rs.getString(1);
                if (s != null) statuses.add(s);
            }
        }
        return statuses;
    }

    public List<String> getDistinctClassifications() throws SQLException {
        List<String> results = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT DISTINCT classification FROM patent WHERE classification IS NOT NULL AND classification != '' ORDER BY classification"
            );
            while (rs.next()) {
                results.add(rs.getString(1));
            }
        }
        return results;
    }

    public List<Integer> getDistinctFilingYears() throws SQLException {
        List<Integer> years = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT DISTINCT CAST(substr(filing_date,1,4) AS INTEGER) as yr FROM patent " +
                "WHERE filing_date IS NOT NULL ORDER BY yr"
            );
            while (rs.next()) {
                years.add(rs.getInt(1));
            }
        }
        return years;
    }

    private List<Patent> executeQuery(String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            return mapResults(stmt.executeQuery(sql));
        }
    }

    private List<Patent> mapResults(ResultSet rs) throws SQLException {
        List<Patent> results = new ArrayList<>();
        while (rs.next()) {
            results.add(mapRow(rs));
        }
        return results;
    }

    private Patent mapRow(ResultSet rs) throws SQLException {
        Patent p = new Patent();
        p.setId(rs.getInt("id"));
        p.setFileNumber(rs.getString("file_number"));
        p.setTitle(rs.getString("title"));
        p.setFilingDate(parseDate(rs.getString("filing_date")));
        p.setApplicationNumber(rs.getString("application_number"));
        p.setPublicationDate(parseDate(rs.getString("publication_date")));
        p.setPublicationNumber(rs.getString("publication_number"));
        p.setIssueGrantDate(parseDate(rs.getString("issue_grant_date")));
        p.setPatentNumber(rs.getString("patent_number"));
        p.setPtoStatus(rs.getString("pto_status"));
        p.setSuffix(rs.getString("suffix"));
        p.setClassification(rs.getString("classification"));
        p.setParentFileNumber(rs.getString("parent_file_number"));
        int csvRow = rs.getInt("csv_row_number");
        p.setCsvRowNumber(rs.wasNull() ? null : csvRow);
        p.setPdfPath(rs.getString("pdf_path"));
        p.setCreatedAt(parseDateTime(rs.getString("created_at")));
        p.setUpdatedAt(parseDateTime(rs.getString("updated_at")));

        trySetString(rs, "primary_inventor_name", p::setPrimaryInventorName);
        trySetString(rs, "secondary_inventor_name", p::setSecondaryInventorName);
        trySetString(rs, "additional_inventor_names", p::setAdditionalInventorNames);
        trySetString(rs, "tag_names", p::setTagNames);

        return p;
    }

    private void trySetString(ResultSet rs, String column, java.util.function.Consumer<String> setter) {
        try {
            setter.accept(rs.getString(column));
        } catch (SQLException e) {
            // Column may not exist in all queries
        }
    }

    private String dateToStr(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : null;
    }

    public static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            // Handle "2019-08-13 0:00:00" format
            String cleaned = s.trim().split("\\s")[0];
            return LocalDate.parse(cleaned, DATE_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s, DATETIME_FMT);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(s.split("\\s")[0], DATE_FMT).atStartOfDay();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
