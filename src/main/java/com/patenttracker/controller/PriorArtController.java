package com.patenttracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patenttracker.model.DiscoveredPatent;
import com.patenttracker.model.SearchAnalysis;
import com.patenttracker.model.SearchSession;
import com.patenttracker.service.ClaudeCliService;
import com.patenttracker.service.PriorArtService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
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

public class PriorArtController {

    @FXML private TextArea ideaTextArea;
    @FXML private Button searchButton;
    @FXML private Button cancelButton;
    @FXML private Button exportButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Label phaseLabel;

    // Results
    @FXML private VBox resultsBox;
    @FXML private TitledPane decomposePane;
    @FXML private VBox decomposeContent;
    @FXML private TitledPane patentsPane;
    @FXML private Label patentsFoundLabel;
    @FXML private Label googleCountLabel;
    @FXML private Label patentsViewCountLabel;
    @FXML private TableView<DiscoveredPatent> discoveredTable;
    @FXML private TableColumn<DiscoveredPatent, String> patentNumCol;
    @FXML private TableColumn<DiscoveredPatent, String> titleCol;
    @FXML private TableColumn<DiscoveredPatent, String> sourceCol;
    @FXML private TableColumn<DiscoveredPatent, String> dateCol;
    @FXML private TitledPane overlapPane;
    @FXML private VBox overlapContent;
    @FXML private TitledPane suggestionsPane;
    @FXML private VBox suggestionsContent;

    // History
    @FXML private Label historyEmptyLabel;
    @FXML private Accordion historyAccordion;

    private final PriorArtService priorArtService = new PriorArtService();
    private static final ObjectMapper om = new ObjectMapper();
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Task<?> currentTask;
    private int currentSessionId = -1;

