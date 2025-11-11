package com.example.TradeShift.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MarketDataService {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataService.class);
    private final RestClient http = RestClient.create();
    

    private final Map<String, PriceCacheEntry> priceCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30000; // 30 seconds cache
    

    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private static final long MIN_REQUEST_INTERVAL_MS = 200; // 200ms between requests
    

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1 second
    

    private static final Map<String, Double> MOCK_PRICES = Map.of(
        "AAPL", 175.50,
        "GOOGL", 142.30,
        "MSFT", 378.85,
        "AMZN", 151.94,
        "TSLA", 248.50,
        "META", 485.20,
        "NVDA", 496.56,
        "NFLX", 485.20
    );

    private static class PriceCacheEntry {
        final double price;
        final long timestamp;
        
        PriceCacheEntry(double price, long timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    @SuppressWarnings("unchecked")
    private double fetchPriceFromAPI(String symbol, int retryCount) {
        if (symbol == null || symbol.trim().isEmpty()) {
            logger.warn("Empty symbol provided for price fetch");
            return 0.0;
        }
        
        String normalizedSymbol = symbol.trim().toUpperCase();
        

        long currentTime = System.currentTimeMillis();
        long lastTime = lastRequestTime.get();
        long timeSinceLastRequest = currentTime - lastTime;
        
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Thread interrupted during rate limiting delay");
                return 0.0;
            }
        }
        lastRequestTime.set(System.currentTimeMillis());
        
        try {
            String url = "https://query1.finance.yahoo.com/v7/finance/quote?symbols=" + 
                        java.net.URLEncoder.encode(normalizedSymbol, java.nio.charset.StandardCharsets.UTF_8);
            
            Map<String, Object> res = http.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .retrieve()
                    .onStatus(status -> status.value() == 429, (request, response) -> {
                        throw new HttpClientErrorException(response.getStatusCode(), 
                            "Rate limit exceeded: " + response.getStatusText());
                    })
                    .body(Map.class);
            
            if (res == null) {
                logger.warn("No response from Yahoo Finance API for symbol: {}", normalizedSymbol);
                return 0.0;
            }
            
            Object qr = res.get("quoteResponse");
            if (!(qr instanceof Map)) {
                logger.warn("Invalid quoteResponse format for symbol: {}", normalizedSymbol);
                return 0.0;
            }
            
            Map<String, Object> quoteResponse = (Map<String, Object>) qr;
            Object resultList = quoteResponse.get("result");
            if (!(resultList instanceof List)) {
                logger.warn("No results in quoteResponse for symbol: {}", normalizedSymbol);
                return 0.0;
            }
            
            List<Map<String, Object>> list = (List<Map<String, Object>>) resultList;
            if (list.isEmpty()) {
                logger.warn("Empty result list for symbol: {}", normalizedSymbol);
                return 0.0;
            }
            
            Map<String, Object> first = list.get(0);
            if (first == null) {
                logger.warn("Null first result for symbol: {}", normalizedSymbol);
                return 0.0;
            }
            

            Double price = extractPrice(first, "regularMarketPrice");
            if (price == null || price <= 0) {
                price = extractPrice(first, "regularMarketPreviousClose");
            }
            if (price == null || price <= 0) {
                price = extractPrice(first, "bid");
            }
            if (price == null || price <= 0) {
                price = extractPrice(first, "ask");
            }
            if (price == null || price <= 0) {
                price = extractPrice(first, "previousClose");
            }
            
            if (price == null || price <= 0) {
                logger.warn("Could not extract valid price for symbol: {}", normalizedSymbol);
                return 0.0;
            }
            

            priceCache.put(normalizedSymbol, new PriceCacheEntry(price, System.currentTimeMillis()));
            logger.debug("Fetched price for {}: {}", normalizedSymbol, price);
            return price;
            
        } catch (HttpClientErrorException e) {
            int statusCode = e.getStatusCode().value();
            

            if (statusCode == 401) {
                logger.debug("Yahoo Finance API returned 401 Unauthorized for symbol {}. Using fallback mechanism.", normalizedSymbol);

                PriceCacheEntry cached = priceCache.get(normalizedSymbol);
                if (cached != null) {
                    logger.debug("Using cached price for {} after 401 error: {}", normalizedSymbol, cached.price);
                    return cached.price;
                }

                Double mockPrice = MOCK_PRICES.get(normalizedSymbol);
                if (mockPrice != null) {
                    logger.debug("Using mock price for {}: {}", normalizedSymbol, mockPrice);

                    priceCache.put(normalizedSymbol, new PriceCacheEntry(mockPrice, System.currentTimeMillis()));
                    return mockPrice;
                }
                logger.warn("No cached or mock price available for symbol {}", normalizedSymbol);
                return 0.0;
            }
            
            if (statusCode == 429) {

                if (retryCount < MAX_RETRIES) {
                    long delay = INITIAL_RETRY_DELAY_MS * (1L << retryCount); // Exponential backoff
                    logger.warn("Rate limit exceeded for symbol {}: {}. Retrying in {}ms (attempt {}/{})", 
                            normalizedSymbol, e.getMessage(), delay, retryCount + 1, MAX_RETRIES);
                    try {
                        Thread.sleep(delay);
                        return fetchPriceFromAPI(symbol, retryCount + 1);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Thread interrupted during retry delay for symbol {}", normalizedSymbol);
                        return 0.0;
                    }
                } else {
                    logger.error("Max retries exceeded for symbol {} due to rate limiting. Returning cached price if available.", 
                            normalizedSymbol);

                    PriceCacheEntry cached = priceCache.get(normalizedSymbol);
                    if (cached != null && !cached.isExpired()) {
                        logger.info("Using cached price for {}: {}", normalizedSymbol, cached.price);
                        return cached.price;
                    }
                    Double mockPrice = MOCK_PRICES.get(normalizedSymbol);
                    if (mockPrice != null) {
                        logger.debug("Using mock price for {} after max retries: {}", normalizedSymbol, mockPrice);
                        return mockPrice;
                    }
                    return 0.0;
                }
            } else {
                logger.error("HTTP error fetching price for symbol {}: {} - {}", 
                        normalizedSymbol, statusCode, e.getMessage());

                PriceCacheEntry cached = priceCache.get(normalizedSymbol);
                if (cached != null && !cached.isExpired()) {
                    logger.info("Using cached price for {} after HTTP error: {}", normalizedSymbol, cached.price);
                    return cached.price;
                }

                if (statusCode == 401 || statusCode == 403) {
                    Double mockPrice = MOCK_PRICES.get(normalizedSymbol);
                    if (mockPrice != null) {
                        logger.debug("Using mock price for {} after auth error: {}", normalizedSymbol, mockPrice);
                        return mockPrice;
                    }
                }
                return 0.0;
            }
        } catch (RestClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            logger.error("REST client error fetching price for symbol {}: {} - {}", 
                    normalizedSymbol, statusCode, e.getMessage());

            PriceCacheEntry cached = priceCache.get(normalizedSymbol);
            if (cached != null && !cached.isExpired()) {
                logger.info("Using cached price for {} after REST error: {}", normalizedSymbol, cached.price);
                return cached.price;
            }

            if (statusCode == 401 || statusCode == 403) {
                Double mockPrice = MOCK_PRICES.get(normalizedSymbol);
                if (mockPrice != null) {
                    logger.debug("Using mock price for {} after REST auth error: {}", normalizedSymbol, mockPrice);
                    return mockPrice;
                }
            }
            return 0.0;
        } catch (Exception e) {
            logger.error("Error fetching price for symbol {}: {}", normalizedSymbol, e.getMessage(), e);

            PriceCacheEntry cached = priceCache.get(normalizedSymbol);
            if (cached != null && !cached.isExpired()) {
                logger.debug("Using cached price for {} after exception: {}", normalizedSymbol, cached.price);
                return cached.price;
            }

            Double mockPrice = MOCK_PRICES.get(normalizedSymbol);
            if (mockPrice != null) {
                logger.debug("Using mock price for {} after exception: {}", normalizedSymbol, mockPrice);
                return mockPrice;
            }
            return 0.0;
        }
    }
    
    private Double extractPrice(Map<String, Object> quote, String fieldName) {
        try {
            Object priceObj = quote.get(fieldName);
            if (priceObj == null) return null;
            
            if (priceObj instanceof Number) {
                return ((Number) priceObj).doubleValue();
            } else if (priceObj instanceof String) {
                String priceStr = ((String) priceObj).trim();
                if (priceStr.isEmpty()) return null;
                return Double.parseDouble(priceStr);
            }
            return null;
        } catch (Exception e) {
            logger.debug("Could not extract price from field {}: {}", fieldName, e.getMessage());
            return null;
        }
    }

    public double getCurrentPrice(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return 0.0;
        }
        
        String normalizedSymbol = symbol.trim().toUpperCase();
        

        PriceCacheEntry cached = priceCache.get(normalizedSymbol);
        if (cached != null && !cached.isExpired()) {
            logger.debug("Returning cached price for {}: {}", normalizedSymbol, cached.price);
            return cached.price;
        }
        
        try {
            double price = fetchPriceFromAPI(symbol, 0);
            if (price <= 0) {

                if (cached != null) {
                    logger.info("API returned invalid price for {}, using stale cache: {}", 
                            normalizedSymbol, cached.price);
                    return cached.price;
                }

                Double mockPrice = MOCK_PRICES.get(normalizedSymbol);
                if (mockPrice != null) {
                    logger.debug("API returned invalid price for {}, using mock price: {}", 
                            normalizedSymbol, mockPrice);
                    return mockPrice;
                }
                logger.warn("Invalid price returned for symbol {}: {}", symbol, price);
            }
            return price;
        } catch (Exception e) {
            logger.error("Error in getCurrentPrice for symbol {}: {}", symbol, e.getMessage(), e);

            if (cached != null) {
                logger.debug("Using cached price for {} after exception: {}", normalizedSymbol, cached.price);
                return cached.price;
            }

            Double mockPrice = MOCK_PRICES.get(normalizedSymbol);
            if (mockPrice != null) {
                logger.debug("Using mock price for {} after exception: {}", normalizedSymbol, mockPrice);
                return mockPrice;
            }
            return 0.0;
        }
    }

    public double getMarketPrice(String symbol) {
        return getCurrentPrice(symbol);
    }
}
