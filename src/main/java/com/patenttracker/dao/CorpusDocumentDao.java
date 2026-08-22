package com.patenttracker.dao;

import com.patenttracker.model.CorpusDocument;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class CorpusDocumentDao {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConnectionProvider connectionProvider;

    public CorpusDocumentDao() {
        this.connectionProvider = () -> PriorArtDatabaseManager.getInstance().getConnection();
    }

    public CorpusDocumentDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public int insert(CorpusDocument doc) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO corpus_document
                (patent_number, title, abstract_text, assignee, filing_date, grant_date,
                 cpc_codes, source, domain, download_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, doc.getPatentNumber());
            ps.setString(2, doc.getTitle());
            ps.setString(3, doc.getAbstractText());
            ps.setString(4, doc.getAssignee());
            ps.setString(5, doc.getFilingDate());
            ps.setString(6, doc.getGrantDate());
            ps.setString(7, doc.getCpcCodes());
            ps.setString(8, doc.getSource());
            ps.setString(9, doc.getDomain());
            ps.setString(10, doc.getDownloadStatus());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        return -1;
    }

    public int insertBatch(List<CorpusDocument> docs) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO corpus_document
                (patent_number, title, abstract_text, assignee, filing_date, grant_date,
                 cpc_codes, source, domain, download_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        int inserted = 0;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (CorpusDocument doc : docs) {
                ps.setString(1, doc.getPatentNumber());
                ps.setString(2, doc.getTitle());
                ps.setString(3, doc.getAbstractText());
                ps.setString(4, doc.getAssignee());
                ps.setString(5, doc.getFilingDate());
                ps.setString(6, doc.getGrantDate());
                ps.setString(7, doc.getCpcCodes());
                ps.setString(8, doc.getSource());
                ps.setString(9, doc.getDomain());
                ps.setString(10, doc.getDownloadStatus() != null ? doc.getDownloadStatus() : "PENDING");
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            conn.commit();
            for (int r : results) {
                if (r > 0 || r == Statement.SUCCESS_NO_INFO) inserted++;
            }
        }
        return inserted;
    }

    public int resetFailedDownloads() throws SQLException {
        String sql = "UPDATE corpus_document SET download_status = 'PENDING', pdf_path = NULL, updated_at = datetime('now') WHERE download_status IN ('FAILED', 'IN_PROGRESS')";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    public void updateDownloadStatus(int id, String status, String pdfPath) throws SQLException {
        String sql = "UPDATE corpus_document SET download_status = ?, pdf_path = ?, updated_at = datetime('now') WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, pdfPath);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public void updateExtractionStatus(int id, String status, String fullText, int pageCount, int wordCount) throws SQLException {
        String sql = """
            UPDATE corpus_document
            SET extraction_status = ?, full_text = ?, page_count = ?, word_count = ?, updated_at = datetime('now')
            WHERE id = ?
            """;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, fullText);
            ps.setInt(3, pageCount);
            ps.setInt(4, wordCount);
            ps.setInt(5, id);
            ps.executeUpdate();
        }
    }

    public void updateChunkingStatus(int id, String status, int chunkCount) throws SQLException {
        String sql = "UPDATE corpus_document SET chunking_status = ?, chunk_count = ?, updated_at = datetime('now') WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, chunkCount);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public CorpusDocument findById(int id) throws SQLException {
        String sql = "SELECT * FROM corpus_document WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    public CorpusDocument findByPatentNumber(String patentNumber) throws SQLException {
        String sql = "SELECT * FROM corpus_document WHERE patent_number = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patentNumber);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    public List<CorpusDocument> findByStatus(String downloadStatus) throws SQLException {
        String sql = "SELECT * FROM corpus_document WHERE download_status = ? ORDER BY created_at";
        List<CorpusDocument> results = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, downloadStatus);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    public List<CorpusDocument> findByDomain(String domain) throws SQLException {
        String sql = "SELECT * FROM corpus_document WHERE domain = ? ORDER BY patent_number";
        List<CorpusDocument> results = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, domain);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    public List<CorpusDocument> findPendingDownloads(int limit) throws SQLException {
        String sql = "SELECT * FROM corpus_document WHERE download_status = 'PENDING' ORDER BY created_at LIMIT ?";
        List<CorpusDocument> results = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    public List<CorpusDocument> findPendingExtraction(int limit) throws SQLException {
        String sql = """
            SELECT * FROM corpus_document
            WHERE download_status = 'COMPLETE' AND extraction_status = 'PENDING'
            ORDER BY created_at LIMIT ?
            """;
        List<CorpusDocument> results = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    public List<CorpusDocument> findPendingChunking(int limit) throws SQLException {
        String sql = """
            SELECT * FROM corpus_document
            WHERE extraction_status = 'COMPLETE' AND chunking_status = 'PENDING'
            ORDER BY created_at LIMIT ?
            """;
        List<CorpusDocument> results = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    public int countAll() throws SQLException {
        return countByColumn(null, null);
    }

    public int countByDomain(String domain) throws SQLException {
        return countByColumn("domain", domain);
    }

    public int[] getStatusCounts() throws SQLException {
        String sql = """
            SELECT
                COUNT(*) as total,
                SUM(CASE WHEN download_status = 'COMPLETE' THEN 1 ELSE 0 END) as downloaded,
                SUM(CASE WHEN extraction_status = 'COMPLETE' THEN 1 ELSE 0 END) as extracted,
                SUM(CASE WHEN chunking_status = 'COMPLETE' THEN 1 ELSE 0 END) as chunked,
                COALESCE(SUM(chunk_count), 0) as total_chunks,
                COALESCE(SUM(word_count), 0) as total_words
            FROM corpus_document
            """;
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new int[]{
                    rs.getInt("total"),
                    rs.getInt("downloaded"),
                    rs.getInt("extracted"),
                    rs.getInt("chunked"),
                    rs.getInt("total_chunks"),
                    rs.getInt("total_words")
                };
            }
        }
        return new int[]{0, 0, 0, 0, 0, 0};
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM corpus_document WHERE id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public void deleteByDomain(String domain) throws SQLException {
        String sql = "DELETE FROM corpus_document WHERE domain = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, domain);
            ps.executeUpdate();
        }
    }

    private int countByColumn(String column, String value) throws SQLException {
        String sql = column != null
            ? "SELECT COUNT(*) FROM corpus_document WHERE " + column + " = ?"
            : "SELECT COUNT(*) FROM corpus_document";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (column != null) ps.setString(1, value);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    private CorpusDocument mapRow(ResultSet rs) throws SQLException {
        return CorpusDocument.builder()
                .id(rs.getInt("id"))
                .patentNumber(rs.getString("patent_number"))
                .title(rs.getString("title"))
                .abstractText(rs.getString("abstract_text"))
                .assignee(rs.getString("assignee"))
                .filingDate(rs.getString("filing_date"))
                .grantDate(rs.getString("grant_date"))
                .cpcCodes(rs.getString("cpc_codes"))
                .source(rs.getString("source"))
                .pdfPath(rs.getString("pdf_path"))
                .fullText(rs.getString("full_text"))
                .pageCount(rs.getInt("page_count"))
                .wordCount(rs.getInt("word_count"))
                .chunkCount(rs.getInt("chunk_count"))
                .downloadStatus(rs.getString("download_status"))
                .extractionStatus(rs.getString("extraction_status"))
                .chunkingStatus(rs.getString("chunking_status"))
                .domain(rs.getString("domain"))
                .createdAt(parseDateTime(rs.getString("created_at")))
                .updatedAt(parseDateTime(rs.getString("updated_at")))
                .build();
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s, DT_FMT);
        } catch (DateTimeParseException e) {
            try { return LocalDateTime.parse(s); }
            catch (DateTimeParseException e2) { return null; }
        }
    }
}
