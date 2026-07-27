package com.patenttracker.dao;

import com.patenttracker.model.MinedPatent;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MinedPatentDaoTest {

    private Connection conn;
    private MinedPatentDao minedPatentDao;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestDbHelper.createTestConnection();
        minedPatentDao = new MinedPatentDao(TestDbHelper.testProvider(conn));
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private MinedPatent makeMinedPatent(String number, String title, String area) {
        return MinedPatent.builder()
                .patentNumber(number)
                .title(title)
                .abstractText("Abstract for " + title)
                .grantDate(LocalDate.of(2024, 1, 15))
                .searchArea(area)
                .searchQuery("test query")
                .build();
    }

    @Test
    void insertOrIgnore() throws Exception {
        MinedPatent mp = makeMinedPatent("US12345", "Test Patent", "Quantum");
        minedPatentDao.insertOrIgnore(mp);

        List<MinedPatent> found = minedPatentDao.findBySearchArea("Quantum");
        assertEquals(1, found.size());
        assertEquals("US12345", found.get(0).getPatentNumber());
        assertEquals("Test Patent", found.get(0).getTitle());
    }

    @Test
    void insertOrIgnore_duplicate() throws Exception {
        MinedPatent mp1 = makeMinedPatent("US99999", "Original Title", "Security");
        minedPatentDao.insertOrIgnore(mp1);

        MinedPatent mp2 = makeMinedPatent("US99999", "Different Title", "Security");
        minedPatentDao.insertOrIgnore(mp2);

        int count = minedPatentDao.countBySearchArea("Security");
        assertEquals(1, count);
    }

    @Test
    void findBySearchArea() throws Exception {
        minedPatentDao.insertOrIgnore(makeMinedPatent("US001", "Patent A", "Quantum"));
        minedPatentDao.insertOrIgnore(makeMinedPatent("US002", "Patent B", "Quantum"));
        minedPatentDao.insertOrIgnore(makeMinedPatent("US003", "Patent C", "Containers"));

        List<MinedPatent> quantum = minedPatentDao.findBySearchArea("Quantum");
        assertEquals(2, quantum.size());

        List<MinedPatent> containers = minedPatentDao.findBySearchArea("Containers");
        assertEquals(1, containers.size());
        assertEquals("Patent C", containers.get(0).getTitle());
    }

    @Test
    void deleteBySearchArea() throws Exception {
        minedPatentDao.insertOrIgnore(makeMinedPatent("US-DEL1", "Del Patent 1", "AreaX"));
        minedPatentDao.insertOrIgnore(makeMinedPatent("US-DEL2", "Del Patent 2", "AreaX"));
        minedPatentDao.insertOrIgnore(makeMinedPatent("US-DEL3", "Del Patent 3", "AreaY"));

        assertEquals(2, minedPatentDao.countBySearchArea("AreaX"));

        minedPatentDao.deleteBySearchArea("AreaX");

        assertEquals(0, minedPatentDao.countBySearchArea("AreaX"));
        assertEquals(1, minedPatentDao.countBySearchArea("AreaY"));
    }
}
