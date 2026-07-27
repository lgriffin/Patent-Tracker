package com.patenttracker.dao;

import com.patenttracker.model.Inventor;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentInventor;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventorDaoTest {

    private Connection conn;
    private InventorDao inventorDao;
    private PatentDao patentDao;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestDbHelper.createTestConnection();
        ConnectionProvider provider = TestDbHelper.testProvider(conn);
        inventorDao = new InventorDao(provider);
        patentDao = new PatentDao(provider);
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    @Test
    void findOrCreate_new() throws Exception {
        Inventor inv = inventorDao.findOrCreate("John Doe", "jdoe");
        assertNotNull(inv);
        assertTrue(inv.getId() > 0);
        assertEquals("John Doe", inv.getFullName());
        assertEquals("jdoe", inv.getUsername());
    }

    @Test
    void findOrCreate_existing() throws Exception {
        Inventor first = inventorDao.findOrCreate("Jane Smith", "jsmith");
        Inventor second = inventorDao.findOrCreate("Jane Smith", "jsmith");

        assertEquals(first.getId(), second.getId());
    }

    @Test
    void findAll() throws Exception {
        inventorDao.findOrCreate("Alice", "alice");
        inventorDao.findOrCreate("Bob", "bob");

        List<Inventor> all = inventorDao.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void findByPatentId() throws Exception {
        Patent p = Patent.builder()
                .fileNumber("FN-INV")
                .title("Test Patent")
                .ptoStatus("Filed")
                .suffix("US")
                .build();
        int patentId = patentDao.insert(p);

        Inventor inv = inventorDao.findOrCreate("Test Inventor", "tinv");
        PatentInventor pi = new PatentInventor(patentId, inv.getId(), "PRIMARY", 1);
        inventorDao.addPatentInventor(pi);

        List<Inventor> found = inventorDao.findByPatentId(patentId);
        assertEquals(1, found.size());
        assertEquals("Test Inventor", found.get(0).getFullName());
    }

    @Test
    void addPatentInventor() throws Exception {
        Patent p = Patent.builder()
                .fileNumber("FN-PI")
                .title("Patent Inventor Test")
                .ptoStatus("Filed")
                .suffix("US")
                .build();
        int patentId = patentDao.insert(p);

        Inventor inv = inventorDao.findOrCreate("Role Inventor", "rinv");
        PatentInventor pi = new PatentInventor(patentId, inv.getId(), "SECONDARY", 2);
        inventorDao.addPatentInventor(pi);

        List<Inventor> found = inventorDao.findByPatentId(patentId);
        assertEquals(1, found.size());
        assertEquals("Role Inventor", found.get(0).getFullName());
    }
}
