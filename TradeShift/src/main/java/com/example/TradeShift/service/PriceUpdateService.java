package com.example.TradeShift.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PriceUpdateService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MarketDataService marketDataService;
    private final Map<String, Set<String>> subscribedSymbols = new ConcurrentHashMap<>();

    public PriceUpdateService(SimpMessagingTemplate messagingTemplate, 
                             MarketDataService marketDataService) {
        this.messagingTemplate = messagingTemplate;
        this.marketDataService = marketDataService;
    }

    public void subscribe(String sessionId, String symbol) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            return;
        }
        subscribedSymbols.computeIfAbsent(sessionId, k -> new HashSet<>()).add(symbol.trim().toUpperCase());
    }

    public void unsubscribe(String sessionId, String symbol) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            return;
        }
        Set<String> symbols = subscribedSymbols.get(sessionId);
        if (symbols != null) {
            symbols.remove(symbol.trim().toUpperCase());
            if (symbols.isEmpty()) {
                subscribedSymbols.remove(sessionId);
            }
        }
    }

    @Scheduled(fixedRate = 2000)
    public void broadcastPriceUpdates() {
        Set<String> allSymbols = new HashSet<>();
        for (Set<String> symbols : subscribedSymbols.values()) {
            allSymbols.addAll(symbols);
        }

        allSymbols.addAll(Arrays.asList("AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"));

        for (String symbol : allSymbols) {
            if (symbol == null || symbol.trim().isEmpty()) {
                continue;
            }
            try {
                double price = marketDataService.getMarketPrice(symbol);
                
                if (price > 0) {
                    Map<String, Object> update = new HashMap<>();
                    update.put("symbol", symbol.toUpperCase());
                    update.put("price", price);
                    update.put("marketPrice", price);
                    update.put("timestamp", System.currentTimeMillis());

                    double change = 0.0;
                    double changePercent = 0.0;
                    update.put("change", change);
                    update.put("changePercent", changePercent);
                    
                    messagingTemplate.convertAndSend("/topic/prices/" + symbol.toUpperCase(), update);
                }
            } catch (Exception e) {
                System.err.println("Error broadcasting price for " + symbol + ": " + e.getMessage());
            }
        }
    }
}

