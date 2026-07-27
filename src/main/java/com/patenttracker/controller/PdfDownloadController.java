package com.patenttracker.controller;

import com.patenttracker.service.PdfDownloadService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class PdfDownloadController {

    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Button cancelButton;
    @FXML private TableView<PdfRow> resultsTable;
    @FXML private TableColumn<PdfRow, String> colFileNumber;
    @FXML private TableColumn<PdfRow, String> colTitle;
    @FXML private TableColumn<PdfRow, String> colSource;
    @FXML private TableColumn<PdfRow, String> colResult;
    @FXML private Label summaryLabel;

    // Summary panel
    @FXML private VBox summaryPanel;
    @FXML private Label summaryTotalLabel;
    @FXML private Label summaryDownloadedLabel;
    @FXML private Label summarySkippedLabel;
    @FXML private Label summaryFailedLabel;

    private PatentListController parentController;
    private Task<?> currentTask;

    @FXML
    public void initialize() {
        colFileNumber.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().fileNumber()));
        colTitle.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().title()));
        colSource.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().source()));
        colResult.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().result()));

        // Color-code source column
        colSource.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (item == null || empty) {
                    setStyle("");
                } else if ("GRANTED".equals(item)) {
                    setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                } else if ("PUBLICATION".equals(item)) {
                    setStyle("-fx-text-fill: #0078d4;");
                } else {
                    setStyle("");
                }
            }
        });

        // Color-code result column
        colResult.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (item != null && item.startsWith("Downloaded")) {
                    setStyle("-fx-text-fill: green;");
                } else if (item != null && !item.isEmpty() && !item.startsWith("Downloaded")) {
                    setStyle("-fx-text-fill: red;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    public void setParentController(PatentListController controller) {
        this.parentController = controller;
    }

    public void startBulkDownload() {
        cancelButton.setManaged(true);
        cancelButton.setVisible(true);
        progressBar.setProgress(0);
        resultsTable.getItems().clear();

        PdfDownloadService pdfService = new PdfDownloadService();
        int[] counts = pdfService.getCounts();
        progressLabel.setText("Starting download... (" + counts[3] + " eligible, "
                + counts[2] + " already cached)");

        int alreadyCached = counts[2];

        var task = new Task<List<PdfDownloadService.DownloadResult>>() {
            @Override
            protected List<PdfDownloadService.DownloadResult> call() {
                var self = this;
                return pdfService.downloadAll(
                        new PdfDownloadService.DownloadProgressCallback() {
                    @Override
                    public void onProgress(int current, int total, String title) {
                        updateProgress(current, total);
                        updateMessage("Downloading " + current + " of " + total
                                + ": " + truncate(title, 40));
                    }

                    @Override
                    public void onResult(PdfDownloadService.DownloadResult result) {
                        Platform.runLater(() -> {
                            String source = result.sourceType() != null ? result.sourceType() : "";
                            String resultText = result.success()
                                    ? "Downloaded" : result.error();
                            resultsTable.getItems().add(new PdfRow(
                                    result.fileNumber(), result.title(), source, resultText));
                            resultsTable.scrollTo(resultsTable.getItems().size() - 1);
                        });
                    }

                    @Override
                    public boolean isCancelled() {
                        return self.isCancelled();
                    }
                });
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(event -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();

            List<PdfDownloadService.DownloadResult> results = task.getValue();

            cancelButton.setManaged(false);
            cancelButton.setVisible(false);
            progressBar.setProgress(1.0);
            progressLabel.setText("Download complete");

            long downloaded = results.stream().filter(PdfDownloadService.DownloadResult::success).count();
            long failed = results.stream().filter(r -> !r.success()).count();

            summaryPanel.setManaged(true);
            summaryPanel.setVisible(true);
            summaryTotalLabel.setText(String.valueOf(results.size()));
            summaryDownloadedLabel.setText(String.valueOf(downloaded));
            summarySkippedLabel.setText(String.valueOf(alreadyCached));
            summaryFailedLabel.setText(String.valueOf(failed));

            summaryLabel.setText(String.format("%d downloaded, %d already cached, %d failed",
                    downloaded, alreadyCached, failed));

            if (parentController != null) {
                parentController.refreshData();
            }
        });

        task.setOnFailed(event -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            progressBar.setProgress(0);
            cancelButton.setManaged(false);
            cancelButton.setVisible(false);
            Throwable ex = task.getException();
            progressLabel.setText("Download failed: " + ex.getMessage());
        });

        task.setOnCancelled(event -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            progressBar.setProgress(0);
            cancelButton.setManaged(false);
            cancelButton.setVisible(false);
            progressLabel.setText("Download cancelled.");
        });

        currentTask = task;
        Thread.ofVirtual().start(task);
    }

    @FXML
    private void handleCancel() {
        if (currentTask != null) {
            currentTask.cancel();
        }
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

    record PdfRow(String fileNumber, String title, String source, String result) {}
}
