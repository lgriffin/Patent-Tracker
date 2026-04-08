package com.patenttracker.controller;

import com.patenttracker.dao.InventorDao;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.dao.TagDao;
import com.patenttracker.model.Inventor;
import com.patenttracker.model.Patent;
import com.patenttracker.model.Tag;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PatentListController {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<Inventor> inventorFilter;
    @FXML private ComboBox<String> classificationFilter;
    @FXML private ComboBox<Tag> tagFilter;
    @FXML private Label resultCountLabel;
    @FXML private FlowPane activeFiltersPane;
    @FXML private TableView<Patent> patentTable;
    @FXML private TableColumn<Patent, String> colTitle;
    @FXML private TableColumn<Patent, String> colFileNumber;
    @FXML private TableColumn<Patent, String> colFilingDate;
    @FXML private TableColumn<Patent, String> colAppNumber;
    @FXML private TableColumn<Patent, String> colPatentNumber;
    @FXML private TableColumn<Patent, String> colStatus;
    @FXML private TableColumn<Patent, String> colPrimaryInventor;
    @FXML private TableColumn<Patent, String> colSecondaryInventor;
    @FXML private TableColumn<Patent, String> colAdditionalInventors;
    @FXML private TableColumn<Patent, String> colClassification;
    @FXML private TableColumn<Patent, String> colTags;

    private final ObservableList<Patent> patents = FXCollections.observableArrayList();
    private PatentDao patentDao;
    private int totalCount;

    @FXML
    public void initialize() {
        patentDao = new PatentDao();

        // Set up cell value factories
        colTitle.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getTitle()));
        colFileNumber.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFileNumber()));
        colFilingDate.setCellValueFactory(cd -> {
            var date = cd.getValue().getFilingDate();
            return new SimpleStringProperty(date != null ? date.format(DISPLAY_DATE) : "");
        });
        colAppNumber.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getApplicationNumber()));
        colPatentNumber.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPatentNumber()));
        colStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPtoStatus()));
        colPrimaryInventor.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPrimaryInventorName()));
        colSecondaryInventor.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getSecondaryInventorName()));
        colAdditionalInventors.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAdditionalInventorNames()));
        colClassification.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getClassification()));
        colTags.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getTagNames()));

        // Status column styling - supports raw USPTO status strings
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().removeAll("status-issued", "status-filed", "status-published",
                        "status-abandoned", "status-dropped", "status-allowed");
                if (item != null) {
                    String lower = item.toLowerCase();
                    if (lower.contains("patented") || lower.contains("issued")) {
                        getStyleClass().add("status-issued");
                    } else if (lower.contains("abandoned")) {
                        getStyleClass().add("status-abandoned");
                    } else if (lower.contains("allowed") || lower.contains("allowance")) {
                        getStyleClass().add("status-allowed");
                    } else if (lower.contains("published") || lower.contains("publication")) {
                        getStyleClass().add("status-published");
                    } else if (lower.contains("docketed") || lower.contains("pending")
                            || lower.contains("filed") || lower.contains("received")) {
                        getStyleClass().add("status-filed");
                    }
                }
            }
        });

        // Tags column styling — wrap text and use smaller font
        colTags.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setStyle("-fx-font-size: 11px;");
            }
        });

        patentTable.setItems(patents);

        // Double-click to open detail
        patentTable.setRowFactory(tv -> {
            TableRow<Patent> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openPatentDetail(row.getItem());
                }
            });
            return row;
        });

        loadFilterOptions();
        refreshData();
    }

    public void refreshData() {
        try {
            applyFilters();
        } catch (SQLException e) {
            showError("Failed to load patents", e.getMessage());
        }
    }

    private void loadFilterOptions() {
        try {
            // Status filter
            List<String> statuses = patentDao.getDistinctStatuses();
            statusFilter.setItems(FXCollections.observableArrayList(statuses));

            // Inventor filter
            InventorDao inventorDao = new InventorDao();
            List<Inventor> inventors = inventorDao.findAll();
            inventorFilter.setItems(FXCollections.observableArrayList(inventors));
            inventorFilter.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(Inventor item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getDisplayName() + " (" + item.getPatentCount() + ")");
                }
            });
            inventorFilter.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Inventor item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getDisplayName());
                }
            });

            // Formal Classification filter — populated from distinct tags
            loadClassificationFilter();

            // Tag filter
            loadTagFilter();

            totalCount = patentDao.count();
        } catch (SQLException e) {
            showError("Failed to load filter options", e.getMessage());
        }
    }

    private void loadClassificationFilter() {
        try {
            // Use distinct tags as classification options
            TagDao tagDao = new TagDao();
            List<Tag> tags = tagDao.findAllWithCounts();
            List<String> tagNames = tags.stream().map(Tag::getName).toList();

            // Also include any existing classification values not covered by tags
            List<String> existingClassifications = patentDao.getDistinctClassifications();
            List<String> combined = new java.util.ArrayList<>(tagNames);
            for (String c : existingClassifications) {
                if (!combined.contains(c)) combined.add(c);
            }
            java.util.Collections.sort(combined, String.CASE_INSENSITIVE_ORDER);

            classificationFilter.setItems(FXCollections.observableArrayList(combined));
        } catch (SQLException e) {
            // Fall back to existing classifications
            try {
                classificationFilter.setItems(FXCollections.observableArrayList(patentDao.getDistinctClassifications()));
            } catch (SQLException ex) {
                // Ignore
            }
        }
    }

    public void loadTagFilter() {
        try {
            TagDao tagDao = new TagDao();
            List<Tag> tags = tagDao.findAllWithCounts();
            tagFilter.setItems(FXCollections.observableArrayList(tags));
            tagFilter.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(Tag item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName() + " (" + item.getPatentCount() + ")");
                }
            });
            tagFilter.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Tag item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });
        } catch (SQLException e) {
            // Tags may not be populated yet
        }
    }

    @FXML
    private void handleSearch() {
        try {
            applyFilters();
        } catch (SQLException e) {
            showError("Search failed", e.getMessage());
        }
    }

    @FXML
    private void handleFilterChange() {
        try {
            applyFilters();
        } catch (SQLException e) {
            showError("Filter failed", e.getMessage());
        }
    }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        statusFilter.getSelectionModel().clearSelection();
        statusFilter.setValue(null);
        inventorFilter.getSelectionModel().clearSelection();
        inventorFilter.setValue(null);
        classificationFilter.getSelectionModel().clearSelection();
        classificationFilter.setValue(null);
        tagFilter.getSelectionModel().clearSelection();
        tagFilter.setValue(null);
        try {
            applyFilters();
        } catch (SQLException e) {
            showError("Failed to clear filters", e.getMessage());
        }
    }

    private void applyFilters() throws SQLException {
        String query = searchField.getText();
        String status = statusFilter.getValue();
        Inventor inventor = inventorFilter.getValue();
        String classification = classificationFilter.getValue();
        Tag tag = tagFilter.getValue();

        boolean hasFilters = (query != null && !query.isBlank()) || status != null
                || inventor != null || classification != null || tag != null;

        List<Patent> results;
        if (hasFilters) {
            results = patentDao.search(
                query,
                status,
                inventor != null ? inventor.getId() : null,
                classification,
                null, null,
                tag != null ? tag.getId() : null
            );
        } else {
            results = patentDao.findAll();
            totalCount = results.size();
        }

        patents.setAll(results);
        resultCountLabel.setText(hasFilters
                ? results.size() + " of " + totalCount + " patents"
                : results.size() + " patents");
    }

    public List<Patent> getCurrentFilteredPatents() {
        return List.copyOf(patents);
    }

    private void openPatentDetail(Patent patent) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/patent-detail.fxml"));
            Parent root = loader.load();

            PatentDetailController controller = loader.getController();
            controller.setPatent(patent);
            controller.setParentController(this);

            Stage stage = new Stage();
            stage.setTitle("Patent: " + patent.getTitle());
            stage.initModality(Modality.NONE);
            Scene scene = new Scene(root, 700, 600);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            showError("Failed to open patent detail", e.getMessage());
        }
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
