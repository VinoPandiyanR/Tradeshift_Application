package com.example.TradeShift.controller;

import com.example.TradeShift.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/balance")
    public ResponseEntity<?> balance(Authentication auth) {
        try {
            String userId = auth.getName();
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "User not authenticated"));
            }
            double bal = accountService.getBalance(userId);
            return ResponseEntity.ok(Map.of("balance", bal));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", "Failed to retrieve balance: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    public static class AmountReq { 
        @NotNull(message = "Amount is required")
        @Min(value = 0, message = "Amount must be non-negative")
        public Double amount; 
    }

    @PostMapping("/credit")
    public ResponseEntity<?> credit(Authentication auth, @Valid @RequestBody AmountReq req) {
        try {
            String userId = auth.getName();
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "User not authenticated"));
            }
            if (req.amount == null || req.amount < 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Validation failed", "message", "Amount must be non-negative"));
            }
            accountService.credit(userId, req.amount);
            return ResponseEntity.ok(Map.of("balance", accountService.getBalance(userId)));
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid request");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", "Failed to credit account: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}


