package com.patenttracker.dao;

import com.patenttracker.model.CorpusDocument;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorpusDocumentDaoTest {

    private Connection conn;
    private CorpusDocumentDao documentDao;

    @BeforeEach
    void setUp() throws Exception {
        conn = PriorArtTestDbHelper.createTestConnection();
        documentDao = new CorpusDocumentDao(PriorArtTestDbHelper.testProvider(conn));
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    @Test
    void insertAndFindByPatentNumber() throws Exception {
        CorpusDocument doc = CorpusDocument.builder()
                .patentNumber("US12345678")
                .title("Test Patent")
                .abstractText("A method for testing")
                .assignee("Test Corp")
                .source("PATENTSVIEW")
                .domain("ai_ml")
                .build();

        int id = documentDao.insert(doc);
        assertTrue(id > 0);

        CorpusDocument found = documentDao.findByPatentNumber("US12345678");
        assertNotNull(found);
        assertEquals("Test Patent", found.getTitle());
        assertEquals("ai_ml", found.getDomain());
        assertEquals("PENDING", found.getDownloadStatus());
    }

    @Test
    void insertBatch() throws Exception {
        List<CorpusDocument> docs = List.of(
                CorpusDocument.builder().patentNumber("US001").title("A").source("PATENTSVIEW").domain("containers").build(),
                CorpusDocument.builder().patentNumber("US002").title("B").source("PATENTSVIEW").domain("containers").build(),
                CorpusDocument.builder().patentNumber("US003").title("C").source("PATENTSVIEW").domain("ai_ml").build()
        );

        int inserted = documentDao.insertBatch(docs);
        assertEquals(3, inserted);
        assertEquals(3, documentDao.countAll());
    }

    @Test
    void insertBatch_ignoresDuplicates() throws Exception {
        documentDao.insert(CorpusDocument.builder().patentNumber("US001").title("Existing").source("PATENTSVIEW").build());

        List<CorpusDocument> docs = List.of(
                CorpusDocument.builder().patentNumber("US001").title("Duplicate").source("PATENTSVIEW").build(),
                CorpusDocument.builder().patentNumber("US002").title("New").source("PATENTSVIEW").build()
        );

        documentDao.insertBatch(docs);
        assertEquals(2, documentDao.countAll());
    }

    @Test
    void updateDownloadStatus() throws Exception {
        int id = documentDao.insert(CorpusDocument.builder()
                .patentNumber("US999").title("Download Test").source("PATENTSVIEW").build());

        documentDao.updateDownloadStatus(id, "COMPLETE", "/path/to/pdf");

        CorpusDocument found = documentDao.findById(id);
        assertEquals("COMPLETE", found.getDownloadStatus());
        assertEquals("/path/to/pdf", found.getPdfPath());
    }

    @Test
    void findPendingDownloads() throws Exception {
        documentDao.insert(CorpusDocument.builder().patentNumber("US-P1").source("PATENTSVIEW").build());
        documentDao.insert(CorpusDocument.builder().patentNumber("US-P2").source("PATENTSVIEW").build());

        int id3 = documentDao.insert(CorpusDocument.builder().patentNumber("US-DL").source("PATENTSVIEW").build());
        documentDao.updateDownloadStatus(id3, "COMPLETE", "/tmp/test.pdf");

        List<CorpusDocument> pending = documentDao.findPendingDownloads(10);
        assertEquals(2, pending.size());
    }

    @Test
    void getStatusCounts() throws Exception {
        documentDao.insert(CorpusDocument.builder().patentNumber("US-A").source("PATENTSVIEW").build());
        int id = documentDao.insert(CorpusDocument.builder().patentNumber("US-B").source("PATENTSVIEW").build());
        documentDao.updateDownloadStatus(id, "COMPLETE", "/tmp/b.pdf");

        int[] stats = documentDao.getStatusCounts();
        assertEquals(2, stats[0]); // total
        assertEquals(1, stats[1]); // downloaded
    }

    @Test
    void deleteByDomain() throws Exception {
        documentDao.insert(CorpusDocument.builder().patentNumber("US-D1").source("PATENTSVIEW").domain("containers").build());
        documentDao.insert(CorpusDocument.builder().patentNumber("US-D2").source("PATENTSVIEW").domain("containers").build());
        documentDao.insert(CorpusDocument.builder().patentNumber("US-D3").source("PATENTSVIEW").domain("ai_ml").build());

        documentDao.deleteByDomain("containers");

        assertEquals(1, documentDao.countAll());
        assertNotNull(documentDao.findByPatentNumber("US-D3"));
    }
}
