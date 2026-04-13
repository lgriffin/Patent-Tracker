package com.patenttracker.service;

import com.patenttracker.controller.SettingsController;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.model.Patent;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PdfDownloadService {

    private static final String GOOGLE_PATENTS_BASE = "https://patents.google.com/patent/US";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    // Matches <meta name="citation_pdf_url" content="...">
    private static final Pattern PDF_URL_PATTERN = Pattern.compile(
            "citation_pdf_url\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    // Fallback: matches any patentimages.storage.googleapis.com URL ending in .pdf
    private static final Pattern GCS_PDF_PATTERN = Pattern.compile(
            "(https://patentimages\\.storage\\.googleapis\\.com/[^\"'\\s]+\\.pdf)",
            Pattern.CASE_INSENSITIVE);

    private final PatentDao patentDao;
    private final HttpClient httpClient;

    public PdfDownloadService() {
        this.patentDao = new PatentDao();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Checks whether the patent has a locally cached PDF that exists on disk.
     */
    public boolean hasCachedPdf(Patent patent) {
        if (patent.getPdfPath() == null || patent.getPdfPath().isBlank()) return false;
        return new File(patent.getPdfPath()).exists();
    }

    /**
     * Checks whether this patent is eligible for PDF download
     * (has a patent number or publication number for Google Patents lookup).
     */
    public boolean canDownload(Patent patent) {
        return (patent.getPatentNumber() != null && !patent.getPatentNumber().isBlank())
            || (patent.getPublicationNumber() != null && !patent.getPublicationNumber().isBlank());
    }

    /**
     * Opens a cached PDF with the system default PDF viewer.
     */
    public void openPdf(Patent patent) throws Exception {
        File pdfFile = new File(patent.getPdfPath());
        if (!pdfFile.exists()) {
            throw new Exception("PDF file not found: " + patent.getPdfPath());
        }
        Desktop.getDesktop().open(pdfFile);
    }

    /**
     * Downloads the PDF for a single patent via Google Patents.
     * 1. Fetches the Google Patents page to find the PDF download URL
     * 2. Downloads the PDF from Google Cloud Storage
     */
    public DownloadResult downloadPdf(Patent patent) {
        // Skip if already cached
        if (hasCachedPdf(patent)) {
            return new DownloadResult(patent.getFileNumber(), patent.getTitle(),
                    true, patent.getPdfPath(), null, "Cached");
        }

        String pdfDir = System.getProperty("user.home") + "/.patenttracker/pdfs";
        try {
            Files.createDirectories(Path.of(pdfDir));
        } catch (Exception e) {
            return new DownloadResult(patent.getFileNumber(), patent.getTitle(),
                    false, null, "Failed to create PDF directory: " + e.getMessage(), null);
        }

        // Try patent number first, then publication number
        if (patent.getPatentNumber() != null && !patent.getPatentNumber().isBlank()) {
            String num = patent.getPatentNumber();
            String pdfPath = pdfDir + "/US" + num + ".pdf";
            DownloadResult result = downloadViaGooglePatents(patent, num, pdfPath, "Patent Grant");
            if (result.success()) return result;
        }

        if (patent.getPublicationNumber() != null && !patent.getPublicationNumber().isBlank()) {
            String num = patent.getPublicationNumber();
            String cleanNum = num.replaceAll("[^A-Za-z0-9]", "");
            // Strip leading "US" if present — we add it in the URL
            String lookupNum = cleanNum.startsWith("US") ? cleanNum.substring(2) : cleanNum;
            String pdfPath = pdfDir + "/US" + lookupNum + ".pdf";
            DownloadResult result = downloadViaGooglePatents(patent, lookupNum, pdfPath, "Publication");
            if (result.success()) return result;
        }

        return new DownloadResult(patent.getFileNumber(), patent.getTitle(),
                false, null, "Could not find PDF on Google Patents.", null);
    }

    /**
     * Fetches the Google Patents page, extracts the PDF URL, and downloads it.
     */
    private DownloadResult downloadViaGooglePatents(Patent patent, String number,
            String pdfPath, String sourceType) {
        try {
            // Step 1: Fetch the Google Patents page to find the PDF URL
            String pageUrl = GOOGLE_PATENTS_BASE + number;
            HttpRequest pageRequest = HttpRequest.newBuilder()
                    .uri(URI.create(pageUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> pageResponse = httpClient.send(pageRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (pageResponse.statusCode() != 200) {
                return new DownloadResult(patent.getFileNumber(), patent.getTitle(),
                        false, null, "Google Patents returned HTTP " + pageResponse.statusCode()
                                + " for US" + number, sourceType);
            }

            // Step 2: Extract PDF URL from page
            String pdfUrl = extractPdfUrl(pageResponse.body());
            if (pdfUrl == null) {
                return new DownloadResult(patent.getFileNumber(), patent.getTitle(),
                        false, null, "No PDF link found on Google Patents page.", sourceType);
            }

            // Step 3: Download the PDF from Google Cloud Storage
            HttpRequest pdfRequest = HttpRequest.newBuilder()
                    .uri(URI.create(pdfUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/pdf,*/*")
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<Path> pdfResponse = httpClient.send(pdfRequest,
                    HttpResponse.BodyHandlers.ofFile(Path.of(pdfPath)));

            if (pdfResponse.statusCode() == 200) {
                long fileSize = Files.size(Path.of(pdfPath));
                if (fileSize < 1024) {
                    Files.deleteIfExists(Path.of(pdfPath));
                    return new DownloadResult(patent.getFileNumber(), patent.getTitle(),
                            false, null, "Downloaded file too small (" + fileSize + " bytes).", sourceType);
                }

                patent.setPdfPath(pdfPath);
                patentDao.update(patent);

                return new DownloadResult(patent.getFileNumber(), patent.getTitle(),
                        true, pdfPath, null, sourceType);
            } else {
                Files.deleteIfExists(Path.of(pdfPath));
                return new DownloadResult(patent.getFileNumber(), patent.getTitle(),
                        false, null, "PDF download returned HTTP " + pdfResponse.statusCode(), sourceType);
            }
        } catch (Exception e) {
            try { Files.deleteIfExists(Path.of(pdfPath)); } catch (Exception ignored) {}
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new DownloadResult(patent.getFileNumber(), patent.getTitle(),
                    false, null, "Download failed: " + msg, sourceType);
        }
    }

    /**
     * Extracts the PDF download URL from a Google Patents HTML page.
     * Looks for the citation_pdf_url meta tag first, then falls back to
     * scanning for patentimages.storage.googleapis.com URLs.
     */
    static String extractPdfUrl(String html) {
        // Primary: <meta name="citation_pdf_url" content="...">
        Matcher m = PDF_URL_PATTERN.matcher(html);
        if (m.find()) {
            return m.group(1);
        }

        // Fallback: any Google Cloud Storage PDF URL
        Matcher gcs = GCS_PDF_PATTERN.matcher(html);
        if (gcs.find()) {
            return gcs.group(1);
        }

        return null;
    }

    /**
     * Bulk download PDFs for all eligible patents that don't already have cached PDFs.
     */
    public List<DownloadResult> downloadAll(DownloadProgressCallback callback) {
        int delay = SettingsController.getRateLimitDelay();
        List<DownloadResult> results = new ArrayList<>();

        try {
            List<Patent> patents = patentDao.findAll();
            List<Patent> eligible = patents.stream()
                    .filter(p -> canDownload(p) && !hasCachedPdf(p))
                    .toList();

            for (int i = 0; i < eligible.size(); i++) {
                if (callback != null && callback.isCancelled()) break;

                Patent patent = eligible.get(i);
                if (callback != null) {
                    callback.onProgress(i + 1, eligible.size(), patent.getTitle());
                }

                DownloadResult result = downloadPdf(patent);
                results.add(result);

                if (callback != null) {
                    callback.onResult(result);
                }

                // Rate limit delay between requests
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
            results.add(new DownloadResult(null, null, false, null,
                    "Database error: " + e.getMessage(), null));
        }

        return results;
    }

    /**
     * Returns total counts for the download dialog summary:
     * [0] = total patents, [1] = eligible, [2] = already cached, [3] = to download.
     */
    public int[] getCounts() {
        try {
            List<Patent> patents = patentDao.findAll();
            int total = patents.size();
            int eligible = 0;
            int cached = 0;
            for (Patent p : patents) {
                if (canDownload(p)) {
                    eligible++;
                    if (hasCachedPdf(p)) cached++;
                }
            }
            return new int[]{total, eligible, cached, eligible - cached};
        } catch (SQLException e) {
            return new int[]{0, 0, 0, 0};
        }
    }

    /**
     * Deletes all cached PDFs from disk and clears pdf_path in the database.
     * Returns the number of PDFs removed.
     */
    public int flushAllPdfs() throws SQLException {
        List<Patent> patents = patentDao.findAll();
        int removed = 0;

        for (Patent patent : patents) {
            if (patent.getPdfPath() != null && !patent.getPdfPath().isBlank()) {
                try {
                    Files.deleteIfExists(Path.of(patent.getPdfPath()));
                } catch (Exception ignored) {}
                patent.setPdfPath(null);
                patentDao.update(patent);
                removed++;
            }
        }

        // Also clean up the pdfs directory of any orphaned files
        Path pdfDir = Path.of(System.getProperty("user.home") + "/.patenttracker/pdfs");
        if (Files.isDirectory(pdfDir)) {
            try (var files = Files.list(pdfDir)) {
                files.filter(p -> p.toString().endsWith(".pdf"))
                     .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            } catch (Exception ignored) {}
        }

        return removed;
    }

    // --- Result record and callback interface ---

    public record DownloadResult(
            String fileNumber, String title, boolean success,
            String pdfPath, String error, String sourceType
    ) {}

    public interface DownloadProgressCallback {
        void onProgress(int current, int total, String title);
        void onResult(DownloadResult result);
        boolean isCancelled();
    }
}
