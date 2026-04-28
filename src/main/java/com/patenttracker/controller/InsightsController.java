package com.patenttracker.controller;

import com.patenttracker.dao.PatentDao;
import com.patenttracker.model.Patent;
import com.patenttracker.model.PatentAnalysis;
import com.patenttracker.service.ClaudeCliService;
import com.patenttracker.service.InsightService;
import com.patenttracker.service.PdfExtractorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class InsightsController {

    private static final DateTimeFormatter DISPLAY_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private Label patentsWithTextLabel;
    @FXML private Label patentsAnalyzedLabel;
    @FXML private Label totalAnalysesLabel;

    @FXML private Button extractAllButton;
    @FXML private Button analyzeAllButton;
    @FXML private Button whitespaceButton;
    @FXML private Button clusteringButton;
    @FXML private Button adjacencyButton;
    @FXML private Button temporalButton;
    @FXML private Button collisionButton;
    @FXML private Button competitorButton;
    @FXML private Button inventionButton;
    @FXML private Button crossDomainButton;
    @FXML private Button analyzeClaimsButton;
    @FXML private Button analyzeExpansionButton;
    @FXML private Button analyzePriorArtButton;
    @FXML private Button exportButton;
    @FXML private Button exportCrossButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Button cancelButton;

    @FXML private Label claimsCountLabel;
    @FXML private Label technologyCountLabel;
    @FXML private Label expansionCountLabel;
    @FXML private Label priorArtCountLabel;
    @FXML private Label whitespaceCountLabel;
    @FXML private Label clusteringCountLabel;
    @FXML private Label adjacencyCountLabel;
    @FXML private Label temporalCountLabel;
    @FXML private Label collisionCountLabel;
    @FXML private Label competitorCountLabel;
    @FXML private Label inventionCountLabel;
    @FXML private Label crossDomainCountLabel;

    @FXML private Accordion resultsAccordion;

    private final InsightService insightService = new InsightService();
    private final PdfExtractorService pdfExtractorService = new PdfExtractorService();
    private final PatentDao patentDao = new PatentDao();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @FXML
    public void initialize() {
        cancelButton.setManaged(false);
        cancelButton.setVisible(false);
        refresh();
    }

    public void refresh() {
        InsightService.InsightStats stats = insightService.getStats();
        patentsWithTextLabel.setText(String.valueOf(stats.withText()));
        patentsAnalyzedLabel.setText(String.valueOf(stats.withAnalysis()));

        int totalAnalyses = stats.analysisByType().values().stream()
                .mapToInt(Integer::intValue).sum();
        totalAnalysesLabel.setText(String.valueOf(totalAnalyses));

        Map<String, Integer> byType = stats.analysisByType();
        claimsCountLabel.setText(String.valueOf(byType.getOrDefault("CLAIMS", 0)));
        technologyCountLabel.setText(String.valueOf(byType.getOrDefault("TECHNOLOGY", 0)));
        expansionCountLabel.setText(String.valueOf(byType.getOrDefault("EXPANSION", 0)));
        priorArtCountLabel.setText(String.valueOf(byType.getOrDefault("PRIOR_ART", 0)));

        refreshPortfolioStatus(whitespaceCountLabel, "WHITESPACE");
        refreshPortfolioStatus(clusteringCountLabel, "CLUSTERING");
        refreshPortfolioStatus(adjacencyCountLabel, "ADJACENCY");
        refreshPortfolioStatus(temporalCountLabel, "TEMPORAL_TRENDS");
        refreshPortfolioStatus(collisionCountLabel, "CLAIM_COLLISION");
        refreshPortfolioStatus(competitorCountLabel, "COMPETITOR_GAPS");
        refreshPortfolioStatus(inventionCountLabel, "INVENTION_PROMPTS");
        refreshPortfolioStatus(crossDomainCountLabel, "CROSS_DOMAIN");

        loadCrossPatentResults();
    }

    @FXML
    public void handleExtractAll() {
        cancelled.set(false);
        setRunning(true);
        progressBar.setProgress(0);
        progressLabel.setText("Starting text extraction...");

        new Thread(() -> {
            List<PdfExtractorService.ExtractionResult> results =
                    pdfExtractorService.extractAll(new PdfExtractorService.ExtractionProgressCallback() {
                        @Override
                        public void onProgress(int current, int total, String title) {
                            Platform.runLater(() -> {
                                progressBar.setProgress((double) current / total);
                                progressLabel.setText("Extracting " + current + " of " + total + ": "
                                        + truncate(title, 50));
                            });
                        }
                        @Override
                        public void onResult(PdfExtractorService.ExtractionResult result) {}
                        @Override
                        public boolean isCancelled() { return cancelled.get(); }
                    });

            Platform.runLater(() -> {
                long success = results.stream().filter(PdfExtractorService.ExtractionResult::success).count();
                long failed = results.size() - success;
                progressLabel.setText("Extraction complete: " + success + " extracted"
                        + (failed > 0 ? ", " + failed + " failed" : ""));
                progressBar.setProgress(1.0);
                setRunning(false);
                refresh();
            });
        }).start();
    }

    @FXML
    public void handleAnalyzeAll() {
        if (!new ClaudeCliService().isAvailable()) {
            progressLabel.setText("Claude CLI not found. Configure it in Settings.");
            return;
        }

        cancelled.set(false);
        setRunning(true);
        progressBar.setProgress(0);
        progressLabel.setText("Starting batch technology extraction...");

        new Thread(() -> {
            List<InsightService.InsightResult> results =
                    insightService.analyzeAll("TECHNOLOGY", "technology",
                            new InsightService.AnalysisProgressCallback() {
                                @Override
                                public void onProgress(int current, int total, String title) {
                                    Platform.runLater(() -> {
                                        progressBar.setProgress((double) current / total);
                                        progressLabel.setText("Analyzing " + current + " of " + total + ": "
                                                + truncate(title, 50));
                                    });
                                }
                                @Override
                                public void onResult(InsightService.InsightResult result) {}
                                @Override
                                public boolean isCancelled() { return cancelled.get(); }
                            });

            Platform.runLater(() -> {
                long success = results.stream().filter(InsightService.InsightResult::success).count();
                long failed = results.size() - success;
                progressLabel.setText("Analysis complete: " + success + " analyzed"
                        + (failed > 0 ? ", " + failed + " failed" : ""));
                progressBar.setProgress(1.0);
                setRunning(false);
                refresh();
            });
        }).start();
    }

    @FXML
    public void handleAnalyzeClaims() {
        runBatchPerPatentAnalysis("Claims Analysis", "CLAIMS", "claims");
    }

    @FXML
    public void handleAnalyzeExpansion() {
        runBatchPerPatentAnalysis("Expansion Analysis", "EXPANSION", "expansion");
    }

    @FXML
    public void handleAnalyzePriorArt() {
        runBatchPerPatentAnalysis("Prior Art Analysis", "PRIOR_ART", "prior-art");
    }

    private void runBatchPerPatentAnalysis(String label, String analysisType, String templateName) {
        if (!new ClaudeCliService().isAvailable()) {
            progressLabel.setText("Claude CLI not found. Configure it in Settings.");
            return;
        }

        cancelled.set(false);
        setRunning(true);
        progressBar.setProgress(0);
        progressLabel.setText("Starting batch " + label.toLowerCase() + "...");

        new Thread(() -> {
            List<InsightService.InsightResult> results =
                    insightService.analyzeAll(analysisType, templateName,
                            new InsightService.AnalysisProgressCallback() {
                                @Override
                                public void onProgress(int current, int total, String title) {
                                    Platform.runLater(() -> {
                                        progressBar.setProgress((double) current / total);
                                        progressLabel.setText(label + " " + current + " of " + total + ": "
                                                + truncate(title, 50));
                                    });
                                }
                                @Override
                                public void onResult(InsightService.InsightResult result) {}
                                @Override
                                public boolean isCancelled() { return cancelled.get(); }
                            });

            Platform.runLater(() -> {
                long success = results.stream().filter(InsightService.InsightResult::success).count();
                long failed = results.size() - success;
                progressLabel.setText(label + " complete: " + success + " analyzed"
                        + (failed > 0 ? ", " + failed + " failed" : ""));
                progressBar.setProgress(1.0);
                setRunning(false);
                refresh();
            });
        }).start();
    }

    @FXML
    private void handleWhitespace() {
        runCrossPatentAnalysis("Whitespace Finder", "WHITESPACE");
    }

    @FXML
    private void handleClustering() {
        runCrossPatentAnalysis("Cross-Patent Clustering", "CLUSTERING");
    }

    @FXML
    private void handleAdjacency() {
        runCrossPatentAnalysis("Adjacency Mapping", "ADJACENCY");
    }

    @FXML
    private void handleTemporal() {
        runCrossPatentAnalysis("Temporal Trends", "TEMPORAL_TRENDS");
    }

    @FXML
    private void handleCollision() {
        runCrossPatentAnalysis("Claim Collision", "CLAIM_COLLISION");
    }

    @FXML
    private void handleCompetitor() {
        runCrossPatentAnalysis("Competitor Gaps", "COMPETITOR_GAPS");
    }

    @FXML
    private void handleInvention() {
        runCrossPatentAnalysis("Invention Prompts", "INVENTION_PROMPTS");
    }

    @FXML
    private void handleCrossDomain() {
        runCrossPatentAnalysis("Cross-Domain Combinator", "CROSS_DOMAIN");
    }

    private void runCrossPatentAnalysis(String label, String analysisType) {
        if (!new ClaudeCliService().isAvailable()) {
            progressLabel.setText("Claude CLI not found. Configure it in Settings.");
            return;
        }

        try {
            List<Patent> patents = patentDao.findAll();
            if (!patents.isEmpty()) {
                PatentAnalysis cached = insightService.getCachedAnalysis(
                        patents.get(0).getId(), analysisType);
                if (cached != null) {
                    String when = cached.getAnalyzedAt() != null
                            ? cached.getAnalyzedAt().format(DISPLAY_DATETIME) : "previously";
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle(label);
                    confirm.setHeaderText("Cached results found from " + when);
                    confirm.setContentText("Re-run this analysis? This will overwrite the cached results.");
                    confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                    if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.NO) {
                        progressLabel.setText("Using cached " + label + " results.");
                        return;
                    }
                    insightService.deleteCachedAnalysis(patents.get(0).getId(), analysisType);
                }
            }
        } catch (SQLException e) {
            // Proceed with analysis
        }

        setRunning(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText("Running " + label + "...");

        InsightService.CrossPatentProgressCallback progressCallback =
                new InsightService.CrossPatentProgressCallback() {
            @Override
            public void onChunkProgress(int currentChunk, int totalChunks) {
                Platform.runLater(() -> {
                    progressBar.setProgress((double) currentChunk / (totalChunks + 1));
                    progressLabel.setText("Running " + label + "... Chunk " + currentChunk + "/" + totalChunks);
                });
            }
            @Override
            public void onMergeProgress() {
                Platform.runLater(() -> {
                    progressLabel.setText("Running " + label + "... Merging chunk results...");
                });
            }
            @Override
            public void onStreamingStatus(String status) {
                Platform.runLater(() -> {
                    progressLabel.setText("Running " + label + "... " + status);
                });
            }
            @Override
            public boolean isCancelled() { return cancelled.get(); }
        };

        new Thread(() -> {
            try {
                List<Patent> patents = patentDao.findAll();
                InsightService.InsightResult result = switch (analysisType) {
                    case "WHITESPACE" -> insightService.analyzeWhitespace(patents, progressCallback);
                    case "CLUSTERING" -> insightService.analyzeClustering(patents, progressCallback);
                    case "ADJACENCY" -> insightService.analyzeAdjacency(patents, progressCallback);
                    case "TEMPORAL_TRENDS" -> insightService.analyzeTemporalTrends(patents, progressCallback);
                    case "CLAIM_COLLISION" -> insightService.analyzeClaimCollision(patents, progressCallback);
                    case "COMPETITOR_GAPS" -> insightService.analyzeCompetitorGaps(patents, progressCallback);
                    case "INVENTION_PROMPTS" -> insightService.analyzeInventionPrompts(patents, progressCallback);
                    case "CROSS_DOMAIN" -> insightService.analyzeCrossDomain(patents, progressCallback);
                    default -> new InsightService.InsightResult(false, analysisType, null, "Unknown type", 0);
                };

                Platform.runLater(() -> {
                    if (result.success()) {
                        progressLabel.setText(label + " completed in " + (result.durationMs() / 1000) + "s."
                                + formatCost(result));
                    } else {
                        progressLabel.setText(label + " failed: " + result.error());
                    }
                    progressBar.setProgress(1.0);
                    setRunning(false);
                    refresh();
                });
            } catch (SQLException e) {
                Platform.runLater(() -> {
                    progressLabel.setText("Database error: " + e.getMessage());
                    progressBar.setProgress(0);
                    setRunning(false);
                });
            }
        }).start();
    }

    @FXML
    private void handleExportMarkdown() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Portfolio Insights");
        fileChooser.setInitialFileName("patent-insights.md");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown Files", "*.md"));

        Stage stage = (Stage) progressLabel.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            progressLabel.setText("Exporting...");
            new Thread(() -> {
                try {
                    String markdown = insightService.exportMarkdown();
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(markdown);
                    }
                    Platform.runLater(() ->
                            progressLabel.setText("Exported to " + file.getName()));
                } catch (Exception e) {
                    Platform.runLater(() ->
                            progressLabel.setText("Export failed: " + e.getMessage()));
                }
            }).start();
        }
    }

    @FXML
    private void handleCancel() {
        cancelled.set(true);
        progressLabel.setText("Cancelling...");
    }

    private void refreshPortfolioStatus(Label label, String analysisType) {
        try {
            List<Patent> patents = patentDao.findAll();
            if (!patents.isEmpty()) {
                PatentAnalysis analysis = insightService.getCachedAnalysis(
                        patents.get(0).getId(), analysisType);
                if (analysis != null && analysis.getAnalyzedAt() != null) {
                    label.setText("Done " + analysis.getAnalyzedAt().format(DISPLAY_DATETIME));
                    label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #28a745;");
                    return;
                }
            }
        } catch (SQLException ignored) {}
        label.setText("Not run");
        label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #999;");
    }

    @FXML
    private void handleExportCrossPatent() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Cross-Patent Results");
        fileChooser.setInitialFileName("cross-patent-analysis.md");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown Files", "*.md"));

        Stage stage = (Stage) progressLabel.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            progressLabel.setText("Exporting...");
            new Thread(() -> {
                try {
                    String markdown = insightService.exportCrossPatentMarkdown();
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(markdown);
                    }
                    Platform.runLater(() ->
                            progressLabel.setText("Exported to " + file.getName()));
                } catch (Exception e) {
                    Platform.runLater(() ->
                            progressLabel.setText("Export failed: " + e.getMessage()));
                }
            }).start();
        }
    }

    private void exportSingleResult(String analysisType, String label) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export " + label);
        String filename = label.toLowerCase().replace(" ", "-") + ".md";
        fileChooser.setInitialFileName(filename);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown Files", "*.md"));

        Stage stage = (Stage) progressLabel.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            progressLabel.setText("Exporting " + label + "...");
            new Thread(() -> {
                try {
                    String markdown = insightService.exportSingleAnalysisMarkdown(analysisType, label);
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(markdown);
                    }
                    Platform.runLater(() ->
                            progressLabel.setText("Exported " + label + " to " + file.getName()));
                } catch (Exception e) {
                    Platform.runLater(() ->
                            progressLabel.setText("Export failed: " + e.getMessage()));
                }
            }).start();
        }
    }

    private void setRunning(boolean running) {
        extractAllButton.setDisable(running);
        analyzeAllButton.setDisable(running);
        analyzeClaimsButton.setDisable(running);
        analyzeExpansionButton.setDisable(running);
        analyzePriorArtButton.setDisable(running);
        whitespaceButton.setDisable(running);
        clusteringButton.setDisable(running);
        adjacencyButton.setDisable(running);
        temporalButton.setDisable(running);
        collisionButton.setDisable(running);
        competitorButton.setDisable(running);
        inventionButton.setDisable(running);
        crossDomainButton.setDisable(running);
        exportButton.setDisable(running);
        exportCrossButton.setDisable(running);
        cancelButton.setManaged(running);
        cancelButton.setVisible(running);
    }

    private void loadCrossPatentResults() {
        resultsAccordion.getPanes().clear();
        try {
            List<Patent> patents = patentDao.findAll();
            if (patents.isEmpty()) return;
            int firstId = patents.get(0).getId();

            addAnalysisPane(firstId, "WHITESPACE", "Whitespace Finder");
            addAnalysisPane(firstId, "CLUSTERING", "Cross-Patent Clustering");
            addAnalysisPane(firstId, "ADJACENCY", "Adjacency Mapping");
            addAnalysisPane(firstId, "TEMPORAL_TRENDS", "Temporal Trends");
            addAnalysisPane(firstId, "CLAIM_COLLISION", "Claim Collision");
            addAnalysisPane(firstId, "COMPETITOR_GAPS", "Competitor Gaps");
            addAnalysisPane(firstId, "INVENTION_PROMPTS", "Invention Prompts");
            addAnalysisPane(firstId, "CROSS_DOMAIN", "Cross-Domain Combinator");
        } catch (SQLException e) {
            // Skip loading results
        }
    }

    private void addAnalysisPane(int patentId, String type, String label) {
        try {
            PatentAnalysis analysis = insightService.getCachedAnalysis(patentId, type);
            if (analysis != null) {
                String timestamp = analysis.getAnalyzedAt() != null
                        ? analysis.getAnalyzedAt().format(DISPLAY_DATETIME) : "unknown";
                String title = label + "  (" + timestamp + ")";

                TextArea content = new TextArea(formatJson(analysis.getResultJson()));
                content.setEditable(false);
                content.setWrapText(true);
                content.setPrefRowCount(15);
                content.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");

                Button exportBtn = new Button("Export");
                exportBtn.getStyleClass().add("secondary-button");
                exportBtn.setOnAction(e -> exportSingleResult(type, label));

                HBox toolbar = new HBox(10);
                toolbar.setAlignment(Pos.CENTER_RIGHT);
                toolbar.setPadding(new Insets(0, 0, 5, 0));
                toolbar.getChildren().add(exportBtn);

                VBox wrapper = new VBox(5, toolbar, content);
                wrapper.setPadding(new Insets(5));

                TitledPane pane = new TitledPane(title, wrapper);
                pane.setExpanded(false);
                resultsAccordion.getPanes().add(pane);
            }
        } catch (SQLException e) {
            // Skip
        }
    }

    private String formatJson(String json) {
        try {
            ObjectMapper om = new ObjectMapper();
            Object obj = om.readValue(json, Object.class);
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return json;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String formatCost(InsightService.InsightResult result) {
        if (result.costUsd() <= 0 && result.inputTokens() <= 0) return "";
        StringBuilder sb = new StringBuilder(" | ");
        if (result.costUsd() > 0) {
            sb.append(String.format("$%.4f", result.costUsd()));
        }
        if (result.inputTokens() > 0 || result.outputTokens() > 0) {
            if (result.costUsd() > 0) sb.append(" (");
            sb.append(formatTokenCount(result.inputTokens())).append(" in / ")
              .append(formatTokenCount(result.outputTokens())).append(" out");
            if (result.costUsd() > 0) sb.append(")");
        }
        return sb.toString();
    }

    private String formatTokenCount(long tokens) {
        if (tokens >= 1_000_000) return String.format("%.1fM", tokens / 1_000_000.0);
        if (tokens >= 1_000) return String.format("%.1fK", tokens / 1_000.0);
        return String.valueOf(tokens);
    }
}
