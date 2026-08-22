package com.patenttracker.dao;

import com.patenttracker.model.SearchAnalysis;
import com.patenttracker.model.SearchSession;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchAnalysisDaoTest {

    private Connection conn;
    private SearchSessionDao sessionDao;
    private SearchAnalysisDao analysisDao;
    private int sessionId;

    @BeforeEach
    void setUp() throws Exception {
        conn = PriorArtTestDbHelper.createTestConnection();
        ConnectionProvider provider = PriorArtTestDbHelper.testProvider(conn);
        sessionDao = new SearchSessionDao(provider);
        analysisDao = new SearchAnalysisDao(provider);

        sessionId = sessionDao.insert(SearchSession.builder()
                .ideaText("Test idea for analysis")
                .build());
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    @Test
    void insertOrUpdateAndFind() throws Exception {
        SearchAnalysis analysis = SearchAnalysis.builder()
                .sessionId(sessionId)
                .phase("DECOMPOSE")
                .resultJson("{\"key_concepts\": []}")
                .modelUsed("claude-sonnet-5")
                .durationMs(5000)
                .costUsd(0.0123)
                .build();

        analysisDao.insertOrUpdate(analysis);

        SearchAnalysis found = analysisDao.findBySessionAndPhase(sessionId, "DECOMPOSE");
        assertNotNull(found);
        assertEquals("{\"key_concepts\": []}", found.getResultJson());
        assertEquals("claude-sonnet-5", found.getModelUsed());
        assertEquals(5000, found.getDurationMs());
        assertEquals(0.0123, found.getCostUsd(), 0.0001);
    }

    @Test
    void insertOrUpdate_replacesExisting() throws Exception {
        analysisDao.insertOrUpdate(SearchAnalysis.builder()
                .sessionId(sessionId)
                .phase("ANALYZE")
                .resultJson("{\"version\": 1}")
                .build());

        analysisDao.insertOrUpdate(SearchAnalysis.builder()
                .sessionId(sessionId)
                .phase("ANALYZE")
                .resultJson("{\"version\": 2}")
                .build());

        SearchAnalysis found = analysisDao.findBySessionAndPhase(sessionId, "ANALYZE");
        assertEquals("{\"version\": 2}", found.getResultJson());

        List<SearchAnalysis> all = analysisDao.findBySessionId(sessionId);
        assertEquals(1, all.size());
    }

    @Test
    void findBySessionId() throws Exception {
        analysisDao.insertOrUpdate(SearchAnalysis.builder()
                .sessionId(sessionId).phase("DECOMPOSE").resultJson("{}").build());
        analysisDao.insertOrUpdate(SearchAnalysis.builder()
                .sessionId(sessionId).phase("ANALYZE").resultJson("{}").build());
        analysisDao.insertOrUpdate(SearchAnalysis.builder()
                .sessionId(sessionId).phase("DIFFERENTIATE").resultJson("{}").build());

        List<SearchAnalysis> all = analysisDao.findBySessionId(sessionId);
        assertEquals(3, all.size());
    }

    @Test
    void findBySessionAndPhase_notFound() throws Exception {
        SearchAnalysis found = analysisDao.findBySessionAndPhase(sessionId, "NONEXISTENT");
        assertNull(found);
    }
}
