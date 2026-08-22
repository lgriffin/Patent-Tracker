package com.patenttracker.dao;

import com.patenttracker.model.CorpusDocument;
import com.patenttracker.model.DocumentChunk;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkDaoTest {

    private Connection conn;
    private CorpusDocumentDao documentDao;
    private DocumentChunkDao chunkDao;
    private int documentId;

    @BeforeEach
    void setUp() throws Exception {
        conn = PriorArtTestDbHelper.createTestConnection();
        ConnectionProvider provider = PriorArtTestDbHelper.testProvider(conn);
        documentDao = new CorpusDocumentDao(provider);
        chunkDao = new DocumentChunkDao(provider);

        documentId = documentDao.insert(CorpusDocument.builder()
                .patentNumber("US12345678")
                .title("Test Patent for Chunking")
                .source("PATENTSVIEW")
                .domain("ai_ml")
                .build());
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    @Test
    void insertBatchAndFindByDocumentId() throws Exception {
        List<DocumentChunk> chunks = List.of(
                DocumentChunk.builder()
                        .documentId(documentId).patentNumber("US12345678")
                        .sectionType("ABSTRACT").chunkIndex(0)
                        .chunkText("A method for processing data using machine learning")
                        .wordCount(8).build(),
                DocumentChunk.builder()
                        .documentId(documentId).patentNumber("US12345678")
                        .sectionType("CLAIMS").chunkIndex(1)
                        .chunkText("1. A method comprising: receiving input data")
                        .wordCount(7).build()
        );

        int inserted = chunkDao.insertBatch(chunks);
        assertEquals(2, inserted);

        List<DocumentChunk> found = chunkDao.findByDocumentId(documentId);
        assertEquals(2, found.size());
        assertEquals("ABSTRACT", found.get(0).getSectionType());
        assertEquals("CLAIMS", found.get(1).getSectionType());
    }

    @Test
    void findBySection() throws Exception {
        chunkDao.insertBatch(List.of(
                DocumentChunk.builder().documentId(documentId).patentNumber("US12345678")
                        .sectionType("ABSTRACT").chunkIndex(0).chunkText("Abstract text").wordCount(2).build(),
                DocumentChunk.builder().documentId(documentId).patentNumber("US12345678")
                        .sectionType("CLAIMS").chunkIndex(1).chunkText("Claim text").wordCount(2).build(),
                DocumentChunk.builder().documentId(documentId).patentNumber("US12345678")
                        .sectionType("CLAIMS").chunkIndex(2).chunkText("Another claim").wordCount(2).build()
        ));

        List<DocumentChunk> claims = chunkDao.findBySection(documentId, "CLAIMS");
        assertEquals(2, claims.size());
    }

    @Test
    void searchFts() throws Exception {
        chunkDao.insertBatch(List.of(
                DocumentChunk.builder().documentId(documentId).patentNumber("US12345678")
                        .sectionType("ABSTRACT").chunkIndex(0)
                        .chunkText("A method for neural network training optimization").wordCount(7).build(),
                DocumentChunk.builder().documentId(documentId).patentNumber("US12345678")
                        .sectionType("CLAIMS").chunkIndex(1)
                        .chunkText("Container orchestration system for distributed computing").wordCount(6).build()
        ));

        List<DocumentChunk> results = chunkDao.searchFts("neural network", 10);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getChunkText().contains("neural"));
    }

    @Test
    void countByDocumentId() throws Exception {
        chunkDao.insertBatch(List.of(
                DocumentChunk.builder().documentId(documentId).patentNumber("US12345678")
                        .sectionType("ABSTRACT").chunkIndex(0).chunkText("A").wordCount(1).build(),
                DocumentChunk.builder().documentId(documentId).patentNumber("US12345678")
                        .sectionType("CLAIMS").chunkIndex(1).chunkText("B").wordCount(1).build()
        ));

        assertEquals(2, chunkDao.countByDocumentId(documentId));
    }

    @Test
    void deleteByDocumentId() throws Exception {
        chunkDao.insertBatch(List.of(
                DocumentChunk.builder().documentId(documentId).patentNumber("US12345678")
                        .sectionType("ABSTRACT").chunkIndex(0).chunkText("Delete me").wordCount(2).build()
        ));

        assertEquals(1, chunkDao.countByDocumentId(documentId));

        chunkDao.deleteByDocumentId(documentId);
        assertEquals(0, chunkDao.countByDocumentId(documentId));
    }

    @Test
    void cascadeDeleteOnDocumentDelete() throws Exception {
        chunkDao.insertBatch(List.of(
                DocumentChunk.builder().documentId(documentId).patentNumber("US12345678")
                        .sectionType("ABSTRACT").chunkIndex(0).chunkText("Cascade me").wordCount(2).build()
        ));

        assertEquals(1, chunkDao.countByDocumentId(documentId));

        documentDao.delete(documentId);
        assertEquals(0, chunkDao.countByDocumentId(documentId));
    }
}
