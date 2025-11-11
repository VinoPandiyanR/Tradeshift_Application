package com.example.TradeShift.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
    private final RestClient http = RestClient.create();

    private static final Map<String, List<Map<String, String>>> MOCK_STOCKS = Map.of(
        "AAPL", List.of(Map.of("symbol", "AAPL", "name", "Apple Inc.")),
        "GOOGL", List.of(Map.of("symbol", "GOOGL", "name", "Alphabet Inc.")),
        "MSFT", List.of(Map.of("symbol", "MSFT", "name", "Microsoft Corporation")),
        "AMZN", List.of(Map.of("symbol", "AMZN", "name", "Amazon.com Inc.")),
        "TSLA", List.of(Map.of("symbol", "TSLA", "name", "Tesla Inc.")),
        "META", List.of(Map.of("symbol", "META", "name", "Meta Platforms Inc.")),
        "NVDA", List.of(Map.of("symbol", "NVDA", "name", "NVIDIA Corporation")),
        "NFLX", List.of(Map.of("symbol", "NFLX", "name", "Netflix Inc."))
    );

    @SuppressWarnings("unchecked")
    @GetMapping("/stock")
    public ResponseEntity<?> search(@RequestParam("q") String q) {
        try {
            if (q == null || q.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Validation failed", "message", "Query parameter 'q' is required"));
            }

            String query = q.trim().toUpperCase();

            if (MOCK_STOCKS.containsKey(query)) {
                logger.debug("Returning mock data for symbol: {}", query);
                return ResponseEntity.ok(MOCK_STOCKS.get(query));
            }

            try {
                String url = "https://query1.finance.yahoo.com/v1/finance/search?q=" + 
                    java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
                
                Map<String, Object> res = http.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Map.class);
                
                Object quotesObj = res != null ? res.get("quotes") : null;
                List<Map<String, Object>> quotes = quotesObj instanceof List ? 
                    (List<Map<String, Object>>) quotesObj : List.of();

                List<Map<String, String>> out = new ArrayList<>();
                for (Map<String, Object> m : quotes) {
                    if (m == null) continue;
                    Map<String, String> row = new HashMap<>();
                    Object sym = m.get("symbol");
                    String symbol = sym == null ? "" : String.valueOf(sym).trim();
                    if (symbol.isEmpty()) continue;

                    row.put("symbol", symbol);
                    Object sn = m.get("shortname");
                    Object ln = m.get("longname");
                    String name = sn != null ? sn.toString() : (ln != null ? ln.toString() : symbol);
                    row.put("name", name);
                    out.add(row);
                }

                if (!out.isEmpty()) {
                    return ResponseEntity.ok(out);
                }
            } catch (RestClientResponseException e) {
                logger.warn("Yahoo Finance API returned error {}: {}", e.getStatusCode().value(), e.getMessage());
            } catch (Exception apiError) {
                logger.warn("Error calling Yahoo Finance API: {}", apiError.getMessage());
            }

            List<Map<String, String>> mockResults = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, String>>> entry : MOCK_STOCKS.entrySet()) {
                String key = entry.getKey();
                String name = entry.getValue().get(0).get("name").toUpperCase();
                if (key.contains(query) || query.contains(key) || name.contains(query)) {
                    mockResults.addAll(entry.getValue());
                }
            }

            if (!mockResults.isEmpty()) {
                logger.debug("Returning mock results for query: {}", query);
                return ResponseEntity.ok(mockResults);
            }

            return ResponseEntity.ok(List.of());
        } catch (Exception e) {
            logger.error("Error in stock search: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", "Failed to search stocks: " + e.getMessage());
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
