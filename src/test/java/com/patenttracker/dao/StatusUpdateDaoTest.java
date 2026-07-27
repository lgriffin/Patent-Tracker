package com.patenttracker.dao;

import com.patenttracker.model.Patent;
import com.patenttracker.model.StatusUpdate;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatusUpdateDaoTest {

    private Connection conn;
    private StatusUpdateDao statusUpdateDao;
    private PatentDao patentDao;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestDbHelper.createTestConnection();
        ConnectionProvider provider = TestDbHelper.testProvider(conn);
        statusUpdateDao = new StatusUpdateDao(provider);
        patentDao = new PatentDao(provider);
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private int insertTestPatent() throws Exception {
        Patent p = Patent.builder()
                .fileNumber("FN-SU")
                .title("Status Update Test")
                .ptoStatus("Filed")
                .suffix("US")
                .build();
        return patentDao.insert(p);
    }

    @Test
    void insertAndFind() throws Exception {
        int patentId = insertTestPatent();

        StatusUpdate su = StatusUpdate.create(patentId, "ptoStatus", "Filed", "Pending", "USPTO_SYNC");
        statusUpdateDao.insert(su);

        List<StatusUpdate> found = statusUpdateDao.findByPatentId(patentId);
        assertEquals(1, found.size());
        assertEquals("ptoStatus", found.get(0).getFieldName());
        assertEquals("Filed", found.get(0).getPreviousValue());
        assertEquals("Pending", found.get(0).getNewValue());
        assertEquals("USPTO_SYNC", found.get(0).getSource());
    }

    @Test
    void findByPatentId_multipleUpdates() throws Exception {
        int patentId = insertTestPatent();

        StatusUpdate su1 = StatusUpdate.create(patentId, "ptoStatus", "Filed", "Pending", "USPTO_SYNC");
        statusUpdateDao.insert(su1);

        StatusUpdate su2 = StatusUpdate.create(patentId, "ptoStatus", "Pending", "Allowed", "USPTO_SYNC");
        statusUpdateDao.insert(su2);

        List<StatusUpdate> found = statusUpdateDao.findByPatentId(patentId);
        assertEquals(2, found.size());
        // Verify both updates are present (order depends on timestamp resolution)
        assertTrue(found.stream().anyMatch(s -> "Pending".equals(s.getNewValue())));
        assertTrue(found.stream().anyMatch(s -> "Allowed".equals(s.getNewValue())));
    }
}
