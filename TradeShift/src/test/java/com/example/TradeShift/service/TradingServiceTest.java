package com.example.TradeShift.service;

import com.example.TradeShift.model.Transaction;
import com.example.TradeShift.model.PortfolioAsset;
import com.example.TradeShift.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private PortfolioService portfolioService;
    
    @Mock
    private AccountService accountService;
    
    @InjectMocks
    private TradingService tradingService;

    @Test
    void testExecuteBuyOrder_Success() {
        // Given
        String userId = "user1";
        String symbol = "AAPL";
        Double quantity = 10.0;
        Double price = 150.0;
        
        // When
        Transaction tx = new Transaction(userId, symbol, quantity, price, "BUY");
        when(transactionRepository.save(any(Transaction.class))).thenReturn(tx);
        doNothing().when(accountService).debit(userId, 1500.0);
        when(portfolioService.addOrUpdate(userId, symbol, quantity, price))
                .thenReturn(new PortfolioAsset(userId, symbol, quantity, price));
        
        // Then
        Transaction result = tradingService.executeBuyOrder(userId, symbol, quantity, price);
        
        assertNotNull(result);
        assertEquals("BUY", result.getType());
        verify(accountService).debit(userId, 1500.0);
        verify(portfolioService).addOrUpdate(userId, symbol, quantity, price);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void testExecuteBuyOrder_InvalidParameters() {
        // Given
        String userId = "user1";
        String symbol = "AAPL";
        
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            tradingService.executeBuyOrder(userId, symbol, -10.0, 150.0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            tradingService.executeBuyOrder(userId, symbol, 10.0, -150.0);
        });
    }

    @Test
    void testExecuteSellOrder_Success() {
        // Given
        String userId = "user1";
        String symbol = "AAPL";
        Double quantity = 5.0;
        Double price = 150.0;
        
        PortfolioAsset asset = new PortfolioAsset(userId, symbol, 10.0, 140.0);
        
        // When
        when(portfolioService.getPortfolioAsset(userId, symbol))
                .thenReturn(Optional.of(asset));
        when(portfolioService.addOrUpdate(userId, symbol, -quantity, price))
                .thenReturn(asset);
        doNothing().when(accountService).credit(userId, 750.0);
        
        Transaction tx = new Transaction(userId, symbol, quantity, price, "SELL");
        when(transactionRepository.save(any(Transaction.class))).thenReturn(tx);
        
        // Then
        Transaction result = tradingService.executeSellOrder(userId, symbol, quantity, price);
        
        assertNotNull(result);
        assertEquals("SELL", result.getType());
        verify(accountService).credit(userId, 750.0);
        verify(portfolioService).addOrUpdate(userId, symbol, -quantity, price);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void testExecuteSellOrder_InsufficientQuantity() {
        // Given
        String userId = "user1";
        String symbol = "AAPL";
        Double quantity = 15.0;
        Double price = 150.0;
        
        PortfolioAsset asset = new PortfolioAsset(userId, symbol, 10.0, 140.0);
        
        // When
        when(portfolioService.getPortfolioAsset(userId, symbol))
                .thenReturn(Optional.of(asset));
        
        // Then
        assertThrows(IllegalArgumentException.class, () -> {
            tradingService.executeSellOrder(userId, symbol, quantity, price);
        });
    }
}

