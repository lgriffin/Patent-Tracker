package com.patenttracker.service;

import com.patenttracker.dao.CorpusDocumentDao;
import com.patenttracker.dao.CorpusDomainDao;
import com.patenttracker.dao.DocumentChunkDao;
import com.patenttracker.model.CorpusDocument;
import com.patenttracker.model.CorpusDomain;
import com.patenttracker.model.DocumentChunk;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CorpusBuilderService {

    private final CorpusDocumentDao documentDao;
    private final DocumentChunkDao chunkDao;
    private final CorpusDomainDao domainDao;
    private final CorpusBulkDownloadService downloadService;
    private final PatentChunkingService chunkingService;

    public CorpusBuilderService() {
        this.documentDao = new CorpusDocumentDao();
        this.chunkDao = new DocumentChunkDao();
        this.domainDao = new CorpusDomainDao();
        this.downloadService = new CorpusBulkDownloadService();
        this.chunkingService = new PatentChunkingService();
    }

    public CorpusBuilderService(CorpusDocumentDao documentDao, DocumentChunkDao chunkDao,
                                 CorpusDomainDao domainDao, CorpusBulkDownloadService downloadService,
                                 PatentChunkingService chunkingService) {
        this.documentDao = documentDao;
        this.chunkDao = chunkDao;
        this.domainDao = domainDao;
        this.downloadService = downloadService;
        this.chunkingService = chunkingService;
    }

    public PipelineResult runProcessingPipeline(PipelineCallback callback) {
        long startTime = System.currentTimeMillis();
        int totalDownloaded = 0;
        int totalExtracted = 0;
        int totalChunked = 0;

        try {
            if (callback != null) callback.onPhaseChange("DOWNLOAD", "Downloading pending PDFs...");
            List<CorpusBulkDownloadService.DownloadResult> downloads =
                    downloadService.downloadPendingPdfs(500, wrapCallback(callback));
            totalDownloaded = (int) downloads.stream().filter(CorpusBulkDownloadService.DownloadResult::success).count();

            if (callback != null && callback.isCancelled()) {
                return PipelineResult.cancelled(System.currentTimeMillis() - startTime);
            }

            if (callback != null) callback.onPhaseChange("EXTRACT", "Extracting text from PDFs...");
            List<CorpusBulkDownloadService.ExtractionResult> extractions =
                    downloadService.extractPendingText(500, wrapCallback(callback));
            totalExtracted = (int) extractions.stream().filter(CorpusBulkDownloadService.ExtractionResult::success).count();

            if (callback != null && callback.isCancelled()) {
                return PipelineResult.cancelled(System.currentTimeMillis() - startTime);
            }

            if (callback != null) callback.onPhaseChange("CHUNK", "Chunking patent documents...");
            totalChunked = chunkPendingDocuments(500, callback);

            long duration = System.currentTimeMillis() - startTime;
            return new PipelineResult(true, false, null, duration,
                    0, totalDownloaded, totalExtracted, totalChunked);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return PipelineResult.failure("Pipeline error: " + msg,
                    System.currentTimeMillis() - startTime);
        }
    }

    public PipelineResult runFullPipeline(String domainName, PipelineCallback callback) {
        long startTime = System.currentTimeMillis();
        int totalDiscovered = 0;
        int totalDownloaded = 0;
        int totalExtracted = 0;
        int totalChunked = 0;

        try {
            CorpusDomain domain = domainDao.findByName(domainName);
            if (domain == null) {
                return PipelineResult.failure("Unknown domain: " + domainName,
                        System.currentTimeMillis() - startTime);
            }

            // Phase 1: Discover patents via PatentsView
            if (callback != null) callback.onPhaseChange("DISCOVER", "Discovering patents for " + domain.getDisplayName() + "...");
            CorpusBulkDownloadService.DiscoverResult discoverResult =
                    downloadService.discoverPatents(domain, wrapCallback(callback));
            totalDiscovered = discoverResult.newlyAdded();

            if (callback != null && callback.isCancelled()) {
                return PipelineResult.cancelled(System.currentTimeMillis() - startTime);
            }

            if (callback != null) callback.onPhaseChange("DOWNLOAD", "Downloading PDFs...");
            List<CorpusBulkDownloadService.DownloadResult> downloads =
                    downloadService.downloadPendingPdfs(100, wrapCallback(callback));
            totalDownloaded = (int) downloads.stream().filter(CorpusBulkDownloadService.DownloadResult::success).count();

            if (callback != null && callback.isCancelled()) {
                return PipelineResult.cancelled(System.currentTimeMillis() - startTime);
            }

            // Phase 3: Extract text from PDFs
            if (callback != null) callback.onPhaseChange("EXTRACT", "Extracting text from PDFs...");
            List<CorpusBulkDownloadService.ExtractionResult> extractions =
                    downloadService.extractPendingText(100, wrapCallback(callback));
            totalExtracted = (int) extractions.stream().filter(CorpusBulkDownloadService.ExtractionResult::success).count();

            if (callback != null && callback.isCancelled()) {
                return PipelineResult.cancelled(System.currentTimeMillis() - startTime);
            }

            // Phase 4: Chunk extracted text
            if (callback != null) callback.onPhaseChange("CHUNK", "Chunking patent documents...");
            totalChunked = chunkPendingDocuments(100, callback);

            long duration = System.currentTimeMillis() - startTime;
            return new PipelineResult(true, false, null, duration,
                    totalDiscovered, totalDownloaded, totalExtracted, totalChunked);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return PipelineResult.failure("Pipeline error: " + msg,
                    System.currentTimeMillis() - startTime);
        }
    }

    public int chunkPendingDocuments(int batchSize, PipelineCallback callback) throws SQLException {
        List<CorpusDocument> pending = documentDao.findPendingChunking(batchSize);
        int totalChunks = 0;

        for (int i = 0; i < pending.size(); i++) {
            if (callback != null && callback.isCancelled()) break;

            CorpusDocument doc = pending.get(i);
            if (callback != null) {
                callback.onProgress(i + 1, pending.size(),
                        "Chunking " + doc.getPatentNumber() + "...");
            }

            if (doc.getFullText() == null || doc.getFullText().isBlank()) {
                documentDao.updateChunkingStatus(doc.getId(), "FAILED", 0);
                continue;
            }

            try {
                List<DocumentChunk> chunks = chunkingService.chunkDocument(
                        doc.getId(), doc.getPatentNumber(), doc.getFullText());

                if (!chunks.isEmpty()) {
                    chunkDao.insertBatch(chunks);
                }

                documentDao.updateChunkingStatus(doc.getId(), "COMPLETE", chunks.size());
                totalChunks += chunks.size();
            } catch (Exception e) {
                documentDao.updateChunkingStatus(doc.getId(), "FAILED", 0);
            }
        }

        return totalChunks;
    }

    public List<CorpusDomain> getAvailableDomains() throws SQLException {
        return domainDao.findAll();
    }

    public int[] getCorpusStats() throws SQLException {
        return documentDao.getStatusCounts();
    }

    public void deleteCorpusByDomain(String domain) throws SQLException {
        documentDao.deleteByDomain(domain);
    }

    private CorpusBulkDownloadService.ProgressCallback wrapCallback(PipelineCallback callback) {
        if (callback == null) return null;
        return new CorpusBulkDownloadService.ProgressCallback() {
            @Override public void onStatus(String status) { callback.onProgress(0, 0, status); }
            @Override public void onProgress(int current, int total, String detail) {
                callback.onProgress(current, total, detail);
            }
            @Override public boolean isCancelled() { return callback.isCancelled(); }
        };
    }

    public record PipelineResult(boolean success, boolean cancelled, String error, long durationMs,
                                  int discovered, int downloaded, int extracted, int chunked) {
        static PipelineResult failure(String error, long durationMs) {
            return new PipelineResult(false, false, error, durationMs, 0, 0, 0, 0);
        }
        static PipelineResult cancelled(long durationMs) {
            return new PipelineResult(false, true, "Pipeline cancelled", durationMs, 0, 0, 0, 0);
        }
    }

    public interface PipelineCallback {
        default void onPhaseChange(String phase, String description) {}
        default void onProgress(int current, int total, String detail) {}
        default boolean isCancelled() { return false; }
    }
}
