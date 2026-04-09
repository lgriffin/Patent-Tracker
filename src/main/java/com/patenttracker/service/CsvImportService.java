package com.patenttracker.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.patenttracker.dao.InventorDao;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.model.Inventor;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentInventor;
import com.patenttracker.util.FileNumberParser;
import com.patenttracker.util.NameParser;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class CsvImportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final PatentDao patentDao;
    private final InventorDao inventorDao;

    public CsvImportService() {
        this.patentDao = new PatentDao();
        this.inventorDao = new InventorDao();
    }

    public CsvImportService(PatentDao patentDao, InventorDao inventorDao) {
        this.patentDao = patentDao;
        this.inventorDao = inventorDao;
    }

    public ImportResult importCsv(String filePath) throws IOException {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        int updated = 0;
        int unchanged = 0;

        try (CSVReader reader = new CSVReader(new FileReader(filePath, StandardCharsets.UTF_8))) {
            // Skip header
            String[] header = reader.readNext();
            if (header == null) {
                return new ImportResult(0, 0, 0, List.of("Empty CSV file"));
            }

            String[] line;
            int rowNum = 1;
            while ((line = reader.readNext()) != null) {
                rowNum++;
                try {
                    if (line.length < 11) {
                        errors.add("Row " + rowNum + ": insufficient columns (" + line.length + ")");
                        continue;
                    }

                    Patent patent = parsePatentRow(line, rowNum);

                    // Check if already exists by file_number (primary key for dedup)
                    Patent existing = patentDao.findByFileNumber(patent.getFileNumber());
                    if (existing != null) {
                        // Update existing record if any fields have changed
                        if (mergeChanges(existing, patent)) {
                            patentDao.update(existing);
                            updated++;
                        } else {
                            unchanged++;
                        }
                        continue;
                    }

                    patentDao.insert(patent);

                    // Process inventors
                    processInventors(patent.getId(), line);

                    imported++;
                } catch (Exception e) {
                    errors.add("Row " + rowNum + ": " + e.getMessage());
                }
            }
        } catch (CsvValidationException e) {
            errors.add("CSV validation error: " + e.getMessage());
        }

        return new ImportResult(imported, updated, unchanged, errors);
    }

    /**
     * Merges non-null fields from the CSV row into an existing patent.
     * Returns true if any field was actually changed.
     */
    private boolean mergeChanges(Patent existing, Patent fromCsv) {
        boolean changed = false;

        if (fromCsv.getTitle() != null && !fromCsv.getTitle().equals(existing.getTitle())) {
            existing.setTitle(fromCsv.getTitle());
            changed = true;
        }
        if (fromCsv.getFilingDate() != null && !fromCsv.getFilingDate().equals(existing.getFilingDate())) {
            existing.setFilingDate(fromCsv.getFilingDate());
            changed = true;
        }
        if (fromCsv.getApplicationNumber() != null && !fromCsv.getApplicationNumber().equals(existing.getApplicationNumber())) {
            existing.setApplicationNumber(fromCsv.getApplicationNumber());
            changed = true;
        }
        if (fromCsv.getPublicationDate() != null && !fromCsv.getPublicationDate().equals(existing.getPublicationDate())) {
            existing.setPublicationDate(fromCsv.getPublicationDate());
            changed = true;
        }
        if (fromCsv.getPublicationNumber() != null && !fromCsv.getPublicationNumber().equals(existing.getPublicationNumber())) {
            existing.setPublicationNumber(fromCsv.getPublicationNumber());
            changed = true;
        }
        if (fromCsv.getIssueGrantDate() != null && !fromCsv.getIssueGrantDate().equals(existing.getIssueGrantDate())) {
            existing.setIssueGrantDate(fromCsv.getIssueGrantDate());
            changed = true;
        }
        if (fromCsv.getPatentNumber() != null && !fromCsv.getPatentNumber().equals(existing.getPatentNumber())) {
            existing.setPatentNumber(fromCsv.getPatentNumber());
            changed = true;
        }
        // ptoStatus is intentionally NOT merged from CSV — USPTO sync is the authoritative source
        if (fromCsv.getClassification() != null && !fromCsv.getClassification().equals(existing.getClassification())) {
            existing.setClassification(fromCsv.getClassification());
            changed = true;
        }
        if (fromCsv.getParentFileNumber() != null && !fromCsv.getParentFileNumber().equals(existing.getParentFileNumber())) {
            existing.setParentFileNumber(fromCsv.getParentFileNumber());
            changed = true;
        }

        return changed;
    }

    private Patent parsePatentRow(String[] cols, int rowNum) {
        Patent p = new Patent();
        p.setCsvRowNumber(rowNum);

        // Col 1: File Number
        p.setFileNumber(clean(cols[1]));

        // Col 2: Title
        String title = clean(cols[2]);
        p.setTitle(title != null ? title : "Untitled");

        // Col 3: Filing Date
        p.setFilingDate(parseDate(clean(cols[3])));

        // Col 4: Application #
        p.setApplicationNumber(clean(cols[4]));

        // Col 5: Publication Date
        p.setPublicationDate(parseDate(clean(cols[5])));

        // Col 6: Publication #
        p.setPublicationNumber(clean(cols[6]));

        // Col 7: Issue/Grant Date
        p.setIssueGrantDate(parseDate(clean(cols[7])));

        // Col 8: Patent #
        p.setPatentNumber(clean(cols[8]));

        // Col 9: PTO Status — used for initial import; USPTO sync overwrites later
        String ptoStatus = clean(cols[9]);
        p.setPtoStatus(ptoStatus != null ? ptoStatus : "Unknown");

        // Col 10: Suffix
        String suffix = clean(cols[10]);
        p.setSuffix(suffix != null && !suffix.isBlank() ? suffix : "US");

        // Col 16: Classification (if present)
        if (cols.length > 16) {
            p.setClassification(clean(cols[16]));
        }

        // Parse parent file number from file number suffix
        FileNumberParser fnp = FileNumberParser.parse(p.getFileNumber());
        if (fnp.hasParent()) {
            p.setParentFileNumber(fnp.getParentFileNumber());
        }

        return p;
    }

    private void processInventors(int patentId, String[] cols) throws SQLException {
        // Col 11: Primary Inventor
        addInventor(patentId, clean(cols[11]), "PRIMARY", 1);

        // Col 12: Secondary (if present)
        if (cols.length > 12) addInventor(patentId, clean(cols[12]), "SECONDARY", 2);

        // Col 13-15: Additional (if present)
        if (cols.length > 13) addInventor(patentId, clean(cols[13]), "ADDITIONAL", 3);
        if (cols.length > 14) addInventor(patentId, clean(cols[14]), "ADDITIONAL", 4);
        if (cols.length > 15) addInventor(patentId, clean(cols[15]), "ADDITIONAL", 5);
    }

    private void addInventor(int patentId, String rawName, String role, int position) throws SQLException {
        if (rawName == null || rawName.isBlank()) return;

        // Handle comma-separated names in a single field (e.g., "pchibon,rsibley")
        String[] names = rawName.split(",");
        for (int i = 0; i < names.length; i++) {
            String name = names[i].trim();
            if (name.isBlank()) continue;

            NameParser parsed = NameParser.parse(name);
            if (parsed.isEmpty()) continue;

            Inventor inventor = inventorDao.findOrCreate(parsed.getFullName(), parsed.getUsername());
            PatentInventor pi = new PatentInventor(patentId, inventor.getId(), role, position + i);
            inventorDao.addPatentInventor(pi);
        }
    }

    private String clean(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String cleaned = s.trim().split("\\s")[0];
            return LocalDate.parse(cleaned, DATE_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public record ImportResult(int imported, int updated, int unchanged, List<String> errors) {
        public boolean hasErrors() { return !errors.isEmpty(); }
        public int skipped() { return unchanged; }
    }
}
