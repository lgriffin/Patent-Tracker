package com.patenttracker.controller;

import com.patenttracker.service.StatsService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DashboardController {

    @FXML private Label totalCountLabel;
    @FXML private Label patentedCountLabel;
    @FXML private Label allowedCountLabel;
    @FXML private Label examinationCountLabel;
    @FXML private Label filedCountLabel;
    @FXML private Label abandonedCountLabel;
    @FXML private Label ownerNameLabel;
    @FXML private Label ownerPrimaryLabel;
    @FXML private Label ownerSecondaryLabel;
    @FXML private Label ownerAdditionalLabel;
    @FXML private Label ownerTotalLabel;
    @FXML private PieChart statusChart;
    @FXML private BarChart<String, Number> yearlyBreakdownChart;

    // Classification table
    @FXML @SuppressWarnings("rawtypes") private TableView classificationTable;
    @FXML @SuppressWarnings("rawtypes") private TableColumn colClassRank;
    @FXML @SuppressWarnings("rawtypes") private TableColumn colClassName;
    @FXML @SuppressWarnings("rawtypes") private TableColumn colClassCount;

    // Collaborator table
    @FXML @SuppressWarnings("rawtypes") private TableView collaboratorTable;
    @FXML @SuppressWarnings("rawtypes") private TableColumn colRank;
    @FXML @SuppressWarnings("rawtypes") private TableColumn colCollaborator;
    @FXML @SuppressWarnings("rawtypes") private TableColumn colPatentCount;
    @FXML @SuppressWarnings("rawtypes") private TableColumn colCollabClassifications;

    record ClassificationRow(int rank, String name, int count) {}
    record CollaboratorRow(int rank, String name, int patents, String classifications) {}

    private final StatsService statsService = new StatsService();

    @SuppressWarnings("unchecked")
    @FXML
    public void initialize() {
        // Set up classification table columns
        colClassRank.setCellValueFactory(cd ->
                new SimpleIntegerProperty(((ClassificationRow) ((TableColumn.CellDataFeatures) cd).getValue()).rank()));
        colClassName.setCellValueFactory(cd ->
                new SimpleStringProperty(((ClassificationRow) ((TableColumn.CellDataFeatures) cd).getValue()).name()));
        colClassCount.setCellValueFactory(cd ->
                new SimpleIntegerProperty(((ClassificationRow) ((TableColumn.CellDataFeatures) cd).getValue()).count()));

        // Set up collaborator table columns
        colRank.setCellValueFactory(cd ->
                new SimpleIntegerProperty(((CollaboratorRow) ((TableColumn.CellDataFeatures) cd).getValue()).rank()));
        colCollaborator.setCellValueFactory(cd ->
                new SimpleStringProperty(((CollaboratorRow) ((TableColumn.CellDataFeatures) cd).getValue()).name()));
        colPatentCount.setCellValueFactory(cd ->
                new SimpleIntegerProperty(((CollaboratorRow) ((TableColumn.CellDataFeatures) cd).getValue()).patents()));
        colCollabClassifications.setCellValueFactory(cd ->
                new SimpleStringProperty(((CollaboratorRow) ((TableColumn.CellDataFeatures) cd).getValue()).classifications()));

        refresh();
    }

    public void refresh() {
        try {
            // Summary cards using grouped statuses
            int total = statsService.getTotalCount();
            totalCountLabel.setText(String.valueOf(total));

            Map<String, Integer> grouped = statsService.getCountByStatusGrouped();
            patentedCountLabel.setText(String.valueOf(grouped.getOrDefault("Patented", 0)));
            allowedCountLabel.setText(String.valueOf(grouped.getOrDefault("Allowed", 0)));
            examinationCountLabel.setText(String.valueOf(grouped.getOrDefault("In Examination", 0)));
            filedCountLabel.setText(String.valueOf(grouped.getOrDefault("Filed/Pending", 0)));
            abandonedCountLabel.setText(String.valueOf(grouped.getOrDefault("Abandoned/Expired", 0)));

            // Owner stats
            String ownerName = SettingsController.getOwnerName();
            ownerNameLabel.setText(ownerName);
            Map<String, Integer> ownerRoles = statsService.getOwnerRoleBreakdown(ownerName);
            int primary = ownerRoles.getOrDefault("PRIMARY", 0);
            int secondary = ownerRoles.getOrDefault("SECONDARY", 0);
            int additional = ownerRoles.getOrDefault("ADDITIONAL", 0);
            ownerPrimaryLabel.setText(String.valueOf(primary));
            ownerSecondaryLabel.setText(String.valueOf(secondary));
            ownerAdditionalLabel.setText(String.valueOf(additional));
            ownerTotalLabel.setText(String.valueOf(primary + secondary + additional));

            // Status pie chart — use grouped statuses
            statusChart.setData(FXCollections.observableArrayList(
                grouped.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(e -> new PieChart.Data(e.getKey() + " (" + e.getValue() + ")", e.getValue()))
                    .toList()
            ));

            // Yearly breakdown: Filed vs Issued vs Published
            yearlyBreakdownChart.getData().clear();
            Map<String, Map<String, Integer>> breakdown = statsService.getYearlyBreakdown();

            XYChart.Series<String, Number> filedSeries = new XYChart.Series<>();
            filedSeries.setName("Filed");
            XYChart.Series<String, Number> issuedSeries = new XYChart.Series<>();
            issuedSeries.setName("Issued");
            XYChart.Series<String, Number> publishedSeries = new XYChart.Series<>();
            publishedSeries.setName("Published");

            breakdown.forEach((year, counts) -> {
                filedSeries.getData().add(new XYChart.Data<>(year, counts.getOrDefault("Filed", 0)));
                issuedSeries.getData().add(new XYChart.Data<>(year, counts.getOrDefault("Issued", 0)));
                publishedSeries.getData().add(new XYChart.Data<>(year, counts.getOrDefault("Published", 0)));
            });

            yearlyBreakdownChart.getData().addAll(filedSeries, issuedSeries, publishedSeries);

            // Classification table
            Map<String, Integer> byClass = statsService.getCountByClassification();
            List<ClassificationRow> classRows = new ArrayList<>();
            int classRank = 1;
            for (var entry : byClass.entrySet()) {
                classRows.add(new ClassificationRow(classRank++, entry.getKey(), entry.getValue()));
            }
            classificationTable.setItems(FXCollections.observableArrayList(classRows));

            // Top 10 collaborators table (excluding owner, with top classifications)
            List<StatsService.CollaboratorInfo> collaborators =
                    statsService.getTopCollaboratorsWithClassifications(ownerName, 10);
            List<CollaboratorRow> collabRows = new ArrayList<>();
            int rank = 1;
            for (var info : collaborators) {
                collabRows.add(new CollaboratorRow(rank++, info.name(), info.patents(), info.topClassifications()));
            }
            collaboratorTable.setItems(FXCollections.observableArrayList(collabRows));

        } catch (SQLException e) {
            totalCountLabel.setText("Error");
        }
    }
}
