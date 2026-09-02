package com.patenttracker.service;

import com.patenttracker.dao.CorpusDocumentDao;
import com.patenttracker.model.CorpusDocument;
import com.patenttracker.model.CorpusDomain;
import com.patenttracker.model.DiscoveredPatent;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class CorpusBulkDownloadService {

    private static final String CORPUS_DIR = System.getProperty("user.home") + "/.patenttracker/corpus";
    private static final String USPTO_PPUBS_PDF = "https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/";

    private final CorpusDocumentDao documentDao;
    private final GooglePatentsSearchService googleSearchService;

    public CorpusBulkDownloadService() {
        this.documentDao = new CorpusDocumentDao();
        this.googleSearchService = new GooglePatentsSearchService();
    }

    public CorpusBulkDownloadService(CorpusDocumentDao documentDao,
                                      GooglePatentsSearchService googleSearchService) {
        this.documentDao = documentDao;
        this.googleSearchService = googleSearchService;
    }

    public DiscoverResult discoverPatents(CorpusDomain domain, ProgressCallback callback) {
        Set<String> seenNumbers = new LinkedHashSet<>();
        List<CorpusDocument> discovered = new ArrayList<>();

        // Batch keywords into combined queries (4 keywords per query) to reduce API calls
        List<String> keywords = domain.getKeywordList();
        List<String> batchedQueries = batchKeywords(keywords, 4);

        if (callback != null) callback.onStatus("Searching Google Patents for " + domain.getDisplayName() + " (" + batchedQueries.size() + " queries)...");

        GooglePatentsSearchService.SearchProgressCallback searchCallback = callback == null ? null :
                new GooglePatentsSearchService.SearchProgressCallback() {
                    @Override public void onStatus(String status) { callback.onStatus(status); }
                    @Override public boolean isCancelled() { return callback.isCancelled(); }
                };

        GooglePatentsSearchService.PriorArtSearchResult result =
                googleSearchService.searchForPriorArt(batchedQueries, searchCallback);

        if (result.success() && result.patents() != null) {
            for (DiscoveredPatent dp : result.patents()) {
                if (seenNumbers.add(dp.getPatentNumber())) {
                    discovered.add(toCorpusDocument(dp, domain.getName()));
                }
            }
        }

        if (callback != null && callback.isCancelled()) {
            return new DiscoverResult(discovered.size(), 0, "Cancelled");
        }

        if (discovered.isEmpty()) {
            String error = result.error() != null ? result.error()
                    : "No patents found for domain: " + domain.getDisplayName();
            return new DiscoverResult(0, 0, error);
        }

        try {
            int inserted = documentDao.insertBatch(discovered);
            if (callback != null) callback.onStatus("Registered " + inserted + " new patents in corpus.");
            return new DiscoverResult(discovered.size(), inserted, null);
        } catch (Exception e) {
            return new DiscoverResult(discovered.size(), 0, "Database error: " + e.getMessage());
        }
    }

    private List<String> batchKeywords(List<String> keywords, int batchSize) {
        List<String> batched = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i += batchSize) {
            int end = Math.min(i + batchSize, keywords.size());
            String combined = String.join(" OR ", keywords.subList(i, end));
            batched.add(combined);
        }
        return batched;
    }

    public List<DownloadResult> downloadPendingPdfs(int batchSize, ProgressCallback callback) {
        int delay = 200; // USPTO PPUBS doesn't need aggressive rate limiting
        List<DownloadResult> results = new ArrayList<>();

        try {
            Files.createDirectories(Path.of(CORPUS_DIR));
            List<CorpusDocument> pending = documentDao.findPendingDownloads(batchSize);

            for (int i = 0; i < pending.size(); i++) {
                if (callback != null && callback.isCancelled()) break;

                CorpusDocument doc = pending.get(i);
                if (callback != null) {
                    callback.onProgress(i + 1, pending.size(),
                            "Downloading " + doc.getPatentNumber() + "...");
                }

                DownloadResult result = downloadSinglePdf(doc);
                results.add(result);

                if (i < pending.size() - 1) {
                    try { Thread.sleep(delay); }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            results.add(new DownloadResult(null, false, "Setup error: " + e.getMessage()));
        }

        return results;
    }

    public List<ExtractionResult> extractPendingText(int batchSize, ProgressCallback callback) {
        List<ExtractionResult> results = new ArrayList<>();

        try {
            List<CorpusDocument> pending = documentDao.findPendingExtraction(batchSize);

            for (int i = 0; i < pending.size(); i++) {
                if (callback != null && callback.isCancelled()) break;

                CorpusDocument doc = pending.get(i);
                if (callback != null) {
                    callback.onProgress(i + 1, pending.size(),
                            "Extracting text from " + doc.getPatentNumber() + "...");
                }

                ExtractionResult result = extractSinglePdf(doc);
                results.add(result);
            }
        } catch (Exception e) {
            results.add(new ExtractionResult(null, false, 0, 0, "Setup error: " + e.getMessage()));
        }

        return results;
    }

    private DownloadResult downloadSinglePdf(CorpusDocument doc) {
        String patentNum = doc.getPatentNumber();
        String pdfPath = CORPUS_DIR + "/" + patentNum + ".pdf";

        if (Files.exists(Path.of(pdfPath))) {
            try {
                documentDao.updateDownloadStatus(doc.getId(), "COMPLETE", pdfPath);
                return new DownloadResult(patentNum, true, null);
            } catch (Exception e) {
                return new DownloadResult(patentNum, false, "DB error: " + e.getMessage());
            }
        }

        try {
            documentDao.updateDownloadStatus(doc.getId(), "IN_PROGRESS", null);

            // Extract bare number for USPTO PPUBS endpoint
            String bareNum = extractBareNumber(patentNum);
            if (bareNum == null) {
                documentDao.updateDownloadStatus(doc.getId(), "FAILED", null);
                return new DownloadResult(patentNum, false, "Cannot parse patent number");
            }

            String pdfUrl = USPTO_PPUBS_PDF + bareNum;
            boolean downloaded = downloadFileViaCurl(pdfUrl, pdfPath);
            if (!downloaded) {
                Files.deleteIfExists(Path.of(pdfPath));
                documentDao.updateDownloadStatus(doc.getId(), "FAILED", null);
                return new DownloadResult(patentNum, false, "USPTO PDF download failed");
            }

            long fileSize = Files.size(Path.of(pdfPath));
            if (fileSize < 1024) {
                Files.deleteIfExists(Path.of(pdfPath));
                documentDao.updateDownloadStatus(doc.getId(), "FAILED", null);
                return new DownloadResult(patentNum, false,
                        "File too small (" + fileSize + " bytes)");
            }

            documentDao.updateDownloadStatus(doc.getId(), "COMPLETE", pdfPath);
            return new DownloadResult(patentNum, true, null);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            try { Files.deleteIfExists(Path.of(pdfPath)); } catch (Exception ex) { }
            try { documentDao.updateDownloadStatus(doc.getId(), "FAILED", null); } catch (Exception ex) { }
            return new DownloadResult(patentNum, false, "Download failed: " + msg);
        }
    }

    static String extractBareNumber(String patentNumber) {
        if (patentNumber == null) return null;
        // Strip "US" prefix and any suffix letters (B1, B2, A1, etc.)
        String num = patentNumber;
        if (num.startsWith("US")) num = num.substring(2);
        num = num.replaceAll("[A-Za-z]+\\d*$", "");
        return num.isEmpty() ? null : num;
    }

    private boolean downloadFileViaCurl(String url, String outputPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "-s", "-L", "-o", outputPath,
                    "--connect-timeout", "15", "--max-time", "120",
                    "-H", "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    url
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.getInputStream().readAllBytes();
            boolean finished = proc.waitFor(150, TimeUnit.SECONDS);
            if (!finished) {
                 proc.destroyForcibly();
                 return false;
             }
             return proc.exitValue() == 0;
         } catch (Exception ex) { }
         return false;
     }

    private ExtractionResult extractSinglePdf(CorpusDocument doc) {
        if (doc.getPdfPath() == null || doc.getPdfPath().isBlank()) {
            return new ExtractionResult(doc.getPatentNumber(), false, 0, 0, "No PDF path");
        }

        File pdfFile = new File(doc.getPdfPath());
        if (!pdfFile.exists()) {
            return new ExtractionResult(doc.getPatentNumber(), false, 0, 0, "PDF not found");
        }

        try (PDDocument pdfDoc = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(pdfDoc);
            int pageCount = pdfDoc.getNumberOfPages();
            int wordCount = PatentChunkingService.countWords(text);

            documentDao.updateExtractionStatus(doc.getId(), "COMPLETE", text, pageCount, wordCount);
            return new ExtractionResult(doc.getPatentNumber(), true, pageCount, wordCount, null);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            try { documentDao.updateExtractionStatus(doc.getId(), "FAILED", null, 0, 0); } catch (Exception ex) { }
            return new ExtractionResult(doc.getPatentNumber(), false, 0, 0,
                    "Extraction failed: " + msg);
        }
    }


    private CorpusDocument toCorpusDocument(DiscoveredPatent dp, String domain) {
        return CorpusDocument.builder()
                .patentNumber(dp.getPatentNumber())
                .title(dp.getTitle())
                .abstractText(dp.getAbstractText())
                .assignee(dp.getAssignee())
                .grantDate(dp.getGrantDate() != null ? dp.getGrantDate().toString() : null)
                .cpcCodes(dp.getCpcCodes())
                .source(dp.getSource())
                .domain(domain)
                .downloadStatus("PENDING")
                .build();
    }

    public record DiscoverResult(int found, int newlyAdded, String error) {
        public boolean success() { return error == null; }
    }

    public record DownloadResult(String patentNumber, boolean success, String error) {}

    public record ExtractionResult(String patentNumber, boolean success,
                                    int pageCount, int wordCount, String error) {}

    public interface ProgressCallback {
        default void onStatus(String status) {}
        default void onProgress(int current, int total, String detail) {}
        default boolean isCancelled() { return false; }
    }
}
