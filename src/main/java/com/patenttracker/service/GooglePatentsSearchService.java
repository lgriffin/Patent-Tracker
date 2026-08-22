package com.patenttracker.service;

import com.patenttracker.dao.MinedPatentDao;
import com.patenttracker.model.DiscoveredPatent;
import com.patenttracker.model.MinedPatent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GooglePatentsSearchService {

    private static final int MAX_RESULTS = 100;
    private static final int MAX_RETRIES = 4;
    private static final long RETRY_BASE_DELAY_MS = 15000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final MinedPatentDao minedPatentDao;

    public GooglePatentsSearchService() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.minedPatentDao = new MinedPatentDao();
    }

    public GooglePatentsSearchService(MinedPatentDao minedPatentDao) {
        this.minedPatentDao = minedPatentDao;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public SearchResult search(String area, List<String> keywords, SearchProgressCallback callback) {
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusYears(2);

        String query = buildQuery(area, keywords);

        if (callback != null) callback.onStatus("Searching Google Patents for: " + area);

        try {
            String queryParam = buildQueryParam(query, fromDate, toDate);
            String xhrUrl = "https://patents.google.com/xhr/query?url="
                    + URLEncoder.encode(queryParam, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(xhrUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = null;
            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                if (callback != null && callback.isCancelled()) {
                    return new SearchResult(false, area, 0, List.of(), "Search cancelled.");
                }
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429 || response.statusCode() == 503) {
                    if (attempt < MAX_RETRIES) {
                        long delay = RETRY_BASE_DELAY_MS * (1L << attempt);
                        if (callback != null) {
                            callback.onStatus("Rate limited (HTTP " + response.statusCode()
                                    + "), retrying in " + (delay / 1000) + "s...");
                        }
                        Thread.sleep(delay);
                        continue;
                    }
                }
                break;
            }

            if (response.statusCode() != 200) {
                boolean rl = response.statusCode() == 429 || response.statusCode() == 503;
                return new SearchResult(false, area, 0, List.of(),
                        "Google Patents returned HTTP " + response.statusCode(), rl);
            }

            List<MinedPatent> patents = parseXhrResults(response.body(), area, query);

            if (patents.isEmpty()) {
                return new SearchResult(false, area, 0, List.of(),
                        "No patents found for \"" + area + "\". Try different keywords.");
            }

            int cap = Math.min(patents.size(), MAX_RESULTS);
            patents = new ArrayList<>(patents.subList(0, cap));

            if (callback != null) callback.onStatus("Found " + patents.size() + " patents.");

            minedPatentDao.deleteBySearchArea(area);
            for (MinedPatent mp : patents) {
                minedPatentDao.insertOrIgnore(mp);
            }

            return new SearchResult(true, area, patents.size(), patents, null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SearchResult(false, area, 0, List.of(), "Search cancelled.");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new SearchResult(false, area, 0, List.of(), "Search failed: " + msg);
        }
    }

    private String buildQuery(String area, List<String> keywords) {
        Set<String> terms = new LinkedHashSet<>();
        terms.add(area);
        if (keywords != null) {
            for (String kw : keywords) {
                if (kw != null && !kw.isBlank()) {
                    terms.add(kw.trim());
                }
                if (terms.size() >= 6) break;
            }
        }
        return String.join(" ", terms);
    }

    private String buildQueryParam(String query, LocalDate fromDate, LocalDate toDate) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String from = fromDate.format(DATE_FMT);
        String to = toDate.format(DATE_FMT);
        return "q=" + encoded
                + "&country=US&type=PATENT"
                + "&after=priority:" + from
                + "&before=priority:" + to
                + "&num=" + MAX_RESULTS;
    }

    private List<MinedPatent> parseXhrResults(String json, String area, String query) {
        List<MinedPatent> results = new ArrayList<>();

        try {
            JsonNode root = mapper.readTree(json);
            JsonNode resultsNode = root.get("results");
            if (resultsNode == null) return results;

            JsonNode clusters = resultsNode.get("cluster");
            if (clusters == null || !clusters.isArray()) return results;

            for (JsonNode cluster : clusters) {
                JsonNode resultArray = cluster.get("result");
                if (resultArray == null || !resultArray.isArray()) continue;

                for (JsonNode item : resultArray) {
                    JsonNode patent = item.get("patent");
                    if (patent == null) continue;

                    String pubNumber = getTextOrNull(patent, "publication_number");
                    if (pubNumber == null) continue;

                    String title = getTextOrNull(patent, "title");
                    if (title != null) {
                        title = title.replaceAll("<[^>]+>", "").trim();
                    }

                    String snippet = getTextOrNull(patent, "snippet");
                    if (snippet != null) {
                        snippet = snippet
                                .replaceAll("<[^>]+>", "")
                                .replace("&hellip;", "...")
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("&quot;", "\"")
                                .replace("&#39;", "'")
                                .trim();
                    }

                    LocalDate grantDate = null;
                    String grantDateStr = getTextOrNull(patent, "grant_date");
                    if (grantDateStr != null) {
                        try {
                            grantDate = LocalDate.parse(grantDateStr);
                        } catch (Exception ignored) {}
                    }

                    MinedPatent mp = MinedPatent.builder()
                            .patentNumber(pubNumber)
                            .title(title != null ? title : pubNumber)
                            .abstractText(snippet)
                            .grantDate(grantDate)
                            .searchArea(area)
                            .searchQuery(query)
                            .build();
                    results.add(mp);

                    if (results.size() >= MAX_RESULTS) return results;
                }
            }
        } catch (Exception ignored) {}

        return results;
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        String text = val.asText().trim();
        return text.isEmpty() ? null : text;
    }

    public record SearchResult(boolean success, String area, int patentsFound,
                                List<MinedPatent> patents, String error,
                                boolean rateLimited) {
        public SearchResult(boolean success, String area, int patentsFound,
                            List<MinedPatent> patents, String error) {
            this(success, area, patentsFound, patents, error, false);
        }
    }

    public interface SearchProgressCallback {
        void onStatus(String status);
        boolean isCancelled();
    }

    public PriorArtSearchResult searchForPriorArt(List<String> queries, SearchProgressCallback callback) {
        List<DiscoveredPatent> allResults = new ArrayList<>();
        Set<String> seenNumbers = new java.util.HashSet<>();

        for (String query : queries) {
            if (callback != null && callback.isCancelled()) break;

            if (callback != null) callback.onStatus("Google Patents: searching \"" + query + "\"...");

            LocalDate toDate = LocalDate.now();
            LocalDate fromDate = toDate.minusYears(5);

            try {
                String queryParam = buildQueryParam(query, fromDate, toDate);
                String xhrUrl = "https://patents.google.com/xhr/query?url="
                        + URLEncoder.encode(queryParam, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(xhrUrl))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response = null;
                for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                    if (callback != null && callback.isCancelled()) break;
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 429 || response.statusCode() == 503) {
                        if (attempt < MAX_RETRIES) {
                            long delay = RETRY_BASE_DELAY_MS * (1L << attempt);
                            if (callback != null) {
                                callback.onStatus("Rate limited, retrying in " + (delay / 1000) + "s...");
                            }
                            Thread.sleep(delay);
                            continue;
                        }
                    }
                    break;
                }

                if (response != null && response.statusCode() == 200) {
                    List<MinedPatent> parsed = parseXhrResults(response.body(), query, query);
                    for (MinedPatent mp : parsed) {
                        if (seenNumbers.add(mp.getPatentNumber())) {
                            DiscoveredPatent dp = DiscoveredPatent.builder()
                                    .patentNumber(mp.getPatentNumber())
                                    .title(mp.getTitle())
                                    .abstractText(mp.getAbstractText())
                                    .grantDate(mp.getGrantDate())
                                    .source(DiscoveredPatent.Source.GOOGLE_PATENTS.name())
                                    .build();
                            allResults.add(dp);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {}
        }

        if (callback != null) {
            callback.onStatus("Google Patents found " + allResults.size() + " unique patents.");
        }
        return new PriorArtSearchResult(!allResults.isEmpty() || queries.isEmpty(), allResults, null);
    }

    public record PriorArtSearchResult(boolean success, List<DiscoveredPatent> patents, String error) {}
}
