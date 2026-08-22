package com.patenttracker.controller;

import com.patenttracker.model.CorpusDomain;
import com.patenttracker.model.DocumentChunk;
import com.patenttracker.service.CorpusBuilderService;
import com.patenttracker.service.CorpusSearchService;
import com.patenttracker.service.TrainingDataExportService;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.SQLException;
import java.util.List;

public class CorpusController {

    @FXML private Label statusLabel;
    @FXML private Label totalDocsLabel;
    @FXML private Label downloadedLabel;
    @FXML private Label extractedLabel;
    @FXML private Label chunkedLabel;
    @FXML private Label totalChunksLabel;
    @FXML private Label totalWordsLabel;

    @FXML private ComboBox<String> domainCombo;
    @FXML private Button buildButton;
    @FXML private Button cancelButton;
    @FXML private Label phaseLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> sectionFilterCombo;
    @FXML private ComboBox<String> searchModeCombo;
    @FXML private Button searchButton;

    @FXML private javafx.scene.layout.VBox searchResultsBox;
    @FXML private Label searchResultsLabel;
    @FXML private Label searchTypeLabel;
    @FXML private TableView<DocumentChunk> searchResultsTable;
    @FXML private TableColumn<DocumentChunk, String> srPatentCol;
    @FXML private TableColumn<DocumentChunk, String> srSectionCol;
    @FXML private TableColumn<DocumentChunk, String> srTextCol;
    @FXML private TableColumn<DocumentChunk, Number> srWordsCol;

    @FXML private TextField exportNameField;
    @FXML private ComboBox<String> exportFormatCombo;
    @FXML private CheckBox includeMetadataCheck;
    @FXML private Button exportButton;
    @FXML private Label exportResultLabel;

    private final CorpusBuilderService builderService = new CorpusBuilderService();
    private final CorpusSearchService searchService = new CorpusSearchService();
    private final TrainingDataExportService exportService = new TrainingDataExportService();

    private Task<?> currentTask;

