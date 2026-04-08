package com.patenttracker.controller;

import com.patenttracker.dao.InventorDao;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.model.Inventor;
import com.patenttracker.model.Patent;
import com.patenttracker.service.GraphDataService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.sql.SQLException;
import java.util.List;

public class GraphController {

    @FXML private WebView webView;
    @FXML private VBox sidePanel;
    @FXML private Label inventorNameLabel;
    @FXML private Label inventorCountLabel;
    @FXML private ListView<String> inventorPatentsList;
    @FXML private ListView<String> coInventorsList;

    private final GraphDataService graphDataService = new GraphDataService();
    private WebEngine webEngine;
    private boolean htmlLoaded = false;

    @FXML
    public void initialize() {
        webEngine = webView.getEngine();

        // Load graph.html
        String graphUrl = getClass().getResource("/graph/graph.html").toExternalForm();
        webEngine.load(graphUrl);

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                htmlLoaded = true;
                // Set up Java bridge
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("javaBridge", new JavaBridge());
            }
        });
    }

    public void refresh(List<Patent> filteredPatents) {
        if (!htmlLoaded) return;

        try {
            String graphJson = graphDataService.buildGraphJson(filteredPatents);
            String escapedJson = graphJson.replace("'", "\\'");
            webEngine.executeScript("receiveGraphData('" + escapedJson + "')");
        } catch (SQLException e) {
            // Log error
        }
    }

    @FXML
    private void handleResetLayout() {
        if (htmlLoaded) {
            webEngine.executeScript("resetLayout()");
        }
    }

    private void showInventorDetails(int inventorId, String name, int patentCount) {
        sidePanel.setManaged(true);
        sidePanel.setVisible(true);

        inventorNameLabel.setText(name);
        inventorCountLabel.setText(patentCount + " patents");

        // Load inventor's patents
        try {
            InventorDao inventorDao = new InventorDao();
            List<Inventor> inventors = inventorDao.findByPatentId(0); // We need a different query

            // Get patents for this inventor
            PatentDao patentDao = new PatentDao();
            List<Patent> patents = patentDao.search(null, null, inventorId, null, null, null, null);

            inventorPatentsList.getItems().clear();
            for (Patent p : patents) {
                inventorPatentsList.getItems().add(p.getTitle());
            }

            // Get co-inventors
            List<InventorDao.CoInventorEdge> allEdges = inventorDao.getCoInventorEdges();
            coInventorsList.getItems().clear();
            for (InventorDao.CoInventorEdge edge : allEdges) {
                if (edge.inventor1Id() == inventorId) {
                    coInventorsList.getItems().add(edge.inventor2Name() + " (" + edge.sharedCount() + " shared)");
                } else if (edge.inventor2Id() == inventorId) {
                    coInventorsList.getItems().add(edge.inventor1Name() + " (" + edge.sharedCount() + " shared)");
                }
            }
        } catch (SQLException e) {
            // Ignore
        }
    }

    // Bridge class called from JavaScript
    public class JavaBridge {
        public void onNodeClicked(int nodeId, String label, int value) {
            javafx.application.Platform.runLater(() -> showInventorDetails(nodeId, label, value));
        }
    }
}
