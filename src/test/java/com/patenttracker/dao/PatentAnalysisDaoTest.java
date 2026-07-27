package com.patenttracker.dao;

import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentAnalysis;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PatentAnalysisDaoTest {

    private Connection conn;
    private PatentAnalysisDao analysisDao;
    private PatentDao patentDao;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestDbHelper.createTestConnection();
        ConnectionProvider provider = TestDbHelper.testProvider(conn);
        analysisDao = new PatentAnalysisDao(provider);
        patentDao = new PatentDao(provider);
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private int insertTestPatent() throws Exception {
        Patent p = Patent.builder()
                .fileNumber("FN-ANALYSIS")
                .title("Analysis Test Patent")
                .ptoStatus("Filed")
                .suffix("US")
                .build();
        return patentDao.insert(p);
    }

    @Test
    void insertOrUpdate_insert() throws Exception {
        int patentId = insertTestPatent();

        PatentAnalysis pa = PatentAnalysis.builder()
                .patentId(patentId)
                .analysisType("TECHNOLOGY")
                .resultJson("{\"field\":\"quantum\"}")
                .modelUsed("claude-3-opus")
                .build();

        int id = analysisDao.insertOrUpdate(pa);
        assertTrue(id > 0);

        PatentAnalysis found = analysisDao.findByPatentIdAndType(patentId, "TECHNOLOGY");
        assertNotNull(found);
        assertEquals("{\"field\":\"quantum\"}", found.getResultJson());
        assertEquals("claude-3-opus", found.getModelUsed());
    }

    @Test
    void insertOrUpdate_update() throws Exception {
        int patentId = insertTestPatent();

        PatentAnalysis pa = PatentAnalysis.builder()
                .patentId(patentId)
                .analysisType("CLAIMS")
                .resultJson("{\"version\":1}")
                .build();
        analysisDao.insertOrUpdate(pa);

        PatentAnalysis updated = PatentAnalysis.builder()
                .patentId(patentId)
                .analysisType("CLAIMS")
                .resultJson("{\"version\":2}")
                .build();
        analysisDao.insertOrUpdate(updated);

        PatentAnalysis found = analysisDao.findByPatentIdAndType(patentId, "CLAIMS");
        assertNotNull(found);
        assertEquals("{\"version\":2}", found.getResultJson());
    }

    @Test
    void findByPatentIdAndType() throws Exception {
        int patentId = insertTestPatent();

        PatentAnalysis pa1 = PatentAnalysis.builder()
                .patentId(patentId)
                .analysisType("TECHNOLOGY")
                .resultJson("{\"type\":\"tech\"}")
                .build();
        analysisDao.insertOrUpdate(pa1);

        PatentAnalysis pa2 = PatentAnalysis.builder()
                .patentId(patentId)
                .analysisType("CLAIMS")
                .resultJson("{\"type\":\"claims\"}")
                .build();
        analysisDao.insertOrUpdate(pa2);

        PatentAnalysis tech = analysisDao.findByPatentIdAndType(patentId, "TECHNOLOGY");
        assertNotNull(tech);
        assertEquals("{\"type\":\"tech\"}", tech.getResultJson());

        PatentAnalysis claims = analysisDao.findByPatentIdAndType(patentId, "CLAIMS");
        assertNotNull(claims);
        assertEquals("{\"type\":\"claims\"}", claims.getResultJson());
    }

    @Test
    void countByType() throws Exception {
        int patentId = insertTestPatent();

        PatentAnalysis pa1 = PatentAnalysis.builder()
                .patentId(patentId)
                .analysisType("TECHNOLOGY")
                .resultJson("{}")
                .build();
        analysisDao.insertOrUpdate(pa1);

        PatentAnalysis pa2 = PatentAnalysis.builder()
                .patentId(patentId)
                .analysisType("CLAIMS")
                .resultJson("{}")
                .build();
        analysisDao.insertOrUpdate(pa2);

        Map<String, Integer> counts = analysisDao.countByType();
        assertEquals(1, counts.get("TECHNOLOGY"));
        assertEquals(1, counts.get("CLAIMS"));
    }

    @Test
    void deleteByPatentIdAndType() throws Exception {
        int patentId = insertTestPatent();

        PatentAnalysis pa = PatentAnalysis.builder()
                .patentId(patentId)
                .analysisType("TECHNOLOGY")
                .resultJson("{}")
                .build();
        analysisDao.insertOrUpdate(pa);

        assertNotNull(analysisDao.findByPatentIdAndType(patentId, "TECHNOLOGY"));

        analysisDao.deleteByPatentIdAndType(patentId, "TECHNOLOGY");

        assertNull(analysisDao.findByPatentIdAndType(patentId, "TECHNOLOGY"));
    }
}