    @FXML
    public void initialize() {
        cancelButton.setManaged(false);
        cancelButton.setVisible(false);

        sectionFilterCombo.setItems(FXCollections.observableArrayList(
                "All Sections", "ABSTRACT", "CLAIMS", "CLAIM_INDIVIDUAL",
                "DESCRIPTION", "DETAILED_DESCRIPTION", "SUMMARY",
                "BACKGROUND", "FIELD_OF_INVENTION", "FULL_TEXT"));
        sectionFilterCombo.getSelectionModel().selectFirst();

        searchModeCombo.setItems(FXCollections.observableArrayList(
                "Keyword", "Semantic", "Hybrid"));
        searchModeCombo.getSelectionModel().selectFirst();

        exportFormatCombo.setItems(FXCollections.observableArrayList(
                "JSONL", "CSV", "Instruction Pairs"));
        exportFormatCombo.getSelectionModel().selectFirst();

        srPatentCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getPatentNumber()));
        srSectionCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getSectionType()));
        srTextCol.setCellValueFactory(cell -> {
            String text = cell.getValue().getChunkText();
            if (text != null && text.length() > 200) text = text.substring(0, 200) + "...";
            return new SimpleStringProperty(text);
        });
        srWordsCol.setCellValueFactory(cell ->
                new SimpleIntegerProperty(cell.getValue().getWordCount()));

        loadDomains();
        refreshStats();
    }

    private void loadDomains() {
        try {
            List<CorpusDomain> domains = builderService.getAvailableDomains();
            domainCombo.setItems(FXCollections.observableArrayList(
                    domains.stream().map(d -> d.getName() + " - " + d.getDisplayName()).toList()));
            if (!domains.isEmpty()) {
                domainCombo.getSelectionModel().selectFirst();
            }
        } catch (SQLException e) {
            statusLabel.setText("Failed to load domains: " + e.getMessage());
        }
    }

    private void refreshStats() {
        try {
            int[] stats = builderService.getCorpusStats();
            totalDocsLabel.setText(String.valueOf(stats[0]));
            downloadedLabel.setText(String.valueOf(stats[1]));
            extractedLabel.setText(String.valueOf(stats[2]));
            chunkedLabel.setText(String.valueOf(stats[3]));
            totalChunksLabel.setText(formatNumber(stats[4]));
            totalWordsLabel.setText(formatNumber(stats[5]));
        } catch (SQLException e) {
            statusLabel.setText("Failed to load stats");
        }
    }

    @FXML
    private void handleBuild() {
        String selected = domainCombo.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isBlank()) {
            statusLabel.setText("Please select a domain.");
            return;
        }

        String domainName = selected.split(" - ")[0].trim();

        buildButton.setDisable(true);
        cancelButton.setManaged(true);
        cancelButton.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        Task<CorpusBuilderService.PipelineResult> task = new Task<>() {
            @Override
            protected CorpusBuilderService.PipelineResult call() {
                Task<?> self = this;
                return builderService.runFullPipeline(domainName, new CorpusBuilderService.PipelineCallback() {
                    @Override
                    public void onPhaseChange(String phase, String description) {
                        Platform.runLater(() -> phaseLabel.setText(phase));
                        Platform.runLater(() -> progressLabel.setText(description));
                    }

                    @Override
                    public void onProgress(int current, int total, String detail) {
                        if (total > 0) {
                            Platform.runLater(() -> {
                                progressBar.setProgress((double) current / total);
                                progressLabel.setText(detail);
                            });
                        } else {
                            Platform.runLater(() -> progressLabel.setText(detail));
                        }
                    }

                    @Override
                    public boolean isCancelled() {
                        return self.isCancelled();
                    }
                });
            }
        };

        task.setOnSucceeded(event -> {
            CorpusBuilderService.PipelineResult result = task.getValue();
            buildButton.setDisable(false);
            cancelButton.setManaged(false);
            cancelButton.setVisible(false);
            progressBar.setProgress(1.0);

            if (result.success()) {
                String msg = String.format("Complete: %d discovered, %d downloaded, %d extracted, %d chunked (%.1fs)",
                        result.discovered(), result.downloaded(), result.extracted(), result.chunked(),
                        result.durationMs() / 1000.0);
                statusLabel.setText(msg);
                progressLabel.setText(msg);
                phaseLabel.setText("DONE");
            } else {
                statusLabel.setText(result.error() != null ? result.error() : "Pipeline failed");
                phaseLabel.setText("FAILED");
            }

            refreshStats();
        });

        task.setOnFailed(event -> {
            buildButton.setDisable(false);
            cancelButton.setManaged(false);
            cancelButton.setVisible(false);
            progressBar.setProgress(0);
            statusLabel.setText("Pipeline error: " + task.getException().getMessage());
            phaseLabel.setText("ERROR");
        });

        currentTask = task;
        Thread.ofVirtual().start(task);
    }

    @FXML
    private void handleCancel() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
            statusLabel.setText("Cancelling...");
        }
    }

    @FXML
    private void handleSearch() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            statusLabel.setText("Enter a search query.");
            return;
        }

        String sectionFilter = sectionFilterCombo.getSelectionModel().getSelectedItem();
        if ("All Sections".equals(sectionFilter)) sectionFilter = null;

        String mode = searchModeCombo.getSelectionModel().getSelectedItem();
        if (mode == null) mode = "Keyword";

        searchButton.setDisable(true);
        String finalSectionFilter = sectionFilter;
        String finalMode = mode;

        Task<CorpusSearchService.SearchResult> task = new Task<>() {
            @Override
            protected CorpusSearchService.SearchResult call() throws Exception {
                return switch (finalMode) {
                    case "Semantic" -> searchService.searchSemantic(query, finalSectionFilter);
                    case "Hybrid" -> searchService.searchHybrid(query, finalSectionFilter);
                    default -> searchService.searchKeyword(query, finalSectionFilter, 50);
                };
            }
        };

        task.setOnSucceeded(event -> {
            searchButton.setDisable(false);
            CorpusSearchService.SearchResult result = task.getValue();

            searchResultsBox.setManaged(true);
            searchResultsBox.setVisible(true);
            searchResultsLabel.setText(result.totalMatches() + " results found");
            searchTypeLabel.setText("Search mode: " + result.searchType());

            if (result.error() != null) {
                searchTypeLabel.setText(searchTypeLabel.getText() + " (" + result.error() + ")");
            }

            searchResultsTable.setItems(FXCollections.observableArrayList(result.results()));
        });

        task.setOnFailed(event -> {
            searchButton.setDisable(false);
            statusLabel.setText("Search failed: " + task.getException().getMessage());
        });

        Thread.ofVirtual().start(task);
    }

    @FXML
    private void handleExport() {
        String name = exportNameField.getText();
        if (name == null || name.isBlank()) {
            name = "patent_corpus_export";
        }

        String format = exportFormatCombo.getSelectionModel().getSelectedItem();
        if (format == null) format = "JSONL";

        boolean includeMetadata = includeMetadataCheck.isSelected();

        exportButton.setDisable(true);
        exportResultLabel.setText("Exporting...");
        String finalName = name;
        String finalFormat = format;

        Task<TrainingDataExportService.ExportResult> task = new Task<>() {
            @Override
            protected TrainingDataExportService.ExportResult call() throws Exception {
                TrainingDataExportService.ExportConfig config =
                        new TrainingDataExportService.ExportConfig(null, null, 0, includeMetadata);
                return switch (finalFormat) {
                    case "CSV" -> exportService.exportCsv(finalName, config);
                    case "Instruction Pairs" -> exportService.exportInstructionPairs(finalName, config);
                    default -> exportService.exportJsonl(finalName, config);
                };
            }
        };

        task.setOnSucceeded(event -> {
            exportButton.setDisable(false);
            TrainingDataExportService.ExportResult result = task.getValue();
            if (result.success()) {
                exportResultLabel.setText(String.format("Exported %,d records (%s, %,d bytes) to %s",
                        result.recordCount(), result.format(), result.fileSizeBytes(), result.filePath()));
                exportResultLabel.setStyle("-fx-text-fill: #27ae60;");
            } else {
                exportResultLabel.setText("Export failed: " + result.error());
                exportResultLabel.setStyle("-fx-text-fill: #e74c3c;");
            }
        });

        task.setOnFailed(event -> {
            exportButton.setDisable(false);
            exportResultLabel.setText("Export error: " + task.getException().getMessage());
            exportResultLabel.setStyle("-fx-text-fill: #e74c3c;");
        });

        Thread.ofVirtual().start(task);
    }

    private String formatNumber(int value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fK", value / 1_000.0);
        return String.valueOf(value);
    }
}
