package com.example.TradeShift.controller;

import com.example.TradeShift.model.Transaction;
import com.example.TradeShift.service.TradingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TradingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradingService tradingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "testuser")
    void testBuyOrder() throws Exception {
        // Given
        TradingController.OrderRequest request = new TradingController.OrderRequest();
        request.symbol = "AAPL";
        request.quantity = 10.0;
        request.price = 150.0;

        Transaction tx = new Transaction("testuser", "AAPL", 10.0, 150.0, "BUY");
        when(tradingService.executeBuyOrder(eq("testuser"), eq("AAPL"), eq(10.0), eq(150.0)))
                .thenReturn(tx);

        // When/Then
        mockMvc.perform(post("/api/trades/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.type").value("BUY"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void testSellOrder() throws Exception {
        // Given
        TradingController.OrderRequest request = new TradingController.OrderRequest();
        request.symbol = "AAPL";
        request.quantity = 5.0;
        request.price = 150.0;

        Transaction tx = new Transaction("testuser", "AAPL", 5.0, 150.0, "SELL");
        when(tradingService.executeSellOrder(eq("testuser"), eq("AAPL"), eq(5.0), eq(150.0)))
                .thenReturn(tx);

        // When/Then
        mockMvc.perform(post("/api/trades/sell")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.type").value("SELL"));
    }
}

