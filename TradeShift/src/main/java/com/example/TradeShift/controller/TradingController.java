package com.example.TradeShift.controller;

import com.example.TradeShift.model.Transaction;
import com.example.TradeShift.service.TradingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/trades")
public class TradingController {

    private final TradingService tradingService;

    public TradingController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    public static class OrderRequest {
        @NotBlank(message = "Symbol is required")
        public String symbol;
        
        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity must be positive")
        public Double quantity;
        
        @NotNull(message = "Price is required")
        @Min(value = 0, message = "Price must be positive")
        public Double price;
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buy(Authentication auth, @Valid @RequestBody OrderRequest order) {
        try {
            String userId = auth.getName();
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "User not authenticated"));
            }
            if (order.symbol == null || order.symbol.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Validation failed", "message", "Symbol cannot be empty"));
            }
            if (order.quantity == null || order.quantity <= 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Validation failed", "message", "Quantity must be positive"));
            }
            if (order.price == null || order.price <= 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Validation failed", "message", "Price must be positive"));
            }
            Transaction tx = tradingService.executeBuyOrder(userId, order.symbol.trim().toUpperCase(), 
                order.quantity, order.price);
            return ResponseEntity.ok(tx);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid request");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", "Failed to execute buy order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sell(Authentication auth, @Valid @RequestBody OrderRequest order) {
        try {
            String userId = auth.getName();
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "User not authenticated"));
            }
            if (order.symbol == null || order.symbol.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Validation failed", "message", "Symbol cannot be empty"));
            }
            if (order.quantity == null || order.quantity <= 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Validation failed", "message", "Quantity must be positive"));
            }
            if (order.price == null || order.price <= 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Validation failed", "message", "Price must be positive"));
            }
            Transaction tx = tradingService.executeSellOrder(userId, order.symbol.trim().toUpperCase(), 
                order.quantity, order.price);
            return ResponseEntity.ok(tx);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid request");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", "Failed to execute sell order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}


