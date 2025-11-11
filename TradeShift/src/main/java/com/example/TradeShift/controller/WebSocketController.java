package com.example.TradeShift.controller;

import com.example.TradeShift.service.PriceUpdateService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class WebSocketController {

    private final PriceUpdateService priceUpdateService;

    public WebSocketController(PriceUpdateService priceUpdateService) {
        this.priceUpdateService = priceUpdateService;
    }

    @MessageMapping("/subscribe")
    public void subscribe(@Payload Map<String, String> payload) {
        String sessionId = payload.get("sessionId");
        String symbol = payload.get("symbol");
        if (sessionId != null && symbol != null) {
            priceUpdateService.subscribe(sessionId, symbol);
        }
    }

    @MessageMapping("/unsubscribe")
    public void unsubscribe(@Payload Map<String, String> payload) {
        String sessionId = payload.get("sessionId");
        String symbol = payload.get("symbol");
        if (sessionId != null && symbol != null) {
            priceUpdateService.unsubscribe(sessionId, symbol);
        }
    }
}

