package com.patenttracker.controller;

import com.patenttracker.dao.*;
import com.patenttracker.model.*;
import com.patenttracker.service.PdfDownloadService;
import com.patenttracker.service.UsptoSyncService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PatentDetailController {

    private static final DateTimeFormatter DISPLAY_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private TextField titleField;
    @FXML private Label fileNumberLabel;
    @FXML private ComboBox<String> statusCombo;
    @FXML private DatePicker filingDatePicker;
    @FXML private TextField appNumberField;
    @FXML private TextField patentNumberField;
    @FXML private DatePicker issueDatePicker;
    @FXML private TextField pubNumberField;
    @FXML private DatePicker pubDatePicker;
    @FXML private TextField suffixField;
    @FXML private ComboBox<String> classificationCombo;
    @FXML private Button saveButton;
    @FXML private Label saveStatusLabel;
    @FXML private Button pdfButton;
    @FXML private Label pdfStatusLabel;
    @FXML private VBox inventorsBox;
    @FXML private FlowPane tagsPane;
    @FXML private TextField newTagField;
    @FXML private Label relatedLabel;
    @FXML private VBox relatedBox;
    @FXML private Separator relatedSeparator;
    @FXML private TableView<StatusUpdate> historyTable;
    @FXML private TableColumn<StatusUpdate, String> histColTimestamp;
    @FXML private TableColumn<StatusUpdate, String> histColField;
    @FXML private TableColumn<StatusUpdate, String> histColOld;
    @FXML private TableColumn<StatusUpdate, String> histColNew;
    @FXML private TableColumn<StatusUpdate, String> histColSource;

    private Patent patent;
    private PatentListController parentController;
    private final PatentDao patentDao = new PatentDao();
    private final StatusUpdateDao statusUpdateDao = new StatusUpdateDao();
    private final PdfDownloadService pdfDownloadService = new PdfDownloadService();

    @FXML
    public void initialize() {
        histColTimestamp.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getTimestamp() != null ? cd.getValue().getTimestamp().format(DISPLAY_DATETIME) : ""));
        histColField.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFieldName()));
        histColOld.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPreviousValue()));
        histColNew.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getNewValue()));
        histColSource.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getSource()));

        // Populate status combo with known statuses
        try {
            List<String> statuses = patentDao.getDistinctStatuses();
            statusCombo.setItems(FXCollections.observableArrayList(statuses));
        } catch (SQLException e) {
            statusCombo.setItems(FXCollections.observableArrayList(
                "Filed", "Published", "Allowed", "Issued", "Abandoned", "Dropped"));
        }

        // Populate classification combo from tags (aggregate of unique tags)
        try {
            TagDao tagDao = new TagDao();
            List<Tag> allTags = tagDao.findAllWithCounts();
            List<String> tagNames = allTags.stream().map(Tag::getName).collect(java.util.stream.Collectors.toList());
            // Also include existing classification values
            List<String> existingClassifications = patentDao.getDistinctClassifications();
            for (String c : existingClassifications) {
                if (!tagNames.contains(c)) tagNames.add(c);
            }
            java.util.Collections.sort(tagNames, String.CASE_INSENSITIVE_ORDER);
            classificationCombo.setItems(FXCollections.observableArrayList(tagNames));
        } catch (SQLException e) {
            // Leave empty — user can still type
        }
    }

    public void setPatent(Patent patent) {
        this.patent = patent;
        displayPatent();
    }

    public void setParentController(PatentListController controller) {
        this.parentController = controller;
    }

    private void displayPatent() {
        if (patent == null) return;

        // Reload from DB to get latest data
        try {
            patent = patentDao.findById(patent.getId());
        } catch (SQLException e) {
            // Use existing data
        }

        titleField.setText(patent.getTitle());
        fileNumberLabel.setText(patent.getFileNumber());
        statusCombo.setValue(patent.getPtoStatus());
        filingDatePicker.setValue(patent.getFilingDate());
        appNumberField.setText(orEmpty(patent.getApplicationNumber()));
        patentNumberField.setText(orEmpty(patent.getPatentNumber()));
        issueDatePicker.setValue(patent.getIssueGrantDate());
        pubNumberField.setText(orEmpty(patent.getPublicationNumber()));
        pubDatePicker.setValue(patent.getPublicationDate());
        suffixField.setText(patent.getSuffix());
        classificationCombo.setValue(patent.getClassification());

        saveStatusLabel.setText("");

        // Update PDF button state
        updatePdfStatus();

        loadInventors();
        loadTags();
        loadRelatedFilings();
        loadHistory();
    }

    @FXML
    private void handleSave() {
        if (patent == null) return;

        List<String> changes = new ArrayList<>();

        // Track and apply each field change
        String newTitle = titleField.getText();
        if (!Objects.equals(newTitle, patent.getTitle())) {
            recordChange("title", patent.getTitle(), newTitle);
            patent.setTitle(newTitle);
            changes.add("title");
        }

        String newStatus = statusCombo.getValue();
        if (!Objects.equals(newStatus, patent.getPtoStatus())) {
            recordChange("ptoStatus", patent.getPtoStatus(), newStatus);
            patent.setPtoStatus(newStatus);
            changes.add("status");
        }

        LocalDate newFilingDate = filingDatePicker.getValue();
        if (!Objects.equals(newFilingDate, patent.getFilingDate())) {
            recordChange("filingDate",
                patent.getFilingDate() != null ? patent.getFilingDate().toString() : null,
                newFilingDate != null ? newFilingDate.toString() : null);
            patent.setFilingDate(newFilingDate);
            changes.add("filingDate");
        }

        String newAppNumber = emptyToNull(appNumberField.getText());
        if (!Objects.equals(newAppNumber, patent.getApplicationNumber())) {
            recordChange("applicationNumber", patent.getApplicationNumber(), newAppNumber);
            patent.setApplicationNumber(newAppNumber);
            changes.add("applicationNumber");
        }

        String newPatentNumber = emptyToNull(patentNumberField.getText());
        if (!Objects.equals(newPatentNumber, patent.getPatentNumber())) {
            recordChange("patentNumber", patent.getPatentNumber(), newPatentNumber);
            patent.setPatentNumber(newPatentNumber);
            changes.add("patentNumber");
        }

        LocalDate newIssueDate = issueDatePicker.getValue();
        if (!Objects.equals(newIssueDate, patent.getIssueGrantDate())) {
            recordChange("issueGrantDate",
                patent.getIssueGrantDate() != null ? patent.getIssueGrantDate().toString() : null,
                newIssueDate != null ? newIssueDate.toString() : null);
            patent.setIssueGrantDate(newIssueDate);
            changes.add("issueGrantDate");
        }

        String newPubNumber = emptyToNull(pubNumberField.getText());
        if (!Objects.equals(newPubNumber, patent.getPublicationNumber())) {
            recordChange("publicationNumber", patent.getPublicationNumber(), newPubNumber);
            patent.setPublicationNumber(newPubNumber);
            changes.add("publicationNumber");
        }

        LocalDate newPubDate = pubDatePicker.getValue();
        if (!Objects.equals(newPubDate, patent.getPublicationDate())) {
            recordChange("publicationDate",
                patent.getPublicationDate() != null ? patent.getPublicationDate().toString() : null,
                newPubDate != null ? newPubDate.toString() : null);
            patent.setPublicationDate(newPubDate);
            changes.add("publicationDate");
        }

        String newSuffix = suffixField.getText();
        if (!Objects.equals(newSuffix, patent.getSuffix())) {
            recordChange("suffix", patent.getSuffix(), newSuffix);
            patent.setSuffix(newSuffix);
            changes.add("suffix");
        }

        String newClassification = emptyToNull(classificationCombo.getValue());
        if (!Objects.equals(newClassification, patent.getClassification())) {
            recordChange("classification", patent.getClassification(), newClassification);
            patent.setClassification(newClassification);
            changes.add("classification");
        }

        if (changes.isEmpty()) {
            saveStatusLabel.setStyle("-fx-text-fill: gray;");
            saveStatusLabel.setText("No changes to save.");
            return;
        }

        try {
            patentDao.update(patent);
            saveStatusLabel.setStyle("-fx-text-fill: green;");
            saveStatusLabel.setText("Saved " + changes.size() + " change(s).");
            loadHistory();
            if (parentController != null) parentController.refreshData();
        } catch (SQLException e) {
            saveStatusLabel.setStyle("-fx-text-fill: red;");
            saveStatusLabel.setText("Save failed: " + e.getMessage());
        }
    }

    private void recordChange(String field, String oldVal, String newVal) {
        try {
            statusUpdateDao.insert(new StatusUpdate(patent.getId(), field, oldVal, newVal, "MANUAL_EDIT"));
        } catch (SQLException e) {
            // Non-critical
        }
    }

    private void loadInventors() {
        inventorsBox.getChildren().clear();
        try {
            List<Inventor> inventors = new InventorDao().findByPatentId(patent.getId());
            for (Inventor inv : inventors) {
                Label label = new Label(inv.getDisplayName());
                label.setStyle("-fx-padding: 2 0 2 10;");
                inventorsBox.getChildren().add(label);
            }
        } catch (SQLException e) {
            inventorsBox.getChildren().add(new Label("Failed to load inventors"));
        }
    }

    private void loadTags() {
        tagsPane.getChildren().clear();
        try {
            TagDao tagDao = new TagDao();
            List<Tag> tags = tagDao.findByPatentId(patent.getId());
            for (Tag tag : tags) {
                HBox chip = new HBox(4);
                chip.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                if (tag.isAiGenerated()) {
                    chip.getStyleClass().addAll("tag-chip", "tag-chip-ai");
                } else {
                    chip.getStyleClass().add("tag-chip");
                }
                Label nameLabel = new Label(tag.getName());
                if (tag.isAiGenerated()) {
                    Label aiLabel = new Label("AI");
                    aiLabel.getStyleClass().add("tag-ai-badge");
                    aiLabel.setStyle("-fx-font-size: 9px; -fx-padding: 0 3; -fx-background-color: #7c3aed; "
                            + "-fx-text-fill: white; -fx-background-radius: 3;");
                    chip.getChildren().add(aiLabel);
                }
                Button removeBtn = new Button("x");
                removeBtn.setStyle("-fx-background-color: transparent; -fx-padding: 0 0 0 4; -fx-cursor: hand; -fx-font-size: 10;");
                removeBtn.setOnAction(e -> {
                    try {
                        tagDao.removeFromPatent(patent.getId(), tag.getId());
                        loadTags();
                    } catch (SQLException ex) {
                        // Ignore
                    }
                });
                chip.getChildren().addAll(nameLabel, removeBtn);
                tagsPane.getChildren().add(chip);
            }
        } catch (SQLException e) {
            // Tags not available
        }
    }

    private void loadRelatedFilings() {
        relatedBox.getChildren().clear();
        try {
            List<Patent> children = patentDao.findByParentFileNumber(patent.getFileNumber());

            boolean hasRelated = false;
            if (patent.getParentFileNumber() != null) {
                Patent parent = patentDao.findByFileNumber(patent.getParentFileNumber());
                if (parent != null) {
                    Hyperlink link = new Hyperlink("Parent: " + parent.getFileNumber() + " \u2014 " + parent.getTitle());
                    link.setOnAction(e -> openRelatedPatent(parent));
                    relatedBox.getChildren().add(link);
                    hasRelated = true;
                }
            }

            for (Patent child : children) {
                Hyperlink link = new Hyperlink(child.getSuffix() + ": " + child.getFileNumber() + " \u2014 " + child.getTitle());
                link.setOnAction(e -> openRelatedPatent(child));
                relatedBox.getChildren().add(link);
                hasRelated = true;
            }

            relatedLabel.setManaged(hasRelated);
            relatedLabel.setVisible(hasRelated);
            relatedBox.setManaged(hasRelated);
            relatedBox.setVisible(hasRelated);
            relatedSeparator.setManaged(hasRelated);
            relatedSeparator.setVisible(hasRelated);
        } catch (SQLException e) {
            // Ignore
        }
    }

    private void loadHistory() {
        try {
            List<StatusUpdate> history = statusUpdateDao.findByPatentId(patent.getId());
            historyTable.getItems().setAll(history);
        } catch (SQLException e) {
            // Ignore
        }
    }

    @FXML
    private void handleAddTag() {
        String tagName = newTagField.getText();
        if (tagName == null || tagName.isBlank()) return;

        try {
            TagDao tagDao = new TagDao();
            Tag tag = tagDao.findOrCreate(tagName.trim());
            tagDao.addToPatent(patent.getId(), tag.getId());
            newTagField.clear();
            loadTags();
        } catch (SQLException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to add tag");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleSyncPatent() {
        if (patent.getApplicationNumber() == null || patent.getApplicationNumber().isBlank()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Cannot Sync");
            alert.setHeaderText("No application number");
            alert.setContentText("This patent does not have an application number for USPTO lookup.");
            alert.showAndWait();
            return;
        }

        saveStatusLabel.setStyle("-fx-text-fill: blue;");
        saveStatusLabel.setText("Syncing with USPTO...");

        new Thread(() -> {
            try {
                UsptoSyncService syncService = new UsptoSyncService();
                UsptoSyncService.SyncResult result = syncService.syncPatent(patent);

                Platform.runLater(() -> {
                    if (result.hasChanges()) {
                        saveStatusLabel.setStyle("-fx-text-fill: green;");
                        saveStatusLabel.setText("USPTO sync: " + result.summary().replace("\n", ", "));
                        displayPatent();
                        if (parentController != null) parentController.refreshData();
                    } else if (result.error() != null) {
                        saveStatusLabel.setStyle("-fx-text-fill: red;");
                        saveStatusLabel.setText("Sync error: " + result.error());
                    } else {
                        saveStatusLabel.setStyle("-fx-text-fill: gray;");
                        saveStatusLabel.setText("Already up to date with USPTO.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    saveStatusLabel.setStyle("-fx-text-fill: red;");
                    saveStatusLabel.setText("Sync failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void updatePdfStatus() {
        if (pdfDownloadService.hasCachedPdf(patent)) {
            pdfButton.setText("View PDF");
            pdfStatusLabel.setText("PDF cached: " + patent.getPdfPath());
            pdfStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: green;");
        } else if (pdfDownloadService.canDownload(patent)) {
            pdfButton.setText("Download PDF");
            pdfStatusLabel.setText("PDF not yet cached. Click to download from USPTO.");
            pdfStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        } else {
            pdfButton.setText("Download PDF");
            pdfStatusLabel.setText("No patent or publication number available for PDF download.");
            pdfStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        }
        pdfStatusLabel.setManaged(true);
        pdfStatusLabel.setVisible(true);
    }

    @FXML
    private void handleViewPdf() {
        if (pdfDownloadService.hasCachedPdf(patent)) {
            try {
                pdfDownloadService.openPdf(patent);
            } catch (Exception e) {
                saveStatusLabel.setStyle("-fx-text-fill: red;");
                saveStatusLabel.setText("Could not open PDF: " + e.getMessage());
            }
            return;
        }

        if (!pdfDownloadService.canDownload(patent)) {
            saveStatusLabel.setStyle("-fx-text-fill: red;");
            saveStatusLabel.setText("No patent or publication number \u2014 cannot download PDF.");
            return;
        }

        pdfButton.setDisable(true);
        saveStatusLabel.setStyle("-fx-text-fill: blue;");
        saveStatusLabel.setText("Downloading PDF...");

        new Thread(() -> {
            PdfDownloadService.DownloadResult result = pdfDownloadService.downloadPdf(patent);
            Platform.runLater(() -> {
                pdfButton.setDisable(false);
                if (result.success()) {
                    saveStatusLabel.setStyle("-fx-text-fill: green;");
                    saveStatusLabel.setText("PDF downloaded and cached (" + result.sourceType() + ").");
                    updatePdfStatus();
                    if (parentController != null) parentController.refreshData();
                } else {
                    saveStatusLabel.setStyle("-fx-text-fill: red;");
                    saveStatusLabel.setText(result.error());
                }
            });
        }).start();
    }

    @FXML
    private void handleViewGooglePatents() {
        String url = UsptoSyncService.getGooglePatentsUrl(patent);
        if (url == null) {
            saveStatusLabel.setStyle("-fx-text-fill: red;");
            saveStatusLabel.setText("No patent or application number to look up.");
            return;
        }
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception e) {
            saveStatusLabel.setStyle("-fx-text-fill: red;");
            saveStatusLabel.setText("Could not open browser: " + e.getMessage());
        }
    }

    private void openRelatedPatent(Patent related) {
        setPatent(related);
    }

    private String orEmpty(String s) {
        return s != null ? s : "";
    }

    private String emptyToNull(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return s.trim();
    }
}
