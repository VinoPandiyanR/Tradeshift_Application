package com.example.TradeShift.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;


@Service
public class RealTimeNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(RealTimeNotificationService.class);
    private final SimpMessagingTemplate messagingTemplate;

    public RealTimeNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }


    public void notifyTransaction(String userId, String type, String symbol, double quantity, double price, double total) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "transaction");
            notification.put("transactionType", type);
            notification.put("symbol", symbol);
            notification.put("quantity", quantity);
            notification.put("price", price);
            notification.put("total", total);
            notification.put("timestamp", System.currentTimeMillis());
            
            String destination = "/topic/user/" + userId + "/notifications";
            messagingTemplate.convertAndSend(destination, notification);
            logger.debug("Sent transaction notification to user {}: {} {} {} shares at ${}", 
                userId, type, quantity, symbol, price);
        } catch (Exception e) {
            logger.error("Error sending transaction notification to user {}: {}", userId, e.getMessage());
        }
    }


    public void notifyBalanceUpdate(String userId, double newBalance, double change) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "balance");
            notification.put("balance", newBalance);
            notification.put("change", change);
            notification.put("timestamp", System.currentTimeMillis());
            
            String destination = "/topic/user/" + userId + "/notifications";
            messagingTemplate.convertAndSend(destination, notification);
            logger.debug("Sent balance update to user {}: ${} (change: ${})", userId, newBalance, change);
        } catch (Exception e) {
            logger.error("Error sending balance update to user {}: {}", userId, e.getMessage());
        }
    }

}

