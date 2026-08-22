package com.patenttracker.dao;

import com.patenttracker.model.SearchSession;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchSessionDaoTest {

    private Connection conn;
    private SearchSessionDao sessionDao;

    @BeforeEach
    void setUp() throws Exception {
        conn = PriorArtTestDbHelper.createTestConnection();
        sessionDao = new SearchSessionDao(PriorArtTestDbHelper.testProvider(conn));
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    @Test
    void insertAndFind() throws Exception {
        SearchSession session = SearchSession.builder()
                .ideaText("A method for quantum-resistant authentication using lattice-based cryptography")
                .build();

        int id = sessionDao.insert(session);
        assertTrue(id > 0);

        SearchSession found = sessionDao.findById(id);
        assertNotNull(found);
        assertEquals("A method for quantum-resistant authentication using lattice-based cryptography",
                found.getIdeaText());
        assertEquals("PENDING", found.getStatus());
    }

    @Test
    void updateStatus() throws Exception {
        int id = sessionDao.insert(SearchSession.builder()
                .ideaText("Test idea")
                .build());

        sessionDao.updateStatus(id, "SEARCHING");

        SearchSession found = sessionDao.findById(id);
        assertEquals("SEARCHING", found.getStatus());
    }

    @Test
    void updateDecomposedJson() throws Exception {
        int id = sessionDao.insert(SearchSession.builder()
                .ideaText("Test idea")
                .build());

        String json = "{\"key_concepts\": []}";
        sessionDao.updateDecomposedJson(id, json);

        SearchSession found = sessionDao.findById(id);
        assertEquals(json, found.getDecomposedJson());
    }

    @Test
    void findAll_returnsAllSessions() throws Exception {
        sessionDao.insert(SearchSession.builder().ideaText("First idea").build());
        sessionDao.insert(SearchSession.builder().ideaText("Second idea").build());
        sessionDao.insert(SearchSession.builder().ideaText("Third idea").build());

        List<SearchSession> all = sessionDao.findAll();
        assertEquals(3, all.size());
    }

    @Test
    void delete() throws Exception {
        int id = sessionDao.insert(SearchSession.builder().ideaText("To delete").build());
        assertNotNull(sessionDao.findById(id));

        sessionDao.delete(id);
        assertNull(sessionDao.findById(id));
    }
}
