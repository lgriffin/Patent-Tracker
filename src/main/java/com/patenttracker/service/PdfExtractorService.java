package com.patenttracker.service;

import com.patenttracker.controller.SettingsController;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.dao.PatentTextDao;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentText;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PdfExtractorService {

    private final PatentDao patentDao;
    private final PatentTextDao patentTextDao;

    public PdfExtractorService() {
        this.patentDao = new PatentDao();
        this.patentTextDao = new PatentTextDao();
    }

    public ExtractionResult extractText(Patent patent) {
        try {
            if (patentTextDao.existsForPatent(patent.getId())) {
                return new ExtractionResult(patent.getFileNumber(), patent.getTitle(),
                        true, 0, null);
            }
        } catch (SQLException e) {
            return new ExtractionResult(patent.getFileNumber(), patent.getTitle(),
                    false, 0, "Database error: " + e.getMessage());
        }

        if (patent.getPdfPath() == null || patent.getPdfPath().isBlank()) {
            return new ExtractionResult(patent.getFileNumber(), patent.getTitle(),
                    false, 0, "No PDF path set.");
        }

        File pdfFile = new File(patent.getPdfPath());
        if (!pdfFile.exists()) {
            return new ExtractionResult(patent.getFileNumber(), patent.getTitle(),
                    false, 0, "PDF file not found: " + patent.getPdfPath());
        }

        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            int pageCount = doc.getNumberOfPages();

            PatentText pt = new PatentText();
            pt.setPatentId(patent.getId());
            pt.setFullText(text);
            pt.setPageCount(pageCount);
            patentTextDao.insert(pt);

            return new ExtractionResult(patent.getFileNumber(), patent.getTitle(),
                    true, pageCount, null);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new ExtractionResult(patent.getFileNumber(), patent.getTitle(),
                    false, 0, "Extraction failed: " + msg);
        }
    }

    public List<ExtractionResult> extractAll(ExtractionProgressCallback callback) {
        int delay = SettingsController.getRateLimitDelay();
        List<ExtractionResult> results = new ArrayList<>();

        try {
            List<Patent> patents = patentDao.findAll();
            List<Patent> eligible = patents.stream()
                    .filter(p -> p.getPdfPath() != null && !p.getPdfPath().isBlank()
                            && new File(p.getPdfPath()).exists())
                    .filter(p -> {
                        try { return !patentTextDao.existsForPatent(p.getId()); }
                        catch (SQLException e) { return false; }
                    })
                    .toList();

            for (int i = 0; i < eligible.size(); i++) {
                if (callback != null && callback.isCancelled()) break;

                Patent patent = eligible.get(i);
                if (callback != null) {
                    callback.onProgress(i + 1, eligible.size(), patent.getTitle());
                }

                ExtractionResult result = extractText(patent);
                results.add(result);

                if (callback != null) {
                    callback.onResult(result);
                }

                if (i < eligible.size() - 1) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            results.add(new ExtractionResult(null, null, false, 0,
                    "Database error: " + e.getMessage()));
        }

        return results;
    }

    public int[] getCounts() {
        try {
            List<Patent> patents = patentDao.findAll();
            int total = patents.size();
            int withPdf = 0;
            int withText = patentTextDao.countAll();
            for (Patent p : patents) {
                if (p.getPdfPath() != null && !p.getPdfPath().isBlank()
                        && new File(p.getPdfPath()).exists()) {
                    withPdf++;
                }
            }
            return new int[]{total, withPdf, withText, withPdf - withText};
        } catch (SQLException e) {
            return new int[]{0, 0, 0, 0};
        }
    }

    public record ExtractionResult(
            String fileNumber, String title, boolean success,
            int pageCount, String error
    ) {}

    public interface ExtractionProgressCallback {
        void onProgress(int current, int total, String title);
        void onResult(ExtractionResult result);
        boolean isCancelled();
    }
}
