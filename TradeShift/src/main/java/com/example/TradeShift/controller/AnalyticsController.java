package com.example.TradeShift.controller;

import com.example.TradeShift.service.PortfolioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final PortfolioService portfolioService;

    public AnalyticsController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(Authentication auth) {
        try {
            String userId = auth.getName();
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "User not authenticated"));
            }
            List<Map<String,Object>> holdings = portfolioService.getPortfolioWithPrices(userId);
            double totalValue = 0, totalCost = 0;
            Map<String,Double> bySymbol = new HashMap<>();
            for (Map<String,Object> h : holdings) {
                if (h == null) continue;
                try {
                    Object qtyObj = h.get("quantity");
                    Object avgObj = h.get("avgBuyPrice");
                    Object priceObj = h.get("marketPrice");
                    if (priceObj == null || !(priceObj instanceof Number) || ((Number)priceObj).doubleValue() <= 0) {
                        priceObj = h.get("currentPrice");
                    }

                    double qty = qtyObj instanceof Number ? ((Number)qtyObj).doubleValue() : 0.0;
                    double avg = avgObj instanceof Number ? ((Number)avgObj).doubleValue() : 0.0;
                    double price = priceObj instanceof Number ? ((Number)priceObj).doubleValue() : 0.0;

                    if (price > 0) {
                        double marketValue = qty * price;
                        totalValue += marketValue;
                        totalCost += qty * avg;
                        String sym = String.valueOf(h.get("symbol"));
                        if (sym != null && !sym.isEmpty() && !sym.equals("null")) {
                            bySymbol.put(sym, bySymbol.getOrDefault(sym, 0.0) + marketValue);
                        }
                    }
                } catch (Exception e) {
                }
            }
            double pl = totalValue - totalCost;
            Map<String,Object> out = new HashMap<>();
            out.put("totalValue", totalValue);
            out.put("totalPL", pl);
            out.put("allocation", bySymbol);
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid request");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", "Failed to retrieve analytics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
