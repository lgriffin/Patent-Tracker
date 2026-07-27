package com.patenttracker.dao;

import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentText;
import org.junit.jupiter.api.*;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

class PatentTextDaoTest {

    private Connection conn;
    private PatentTextDao textDao;
    private PatentDao patentDao;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestDbHelper.createTestConnection();
        ConnectionProvider provider = TestDbHelper.testProvider(conn);
        textDao = new PatentTextDao(provider);
        patentDao = new PatentDao(provider);
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private int insertTestPatent(String fileNumber) throws Exception {
        Patent p = Patent.builder()
                .fileNumber(fileNumber)
                .title("Text Test Patent " + fileNumber)
                .ptoStatus("Filed")
                .suffix("US")
                .build();
        return patentDao.insert(p);
    }

    @Test
    void insertAndFind() throws Exception {
        int patentId = insertTestPatent("FN-TXT1");

        PatentText pt = PatentText.builder()
                .patentId(patentId)
                .fullText("This is the full patent text content.")
                .pageCount(10)
                .build();
        textDao.insert(pt);

        PatentText found = textDao.findByPatentId(patentId);
        assertNotNull(found);
        assertEquals("This is the full patent text content.", found.getFullText());
        assertEquals(10, found.getPageCount());
    }

    @Test
    void existsForPatent() throws Exception {
        int patentId = insertTestPatent("FN-TXT2");

        assertFalse(textDao.existsForPatent(patentId));

        PatentText pt = PatentText.builder()
                .patentId(patentId)
                .fullText("Some text.")
                .pageCount(1)
                .build();
        textDao.insert(pt);

        assertTrue(textDao.existsForPatent(patentId));
    }

    @Test
    void countAll() throws Exception {
        int p1Id = insertTestPatent("FN-TXT3");
        int p2Id = insertTestPatent("FN-TXT4");

        PatentText pt1 = PatentText.builder()
                .patentId(p1Id)
                .fullText("Text 1")
                .pageCount(1)
                .build();
        textDao.insert(pt1);

        PatentText pt2 = PatentText.builder()
                .patentId(p2Id)
                .fullText("Text 2")
                .pageCount(2)
                .build();
        textDao.insert(pt2);

        assertEquals(2, textDao.countAll());
    }
}
