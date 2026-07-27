package com.patenttracker.dao;

import com.patenttracker.model.Patent;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PatentDaoTest {

    private Connection conn;
    private PatentDao dao;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestDbHelper.createTestConnection();
        dao = new PatentDao(TestDbHelper.testProvider(conn));
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private Patent makePatent(String fileNumber, String title, String status) {
        return Patent.builder()
                .fileNumber(fileNumber)
                .title(title)
                .ptoStatus(status)
                .suffix("US")
                .build();
    }

    @Test
    void insertAndFindById() throws Exception {
        Patent p = Patent.builder()
                .fileNumber("FN-001")
                .title("Quantum Key Distribution")
                .ptoStatus("Filed")
                .suffix("US")
                .applicationNumber("15/123456")
                .patentNumber("US12345678")
                .classification("H04L")
                .build();

        int id = dao.insert(p);
        assertTrue(id > 0);

        Patent found = dao.findById(id);
        assertNotNull(found);
        assertEquals("FN-001", found.getFileNumber());
        assertEquals("Quantum Key Distribution", found.getTitle());
        assertEquals("Filed", found.getPtoStatus());
        assertEquals("US", found.getSuffix());
        assertEquals("15/123456", found.getApplicationNumber());
        assertEquals("US12345678", found.getPatentNumber());
        assertEquals("H04L", found.getClassification());
    }

    @Test
    void findByFileNumber() throws Exception {
        Patent p = makePatent("FN-002", "Container Migration", "Pending");
        dao.insert(p);

        Patent found = dao.findByFileNumber("FN-002");
        assertNotNull(found);
        assertEquals("Container Migration", found.getTitle());
    }

    @Test
    void findAll() throws Exception {
        dao.insert(makePatent("FN-A", "Patent A", "Filed"));
        dao.insert(makePatent("FN-B", "Patent B", "Pending"));
        dao.insert(makePatent("FN-C", "Patent C", "Issued"));

        List<Patent> all = dao.findAll();
        assertEquals(3, all.size());
    }

    @Test
    void count() throws Exception {
        dao.insert(makePatent("FN-X", "Patent X", "Filed"));
        dao.insert(makePatent("FN-Y", "Patent Y", "Pending"));

        assertEquals(2, dao.count());
    }

    @Test
    void update() throws Exception {
        Patent p = makePatent("FN-U", "Original Title", "Filed");
        int id = dao.insert(p);

        Patent inserted = dao.findById(id);
        Patent updated = Patent.builder(inserted)
                .title("Updated Title")
                .ptoStatus("Allowed")
                .build();
        dao.update(updated);

        Patent found = dao.findById(id);
        assertNotNull(found);
        assertEquals("Updated Title", found.getTitle());
        assertEquals("Allowed", found.getPtoStatus());
    }

    @Test
    void search_byTitle() throws Exception {
        dao.insert(makePatent("FN-S1", "Quantum Entanglement System", "Filed"));
        dao.insert(makePatent("FN-S2", "Container Orchestration Method", "Filed"));
        dao.insert(makePatent("FN-S3", "Quantum Key Distribution", "Pending"));

        List<Patent> results = dao.search("Quantum", null, null, null, null, null, null);
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(r -> r.getTitle().contains("Quantum")));
    }

    @Test
    void search_byStatus() throws Exception {
        dao.insert(makePatent("FN-T1", "Patent One", "Filed"));
        dao.insert(makePatent("FN-T2", "Patent Two", "Pending"));
        dao.insert(makePatent("FN-T3", "Patent Three", "Filed"));

        List<Patent> results = dao.search(null, "Filed", null, null, null, null, null);
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(r -> "Filed".equals(r.getPtoStatus())));
    }
}
