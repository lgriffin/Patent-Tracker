package com.patenttracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patenttracker.dao.PatentDao;
import com.patenttracker.dao.StatusUpdateDao;
import com.patenttracker.model.Patent;
import com.patenttracker.model.StatusUpdate;

import com.patenttracker.controller.SettingsController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class UsptoSyncService {

    private static final String ODP_BASE_URL = "https://api.uspto.gov/api/v1/patent/applications/";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final PatentDao patentDao;
    private final StatusUpdateDao statusUpdateDao;
    private final HttpClient httpClient;

    public UsptoSyncService() {
        this.patentDao = new PatentDao();
        this.statusUpdateDao = new StatusUpdateDao();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Status lifecycle phases ordered by progression.
     * Used to determine if a status change is a progression or regression.
     */
    private static final List<String> STATUS_LIFECYCLE = List.of(
        "Filed", "Pending", "Docketed", "Examined",
        "Published", "Allowed", "Patented", "Issued"
    );

    private static final List<String> TERMINAL_STATUSES = List.of(
        "Abandoned", "Expired", "Withdrawn"
    );

    public SyncResult syncPatent(Patent patent) {
        String appNumber = patent.getApplicationNumber();
        String title = patent.getTitle();
        if (appNumber == null || appNumber.isBlank()) {
            return new SyncResult(false, null, "Patent has no application number.",
                    appNumber, title, null, null, StatusMovement.NONE);
        }

        // Strip slashes: "15/661380" -> "15661380"
        String cleanAppNumber = appNumber.replace("/", "");

        try {
            String apiKey = SettingsController.getApiKey();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(ODP_BASE_URL + cleanAppNumber))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("X-Api-Key", apiKey);
            }
            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return processResponse(patent, response.body());
            } else if (response.statusCode() == 404) {
                return new SyncResult(false, null,
                        "Not yet published (pre-publication applications are not in the public API).",
                        appNumber, title, null, null, StatusMovement.NONE);
            } else if (response.statusCode() == 429) {
                return new SyncResult(false, null, "Rate limited by USPTO. Please wait and try again.",
                        appNumber, title, null, null, StatusMovement.NONE);
            } else {
                return new SyncResult(false, null,
                        "USPTO API returned HTTP " + response.statusCode() + ": " + response.body(),
                        appNumber, title, null, null, StatusMovement.NONE);
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new SyncResult(false, null, "Connection error: " + msg,
                    appNumber, title, null, null, StatusMovement.NONE);
        }
    }

    public List<SyncResult> syncAll(SyncProgressCallback progressCallback) {
        int delay = SettingsController.getRateLimitDelay();
        List<SyncResult> results = new ArrayList<>();

        try {
            List<Patent> patents = patentDao.findAll();
            List<Patent> syncable = patents.stream()
                    .filter(p -> p.getApplicationNumber() != null && !p.getApplicationNumber().isBlank())
                    .toList();

            for (int i = 0; i < syncable.size(); i++) {
                if (progressCallback != null && progressCallback.isCancelled()) {
                    break;
                }

                Patent patent = syncable.get(i);
                if (progressCallback != null) {
                    progressCallback.onProgress(i + 1, syncable.size(), patent.getTitle());
                }

                SyncResult result = syncPatent(patent);
                results.add(result);

                if (progressCallback != null) {
                    progressCallback.onResult(result);
                }

                // Rate limit delay
                if (i < syncable.size() - 1) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            results.add(new SyncResult(false, null, "Database error: " + e.getMessage(),
                    null, null, null, null, StatusMovement.NONE));
        }

        return results;
    }

    private SyncResult processResponse(Patent patent, String responseBody) {
        String appNumber = patent.getApplicationNumber();
        String title = patent.getTitle();
        try {
            JsonNode root = mapper.readTree(responseBody);

            // ODP response: { count: N, patentFileWrapperDataBag: [ { applicationMetaData: {...} } ] }
            JsonNode dataBag = root.path("patentFileWrapperDataBag");
            if (!dataBag.isArray() || dataBag.isEmpty()) {
                return new SyncResult(false, null,
                    "Application " + appNumber + " not found on USPTO.",
                    appNumber, title, null, null, StatusMovement.NONE);
            }

            JsonNode appMeta = dataBag.get(0).path("applicationMetaData");
            List<String> changes = new ArrayList<>();
            String oldStatus = patent.getPtoStatus();
            String newStatus = null;
            StatusMovement movement = StatusMovement.NONE;

            // Check status - store raw USPTO applicationStatusDescriptionText
            String odpStatus = getTextField(appMeta, "applicationStatusDescriptionText");
            if (odpStatus != null && !odpStatus.equals(patent.getPtoStatus())) {
                recordChange(patent.getId(), "ptoStatus", patent.getPtoStatus(), odpStatus);
                newStatus = odpStatus;
                movement = classifyMovement(oldStatus, newStatus);
                patent.setPtoStatus(odpStatus);
                changes.add("Status: " + oldStatus + " -> " + odpStatus + " (" + movementLabel(movement) + ")");
            }

            // Check patent number
            String odpPatentNumber = getTextField(appMeta, "patentNumber");
            if (odpPatentNumber != null) {
                if (patent.getPatentNumber() == null || !patent.getPatentNumber().equals(odpPatentNumber)) {
                    recordChange(patent.getId(), "patentNumber", patent.getPatentNumber(), odpPatentNumber);
                    patent.setPatentNumber(odpPatentNumber);
                    changes.add("Patent #: " + odpPatentNumber);
                }
            }

            // Check grant/issue date (ODP format: yyyy-MM-dd)
            String odpGrantDate = getTextField(appMeta, "grantDate");
            if (odpGrantDate != null) {
                LocalDate grantDate = parseDate(odpGrantDate);
                if (grantDate != null && !grantDate.equals(patent.getIssueGrantDate())) {
                    String oldVal = patent.getIssueGrantDate() != null ? patent.getIssueGrantDate().toString() : null;
                    recordChange(patent.getId(), "issueGrantDate", oldVal, grantDate.toString());
                    patent.setIssueGrantDate(grantDate);
                    changes.add("Issue Date: " + grantDate);
                }
            }

            // Check publication date
            String odpPubDate = getTextField(appMeta, "earliestPublicationDate");
            if (odpPubDate != null) {
                LocalDate pubDate = parseDate(odpPubDate);
                if (pubDate != null && !pubDate.equals(patent.getPublicationDate())) {
                    String oldVal = patent.getPublicationDate() != null ? patent.getPublicationDate().toString() : null;
                    recordChange(patent.getId(), "publicationDate", oldVal, pubDate.toString());
                    patent.setPublicationDate(pubDate);
                    changes.add("Publication Date: " + pubDate);
                }
            }

            // Check publication number
            String odpPubNumber = getTextField(appMeta, "earliestPublicationNumber");
            if (odpPubNumber != null) {
                if (patent.getPublicationNumber() == null || !patent.getPublicationNumber().equals(odpPubNumber)) {
                    recordChange(patent.getId(), "publicationNumber", patent.getPublicationNumber(), odpPubNumber);
                    patent.setPublicationNumber(odpPubNumber);
                    changes.add("Publication #: " + odpPubNumber);
                }
            }

            if (!changes.isEmpty()) {
                patentDao.update(patent);
                return new SyncResult(true, String.join("\n", changes), null,
                        appNumber, title, oldStatus, newStatus, movement);
            }

            return new SyncResult(false, null, null,
                    appNumber, title, null, null, StatusMovement.NONE);

        } catch (Exception e) {
            return new SyncResult(false, null, "Failed to parse USPTO response: " + e.getMessage(),
                    appNumber, title, null, null, StatusMovement.NONE);
        }
    }

    private String getTextField(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) return null;
        String val = field.asText().trim();
        return val.isEmpty() ? null : val;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        for (String pattern : new String[]{"yyyy-MM-dd", "MM-dd-yyyy", "M/d/yyyy"}) {
            try {
                return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException e) {
                // Try next pattern
            }
        }
        return null;
    }

    /**
     * Determines the lifecycle phase (0-based index) of a USPTO status string.
     * Higher index = further along in the patent lifecycle.
     */
    static int lifecyclePhase(String status) {
        if (status == null || status.isBlank()) return 0;
        String lower = status.toLowerCase();
        if (lower.contains("patented") || lower.contains("issued")) return 7;
        if (lower.contains("allowed") || lower.contains("allowance")) return 6;
        if (lower.contains("published") || lower.contains("publication")) return 5;
        if (lower.contains("examined") || lower.contains("examination")
                || lower.contains("action") || lower.contains("response")
                || lower.contains("rejection")) return 4;
        if (lower.contains("docketed")) return 3;
        if (lower.contains("pending") || lower.contains("received")) return 2;
        if (lower.contains("filed") || lower.contains("new case")) return 1;
        return 0; // unknown
    }

    static boolean isTerminal(String status) {
        if (status == null) return false;
        String lower = status.toLowerCase();
        return lower.contains("abandoned") || lower.contains("expired") || lower.contains("withdrawn");
    }

    static StatusMovement classifyMovement(String oldStatus, String newStatus) {
        if (newStatus == null) return StatusMovement.NONE;
        if (isTerminal(newStatus)) return StatusMovement.TERMINAL;
        if (oldStatus == null || oldStatus.isBlank()) return StatusMovement.PROGRESSED;
        int oldPhase = lifecyclePhase(oldStatus);
        int newPhase = lifecyclePhase(newStatus);
        if (newPhase > oldPhase) return StatusMovement.PROGRESSED;
        if (newPhase < oldPhase) return StatusMovement.REGRESSED;
        return StatusMovement.LATERAL;
    }

    public static String movementLabel(StatusMovement movement) {
        return switch (movement) {
            case PROGRESSED -> "Progressed";
            case REGRESSED -> "Regressed";
            case LATERAL -> "Lateral";
            case TERMINAL -> "Terminal";
            case NONE -> "No Change";
        };
    }

    private void recordChange(int patentId, String field, String oldVal, String newVal) {
        try {
            statusUpdateDao.insert(new StatusUpdate(patentId, field, oldVal, newVal, "USPTO_SYNC"));
        } catch (SQLException e) {
            // Non-critical
        }
    }

    /**
     * Returns a Google Patents URL for the given patent, for manual lookup.
     */
    public static String getGooglePatentsUrl(Patent patent) {
        if (patent.getPatentNumber() != null && !patent.getPatentNumber().isBlank()) {
            return "https://patents.google.com/patent/US" + patent.getPatentNumber();
        }
        if (patent.getApplicationNumber() != null && !patent.getApplicationNumber().isBlank()) {
            String clean = patent.getApplicationNumber().replace("/", "");
            return "https://patents.google.com/patent/US" + clean;
        }
        return null;
    }

    public record SyncResult(boolean hasChanges, String summary, String error,
                                String appNumber, String title,
                                String oldStatus, String newStatus, StatusMovement movement) {}

    public enum StatusMovement {
        PROGRESSED, REGRESSED, LATERAL, TERMINAL, NONE
    }

    public interface SyncProgressCallback {
        void onProgress(int current, int total, String patentTitle);
        void onResult(SyncResult result);
        boolean isCancelled();
    }

}
