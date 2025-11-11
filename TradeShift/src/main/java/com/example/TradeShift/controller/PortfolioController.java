package com.example.TradeShift.controller;

import com.example.TradeShift.model.PortfolioAsset;
import com.example.TradeShift.service.PortfolioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public ResponseEntity<?> getPortfolio(Authentication auth) {
        try {
            String userId = auth.getName();
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "User not authenticated"));
            }
            List<Map<String,Object>> portfolio = portfolioService.getPortfolioWithPrices(userId);
            return ResponseEntity.ok(portfolio);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", "Failed to retrieve portfolio: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    public static class AddAssetRequest {
        @NotBlank(message = "Symbol is required")
        public String symbol;
        
        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity must be non-negative")
        public Double quantity;
        
        @NotNull(message = "Average buy price is required")
        @Min(value = 0, message = "Average buy price must be non-negative")
        public Double avgBuyPrice;
    }

    @PostMapping
    public ResponseEntity<?> addAsset(Authentication auth, @Valid @RequestBody AddAssetRequest req) {
        try {
            String userId = auth.getName();
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "User not authenticated"));
            }
            if (req.symbol == null || req.symbol.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Validation failed", "message", "Symbol cannot be empty"));
            }
            if (req.quantity == null || req.quantity <= 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Validation failed", "message", "Quantity must be greater than 0"));
            }
            if (req.avgBuyPrice == null || req.avgBuyPrice <= 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Validation failed", "message", "Average buy price must be greater than 0"));
            }

            String normalizedSymbol = req.symbol.trim().toUpperCase();
            

            PortfolioAsset saved = portfolioService.addOrUpdate(userId, normalizedSymbol, 
                req.quantity, req.avgBuyPrice);
            
            if (saved == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid request", "message", "Failed to add asset. Please check your input."));
            }
            
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid request");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", "Failed to add asset: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAsset(Authentication auth, @PathVariable Long id) {
        try {
            String userId = auth.getName();
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "User not authenticated"));
            }
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Validation failed", "message", "Invalid asset ID"));
            }
            portfolioService.remove(userId, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid request");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", "Failed to delete asset: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}


