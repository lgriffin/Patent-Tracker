package com.patenttracker.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages formal classification definitions that the user cares about.
 * Classifications are stored in config.properties as a comma-separated list.
 */
public class ClassificationController {

    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.patenttracker";
    private static final String CONFIG_FILE = CONFIG_DIR + "/config.properties";
    private static final String CLASSIFICATIONS_KEY = "classifications";

    @FXML private TextField newClassificationField;
    @FXML private TableView<ClassificationRow> classificationsTable;
    @FXML private TableColumn<ClassificationRow, String> colName;
    @FXML private TableColumn<ClassificationRow, Number> colCount;
    @FXML private TableColumn<ClassificationRow, Void> colActions;
    @FXML private Label statusLabel;

    private final ObservableList<ClassificationRow> rows = FXCollections.observableArrayList();

    record ClassificationRow(String name, int patentCount) {}

    @FXML
    public void initialize() {
        colName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().name()));
        colCount.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().patentCount()));

        // Action buttons column (Rename / Delete)
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button renameBtn = new Button("Rename");
            private final Button deleteBtn = new Button("Delete");
            {
                renameBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6;");
                deleteBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6; -fx-text-fill: #dc3545;");
                renameBtn.setOnAction(e -> handleRename(getIndex()));
                deleteBtn.setOnAction(e -> handleDelete(getIndex()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(4, renameBtn, deleteBtn);
                    setGraphic(box);
                }
            }
        });

        classificationsTable.setItems(rows);
        loadClassifications();
    }

    private void loadClassifications() {
        rows.clear();
        List<String> classifications = getClassifications();

        // Count patents per classification using tags
        Map<String, Integer> counts = new HashMap<>();
        try {
            var tagDao = new com.patenttracker.dao.TagDao();
            var allTags = tagDao.findAllWithCounts();
            for (var tag : allTags) {
                counts.put(tag.getName().toLowerCase(), tag.getPatentCount());
            }
        } catch (Exception e) {
            // Non-critical - just show 0 counts
        }

        for (String name : classifications) {
            int count = counts.getOrDefault(name.toLowerCase(), 0);
            rows.add(new ClassificationRow(name, count));
        }

        statusLabel.setText(classifications.size() + " classifications defined");
    }

    @FXML
    private void handleAdd() {
        String name = newClassificationField.getText().trim();
        if (name.isEmpty()) return;

        List<String> current = getClassifications();
        // Check for duplicates (case-insensitive)
        boolean exists = current.stream().anyMatch(c -> c.equalsIgnoreCase(name));
        if (exists) {
            statusLabel.setText("Classification '" + name + "' already exists");
            return;
        }

        current.add(name);
        saveClassifications(current);
        newClassificationField.clear();
        loadClassifications();
        statusLabel.setText("Added '" + name + "'");
    }

    private void handleRename(int index) {
        if (index < 0 || index >= rows.size()) return;
        String oldName = rows.get(index).name();

        TextInputDialog dialog = new TextInputDialog(oldName);
        dialog.setTitle("Rename Classification");
        dialog.setHeaderText("Rename '" + oldName + "'");
        dialog.setContentText("New name:");
        dialog.showAndWait().ifPresent(newName -> {
            newName = newName.trim();
            if (!newName.isEmpty() && !newName.equals(oldName)) {
                List<String> current = getClassifications();
                int idx = -1;
                for (int i = 0; i < current.size(); i++) {
                    if (current.get(i).equalsIgnoreCase(oldName)) { idx = i; break; }
                }
                if (idx >= 0) {
                    current.set(idx, newName);
                    saveClassifications(current);
                    loadClassifications();
                    statusLabel.setText("Renamed '" + oldName + "' to '" + newName + "'");
                }
            }
        });
    }

    private void handleDelete(int index) {
        if (index < 0 || index >= rows.size()) return;
        String name = rows.get(index).name();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Classification");
        confirm.setHeaderText("Delete '" + name + "'?");
        confirm.setContentText("This removes the classification from your list. Existing patent tags are not affected.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                List<String> current = getClassifications();
                current.removeIf(c -> c.equalsIgnoreCase(name));
                saveClassifications(current);
                loadClassifications();
                statusLabel.setText("Deleted '" + name + "'");
            }
        });
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) classificationsTable.getScene().getWindow();
        stage.close();
    }

    // --- Static methods for accessing classifications from other parts of the app ---

    public static List<String> getClassifications() {
        Properties props = SettingsController.loadProperties();
        String raw = props.getProperty(CLASSIFICATIONS_KEY, "");
        if (raw.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    public static void saveClassifications(List<String> classifications) {
        Properties props = SettingsController.loadProperties();
        String value = classifications.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("|"));
        props.setProperty(CLASSIFICATIONS_KEY, value);

        try {
            Files.createDirectories(Path.of(CONFIG_DIR));
            try (OutputStream os = new FileOutputStream(CONFIG_FILE)) {
                props.store(os, "Patent Portfolio Tracker Settings");
            }
        } catch (IOException e) {
            // Fail silently - settings will be re-read from default
        }
    }
}
