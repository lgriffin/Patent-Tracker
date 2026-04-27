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
import javafx.scene.control.*;
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
    @FXML private Button exportButton;
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
        whitespaceCountLabel.setText(String.valueOf(byType.getOrDefault("WHITESPACE", 0)));
        clusteringCountLabel.setText(String.valueOf(byType.getOrDefault("CLUSTERING", 0)));
        adjacencyCountLabel.setText(String.valueOf(byType.getOrDefault("ADJACENCY", 0)));
        temporalCountLabel.setText(String.valueOf(byType.getOrDefault("TEMPORAL_TRENDS", 0)));
        collisionCountLabel.setText(String.valueOf(byType.getOrDefault("CLAIM_COLLISION", 0)));
        competitorCountLabel.setText(String.valueOf(byType.getOrDefault("COMPETITOR_GAPS", 0)));
        inventionCountLabel.setText(String.valueOf(byType.getOrDefault("INVENTION_PROMPTS", 0)));
        crossDomainCountLabel.setText(String.valueOf(byType.getOrDefault("CROSS_DOMAIN", 0)));

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
                }
            }
        } catch (SQLException e) {
            // Proceed with analysis
        }

        setRunning(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText("Running " + label + "... (this may take several minutes)");

        new Thread(() -> {
            try {
                List<Patent> patents = patentDao.findAll();
                InsightService.InsightResult result = switch (analysisType) {
                    case "WHITESPACE" -> insightService.analyzeWhitespace(patents);
                    case "CLUSTERING" -> insightService.analyzeClustering(patents);
                    case "ADJACENCY" -> insightService.analyzeAdjacency(patents);
                    case "TEMPORAL_TRENDS" -> insightService.analyzeTemporalTrends(patents);
                    case "CLAIM_COLLISION" -> insightService.analyzeClaimCollision(patents);
                    case "COMPETITOR_GAPS" -> insightService.analyzeCompetitorGaps(patents);
                    case "INVENTION_PROMPTS" -> insightService.analyzeInventionPrompts(patents);
                    case "CROSS_DOMAIN" -> insightService.analyzeCrossDomain(patents);
                    default -> new InsightService.InsightResult(false, analysisType, null, "Unknown type", 0);
                };

                Platform.runLater(() -> {
                    if (result.success()) {
                        progressLabel.setText(label + " completed in " + (result.durationMs() / 1000) + "s.");
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

    private void setRunning(boolean running) {
        extractAllButton.setDisable(running);
        analyzeAllButton.setDisable(running);
        whitespaceButton.setDisable(running);
        clusteringButton.setDisable(running);
        adjacencyButton.setDisable(running);
        temporalButton.setDisable(running);
        collisionButton.setDisable(running);
        competitorButton.setDisable(running);
        inventionButton.setDisable(running);
        crossDomainButton.setDisable(running);
        exportButton.setDisable(running);
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

                TitledPane pane = new TitledPane(title, content);
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
}
