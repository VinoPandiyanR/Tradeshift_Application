package com.example.TradeShift.controller;

import com.example.TradeShift.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MarketDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketDataService marketDataService;

    @Test
    void testGetCurrentPrice_Success() throws Exception {
        // Given
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(150.0);

        // When/Then
        mockMvc.perform(get("/api/marketdata/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(150.0))
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    void testGetCurrentPrice_InvalidSymbol() throws Exception {
        // Given
        when(marketDataService.getCurrentPrice("INVALID")).thenReturn(0.0);

        // When/Then
        mockMvc.perform(get("/api/marketdata/INVALID"))
                .andExpect(status().isOk()) // Service returns 0.0, not an error
                .andExpect(jsonPath("$.price").value(0.0));
    }
}

