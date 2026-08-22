package com.patenttracker.service;

import com.patenttracker.dao.CorpusDocumentDao;
import com.patenttracker.dao.CorpusDomainDao;
import com.patenttracker.dao.PriorArtDatabaseManager;
import com.patenttracker.model.CorpusDomain;

import java.util.List;

public class CorpusRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("Initializing prior_art.db...");
        PriorArtDatabaseManager.getInstance().initialize();

        boolean processOnly = List.of(args).contains("--process-only");
        boolean resetFailed = List.of(args).contains("--reset-failed");

        if (resetFailed) {
            CorpusDocumentDao docDao = new CorpusDocumentDao();
            int reset = docDao.resetFailedDownloads();
            System.out.println("Reset " + reset + " failed/in-progress downloads to PENDING.");
        }

        CorpusDomainDao domainDao = new CorpusDomainDao();
        List<CorpusDomain> domains = domainDao.findAll();

        System.out.println("Found " + domains.size() + " domains:");
        for (CorpusDomain d : domains) {
            System.out.println("  - " + d.getName() + " (" + d.getDisplayName() + ")");
        }

        CorpusBuilderService builder = new CorpusBuilderService();

        if (processOnly) {
            System.out.println("\nRunning processing pipeline (download/extract/chunk) for all pending documents...\n");
            CorpusBuilderService.PipelineCallback cb = new CorpusBuilderService.PipelineCallback() {
                @Override
                public void onPhaseChange(String phase, String description) {
                    System.out.println("[process] " + phase + ": " + description);
                }
                @Override
                public void onProgress(int current, int total, String detail) {
                    if (total > 0) {
                        System.out.println("[process] " + current + "/" + total + " " + detail);
                    } else {
                        System.out.println("[process] " + detail);
                    }
                }
            };
            CorpusBuilderService.PipelineResult result = builder.runProcessingPipeline(cb);
            if (result.success()) {
                System.out.printf("COMPLETE: %d downloaded, %d extracted, %d chunked (%.1fs)%n",
                        result.downloaded(), result.extracted(), result.chunked(),
                        result.durationMs() / 1000.0);
            } else {
                System.out.println("FAILED: " + result.error());
            }
        } else {
            // Filter to specific domains if args provided, otherwise all
            List<CorpusDomain> toProcess;
            List<String> domainArgs = List.of(args).stream()
                    .filter(a -> !a.startsWith("--"))
                    .toList();
            if (!domainArgs.isEmpty()) {
                toProcess = domains.stream()
                        .filter(d -> domainArgs.contains(d.getName()))
                        .toList();
            } else {
                toProcess = domains;
            }

            System.out.println("\nStarting corpus build for " + toProcess.size() + " domains (sequential to respect rate limits)...\n");

            for (CorpusDomain domain : toProcess) {
                try {
                    System.out.println("[" + domain.getName() + "] Starting pipeline...");
                    CorpusBuilderService.PipelineResult result = builder.runFullPipeline(
                            domain.getName(), new CorpusBuilderService.PipelineCallback() {
                                @Override
                                public void onPhaseChange(String phase, String description) {
                                    System.out.println("[" + domain.getName() + "] " + phase + ": " + description);
                                }

                                @Override
                                public void onProgress(int current, int total, String detail) {
                                    if (total > 0) {
                                        System.out.println("[" + domain.getName() + "] " + current + "/" + total + " " + detail);
                                    } else {
                                        System.out.println("[" + domain.getName() + "] " + detail);
                                    }
                                }
                            });

                    if (result.success()) {
                        System.out.printf("[%s] COMPLETE: %d discovered, %d downloaded, %d extracted, %d chunked (%.1fs)%n",
                                domain.getName(), result.discovered(), result.downloaded(),
                                result.extracted(), result.chunked(), result.durationMs() / 1000.0);
                    } else {
                        System.out.println("[" + domain.getName() + "] FAILED: " + result.error());
                    }
                } catch (Exception e) {
                    System.out.println("[" + domain.getName() + "] ERROR: " + e.getMessage());
                }
            }
        }
        System.out.println("\nAll domains complete.");

        // Print final stats
        try {
            int[] stats = builder.getCorpusStats();
            System.out.printf("Corpus totals: %d documents, %d downloaded, %d extracted, %d chunked, %d chunks, %d words%n",
                    stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
        } catch (Exception e) {
            System.out.println("Could not load stats: " + e.getMessage());
        }
    }
}
