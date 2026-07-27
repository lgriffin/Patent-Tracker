package com.patenttracker.dao;

import com.patenttracker.model.Patent;
import com.patenttracker.model.Tag;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagDaoTest {

    private Connection conn;
    private TagDao tagDao;
    private PatentDao patentDao;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestDbHelper.createTestConnection();
        ConnectionProvider provider = TestDbHelper.testProvider(conn);
        tagDao = new TagDao(provider);
        patentDao = new PatentDao(provider);
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private int insertTestPatent(String fileNumber) throws Exception {
        Patent p = Patent.builder()
                .fileNumber(fileNumber)
                .title("Test Patent " + fileNumber)
                .ptoStatus("Filed")
                .suffix("US")
                .build();
        return patentDao.insert(p);
    }

    @Test
    void findOrCreate() throws Exception {
        Tag tag = tagDao.findOrCreate("Quantum");
        assertNotNull(tag);
        assertTrue(tag.getId() > 0);
        assertEquals("Quantum", tag.getName());

        // Creating same tag returns existing
        Tag same = tagDao.findOrCreate("Quantum");
        assertEquals(tag.getId(), same.getId());
    }

    @Test
    void addToPatent_andFindByPatentId() throws Exception {
        int patentId = insertTestPatent("FN-TAG1");
        Tag tag = tagDao.findOrCreate("Security");
        tagDao.addToPatent(patentId, tag.getId());

        List<Tag> tags = tagDao.findByPatentId(patentId);
        assertEquals(1, tags.size());
        assertEquals("Security", tags.get(0).getName());
    }

    @Test
    void removeFromPatent() throws Exception {
        int patentId = insertTestPatent("FN-TAG2");
        Tag tag = tagDao.findOrCreate("ML");
        tagDao.addToPatent(patentId, tag.getId());

        List<Tag> before = tagDao.findByPatentId(patentId);
        assertEquals(1, before.size());

        tagDao.removeFromPatent(patentId, tag.getId());

        List<Tag> after = tagDao.findByPatentId(patentId);
        assertEquals(0, after.size());
    }

    @Test
    void findAllWithCounts() throws Exception {
        int p1Id = insertTestPatent("FN-TC1");
        int p2Id = insertTestPatent("FN-TC2");
        Tag tag1 = tagDao.findOrCreate("Containers");
        Tag tag2 = tagDao.findOrCreate("Edge");
        tagDao.addToPatent(p1Id, tag1.getId());
        tagDao.addToPatent(p2Id, tag1.getId());
        tagDao.addToPatent(p1Id, tag2.getId());

        List<Tag> all = tagDao.findAllWithCounts();
        assertEquals(2, all.size());

        Tag containers = all.stream().filter(t -> "Containers".equals(t.getName())).findFirst().orElse(null);
        assertNotNull(containers);
        assertEquals(2, containers.getPatentCount());

        Tag edge = all.stream().filter(t -> "Edge".equals(t.getName())).findFirst().orElse(null);
        assertNotNull(edge);
        assertEquals(1, edge.getPatentCount());
    }

    @Test
    void rename() throws Exception {
        Tag tag = tagDao.findOrCreate("OldName");
        tagDao.rename(tag.getId(), "NewName");

        Tag renamed = tagDao.findByName("NewName");
        assertNotNull(renamed);
        assertEquals(tag.getId(), renamed.getId());
        assertEquals("NewName", renamed.getName());
    }

    @Test
    void delete() throws Exception {
        Tag tag = tagDao.findOrCreate("ToDelete");
        int id = tag.getId();

        tagDao.delete(id);

        Tag gone = tagDao.findByName("ToDelete");
        assertNull(gone);
    }
}
