package com.patenttracker.controller;

import com.patenttracker.model.MinedPatent;
import com.patenttracker.model.PatentAnalysis;
import com.patenttracker.service.ClaudeCliService;
import com.patenttracker.service.PatentMiningService;
import com.patenttracker.service.PatentMiningService.AreaOfInterest;
import com.patenttracker.service.PatentMiningService.InventionPromptItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MiningController {

    // Mode selection
    @FXML private RadioButton generalRadio;
    @FXML private RadioButton promptRadio;
    @FXML private ToggleGroup modeGroup;

    // General area section
    @FXML private VBox generalAreaBox;
    @FXML private ComboBox<AreaOfInterest> areaComboBox;
    @FXML private Button refreshAreasButton;
    @FXML private VBox areaDetailBox;
    @FXML private Label areaKeywordsLabel;
    @FXML private Label areaSourcesLabel;

    // Invention prompt section
    @FXML private VBox inventionPromptBox;
    @FXML private ListView<InventionPromptItem> promptListView;
    @FXML private Button refreshPromptsButton;
    @FXML private VBox promptDetailBox;
    @FXML private Label promptTitleLabel;
    @FXML private Label promptDomainLabel;
    @FXML private Label promptProblemLabel;
    @FXML private Label promptDescriptionLabel;
    @FXML private Label promptSourcePatentsLabel;

    // Shared controls
    @FXML private Button mineButton;
    @FXML private Button mineAllButton;
    @FXML private Button cancelButton;
    @FXML private Button exportButton;
    @FXML private Button exportAllButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;

    // Search results
    @FXML private VBox searchResultsBox;
    @FXML private Label patentsFoundLabel;
    @FXML private Label searchAreaLabel;
    @FXML private TableView<MinedPatent> minedPatentsTable;
    @FXML private TableColumn<MinedPatent, String> patentNumCol;
    @FXML private TableColumn<MinedPatent, String> titleCol;
    @FXML private TableColumn<MinedPatent, String> dateCol;

    // History
    @FXML private VBox historyBox;
    @FXML private Label historyEmptyLabel;
    @FXML private Accordion historyAccordion;

    private final PatentMiningService miningService = new PatentMiningService();
    private Task<?> currentTask;
    private List<AreaOfInterest> areas = new ArrayList<>();
    private List<InventionPromptItem> inventionPrompts = new ArrayList<>();
    private String currentMiningArea;
    private boolean currentModeIsIP = false;

    @FXML
    public void initialize() {
        cancelButton.setManaged(false);
        cancelButton.setVisible(false);

        patentNumCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getPatentNumber()));
        titleCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getTitle()));
        dateCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getGrantDate() != null
                        ? cell.getValue().getGrantDate().toString() : ""));

        // Mode toggle
        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            boolean isPromptMode = newToggle == promptRadio;
            generalAreaBox.setManaged(!isPromptMode);
            generalAreaBox.setVisible(!isPromptMode);
            inventionPromptBox.setManaged(isPromptMode);
            inventionPromptBox.setVisible(isPromptMode);
            mineAllButton.setManaged(isPromptMode);
            mineAllButton.setVisible(isPromptMode);
            updateMineButtonState();

            if (isPromptMode && inventionPrompts.isEmpty()) {
                handleRefreshPrompts();
            }
        });

        // General area ComboBox
        areaComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateAreaDetails(newVal);
            } else {
                areaDetailBox.setManaged(false);
                areaDetailBox.setVisible(false);
            }
            updateMineButtonState();
        });

        areaComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(AreaOfInterest area) {
                return area != null ? area.name() : "";
            }
            @Override
            public AreaOfInterest fromString(String string) {
                if (string == null || string.isBlank()) return null;
                return areas.stream()
                        .filter(a -> a.name().equalsIgnoreCase(string))
                        .findFirst()
                        .orElse(new AreaOfInterest(string, List.of(), List.of("Custom")));
            }
        });

        // Invention prompt ListView
        promptListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updatePromptDetails(newVal);
            } else {
                promptDetailBox.setManaged(false);
                promptDetailBox.setVisible(false);
            }
            updateMineButtonState();
        });

        promptListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(InventionPromptItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                }
            }
        });

        refresh();
    }

    private void updateMineButtonState() {
        if (promptRadio.isSelected()) {
            mineButton.setDisable(promptListView.getSelectionModel().getSelectedItem() == null);
        } else {
            mineButton.setDisable(areaComboBox.getValue() == null
                    && (areaComboBox.getEditor().getText() == null
                    || areaComboBox.getEditor().getText().isBlank()));
        }
    }

    public void refresh() {
        if (areas.isEmpty()) {
            handleRefreshAreas();
        }
        loadMiningHistory();
    }

    @FXML
    public void handleRefreshAreas() {
        refreshAreasButton.setDisable(true);
        progressLabel.setText("Extracting areas of interest from portfolio analyses...");

        var task = new Task<List<AreaOfInterest>>() {
            @Override
            protected List<AreaOfInterest> call() throws Exception {
                return miningService.extractAreasOfInterest();
            }
        };

        task.setOnSucceeded(event -> {
            List<AreaOfInterest> extracted = task.getValue();
            areas = extracted;
            areaComboBox.setItems(FXCollections.observableArrayList(areas));
            refreshAreasButton.setDisable(false);
            if (areas.isEmpty()) {
                progressLabel.setText("No areas found. Run portfolio analyses on the Insights tab first.");
            } else {
                progressLabel.setText("Found " + areas.size() + " areas of interest.");
            }
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            progressLabel.setText("Error loading areas: " + ex.getMessage());
            refreshAreasButton.setDisable(false);
        });

        Thread.ofVirtual().start(task);
    }

    @FXML
    public void handleRefreshPrompts() {
        refreshPromptsButton.setDisable(true);
        progressLabel.setText("Loading invention prompts...");

        var task = new Task<List<InventionPromptItem>>() {
            @Override
            protected List<InventionPromptItem> call() throws Exception {
                return miningService.extractInventionPrompts();
            }
        };

        task.setOnSucceeded(event -> {
            List<InventionPromptItem> extracted = task.getValue();
            inventionPrompts = extracted;
            promptListView.setItems(FXCollections.observableArrayList(inventionPrompts));
            refreshPromptsButton.setDisable(false);
            if (inventionPrompts.isEmpty()) {
                progressLabel.setText("No invention prompts found. Run 'Invention Prompts' analysis on the Insights tab first.");
            } else {
                progressLabel.setText("Found " + inventionPrompts.size() + " invention prompts.");
            }
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            progressLabel.setText("Error loading prompts: " + ex.getMessage());
            refreshPromptsButton.setDisable(false);
        });

        Thread.ofVirtual().start(task);
    }

    @FXML
    public void handleMine() {
        if (promptRadio.isSelected()) {
            handleMineInventionPrompt();
        } else {
            handleMineGeneralArea();
        }
    }

    private void handleMineGeneralArea() {
        AreaOfInterest selected = areaComboBox.getValue();
        if (selected == null) {
            String typed = areaComboBox.getEditor().getText();
            if (typed != null && !typed.isBlank()) {
                selected = new AreaOfInterest(typed.trim(), List.of(), List.of("Custom"));
            } else {
                progressLabel.setText("Select an area of interest first.");
                return;
            }
        }

        if (!new ClaudeCliService().isAvailable()) {
            progressLabel.setText("Claude CLI not found. Configure it in Settings.");
            return;
        }

        setRunning(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        currentMiningArea = selected.name();
        currentModeIsIP = false;

        AreaOfInterest finalArea = selected;
        var task = new Task<PatentMiningService.MiningResult>() {
            @Override
            protected PatentMiningService.MiningResult call() {
                var self = this;
                return miningService.mineArea(finalArea,
                        new PatentMiningService.MiningProgressCallback() {
                            @Override
                            public void onStatus(String status) {
                                updateMessage(status);
                            }
                            @Override
                            public boolean isCancelled() { return self.isCancelled(); }
                        });
            }
        };

        progressLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(event -> {
            progressLabel.textProperty().unbind();
            PatentMiningService.MiningResult result = task.getValue();
            if (result.success()) {
                progressLabel.setText("Mining complete in " + (result.durationMs() / 1000) + "s."
                        + formatCost(result));
                displaySearchResults(result.area(), result.externalPatentsFound());
                loadMiningHistory();
            } else {
                progressLabel.setText("Mining failed: " + result.error());
                if (result.externalPatentsFound() > 0) {
                    displaySearchResults(result.area(), result.externalPatentsFound());
                }
            }
            progressBar.setProgress(result.success() ? 1.0 : 0);
            setRunning(false);
        });

        task.setOnFailed(event -> {
            progressLabel.textProperty().unbind();
            Throwable ex = task.getException();
            progressLabel.setText("Mining failed: " + ex.getMessage());
            progressBar.setProgress(0);
            setRunning(false);
        });

        task.setOnCancelled(event -> {
            progressLabel.textProperty().unbind();
            progressBar.setProgress(0);
            progressLabel.setText("Mining cancelled.");
            setRunning(false);
        });

        currentTask = task;
        Thread.ofVirtual().start(task);
    }

    private void handleMineInventionPrompt() {
        InventionPromptItem selected = promptListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            progressLabel.setText("Select an invention prompt first.");
            return;
        }

        if (!new ClaudeCliService().isAvailable()) {
            progressLabel.setText("Claude CLI not found. Configure it in Settings.");
            return;
        }

        setRunning(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        currentMiningArea = selected.title();
        currentModeIsIP = true;

        var task = new Task<PatentMiningService.MiningResult>() {
            @Override
            protected PatentMiningService.MiningResult call() {
                var self = this;
                return miningService.mineInventionPrompt(selected,
                        new PatentMiningService.MiningProgressCallback() {
                            @Override
                            public void onStatus(String status) {
                                updateMessage(status);
                            }
                            @Override
                            public boolean isCancelled() { return self.isCancelled(); }
                        });
            }
        };

        progressLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(event -> {
            progressLabel.textProperty().unbind();
            PatentMiningService.MiningResult result = task.getValue();
            if (result.success()) {
                progressLabel.setText("Mining complete in " + (result.durationMs() / 1000) + "s."
                        + formatCost(result));
                displaySearchResults(result.area(), result.externalPatentsFound());
                loadMiningHistory();
            } else {
                progressLabel.setText("Mining failed: " + result.error());
                if (result.externalPatentsFound() > 0) {
                    displaySearchResults(result.area(), result.externalPatentsFound());
                }
            }
            progressBar.setProgress(result.success() ? 1.0 : 0);
            setRunning(false);
        });

        task.setOnFailed(event -> {
            progressLabel.textProperty().unbind();
            Throwable ex = task.getException();
            progressLabel.setText("Mining failed: " + ex.getMessage());
            progressBar.setProgress(0);
            setRunning(false);
        });

        task.setOnCancelled(event -> {
            progressLabel.textProperty().unbind();
            progressBar.setProgress(0);
            progressLabel.setText("Mining cancelled.");
            setRunning(false);
        });

        currentTask = task;
        Thread.ofVirtual().start(task);
    }

    @FXML
    private void handleCancel() {
        if (currentTask != null) {
            currentTask.cancel();
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            progressLabel.setText("Cancelling...");
        }
    }

    @FXML
    public void handleExport() {
        if (currentMiningArea == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Mining Results");
        String prefix = currentModeIsIP ? "invention-mining-" : "patent-mining-";
        String filename = prefix + currentMiningArea.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-") + ".md";
        fileChooser.setInitialFileName(filename);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown Files", "*.md"));

        Stage stage = (Stage) progressLabel.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            progressLabel.setText("Exporting...");

            var task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    String markdown = currentModeIsIP
                            ? miningService.exportIPMiningMarkdown(currentMiningArea)
                            : miningService.exportMiningMarkdown(currentMiningArea);
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(markdown);
                    }
                    return null;
                }
            };

            task.setOnSucceeded(event ->
                    progressLabel.setText("Exported to " + file.getName()));
            task.setOnFailed(event ->
                    progressLabel.setText("Export failed: " + task.getException().getMessage()));

            Thread.ofVirtual().start(task);
        }
    }

    private void updateAreaDetails(AreaOfInterest area) {
        if (area.keywords() != null && !area.keywords().isEmpty()) {
            areaKeywordsLabel.setText("Keywords: " + String.join(", ", area.keywords()));
        } else {
            areaKeywordsLabel.setText("Keywords: (none — will search by area name)");
        }
        if (area.sourceAnalyses() != null && !area.sourceAnalyses().isEmpty()) {
            areaSourcesLabel.setText("Sources: " + String.join(", ", area.sourceAnalyses()));
        } else {
            areaSourcesLabel.setText("");
        }
        areaDetailBox.setManaged(true);
        areaDetailBox.setVisible(true);
    }

    private void updatePromptDetails(InventionPromptItem prompt) {
        promptTitleLabel.setText(prompt.title());
        promptDomainLabel.setText("[" + prompt.category() + "] "
                + (prompt.domain() != null && !prompt.domain().isBlank() ? prompt.domain() : ""));
        promptProblemLabel.setText(prompt.problemStatement());
        promptDescriptionLabel.setText(prompt.description());
        if (prompt.sourcePatents() != null && !prompt.sourcePatents().isEmpty()) {
            promptSourcePatentsLabel.setText("Source patents: " + String.join(", ", prompt.sourcePatents()));
        } else {
            promptSourcePatentsLabel.setText("");
        }
        promptDetailBox.setManaged(true);
        promptDetailBox.setVisible(true);
    }

    private void displaySearchResults(String area, int count) {
        patentsFoundLabel.setText(String.valueOf(count));
        searchAreaLabel.setText(area);

         try {
             var dao = new com.patenttracker.dao.MinedPatentDao();
             List<MinedPatent> mined = dao.findBySearchArea(area);
             minedPatentsTable.setItems(FXCollections.observableArrayList(mined));
         } catch (SQLException ex) { }

         searchResultsBox.setManaged(true);
        searchResultsBox.setVisible(true);
    }

    @FXML
    public void handleMineAllPrompts() {
        if (!new ClaudeCliService().isAvailable()) {
            progressLabel.setText("Claude CLI not found. Configure it in Settings.");
            return;
        }

        setRunning(true);
        progressBar.setProgress(0);
        currentModeIsIP = true;

        AtomicInteger skippedCount = new AtomicInteger(0);

        var task = new Task<List<PatentMiningService.MiningResult>>() {
            @Override
            protected List<PatentMiningService.MiningResult> call() throws Exception {
                var self = this;
                return miningService.mineAllInventionPrompts(
                        new PatentMiningService.BulkMiningProgressCallback() {
                            @Override
                            public void onStatus(String status) {
                                updateMessage(status);
                            }
                            @Override
                            public boolean isCancelled() { return self.isCancelled(); }
                            @Override
                            public void onPromptProgress(int current, int total, String promptTitle) {
                                updateProgress(current - 1, total);
                                updateMessage("Mining " + current + "/" + total + ": " + promptTitle);
                            }
                            @Override
                            public void onPromptComplete(String promptTitle, boolean success, String error) {
                                if (success) {
                                    updateMessage("Completed: " + promptTitle);
                                } else {
                                    String reason = error != null ? " — " + error : "";
                                    updateMessage("Failed: " + promptTitle + reason);
                                }
                            }
                            @Override
                            public void onPromptSkipped(String promptTitle) {
                                skippedCount.incrementAndGet();
                                updateMessage("Skipped (already mined): " + promptTitle);
                            }
                        });
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(event -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();

            List<PatentMiningService.MiningResult> results = task.getValue();
            long successCount = results.stream().filter(PatentMiningService.MiningResult::success).count();
            double totalCost = results.stream().mapToDouble(PatentMiningService.MiningResult::costUsd).sum();
            int skipped = skippedCount.get();

            progressBar.setProgress(1.0);
            String costStr = totalCost > 0 ? String.format(" | Total cost: $%.4f", totalCost) : "";
            String skippedStr = skipped > 0 ? " | " + skipped + " already mined" : "";
            progressLabel.setText("Bulk mining complete: " + successCount + "/" + results.size()
                    + " succeeded" + skippedStr + costStr);
            loadMiningHistory();
            setRunning(false);
        });

        task.setOnFailed(event -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            Throwable ex = task.getException();
            progressLabel.setText("Bulk mining error: " + ex.getMessage());
            progressBar.setProgress(0);
            setRunning(false);
        });

        task.setOnCancelled(event -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            progressBar.setProgress(0);
            progressLabel.setText("Bulk mining cancelled.");
            setRunning(false);
        });

        currentTask = task;
        Thread.ofVirtual().start(task);
    }

    @FXML
    public void handleExportAll() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export All Mining Results");
        fileChooser.setInitialFileName("patent-mining-all-results.md");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown Files", "*.md"));

        Stage stage = (Stage) progressLabel.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            progressLabel.setText("Exporting all results...");

            var task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    String markdown = miningService.exportAllMiningMarkdown();
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(markdown);
                    }
                    return null;
                }
            };

            task.setOnSucceeded(event ->
                    progressLabel.setText("Exported all results to " + file.getName()));
            task.setOnFailed(event ->
                    progressLabel.setText("Export failed: " + task.getException().getMessage()));

            Thread.ofVirtual().start(task);
        }
    }

    private TitledPane createValidationPane(JsonNode validation) {
        VBox content = new VBox(8);
        content.setPadding(new Insets(10));

        addLabeledField(content, "Original Idea", textOf(validation, "original_idea"));

        String support = textOf(validation, "landscape_support");
        if (support != null) {
            Label supportLabel = new Label("Landscape Support: " + support);
            supportLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;"
                    + (support.equals("STRONG") ? " -fx-text-fill: #27ae60;"
                    : support.equals("WEAK") ? " -fx-text-fill: #e74c3c;"
                    : " -fx-text-fill: #f39c12;"));
            content.getChildren().add(supportLabel);
        }

        Label diffLabel = new Label(textOf(validation, "differentiation_assessment"));
        if (diffLabel.getText() != null) {
            diffLabel.setWrapText(true);
            content.getChildren().addAll(
                    new Label("Differentiation Assessment:"),
                    diffLabel
            );
        }

        addArrayField(content, validation, "closest_external", "Closest External Patents");

        JsonNode refinements = validation.get("recommended_refinements");
        if (refinements != null && refinements.isArray() && !refinements.isEmpty()) {
            Label refTitle = new Label("Recommended Refinements:");
            refTitle.setStyle("-fx-font-weight: bold;");
            content.getChildren().add(refTitle);
            for (JsonNode r : refinements) {
                Label item = new Label("  - " + r.asText());
                item.setWrapText(true);
                content.getChildren().add(item);
            }
        }

        JsonNode risks = validation.get("risk_factors");
        if (risks != null && risks.isArray() && !risks.isEmpty()) {
            Label riskTitle = new Label("Risk Factors:");
            riskTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #e74c3c;");
            content.getChildren().add(riskTitle);
            for (JsonNode r : risks) {
                Label item = new Label("  - " + r.asText());
                item.setWrapText(true);
                content.getChildren().add(item);
            }
        }

        return new TitledPane("Idea Validation", content);
    }

    private TitledPane createSummaryPane(JsonNode summary) {
        VBox content = new VBox(5);
        content.setPadding(new Insets(10));

        addLabeledField(content, "Search Area", textOf(summary, "search_area"));
        addLabeledField(content, "External Patents Analyzed",
                summary.has("external_patents_analyzed")
                        ? String.valueOf(summary.get("external_patents_analyzed").asInt()) : "0");
        addLabeledField(content, "Portfolio Overlap", textOf(summary, "portfolio_overlap"));
        addLabeledField(content, "Competitive Density", textOf(summary, "competitive_density"));

        JsonNode trends = summary.get("key_trends");
        if (trends != null && trends.isArray()) {
            Label trendsLabel = new Label("Key Trends:");
            trendsLabel.setStyle("-fx-font-weight: bold;");
            content.getChildren().add(trendsLabel);
            for (JsonNode t : trends) {
                content.getChildren().add(new Label("  - " + t.asText()));
            }
        }

        return new TitledPane("Landscape Summary", content);
    }

    private TitledPane createIdeaPane(JsonNode idea, int num) {
        VBox content = new VBox(8);
        content.setPadding(new Insets(10));

        Label problem = new Label(textOf(idea, "problem_statement"));
        problem.setWrapText(true);
        problem.setStyle("-fx-font-weight: bold;");

        Label desc = new Label(textOf(idea, "description"));
        desc.setWrapText(true);

        content.getChildren().addAll(
                new Label("Problem Statement:"),
                problem,
                new Separator(),
                new Label("Description:"),
                desc
        );

        String novelty = textOf(idea, "novelty_angle");
        if (novelty != null) {
            addLabeledField(content, "Novelty Angle", novelty);
        }

        addLabeledField(content, "Technical Domain", textOf(idea, "technical_domain"));
        addLabeledField(content, "Feasibility", textOf(idea, "feasibility"));
        addLabeledField(content, "Strategic Value", textOf(idea, "strategic_value"));
        addArrayField(content, idea, "inspired_by_external", "Inspired by External");
        addArrayField(content, idea, "builds_on_portfolio", "Builds on Portfolio");

        String title = textOf(idea, "title");
        return new TitledPane("Idea " + num + ": " + (title != null ? title : "Untitled"), content);
    }

    private TitledPane createDefensivePane(JsonNode defensive) {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        int num = 1;
        for (JsonNode def : defensive) {
            VBox item = new VBox(5);
            Label title = new Label(num++ + ". " + textOf(def, "title"));
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
            item.getChildren().add(title);

            Label problem = new Label(textOf(def, "problem_statement"));
            problem.setWrapText(true);
            item.getChildren().add(problem);

            Label desc = new Label(textOf(def, "description"));
            desc.setWrapText(true);
            item.getChildren().add(desc);

            addLabeledField(item, "Urgency", textOf(def, "urgency"));
            addArrayField(item, def, "threat_patents", "Threat Patents");

            content.getChildren().add(item);
            content.getChildren().add(new Separator());
        }

        return new TitledPane("Defensive Opportunities (" + defensive.size() + ")", content);
    }

    private TitledPane createBlindSpotsPane(JsonNode blindSpots) {
        VBox content = new VBox(5);
        content.setPadding(new Insets(10));

        for (JsonNode spot : blindSpots) {
            content.getChildren().add(new Label("- " + spot.asText()));
        }

        return new TitledPane("Portfolio Blind Spots (" + blindSpots.size() + ")", content);
    }

    private void addLabeledField(VBox container, String label, String value) {
        if (value == null || value.isBlank()) return;
        Label l = new Label(label + ": " + value);
        l.setWrapText(true);
        container.getChildren().add(l);
    }

    private void addArrayField(VBox container, JsonNode node, String field, String label) {
        JsonNode arr = node.get(field);
        if (arr == null || !arr.isArray() || arr.isEmpty()) return;
        List<String> items = new ArrayList<>();
        for (JsonNode item : arr) items.add(item.asText());
        Label l = new Label(label + ": " + String.join(", ", items));
        l.setWrapText(true);
        container.getChildren().add(l);
    }

    private String textOf(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        return val.asText();
    }

    private void loadMiningHistory() {
        historyAccordion.getPanes().clear();

        try {
            List<PatentMiningService.MiningHistoryItem> history = miningService.getAllMiningResults();

            if (history.isEmpty()) {
                historyEmptyLabel.setManaged(true);
                historyEmptyLabel.setVisible(true);
                exportAllButton.setDisable(true);
                exportButton.setDisable(true);
                return;
            }

            historyEmptyLabel.setManaged(false);
            historyEmptyLabel.setVisible(false);
            exportAllButton.setDisable(false);
            exportButton.setDisable(history.isEmpty());

            if (!history.isEmpty()) {
                var first = history.getFirst();
                currentMiningArea = first.area();
                currentModeIsIP = first.isIPMining();
            }

            ObjectMapper om = new ObjectMapper();
            java.time.format.DateTimeFormatter dtFmt =
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            for (PatentMiningService.MiningHistoryItem item : history) {
                String typeLabel = item.isIPMining() ? "IP" : "General";
                String timestamp = item.analyzedAt() != null
                        ? item.analyzedAt().format(dtFmt) : "unknown";
                String title = "[" + typeLabel + "] " + item.area() + "  (" + timestamp + ")";

                VBox entryContent = new VBox(5);
                entryContent.setPadding(new Insets(5));

                HBox toolbar = new HBox(10);
                toolbar.setAlignment(Pos.CENTER_LEFT);
                Label tsLabel = new Label("Analyzed: " + timestamp);
                tsLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11px;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Button entryExport = new Button("Export");
                entryExport.getStyleClass().add("secondary-button");
                final String areaName = item.area();
                final boolean isIP = item.isIPMining();
                entryExport.setOnAction(e -> exportSingleResult(areaName, isIP));
                toolbar.getChildren().addAll(tsLabel, spacer, entryExport);
                entryContent.getChildren().add(toolbar);

                try {
                    JsonNode root = om.readTree(item.resultJson());
                    Accordion nested = new Accordion();

                    if (item.isIPMining()) {
                        JsonNode validation = root.get("idea_validation");
                        if (validation != null) nested.getPanes().add(createValidationPane(validation));
                    }

                    JsonNode summary = root.get("landscape_summary");
                    if (summary != null) nested.getPanes().add(createSummaryPane(summary));

                    JsonNode ideas = root.get("patent_ideas");
                    if (ideas != null && ideas.isArray()) {
                        int num = 1;
                        for (JsonNode idea : ideas) {
                            nested.getPanes().add(createIdeaPane(idea, num++));
                        }
                    }

                    JsonNode defensive = root.get("defensive_opportunities");
                    if (defensive != null && defensive.isArray() && !defensive.isEmpty()) {
                        nested.getPanes().add(createDefensivePane(defensive));
                    }

                    JsonNode blindSpots = root.get("portfolio_blind_spots");
                    if (blindSpots != null && blindSpots.isArray() && !blindSpots.isEmpty()) {
                        nested.getPanes().add(createBlindSpotsPane(blindSpots));
                    }

                    entryContent.getChildren().add(nested);
                } catch (Exception e) {
                    TextArea raw = new TextArea(item.resultJson());
                    raw.setEditable(false);
                    raw.setWrapText(true);
                    raw.setPrefRowCount(10);
                    entryContent.getChildren().add(raw);
                }

                TitledPane historyEntry = new TitledPane(title, entryContent);
                historyEntry.setExpanded(false);
                historyAccordion.getPanes().add(historyEntry);
            }

            if (!historyAccordion.getPanes().isEmpty()) {
                historyAccordion.setExpandedPane(historyAccordion.getPanes().getFirst());
            }

        } catch (SQLException e) {
            historyEmptyLabel.setText("Error loading history: " + e.getMessage());
            historyEmptyLabel.setManaged(true);
            historyEmptyLabel.setVisible(true);
        }
    }

    private void exportSingleResult(String area, boolean isIP) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Mining Result");
        String prefix = isIP ? "invention-mining-" : "patent-mining-";
        String filename = prefix + area.toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".md";
        fileChooser.setInitialFileName(filename);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown Files", "*.md"));

        Stage stage = (Stage) progressLabel.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            progressLabel.setText("Exporting...");

            var task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    String markdown = isIP
                            ? miningService.exportIPMiningMarkdown(area)
                            : miningService.exportMiningMarkdown(area);
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(markdown);
                    }
                    return null;
                }
            };

            task.setOnSucceeded(event ->
                    progressLabel.setText("Exported to " + file.getName()));
            task.setOnFailed(event ->
                    progressLabel.setText("Export failed: " + task.getException().getMessage()));

            Thread.ofVirtual().start(task);
        }
    }

    private void setRunning(boolean running) {
        mineButton.setDisable(running);
        mineAllButton.setDisable(running);
        refreshAreasButton.setDisable(running);
        refreshPromptsButton.setDisable(running);
        exportButton.setDisable(running);
        exportAllButton.setDisable(running);
        areaComboBox.setDisable(running);
        promptListView.setDisable(running);
        generalRadio.setDisable(running);
        promptRadio.setDisable(running);
        cancelButton.setManaged(running);
        cancelButton.setVisible(running);
    }

    private String formatCost(PatentMiningService.MiningResult result) {
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
