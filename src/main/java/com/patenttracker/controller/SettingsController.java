package com.patenttracker.controller;

import com.patenttracker.dao.InventorDao;
import com.patenttracker.model.Inventor;
import com.patenttracker.service.PdfDownloadService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class SettingsController {

    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.patenttracker";
    private static final String CONFIG_FILE = CONFIG_DIR + "/config.properties";
    private static final String DEFAULT_API_KEY = "";

    @FXML private ComboBox<String> ownerNameCombo;
    @FXML private PasswordField apiKeyField;
    @FXML private Spinner<Integer> rateLimitSpinner;
    @FXML private Label dbPathLabel;
    @FXML private Label ownerStatusLabel;
    @FXML private Label apiKeyStatusLabel;
    @FXML private Label pdfCacheLabel;
    @FXML private Label flushStatusLabel;

    private Runnable onOwnerChanged;

    @FXML
    public void initialize() {
        rateLimitSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(100, 5000, 1100, 100)
        );

        // Make the combo editable so users can type a name not yet in the DB
        ownerNameCombo.setEditable(true);

        // Populate with known inventors from the database
        try {
            InventorDao inventorDao = new InventorDao();
            List<Inventor> inventors = inventorDao.findAll();
            List<String> names = inventors.stream()
                    .map(Inventor::getFullName)
                    .filter(n -> n != null && !n.isBlank())
                    .distinct()
                    .toList();
            ownerNameCombo.setItems(FXCollections.observableArrayList(names));
        } catch (SQLException e) {
            // DB may not be ready yet — leave combo empty, user can type
        }

        dbPathLabel.setText(CONFIG_DIR + "/patents.db");

        refreshPdfCacheLabel();
        loadSettings();
    }

    private void loadSettings() {
        Properties props = loadProperties();
        ownerNameCombo.setValue(props.getProperty("owner.name", "Leigh Griffin"));
        apiKeyField.setText(props.getProperty("uspto.api.key", DEFAULT_API_KEY));
        String delay = props.getProperty("uspto.rate.delay", "1100");
        try {
            rateLimitSpinner.getValueFactory().setValue(Integer.parseInt(delay));
        } catch (NumberFormatException e) {
            rateLimitSpinner.getValueFactory().setValue(1100);
        }
    }

    public void setOnOwnerChanged(Runnable callback) {
        this.onOwnerChanged = callback;
    }

    @FXML
    private void handleApplyOwner() {
        String ownerName = ownerNameCombo.getValue();
        if (ownerName == null || ownerName.trim().isEmpty()) {
            ownerStatusLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-size: 11px;");
            ownerStatusLabel.setText("Please select or enter a name.");
            ownerStatusLabel.setManaged(true);
            ownerStatusLabel.setVisible(true);
            return;
        }

        // Save just the owner name immediately
        Properties props = loadProperties();
        props.setProperty("owner.name", ownerName.trim());
        saveProperties(props);
        saved = true;

        ownerStatusLabel.setStyle("-fx-text-fill: #28a745; -fx-font-size: 11px;");
        ownerStatusLabel.setText("Applied: Dashboard now shows stats for " + ownerName.trim());
        ownerStatusLabel.setManaged(true);
        ownerStatusLabel.setVisible(true);

        // Trigger live dashboard refresh
        if (onOwnerChanged != null) {
            onOwnerChanged.run();
        }
    }

    @FXML
    private void handleApplyApiKey() {
        Properties props = loadProperties();
        props.setProperty("uspto.api.key", apiKeyField.getText());
        saveProperties(props);
        saved = true;

        apiKeyStatusLabel.setStyle("-fx-text-fill: #28a745; -fx-font-size: 11px;");
        apiKeyStatusLabel.setText("API key saved.");
        apiKeyStatusLabel.setManaged(true);
        apiKeyStatusLabel.setVisible(true);
    }

    private boolean saved = false;

    @FXML
    private void handleSave() {
        String ownerName = ownerNameCombo.getValue();
        if (ownerName == null || ownerName.trim().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Validation");
            alert.setHeaderText("Portfolio Owner is required");
            alert.setContentText("Please select or enter a portfolio owner name.");
            alert.showAndWait();
            return;
        }

        Properties props = loadProperties();
        props.setProperty("owner.name", ownerName.trim());
        props.setProperty("uspto.api.key", apiKeyField.getText());
        props.setProperty("uspto.rate.delay", String.valueOf(rateLimitSpinner.getValue()));
        saveProperties(props);

        saved = true;
        Stage stage = (Stage) apiKeyField.getScene().getWindow();
        stage.close();
    }

    public boolean wasSaved() {
        return saved;
    }

    private void refreshPdfCacheLabel() {
        PdfDownloadService pdfService = new PdfDownloadService();
        int[] counts = pdfService.getCounts();
        pdfCacheLabel.setText(counts[2] + " PDFs cached (" + counts[1] + " eligible patents)");
    }

    @FXML
    private void handleFlushPdfs() {
        // Confirmation dialog requiring the user to type DELETE
        TextInputDialog confirm = new TextInputDialog();
        confirm.setTitle("Confirm Flush");
        confirm.setHeaderText("This will permanently delete all cached PDFs.");
        confirm.setContentText("Type DELETE to confirm:");
        Optional<String> result = confirm.showAndWait();

        if (result.isEmpty() || !"DELETE".equals(result.get().trim())) {
            flushStatusLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
            flushStatusLabel.setText("Flush cancelled.");
            flushStatusLabel.setManaged(true);
            flushStatusLabel.setVisible(true);
            return;
        }

        try {
            PdfDownloadService pdfService = new PdfDownloadService();
            int removed = pdfService.flushAllPdfs();
            flushStatusLabel.setStyle("-fx-text-fill: #28a745; -fx-font-size: 11px;");
            flushStatusLabel.setText("Flushed " + removed + " cached PDF(s).");
            flushStatusLabel.setManaged(true);
            flushStatusLabel.setVisible(true);
            refreshPdfCacheLabel();
        } catch (SQLException e) {
            flushStatusLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-size: 11px;");
            flushStatusLabel.setText("Flush failed: " + e.getMessage());
            flushStatusLabel.setManaged(true);
            flushStatusLabel.setVisible(true);
        }
    }

    @FXML
    private void handleCancel() {
        Stage stage = (Stage) ownerNameCombo.getScene().getWindow();
        stage.close();
    }

    public static Properties loadProperties() {
        Properties props = new Properties();
        Path path = Path.of(CONFIG_FILE);
        if (Files.exists(path)) {
            try (InputStream is = new FileInputStream(path.toFile())) {
                props.load(is);
            } catch (IOException e) {
                // Use defaults
            }
        }
        return props;
    }

    private void saveProperties(Properties props) {
        try {
            Files.createDirectories(Path.of(CONFIG_DIR));
            try (OutputStream os = new FileOutputStream(CONFIG_FILE)) {
                props.store(os, "Patent Portfolio Tracker Settings");
            }
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to save settings");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    public static String getOwnerName() {
        return loadProperties().getProperty("owner.name", "Leigh Griffin");
    }

    public static String getApiKey() {
        return loadProperties().getProperty("uspto.api.key", DEFAULT_API_KEY);
    }

    public static int getRateLimitDelay() {
        String delay = loadProperties().getProperty("uspto.rate.delay", "1100");
        try {
            return Integer.parseInt(delay);
        } catch (NumberFormatException e) {
            return 1100;
        }
    }
}
