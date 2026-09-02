package com.patenttracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patenttracker.model.DiscoveredPatent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class PatentsViewSearchService {

    private static final String API_BASE = "https://api.patentsview.org/patents/query";
    private static final int MAX_RESULTS_PER_QUERY = 50;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_DELAY_MS = 5000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient httpClient;

    public PatentsViewSearchService() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public SearchResult searchByKeywords(List<String> keywords, SearchProgressCallback callback) {
        if (keywords == null || keywords.isEmpty()) {
            return new SearchResult(false, List.of(), "No keywords provided.");
        }

        try {
            String queryJson = buildKeywordQuery(keywords);
            return executeSearch(queryJson, callback);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new SearchResult(false, List.of(), "PatentsView search failed: " + msg);
        }
    }

    public SearchResult searchByCpc(List<String> cpcCodes, SearchProgressCallback callback) {
        if (cpcCodes == null || cpcCodes.isEmpty()) {
            return new SearchResult(false, List.of(), "No CPC codes provided.");
        }

        try {
            String queryJson = buildCpcQuery(cpcCodes);
            return executeSearch(queryJson, callback);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new SearchResult(false, List.of(), "PatentsView CPC search failed: " + msg);
        }
    }

    private SearchResult executeSearch(String queryJson, SearchProgressCallback callback) {
        try {
            if (callback != null) callback.onStatus("Querying PatentsView API...");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(queryJson))
                    .build();

            HttpResponse<String> response = null;
            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                if (callback != null && callback.isCancelled()) {
                    return new SearchResult(false, List.of(), "Search cancelled.");
                }
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429 || response.statusCode() == 503) {
                    if (attempt < MAX_RETRIES) {
                        long delay = RETRY_BASE_DELAY_MS * (1L << attempt);
                        if (callback != null) {
                            callback.onStatus("PatentsView rate limited, retrying in " + (delay / 1000) + "s...");
                        }
                        Thread.sleep(delay);
                        continue;
                    }
                }
                break;
            }

            if (response.statusCode() != 200) {
                return new SearchResult(false, List.of(),
                        "PatentsView returned HTTP " + response.statusCode());
            }

            List<DiscoveredPatent> patents = parseResponse(response.body());
            if (callback != null) {
                callback.onStatus("PatentsView found " + patents.size() + " patents.");
            }
            return new SearchResult(true, patents, null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SearchResult(false, List.of(), "Search cancelled.");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new SearchResult(false, List.of(), "PatentsView error: " + msg);
        }
    }

    private String buildKeywordQuery(List<String> keywords) {
        StringBuilder criteriaList = new StringBuilder();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) criteriaList.append(",");
            String escaped = keywords.get(i).replace("\"", "\\\"");
            criteriaList.append("{\"_text_any\":{\"patent_abstract\":\"").append(escaped).append("\"}}");
        }

        return """
            {
              "q": {"_or": [%s]},
              "f": ["patent_number","patent_title","patent_abstract","patent_date",
                     "assignee_organization","cpc_group_id"],
              "o": {"per_page": %d},
              "s": [{"patent_date":"desc"}]
            }
            """.formatted(criteriaList.toString(), MAX_RESULTS_PER_QUERY);
    }

    private String buildCpcQuery(List<String> cpcCodes) {
        StringBuilder criteriaList = new StringBuilder();
        for (int i = 0; i < cpcCodes.size(); i++) {
            if (i > 0) criteriaList.append(",");
            String escaped = cpcCodes.get(i).replace("\"", "\\\"");
            criteriaList.append("{\"_begins\":{\"cpc_group_id\":\"").append(escaped).append("\"}}");
        }

        return """
            {
              "q": {"_or": [%s]},
              "f": ["patent_number","patent_title","patent_abstract","patent_date",
                     "assignee_organization","cpc_group_id"],
              "o": {"per_page": %d},
              "s": [{"patent_date":"desc"}]
            }
            """.formatted(criteriaList.toString(), MAX_RESULTS_PER_QUERY);
    }

    private List<DiscoveredPatent> parseResponse(String json) {
        List<DiscoveredPatent> results = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode patents = root.get("patents");
            if (patents == null || !patents.isArray()) return results;

            for (JsonNode p : patents) {
                String patentNumber = getTextOrNull(p, "patent_number");
                if (patentNumber == null) continue;

                String title = getTextOrNull(p, "patent_title");
                String abstractText = getTextOrNull(p, "patent_abstract");
                LocalDate grantDate = parseDate(getTextOrNull(p, "patent_date"));

                String assignee = null;
                JsonNode assignees = p.get("assignees");
                if (assignees != null && assignees.isArray() && !assignees.isEmpty()) {
                    assignee = getTextOrNull(assignees.get(0), "assignee_organization");
                }

                StringBuilder cpcBuilder = new StringBuilder();
                JsonNode cpcs = p.get("cpcs");
                if (cpcs != null && cpcs.isArray()) {
                    for (JsonNode cpc : cpcs) {
                        String code = getTextOrNull(cpc, "cpc_group_id");
                        if (code != null) {
                            if (!cpcBuilder.isEmpty()) cpcBuilder.append(",");
                            cpcBuilder.append(code);
                        }
                    }
                }

                DiscoveredPatent dp = DiscoveredPatent.builder()
                        .patentNumber("US" + patentNumber)
                        .title(title != null ? title : patentNumber)
                        .abstractText(abstractText)
                        .assignee(assignee)
                        .grantDate(grantDate)
                        .cpcCodes(cpcBuilder.isEmpty() ? null : cpcBuilder.toString())
                        .source(DiscoveredPatent.Source.PATENTSVIEW.name())
                        .build();
                results.add(dp);
            }
        } catch (Exception ex) { }
        return results;
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        String text = val.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public record SearchResult(boolean success, List<DiscoveredPatent> patents, String error) {}

    public interface SearchProgressCallback {
        void onStatus(String status);
        boolean isCancelled();
    }
}
