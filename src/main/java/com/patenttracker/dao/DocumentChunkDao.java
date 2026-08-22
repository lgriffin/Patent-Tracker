package com.patenttracker.dao;

import com.patenttracker.model.DocumentChunk;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class DocumentChunkDao {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConnectionProvider connectionProvider;

    public DocumentChunkDao() {
        this.connectionProvider = () -> PriorArtDatabaseManager.getInstance().getConnection();
    }

    public DocumentChunkDao(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public int insertBatch(List<DocumentChunk> chunks) throws SQLException {
        String sql = """
            INSERT INTO document_chunk
                (document_id, patent_number, section_type, chunk_index,
                 chunk_text, word_count, start_position, end_position, metadata_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        int inserted = 0;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (DocumentChunk chunk : chunks) {
                ps.setInt(1, chunk.getDocumentId());
                ps.setString(2, chunk.getPatentNumber());
                ps.setString(3, chunk.getSectionType());
                ps.setInt(4, chunk.getChunkIndex());
                ps.setString(5, chunk.getChunkText());
                ps.setInt(6, chunk.getWordCount());
                ps.setInt(7, chunk.getStartPosition());
                ps.setInt(8, chunk.getEndPosition());
                ps.setString(9, chunk.getMetadataJson());
                ps.addBatch();
                inserted++;
            }
            ps.executeBatch();
            conn.commit();
        }
        return inserted;
    }

    public List<DocumentChunk> findByDocumentId(int documentId) throws SQLException {
        String sql = "SELECT * FROM document_chunk WHERE document_id = ? ORDER BY chunk_index";
        return executeQuery(sql, ps -> ps.setInt(1, documentId));
    }

    public List<DocumentChunk> findByPatentNumber(String patentNumber) throws SQLException {
        String sql = "SELECT * FROM document_chunk WHERE patent_number = ? ORDER BY chunk_index";
        return executeQuery(sql, ps -> ps.setString(1, patentNumber));
    }

    public List<DocumentChunk> findBySection(int documentId, String sectionType) throws SQLException {
        String sql = "SELECT * FROM document_chunk WHERE document_id = ? AND section_type = ? ORDER BY chunk_index";
        return executeQuery(sql, ps -> {
            ps.setInt(1, documentId);
            ps.setString(2, sectionType);
        });
    }

    public List<DocumentChunk> searchFts(String query, int limit) throws SQLException {
        String sql = """
            SELECT dc.* FROM document_chunk dc
            JOIN chunk_fts fts ON dc.id = fts.rowid
            WHERE chunk_fts MATCH ?
            ORDER BY rank
            LIMIT ?
            """;
        return executeQuery(sql, ps -> {
            ps.setString(1, query);
            ps.setInt(2, limit);
        });
    }

    public List<DocumentChunk> searchFtsBySection(String query, String sectionType, int limit) throws SQLException {
        String sql = """
            SELECT dc.* FROM document_chunk dc
            JOIN chunk_fts fts ON dc.id = fts.rowid
            WHERE chunk_fts MATCH ? AND dc.section_type = ?
            ORDER BY rank
            LIMIT ?
            """;
        return executeQuery(sql, ps -> {
            ps.setString(1, query);
            ps.setString(2, sectionType);
            ps.setInt(3, limit);
        });
    }

    public int countByDocumentId(int documentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM document_chunk WHERE document_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, documentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM document_chunk";
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public void deleteByDocumentId(int documentId) throws SQLException {
        String sql = "DELETE FROM document_chunk WHERE document_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, documentId);
            ps.executeUpdate();
        }
    }

    public List<DocumentChunk> findAll(int limit, int offset) throws SQLException {
        String sql = "SELECT * FROM document_chunk ORDER BY id LIMIT ? OFFSET ?";
        return executeQuery(sql, ps -> {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
        });
    }

    private List<DocumentChunk> executeQuery(String sql, SqlConsumer<PreparedStatement> paramSetter) throws SQLException {
        List<DocumentChunk> results = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            paramSetter.accept(ps);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    private DocumentChunk mapRow(ResultSet rs) throws SQLException {
        return DocumentChunk.builder()
                .id(rs.getInt("id"))
                .documentId(rs.getInt("document_id"))
                .patentNumber(rs.getString("patent_number"))
                .sectionType(rs.getString("section_type"))
                .chunkIndex(rs.getInt("chunk_index"))
                .chunkText(rs.getString("chunk_text"))
                .wordCount(rs.getInt("word_count"))
                .startPosition(rs.getInt("start_position"))
                .endPosition(rs.getInt("end_position"))
                .metadataJson(rs.getString("metadata_json"))
                .createdAt(parseDateTime(rs.getString("created_at")))
                .build();
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s, DT_FMT); }
        catch (DateTimeParseException e) {
            try { return LocalDateTime.parse(s); }
            catch (DateTimeParseException e2) { return null; }
        }
    }

    @FunctionalInterface
    interface SqlConsumer<T> {
        void accept(T t) throws SQLException;
    }
}
