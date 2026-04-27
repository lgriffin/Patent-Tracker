package com.patenttracker.controller;

import com.patenttracker.service.AutoTagService;
import com.patenttracker.service.CsvImportService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;

public class MainController {

    @FXML private TabPane tabPane;
    @FXML private Tab patentsTab;
    @FXML private Tab graphTab;
    @FXML private Tab dashboardTab;
    @FXML private Tab insightsTab;
    @FXML private Label statusLabel;
    @FXML private MenuItem importCsvMenuItem;
    @FXML private MenuItem syncAllMenuItem;

    @FXML private PatentListController patentListController;
    @FXML private GraphController graphController;
    @FXML private DashboardController dashboardController;
    @FXML private InsightsController insightsController;

    @FXML
    public void initialize() {
        // Refresh data when switching tabs
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == graphTab && graphController != null) {
                graphController.refresh(patentListController.getCurrentFilteredPatents());
            } else if (newTab == dashboardTab && dashboardController != null) {
                dashboardController.refresh();
            } else if (newTab == insightsTab && insightsController != null) {
                insightsController.refresh();
            }
        });
    }

    @FXML
    private void handleImportCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Patent CSV");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );

        Stage stage = (Stage) tabPane.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            statusLabel.setText("Importing " + file.getName() + "...");

            new Thread(() -> {
                try {
                    CsvImportService importService = new CsvImportService();
                    CsvImportService.ImportResult result = importService.importCsv(file.getAbsolutePath());

                    Platform.runLater(() -> {
                        StringBuilder msg = new StringBuilder();
                        msg.append(result.imported()).append(" new");
                        if (result.updated() > 0) {
                            msg.append(", ").append(result.updated()).append(" updated");
                        }
                        if (result.unchanged() > 0) {
                            msg.append(", ").append(result.unchanged()).append(" unchanged");
                        }
                        String statusMsg = "Import complete: " + msg;
                        statusLabel.setText(statusMsg);

                        // Show summary dialog
                        Alert summary = new Alert(result.hasErrors() ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION);
                        summary.setTitle("CSV Import Results");
                        summary.setHeaderText(statusMsg);
                        StringBuilder details = new StringBuilder();
                        details.append("New patents added: ").append(result.imported()).append("\n");
                        details.append("Existing patents updated: ").append(result.updated()).append("\n");
                        details.append("Unchanged (already up to date): ").append(result.unchanged()).append("\n");
                        if (result.hasErrors()) {
                            details.append("\nErrors (").append(result.errors().size()).append("):\n");
                            result.errors().stream().limit(10).forEach(e -> details.append("  ").append(e).append("\n"));
                            if (result.errors().size() > 10) {
                                details.append("  ... and ").append(result.errors().size() - 10).append(" more");
                            }
                        }
                        summary.setContentText(details.toString());
                        summary.showAndWait();

                        patentListController.refreshData();
                        patentListController.loadTagFilter();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Import failed");
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Import Error");
                        alert.setHeaderText("Failed to import CSV");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    });
                }
            }).start();
        }
    }

    @FXML
    private void handleAutoTag() {
        statusLabel.setText("Running AI auto-tagger...");

        new Thread(() -> {
            try {
                AutoTagService autoTagService = new AutoTagService();
                AutoTagService.AutoTagResult result = autoTagService.autoTagAll();

                Platform.runLater(() -> {
                    statusLabel.setText("Auto-tagged " + result.patentsTagged() + " of "
                            + result.totalPatents() + " patents (" + result.tagsApplied() + " tags applied)");
                    patentListController.refreshData();
                    patentListController.loadTagFilter();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Auto-tag failed");
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Auto-Tag Error");
                    alert.setHeaderText("Failed to auto-tag patents");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }

    @FXML
    private void handleSyncAll() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/sync.fxml"));
            Parent root = loader.load();

            SyncController controller = loader.getController();
            controller.setParentController(patentListController);

            Stage stage = new Stage();
            stage.setTitle("USPTO Sync");
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 800, 500);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            stage.setScene(scene);
            stage.show();

            controller.startBulkSync();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Sync Error");
            alert.setHeaderText("Failed to open sync view");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleDownloadAllPdfs() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pdf-download.fxml"));
            Parent root = loader.load();

            PdfDownloadController controller = loader.getController();
            controller.setParentController(patentListController);

            Stage stage = new Stage();
            stage.setTitle("Download PDFs");
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 900, 500);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            stage.setScene(scene);
            stage.show();

            controller.startBulkDownload();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("PDF Download Error");
            alert.setHeaderText("Failed to open PDF download view");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleExtractAllText() {
        tabPane.getSelectionModel().select(insightsTab);
        if (insightsController != null) {
            insightsController.handleExtractAll();
        }
    }

    @FXML
    private void handleBatchAnalysis() {
        tabPane.getSelectionModel().select(insightsTab);
        if (insightsController != null) {
            insightsController.handleAnalyzeAll();
        }
    }

    @FXML
    private void handleManageClassifications() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/classifications.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Manage Classifications");
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 550, 500);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();

            // Refresh filters after classifications may have changed
            patentListController.refreshData();
            patentListController.loadTagFilter();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to open classifications manager");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
            Parent root = loader.load();

            SettingsController controller = loader.getController();

            // Live refresh: when owner is applied, update dashboard immediately
            controller.setOnOwnerChanged(() -> {
                if (dashboardController != null) {
                    dashboardController.refresh();
                }
            });

            Stage stage = new Stage();
            stage.setTitle("Settings");
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 520, 640);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();

            // Also refresh on close if anything was saved
            if (controller.wasSaved() && dashboardController != null) {
                dashboardController.refresh();
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to open settings");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }
}
