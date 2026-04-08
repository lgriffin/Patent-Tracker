package com.patenttracker.controller;

import com.patenttracker.service.UsptoSyncService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncController {

    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Button cancelButton;
    @FXML private TableView<SyncRow> resultsTable;
    @FXML private TableColumn<SyncRow, String> colApp;
    @FXML private TableColumn<SyncRow, String> colTitle;
    @FXML private TableColumn<SyncRow, String> colStatus;
    @FXML private TableColumn<SyncRow, String> colResult;
    @FXML private Label summaryLabel;

    // Summary panel
    @FXML private VBox summaryPanel;
    @FXML private Label summaryTotalLabel;
    @FXML private Label summaryUpdatedLabel;
    @FXML private Label summaryUnchangedLabel;
    @FXML private Label summaryErrorsLabel;
    @FXML private Label summaryProgressedLabel;
    @FXML private Label summaryRegressedLabel;
    @FXML private Label summaryTerminalLabel;
    @FXML private Label summaryLateralLabel;
    @FXML private Label summaryFieldsLabel;

    private PatentListController parentController;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @FXML
    public void initialize() {
        colApp.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().appNumber()));
        colTitle.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().title()));
        colStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().statusChange()));
        colResult.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().result()));

        // Color-code status movement
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.contains("Progressed")) {
                    setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                } else if (item.contains("Regressed")) {
                    setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                } else if (item.contains("Terminal")) {
                    setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                } else if (item.contains("Lateral")) {
                    setStyle("-fx-text-fill: #fd7e14;");
                } else {
                    setStyle("-fx-text-fill: #999;");
                }
            }
        });

        // Color-code result details
        colResult.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (item != null && item.startsWith("Error")) {
                    setStyle("-fx-text-fill: red;");
                } else if (item != null && item.contains(":")) {
                    setStyle("-fx-text-fill: green;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    public void setParentController(PatentListController controller) {
        this.parentController = controller;
    }

    public void startBulkSync() {
        cancelled.set(false);
        cancelButton.setManaged(true);
        cancelButton.setVisible(true);
        progressBar.setProgress(0);
        progressLabel.setText("Starting sync...");
        resultsTable.getItems().clear();

        new Thread(() -> {
            UsptoSyncService syncService = new UsptoSyncService();
            List<UsptoSyncService.SyncResult> results = syncService.syncAll(new UsptoSyncService.SyncProgressCallback() {
                @Override
                public void onProgress(int current, int total, String patentTitle) {
                    Platform.runLater(() -> {
                        progressBar.setProgress((double) current / total);
                        progressLabel.setText("Syncing " + current + " of " + total + ": " + truncate(patentTitle, 40));
                    });
                }

                @Override
                public void onResult(UsptoSyncService.SyncResult result) {
                    Platform.runLater(() -> {
                        String statusChange = formatStatusChange(result);
                        String resultText = formatResultText(result);
                        resultsTable.getItems().add(new SyncRow(
                                result.appNumber(), result.title(), statusChange, resultText));
                        // Auto-scroll to the latest row
                        resultsTable.scrollTo(resultsTable.getItems().size() - 1);
                    });
                }

                @Override
                public boolean isCancelled() {
                    return cancelled.get();
                }
            });

            Platform.runLater(() -> {
                cancelButton.setManaged(false);
                cancelButton.setVisible(false);
                progressBar.setProgress(1.0);

                long updatedCount = results.stream().filter(UsptoSyncService.SyncResult::hasChanges).count();
                long errorCount = results.stream().filter(r -> r.error() != null).count();
                long noChange = results.size() - updatedCount - errorCount;
                long progressed = results.stream()
                        .filter(r -> r.movement() == UsptoSyncService.StatusMovement.PROGRESSED).count();
                long regressed = results.stream()
                        .filter(r -> r.movement() == UsptoSyncService.StatusMovement.REGRESSED).count();
                long terminal = results.stream()
                        .filter(r -> r.movement() == UsptoSyncService.StatusMovement.TERMINAL).count();
                long lateral = results.stream()
                        .filter(r -> r.movement() == UsptoSyncService.StatusMovement.LATERAL).count();

                progressLabel.setText("Sync complete");

                // Populate summary panel
                summaryPanel.setManaged(true);
                summaryPanel.setVisible(true);
                summaryTotalLabel.setText(String.valueOf(results.size()));
                summaryUpdatedLabel.setText(String.valueOf(updatedCount));
                summaryUnchangedLabel.setText(String.valueOf(noChange));
                summaryErrorsLabel.setText(String.valueOf(errorCount));
                summaryProgressedLabel.setText(String.valueOf(progressed));
                summaryRegressedLabel.setText(String.valueOf(regressed));
                summaryTerminalLabel.setText(String.valueOf(terminal));
                summaryLateralLabel.setText(String.valueOf(lateral));

                // Count changed fields across all results
                Map<String, Integer> fieldChanges = new LinkedHashMap<>();
                for (var result : results) {
                    if (result.summary() != null) {
                        for (String change : result.summary().split("\n")) {
                            String fieldName = change.split(":")[0].trim();
                            fieldChanges.merge(fieldName, 1, Integer::sum);
                        }
                    }
                }
                if (fieldChanges.isEmpty()) {
                    summaryFieldsLabel.setText("No fields changed");
                } else {
                    StringBuilder fields = new StringBuilder();
                    fieldChanges.forEach((field, count) ->
                            fields.append(field).append(": ").append(count).append("  "));
                    summaryFieldsLabel.setText(fields.toString().trim());
                }

                summaryLabel.setText(String.format("%d updated, %d unchanged, %d errors",
                        updatedCount, noChange, errorCount));

                if (parentController != null) {
                    parentController.refreshData();
                }
            });
        }).start();
    }

    private String formatStatusChange(UsptoSyncService.SyncResult result) {
        if (result.error() != null && result.newStatus() == null) return "";
        if (result.newStatus() == null) return "No change";
        String arrow = switch (result.movement()) {
            case PROGRESSED -> " >> ";
            case REGRESSED -> " << ";
            case TERMINAL -> " XX ";
            default -> " -> ";
        };
        String old = result.oldStatus() != null ? result.oldStatus() : "(none)";
        return old + arrow + result.newStatus()
                + " [" + UsptoSyncService.movementLabel(result.movement()) + "]";
    }

    private String formatResultText(UsptoSyncService.SyncResult result) {
        if (result.error() != null) return "Error: " + result.error();
        if (result.summary() != null) return result.summary().replace("\n", "; ");
        return "No changes";
    }

    @FXML
    private void handleCancel() {
        cancelled.set(true);
        progressLabel.setText("Cancelling...");
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) progressBar.getScene().getWindow();
        stage.close();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    record SyncRow(String appNumber, String title, String statusChange, String result) {}
}
