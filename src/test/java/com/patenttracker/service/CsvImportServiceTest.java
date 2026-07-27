package com.patenttracker.service;

import com.patenttracker.dao.ConnectionProvider;
import com.patenttracker.dao.InventorDao;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.dao.TestDbHelper;
import com.patenttracker.model.Patent;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvImportServiceTest {

    private Connection conn;
    private PatentDao patentDao;
    private InventorDao inventorDao;
    private CsvImportService service;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestDbHelper.createTestConnection();
        ConnectionProvider provider = TestDbHelper.testProvider(conn);
        patentDao = new PatentDao(provider);
        inventorDao = new InventorDao(provider);
        service = new CsvImportService(patentDao, inventorDao);
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    @Test
    void importBasicCsv() throws Exception {
        // Create temp CSV file matching the expected column format
        // Cols: 0=row, 1=fileNumber, 2=title, 3=filingDate, 4=appNumber,
        //       5=pubDate, 6=pubNumber, 7=issueDate, 8=patentNumber,
        //       9=ptoStatus, 10=suffix, 11=primaryInventor
        File tempCsv = File.createTempFile("patent_test_", ".csv");
        tempCsv.deleteOnExit();

        try (FileWriter fw = new FileWriter(tempCsv)) {
            fw.write("Row,File Number,Title,Filing Date,Application Number,Publication Date,Publication Number,Issue/Grant Date,Patent Number,PTO Status,Suffix,Primary Inventor\n");
            fw.write("1,FN-CSV-001,Quantum Computing System,2023-01-15,15/123456,2023-06-01,US20230001,2024-01-01,US11111111,Patented,US,Leigh Griffin\n");
            fw.write("2,FN-CSV-002,Container Security Method,2023-03-20,15/789012,,,,,Filed,US,John Doe\n");
        }

        CsvImportService.ImportResult result = service.importCsv(tempCsv.getAbsolutePath());

        assertEquals(2, result.imported());
        assertEquals(0, result.updated());
        assertFalse(result.hasErrors());

        List<Patent> patents = patentDao.findAll();
        assertEquals(2, patents.size());

        Patent quantum = patentDao.findByFileNumber("FN-CSV-001");
        assertNotNull(quantum);
        assertEquals("Quantum Computing System", quantum.getTitle());
        assertEquals("Patented", quantum.getPtoStatus());
        assertEquals("US11111111", quantum.getPatentNumber());

        Patent container = patentDao.findByFileNumber("FN-CSV-002");
        assertNotNull(container);
        assertEquals("Container Security Method", container.getTitle());
        assertEquals("Filed", container.getPtoStatus());
    }

    @Test
    void importCsv_reImport_updates() throws Exception {
        File tempCsv = File.createTempFile("patent_reimport_", ".csv");
        tempCsv.deleteOnExit();

        try (FileWriter fw = new FileWriter(tempCsv)) {
            fw.write("Row,File Number,Title,Filing Date,Application Number,Publication Date,Publication Number,Issue/Grant Date,Patent Number,PTO Status,Suffix,Primary Inventor\n");
            fw.write("1,FN-RE-001,Original Title,2023-01-15,15/111111,,,,,Filed,US,inventor1\n");
        }

        CsvImportService.ImportResult first = service.importCsv(tempCsv.getAbsolutePath());
        assertEquals(1, first.imported());

        // Re-import with updated title
        File tempCsv2 = File.createTempFile("patent_reimport2_", ".csv");
        tempCsv2.deleteOnExit();

        try (FileWriter fw = new FileWriter(tempCsv2)) {
            fw.write("Row,File Number,Title,Filing Date,Application Number,Publication Date,Publication Number,Issue/Grant Date,Patent Number,PTO Status,Suffix,Primary Inventor\n");
            fw.write("1,FN-RE-001,Updated Title,2023-01-15,15/111111,,,,,Filed,US,inventor1\n");
        }

        CsvImportService.ImportResult second = service.importCsv(tempCsv2.getAbsolutePath());
        assertEquals(0, second.imported());
        assertEquals(1, second.updated());

        Patent updated = patentDao.findByFileNumber("FN-RE-001");
        assertNotNull(updated);
        assertEquals("Updated Title", updated.getTitle());
    }

    @Test
    void importCsv_emptyFile() throws Exception {
        File tempCsv = File.createTempFile("patent_empty_", ".csv");
        tempCsv.deleteOnExit();

        try (FileWriter fw = new FileWriter(tempCsv)) {
            // Empty - no header
        }

        CsvImportService.ImportResult result = service.importCsv(tempCsv.getAbsolutePath());
        assertEquals(0, result.imported());
        assertTrue(result.hasErrors());
    }
}