    @FXML
    public void initialize() {
        cancelButton.setManaged(false);
        cancelButton.setVisible(false);

        patentNumCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getPatentNumber()));
        titleCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getTitle()));
        sourceCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getSource()));
        dateCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getGrantDate() != null
                        ? cell.getValue().getGrantDate().toString() : ""));

        loadHistory();
    }

    @FXML
    public void handleSearch() {
        String ideaText = ideaTextArea.getText();
        if (ideaText == null || ideaText.isBlank()) {
            progressLabel.setText("Please enter an invention idea first.");
            return;
        }

        if (!new ClaudeCliService().isAvailable()) {
            progressLabel.setText("Claude CLI not found. Configure it in Settings.");
            return;
        }

        setRunning(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        resultsBox.setManaged(false);
        resultsBox.setVisible(false);

        var task = new Task<PriorArtService.PipelineResult>() {
            @Override
            protected PriorArtService.PipelineResult call() {
                var self = this;
                return priorArtService.runFullPipeline(ideaText,
                        new PriorArtService.PipelineProgressCallback() {
                            @Override
                            public void onPhaseChange(int phase, String description) {
                                Platform.runLater(() -> {
                                    phaseLabel.setText("Phase " + phase + "/4");
                                    progressBar.setProgress(phase / 4.0);
                                });
                                updateMessage(description);
                            }

                            @Override
                            public void onSearchStatus(String status) {
                                updateMessage(status);
                            }

                            @Override
                            public void onDecomposeComplete(String decomposedJson) {
                                Platform.runLater(() -> displayDecomposition(decomposedJson));
                            }

                            @Override
                            public void onSearchComplete(int patentsFound) {
                                Platform.runLater(() -> {
                                    patentsFoundLabel.setText(String.valueOf(patentsFound));
                                    resultsBox.setManaged(true);
                                    resultsBox.setVisible(true);
                                });
                            }

                            @Override
                            public boolean isCancelled() {
                                return self.isCancelled();
                            }
                        });
            }
        };

        progressLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(event -> {
            progressLabel.textProperty().unbind();
            PriorArtService.PipelineResult result = task.getValue();
            currentSessionId = result.sessionId();

            if (result.success()) {
                progressLabel.setText("Complete in " + (result.durationMs() / 1000) + "s."
                        + formatCost(result));
                progressBar.setProgress(1.0);
                phaseLabel.setText("Complete");
                displayFullResults(result.sessionId());
                exportButton.setDisable(false);
            } else {
                progressLabel.setText("Failed: " + result.error());
                progressBar.setProgress(0);
                phaseLabel.setText("Failed");
                if (result.sessionId() > 0) {
                    displayPartialResults(result.sessionId());
                }
            }
            loadHistory();
            setRunning(false);
        });

        task.setOnFailed(event -> {
            progressLabel.textProperty().unbind();
            Throwable ex = task.getException();
            progressLabel.setText("Error: " + ex.getMessage());
            progressBar.setProgress(0);
            phaseLabel.setText("");
            setRunning(false);
        });

        task.setOnCancelled(event -> {
            progressLabel.textProperty().unbind();
            progressLabel.setText("Search cancelled.");
            progressBar.setProgress(0);
            phaseLabel.setText("");
            setRunning(false);
        });

        currentTask = task;
        Thread.ofVirtual().start(task);
    }

    @FXML
    private void handleCancel() {
        if (currentTask != null) {
            currentTask.cancel();
            progressLabel.textProperty().unbind();
            progressLabel.setText("Cancelling...");
        }
    }

    @FXML
    public void handleExport() {
        if (currentSessionId < 0) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Prior Art Report");
        fileChooser.setInitialFileName("prior-art-report.md");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown Files", "*.md"));

        Stage stage = (Stage) progressLabel.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            progressLabel.setText("Exporting...");

            var task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    String markdown = priorArtService.exportMarkdown(currentSessionId);
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(markdown);
                    }
                    return null;
                }
            };

            task.setOnSucceeded(e -> progressLabel.setText("Exported to " + file.getName()));
            task.setOnFailed(e -> progressLabel.setText("Export failed: " + task.getException().getMessage()));
            Thread.ofVirtual().start(task);
        }
    }

    private void displayDecomposition(String json) {
        decomposeContent.getChildren().clear();
        try {
            JsonNode root = om.readTree(json);

            String summary = textOf(root, "idea_summary");
            if (summary != null) {
                Label summaryLabel = new Label(summary);
                summaryLabel.setWrapText(true);
                summaryLabel.setStyle("-fx-font-size: 12px;");
                decomposeContent.getChildren().add(summaryLabel);
            }

            JsonNode concepts = root.get("key_concepts");
            if (concepts != null && concepts.isArray()) {
                Label header = new Label("Key Concepts:");
                header.setStyle("-fx-font-weight: bold;");
                decomposeContent.getChildren().add(header);
                for (JsonNode c : concepts) {
                    Label item = new Label("  - " + textOf(c, "concept") + ": " + textOf(c, "description"));
                    item.setWrapText(true);
                    decomposeContent.getChildren().add(item);
                }
            }

            JsonNode claims = root.get("potential_claims");
            if (claims != null && claims.isArray()) {
                Label header = new Label("Potential Claims:");
                header.setStyle("-fx-font-weight: bold;");
                decomposeContent.getChildren().add(header);
                int num = 1;
                for (JsonNode c : claims) {
                    Label item = new Label("  " + num++ + ". " + c.asText());
                    item.setWrapText(true);
                    decomposeContent.getChildren().add(item);
                }
            }

            JsonNode cpcs = root.get("cpc_classifications");
            if (cpcs != null && cpcs.isArray()) {
                Label header = new Label("CPC Classifications:");
                header.setStyle("-fx-font-weight: bold;");
                decomposeContent.getChildren().add(header);
                for (JsonNode c : cpcs) {
                    Label item = new Label("  - " + textOf(c, "code") + ": " + textOf(c, "description"));
                    item.setWrapText(true);
                    decomposeContent.getChildren().add(item);
                }
            }

            String novelty = textOf(root, "novelty_hypothesis");
            if (novelty != null) {
                Label header = new Label("Novelty Hypothesis:");
                header.setStyle("-fx-font-weight: bold;");
                Label noveltyLabel = new Label(novelty);
                noveltyLabel.setWrapText(true);
                noveltyLabel.setStyle("-fx-text-fill: #27ae60;");
                decomposeContent.getChildren().addAll(header, noveltyLabel);
            }

            decomposePane.setExpanded(true);
            resultsBox.setManaged(true);
            resultsBox.setVisible(true);

        } catch (Exception e) {
            TextArea raw = new TextArea(json);
            raw.setEditable(false);
            raw.setWrapText(true);
            raw.setPrefRowCount(6);
            decomposeContent.getChildren().add(raw);
        }
    }

    private void displayFullResults(int sessionId) {
        try {
            List<DiscoveredPatent> patents = priorArtService.getDiscoveredPatents(sessionId);
            discoveredTable.setItems(FXCollections.observableArrayList(patents));
            patentsFoundLabel.setText(String.valueOf(patents.size()));

            long googleCount = patents.stream()
                    .filter(p -> "GOOGLE_PATENTS".equals(p.getSource())).count();
            long pvCount = patents.stream()
                    .filter(p -> "PATENTSVIEW".equals(p.getSource())).count();
            googleCountLabel.setText(String.valueOf(googleCount));
            patentsViewCountLabel.setText(String.valueOf(pvCount));

            SearchAnalysis overlap = priorArtService.getAnalysisByPhase(sessionId, "ANALYZE");
            if (overlap != null) {
                displayOverlapAnalysis(overlap.getResultJson());
            }

            SearchAnalysis diff = priorArtService.getAnalysisByPhase(sessionId, "DIFFERENTIATE");
            if (diff != null) {
                displayDifferentiation(diff.getResultJson());
            }

            resultsBox.setManaged(true);
            resultsBox.setVisible(true);

        } catch (SQLException e) {
            progressLabel.setText("Error loading results: " + e.getMessage());
        }
    }

    private void displayPartialResults(int sessionId) {
        try {
             List<DiscoveredPatent> patents = priorArtService.getDiscoveredPatents(sessionId);
             if (!patents.isEmpty()) {
                 discoveredTable.setItems(FXCollections.observableArrayList(patents));
                 patentsFoundLabel.setText(String.valueOf(patents.size()));
                 resultsBox.setManaged(true);
                 resultsBox.setVisible(true);
             }
         } catch (SQLException ex) { }
     }

     private void displayOverlapAnalysis(String json) {
        overlapContent.getChildren().clear();
        try {
            JsonNode root = om.readTree(json);

            JsonNode assessment = root.get("overlap_assessment");
            if (assessment != null) {
                String risk = textOf(assessment, "overall_risk");
                Label riskLabel = new Label("Overall Risk: " + risk);
                riskLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;"
                        + ("HIGH".equals(risk) ? " -fx-text-fill: #e74c3c;"
                        : "LOW".equals(risk) ? " -fx-text-fill: #27ae60;"
                        : " -fx-text-fill: #f39c12;"));
                overlapContent.getChildren().add(riskLabel);

                Label summaryLabel = new Label(textOf(assessment, "summary"));
                summaryLabel.setWrapText(true);
                overlapContent.getChildren().add(summaryLabel);
            }

            JsonNode overlaps = root.get("concept_overlaps");
            if (overlaps != null && overlaps.isArray()) {
                for (JsonNode o : overlaps) {
                    String level = textOf(o, "overlap_level");
                    String color = switch (level) {
                        case "FULL" -> "#e74c3c";
                        case "PARTIAL" -> "#f39c12";
                        case "MINIMAL" -> "#3498db";
                        default -> "#27ae60";
                    };

                    Label conceptLabel = new Label(textOf(o, "concept") + " -- " + level);
                    conceptLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + ";");
                    overlapContent.getChildren().add(conceptLabel);

                    JsonNode patents = o.get("overlapping_patents");
                    if (patents != null && patents.isArray()) {
                        for (JsonNode p : patents) {
                            Label pLabel = new Label("  " + textOf(p, "patent_number")
                                    + ": " + textOf(p, "overlap_description"));
                            pLabel.setWrapText(true);
                            pLabel.setStyle("-fx-font-size: 11px;");
                            overlapContent.getChildren().add(pLabel);
                        }
                    }

                    String gap = textOf(o, "gap_description");
                    if (gap != null) {
                        Label gapLabel = new Label("  Gap: " + gap);
                        gapLabel.setWrapText(true);
                        gapLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #27ae60;");
                        overlapContent.getChildren().add(gapLabel);
                    }

                    overlapContent.getChildren().add(new Separator());
                }
            }

            JsonNode blocking = root.get("blocking_patents");
            if (blocking != null && blocking.isArray() && !blocking.isEmpty()) {
                Label header = new Label("Blocking Patents:");
                header.setStyle("-fx-font-weight: bold; -fx-text-fill: #e74c3c;");
                overlapContent.getChildren().add(header);
                for (JsonNode b : blocking) {
                    Label item = new Label("  " + textOf(b, "patent_number")
                            + ": " + textOf(b, "blocking_reason"));
                    item.setWrapText(true);
                    overlapContent.getChildren().add(item);
                }
            }

            overlapPane.setManaged(true);
            overlapPane.setVisible(true);

        } catch (Exception e) {
            TextArea raw = new TextArea(json);
            raw.setEditable(false);
            raw.setWrapText(true);
            raw.setPrefRowCount(8);
            overlapContent.getChildren().add(raw);
            overlapPane.setManaged(true);
            overlapPane.setVisible(true);
        }
    }

    private void displayDifferentiation(String json) {
        suggestionsContent.getChildren().clear();
        try {
            JsonNode root = om.readTree(json);

            String strategy = textOf(root, "differentiation_strategy");
            if (strategy != null) {
                Label strategyLabel = new Label("Strategy: " + strategy);
                strategyLabel.setWrapText(true);
                strategyLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
                suggestionsContent.getChildren().add(strategyLabel);
                suggestionsContent.getChildren().add(new Separator());
            }

            JsonNode suggestions = root.get("suggestions");
            if (suggestions != null && suggestions.isArray()) {
                int num = 1;
                for (JsonNode s : suggestions) {
                    VBox suggBox = new VBox(5);
                    suggBox.setPadding(new Insets(8));
                    suggBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");

                    String strength = textOf(s, "strength");
                    String strengthColor = switch (strength) {
                        case "STRONG" -> "#27ae60";
                        case "MODERATE" -> "#f39c12";
                        default -> "#3498db";
                    };

                    Label titleLabel = new Label(num++ + ". [" + strength + "] " + textOf(s, "title"));
                    titleLabel.setWrapText(true);
                    titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: "
                            + strengthColor + ";");

                    Label descLabel = new Label(textOf(s, "description"));
                    descLabel.setWrapText(true);

                    suggBox.getChildren().addAll(titleLabel, descLabel);

                    String novelty = textOf(s, "novelty_argument");
                    if (novelty != null) {
                        Label noveltyLabel = new Label("Novelty: " + novelty);
                        noveltyLabel.setWrapText(true);
                        noveltyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
                        suggBox.getChildren().add(noveltyLabel);
                    }

                    String claim = textOf(s, "claim_language_hint");
                    if (claim != null) {
                        Label claimLabel = new Label("Claim hint: " + claim);
                        claimLabel.setWrapText(true);
                        claimLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #2980b9; -fx-font-style: italic;");
                        suggBox.getChildren().add(claimLabel);
                    }

                    suggestionsContent.getChildren().add(suggBox);
                }
            }

            JsonNode focus = root.get("recommended_claim_focus");
            if (focus != null && focus.isArray() && !focus.isEmpty()) {
                suggestionsContent.getChildren().add(new Separator());
                Label header = new Label("Recommended Claim Focus:");
                header.setStyle("-fx-font-weight: bold;");
                suggestionsContent.getChildren().add(header);
                for (JsonNode f : focus) {
                    Label item = new Label("  - " + f.asText());
                    item.setWrapText(true);
                    suggestionsContent.getChildren().add(item);
                }
            }

            JsonNode nextSteps = root.get("next_steps");
            if (nextSteps != null && nextSteps.isArray() && !nextSteps.isEmpty()) {
                suggestionsContent.getChildren().add(new Separator());
                Label header = new Label("Next Steps:");
                header.setStyle("-fx-font-weight: bold;");
                suggestionsContent.getChildren().add(header);
                for (JsonNode n : nextSteps) {
                    Label item = new Label("  - " + n.asText());
                    item.setWrapText(true);
                    suggestionsContent.getChildren().add(item);
                }
            }

            suggestionsPane.setManaged(true);
            suggestionsPane.setVisible(true);

        } catch (Exception e) {
            TextArea raw = new TextArea(json);
            raw.setEditable(false);
            raw.setWrapText(true);
            raw.setPrefRowCount(8);
            suggestionsContent.getChildren().add(raw);
            suggestionsPane.setManaged(true);
            suggestionsPane.setVisible(true);
        }
    }

    private void loadHistory() {
        historyAccordion.getPanes().clear();
        try {
            List<SearchSession> sessions = priorArtService.getSearchHistory();

            if (sessions.isEmpty()) {
                historyEmptyLabel.setManaged(true);
                historyEmptyLabel.setVisible(true);
                return;
            }

            historyEmptyLabel.setManaged(false);
            historyEmptyLabel.setVisible(false);

            for (SearchSession session : sessions) {
                String timestamp = session.getCreatedAt() != null
                        ? session.getCreatedAt().format(DT_FMT) : "unknown";
                String ideaPreview = session.getIdeaText();
                if (ideaPreview.length() > 80) {
                    ideaPreview = ideaPreview.substring(0, 80) + "...";
                }

                String title = "[" + session.getStatus() + "] " + ideaPreview + "  (" + timestamp + ")";

                VBox entryContent = new VBox(5);
                entryContent.setPadding(new Insets(5));

                HBox toolbar = new HBox(10);
                toolbar.setAlignment(Pos.CENTER_LEFT);
                Label statusLabel = new Label("Status: " + session.getStatus() + " | " + timestamp);
                statusLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11px;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button loadBtn = new Button("Load");
                loadBtn.getStyleClass().add("secondary-button");
                final int sid = session.getId();
                final String ideaText = session.getIdeaText();
                loadBtn.setOnAction(e -> {
                    ideaTextArea.setText(ideaText);
                    currentSessionId = sid;
                    displayFullResults(sid);
                    exportButton.setDisable(false);
                });

                Button exportBtn = new Button("Export");
                exportBtn.getStyleClass().add("secondary-button");
                exportBtn.setOnAction(e -> exportSession(sid));

                Button deleteBtn = new Button("Delete");
                deleteBtn.getStyleClass().add("secondary-button");
                deleteBtn.setOnAction(e -> {
                    try {
                        priorArtService.deleteSession(sid);
                        loadHistory();
                    } catch (SQLException ex) {
                        progressLabel.setText("Delete failed: " + ex.getMessage());
                    }
                });

                toolbar.getChildren().addAll(statusLabel, spacer, loadBtn, exportBtn, deleteBtn);
                entryContent.getChildren().add(toolbar);

                 int patentCount;
                 try {
                     patentCount = priorArtService.getDiscoveredPatents(sid).size();
                 } catch (SQLException ex) {
                     patentCount = 0;
                 }
                Label statsLabel = new Label("Patents found: " + patentCount);
                statsLabel.setStyle("-fx-font-size: 11px;");
                entryContent.getChildren().add(statsLabel);

                Label ideaLabel = new Label(session.getIdeaText());
                ideaLabel.setWrapText(true);
                ideaLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
                entryContent.getChildren().add(ideaLabel);

                TitledPane historyEntry = new TitledPane(title, entryContent);
                historyEntry.setExpanded(false);
                historyAccordion.getPanes().add(historyEntry);
            }

        } catch (SQLException e) {
            historyEmptyLabel.setText("Error loading history: " + e.getMessage());
            historyEmptyLabel.setManaged(true);
            historyEmptyLabel.setVisible(true);
        }
    }

    private void exportSession(int sessionId) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Prior Art Report");
        fileChooser.setInitialFileName("prior-art-report-" + sessionId + ".md");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown Files", "*.md"));

        Stage stage = (Stage) progressLabel.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            var task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    String markdown = priorArtService.exportMarkdown(sessionId);
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(markdown);
                    }
                    return null;
                }
            };
            task.setOnSucceeded(e -> progressLabel.setText("Exported to " + file.getName()));
            task.setOnFailed(e -> progressLabel.setText("Export failed: " + task.getException().getMessage()));
            Thread.ofVirtual().start(task);
        }
    }

    private void setRunning(boolean running) {
        searchButton.setDisable(running);
        ideaTextArea.setDisable(running);
        exportButton.setDisable(running);
        cancelButton.setManaged(running);
        cancelButton.setVisible(running);
    }

    private String textOf(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        return val.asText();
    }

    private String formatCost(PriorArtService.PipelineResult result) {
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
