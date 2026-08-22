package com.patenttracker.dao;

import com.patenttracker.model.DiscoveredPatent;
import com.patenttracker.model.SearchSession;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveredPatentDaoTest {

    private Connection conn;
    private SearchSessionDao sessionDao;
    private DiscoveredPatentDao discoveredPatentDao;
    private int sessionId;

    @BeforeEach
    void setUp() throws Exception {
        conn = PriorArtTestDbHelper.createTestConnection();
        ConnectionProvider provider = PriorArtTestDbHelper.testProvider(conn);
        sessionDao = new SearchSessionDao(provider);
        discoveredPatentDao = new DiscoveredPatentDao(provider);

        sessionId = sessionDao.insert(SearchSession.builder()
                .ideaText("Test idea for discovered patents")
                .build());
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private DiscoveredPatent makePatent(String number, String title, String source) {
        return DiscoveredPatent.builder()
                .sessionId(sessionId)
                .patentNumber(number)
                .title(title)
                .abstractText("Abstract for " + title)
                .assignee("Test Corp")
                .grantDate(LocalDate.of(2024, 6, 15))
                .source(source)
                .relevanceScore(0.85)
                .build();
    }

    @Test
    void insertAndFind() throws Exception {
        discoveredPatentDao.insert(makePatent("US12345678", "Test Patent", "GOOGLE_PATENTS"));

        List<DiscoveredPatent> found = discoveredPatentDao.findBySessionId(sessionId);
        assertEquals(1, found.size());
        assertEquals("US12345678", found.get(0).getPatentNumber());
        assertEquals("Test Patent", found.get(0).getTitle());
        assertEquals("Test Corp", found.get(0).getAssignee());
        assertEquals("GOOGLE_PATENTS", found.get(0).getSource());
        assertEquals(0.85, found.get(0).getRelevanceScore(), 0.01);
    }

    @Test
    void insertBatch() throws Exception {
        List<DiscoveredPatent> batch = List.of(
                makePatent("US001", "Patent A", "GOOGLE_PATENTS"),
                makePatent("US002", "Patent B", "PATENTSVIEW"),
                makePatent("US003", "Patent C", "GOOGLE_PATENTS")
        );
        discoveredPatentDao.insertBatch(batch);

        assertEquals(3, discoveredPatentDao.countBySessionId(sessionId));

        List<DiscoveredPatent> found = discoveredPatentDao.findBySessionId(sessionId);
        assertEquals(3, found.size());
    }

    @Test
    void findBySessionId_orderedByRelevance() throws Exception {
        discoveredPatentDao.insert(DiscoveredPatent.builder()
                .sessionId(sessionId).patentNumber("US-LOW").title("Low")
                .source("GOOGLE_PATENTS").relevanceScore(0.3).build());
        discoveredPatentDao.insert(DiscoveredPatent.builder()
                .sessionId(sessionId).patentNumber("US-HIGH").title("High")
                .source("GOOGLE_PATENTS").relevanceScore(0.95).build());
        discoveredPatentDao.insert(DiscoveredPatent.builder()
                .sessionId(sessionId).patentNumber("US-MID").title("Mid")
                .source("PATENTSVIEW").relevanceScore(0.7).build());

        List<DiscoveredPatent> found = discoveredPatentDao.findBySessionId(sessionId);
        assertEquals("US-HIGH", found.get(0).getPatentNumber());
        assertEquals("US-MID", found.get(1).getPatentNumber());
        assertEquals("US-LOW", found.get(2).getPatentNumber());
    }

    @Test
    void deleteBySessionId() throws Exception {
        discoveredPatentDao.insert(makePatent("US-DEL1", "Del 1", "GOOGLE_PATENTS"));
        discoveredPatentDao.insert(makePatent("US-DEL2", "Del 2", "PATENTSVIEW"));

        assertEquals(2, discoveredPatentDao.countBySessionId(sessionId));

        discoveredPatentDao.deleteBySessionId(sessionId);

        assertEquals(0, discoveredPatentDao.countBySessionId(sessionId));
    }

    @Test
    void cascadeDeleteOnSessionDelete() throws Exception {
        discoveredPatentDao.insert(makePatent("US-CASCADE", "Cascade", "GOOGLE_PATENTS"));
        assertEquals(1, discoveredPatentDao.countBySessionId(sessionId));

        sessionDao.delete(sessionId);

        assertEquals(0, discoveredPatentDao.countBySessionId(sessionId));
    }
}
